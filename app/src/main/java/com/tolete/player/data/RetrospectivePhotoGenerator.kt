package com.tolete.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.tolete.player.data.RetrospectiveCardRenderer.Layout
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Gera fotos 1080×1920 dos cards da retrospectiva (mesmo visual dos vídeos, via
 * [RetrospectiveCardRenderer]), aplicando reversão de nomes via [ScrobbleEdits] / biblioteca local.
 */
object RetrospectivePhotoGenerator {

    private const val TAG = "RetroPhoto"

    data class Result(val dir: File, val photos: List<File>, val failed: List<String>)

    fun generateAll(
        context: Context,
        exportDir: File,
        libraryTracks: List<Track>? = null,
        theme: RetrospectiveCardRenderer.ThemeColors? = null
    ): Result {
        val app = context.applicationContext
        val models = buildModels(app, exportDir, libraryTracks ?: emptyList(), theme)
        val photos = mutableListOf<File>()
        val failed = mutableListOf<String>()
        for (model in models) {
            try {
                val bmp = Bitmap.createBitmap(
                    RetrospectiveCardRenderer.W, RetrospectiveCardRenderer.H, Bitmap.Config.ARGB_8888
                )
                val assets = RetrospectiveCardRenderer.loadAssets(app, model)
                try {
                    RetrospectiveCardRenderer.render(app, bmp, model, RetrospectiveCardRenderer.STILL_TIME, assets)
                } finally {
                    assets.recycle()
                }
                val out = File(exportDir, "${model.id}.jpg")
                FileOutputStream(out).use { fos -> bmp.compress(Bitmap.CompressFormat.JPEG, 92, fos) }
                bmp.recycle()
                if (out.isFile && out.length() > 1024L) photos += out else failed += model.id
            } catch (e: Exception) {
                Log.e(TAG, "Card ${model.id} falhou", e)
                failed += "${model.id}: ${e.message}"
            }
        }
        return Result(exportDir, photos, failed)
    }

    private data class ChartRow(val name: String, val artist: String, val plays: Int)

    private fun eq(a: String, b: String) =
        a.lowercase(Locale.ROOT).trim() == b.lowercase(Locale.ROOT).trim()

