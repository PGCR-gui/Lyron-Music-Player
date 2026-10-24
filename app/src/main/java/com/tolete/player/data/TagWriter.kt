package com.tolete.player.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import java.io.File

/**
 * Grava tags no arquivo de áudio com JAudioTagger.
 * Precisa de path real e permissão de escrita
 * (idealmente “Acesso a todos os arquivos”).
 */
object TagWriter {

    data class Result(val ok: Boolean, val message: String)

    /** Conjunto completo de campos editáveis (versão experimental). */
    data class Fields(
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val albumArtist: String = "",
        val genre: String = "",
        val year: String = "",
        val track: String = "",
        val trackTotal: String = "",
        val disc: String = "",
        val discTotal: String = "",
        val composer: String = "",
        val lyricist: String = "",
        val comment: String = "",
        val lyrics: String = "",
        val bpm: String = "",
        val isrc: String = "",
        val copyright: String = "",
        val encoder: String = "",
        val publisher: String = "",
        val originalArtist: String = "",
        val originalYear: String = "",
        /** Nova capa (JPEG) a gravar junto com as tags; null = não alterar. */
        val cover: ByteArray? = null
    )

    init {
        try {
            TagOptionSingleton.getInstance().setAndroid(true)
        } catch (_: Throwable) {
        }
    }

    /**
     * Grava apenas os campos informados (não-nulos). Campos omitidos permanecem intactos no arquivo.
     * Útil para edição em lote (Artista / Álbum / Artista do Álbum).
     */
    fun writePartial(
        context: Context,
        track: Track,
        artist: String? = null,
        album: String? = null,
        albumArtist: String? = null,
        genre: String? = null,
        year: String? = null,
        trackTotal: String? = null,
        discTotal: String? = null
    ): Result {
        if (artist == null && album == null && albumArtist == null && genre == null && year == null && trackTotal == null && discTotal == null) {
            return Result(true, "Nada a alterar")
        }
        val file = resolveWritableFile(context, track)
            ?: return Result(
                false,
                "Sem acesso de escrita ao arquivo. Ative “Acesso a todos os arquivos” nas definições do sistema."
            )
        return try {
            val audio = AudioFileIO.read(file)
            val tag = audio.tagOrCreateAndSetDefault
            fun set(key: FieldKey, value: String) {
                val v = value.trim()
                try {
                    if (v.isEmpty()) tag.deleteField(key) else tag.setField(key, v)
                } catch (_: Exception) { }
            }
            artist?.let { set(FieldKey.ARTIST, it) }
            album?.let { set(FieldKey.ALBUM, it) }
            albumArtist?.let { set(FieldKey.ALBUM_ARTIST, it) }
            genre?.let { set(FieldKey.GENRE, it) }
            year?.let { set(FieldKey.YEAR, it) }
            trackTotal?.let { set(FieldKey.TRACK_TOTAL, it) }
            discTotal?.let { set(FieldKey.DISC_TOTAL, it) }
            audio.commit()
            Result(true, "Tags gravadas em ${file.name}")
        } catch (e: Exception) {
            Result(false, e.message?.take(160) ?: "Falha ao gravar tags")
        }
    }

    fun write(context: Context, track: Track, fields: Fields): Result =
        editFile(context, track, { name -> "Tags gravadas em $name" }) { audio ->
            val tag = audio.tagOrCreateAndSetDefault

            fun set(key: FieldKey, value: String) {
                val v = value.trim()
                try {
                    if (v.isEmpty()) {
                        tag.deleteField(key)
                    } else {
                        tag.setField(key, v)
                    }
                } catch (_: Exception) {
                    // Campo não suportado neste formato — ignora
                }
            }

            set(FieldKey.TITLE, fields.title)
            set(FieldKey.ARTIST, fields.artist)
            set(FieldKey.ALBUM, fields.album)
            set(FieldKey.ALBUM_ARTIST, fields.albumArtist)
            set(FieldKey.GENRE, fields.genre)
            set(FieldKey.YEAR, fields.year)

            val trackVal = when {
                fields.track.isNotBlank() && fields.trackTotal.isNotBlank() ->
                    "${fields.track.trim()}/${fields.trackTotal.trim()}"
                fields.track.isNotBlank() -> fields.track.trim()
                else -> ""
            }
            set(FieldKey.TRACK, trackVal)
            set(FieldKey.TRACK_TOTAL, fields.trackTotal)

            val discVal = when {
                fields.disc.isNotBlank() && fields.discTotal.isNotBlank() ->
                    "${fields.disc.trim()}/${fields.discTotal.trim()}"
                fields.disc.isNotBlank() -> fields.disc.trim()
                else -> ""
            }
            set(FieldKey.DISC_NO, discVal)
            set(FieldKey.DISC_TOTAL, fields.discTotal)

            set(FieldKey.COMPOSER, fields.composer)
            set(FieldKey.LYRICIST, fields.lyricist)
            set(FieldKey.COMMENT, fields.comment)
            set(FieldKey.LYRICS, fields.lyrics)
            set(FieldKey.BPM, fields.bpm)
            set(FieldKey.ISRC, fields.isrc)
            set(FieldKey.COPYRIGHT, fields.copyright)
            set(FieldKey.ENCODER, fields.encoder)
            set(FieldKey.RECORD_LABEL, fields.publisher)
            set(FieldKey.ORIGINAL_ARTIST, fields.originalArtist)
            set(FieldKey.ORIGINAL_YEAR, fields.originalYear)

            fields.cover?.let { setCover(tag, it, "image/jpeg") }
        }

