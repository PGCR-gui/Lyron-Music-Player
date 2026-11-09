package com.tolete.player.video

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.tolete.player.data.LibraryCache
import com.tolete.player.data.RetrospectiveCardRenderer
import com.tolete.player.data.RetrospectivePhotoGenerator
import com.tolete.player.data.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Gera vídeos de 3–15s (padrão 8s; 1080×1920 @ 30fps) para cada card da retrospectiva:
 * fundo na cor primária da capa/tema, formas e ícones flutuantes, texto/fotos do card,
 * marca d’água Lyron e áudio (top 10 aleatória ou regra especial do card).
 */
object RetrospectiveVideoGenerator {

    private const val TAG = "RetroVideo"
    private const val W = 1080
    private const val H = 1920
    private const val FPS = 30
    const val MIN_DURATION_SEC = 3
    const val MAX_DURATION_SEC = 15
    const val DEFAULT_DURATION_SEC = 8

    /** Escolhas do usuário: duração (3–15 s) e música por card; card sem escolha usa a automática. */
    data class VideoOptions(
        val durationSec: Int = DEFAULT_DURATION_SEC,
        val audioByCard: Map<String, Track> = emptyMap()
    )

    /** Como a música de um card pode ser escolhida. */
    enum class AudioKind { LOCKED, RESTRICTED, FREE }

    class CardAudioOptions(
        val cardId: String,
        val label: String,
        val kind: AudioKind,
        val candidates: List<Track>
    )

    data class CardSpec(
        val id: String,
        val title: String,
        val body: String,
        val icons: String,
        val colorFrom: String
    )

    data class Result(
        val dir: File,
        val videos: List<File>,
        val failed: List<String>
    )

    @Volatile
    private var cancelFlag = false

    fun cancel() { cancelFlag = true }

    /**
     * Lê [manifest.json] na pasta da retrospectiva e gera `001.mp4` … `010.mp4`.
     * [libraryTracks] opcional — se null, tenta carregar via [LibraryCache].
     */
    suspend fun generateAll(
        context: Context,
        exportDir: File,
        libraryTracks: List<Track>? = null,
        theme: RetrospectiveCardRenderer.ThemeColors? = null,
        options: VideoOptions = VideoOptions(),
        onProgress: (cardIndex: Int, total: Int, frac: Float) -> Unit = { _, _, _ -> }
    ): Result = withContext(Dispatchers.IO) {
        cancelFlag = false
        val durationMs = options.durationSec.coerceIn(MIN_DURATION_SEC, MAX_DURATION_SEC) * 1000L
        val app = context.applicationContext
        val manifestFile = File(exportDir, "manifest.json")
        if (!manifestFile.isFile) throw IllegalArgumentException("manifest.json não encontrado")

        val root = JSONObject(manifestFile.readText())
        val cardsArr = root.getJSONArray("cards")
        val cards = buildList {
            for (i in 0 until cardsArr.length()) {
                val o = cardsArr.getJSONObject(i)
                add(
                    CardSpec(
                        id = o.getString("id"),
                        title = o.optString("title"),
                        body = o.optString("body"),
                        icons = o.optString("icons", "musicas"),
                        colorFrom = o.optString("colorFrom", "theme")
                    )
                )
            }
        }

        val tracks = libraryTracks ?: LibraryCache(app).load().orEmpty()
        val topTracks = parseChart(root, "topTracks")
        var topArtists = parseChart(root, "topArtists")
        val topAlbums = parseChart(root, "topAlbums")
        if (topArtists.isEmpty() && topTracks.isNotEmpty()) {
            topArtists = topTracks
                .filter { it.artist.isNotBlank() }
                .groupBy { it.artist.trim() }
                .map { (name, rows) -> ChartRow(name, "", rows.sumOf { it.plays }) }
                .sortedByDescending { it.plays }
        }

        val models = RetrospectivePhotoGenerator.buildModels(app, exportDir, tracks, theme)

        val videos = mutableListOf<File>()
        val failed = mutableListOf<String>()

        for ((index, card) in cards.withIndex()) {
            if (cancelFlag) {
                failed += "cancelado"
                break
            }
            onProgress(index, cards.size, 0f)
            try {
                // Card 005 (música mais ouvida) sempre usa a própria música; os demais aceitam escolha manual.
                val audioTrack = (if (card.id == "005") null else options.audioByCard[card.id])
                    ?: resolveAudioTrack(card, tracks, topTracks, topArtists, topAlbums)
                val model = models.firstOrNull { it.id == card.id }
                    ?: throw IllegalStateException("modelo do card ${card.id} não encontrado")
                val out = File(exportDir, "${card.id}.mp4")
                renderCardVideo(app, card, model, audioTrack, durationMs, out) { f ->
                    onProgress(index, cards.size, f)
                }
                if (cancelFlag) {
                    out.delete()
                    failed += "cancelado"
                    break
                }
                if (out.isFile && out.length() > 1024L) videos += out
                else failed += card.id
            } catch (e: Exception) {
                if (cancelFlag) {
                    failed += "cancelado"
                    break
                }
                Log.e(TAG, "Card ${card.id} falhou", e)
                failed += "${card.id}: ${e.message}"
            }
        }
        Result(exportDir, videos, failed)
    }

