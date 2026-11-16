package com.tolete.player.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.tolete.player.data.LibraryCache
import com.tolete.player.data.RetrospectiveCardRenderer
import com.tolete.player.data.RetrospectivePhotoGenerator
import com.tolete.player.data.Track
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Gera vídeos (padrão 8s; trecho escolhido à mão sem limite máximo; 1080×1920 @ 30 ou 60fps) para cada card da retrospectiva:
 * fundo na cor primária da capa/tema, formas e ícones flutuantes, texto/fotos do card,
 * marca d’água Lyron e áudio (faixas listadas ou regra especial do card).
 */
object RetrospectiveVideoGenerator {

    private const val TAG = "RetroVideo"
    private const val W = 1080
    private const val H = 1920
    const val FPS_30 = 30
    const val FPS_60 = 60
    /** Faixa do controle de duração padrão (cards sem trecho escolhido à mão). */
    const val MIN_DURATION_SEC = 3
    const val MAX_DURATION_SEC = 15
    const val DEFAULT_DURATION_SEC = 8

    /**
     * Duração mínima do trecho que o usuário corta em cada música (define a duração do vídeo).
     * Não há duração máxima: o trecho pode ir até a música inteira.
     */
    const val MIN_CLIP_SEC = 3

    /** Início do corte automático: mesma regra usada no render, para o editor abrir no mesmo ponto. */
    fun autoStartMs(trackDurationMs: Long, clipMs: Long): Long {
        val dur = trackDurationMs.coerceAtLeast(clipMs + 500)
        return if (dur > clipMs + 1000) ((dur - clipMs) / 3).coerceAtLeast(0L) else 0L
    }

    /** Trecho da música escolhido à mão; a duração do vídeo do card é [durationMs]. */
    data class CardClip(val startMs: Long, val endMs: Long) {
        val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
    }

    /**
     * Escolhas do usuário: duração padrão, FPS, música e trecho por card.
     * Card sem trecho em [clipByCard] usa [durationSec] e o corte automático.
     */
    data class VideoOptions(
        val durationSec: Int = DEFAULT_DURATION_SEC,
        val audioByCard: Map<String, Track> = emptyMap(),
        val clipByCard: Map<String, CardClip> = emptyMap(),
        val fps: Int = FPS_30,
        /** Se não for null, só estes cards (ids "001"…"010") são renderizados; null = todos. */
        val onlyCardIds: Set<String>? = null
    )

    /** Como a música de um card pode ser escolhida. */
    enum class AudioKind { LOCKED, RESTRICTED, FREE }