    private fun parseChart(root: JSONObject, key: String): List<ChartRow> {
        val arr = root.optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(ChartRow(o.optString("name"), o.optString("artist"), o.optInt("plays")))
            }
        }
    }

    /**
     * Monta o modelo de cada card a partir do manifest.json: textos (com nomes revertidos),
     * fotos de cada item e cor de fundo (cor primária da imagem do #1 ou do #3, conforme o card).
     * Cards sem imagem usam as cores do tema.
     */
    fun buildModels(
        context: Context,
        exportDir: File,
        libraryTracks: List<Track>,
        theme: RetrospectiveCardRenderer.ThemeColors?
    ): List<RetrospectiveCardRenderer.Model> {
        val manifestFile = File(exportDir, "manifest.json")
        if (!manifestFile.isFile) throw IllegalArgumentException("manifest.json não encontrado")
        val root = JSONObject(manifestFile.readText())
        val cardsArr = root.getJSONArray("cards")
        val tracks = libraryTracks
        try { ArtistMetaStore.rebuildPhotoIndex() } catch (_: Exception) { }

        fun resolveName(artist: String, title: String): Pair<String, String> {
            ScrobbleEdits.reverseManual(artist, title)?.let { return it.artist to it.track }
            val t = tracks.firstOrNull { eq(it.artist, artist) && eq(it.title, title) }
                ?: tracks.firstOrNull { eq(it.title, title) }
            return if (t != null) ScrobbleEdits.firstArtist(t.artist) to t.title
            else ScrobbleEdits.firstArtist(artist) to title
        }
        fun resolveArtist(name: String): String {
            ScrobbleEdits.reverseManual(name, "")?.let { return it.artist }
            return ScrobbleEdits.firstArtist(tracks.firstOrNull { eq(it.artist, name) }?.artist ?: name)
        }

        val topTracks = parseChart(root, "topTracks").map {
            val (a, t) = resolveName(it.artist, it.name); it.copy(name = t, artist = a)
        }
        val topArtists = parseChart(root, "topArtists").map { it.copy(name = resolveArtist(it.name)) }
        val topAlbums = parseChart(root, "topAlbums").map { it.copy(artist = resolveArtist(it.artist)) }

        // ---- imagens de cada item ----
        fun artistImg(name: String): String? =
            ArtistMetaStore.cachedPhotoPath(name)
                ?: tracks.firstOrNull { eq(it.artist, name) || it.artist.lowercase().contains(name.lowercase()) }?.artPath
        fun trackImg(title: String, artist: String): String? =
            tracks.firstOrNull {
                eq(it.title, title) && (artist.isBlank() || eq(it.artist, artist) ||
                    it.artist.lowercase().contains(artist.lowercase()))
            }?.artPath ?: tracks.firstOrNull { eq(it.title, title) }?.artPath
        fun albumImg(name: String, artist: String): String? =
            tracks.firstOrNull { eq(it.album, name) && (artist.isBlank() || eq(it.artist, artist) || eq(it.albumArtist, artist)) }?.artPath
                ?: tracks.firstOrNull { eq(it.album, name) }?.artPath

        val artistImgs = topArtists.take(5).map { artistImg(it.name) }
        val trackImgs = topTracks.take(5).map { trackImg(it.name, it.artist) }
        val albumImgs = topAlbums.take(5).map { albumImg(it.name, it.artist) }

        // Cor primária da imagem de índice [idx]; se faltar imagem, tenta as vizinhas mais próximas.
        fun colorAt(imgs: List<String?>, idx: Int): Int? {
            val order = (idx downTo 0) + ((idx + 1) until imgs.size)
            for (i in order) RetrospectiveCardRenderer.primaryColor(imgs.getOrNull(i))?.let { return it }
            return null
        }

        val fallbackTheme = theme ?: RetrospectiveCardRenderer.ThemeColors(
            0xFFBB86FC.toInt(), 0xFF3A2A6B.toInt(), 0xFF241440.toInt(), false
        )
        val themeA = RetrospectiveCardRenderer.tuneForBackground(fallbackTheme.primary)
        val themeB = RetrospectiveCardRenderer.tuneForBackground(fallbackTheme.secondary)

        fun rowsOf(kind: String): List<RetrospectiveCardRenderer.Row> = when (kind) {
            "artists" -> topArtists.take(5).mapIndexed { i, a -> RetrospectiveCardRenderer.Row(a.name, "", a.plays, artistImgs.getOrNull(i)) }
            "tracks" -> topTracks.take(5).mapIndexed { i, t -> RetrospectiveCardRenderer.Row(t.name, t.artist, t.plays, trackImgs.getOrNull(i)) }
            else -> topAlbums.take(5).mapIndexed { i, a -> RetrospectiveCardRenderer.Row(a.name, a.artist, a.plays, albumImgs.getOrNull(i)) }
        }

        val out = mutableListOf<RetrospectiveCardRenderer.Model>()
        for (i in 0 until cardsArr.length()) {
            val o = cardsArr.getJSONObject(i)
            val id = o.getString("id")
            val icons = o.optString("icons", "musicas")
            val title = o.optString("title")
            val body = o.optString("body")
            out += when (id) {
                "003" -> {
                    val a = topArtists.firstOrNull()
                    RetrospectiveCardRenderer.Model(
                        id, "Seu artista mais ouvido", a?.name ?: "—", icons, Layout.HERO,
                        colorAt(artistImgs, 0) ?: themeA,
                        heroImagePath = artistImgs.firstOrNull(), heroCircle = true,
                        heroCaption = a?.let { "${it.plays} plays" } ?: ""
                    )
                }
                "004" -> RetrospectiveCardRenderer.Model(
                    id, "Artistas mais ouvidos", "", icons, Layout.LIST,
                    colorAt(artistImgs, 2) ?: themeB, rows = rowsOf("artists")
                )
                "005" -> {
                    val t = topTracks.firstOrNull()
                    RetrospectiveCardRenderer.Model(
                        id, "Sua música mais ouvida", t?.let { "${it.name}\n${it.artist}" } ?: "—", icons, Layout.HERO,
                        colorAt(trackImgs, 0) ?: themeA,
                        heroImagePath = trackImgs.firstOrNull(),
                        heroCaption = t?.let { "${it.plays} plays" } ?: ""
                    )
                }
                "006" -> RetrospectiveCardRenderer.Model(
                    id, "Músicas mais ouvidas", "", icons, Layout.LIST,
                    colorAt(trackImgs, 2) ?: themeB, rows = rowsOf("tracks")
                )
                "007" -> {
                    val a = topAlbums.firstOrNull()
                    RetrospectiveCardRenderer.Model(
                        id, "Seu álbum mais ouvido", a?.let { "${it.name}\n${it.artist}" } ?: "—", icons, Layout.HERO,
                        colorAt(albumImgs, 0) ?: themeA,
                        heroImagePath = albumImgs.firstOrNull(),
                        heroCaption = a?.let { "${it.plays} plays" } ?: ""
                    )
                }
                "008" -> RetrospectiveCardRenderer.Model(
                    id, "Álbuns mais ouvidos", "", icons, Layout.LIST,
                    colorAt(albumImgs, 2) ?: themeB, rows = rowsOf("albums")
                )
                "009" -> {
                    val genres = genreStats(context, tracks, topTracks)
                    if (genres.isEmpty()) {
                        RetrospectiveCardRenderer.Model(
                            id, "Seus gêneros favoritos",
                            "Não encontrei tags de gênero nas músicas mais ouvidas deste período.",
                            icons, Layout.TEXT, themeB
                        )
                    } else {
                        RetrospectiveCardRenderer.Model(
                            id, "Seus gêneros favoritos foram…", "", icons, Layout.GENRES, themeB,
                            genres = genres
                        )
                    }
                }
                "010" -> RetrospectiveCardRenderer.Model(
                    id, "Essa é sua retrospectiva!",
                    buildString {
                        append(body.lineSequence().firstOrNull() ?: "")
                        append("\nArtista: ${topArtists.firstOrNull()?.name ?: "—"}")
                        append("\nMúsica: ${topTracks.firstOrNull()?.name ?: "—"}")
                        append("\nÁlbum: ${topAlbums.firstOrNull()?.name ?: "—"}")
                    },
                    icons, Layout.TEXT, themeA
                )
                "001" -> RetrospectiveCardRenderer.Model(id, title, body, icons, Layout.INTRO, themeA)
                "002" -> RetrospectiveCardRenderer.Model(id, title, body, icons, Layout.TEXT, themeB)
                else -> RetrospectiveCardRenderer.Model(id, title, body, icons, Layout.TEXT, themeB)
            }
        }
        return out
    }

    /**
     * Participação de cada gênero nos plays das músicas mais ouvidas, a partir das tags de gênero da
     * biblioteca local (respeita “separar gêneros” e os separadores das configurações da Biblioteca).
     */
    private fun genreStats(
        context: Context,
        tracks: List<Track>,
        rows: List<ChartRow>
    ): List<RetrospectiveCardRenderer.GenreStat> {
        if (tracks.isEmpty() || rows.isEmpty()) return emptyList()
        val prefs = context.getSharedPreferences("tolete", Context.MODE_PRIVATE)
        val split = prefs.getBoolean("split_genres", false)
        val seps = prefs.getString("genre_separators", null) ?: "; /"
        val splitter = TagSplitter(if (split) seps else "")

        val plays = LinkedHashMap<String, Int>()
        val display = HashMap<String, String>()
        for (r in rows) {
            val t = tracks.firstOrNull {
                eq(it.title, r.name) && (r.artist.isBlank() || eq(it.artist, r.artist) ||
                    it.artist.lowercase().contains(r.artist.lowercase()))
            } ?: tracks.firstOrNull { eq(it.title, r.name) } ?: continue
            if (t.genre.isBlank()) continue
            // Quando a tag tem vários gêneros, os plays são divididos entre eles.
            val parts = splitter.split(t.genre).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isEmpty()) continue
            val share = r.plays.toDouble() / parts.size
            for (p in parts) {
                val k = p.lowercase(Locale.ROOT)
                display.putIfAbsent(k, p)
                plays[k] = (plays[k] ?: 0) + Math.round(share).toInt().coerceAtLeast(1)
            }
        }
        val total = plays.values.sum()
        if (total <= 0) return emptyList()
        return plays.entries.sortedByDescending { it.value }.take(5).map {
            RetrospectiveCardRenderer.GenreStat(
                display[it.key].orEmpty(),
                Math.round(it.value * 100f / total).coerceAtLeast(1)
            )
        }
    }

    /** Carrega ic_float_<kind>_1..3 de drawable. */
    fun loadFloatIcons(context: Context, kind: String): List<Bitmap> {
        val prefix = when (kind) {
            "artistas" -> "ic_float_artistas"
            "albuns" -> "ic_float_albuns"
            "generos" -> "ic_float_generos"
            "lastfm" -> "ic_float_lastfm"
            else -> "ic_float_musicas"
        }
        val out = mutableListOf<Bitmap>()
        for (i in 1..3) {
            val id = context.resources.getIdentifier("${prefix}_$i", "drawable", context.packageName)
            if (id == 0) continue
            try {
                BitmapFactory.decodeResource(context.resources, id)?.let { out += it }
            } catch (_: Exception) { }
        }
        return out
    }
}
