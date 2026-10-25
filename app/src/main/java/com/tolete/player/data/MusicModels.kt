package com.tolete.player.data

import android.net.Uri

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String = "",
    val genre: String,
    val duration: Long,          // em milissegundos
    val uri: Uri,
    val path: String,
    val folder: String,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val composer: String = "",
    val artPath: String? = null,
    val sourceTree: String = "",
    val codec: String? = null,
    val bitrate: Int = 0,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val fileSize: Long = 0L,
    val lastModified: Long = 0L,
    val bitDepth: Int = 0        // 16, 24, 32 (0 = desconhecido)
) {
    val isHiRes: Boolean get() = sampleRate >= 96_000
}

data class Artist(
    val name: String,
    val trackCount: Int,
    val artPath: String? = null,
    val totalDuration: Long = 0L
)

data class Album(
    val name: String,
    val artist: String,
    val trackCount: Int,
    val year: Int? = null,
    val artPath: String? = null,
    val totalDuration: Long = 0L
)

data class Genre(
    val name: String,
    val trackCount: Int,
    val artPath: String? = null,
    val totalDuration: Long = 0L
)

data class Folder(
    val name: String,
    val path: String,
    val trackCount: Int
)

data class SourceFolder(
    val uri: String,
    val name: String,
    val path: String,
    val trackCount: Int
)

data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<String>,
    val isFavorites: Boolean = false
) {
    companion object {
        const val FAVORITES_ID = "favorites"
        fun favorites(trackIds: List<String> = emptyList()) =
            Playlist(FAVORITES_ID, "Favoritos", trackIds, isFavorites = true)
    }
}

data class LyricWord(
    val timeMs: Long,
    val text: String,
    val endMs: Long = -1L
)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList()
) {
    val hasSyllableSync: Boolean get() = words.isNotEmpty()
}
