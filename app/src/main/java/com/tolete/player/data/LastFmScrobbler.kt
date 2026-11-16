package com.tolete.player.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Scrobbling no Last.fm: login (auth.getMobileSession), "ouvindo agora" e scrobbles.
 *
 * - A senha é usada só no login e NUNCA é guardada; fica apenas a chave de sessão.
 * - Scrobbles vão para uma fila salva no aparelho e são reenviados quando houver conexão.
 * - Regras do Last.fm: faixas com menos de 30 s não são enviadas.
 */
object LastFmScrobbler {

    /**
     * Segredo compartilhado (Shared secret) do app em https://www.last.fm/api/accounts
     * (o mesmo cadastro da API_KEY em LastFmClient). Sem ele o Last.fm recusa o login/scrobble,
     * pois toda chamada autenticada precisa ser assinada. O usuário informa nas configurações.
     */
    @Volatile private var apiSecret: String = ""

    private const val PREFS = "tolete"
    private const val KEY_ENABLED = "scrobble_enabled"
    private const val KEY_SESSION = "scrobble_session_key"
    private const val KEY_USER = "scrobble_user"
    private const val KEY_QUEUE = "scrobble_queue"
    private const val KEY_SECRET = "scrobble_api_secret"
    private const val KEY_APIKEY = "scrobble_api_key"
    private const val QUEUE_MAX = 2000
    private const val BATCH = 50
    private const val MIN_TRACK_MS = 30_000L

    private val _hasSecretFlow = MutableStateFlow(false)
    /** true quando o API Secret foi informado. */
    val hasSecretFlow: StateFlow<Boolean> = _hasSecretFlow.asStateFlow()
    val hasSecret: Boolean get() = apiSecret.isNotBlank()

    private val _current = MutableStateFlow<Track?>(null)
    /** Faixa tocando agora (a que a notificação do scrobbler mostra). */
    val current: StateFlow<Track?> = _current.asStateFlow()

    private val _scrobbledId = MutableStateFlow<String?>(null)
    /** ID da faixa cujo scrobble já foi enviado/enfileirado nesta reprodução (a notificação mostra "Enviado"). */
    val scrobbledId: StateFlow<String?> = _scrobbledId.asStateFlow()

    private val _loveVersion = MutableStateFlow(0)
    /** Muda quando um love/unlove termina (para a notificação atualizar). */
    val loveVersion: StateFlow<Int> = _loveVersion.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushMutex = Mutex()
    private val queueLock = Any()
    private var appCtx: Context? = null
    private var nowPlayingJob: Job? = null
    @Volatile private var sessionKey: String? = null

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _username = MutableStateFlow<String?>(null)
    /** Usuário logado (null = sem login). */
    val username: StateFlow<String?> = _username.asStateFlow()

    private val _pending = MutableStateFlow(0)
    /** Scrobbles aguardando envio. */
    val pending: StateFlow<Int> = _pending.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private data class Entry(
        val artist: String,
        val track: String,
        val album: String,
        val durationSec: Int,
        val ts: Long,
        val albumArtist: String = ""
    )

    @Synchronized
    fun init(context: Context) {
        if (appCtx != null) return
        val ctx = context.applicationContext
        appCtx = ctx
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        LyronStorage.init(ctx)
        LastFmClient.initialize(ctx)
        ScrobbleEdits.init(ctx)
        apiSecret = p.getString(KEY_SECRET, "")?.trim().orEmpty()
        LastFmClient.API_KEY = p.getString(KEY_APIKEY, null)?.trim()?.takeIf { it.isNotBlank() }
            ?: LastFmClient.DEFAULT_API_KEY
        _hasSecretFlow.value = apiSecret.isNotBlank()
        sessionKey = p.getString(KEY_SESSION, null)?.takeIf { it.isNotBlank() }
        _username.value = if (sessionKey != null) p.getString(KEY_USER, null) else null
        _enabled.value = p.getBoolean(KEY_ENABLED, true) && sessionKey != null
        _pending.value = synchronized(queueLock) { readQueue().size }
        if (sessionKey != null && _pending.value > 0) scope.launch { flush() }
    }

