package com.tolete.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

class MusicScanner(private val context: Context) {

    private val supportedExtensions = setOf(
        "mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma", "aiff", "alac"
    )

    // Capas extraídas ficam em files/art/<sha1>.jpg (mesma capa = mesmo arquivo)
    private val artDir: File by lazy { File(context.filesDir, "art").apply { mkdirs() } }

    /** [onTrack] é chamado a cada faixa encontrada (para mostrar progresso). */
    suspend fun scanTree(
        treeUri: Uri,
        sourceTree: String = treeUri.toString(),
        onTrack: () -> Unit = {},
        existingById: Map<String, Track> = emptyMap()
    ): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )

        scanRecursive(childrenUri, treeUri, sourceTree, tracks, onTrack, existingById)
        tracks
    }

    /**
     * Escaneia todas as faixas de áudio visíveis pelo MediaStore
     * (requer READ_EXTERNAL_STORAGE / READ_MEDIA_AUDIO ou MANAGE_EXTERNAL_STORAGE).
     */
    suspend fun scanAllMedia(
        onTrack: () -> Unit = {},
        existingById: Map<String, Track> = emptyMap()
    ): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        try {
            context.contentResolver.query(
                collection, projection, selection, null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val yearCol = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
                val trackCol = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val modCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = android.content.ContentUris.withAppendedId(collection, id)
                    val fileName = if (nameCol >= 0) cursor.getString(nameCol) ?: "audio" else "audio"
                    val extension = fileName.substringAfterLast('.', "").lowercase()
                    if (extension.isNotEmpty() && extension !in supportedExtensions) continue

                    val docId = "media_store:$id"
                    val trackId = UUID.nameUUIDFromBytes(docId.toByteArray()).toString()
                    val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
                    val modified = if (modCol >= 0 && !cursor.isNull(modCol)) cursor.getLong(modCol) * 1000L else 0L

                    val cached = existingById[trackId]
                    if (cached != null &&
                        (modified == 0L || cached.lastModified == 0L || cached.lastModified == modified) &&
                        (size == 0L || cached.fileSize == 0L || cached.fileSize == size)
                    ) {
                        tracks += cached
                        onTrack()
                        continue
                    }

                    val track = extractMetadata(
                        uri = uri,
                        docId = docId,
                        fileName = fileName,
                        extension = extension,
                        size = size,
                        sourceTree = "all_files",
                        lastModified = modified
                    )
                    if (track != null) {
                        tracks += track
                        onTrack()
                    } else {
                        val title = (if (titleCol >= 0) cursor.getString(titleCol) else null)
                            ?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.')
                        val artist = (if (artistCol >= 0) cursor.getString(artistCol) else null)
                            ?.takeIf { it.isNotBlank() } ?: "Artista desconhecido"
                        val album = (if (albumCol >= 0) cursor.getString(albumCol) else null)
                            ?.takeIf { it.isNotBlank() } ?: "Álbum desconhecido"
                        val duration = if (durCol >= 0) cursor.getLong(durCol) else 0L
                        val year = if (yearCol >= 0) cursor.getInt(yearCol).takeIf { it > 0 } else null
                        val trackNum = if (trackCol >= 0) {
                            cursor.getInt(trackCol).let { if (it > 1000) it % 1000 else it }.takeIf { it > 0 }
                        } else null
                        val path = if (dataCol >= 0) cursor.getString(dataCol) ?: fileName else fileName
                        tracks += Track(
                            id = trackId,
                            title = title,
                            artist = artist,
                            album = album,
                            genre = "Desconhecido",
                            duration = duration,
                            uri = uri,
                            path = path,
                            folder = path.substringBeforeLast('/').substringAfterLast('/'),
                            year = year,
                            trackNumber = trackNum,
                            artPath = null,
                            sourceTree = "all_files",
                            codec = null,
                            fileSize = size,
                            lastModified = modified
                        )
                        onTrack()
                    }
                }
            }
        } catch (_: Exception) { }
        tracks
    }


    private fun scanRecursive(
        childrenUri: Uri,
        treeUri: Uri,
        sourceTree: String,
        tracks: MutableList<Track>,
        onTrack: () -> Unit,
        existingById: Map<String, Track> = emptyMap()
    ) {
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            ),
            null, null, null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                val docId = cursor.getString(idIndex)
                val name = cursor.getString(nameIndex)
                val mime = cursor.getString(mimeIndex)

                val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                if (DocumentsContract.Document.MIME_TYPE_DIR == mime) {
                    val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                    scanRecursive(childUri, treeUri, sourceTree, tracks, onTrack, existingById)
                } else {
                    val extension = name.substringAfterLast('.', "").lowercase()
                    if (extension in supportedExtensions) {
                        val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
                        val modified = if (modIndex >= 0 && !cursor.isNull(modIndex)) cursor.getLong(modIndex) else 0L
                        val trackId = UUID.nameUUIDFromBytes(docId.toByteArray()).toString()
                        val cached = existingById[trackId]
                        if (cached != null &&
                            (modified == 0L || cached.lastModified == 0L || cached.lastModified == modified) &&
                            (size == 0L || cached.fileSize == 0L || cached.fileSize == size)
                        ) {
                            tracks.add(cached)
                            onTrack()
                        } else {
                            extractMetadata(documentUri, docId, name, extension, size, sourceTree, modified)?.let {
                                tracks.add(it)
                                onTrack()
                            }
                        }
                    }
                }
            }
        }
    }

    private class AudioProps(
        var mime: String? = null,
        var sampleRate: Int = 0,
        var channels: Int = 0,
        var bitrate: Int = 0,
        var bitDepth: Int = 0
    )

    /** Lê codec, taxa de amostragem, canais e bitrate direto do formato do arquivo. */
    private fun readAudioProps(uri: Uri): AudioProps {
        val props = AudioProps()
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("audio/")) continue
                props.mime = mime
                if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) props.sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) props.channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                if (f.containsKey(MediaFormat.KEY_BIT_RATE)) props.bitrate = f.getInteger(MediaFormat.KEY_BIT_RATE)
                // PCM encoding / bit depth when available
                try {
                    // KEY_PCM_ENCODING values: 3=16bit, 4=float, 21=24bit, 22=32bit (API 31+)
                    if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        when (f.getInteger(MediaFormat.KEY_PCM_ENCODING)) {
                            3 -> props.bitDepth = 16
                            4 -> props.bitDepth = 32
                            21 -> props.bitDepth = 24
                            22 -> props.bitDepth = 32
                            2 -> props.bitDepth = 8
                        }
                    }
                } catch (_: Exception) {}
                if (props.bitDepth == 0) {
                    val m = mime.lowercase()
                    props.bitDepth = when {
                        "flac" in m || "alac" in m || "wav" in m || "aiff" in m -> 16
                        "mpeg" in m || "mp4" in m || "aac" in m -> 16
                        else -> 0
                    }
                }
                break
            }
        } catch (e: Exception) {
            // segue com o que o MediaMetadataRetriever conseguir
        } finally {
            extractor.release()
        }
        return props
    }

    private fun codecLabel(mime: String?, ext: String): String = when {
        mime == "audio/flac" || ext == "flac" -> "FLAC"
        mime == "audio/mpeg" || ext == "mp3" -> "MP3"
        mime == "audio/alac" || ext == "alac" -> "ALAC"
        mime == "audio/mp4a-latm" -> "AAC"
        mime == "audio/opus" || ext == "opus" -> "Opus"
        mime == "audio/vorbis" || ext == "ogg" -> "Vorbis"
        ext == "wav" -> "WAV"
        ext == "aiff" -> "AIFF"
        ext == "wma" -> "WMA"
        else -> ext.uppercase().ifEmpty { "?" }
    }

    private fun extractMetadata(
        uri: Uri,
        docId: String,
        fileName: String,
        extension: String,
        size: Long,
        sourceTree: String,
        lastModified: Long = 0L
    ): Track? {
        // Preferência: JAudioTagger quando há path de ficheiro legível
        val resolvedPath = resolveFilePath(uri, fileName)
        val jt = resolvedPath?.let { p ->
            try { TagReader.read(File(p)) } catch (_: Exception) { null }
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)

            val title = jt?.title
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: fileName.substringBeforeLast('.')
            val artist = jt?.artist
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: "Desconhecido"
            val album = jt?.album
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?: "Desconhecido"
            val albumArtist = jt?.albumArtist
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?: ""
            val genre = jt?.genre
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                ?: "Desconhecido"
            val duration = jt?.durationMs?.takeIf { it > 0 }
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: 0L
            val year = jt?.year?.take(4)?.toIntOrNull()
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull()
            val trackNumber = jt?.track?.toIntOrNull()
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    ?.substringBefore('/')?.toIntOrNull()

            val props = readAudioProps(uri)
            var bitrate = jt?.bitRate?.takeIf { it > 0 }
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
                ?: 0
            if (bitrate <= 0) bitrate = props.bitrate
            if (bitrate <= 0 && size > 0 && duration > 0) bitrate = (size * 8 * 1000 / duration).toInt()
            var sampleRate = jt?.sampleRate?.takeIf { it > 0 } ?: props.sampleRate
            if (sampleRate <= 0 && Build.VERSION.SDK_INT >= 31) {
                sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
            }
            val channels = jt?.channels?.takeIf { it > 0 } ?: props.channels
            val bitDepth = jt?.bitDepth?.takeIf { it > 0 }
                ?: refineBitDepth(uri, extension, props, bitrate, sampleRate)

            val artPath = try {
                retriever.embeddedPicture?.let { saveArt(it) }
                    ?: resolvedPath?.let { TagReader.readArtworkBytes(File(it))?.let { b -> saveArt(b) } }
            } catch (e: OutOfMemoryError) {
                null
            } catch (_: Exception) {
                null
            }

            val folder = (resolvedPath ?: uri.path)?.substringBeforeLast('/')?.substringAfterLast('/') ?: "Raiz"

            Track(
                id = UUID.nameUUIDFromBytes(docId.toByteArray()).toString(),
                title = title,
                artist = artist,
                album = album,
                albumArtist = albumArtist,
                genre = genre,
                duration = duration,
                uri = uri,
                path = resolvedPath ?: fileName,
                folder = folder,
                year = year,
                trackNumber = trackNumber,
                artPath = artPath,
                sourceTree = sourceTree,
                codec = jt?.format?.uppercase() ?: codecLabel(props.mime, extension),
                bitrate = bitrate,
                sampleRate = sampleRate,
                channels = channels,
                fileSize = size,
                lastModified = lastModified,
                bitDepth = bitDepth
            )
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    /** Resolve path absoluto legível a partir da URI (MediaStore DATA, file://, etc.). */
    private fun resolveFilePath(uri: Uri, fileName: String): String? {
        if (uri.scheme == "file") {
            val p = uri.path
            if (!p.isNullOrBlank() && File(p).isFile) return p
        }
        if (fileName.startsWith("/") && File(fileName).isFile) return fileName
        try {
            if (uri.scheme == "content") {
                context.contentResolver.query(
                    uri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                        if (idx >= 0) {
                            val p = c.getString(idx)
                            if (!p.isNullOrBlank() && File(p).isFile) return p
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        val path = uri.path
        if (!path.isNullOrBlank() && path.startsWith("/") && File(path).isFile) return path
        return null
    }

    /**
     * Salva a capa como JPEG reduzido (~512px+) em files/art.
     * O nome é o SHA-1 dos bytes: faixas do mesmo álbum reaproveitam o mesmo arquivo
     * e não decodificamos a imagem de novo.
     */
    private fun saveArt(bytes: ByteArray): String? {
        return try {
            val hash = MessageDigest.getInstance("SHA-1").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            val file = File(artDir, "$hash.jpg")

            if (!file.exists()) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

                var sample = 1
                while (bounds.outWidth / (sample * 2) >= ART_MIN_SIZE) sample *= 2

                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    ?: return null

                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                }
                bitmap.recycle()
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /**
     * Aprimora bit depth: cabeçalho WAV/FLAC, heurística por bitrate, fallback do extractor.
     */
    private fun refineBitDepth(
        uri: Uri,
        extension: String,
        props: AudioProps,
        bitrate: Int,
        sampleRate: Int
    ): Int {
        // 1) Cabeçalhos sem compressão / FLAC
        readBitDepthFromHeader(uri, extension)?.let { if (it > 0) return it }

        // 2) Já veio do MediaFormat
        if (props.bitDepth in listOf(8, 16, 24, 32)) return props.bitDepth

        // 3) Heurística: bitrate ≈ sampleRate * channels * bitDepth
        val ch = props.channels.coerceAtLeast(1)
        val sr = sampleRate.coerceAtLeast(props.sampleRate).coerceAtLeast(1)
        if (bitrate > 0) {
            val estimated = (bitrate.toDouble() / (sr * ch)).toInt()
            when {
                estimated >= 28 -> return 32
                estimated >= 20 -> return 24
                estimated >= 12 -> return 16
            }
        }

        // 4) Fallback por codec
        return when (extension.lowercase()) {
            "flac", "alac", "wav", "aiff", "aif" -> 16
            "mp3", "aac", "m4a", "ogg", "opus", "wma" -> 16
                        else -> props.bitDepth.takeIf { it > 0 } ?: 16
        }
    }

    private fun readBitDepthFromHeader(uri: Uri, extension: String): Int? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val head = ByteArray(48)
                val n = input.read(head)
                if (n < 16) return@use null
                when (extension.lowercase()) {
                    "wav", "wave" -> parseWavBitDepth(head, n)
                    "flac" -> parseFlacBitDepth(head, n)
                    else -> null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** WAV: bits per sample no offset 34 (PCM fmt chunk padrão). */
    private fun parseWavBitDepth(head: ByteArray, n: Int): Int? {
        if (n < 36) return null
        if (head[0] != 'R'.code.toByte() || head[1] != 'I'.code.toByte()) return null
        // Procura "fmt " nos primeiros bytes
        var i = 12
        while (i + 8 < n) {
            val id = String(head, i, 4, Charsets.US_ASCII)
            val size = (head[i + 4].toInt() and 0xFF) or
                ((head[i + 5].toInt() and 0xFF) shl 8) or
                ((head[i + 6].toInt() and 0xFF) shl 16) or
                ((head[i + 7].toInt() and 0xFF) shl 24)
            if (id == "fmt " && i + 16 < n) {
                val bits = (head[i + 14].toInt() and 0xFF) or ((head[i + 15].toInt() and 0xFF) shl 8)
                return when {
                    bits >= 32 -> 32
                    bits >= 24 -> 24
                    bits >= 16 -> 16
                    bits > 0 -> bits
                    else -> null
                }
            }
            i += 8 + size
            if (size <= 0) break
        }
        // Layout clássico: bits em offset 34
        if (n > 35) {
            val bits = (head[34].toInt() and 0xFF) or ((head[35].toInt() and 0xFF) shl 8)
            if (bits in listOf(8, 16, 24, 32)) return bits
        }
        return null
    }

    /** FLAC STREAMINFO: bits/sample nos bits 116–120 do bloco (após "fLaC" + header). */
    private fun parseFlacBitDepth(head: ByteArray, n: Int): Int? {
        if (n < 26) return null
        if (head[0] != 'f'.code.toByte() || head[1] != 'L'.code.toByte()) return null
        // byte 20-21 do STREAMINFO (offset 8 do file + 12 no STREAMINFO) — simplificado:
        // bits-per-sample: ((b20 & 0x01) << 4) | ((b21 & 0xF0) >> 4)  + 1
        // No pacote mínimo STREAMINFO começa no offset 8
        if (n < 22) return null
        val b20 = head[20].toInt() and 0xFF
        val b21 = head[21].toInt() and 0xFF
        val bits = (((b20 and 0x01) shl 4) or ((b21 and 0xF0) shr 4)) + 1
        return when {
            bits >= 32 -> 32
            bits >= 24 -> 24
            bits >= 16 -> 16
            bits in 4..32 -> bits
            else -> null
        }
    }

    private companion object {
        const val ART_MIN_SIZE = 512
    }
}

