package com.tolete.player.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Liga o app (MusicViewModel) ao PlaybackService, que mostra o botão de favoritar na notificação.
 *
 *  - Com o app aberto, o ViewModel é o "dono" dos favoritos: o serviço pede a ele.
 *  - Com o app fechado (só o serviço vivo), o serviço edita playlists.json direto.
 *  - Qualquer mudança chama [notifyChanged], e o serviço redesenha o ícone.
 */
object FavoritesBridge {

    interface Owner {
        fun isFavorite(trackId: String): Boolean
        fun toggleFavorite(trackId: String)
    }

    @Volatile
    var owner: Owner? = null

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun notifyChanged() {
        _version.update { it + 1 }
    }
}

/** Acesso direto ao playlists.json (mesmo formato do ViewModel) para quando o app está fechado. */
object FavoritesFile {

    internal val lock = Any()

    private fun file(context: Context) = File(context.filesDir, "playlists.json")

    private fun isFavoritesPlaylist(o: JSONObject) =
        o.optBoolean("isFavorites") || o.optString("id") == Playlist.FAVORITES_ID

    private fun load(context: Context): JSONArray {
        val f = file(context)
        if (!f.exists()) return JSONArray()
        return JSONObject(f.readText()).getJSONArray("playlists")
    }

    fun isFavorite(context: Context, trackId: String): Boolean = synchronized(lock) {
        try {
            val arr = load(context)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (!isFavoritesPlaylist(o)) continue
                val ids = o.getJSONArray("trackIds")
                for (j in 0 until ids.length()) if (ids.getString(j) == trackId) return@synchronized true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    fun toggle(context: Context, trackId: String) = synchronized(lock) {
        try {
            val arr = load(context)
            val out = JSONArray()
            var done = false
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (!done && isFavoritesPlaylist(o)) {
                    val old = o.getJSONArray("trackIds")
                    val ids = ArrayList<String>(old.length() + 1)
                    for (j in 0 until old.length()) ids.add(old.getString(j))
                    if (!ids.remove(trackId)) ids.add(0, trackId) // novo favorito vai para o topo, como no app
                    o.put("trackIds", JSONArray(ids))
                    done = true
                }
                out.put(o)
            }
            if (!done) {
                // sem playlist de favoritos ainda: cria no topo
                val fav = JSONObject()
                    .put("id", Playlist.FAVORITES_ID)
                    .put("name", "Favoritos")
                    .put("isFavorites", true)
                    .put("trackIds", JSONArray(listOf(trackId)))
                val withFav = JSONArray().put(fav)
                for (i in 0 until out.length()) withFav.put(out.getJSONObject(i))
                write(context, withFav)
            } else {
                write(context, out)
            }
        } catch (_: Exception) { }
    }

    private fun write(context: Context, playlists: JSONArray) {
        val f = file(context)
        val tmp = File(f.parentFile, "playlists.json.tmp")
        tmp.writeText(JSONObject().put("v", 1).put("playlists", playlists).toString())
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }
}