    private val cardLabels = mapOf(
        "001" to "Introdução",
        "002" to "Tempo ouvido",
        "003" to "Artista mais ouvido",
        "004" to "Artistas mais ouvidos",
        "005" to "Música mais ouvida",
        "006" to "Músicas mais ouvidas",
        "007" to "Álbum mais ouvido",
        "008" to "Álbuns mais ouvidos",
        "009" to "Gêneros favoritos",
        "010" to "Fechamento"
    )

    /**
     * Músicas que podem ser escolhidas em cada card:
     * - 005 (música mais ouvida): travado, usa a própria música;
     * - 004/006/008 (top 5): só faixas dos artistas/músicas/álbuns listados no card;
     * - demais: qualquer música da biblioteca (as do ranking aparecem primeiro).
     */
    suspend fun audioOptions(
        context: Context,
        exportDir: File,
        libraryTracks: List<Track>? = null
    ): List<CardAudioOptions> = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val root = JSONObject(File(exportDir, "manifest.json").readText())
        val library = libraryTracks ?: LibraryCache(app).load().orEmpty()
        val topTracks = parseChart(root, "topTracks")
        var topArtists = parseChart(root, "topArtists")
        val topAlbums = parseChart(root, "topAlbums")
        if (topArtists.isEmpty() && topTracks.isNotEmpty()) {
            topArtists = topTracks.filter { it.artist.isNotBlank() }.groupBy { it.artist.trim() }
                .map { (n, rows) -> ChartRow(n, "", rows.sumOf { it.plays }) }.sortedByDescending { it.plays }
        }

        val ranked = topTracks.mapNotNull { matchTrack(library, it.name, it.artist) }.distinctBy { it.id }
        val everything = (ranked + library.sortedBy { norm(it.title) }).distinctBy { it.id }

        val listedArtists = topArtists.take(5).flatMap { a ->
            library.filter { norm(it.artist).contains(norm(a.name)) }.sortedBy { norm(it.title) }
        }.distinctBy { it.id }
        val listedTracks = topTracks.take(5).mapNotNull { matchTrack(library, it.name, it.artist) }.distinctBy { it.id }
        val listedAlbums = topAlbums.take(5).flatMap { al ->
            library.filter {
                norm(it.album) == norm(al.name) &&
                    (al.artist.isBlank() || norm(it.artist).contains(norm(al.artist)) || norm(al.artist).contains(norm(it.artist)))
            }.sortedBy { it.trackNumber ?: Int.MAX_VALUE }
        }.distinctBy { it.id }

