package com.tolete.player.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/**
 * Edições de tags enviadas ao Last.fm e faixas com scrobbling pulado.
 *
 * - As edições ficam em /Lyron/scrobble_edits.json, no mesmo formato do arquivo de exemplo
 *   (lista "simple_edits" com origTrack/origArtist/... e track/artist/album/albumArtist),
 *   trocando o cabeçalho "pano_version" por "lyron_app" + "lyron_version".
 * - A importação aceita esse formato e também arquivos com "pano_version".
 * - Os campos "orig*" funcionam como filtro: só quem tem "hasOrig*" = true é comparado.
 */
object ScrobbleEdits {

    data class Edit(
        val origTrack: String? = null,
        val origArtist: String? = null,
        val origAlbum: String? = null,
        val origAlbumArtist: String? = null,
        val track: String = "",
        val artist: String = "",
        val album: String = "",
        val albumArtist: String = "",
        val continueMatching: Boolean = true
    )

    /** Metadados que vão para o Last.fm. */
    data class Meta(
        val track: String,
        val artist: String,
        val album: String,
        val albumArtist: String
    )

    const val FILE_NAME = "scrobble_edits.json"
    const val APP_NAME = "Lyron Music Player"

    private const val PREFS = "tolete"
    private const val KEY_SKIPPED = "scrobble_skipped_tracks"
    /** Só o primeiro artista (usa separadores da biblioteca). */
    private const val KEY_AUTO_FIRST_ARTIST = "scrobble_auto_first_artist"
    /** Remove textos entre ( ), [ ] no título. */
    private const val KEY_AUTO_STRIP_PARENS = "scrobble_auto_strip_parens"
    /** Mesma chave da biblioteca — separadores de artista. */
    private const val KEY_ARTIST_SEPARATORS = "artist_separators"
    private const val KEY_ARTIST_EXCEPTIONS = "artist_exceptions"

    private val lock = Any()
    private var appCtx: Context? = null
    private var appVersion: String = ""
    private var edits: List<Edit> = emptyList()
    private var skipped: Set<String> = emptySet()
    private var autoFirstArtist: Boolean = false
    private var autoStripParens: Boolean = false

    private val _version = MutableStateFlow(0)
    /** Muda a cada alteração (edições, faixas puladas ou regras automáticas). */
    val version: StateFlow<Int> = _version.asStateFlow()

    private val _autoFirstArtistFlow = MutableStateFlow(false)
    val autoFirstArtistFlow: StateFlow<Boolean> = _autoFirstArtistFlow.asStateFlow()
    private val _autoStripParensFlow = MutableStateFlow(false)
    val autoStripParensFlow: StateFlow<Boolean> = _autoStripParensFlow.asStateFlow()

    fun init(context: Context) {
        synchronized(lock) {
            if (appCtx != null) return
            val ctx = context.applicationContext
            appCtx = ctx
            appVersion = try {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty()
            } catch (_: Exception) { "" }
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            skipped = prefs.getStringSet(KEY_SKIPPED, emptySet())?.toSet().orEmpty()
            autoFirstArtist = prefs.getBoolean(KEY_AUTO_FIRST_ARTIST, false)
            autoStripParens = prefs.getBoolean(KEY_AUTO_STRIP_PARENS, false)
            _autoFirstArtistFlow.value = autoFirstArtist
            _autoStripParensFlow.value = autoStripParens
            edits = try {
                val f = file()
                if (f.exists()) parse(f.readText()) else emptyList()
            } catch (_: Exception) { emptyList() }
        }
    }

    fun isAutoFirstArtist(): Boolean = synchronized(lock) { autoFirstArtist }
    fun isAutoStripParens(): Boolean = synchronized(lock) { autoStripParens }