    class CardAudioOptions(
        val cardId: String,
        val label: String,
        val kind: AudioKind,
        val candidates: List<Track>,
        /** Para cards LOCKED: a música fixa do card (não dá para trocar, mas dá para escolher o trecho). */
        val lockedTrack: Track? = null
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
    private var nextGenerationId = 0L
    private var activeGenerationId = 0L

    /** Estado global: a geração roda fora da tela, então sobrevive a recriação da Activity. */
    sealed interface State {
        object Idle : State
        data class Running(
            val index: Int,
            val total: Int,
            val frac: Float,
            val title: String = "",
            val stage: String = "Preparando…",
            val overallFrac: Float = frac,
            val batch: Boolean = total > 1,
            val generationId: Long = 0L
        ) : State
        data class Finished(val result: Result, val generationId: Long = 0L) : State
        data class Failed(val message: String, val generationId: Long = 0L) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    fun isRunning(): Boolean = _state.value is State.Running

    /** Limpa Finished/Failed depois que a tela tratou o resultado. */
    fun consume() {
        if (_state.value !is State.Running) _state.value = State.Idle
    }

    fun cancel() {
        cancelFlag = true
        (_state.value as? State.Running)?.let { running ->
            _state.value = running.copy(stage = "Cancelando…")
        }
    }

    private fun newGenerationId(): Long = synchronized(this) {
        (++nextGenerationId).also { activeGenerationId = it }
    }

    private fun publishProgress(
        generationId: Long,
        index: Int,
        total: Int,
        frac: Float,
        title: String,
        stage: String,
        batch: Boolean
    ) {
        if (generationId != activeGenerationId) return
        val safeTotal = total.coerceAtLeast(1)
        val safeFrac = frac.coerceIn(0f, 1f)
        _state.value = State.Running(
            index = index,
            total = safeTotal,
            frac = safeFrac,
            title = title,
            stage = if (cancelFlag) "Cancelando…" else stage,
            overallFrac = ((index + safeFrac) / safeTotal).coerceIn(0f, 1f),
            batch = batch,
            generationId = generationId
        )
    }

    /**
     * Inicia a geração em escopo próprio do app, com serviço em primeiro plano (notificação de
     * progresso) e WakeLock, para o sistema não matar o processo no meio da renderização.
     */
    fun start(
        context: Context,
        exportDir: File,
        libraryTracks: List<Track>? = null,
        theme: RetrospectiveCardRenderer.ThemeColors? = null,
        options: VideoOptions = VideoOptions()
    ): Boolean {
        if (isRunning()) return false
        val app = context.applicationContext
        cancelFlag = false
        val generationId = newGenerationId()
        publishProgress(generationId, 0, 1, 0f, "Retrospectiva", "Preparando vídeos…", true)
        try {
            ContextCompat.startForegroundService(app, Intent(app, RetrospectiveVideoService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível iniciar o serviço de progresso", e)
        }
        job = scope.launch {
            val wl = try {
                (app.getSystemService(Context.POWER_SERVICE) as PowerManager)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lyron:retrovideo")
                    .apply { acquire(60 * 60 * 1000L) }
            } catch (_: Exception) {
                null
            }
            try {
                val result = generateAll(app, exportDir, libraryTracks, theme, options) { idx, total, frac, title, stage ->
                    publishProgress(generationId, idx, total, frac, title, stage, true)
                }
                _state.value = State.Finished(result, generationId)
            } catch (e: CancellationException) {
                _state.value = State.Failed("cancelado", generationId)
            } catch (t: Throwable) {
                Log.e(TAG, "Geração dos vídeos falhou", t)
                _state.value = State.Failed(t.message ?: t.javaClass.simpleName, generationId)
            } finally {
                try {
                    if (wl?.isHeld == true) wl.release()
                } catch (_: Exception) { }
            }
        }
        return true
    }

    /** Gera um vídeo para um info card do Last.fm, usando o mesmo pipeline H.264/AAC/MP4. */
    fun startCardVideo(
        context: Context,
        cardImage: File,
        audioTrack: Track,
        clip: CardClip,
        output: File
    ): Boolean {
        if (isRunning()) return false
        if (!cardImage.isFile) return false
        val app = context.applicationContext
        cancelFlag = false
        val generationId = newGenerationId()
        publishProgress(generationId, 0, 1, 0f, audioTrack.title, "Preparando vídeo…", false)
        try {
            ContextCompat.startForegroundService(app, Intent(app, RetrospectiveVideoService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível iniciar o serviço de progresso", e)
        }
        job = scope.launch {
            val wl = try {
                (app.getSystemService(Context.POWER_SERVICE) as PowerManager)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lyron:lfcardvideo")
                    .apply { acquire(60 * 60 * 1000L) }
            } catch (_: Exception) {
                null
            }
            try {
                renderInfoCardVideo(app, cardImage, audioTrack, clip, output) { frac ->
                    publishProgress(
                        generationId, 0, 1, frac, audioTrack.title,
                        if (frac >= 1f) "Finalizando vídeo…" else "Renderizando vídeo", false
                    )
                }
                _state.value = State.Finished(
                    Result(output.parentFile ?: app.cacheDir, listOf(output), emptyList()), generationId
                )
            } catch (e: CancellationException) {
                output.delete()
                _state.value = State.Failed("cancelado", generationId)
            } catch (t: Throwable) {
                output.delete()
                Log.e(TAG, "Geração do vídeo do info card falhou", t)
                _state.value = State.Failed(
                    if (cancelFlag) "cancelado" else (t.message ?: t.javaClass.simpleName), generationId
                )
            } finally {
                try {
                    if (wl?.isHeld == true) wl.release()
                } catch (_: Exception) { }
            }
        }
        return true
    }

    /** Renderiza uma imagem de info card em movimento suave e adiciona o trecho escolhido. */
    private suspend fun renderInfoCardVideo(
        app: Context,
        cardImage: File,
        audioTrack: Track,
        clip: CardClip,
        out: File,
        onProgress: (Float) -> Unit
    ) = coroutineScope {
        val durationMs = clip.durationMs.coerceAtLeast(1L)
        val startMs = clip.startMs.coerceIn(0L, (audioTrack.duration - durationMs).coerceAtLeast(0L))
        val endMs = (startMs + durationMs).coerceAtMost(audioTrack.duration.coerceAtLeast(durationMs))
        val actualDurationMs = (endMs - startMs).coerceAtLeast(1L)
        val fps = FPS_30
        val totalFrames = (actualDurationMs * fps / 1000L).toInt().coerceAtLeast(1)
        val cache = File(app.cacheDir, "retro_video").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val videoTmp = File(cache, "lf_card_v_$stamp.mp4")
        val audioTmp = File(cache, "lf_card_a_$stamp.m4a")
        val bitrate = (W.toLong() * H * fps * 0.10).toInt().coerceIn(2_500_000, 12_000_000)
        out.parentFile?.mkdirs()

        val glExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "lyron-lf-card-gl") }
        val glDispatcher = glExecutor.asCoroutineDispatcher()
        try {
            val audioJob = async(Dispatchers.IO) {
                AudioClipEncoder(app).encode(
                    uri = audioTrack.uri,
                    startMs = startMs,
                    endMs = endMs,
                    muxer = AudioMuxer(audioTmp),
                    fadeEdges = true,
                    cancelled = { cancelFlag }
                )
            }

            val videoJob = async(glDispatcher) {
                val card = BitmapFactory.decodeFile(cardImage.absolutePath)
                    ?: throw java.io.IOException("Não foi possível abrir a imagem do info card")
                val frame = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(frame)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                val muxer = VideoMuxer(videoTmp)
                var encoder: GlVideoEncoder? = null
                var finished = false
                try {
                    encoder = GlVideoEncoder(W, H, fps, bitrate, muxer, gles3 = false)
                    for (i in 0 until totalFrames) {
                        if (cancelFlag) throw CancellationException("cancelado")
                        val p = if (totalFrames <= 1) 1f else i.toFloat() / (totalFrames - 1)
                        val zoom = 1.015f + 0.045f * p
                        val dw = W * zoom
                        val dh = H * zoom
                        canvas.drawColor(Color.BLACK)
                        canvas.drawBitmap(
                            card, null,
                            RectF((W - dw) / 2f, (H - dh) / 2f, (W + dw) / 2f, (H + dh) / 2f),
                            paint
                        )
                        encoder!!.encodeFrame(frame, i * 1_000_000_000L / fps)
                        if (i % (fps / 2).coerceAtLeast(1) == 0) onProgress(i.toFloat() / totalFrames)
                    }
                    encoder!!.finish()
                    finished = true
                } finally {
                    if (!finished) encoder?.release()
                    frame.recycle()
                    card.recycle()
                    // Escreve o índice final MP4 antes do remux.
                    muxer.finish()
                }
            }

            videoJob.await()
            audioJob.await()
            if (!audioTmp.isFile || audioTmp.length() <= 512L) {
                throw java.io.IOException("O trecho de música não pôde ser codificado")
            }
            AvMuxer.mux(videoTmp, audioTmp, out) { cancelFlag }
            if (!out.isFile || out.length() < 1024L) throw java.io.IOException("Vídeo MP4 inválido")
            onProgress(1f)
        } finally {
            glDispatcher.close()
            videoTmp.delete()
            audioTmp.delete()
            cardImage.delete()
        }
    }

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
        onProgress: (cardIndex: Int, total: Int, frac: Float, title: String, stage: String) -> Unit =
            { _, _, _, _, _ -> }
    ): Result = withContext(Dispatchers.IO) {
        val defaultDurationMs = options.durationSec.coerceIn(MIN_DURATION_SEC, MAX_DURATION_SEC) * 1000L
        val fps = if (options.fps == FPS_60) FPS_60 else FPS_30
        val app = context.applicationContext
        val manifestFile = File(exportDir, "manifest.json")
        if (!manifestFile.isFile) throw IllegalArgumentException("manifest.json não encontrado")

        val root = JSONObject(manifestFile.readText())
        val cardsArr = root.getJSONArray("cards")
        val cards = buildList<CardSpec> {
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
        }.let { all ->
            val only = options.onlyCardIds
            if (only == null) all else all.filter { it.id in only }
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
            val cardTitle = cardLabels[card.id] ?: card.title.ifBlank { "Card ${card.id}" }
            onProgress(index, cards.size, 0f, cardTitle, "Preparando card…")
            try {
                // Card 005 (música mais ouvida) sempre usa a própria música (só o trecho é editável).
                val audioTrack = (if (card.id == "005") null else options.audioByCard[card.id])
                    ?: resolveAudioTrack(card, tracks, topTracks, topArtists, topAlbums)
                // Trecho escolhido à mão: define a duração do vídeo deste card.
                val clip = audioTrack?.let { options.clipByCard[card.id] }
                val durationMs = clip?.durationMs
                    ?.coerceAtLeast(MIN_CLIP_SEC * 1000L)
                    ?: defaultDurationMs
                val manualStartMs = clip?.startMs
                val model = models.firstOrNull { it.id == card.id }
                    ?: throw IllegalStateException("modelo do card ${card.id} não encontrado")
                val out = File(exportDir, "${card.id}.mp4")
                renderCardVideo(app, card, model, audioTrack, manualStartMs, durationMs, fps, out) { f ->
                    onProgress(
                        index, cards.size, f, cardTitle,
                        if (f >= 1f) "Finalizando vídeo…" else "Renderizando vídeo"
                    )
                }
                if (cancelFlag) {
                    out.delete()
                    failed += "cancelado"
                    break
                }
                if (out.isFile && out.length() > 1024L) videos += out
                else failed += card.id
            } catch (e: CancellationException) {
                File(exportDir, "${card.id}.mp4").delete()
                failed += "cancelado"
                break
            } catch (t: Throwable) {
                // Inclui OutOfMemoryError: o card falha, mas o app não fecha.
                if (cancelFlag) {
                    File(exportDir, "${card.id}.mp4").delete()
                    failed += "cancelado"
                    break
                }
                Log.e(TAG, "Card ${card.id} falhou", t)
                failed += "${card.id}: ${t.message ?: t.javaClass.simpleName}"
                System.gc()
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
     * - 003/007: só faixas do artista/álbum mais ouvido;
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
            library.filter { artistMatches(it.artist, a.name) }.sortedBy { norm(it.title) }
        }.distinctBy { it.id }
        val mostHeardArtist = topArtists.firstOrNull()?.let { artist ->
            library.filter { artistMatches(it.artist, artist.name) }.sortedBy { norm(it.title) }
        }.orEmpty().distinctBy { it.id }
        val listedTracks = topTracks.take(5).mapNotNull { matchTrack(library, it.name, it.artist) }.distinctBy { it.id }
        val mostHeardAlbum = topAlbums.firstOrNull()?.let { album ->
            library.filter {
                norm(it.album) == norm(album.name) &&
                    (album.artist.isBlank() || artistMatches(it.artist, album.artist))
            }.sortedBy { it.trackNumber ?: Int.MAX_VALUE }
        }.orEmpty().distinctBy { it.id }
        val listedAlbums = topAlbums.take(5).flatMap { al ->
            library.filter {
                norm(it.album) == norm(al.name) &&
                    (al.artist.isBlank() || artistMatches(it.artist, al.artist))
            }.sortedBy { it.trackNumber ?: Int.MAX_VALUE }
        }.distinctBy { it.id }

        val cardsArr = root.getJSONArray("cards")
        buildList {
            for (i in 0 until cardsArr.length()) {
                val id = cardsArr.getJSONObject(i).getString("id")
                val label = cardLabels[id] ?: id
                add(
                    when (id) {
                        "005" -> CardAudioOptions(
                            id, label, AudioKind.LOCKED, emptyList(),
                            lockedTrack = topTracks.firstOrNull()?.let { matchTrack(library, it.name, it.artist) }
                        )
                        "003" -> CardAudioOptions(id, label, AudioKind.RESTRICTED, mostHeardArtist)
                        "004" -> CardAudioOptions(id, label, AudioKind.RESTRICTED, listedArtists)
                        "006" -> CardAudioOptions(id, label, AudioKind.RESTRICTED, listedTracks)
                        "007" -> CardAudioOptions(id, label, AudioKind.RESTRICTED, mostHeardAlbum)
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

    private fun artistMatches(localArtist: String, rankedArtist: String): Boolean {
        val ranked = norm(rankedArtist)
        if (ranked.isBlank()) return false
        val local = norm(localArtist)
        val boundaryMatch = Regex("(^|[^\\p{L}\\p{N}])${Regex.escape(ranked)}($|[^\\p{L}\\p{N}])")
        return boundaryMatch.containsMatchIn(local)
    }

    private fun matchTrack(library: List<Track>, title: String, artist: String): Track? {
        val t = norm(title)
        val a = norm(artist)
        val titleMatches = library.filter { norm(it.title) == t }
        return if (a.isBlank()) titleMatches.firstOrNull()
        else titleMatches.firstOrNull { artistMatches(it.artist, a) }
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
                // O automático só sorteia entre faixas locais do artista exibido no card.
                library.filter { artistMatches(it.artist, artist) }.randomOrNull()
            }
            "007" -> {
                val album = topAlbums.firstOrNull() ?: return null
                library.filter {
                    norm(it.album) == norm(album.name) &&
                        (album.artist.isBlank() || artistMatches(it.artist, album.artist))
                }.randomOrNull()
            }
            else -> {
                // Somente as cinco músicas mostradas no card; nunca cair para toda a biblioteca.
                topTracks.take(5).mapNotNull { matchTrack(library, it.name, it.artist) }.randomOrNull()
            }
        }
    }

    private suspend fun renderCardVideo(
        app: Context,
        card: CardSpec,
        model: RetrospectiveCardRenderer.Model,
        audioTrack: Track?,
        manualStartMs: Long?,
        durationMs: Long,
        fps: Int,
        out: File,
        onProgress: (Float) -> Unit
    ) = coroutineScope {
        val totalFrames = (durationMs * fps / 1000L).toInt()
        val cache = File(app.cacheDir, "retro_video").apply { mkdirs() }
        val ts = System.currentTimeMillis()
        val videoTmp = File(cache, "rv_${card.id}_v_$ts.mp4")
        val audioTmp = File(cache, "rv_${card.id}_a_$ts.m4a")
        val bitrate = (W.toLong() * H * fps * 0.10).toInt().coerceIn(2_500_000, 12_000_000)

        val glExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "lyron-retro-gl") }
        val glDispatcher = glExecutor.asCoroutineDispatcher()
        try {
            val audioJob = async(Dispatchers.Default) {
                if (audioTrack != null) {
                    val dur = audioTrack.duration.coerceAtLeast(durationMs + 500)
                    val start = manualStartMs?.coerceIn(0L, (audioTrack.duration - durationMs).coerceAtLeast(0L))
                        ?: autoStartMs(audioTrack.duration, durationMs)
                    val end = (start + durationMs).coerceAtMost(dur)
                    try {
                        AudioClipEncoder(app).encode(
                            uri = audioTrack.uri,
                            startMs = start,
                            endMs = end,
                            muxer = AudioMuxer(audioTmp),
                            fadeEdges = true,
                            cancelled = { cancelFlag }
                        )
                    } catch (e: Throwable) {
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
                    enc = GlVideoEncoder(W, H, fps, bitrate, muxer, gles3 = false)
                    for (i in 0 until totalFrames) {
                        if (cancelFlag) throw CancellationException("cancelado")
                        val tSec = i.toFloat() / fps
                        RetrospectiveCardRenderer.render(app, frameBmp, model, tSec, assets, nowPlaying)
                        val ptsNs = i * 1_000_000_000L / fps
                        enc!!.encodeFrame(frameBmp, ptsNs)
                        if (i % (fps / 2).coerceAtLeast(1) == 0) onProgress(i.toFloat() / totalFrames)
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
            AvMuxer.mux(videoTmp, if (hasAudio) audioTmp else null, out) { cancelFlag }
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