        val cardsArr = root.getJSONArray("cards")
        buildList {
            for (i in 0 until cardsArr.length()) {
                val id = cardsArr.getJSONObject(i).getString("id")
                val label = cardLabels[id] ?: id
                add(
                    when (id) {
                        "005" -> CardAudioOptions(id, label, AudioKind.LOCKED, emptyList())
                        "004" -> CardAudioOptions(id, label, AudioKind.RESTRICTED, listedArtists)
                        "006" -> CardAudioOptions(id, label, AudioKind.RESTRICTED, listedTracks)
                        "008" -> CardAudioOptions(id, label, AudioKind.RESTRICTED, listedAlbums)
                        else -> CardAudioOptions(id, label, AudioKind.FREE, everything)
                    }
                )
            }
        }
    }

    private data class ChartRow(val name: String, val artist: String, val plays: Int)

    private fun parseChart(root: JSONObject, key: String): List<ChartRow> {
        val arr = root.optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    ChartRow(
                        name = o.optString("name"),
                        artist = o.optString("artist"),
                        plays = o.optInt("plays")
                    )
                )
            }
        }
    }

    private fun norm(s: String) = s.lowercase(Locale.ROOT).trim()

    private fun matchTrack(library: List<Track>, title: String, artist: String): Track? {
        val t = norm(title)
        val a = norm(artist)
        return library.firstOrNull {
            norm(it.title) == t && (a.isBlank() || norm(it.artist).contains(a) || a.contains(norm(it.artist)))
        } ?: library.firstOrNull { norm(it.title) == t }
    }

    private fun resolveAudioTrack(
        card: CardSpec,
        library: List<Track>,
        topTracks: List<ChartRow>,
        topArtists: List<ChartRow>,
        topAlbums: List<ChartRow>
    ): Track? {
        if (library.isEmpty()) return null
        return when (card.id) {
            "005" -> topTracks.firstOrNull()?.let { matchTrack(library, it.name, it.artist) }
            "003" -> {
                val artist = topArtists.firstOrNull()?.name ?: return null
                // música mais tocada do artista (entre tops ou biblioteca)
                topTracks.firstOrNull { norm(it.artist).contains(norm(artist)) }
                    ?.let { matchTrack(library, it.name, it.artist) }
                    ?: library.filter { norm(it.artist).contains(norm(artist)) }
                        .maxByOrNull { it.duration }
            }
            "007" -> {
                val album = topAlbums.firstOrNull() ?: return null
                library.filter {
                    norm(it.album) == norm(album.name) &&
                        (album.artist.isBlank() || norm(it.artist).contains(norm(album.artist)))
                }.maxByOrNull { it.duration }
                    ?: topTracks.firstOrNull()?.let { matchTrack(library, it.name, it.artist) }
            }
            else -> {
                // top 10 aleatória
                val pool = topTracks.take(10).mapNotNull { matchTrack(library, it.name, it.artist) }
                if (pool.isNotEmpty()) pool.random()
                else library.randomOrNull()
            }
        }
    }

    private suspend fun renderCardVideo(
        app: Context,
        card: CardSpec,
        model: RetrospectiveCardRenderer.Model,
        audioTrack: Track?,
        durationMs: Long,
        out: File,
        onProgress: (Float) -> Unit
    ) = coroutineScope {
        val totalFrames = (durationMs * FPS / 1000L).toInt()
        val cache = File(app.cacheDir, "retro_video").apply { mkdirs() }
        val ts = System.currentTimeMillis()
        val videoTmp = File(cache, "rv_${card.id}_v_$ts.mp4")
        val audioTmp = File(cache, "rv_${card.id}_a_$ts.m4a")
        val bitrate = (W.toLong() * H * FPS * 0.10).toInt().coerceIn(2_500_000, 12_000_000)

        val glExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "lyron-retro-gl") }
        val glDispatcher = glExecutor.asCoroutineDispatcher()
        try {
            val audioJob = async(Dispatchers.Default) {
                if (audioTrack != null) {
                    val dur = audioTrack.duration.coerceAtLeast(durationMs + 500)
                    val start = if (dur > durationMs + 1000) {
                        ((dur - durationMs) / 3).coerceAtLeast(0L)
                    } else 0L
                    val end = (start + durationMs).coerceAtMost(dur)
                    try {
                        AudioClipEncoder(app).encode(
                            uri = audioTrack.uri,
                            startMs = start,
                            endMs = end,
                            muxer = AudioMuxer(audioTmp),
                            fadeEdges = true,
                            cancelled = { false }
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Áudio do card ${card.id} falhou", e)
                        audioTmp.delete()
                    }
                }
            }

            val videoJob = async(glDispatcher) {
                val muxer = VideoMuxer(videoTmp)
                var enc: GlVideoEncoder? = null
                val frameBmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
                val assets = RetrospectiveCardRenderer.loadAssets(app, model, audioTrack?.artPath)
                val nowPlaying = audioTrack?.let { RetrospectiveCardRenderer.NowPlaying(it.title, it.artist) }
                var finished = false
                try {
                    enc = GlVideoEncoder(W, H, FPS, bitrate, muxer, gles3 = false)
                    for (i in 0 until totalFrames) {
                        if (cancelFlag) throw CancellationException("cancelado")
                        val tSec = i.toFloat() / FPS
                        RetrospectiveCardRenderer.render(app, frameBmp, model, tSec, assets, nowPlaying)
                        val ptsNs = i * 1_000_000_000L / FPS
                        enc!!.encodeFrame(frameBmp, ptsNs)
                        if (i % 12 == 0) onProgress(i.toFloat() / totalFrames)
                    }
                    enc!!.finish()
                    finished = true
                } finally {
                    if (!finished) enc?.release()
                    frameBmp.recycle()
                    assets.recycle()
                    // Sem isso o MP4 fica sem o índice final (moov) e o AvMuxer não consegue ler o arquivo.
                    muxer.finish()
                }
            }

            videoJob.await()
            audioJob.await()

            val hasAudio = audioTmp.isFile && audioTmp.length() > 512L
            AvMuxer.mux(videoTmp, if (hasAudio) audioTmp else null, out) { false }
            if (!out.exists() || out.length() < 1024L) {
                throw java.io.IOException("Vídeo inválido para card ${card.id}")
            }
            onProgress(1f)
        } finally {
            glDispatcher.close()
            videoTmp.delete()
            audioTmp.delete()
        }
    }
}
