package com.tolete.player.data

import java.io.File

/**
 * Dados persistentes em /storage/emulated/0/Lyron — fonte de verdade do app.
 */
object LyronStorage {
    val root: File
        get() = File("/storage/emulated/0/Lyron").also { it.mkdirs() }

    val artistPhotos: File
        get() = File(root, "artist_photos").also { ensureDir(it) }

    val artistInfos: File
        get() = File(root, "artist_infos").also { it.mkdirs() }

    val albumInfos: File
        get() = File(root, "album_infos").also { it.mkdirs() }

    val playlistsDir: File
        get() = File(root, "playlists").also { it.mkdirs() }

    val favoritesFile: File
        get() = File(root, "favorites.json")

    val playbackHistory: File
        get() = File(root, "playback history.csv")

    private fun ensureDir(dir: File) {
        dir.mkdirs()
        val nomedia = File(dir, ".nomedia")
        if (!nomedia.exists()) {
            try { nomedia.writeText("") } catch (_: Exception) {}
        }
    }

    fun sanitizeFileName(name: String): String {
        val cleaned = name.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
        return cleaned.take(120).ifBlank { "unknown" }
    }

    fun artistPhotoFile(artistName: String): File =
        File(artistPhotos, sanitizeFileName(artistName) + ".jpg")

    fun artistInfoFile(artistName: String): File =
        File(artistInfos, sanitizeFileName(artistName) + ".txt")

    fun albumInfoFile(albumName: String, artistName: String? = null): File {
        val base = sanitizeFileName(albumName)
        val name = if (!artistName.isNullOrBlank()) {
            "$base - ${sanitizeFileName(artistName)}"
        } else base
        return File(albumInfos, "$name.txt")
    }

    fun playlistFile(playlistId: String, playlistName: String): File {
        val safe = sanitizeFileName(playlistName)
        return File(playlistsDir, "${sanitizeFileName(playlistId)}_$safe.json")
    }
}
