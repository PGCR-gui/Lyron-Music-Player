package com.tolete.player.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Guarda a biblioteca escaneada em files/library.json para o app abrir instantaneamente,
 * sem reler todas as pastas. Se o arquivo não existir, estiver corrompido ou tiver uma
 * versão antiga do formato, [load] devolve null e o ViewModel faz um novo scan.
 */
class LibraryCache(context: Context) {

    private val file = File(context.filesDir, "library.json")

    suspend fun load(): List<Track>? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        try {
            val root = JSONObject(file.readText())
            if (root.optInt("v") != VERSION) return@withContext null
            val arr = root.getJSONArray("tracks")
            List(arr.length()) { arr.getJSONObject(it).toTrack() }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun save(tracks: List<Track>) = withContext(Dispatchers.IO) {
        try {
            val arr = JSONArray()
            tracks.forEach { arr.put(it.toJson()) }
            val root = JSONObject().put("v", VERSION).put("tracks", arr)
            // grava num temporário e renomeia: nunca deixa o cache pela metade
            val tmp = File(file.parentFile, "library.json.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            // cache é só otimização; falhar aqui não pode derrubar o app
        }
    }

    private fun Track.toJson() = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("artist", artist)
        put("album", album)
        put("genre", genre)
        put("duration", duration)
        put("uri", uri.toString())
        put("path", path)
        put("folder", folder)
        year?.let { put("year", it) }
        trackNumber?.let { put("trackNumber", it) }
        discNumber?.let { put("discNumber", it) }
        if (composer.isNotBlank()) put("composer", composer)
        artPath?.let { put("artPath", it) }
        put("sourceTree", sourceTree)
        codec?.let { put("codec", it) }
        put("bitrate", bitrate)
        put("sampleRate", sampleRate)
        put("channels", channels)
        put("fileSize", fileSize)
        put("lastModified", lastModified)
        put("bitDepth", bitDepth)
    }

    private fun JSONObject.toTrack() = Track(
        id = getString("id"),
        title = getString("title"),
        artist = getString("artist"),
        album = getString("album"),
        genre = getString("genre"),
        duration = getLong("duration"),
        uri = Uri.parse(getString("uri")),
        path = getString("path"),
        folder = getString("folder"),
        year = if (has("year")) getInt("year") else null,
        trackNumber = if (has("trackNumber")) getInt("trackNumber") else null,
        discNumber = if (has("discNumber")) getInt("discNumber") else null,
        composer = optString("composer", ""),
        artPath = if (has("artPath")) getString("artPath") else null,
        sourceTree = optString("sourceTree", ""),
        codec = if (has("codec")) getString("codec") else null,
        bitrate = optInt("bitrate"),
        sampleRate = optInt("sampleRate"),
        channels = optInt("channels"),
        fileSize = optLong("fileSize"),
        lastModified = optLong("lastModified"),
        bitDepth = optInt("bitDepth")
    )

    private companion object {
        const val VERSION = 2
    }
}