    fun write(
        context: Context,
        track: Track,
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: Int?,
        trackNumber: Int?
    ): Result = write(
        context,
        track,
        Fields(
            title = title,
            artist = artist,
            album = album,
            genre = genre,
            year = year?.takeIf { it > 0 }?.toString().orEmpty(),
            track = trackNumber?.takeIf { it > 0 }?.toString().orEmpty()
        )
    )

    /**
     * Substitui a capa embutida do arquivo pela imagem em [imageBytes] (JPEG/PNG).
     * Também grava uma cópia local em cache para a UI atualizar de imediato.
     * @return Result + caminho da capa em cache (se ok)
     */
    fun writeArtwork(
        context: Context,
        track: Track,
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg"
    ): Pair<Result, String?> {
        val res = editFile(context, track, { name -> "Capa atualizada em $name" }) { audio ->
            setCover(audio.tagOrCreateAndSetDefault, imageBytes, mimeType)
        }
        if (!res.ok) return res to null
        return res to saveCoverPreview(context, imageBytes, mimeType)
    }

    /** Salva uma cópia da capa em filesDir/art (mesma pasta das capas da biblioteca; nome único, para o Coil não reutilizar a imagem antiga). */
    fun saveCoverPreview(context: Context, imageBytes: ByteArray, mimeType: String = "image/jpeg"): String {
        val cacheDir = File(context.filesDir, "art").apply { mkdirs() }
        val ext = if (mimeType.contains("png")) "png" else "jpg"
        val out = File(cacheDir, "cover_${System.currentTimeMillis()}.$ext")
        out.writeBytes(imageBytes)
        return out.absolutePath
    }

    private fun setCover(tag: org.jaudiotagger.tag.Tag, bytes: ByteArray, mime: String) {
        try { tag.deleteArtworkField() } catch (_: Exception) {}
        val artwork = org.jaudiotagger.tag.images.ArtworkFactory.getNew()
        artwork.binaryData = bytes
        artwork.mimeType = mime
        artwork.pictureType = 3 // front cover
        tag.setField(artwork)
    }

    /**
     * Edita o arquivo de áudio: primeiro direto pelo caminho real; se não houver acesso,
     * copia via ContentResolver (SAF/MediaStore) para um temporário, edita e grava de volta.
     */
    private fun editFile(
        context: Context,
        track: Track,
        okMsg: (String) -> String,
        edit: (org.jaudiotagger.audio.AudioFile) -> Unit
    ): Result {
        val direct = resolveWritableFile(context, track)
        var directError: String? = null
        if (direct != null) {
            try {
                val audio = AudioFileIO.read(direct)
                edit(audio)
                audio.commit()
                return Result(true, okMsg(direct.name))
            } catch (e: Exception) {
                directError = e.message?.take(160) ?: "Falha ao gravar"
            }
        }
        if (track.uri.scheme == "content") {
            var tmp: File? = null
            try {
                val cr = context.contentResolver
                val name = displayName(context, track)
                val ext = name.substringAfterLast('.', "").ifBlank { "mp3" }
                val dir = File(context.cacheDir, "tagedit").apply { mkdirs() }
                tmp = File(dir, "edit_${System.nanoTime()}.$ext")
                val input = cr.openInputStream(track.uri)
                    ?: return Result(false, directError ?: "Não foi possível abrir o arquivo")
                input.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                val audio = AudioFileIO.read(tmp)
                edit(audio)
                audio.commit()
                val out = cr.openOutputStream(track.uri, "wt")
                    ?: return Result(false, "Sem permissão de escrita neste arquivo")
                out.use { o -> tmp.inputStream().use { i -> i.copyTo(o) } }
                return Result(true, okMsg(name))
            } catch (e: SecurityException) {
                return Result(
                    false,
                    "Sem permissão de escrita. Ative “Acesso a todos os arquivos” nas definições do sistema."
                )
            } catch (e: Exception) {
                return Result(false, e.message?.take(160) ?: directError ?: "Falha ao gravar")
            } finally {
                try { tmp?.delete() } catch (_: Exception) {}
            }
        }
        return Result(
            false,
            directError
                ?: "Sem acesso de escrita ao arquivo. Ative “Acesso a todos os arquivos” nas definições do sistema."
        )
    }

    private fun displayName(context: Context, track: Track): String {
        val fromPath = track.path.substringAfterLast('/')
        if (fromPath.contains('.')) return fromPath
        return try {
            context.contentResolver.query(
                track.uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: Exception) { null } ?: fromPath
    }

    private fun resolveWritableFile(context: Context, track: Track): File? {
        val candidates = ArrayList<File>()
        if (track.path.startsWith("/")) candidates.add(File(track.path))
        track.uri.path?.takeIf { it.startsWith("/") }?.let { candidates.add(File(it)) }
        if (track.uri.scheme == "file") track.uri.path?.let { candidates.add(File(it)) }
        if (track.uri.scheme == "content") {
            resolveMediaStorePath(context, track.uri)?.let { candidates.add(File(it)) }
        }
        for (f in candidates.distinctBy { it.absolutePath }) {
            if (!f.isFile) continue
            if (f.canWrite()) return f
            if (hasAllFilesAccess() && f.parentFile?.exists() == true) return f
            if (f.parentFile?.canWrite() == true) return f
        }
        return null
    }

    private fun resolveMediaStorePath(context: Context, uri: android.net.Uri): String? {
        return try {
            context.contentResolver.query(
                uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (idx >= 0) c.getString(idx)?.takeIf { it.startsWith("/") } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true
}
