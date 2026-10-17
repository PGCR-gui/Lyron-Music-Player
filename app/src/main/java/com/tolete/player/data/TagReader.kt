package com.tolete.player.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.AudioHeader
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.Artwork
import java.io.File

/**
 * Leitura de tags e propriedades de áudio via JAudioTagger.
 * Suporta MP3, FLAC, OGG, M4A/MP4, WAV, AIFF, WMA, DSF, etc.
 *
 * Requer path de ficheiro legível (idealmente com “Acesso a todos os arquivos”).
 */
object TagReader {

    data class Tags(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val albumArtist: String? = null,
        val genre: String? = null,
        val year: String? = null,
        val track: String? = null,
        val trackTotal: String? = null,
        val disc: String? = null,
        val discTotal: String? = null,
        val composer: String? = null,
        val lyricist: String? = null,
        val comment: String? = null,
        val lyrics: String? = null,
        val bpm: String? = null,
        val isrc: String? = null,
        val copyright: String? = null,
        val encoder: String? = null,
        val publisher: String? = null,
        val originalArtist: String? = null,
        val originalYear: String? = null,
        // Técnico (só leitura)
        val format: String? = null,
        val sampleRate: Int = 0,
        val bitRate: Int = 0,
        val channels: Int = 0,
        val bitDepth: Int = 0,
        val durationMs: Long = 0L,
        val hasArtwork: Boolean = false
    )

    init {
        try {
            TagOptionSingleton.getInstance().setAndroid(true)
        } catch (_: Throwable) {
        }
    }

    fun read(file: File): Tags? {
        if (!file.isFile || !file.canRead()) return null
        return try {
            val audio = AudioFileIO.read(file)
            val tag = audio.tag
            val header = audio.audioHeader
            fromTagAndHeader(tag, header)
        } catch (_: Exception) {
            null
        }
    }

    fun read(context: Context, track: Track): Tags? {
        val file = resolveReadableFile(context, track) ?: return null
        return read(file)
    }

    fun readArtworkBytes(file: File): ByteArray? {
        return try {
            val audio = AudioFileIO.read(file)
            val art: Artwork? = audio.tag?.firstArtwork
            art?.binaryData
        } catch (_: Exception) {
            null
        }
    }

    private fun fromTagAndHeader(tag: Tag?, header: AudioHeader?): Tags {
        fun g(key: FieldKey): String? = try {
            tag?.getFirst(key)?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }

        val durationMs = try {
            val sec = header?.trackLength ?: 0
            if (sec > 0) sec.toLong() * 1000L else 0L
        } catch (_: Exception) {
            0L
        }

        val sampleRate = try {
            header?.sampleRateAsNumber?.toInt() ?: 0
        } catch (_: Exception) {
            0
        }

        val bitRate = try {
            // jaudiotagger devolve kbps → convertemos para bps (Int)
            val kbps = header?.bitRateAsNumber?.toInt() ?: 0
            kbps * 1000
        } catch (_: Exception) {
            0
        }

        val channels = try {
            when (header?.channels?.lowercase()) {
                "1", "mono" -> 1
                "2", "stereo" -> 2
                else -> header?.channels?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            }
        } catch (_: Exception) {
            0
        }

        val bitDepth = try {
            header?.bitsPerSample?.toInt() ?: 0
        } catch (_: Exception) {
            0
        }

        val format = try {
            header?.format?.trim()?.takeIf { it.isNotEmpty() }
                ?: header?.encodingType?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }

        val hasArt = try {
            tag?.firstArtwork != null
        } catch (_: Exception) {
            false
        }

        // TRACK pode ser "3/12"
        val trackRaw = g(FieldKey.TRACK)
        val trackTotalField = g(FieldKey.TRACK_TOTAL)
        val (trackNo, trackTot) = splitFraction(trackRaw, trackTotalField)

        val discRaw = g(FieldKey.DISC_NO)
        val discTotalField = g(FieldKey.DISC_TOTAL)
        val (discNo, discTot) = splitFraction(discRaw, discTotalField)

        return Tags(
            title = g(FieldKey.TITLE),
            artist = g(FieldKey.ARTIST),
            album = g(FieldKey.ALBUM),
            albumArtist = g(FieldKey.ALBUM_ARTIST),
            genre = g(FieldKey.GENRE),
            year = g(FieldKey.YEAR),
            track = trackNo,
            trackTotal = trackTot,
            disc = discNo,
            discTotal = discTot,
            composer = g(FieldKey.COMPOSER),
            lyricist = g(FieldKey.LYRICIST),
            comment = g(FieldKey.COMMENT),
            lyrics = g(FieldKey.LYRICS),
            bpm = g(FieldKey.BPM),
            isrc = g(FieldKey.ISRC),
            copyright = g(FieldKey.COPYRIGHT),
            encoder = g(FieldKey.ENCODER),
            publisher = g(FieldKey.RECORD_LABEL),
            originalArtist = g(FieldKey.ORIGINAL_ARTIST),
            originalYear = g(FieldKey.ORIGINAL_YEAR),
            format = format,
            sampleRate = sampleRate,
            bitRate = bitRate,
            channels = channels,
            bitDepth = bitDepth,
            durationMs = durationMs,
            hasArtwork = hasArt
        )
    }

    private fun splitFraction(raw: String?, explicitTotal: String?): Pair<String?, String?> {
        if (raw.isNullOrBlank()) return null to explicitTotal?.takeIf { it.isNotBlank() }
        val parts = raw.split('/', limit = 2).map { it.trim() }
        val num = parts.getOrNull(0)?.takeIf { it.isNotEmpty() }
        val tot = parts.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: explicitTotal?.takeIf { it.isNotBlank() }
        return num to tot
    }

    fun resolveReadableFile(context: Context, track: Track): File? {
        val candidates = ArrayList<File>()
        if (track.path.startsWith("/")) {
            candidates.add(File(track.path))
        }
        track.uri.path?.takeIf { it.startsWith("/") }?.let { candidates.add(File(it)) }
        if (track.uri.scheme == "file") {
            track.uri.path?.let { candidates.add(File(it)) }
        }
        // MediaStore content://…/_id → DATA
        if (track.uri.scheme == "content") {
            resolveMediaStorePath(context, track.uri)?.let { candidates.add(File(it)) }
        }
        for (f in candidates) {
            if (f.isFile && f.canRead()) return f
        }
        return null
    }

    private fun resolveMediaStorePath(context: Context, uri: Uri): String? {
        return try {
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (idx >= 0) c.getString(idx)?.takeIf { it.startsWith("/") }
                    else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true
}
