package com.tolete.player.data

import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

/** Busca somente leitura no repositório público de letras do Lyron. */
internal object LyronLyricsRepository {

    private const val INDEX_URL =
        "https://raw.githubusercontent.com/PGCR-gui/Lyrics_lyron/main/index.json"
    private const val RAW_FILE_PREFIX =
        "https://raw.githubusercontent.com/PGCR-gui/Lyrics_lyron/main/"
    private const val MAX_CANDIDATES = 5

    private val wordTimestampRegex = Regex("""<\d{1,2}:\d{2}(?:\.\d{1,3})?>""")

    private data class IndexedLyric(
        val path: String,
        val artist: String,
        val title: String,
        val matchScore: Int,
        val syncedBy: String? = null
    )

    /** Chamado na thread de IO por [LyricsSearch]. */
    fun search(trackName: String, artistName: String): List<LyricsSearchResult> {
        val titleQuery = trackName.trim()
        val artistQuery = artistName.trim()
        if (titleQuery.isBlank()) return emptyList()

        val indexText = LyricsHttp.get(INDEX_URL) ?: return emptyList()
        val files = parseFiles(indexText)
        if (files.isEmpty()) return emptyList()

        val candidates = files.mapNotNull { parseCandidate(it, titleQuery, artistQuery) }
            .sortedWith(compareBy<IndexedLyric> { it.matchScore }.thenBy { it.path })
            .take(MAX_CANDIDATES)

        return candidates.mapNotNull { item ->
            val rawLyrics = LyricsHttp.get(rawFileUrl(item.path))?.trim()?.takeIf { it.isNotBlank() }
                ?.let { LyricsFormat.unescapeHtml(it) }
                ?: return@mapNotNull null
            val lines = LyricsExtractor.parseLyricsContent(rawLyrics)
            if (lines.isEmpty()) return@mapNotNull null

            val type = when {
                wordTimestampRegex.containsMatchIn(rawLyrics) -> LyricsType.WORD
                lines.any { it.timeMs >= 0L } -> LyricsType.SYNCED
                else -> LyricsType.PLAIN
            }
            LyricsSearchResult(
                id = "lyron:${item.path.lowercase(Locale.ROOT)}",
                source = LyricsSource.LYRON_REPOSITORY,
                trackName = item.title,
                artistName = item.artist,
                albumName = "",
                durationSec = 0,
                type = type,
                lines = lines,
                // Preserva os marcadores ELRC+ exatamente como foram publicados.
                lrcText = rawLyrics,
                syncedBy = item.syncedBy
            )
        }
    }

    private data class IndexEntry(val path: String, val syncedBy: String?)

    private fun parseFiles(rawIndex: String): List<IndexEntry> {
        return try {
            // Tolera vírgulas finais para que um erro comum de edição manual não derrube a busca.
            val validJson = Regex(",\\s*([}\\]])").replace(rawIndex) { it.groupValues[1] }
            val root = JSONObject(validJson)
            val files = root.optJSONArray("files") ?: return emptyList()
            (0 until files.length()).mapNotNull { i ->
                val item = files.opt(i) ?: return@mapNotNull null
                when (item) {
                    is String -> {
                        val path = item.trim()
                        if (isSafeLrcPath(path)) IndexEntry(path, null) else null
                    }
                    is JSONObject -> {
                        val path = item.optString("path").ifBlank {
                            item.optString("file")
                        }.trim()
                        if (!isSafeLrcPath(path)) return@mapNotNull null
                        val synced = sequenceOf(
                            item.optString("syncedBy"),
                            item.optString("synced_by"),
                            item.optString("syncBy"),
                            item.optString("sync_by"),
                            item.optString("uploadedBy"),
                            item.optString("uploaded_by"),
                            item.optString("by")
                        ).map { it.trim() }.firstOrNull { it.isNotBlank() }
                        IndexEntry(path, synced)
                    }
                    else -> null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isSafeLrcPath(path: String): Boolean =
        path.isNotBlank() &&
            !path.startsWith('/') &&
            !path.contains("..") &&
            !path.contains('\\') &&
            !path.contains("://") &&
            path.endsWith(".lrc", ignoreCase = true)

    private fun parseCandidate(entry: IndexEntry, titleQuery: String, artistQuery: String): IndexedLyric? {
        val path = entry.path
        val fileName = path.substringAfterLast('/')
        val stem = fileName.substringBeforeLast('.', missingDelimiterValue = "")
        val separator = stem.indexOf(" - ")
        if (separator <= 0 || separator + 3 >= stem.length) return null

        val artist = stem.substring(0, separator).trim()
        val title = stem.substring(separator + 3).trim()
        val titleScore = matchScore(titleQuery, title) ?: return null
        val artistScore = if (artistQuery.isBlank()) 0 else matchScore(artistQuery, artist) ?: return null
        return IndexedLyric(path, artist, title, titleScore + artistScore, entry.syncedBy)
    }

    /** Igualdade normalizada é preferida; contém permite nomes com transliteração entre parênteses. */
    private fun matchScore(query: String, candidate: String): Int? {
        val q = normalize(query)
        val c = normalize(candidate)
        if (q.isBlank() || c.isBlank()) return null
        if (q == c) return 0
        if (q.length < 3 || c.length < 3) return null
        return if (c.contains(q) || q.contains(c)) 1 else null
    }

    private fun normalize(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() && it.isLetterOrDigit() }

    private fun rawFileUrl(path: String): String = RAW_FILE_PREFIX + path
        .split('/')
        .joinToString("/") { segment ->
            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
}
