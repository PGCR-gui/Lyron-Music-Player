package com.tolete.player.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Persistência local de metadados de artistas e álbuns:
 * biografia/texto e foto (URL remota ou arquivo local).
 */
object ArtistMetaStore {

    private const val PREFS = "entity_meta"
    private const val PHOTO_DIR = "artist_photos"

    data class Meta(
        val text: String? = null,
        val imageUrl: String? = null,
        val localImagePath: String? = null,
        val source: String? = null
    ) {
        val displayImage: String?
            get() = localImagePath?.takeIf { File(it).exists() } ?: imageUrl
    }

    fun artistKey(name: String) = "artist_${name.trim().lowercase()}"
    fun albumKey(album: String, artist: String?) =
        "album_${album.trim().lowercase()}_${(artist ?: "").trim().lowercase()}"

    fun load(context: Context, key: String): Meta? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key, null) ?: return null
        return try {
            val o = JSONObject(raw)
            Meta(
                text = o.optString("text").takeIf { it.isNotBlank() },
                imageUrl = o.optString("imageUrl").takeIf { it.isNotBlank() },
                localImagePath = o.optString("localImagePath").takeIf { it.isNotBlank() },
                source = o.optString("source").takeIf { it.isNotBlank() }
            )
        } catch (_: Exception) {
            null
        }
    }

    fun save(context: Context, key: String, meta: Meta) {
        val o = JSONObject()
        meta.text?.let { o.put("text", it) }
        meta.imageUrl?.let { o.put("imageUrl", it) }
        meta.localImagePath?.let { o.put("localImagePath", it) }
        meta.source?.let { o.put("source", it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(key, o.toString()).apply()
    }

    fun saveText(context: Context, key: String, text: String?, source: String = "manual") {
        val cur = load(context, key) ?: Meta()
        save(context, key, cur.copy(text = text?.ifBlank { null }, source = source))
    }

    fun saveRemoteImage(context: Context, key: String, imageUrl: String?, source: String) {
        val cur = load(context, key) ?: Meta()
        if (cur.localImagePath != null && File(cur.localImagePath).exists()) {
            save(context, key, cur.copy(imageUrl = imageUrl ?: cur.imageUrl, source = cur.source ?: source))
        } else {
            save(context, key, cur.copy(imageUrl = imageUrl, source = source))
        }
    }

    fun saveLocalPhoto(context: Context, key: String, uri: Uri): String? {
        return try {
            val dir = File(context.filesDir, PHOTO_DIR).also { it.mkdirs() }
            val dest = File(dir, key.hashCode().toString() + "_" + System.currentTimeMillis() + ".jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: return null
            val cur = load(context, key)
            cur?.localImagePath?.let { old ->
                try { File(old).delete() } catch (_: Exception) {}
            }
            save(context, key, (cur ?: Meta()).copy(localImagePath = dest.absolutePath, source = "manual_photo"))
            dest.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun exportAll(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val o = JSONObject()
        prefs.all.forEach { (k, v) ->
            if (v is String) o.put(k, v)
        }
        return o.toString()
    }

    fun importAll(context: Context, json: String) {
        try {
            val o = JSONObject(json)
            val ed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            o.keys().forEach { k -> ed.putString(k, o.getString(k)) }
            ed.apply()
        } catch (_: Exception) {}
    }
}