    fun setAutoFirstArtist(enabled: Boolean) {
        val ctx = appCtx ?: return
        synchronized(lock) {
            autoFirstArtist = enabled
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_AUTO_FIRST_ARTIST, enabled).apply()
        }
        _autoFirstArtistFlow.value = enabled
        _version.value = _version.value + 1
    }

    fun setAutoStripParens(enabled: Boolean) {
        val ctx = appCtx ?: return
        synchronized(lock) {
            autoStripParens = enabled
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_AUTO_STRIP_PARENS, enabled).apply()
        }
        _autoStripParensFlow.value = enabled
        _version.value = _version.value + 1
    }

    private fun file() = File(LyronStorage.root, FILE_NAME)

    private fun norm(s: String) = s.trim().lowercase()

    /** Chave estável de uma faixa, independente de ID da biblioteca. */
    fun key(artist: String, title: String) = norm(artist) + "\u0001" + norm(title)

    // ---------- pular scrobbling ----------

    fun isSkipped(artist: String, title: String): Boolean =
        synchronized(lock) { key(artist, title) in skipped }

    /** Alterna e salva a preferência. Retorna o novo estado (true = pulado). */
    fun toggleSkipped(artist: String, title: String): Boolean {
        val ctx = appCtx ?: return false
        val k = key(artist, title)
        val now = synchronized(lock) {
            skipped = if (k in skipped) skipped - k else skipped + k
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putStringSet(KEY_SKIPPED, skipped.toSet()).apply()
            k in skipped
        }
        _version.value = _version.value + 1
        return now
    }

    // ---------- aplicar edições ----------

    /**
     * Aplica as edições salvas aos metadados originais da faixa.
     * [original] é a faixa como está no arquivo; o resultado é o que vai para o Last.fm.
     *
     * Ordem:
     * 1) Edições manuais salvas em scrobble_edits.json
     * 2) Se NÃO houver edição manual para a faixa, regras automáticas (se ativadas):
     *    - só o primeiro artista (separadores da biblioteca)
     *    - remover textos entre ( ) e [ ] em título, artista e álbum
     * Faixas com edição manual nunca são alteradas pelas regras automáticas.
     * Regras automáticas não gravam edição permanente — a faixa continua editável.
     */
    fun apply(original: Meta): Meta {
        val (list, firstArtist, stripParens) = synchronized(lock) {
            Triple(edits, autoFirstArtist, autoStripParens)
        }
        var cur = original
        var matchedManual = false
        for (e in list) {
            if (!matches(e, cur)) continue
            matchedManual = true
            cur = Meta(
                track = e.track.ifBlank { cur.track },
                artist = e.artist.ifBlank { cur.artist },
                album = e.album.ifBlank { cur.album },
                albumArtist = e.albumArtist.ifBlank { cur.albumArtist }
            )
            if (!e.continueMatching) break
        }
        // Edição manual tem prioridade absoluta
        if (matchedManual) return cur

        if (firstArtist) {
            cur = cur.copy(artist = firstArtistOnly(cur.artist))
        }
        if (stripParens) {
            cur = cur.copy(
                track = stripParentheticals(cur.track),
                artist = stripParentheticals(cur.artist),
                album = stripParentheticals(cur.album),
                albumArtist = stripParentheticals(cur.albumArtist)
            )
        }
        return cur
    }

    /**
     * Caminho inverso de [apply]: dado o que está no Last.fm ([artist]/[track]), devolve o
     * título/artista/álbum originais quando existe uma edição manual que produz exatamente isso.
     * Os campos "orig*" ficam salvos normalizados (minúsculos); por isso o ideal é usar antes o
     * índice da biblioteca (ver LastFmDashboard), que devolve as tags reais do arquivo.
     * Retorna null se nenhuma edição manual leva a esse nome.
     */
    fun reverseManual(artist: String, track: String): Meta? = synchronized(lock) {
        val a = norm(artist)
        val t = norm(track)
        edits.firstOrNull { e ->
            e.origTrack != null &&
                norm(e.track.ifBlank { e.origTrack }) == t &&
                (e.origArtist == null || norm(e.artist.ifBlank { e.origArtist }) == a)
        }?.let { e ->
            Meta(
                track = e.origTrack.orEmpty().ifBlank { track },
                // reverso sempre devolve um único artista (o primeiro, pelos separadores da biblioteca)
                artist = firstArtist(e.origArtist ?: artist),
                album = e.origAlbum.orEmpty(),
                albumArtist = firstArtist(e.origAlbumArtist.orEmpty())
            )
        }
    }

    /**
     * Só o primeiro artista de um valor de tag ("A; B" → "A"), usando os separadores e as
     * exceções configurados na Biblioteca. Usado pelo editor reverso (dashboard/retrospectiva).
     */
    fun firstArtist(raw: String): String = firstArtistOnly(raw)

    /** True se existe edição manual salva para esta faixa (orig track+artist). */
    fun hasManualEdit(original: Meta): Boolean = findFor(original) != null

    private fun firstArtistOnly(artist: String): String {
        val raw = artist.trim()
        if (raw.isEmpty()) return raw
        val prefs = appCtx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seps = prefs?.getString(KEY_ARTIST_SEPARATORS, null) ?: "; /"
        val exceptions = prefs?.getString(KEY_ARTIST_EXCEPTIONS, null).orEmpty()
        val parts = TagSplitter(seps, exceptions).split(raw)
        return parts.firstOrNull()?.trim().orEmpty().ifBlank { raw }
    }

    /** Remove trechos entre ( ) e [ ] (ex.: "Música (Ao Vivo)" → "Música"). Aplica a título, artista e álbum. */
    private fun stripParentheticals(title: String): String {
        var s = title
        // Repete para aninhados simples / múltiplos sufixos
        repeat(8) {
            val next = s
                .replace(Regex("""\s*[\(\[].*?[\)\]]"""), "")
                .replace(Regex("""\s{2,}"""), " ")
                .trim()
            if (next == s) return s.ifBlank { title.trim() }
            s = next
        }
        return s.ifBlank { title.trim() }
    }

    private fun matches(e: Edit, m: Meta): Boolean {
        if (e.origTrack != null && norm(e.origTrack) != norm(m.track)) return false
        if (e.origArtist != null && norm(e.origArtist) != norm(m.artist)) return false
        if (e.origAlbum != null && norm(e.origAlbum) != norm(m.album)) return false
        if (e.origAlbumArtist != null && norm(e.origAlbumArtist) != norm(m.albumArtist)) return false
        // sem nenhum filtro a edição valeria para tudo: ignora
        return e.origTrack != null || e.origArtist != null ||
            e.origAlbum != null || e.origAlbumArtist != null
    }

    /** Edição já salva para esta faixa (para preencher o diálogo), ou null. */
    fun findFor(original: Meta): Edit? = synchronized(lock) {
        edits.firstOrNull {
            it.origTrack != null && norm(it.origTrack) == norm(original.track) &&
                (it.origArtist == null || norm(it.origArtist) == norm(original.artist))
        }
    }

    /**
     * Salva uma edição criada pelo usuário para a faixa [original], substituindo a anterior
     * da mesma faixa, e grava o arquivo em /Lyron.
     */
    fun saveEdit(original: Meta, edited: Meta): Boolean {
        val newEdit = Edit(
            origTrack = original.track.trim(),
            origArtist = original.artist.trim(),
            origAlbum = original.album.takeIf { it.isNotBlank() }?.trim(),
            origAlbumArtist = null,
            track = edited.track.trim(),
            artist = edited.artist.trim(),
            album = edited.album.trim(),
            albumArtist = edited.albumArtist.trim(),
            continueMatching = true
        )
        synchronized(lock) {
            edits = edits.filterNot { sameTarget(it, newEdit) } + newEdit
        }
        _version.value = _version.value + 1
        return writeFile()
    }

    /** Remove a edição desta faixa (volta às tags originais). */
    fun removeEditFor(original: Meta): Boolean {
        synchronized(lock) {
            edits = edits.filterNot {
                it.origTrack?.let(::norm) == norm(original.track) &&
                    it.origArtist?.let(::norm) == norm(original.artist)
            }
        }
        _version.value = _version.value + 1
        return writeFile()
    }

    fun count(): Int = synchronized(lock) { edits.size }

    private fun sameTarget(a: Edit, b: Edit) =
        a.origTrack?.let(::norm) == b.origTrack?.let(::norm) &&
            a.origArtist?.let(::norm) == b.origArtist?.let(::norm)

    // ---------- gerenciamento (tela Scrobble › Gerenciar edições) ----------

    /** Cópia da lista de edições salvas, na ordem do arquivo. */
    fun list(): List<Edit> = synchronized(lock) { edits.toList() }

    /** Troca [old] por [new] (mesma posição) e grava o arquivo. */
    fun replace(old: Edit, new: Edit): Boolean {
        var found = false
        synchronized(lock) {
            edits = edits.map { if (!found && it == old) { found = true; new } else it }
        }
        if (!found) return false
        _version.value = _version.value + 1
        return writeFile()
    }

    /** Apaga [edit]; a faixa volta às tags originais no próximo scrobble. */
    fun remove(edit: Edit): Boolean {
        var found = false
        synchronized(lock) {
            edits = edits.filter { if (!found && it == edit) { found = true; false } else true }
        }
        if (!found) return false
        _version.value = _version.value + 1
        return writeFile()
    }

    // ---------- arquivo ----------

    private fun writeFile(): Boolean {
        val snapshot = synchronized(lock) { edits }
        return try {
            val f = file()
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(toJson(snapshot).toString(4))
            if (!tmp.renameTo(f)) {
                f.writeText(toJson(snapshot).toString(4))
                tmp.delete()
            }
            true
        } catch (_: Exception) { false }
    }

    private fun toJson(list: List<Edit>): JSONObject {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject()
                    .put("hasOrigTrack", e.origTrack != null)
                    .put("origTrack", e.origTrack.orEmpty())
                    .put("hasOrigArtist", e.origArtist != null)
                    .put("origArtist", e.origArtist.orEmpty())
                    .put("hasOrigAlbum", e.origAlbum != null)
                    .put("origAlbum", e.origAlbum.orEmpty())
                    .put("hasOrigAlbumArtist", e.origAlbumArtist != null)
                    .put("origAlbumArtist", e.origAlbumArtist.orEmpty())
                    .put("track", e.track)
                    .put("album", e.album)
                    .put("artist", e.artist)
                    .put("albumArtist", e.albumArtist)
                    .put("continueMatching", e.continueMatching)
            )
        }
        return JSONObject()
            .put("lyron_app", APP_NAME)
            .put("lyron_version", appVersion)
            .put("simple_edits", arr)
    }

    /**
     * Lê um JSON de edições. Tolera arquivo cortado no fim (como o de exemplo, que termina
     * sem fechar o array/objeto) tentando fechar as chaves que faltam.
     */
    private fun parse(text: String): List<Edit> {
        val root = try {
            JSONObject(text)
        } catch (_: JSONException) {
            repair(text) ?: throw JSONException("Arquivo de edições inválido")
        }
        val arr = root.optJSONArray("simple_edits") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            fun orig(has: String, key: String): String? =
                if (o.optBoolean(has, false)) o.optString(key, "") else null
            Edit(
                origTrack = orig("hasOrigTrack", "origTrack"),
                origArtist = orig("hasOrigArtist", "origArtist"),
                origAlbum = orig("hasOrigAlbum", "origAlbum"),
                origAlbumArtist = orig("hasOrigAlbumArtist", "origAlbumArtist"),
                track = o.optString("track", ""),
                artist = o.optString("artist", ""),
                album = o.optString("album", ""),
                albumArtist = o.optString("albumArtist", ""),
                continueMatching = o.optBoolean("continueMatching", true)
            )
        }
    }

    private fun repair(text: String): JSONObject? {
        val t = text.trimEnd().trimEnd(',')
        for (suffix in listOf("]}", "\n]\n}", "}]}")) {
            try { return JSONObject(t + suffix) } catch (_: JSONException) { }
        }
        return null
    }

    /**
     * Importa edições de um arquivo (formato Lyron ou Pano). Mescla com as existentes:
     * uma edição importada substitui a local da mesma faixa. Retorna a quantidade
     * importada, ou -1 se o arquivo for inválido.
     */
    fun importFrom(text: String): Int {
        val incoming = try { parse(text) } catch (_: Exception) { return -1 }
        if (incoming.isEmpty()) return 0
        synchronized(lock) {
            val kept = edits.filterNot { old -> incoming.any { sameTarget(it, old) && norm(it.origAlbum.orEmpty()) == norm(old.origAlbum.orEmpty()) } }
            edits = kept + incoming
        }
        _version.value = _version.value + 1
        writeFile()
        return incoming.size
    }

    /** Exporta o JSON atual (para "Salvar como…"). */
    fun exportText(): String = toJson(synchronized(lock) { edits }).toString(4)
}
