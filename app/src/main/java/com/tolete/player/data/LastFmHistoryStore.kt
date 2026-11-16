package com.tolete.player.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tolete.player.MainActivity
import com.tolete.player.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

/**
 * Espelho local do histórico de scrobbles do Last.fm em
 * `/storage/emulated/0/Lyron/lastfm history.csv`.
 *
 * Usado para 1ª/última reprodução e lista completa sem varrer a API no info card.
 */
object LastFmHistoryStore {

    private const val CHANNEL_ID = "lyron_lastfm_history_sync"
    private const val NOTIF_ID = 7066
    private const val PREFS = "tolete"
    private const val KEY_LAST_UTS = "lastfm_history_last_uts"
    private const val KEY_LAST_SYNC_MS = "lastfm_history_last_sync_ms"
    private const val KEY_TOTAL = "lastfm_history_total"

    private val CSV_HEADER = "epochMs,artist,track,album"

    data class SyncProgress(
        val running: Boolean = false,
        val page: Int = 0,
        val totalPages: Int = 0,
        val imported: Int = 0,
        val message: String = ""
    )

    private val _progress = MutableStateFlow(SyncProgress())
    val progress: StateFlow<SyncProgress> = _progress.asStateFlow()

    private val running = AtomicBoolean(false)

    private fun dir(): File {
        val d = File("/storage/emulated/0/Lyron")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun csvFile(): File {
        val d = dir()
        val spaced = File(d, "lastfm history.csv")
        if (spaced.exists()) return spaced
        val underscored = File(d, "lastfm_history.csv")
        return if (underscored.exists()) underscored else spaced
    }

    fun normalizeKey(artist: String, track: String): String {
        fun n(s: String) = Normalizer.normalize(s.trim().replace(Regex("\\s+"), " "), Normalizer.Form.NFC)
            .lowercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() || it.isWhitespace() }
            .replace(Regex("\\s+"), " ")
            .trim()
        return n(artist) + "\u0000" + n(track)
    }

