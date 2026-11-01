package com.tolete.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Metadados de artistas/álbuns em /storage/emulated/0/Lyron:
 * - artist_photos/<Artista>.jpg (+ .nomedia)
 * - artist_infos/<Artista>.txt
 * - album_infos/<Álbum - Artista>.txt
 * As fotos são sempre gravadas como JPEG real (.jpg). Arquivos .url antigos são
 * convertidos para .jpg por [migrateLegacyUrlFiles].
 *
 * IMPORTANTE: tudo que toca disco/rede aqui (load, save*, clear, rebuildPhotoIndex,
 * migrateLegacyUrlFiles) deve rodar em Dispatchers.IO. Para a UI (listas/composição) use
 * [cachedPhotoPath], que só consulta memória.
 */
object ArtistMetaStore {

    data class Meta(
        val text: String? = null,
        val imageUrl: String? = null,
        val localImagePath: String? = null,
        val source: String? = null
    ) {
        val displayImage: String?
            get() = localImagePath?.takeIf { File(it).exists() } ?: imageUrl
    }

    // ---------- Índice de fotos em memória (leitura barata para a UI) ----------

    private val lock = Any()
    @Volatile private var photoIndex: Map<String, String> = emptyMap()
    private val _photoVersion = MutableStateFlow(0)
    /** Incrementa sempre que o índice muda; a UI observa para recompor. */
    val photoVersion: StateFlow<Int> = _photoVersion.asStateFlow()
    @Volatile private var legacyMigrated = false

    private fun indexKey(name: String) = LyronStorage.sanitizeFileName(name).lowercase()

    /** Caminho da foto local do artista, só consultando memória (sem disco). */
    fun cachedPhotoPath(name: String): String? = photoIndex[indexKey(name)]

    private fun indexSet(name: String, path: String?) = synchronized(lock) {
        val m = photoIndex.toMutableMap()
        if (path == null) m.remove(indexKey(name)) else m[indexKey(name)] = path
        photoIndex = m
        _photoVersion.value = _photoVersion.value + 1
    }

    /** Relê a pasta artist_photos uma única vez. Chamar em IO. */
    fun rebuildPhotoIndex() {
        val map = HashMap<String, String>()
        try {
            LyronStorage.artistPhotos.listFiles()?.forEach { f ->
                if (f.isFile && f.length() > 0 && f.extension.equals("jpg", ignoreCase = true)) {
                    map[f.nameWithoutExtension.lowercase()] = f.absolutePath
                }
            }
        } catch (_: Exception) {}
        synchronized(lock) {
            photoIndex = map
            _photoVersion.value = _photoVersion.value + 1
        }
    }

    /**
     * Converte sidecars .url antigos em fotos .jpg de verdade (uma vez por execução do app).
     * Chamar em IO. Retorna quantas fotos foram baixadas.
     */
    fun migrateLegacyUrlFiles(): Int {
        if (legacyMigrated) return 0
        legacyMigrated = true
        var converted = 0
        try {
            val files = LyronStorage.artistPhotos.listFiles()
                ?.filter { it.isFile && it.extension.equals("url", ignoreCase = true) }
                ?: return 0
            for (f in files) {
                val jpg = File(f.parentFile, f.nameWithoutExtension + ".jpg")
                if (jpg.isFile && jpg.length() > 0) { f.delete(); continue }
                val url = try { f.readText(Charsets.UTF_8).trim() } catch (_: Exception) { "" }
                if (url.startsWith("http") && downloadImageAsJpeg(url, jpg)) {
                    f.delete()
                    converted++
                }
            }
        } catch (_: Exception) {}
        if (converted > 0) rebuildPhotoIndex()
        return converted
    }

    fun artistKey(name: String) = "artist_${name.trim().lowercase()}"
    fun albumKey(album: String, artist: String?) =
        "album_${album.trim().lowercase()}_${(artist ?: "").trim().lowercase()}"