    fun isLoggedIn(): Boolean = sessionKey != null

    /** Salva o API Secret informado pelo usuário (vazio = remove). */
    fun setApiSecret(value: String) {
        val ctx = appCtx ?: return
        apiSecret = value.trim()
        _hasSecretFlow.value = apiSecret.isNotBlank()
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SECRET, apiSecret).apply()
        _status.value = if (apiSecret.isBlank()) "API Secret removido." else "API Secret salvo."
    }

    fun getApiSecret(): String = apiSecret

    fun getApiKey(): String = LastFmClient.API_KEY

    /** Salva a API Key do app cadastrado no Last.fm (vazio = volta à chave padrão). */
    fun setApiKey(value: String) {
        val ctx = appCtx ?: return
        val k = value.trim()
        LastFmClient.API_KEY = k.ifBlank { LastFmClient.DEFAULT_API_KEY }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_APIKEY, k).apply()
        _status.value = "API Key salva."
    }

    fun setEnabled(on: Boolean) {
        val ctx = appCtx ?: return
        _enabled.value = on && sessionKey != null
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
        if (on) scope.launch { flush() }
    }

    /** Faz login com usuário e senha do Last.fm. Retorna true se deu certo. */
    suspend fun login(user: String, password: String): Boolean {
        val ctx = appCtx ?: return false
        if (!hasSecret) {
            _status.value = "Informe o API Secret do Last.fm antes de entrar."
            return false
        }
        val u = user.trim()
        if (u.isBlank() || password.isEmpty()) {
            _status.value = "Informe usuário e senha."
            return false
        }
        _busy.value = true
        _status.value = "Entrando…"
        return try {
            val params = linkedMapOf(
                "method" to "auth.getMobileSession",
                "username" to u,
                "password" to password,
                "api_key" to LastFmClient.API_KEY
            )
            params["api_sig"] = sign(params)
            val (code, json) = withContext(Dispatchers.IO) { post(params) }
            val session = json?.optJSONObject("session")
            val key = session?.optString("key")?.takeIf { it.isNotBlank() }
            if (code in 200..299 && key != null) {
                val name = session?.optString("name")?.takeIf { it.isNotBlank() } ?: u
                sessionKey = key
                _username.value = name
                _enabled.value = true
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_SESSION, key)
                    .putString(KEY_USER, name)
                    .putBoolean(KEY_ENABLED, true)
                    .apply()
                _status.value = "Conectado como $name"
                scope.launch { flush() }
                true
            } else {
                _status.value = json?.optString("message")?.takeIf { it.isNotBlank() }
                    ?.let { "Não foi possível entrar: $it" }
                    ?: "Não foi possível entrar (erro $code)."
                false
            }
        } catch (e: IOException) {
            _status.value = "Sem conexão com o Last.fm."
            false
        } catch (e: Exception) {
            _status.value = "Erro ao entrar: ${e.message}"
            false
        } finally {
            _busy.value = false
        }
    }

    fun logout() {
        val ctx = appCtx ?: return
        nowPlayingJob?.cancel()
        sessionKey = null
        _username.value = null
        _enabled.value = false
        synchronized(queueLock) { saveQueue(emptyList()) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_SESSION).remove(KEY_USER).remove(KEY_QUEUE).apply()
        _status.value = "Desconectado."
    }

    private fun eligible(track: Track): Boolean =
        track.title.isNotBlank() && track.artist.isNotBlank() && track.duration >= MIN_TRACK_MS

    /**
     * Define a faixa atual SEM enviar "ouvindo agora" (usado quando a faixa muda com o player pausado,
     * ou pelo próprio serviço). [newPlay] = true começa uma nova reprodução: limpa o estado "enviado".
     */
    fun setCurrent(track: Track?, newPlay: Boolean = false) {
        _current.value = track
        if (newPlay) _scrobbledId.value = null
    }

    /** "Ouvindo agora". Com debounce: em trocas rápidas só a última faixa é enviada. */
    fun nowPlaying(track: Track) {
        _current.value = track
        if (!_enabled.value || sessionKey == null || !eligible(track)) return
        if (ScrobbleEdits.isSkipped(track.artist, track.title)) return
        nowPlayingJob?.cancel()
        nowPlayingJob = scope.launch {
            delay(1500)
            val sk = sessionKey ?: return@launch
            // pulou nesse meio tempo?
            if (ScrobbleEdits.isSkipped(track.artist, track.title)) return@launch
            try {
                val meta = metaFor(track)
                val params = linkedMapOf(
                    "method" to "track.updateNowPlaying",
                    "artist" to meta.artist,
                    "track" to meta.track,
                    "api_key" to LastFmClient.API_KEY,
                    "sk" to sk
                )
                if (meta.album.isNotBlank()) params["album"] = meta.album
                if (meta.albumArtist.isNotBlank()) params["albumArtist"] = meta.albumArtist
                params["duration"] = (track.duration / 1000L).toString()
                params["api_sig"] = sign(params)
                val (_, json) = post(params)
                if (json?.optInt("error", 0) == 9) invalidateSession()
            } catch (_: Exception) {
                // "ouvindo agora" é descartável: não reenvia
            }
        }
    }

    /** Cancela o "ouvindo agora" ainda não enviado (faixa pulada). */
    fun cancelNowPlaying() { nowPlayingJob?.cancel() }

    /** Metadados que vão ao Last.fm: tags do arquivo + edições salvas. */
    fun metaFor(track: Track): ScrobbleEdits.Meta =
        ScrobbleEdits.apply(ScrobbleEdits.Meta(track.title, track.artist, track.album, track.albumArtist))

    /** Enfileira o scrobble (início da reprodução em [startedAtMs]) e tenta enviar. */
    fun scrobble(track: Track, startedAtMs: Long) {
        if (!_enabled.value || sessionKey == null || !eligible(track)) return
        if (ScrobbleEdits.isSkipped(track.artist, track.title)) return
        val meta = metaFor(track)
        _scrobbledId.value = track.id
        val entry = Entry(
            artist = meta.artist,
            track = meta.track,
            album = meta.album,
            durationSec = (track.duration / 1000L).toInt(),
            ts = (startedAtMs / 1000L),
            albumArtist = meta.albumArtist
        )
        // fora da thread principal: ler/gravar a fila envolve JSON e SharedPreferences
        scope.launch {
            synchronized(queueLock) {
                val q = readQueue().toMutableList()
                q.add(entry)
                while (q.size > QUEUE_MAX) q.removeAt(0)
                saveQueue(q)
            }
            // Espelho local para o info card (independe do envio ter sido confirmado ainda)
            try {
                appCtx?.let { ctx ->
                    LastFmHistoryStore.appendScrobble(
                        ctx,
                        meta.artist,
                        meta.track,
                        meta.album.takeIf { it.isNotBlank() },
                        startedAtMs
                    )
                }
            } catch (_: Exception) { }
            flush()
        }
    }

    /**
     * Ama/desama a faixa no Last.fm (track.love / track.unlove) usando as tags já editadas.
     * Retorna true se o Last.fm aceitou. Sem login/secret, retorna false sem erro.
     */
    suspend fun setLoved(track: Track, loved: Boolean): Boolean {
        val sk = sessionKey ?: return false
        if (!hasSecret) return false
        return withContext(Dispatchers.IO) {
            try {
                val meta = metaFor(track)
                val params = linkedMapOf(
                    "method" to if (loved) "track.love" else "track.unlove",
                    "artist" to meta.artist,
                    "track" to meta.track,
                    "api_key" to LastFmClient.API_KEY,
                    "sk" to sk
                )
                params["api_sig"] = sign(params)
                val (code, json) = post(params)
                if (json?.optInt("error", 0) == 9) invalidateSession()
                val ok = code in 200..299 && (json?.optInt("error", 0) ?: 0) == 0
                if (!ok) {
                    _status.value = json?.optString("message")?.takeIf { it.isNotBlank() }
                        ?.let { "Last.fm: $it" } ?: "Last.fm: falha ao favoritar (erro $code)."
                }
                ok
            } catch (_: Exception) {
                _status.value = "Sem conexão com o Last.fm para favoritar."
                false
            } finally {
                _loveVersion.value = _loveVersion.value + 1
            }
        }
    }

    /** Reenvia os scrobbles pendentes (também usado pelo botão da página). */
    fun flushNow() {
        scope.launch { flush() }
    }

    private suspend fun flush() {
        flushMutex.withLock {
            val sk = sessionKey ?: return@withLock
            while (true) {
                val batch = synchronized(queueLock) { readQueue().take(BATCH) }
                if (batch.isEmpty()) break
                val params = linkedMapOf(
                    "method" to "track.scrobble",
                    "api_key" to LastFmClient.API_KEY,
                    "sk" to sk
                )
                batch.forEachIndexed { i, e ->
                    params["artist[$i]"] = e.artist
                    params["track[$i]"] = e.track
                    params["timestamp[$i]"] = e.ts.toString()
                    if (e.album.isNotBlank()) params["album[$i]"] = e.album
                    if (e.albumArtist.isNotBlank()) params["albumArtist[$i]"] = e.albumArtist
                    if (e.durationSec > 0) params["duration[$i]"] = e.durationSec.toString()
                }
                params["api_sig"] = sign(params)
                val (code, json) = try {
                    post(params)
                } catch (_: IOException) {
                    return@withLock // sem rede: mantém na fila
                }
                val err = json?.optInt("error", 0) ?: 0
                when {
                    code in 200..299 && err == 0 -> removeFirst(batch.size)
                    err == 9 -> { invalidateSession(); return@withLock }
                    err == 11 || err == 16 || err == 29 || code >= 500 || code == 0 ->
                        return@withLock // serviço fora/limite de taxa: tenta depois
                    else -> removeFirst(batch.size) // dados inválidos: descarta p/ não travar a fila
                }
            }
        }
    }

    private fun invalidateSession() {
        val ctx = appCtx ?: return
        sessionKey = null
        _enabled.value = false
        _username.value = null
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_SESSION).remove(KEY_USER).apply()
        _status.value = "Sessão do Last.fm expirou. Entre novamente."
    }

    private fun removeFirst(n: Int) {
        synchronized(queueLock) {
            val q = readQueue()
            saveQueue(q.drop(n))
        }
    }

    // ---------- fila persistida ----------

    private fun readQueue(): List<Entry> {
        val ctx = appCtx ?: return emptyList()
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_QUEUE, null)
            ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Entry(
                    artist = o.optString("a"),
                    track = o.optString("t"),
                    album = o.optString("al"),
                    durationSec = o.optInt("d"),
                    ts = o.optLong("ts"),
                    albumArtist = o.optString("aa")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveQueue(list: List<Entry>) {
        val ctx = appCtx ?: return
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("a", it.artist).put("t", it.track).put("al", it.album)
                    .put("d", it.durationSec).put("ts", it.ts).put("aa", it.albumArtist)
            )
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_QUEUE, arr.toString()).apply()
        _pending.value = list.size
    }

    // ---------- HTTP / assinatura ----------

    private fun sign(params: Map<String, String>): String {
        val sb = StringBuilder()
        params.toSortedMap().forEach { (k, v) ->
            if (k != "format" && k != "callback") sb.append(k).append(v)
        }
        sb.append(apiSecret)
        val digest = MessageDigest.getInstance("MD5").digest(sb.toString().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** POST x-www-form-urlencoded. Retorna (código HTTP, JSON da resposta ou null). */
    private fun post(params: Map<String, String>): Pair<Int, JSONObject?> {
        val body = (params + ("format" to "json")).entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }
        val conn = (URL(LastFmClient.BASE).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("User-Agent", LastFmClient.USER_AGENT)
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = try { if (text.isNotBlank()) JSONObject(text) else null } catch (_: Exception) { null }
            return code to json
        } finally {
            conn.disconnect()
        }
    }
}
