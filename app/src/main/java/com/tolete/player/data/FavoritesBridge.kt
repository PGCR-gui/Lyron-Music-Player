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

/** Acesso direto a favorites.json + playlists.json em /Lyron. */
object FavoritesFile {

    internal val lock = Any()

    private fun playlistsFile(context: Context) =
        File(LyronStorage.playlistsDir, "playlists.json")

    private fun favoritesFile(context: Context) = LyronStorage.favoritesFile

    private fun isFavoritesPlaylist(o: JSONObject) =
        o.optBoolean("isFavorites") || o.optString("id") == Playlist.FAVORITES_ID

    private fun loadPlaylistArray(context: Context): JSONArray {
        val f = playlistsFile(context)
        if (!f.exists()) return JSONArray()
        return try {
            JSONObject(f.readText()).getJSONArray("playlists")
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun loadFavoriteIds(context: Context): MutableList<String> {
        val f = favoritesFile(context)
        if (f.exists()) {
            try {
                val o = JSONObject(f.readText())
                val arr = o.optJSONArray("trackIds") ?: return mutableListOf()
                return (0 until arr.length()).map { arr.getString(it) }.toMutableList()
            } catch (_: Exception) {}
        }
        // fallback playlists.json
        try {
            val arr = loadPlaylistArray(context)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (!isFavoritesPlaylist(o)) continue
                val ids = o.getJSONArray("trackIds")
                return (0 until ids.length()).map { ids.getString(it) }.toMutableList()
            }
        } catch (_: Exception) {}
        return mutableListOf()
    }

    private fun writeFavorites(context: Context, ids: List<String>) {
        val f = favoritesFile(context)
        val tmp = File(f.parentFile, "favorites.json.tmp")
        tmp.writeText(JSONObject().put("v", 1).put("trackIds", JSONArray(ids)).toString())
        if (!tmp.renameTo(f)) {
            f.writeText(JSONObject().put("v", 1).put("trackIds", JSONArray(ids)).toString())
            tmp.delete()
        }
    }

    fun isFavorite(context: Context, trackId: String): Boolean = synchronized(lock) {
        trackId in loadFavoriteIds(context)
    }

    fun toggle(context: Context, trackId: String) = synchronized(lock) {
        try {
            val ids = loadFavoriteIds(context)
            if (!ids.remove(trackId)) ids.add(0, trackId)
            writeFavorites(context, ids)
        } catch (_: Exception) { }
    }

    fun clearAll(context: Context) = synchronized(lock) {
        try {
            writeFavorites(context, emptyList())
        } catch (_: Exception) {}
    }
}