    private fun parseArtistName(key: String): String =
        key.removePrefix("artist_").trim()

    private fun parseAlbum(key: String): Pair<String, String?> {
        val rest = key.removePrefix("album_")
        val idx = rest.lastIndexOf('_')
        return if (idx > 0) {
            rest.substring(0, idx).trim() to rest.substring(idx + 1).trim().ifBlank { null }
        } else rest.trim() to null
    }

    fun load(context: Context, key: String): Meta? {
        return try {
            when {
                key.startsWith("artist_") -> {
                    val name = parseArtistName(key)
                    val info = LyronStorage.artistInfoFile(name)
                    val photo = LyronStorage.artistPhotoFile(name)
                    val urlFile = File(photo.parentFile, photo.nameWithoutExtension + ".url")
                    val text = if (info.isFile) info.readText(Charsets.UTF_8).ifBlank { null } else null
                    val local = if (photo.isFile && photo.length() > 0) photo.absolutePath else null
                    val url = if (urlFile.isFile) urlFile.readText(Charsets.UTF_8).trim().ifBlank { null } else null
                    if (text == null && local == null && url == null) null
                    else Meta(text = text, imageUrl = url, localImagePath = local, source = "lyron")
                }
                key.startsWith("album_") -> {
                    val (album, artist) = parseAlbum(key)
                    val info = LyronStorage.albumInfoFile(album, artist)
                    val text = if (info.isFile) info.readText(Charsets.UTF_8).ifBlank { null } else null
                    if (text == null) null else Meta(text = text, source = "lyron")
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun clear(context: Context, key: String) {
        try {
            when {
                key.startsWith("artist_") -> {
                    val name = parseArtistName(key)
                    LyronStorage.artistInfoFile(name).delete()
                    val photo = LyronStorage.artistPhotoFile(name)
                    photo.delete()
                    File(photo.parentFile, photo.nameWithoutExtension + ".url").delete()
                    indexSet(name, null)
                }
                key.startsWith("album_") -> {
                    val (album, artist) = parseAlbum(key)
                    LyronStorage.albumInfoFile(album, artist).delete()
                }
            }
        } catch (_: Exception) {}
    }

    fun save(context: Context, key: String, meta: Meta) {
        try {
            when {
                key.startsWith("artist_") -> {
                    val name = parseArtistName(key)
                    if (meta.text != null) {
                        LyronStorage.artistInfoFile(name).writeText(meta.text, Charsets.UTF_8)
                    }
                    // Nunca gravamos .url: a foto remota é baixada como .jpg em saveRemoteImage.
                    meta.localImagePath?.let { path ->
                        val src = File(path)
                        val dest = LyronStorage.artistPhotoFile(name)
                        if (src.exists() && src.absolutePath != dest.absolutePath) {
                            src.copyTo(dest, overwrite = true)
                            indexSet(name, dest.absolutePath)
                        }
                    }
                }
                key.startsWith("album_") -> {
                    val (album, artist) = parseAlbum(key)
                    if (meta.text != null) {
                        LyronStorage.albumInfoFile(album, artist).writeText(meta.text, Charsets.UTF_8)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun saveText(context: Context, key: String, text: String?, source: String = "manual") {
        val cur = load(context, key) ?: Meta()
        save(context, key, cur.copy(text = text?.ifBlank { null }, source = source))
    }

    /**
     * Baixa a foto do artista e grava como JPEG em Lyron/artist_photos/<Artista>.jpg.
     * Chamar em IO. Retorna true se a foto ficou salva em disco; em caso de falha nada é
     * gravado (nem .url) e a tentativa se repete na próxima abertura da tela.
     */
    fun saveRemoteImage(context: Context, key: String, imageUrl: String?, source: String): Boolean {
        if (imageUrl.isNullOrBlank() || !key.startsWith("artist_")) return false
        val name = parseArtistName(key)
        val dest = LyronStorage.artistPhotoFile(name)
        if (dest.isFile && dest.length() > 0L) {
            indexSet(name, dest.absolutePath)
            return true
        }
        return try {
            if (downloadImageAsJpeg(imageUrl, dest)) {
                indexSet(name, dest.absolutePath)
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }

    /** Salva a foto escolhida na galeria como JPEG. Chamar em IO. */
    fun saveLocalPhoto(context: Context, key: String, uri: Uri): String? {
        return try {
            if (!key.startsWith("artist_")) return null
            val name = parseArtistName(key)
            val dest = LyronStorage.artistPhotoFile(name)
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            if (bytes.isEmpty()) return null
            if (!writeJpeg(bytes, dest)) {
                // Formato que o BitmapFactory não decodifica: mantém os bytes originais.
                FileOutputStream(dest).use { it.write(bytes) }
            }
            // Foto nova substitui qualquer .url antigo
            File(dest.parentFile, dest.nameWithoutExtension + ".url").delete()
            indexSet(name, dest.absolutePath)
            dest.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private const val MAX_SIDE = 1200

    /** Decodifica [bytes] (png/webp/jpg…) e grava um JPEG real em [dest] via arquivo temporário. */
    private fun writeJpeg(bytes: ByteArray, dest: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return false

        // JPEG não tem transparência: compõe sobre fundo branco
        val bmp = if (decoded.hasAlpha()) {
            val flat = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(flat)
            canvas.drawColor(android.graphics.Color.WHITE)
            canvas.drawBitmap(decoded, 0f, 0f, null)
            decoded.recycle()
            flat
        } else decoded

        val tmp = File(dest.parentFile, dest.name + ".tmp")
        return try {
            FileOutputStream(tmp).use { out ->
                if (!bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)) return false
            }
            if (tmp.length() <= 0L) return false
            if (dest.exists()) dest.delete()
            tmp.renameTo(dest)
        } finally {
            if (tmp.exists()) tmp.delete()
            bmp.recycle()
        }
    }

    /** Baixa [url] e grava como .jpg em [dest]. Falha (false) se não for imagem válida. */
    private fun downloadImageAsJpeg(url: String, dest: File): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 20000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "LyronMusicPlayer/0.67")
            }
            if (conn.responseCode !in 200..299) return false
            val buf = ByteArrayOutputStream()
            conn.inputStream.use { it.copyTo(buf) }
            val bytes = buf.toByteArray()
            bytes.isNotEmpty() && writeJpeg(bytes, dest)
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    /** Exporta índice JSON leve (textos + paths) para backup de preferências. */
    fun exportAll(context: Context): String {
        val o = JSONObject()
        try {
            LyronStorage.artistInfos.listFiles()?.filter { it.extension == "txt" }?.forEach { f ->
                val name = f.nameWithoutExtension
                val key = artistKey(name)
                val photo = LyronStorage.artistPhotoFile(name)
                val meta = JSONObject()
                meta.put("text", f.readText(Charsets.UTF_8))
                if (photo.isFile) meta.put("localImagePath", photo.absolutePath)
                o.put(key, meta.toString())
            }
            LyronStorage.albumInfos.listFiles()?.filter { it.extension == "txt" }?.forEach { f ->
                o.put("album_file_${f.nameWithoutExtension}", JSONObject().put("text", f.readText(Charsets.UTF_8)).toString())
            }
        } catch (_: Exception) {}
        return o.toString()
    }

    fun importAll(context: Context, json: String) {
        try {
            val o = JSONObject(json)
            o.keys().forEach { k ->
                try {
                    val inner = JSONObject(o.getString(k))
                    val text = inner.optString("text").takeIf { it.isNotBlank() }
                    val local = inner.optString("localImagePath").takeIf { it.isNotBlank() }
                    val url = inner.optString("imageUrl").takeIf { it.isNotBlank() }
                    save(context, k, Meta(text = text, localImagePath = local, imageUrl = url, source = "import"))
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }
}