    /** Timestamps (ms) de uma faixa, mais recentes primeiro. */
    suspend fun playTimes(artist: String, track: String): List<Long> = withContext(Dispatchers.IO) {
        val key = normalizeKey(artist, track)
        if (key == "\u0000") return@withContext emptyList()
        val file = csvFile()
        if (!file.exists() || file.length() == 0L) return@withContext emptyList()
        val out = ArrayList<Long>()
        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.drop(1).forEach { line ->
                if (line.isBlank()) return@forEach
                val cells = parseCsv(line)
                if (cells.size < 3) return@forEach
                val ts = cells[0].toLongOrNull() ?: return@forEach
                val a = cells.getOrNull(1).orEmpty()
                val t = cells.getOrNull(2).orEmpty()
                if (normalizeKey(a, t) == key) out.add(ts)
            }
        }
        out.sortDescending()
        out
    }

    /** Quantidade de scrobbles no CSV local (linhas sem o cabeçalho). */
    fun scrobbleCount(): Int {
        val file = csvFile()
        if (!file.exists() || file.length() == 0L) return 0
        return try {
            file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.drop(1).count { it.isNotBlank() }
            }
        } catch (_: Exception) { 0 }
    }

    /**
     * Fatia do CSV local: [limit] scrobbles a partir de [offset] (0 = mais recente).
     * O arquivo guarda os mais recentes primeiro.
     */
    fun scrobblePage(offset: Int, limit: Int): List<LastFmClient.ScrobbleItem> {
        val file = csvFile()
        if (limit <= 0 || !file.exists() || file.length() == 0L) return emptyList()
        val out = ArrayList<LastFmClient.ScrobbleItem>(limit)
        try {
            file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                var idx = 0
                for (line in lines.drop(1)) {
                    if (line.isBlank()) continue
                    if (idx++ < offset) continue
                    if (out.size >= limit) break
                    val cells = parseCsv(line)
                    if (cells.size < 3) continue
                    val ts = cells[0].toLongOrNull() ?: continue
                    out.add(
                        LastFmClient.ScrobbleItem(
                            title = cells[2],
                            artist = cells[1],
                            album = cells.getOrNull(3)?.takeIf { it.isNotBlank() },
                            dateMs = ts,
                            nowPlaying = false
                        )
                    )
                }
            }
        } catch (_: Exception) { }
        return out
    }

    fun recentScrobbles(limit: Int): List<LastFmClient.ScrobbleItem> = scrobblePage(0, limit)

    fun firstAndLast(artist: String, track: String): Pair<Long?, Long?> {
        // Sync helper — prefer suspend playTimes when possible
        val file = csvFile()
        if (!file.exists() || file.length() == 0L) return null to null
        val key = normalizeKey(artist, track)
        var first: Long? = null
        var last: Long? = null
        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.drop(1).forEach { line ->
                if (line.isBlank()) return@forEach
                val cells = parseCsv(line)
                if (cells.size < 3) return@forEach
                val ts = cells[0].toLongOrNull() ?: return@forEach
                val a = cells.getOrNull(1).orEmpty()
                val t = cells.getOrNull(2).orEmpty()
                if (normalizeKey(a, t) != key) return@forEach
                if (first == null || ts < first!!) first = ts
                if (last == null || ts > last!!) last = ts
            }
        }
        return first to last
    }

    fun appendScrobble(context: Context, artist: String, track: String, album: String?, epochMs: Long) {
        if (artist.isBlank() || track.isBlank() || epochMs <= 0L) return
        try {
            val file = csvFile()
            synchronized(this) {
                val line = listOf(
                    epochMs.toString(),
                    artist,
                    track,
                    album.orEmpty()
                ).joinToString(",") { csvEscape(it) }
                if (!file.exists() || file.length() == 0L) {
                    file.writeText(CSV_HEADER + "\n" + line + "\n", Charsets.UTF_8)
                } else {
                    // O arquivo é "mais recente primeiro": o novo scrobble entra logo após o cabeçalho.
                    val tmp = File(file.parentFile, file.name + ".tmp")
                    tmp.bufferedWriter(Charsets.UTF_8).use { w ->
                        w.append(CSV_HEADER).append('\n')
                        w.append(line).append('\n')
                        file.bufferedReader(Charsets.UTF_8).useLines { ls ->
                            for (l in ls.drop(1)) if (l.isNotBlank()) w.append(l).append('\n')
                        }
                    }
                    if (!tmp.renameTo(file)) {
                        tmp.copyTo(file, overwrite = true)
                        tmp.delete()
                    }
                }
            }
            // Não avança KEY_LAST_UTS: o cursor só anda no sync com a API, senão scrobbles feitos
            // em outros dispositivos antes deste ficariam de fora do download incremental.
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            p.edit().putInt(KEY_TOTAL, p.getInt(KEY_TOTAL, 0) + 1).apply()
        } catch (_: Exception) { }
    }

    fun stats(context: Context): Pair<Long, Int> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getLong(KEY_LAST_SYNC_MS, 0L) to p.getInt(KEY_TOTAL, 0)
    }

    /**
     * Baixa o histórico (completo ou incremental desde o último uts).
     * [full] = true ignora o cursor e reescreve o arquivo.
     */
    suspend fun sync(context: Context, user: String, full: Boolean = false): Boolean {
        if (user.isBlank()) return false
        if (!running.compareAndSet(false, true)) return false
        val app = context.applicationContext
        ensureChannel(app)
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val csvMissing = csvFile().let { !it.exists() || it.length() == 0L }
            val fromUts = if (full || csvMissing) null else prefs.getLong(KEY_LAST_UTS, 0L).takeIf { it > 0L }
            _progress.value = SyncProgress(true, 0, 0, 0, "Iniciando…")
            notify(nm, app, "Sincronizando Last.fm…", "Preparando", 0, 0, ongoing = true)

            val collected = ArrayList<Quad>(4096)
            var page = 1
            var totalPages = 1
            var newestUts = fromUts ?: 0L
            var imported = 0

            while (coroutineContext.isActive) {
                val r = LastFmClient.getScrobblePage(
                    user = user,
                    page = page,
                    limit = 200,
                    fromSec = fromUts?.let { it + 1 }
                ) ?: break
                totalPages = r.totalPages.coerceAtLeast(1)
                for (item in r.items) {
                    if (item.nowPlaying) continue
                    val ts = item.dateMs ?: continue
                    val uts = ts / 1000L
                    if (uts > newestUts) newestUts = uts
                    collected.add(Quad(ts, item.artist, item.title, item.album.orEmpty()))
                    imported++
                }
                _progress.value = SyncProgress(
                    true, page, totalPages, imported,
                    "Página $page de $totalPages"
                )
                notify(
                    nm, app,
                    "Sincronizando Last.fm…",
                    "Página $page/$totalPages · $imported scrobbles",
                    page, totalPages,
                    ongoing = true
                )
                if (page >= totalPages) break
                page++
                // Pausa leve para não estourar rate limit
                kotlinx.coroutines.delay(120)
            }

            if (!coroutineContext.isActive) {
                _progress.value = SyncProgress(false, message = "Cancelado")
                nm.cancel(NOTIF_ID)
                return false
            }

            withContext(Dispatchers.IO) {
                synchronized(this@LastFmHistoryStore) {
                    val file = csvFile()
                    val merged: List<Quad> = if (full || fromUts == null) {
                        collected.sortedByDescending { it.ts }
                    } else {
                        // Incremental: junta novos + existentes, remove duplicados (inclusive os que o
                        // scrobbler gravou localmente) e mantém mais recente primeiro.
                        val seen = HashSet<String>()
                        val out = ArrayList<Quad>(collected.size + 4096)
                        for (q in collected) if (seen.add(dedupKey(q))) out.add(q)
                        for (q in readAllQuads(file)) if (seen.add(dedupKey(q))) out.add(q)
                        out.sortByDescending { it.ts }
                        out
                    }
                    BufferedWriter(FileWriter(file, false)).use { w ->
                        w.append(CSV_HEADER).append('\n')
                        for (q in merged) w.append(csvLine(q)).append('\n')
                    }
                    val totalLines = file.bufferedReader().use { it.lineSequence().drop(1).count { l -> l.isNotBlank() } }
                    prefs.edit()
                        .putLong(KEY_LAST_UTS, newestUts)
                        .putLong(KEY_LAST_SYNC_MS, System.currentTimeMillis())
                        .putInt(KEY_TOTAL, totalLines)
                        .apply()
                }
            }

            _progress.value = SyncProgress(false, totalPages, totalPages, imported, "Concluído · $imported novos")
            notify(nm, app, "Histórico Last.fm atualizado", "$imported scrobbles processados", 0, 0, ongoing = false)
            return true
        } catch (e: Exception) {
            _progress.value = SyncProgress(false, message = "Erro: ${e.message}")
            notify(nm, app, "Falha no sync Last.fm", e.message ?: "erro", 0, 0, ongoing = false)
            return false
        } finally {
            running.set(false)
        }
    }

    private fun dedupKey(q: Quad) = "${q.ts / 1000L}|${normalizeKey(q.artist, q.track)}"

    private fun readAllQuads(file: File): List<Quad> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        val out = ArrayList<Quad>(4096)
        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines.drop(1)) {
                if (line.isBlank()) continue
                val c = parseCsv(line)
                if (c.size < 3) continue
                val ts = c[0].toLongOrNull() ?: continue
                out.add(Quad(ts, c[1], c[2], c.getOrNull(3).orEmpty()))
            }
        }
        return out
    }

    /** Roda o sync incremental; se já houver um em andamento, espera ele terminar. */
    suspend fun syncOrAwait(context: Context, user: String): Boolean {
        if (running.get()) {
            _progress.first { !it.running }
            return true
        }
        return sync(context, user, full = false)
    }

    private data class Quad(val ts: Long, val artist: String, val track: String, val album: String)

    private fun csvLine(q: Quad) =
        listOf(q.ts.toString(), q.artist, q.track, q.album).joinToString(",") { csvEscape(it) }

    private fun csvEscape(s: String): String {
        val needs = s.contains(',') || s.contains('"') || s.contains('\n')
        return if (!needs) s else "\"" + s.replace("\"", "\"\"") + "\""
    }

    private fun parseCsv(line: String): List<String> {
        val out = ArrayList<String>(4)
        val sb = StringBuilder()
        var i = 0
        var inQ = false
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQ && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"'); i++
                    } else inQ = !inQ
                }
                c == ',' && !inQ -> {
                    out.add(sb.toString()); sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Histórico Last.fm",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progresso do download do histórico de scrobbles"
                setShowBadge(false)
            }
        )
    }

    private fun notify(
        nm: NotificationManager,
        ctx: Context,
        title: String,
        text: String,
        progress: Int,
        max: Int,
        ongoing: Boolean
    ) {
        val pi = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val b = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (ongoing && max > 0) {
            b.setProgress(max, progress.coerceIn(0, max), false)
        } else if (ongoing) {
            b.setProgress(0, 0, true)
        }
        nm.notify(NOTIF_ID, b.build())
        if (!ongoing) {
            // deixa a notificação de conclusão sumir sozinha depois
        }
    }
}
