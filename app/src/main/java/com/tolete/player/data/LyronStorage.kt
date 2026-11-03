package com.tolete.player.data

import android.content.Context
import java.io.File

/**
 * Dados persistentes do app.
 * Suporta três modos: Padrão (/storage/emulated/0/Lyron), Interno (filesDir/Lyron)
 * e Personalizado (pasta escolhida pelo usuário).
 */
object LyronStorage {
    enum class Mode {
        DEFAULT,   // /storage/emulated/0/Lyron
        INTERNAL,  // context.filesDir/Lyron
        CUSTOM     // pasta personalizada
    }

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var currentMode: Mode = Mode.DEFAULT

    @Volatile
    private var customRootPath: String? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences("tolete", Context.MODE_PRIVATE)
        currentMode = try {
            Mode.valueOf(prefs.getString(KEY_DATA_MODE, Mode.DEFAULT.name) ?: Mode.DEFAULT.name)
        } catch (_: Exception) {
            Mode.DEFAULT
        }
        customRootPath = prefs.getString(KEY_CUSTOM_ROOT, null)
    }

    fun getMode(): Mode = currentMode

    fun getCustomRootPath(): String? = customRootPath

    fun setMode(context: Context, mode: Mode, customPath: String? = null) {
        currentMode = mode
        if (mode == Mode.CUSTOM && !customPath.isNullOrBlank()) {
            customRootPath = customPath
        }
        context.getSharedPreferences("tolete", Context.MODE_PRIVATE).edit()
            .putString(KEY_DATA_MODE, mode.name)
            .apply {
                if (mode == Mode.CUSTOM && !customPath.isNullOrBlank()) {
                    putString(KEY_CUSTOM_ROOT, customPath)
                }
            }
            .apply()
        // Garante pastas
        root
        artistPhotos
        artistInfos
        albumInfos
        playlistsDir
    }

    val root: File
        get() {
            val dir = when (currentMode) {
                Mode.DEFAULT -> File("/storage/emulated/0/Lyron")
                Mode.INTERNAL -> {
                    val ctx = appContext
                    if (ctx != null) File(ctx.filesDir, "Lyron")
                    else File("/storage/emulated/0/Lyron")
                }
                Mode.CUSTOM -> {
                    val path = customRootPath
                    if (!path.isNullOrBlank()) File(path)
                    else File("/storage/emulated/0/Lyron")
                }
            }
            dir.mkdirs()
            return dir
        }

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

    /** Pasta para imagens de letras geradas (share). Quando INTERNAL, usa Pictures/Lyron. */
    fun lyricsImagesDir(context: Context): File {
        return if (currentMode == Mode.INTERNAL) {
            File("/storage/emulated/0/Pictures/Lyron").also { it.mkdirs() }
        } else {
            File(root, "Lyrics Images").also { it.mkdirs() }
        }
    }

    /** Pasta para vídeos de letras gerados. Quando INTERNAL, usa Movies/Lyron. */
    fun lyricsVideosDir(context: Context): File {
        return if (currentMode == Mode.INTERNAL) {
            File("/storage/emulated/0/Movies/Lyron").also { it.mkdirs() }
        } else {
            File(root, "Lyrics Videos").also { it.mkdirs() }
        }
    }

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

    private const val KEY_DATA_MODE = "data_storage_mode"
    private const val KEY_CUSTOM_ROOT = "data_storage_custom_root"
}
