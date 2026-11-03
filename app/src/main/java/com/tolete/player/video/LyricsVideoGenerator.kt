package com.tolete.player.video

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import androidx.core.content.ContextCompat
import android.media.MediaCodecList
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import com.tolete.player.data.LyricLine
import com.tolete.player.data.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Gera um MP4 (H.264 + AAC) com a letra sincronizada de um trecho (ou da faixa inteira).
 *
 * Pipeline:
 *  1. vídeo: [LyricsFrameRenderer] → [GlVideoEncoder] → [VideoMuxer] (temp .mp4, só vídeo)
 *  2. áudio: [AudioClipEncoder] → [AudioMuxer] (temp .m4a, só áudio)  — em paralelo com o 1
 *  3. [AvMuxer] junta os dois sem recodificar no arquivo final
 *
 * O trabalho roda num escopo próprio do app, então continua se a tela for fechada.
 */
object LyricsVideoGenerator {

    private const val TAG = "LyricsVideoGenerator"
    private val SHORT_SIDES = listOf(480, 720, 1080, 1440)

    /** Proporção do vídeo; [w] e [h] são a razão largura:altura. */
    enum class Aspect(val label: String, val w: Float, val h: Float) {
        V_9_16("9:16", 9f, 16f),
        V_9_195("9:19.5", 9f, 19.5f),
        H_16_9("16:9", 16f, 9f),
        H_195_9("19.5:9", 19.5f, 9f);

        val vertical: Boolean get() = h > w
    }

    val FPS_OPTIONS = listOf(30, 60)

    data class Options(
        val startMs: Long,
        val endMs: Long,
        val shortSide: Int,
        val aspect: Aspect,
        val fps: Int,
        val style: LyricsVideoStyle
    )

    sealed interface State {
        object Idle : State
        data class Running(val trackTitle: String, val stage: String, val progress: Float) : State
        data class Done(val file: File, val trackTitle: String, val artist: String) : State
        /** Salvo direto na pasta (app minimizado ao terminar). */
        data class Saved(val file: File, val trackTitle: String) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val attachedUis = AtomicInteger(0)

    /** A tela do Now Playing avisa quando está visível (para saber se pode mostrar o resultado). */
    fun attachUi() {
        attachedUis.incrementAndGet()
    }

    fun detachUi() {
        attachedUis.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    fun isRunning(): Boolean = _state.value is State.Running

    /** Limpa Done/Failed depois que a UI tratou o resultado. */
    fun consume() {
        if (_state.value !is State.Running) _state.value = State.Idle
    }

    fun cancel() {
        job?.cancel()
    }

    fun dimsFor(shortSide: Int, aspect: Aspect): Pair<Int, Int> {
        val ratio = max(aspect.w, aspect.h) / min(aspect.w, aspect.h)
        val long = ((shortSide * ratio / 2f).roundToInt()) * 2
        return if (aspect.vertical) shortSide to long else long to shortSide
    }

    /** Resoluções que o encoder H.264 do aparelho aceita para essa proporção e taxa de quadros. */
    fun supportedShortSides(aspect: Aspect, fps: Int): List<Int> {
        val fallback = listOf(480, 720)
        return try {
            val encoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
                info.isEncoder && info.supportedTypes.any { it.equals("video/avc", ignoreCase = true) }
            }
            SHORT_SIDES.filter { s ->
                val (w, h) = dimsFor(s, aspect)
                encoders.any { info ->
                    try {
                        val v = info.getCapabilitiesForType("video/avc").videoCapabilities
                        v.areSizeAndRateSupported(w, h, fps.toDouble()) ||
                            v.areSizeAndRateSupported(h, w, fps.toDouble())
                    } catch (_: Exception) {
                        false
                    }
                }
            }.ifEmpty { fallback }
        } catch (_: Exception) {
            fallback
        }
    }

    // ---- app em primeiro plano? (para decidir entre perguntar ou salvar direto) ----
    private val startedActivities = AtomicInteger(0)
    private var tracking = false

    @Synchronized
    private fun ensureTracking(app: Context) {
        if (tracking) return
        tracking = true
        // start() vem de um toque do usuário, então o app está visível agora.
        startedActivities.set(1)
        (app as Application).registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities.incrementAndGet()
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities.updateAndGet { (it - 1).coerceAtLeast(0) }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    fun isAppForeground(): Boolean = startedActivities.get() > 0

    fun start(context: Context, track: Track, lyrics: List<LyricLine>, options: Options): Boolean {
        if (isRunning()) return false
        val app = context.applicationContext
        ensureTracking(app)
        _state.value = State.Running(track.title, "Preparando…", 0f)
        try {
            ContextCompat.startForegroundService(app, Intent(app, LyricsVideoService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível iniciar o serviço de progresso", e)
        }

        job = scope.launch {
            val wl = try {
                (app.getSystemService(Context.POWER_SERVICE) as PowerManager)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lyron:lyricsvideo")
                    .apply { acquire(60 * 60 * 1000L) }
            } catch (_: Exception) {
                null
            }
            try {
                val out = generate(app, track, lyrics, options)
                if (!isAppForeground()) {
                    // App minimizado: salva direto na pasta em vez de perguntar.
                    val saved = saveToFolder(app, out)
                    _state.value = if (saved != null) State.Saved(saved, track.title)
                    else State.Done(out, track.title, track.artist)
                } else {
                    _state.value = State.Done(out, track.title, track.artist)
                    if (attachedUis.get() == 0) {
                        toast(app, "Vídeo com letra pronto! Abra o Tocando agora para salvar ou compartilhar.")
                    }
                }
            } catch (e: CancellationException) {
                _state.value = State.Idle
            } catch (t: Throwable) {
                Log.e(TAG, "Falha ao gerar vídeo", t)
                _state.value = State.Failed(friendly(t))
            } finally {
                try {
                    if (wl?.isHeld == true) wl.release()
                } catch (_: Exception) { }
            }
        }
        return true
    }

    // ---------------------------------------------------------------------------------------

    private suspend fun generate(
        app: Context,
        track: Track,
        lyrics: List<LyricLine>,
        options: Options
    ): File = coroutineScope {
        val job = coroutineContext[Job]!!
        val cancelled = { !job.isActive }

        val dir = File(app.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()
            ?.filter { it.name.startsWith("lyric_video_") || it.name.startsWith("lv_tmp_") }
            ?.forEach { it.delete() }

        val ts = System.currentTimeMillis()
        val videoTmp = File(dir, "lv_tmp_video_$ts.mp4")
        val audioTmp = File(dir, "lv_tmp_audio_$ts.m4a")
        val out = File(dir, "lyric_video_$ts.mp4")

        val (w, h) = dimsFor(options.shortSide, options.aspect)
        val startMs = options.startMs.coerceAtLeast(0L)
        val endMs = options.endMs.coerceAtLeast(startMs + 1000L)
        val durMs = endMs - startMs
        val totalFrames = ceil(durMs * options.fps / 1000.0).toInt().coerceAtLeast(1)
        val isWhole = startMs <= 0L && endMs >= track.duration - 50L
        val bitrate = (w.toLong() * h * options.fps * 0.12).toInt().coerceIn(1_500_000, 40_000_000)

        fun publish(stage: String, videoFrac: Float) {
            _state.value = State.Running(track.title, stage, (videoFrac * 0.93f).coerceIn(0f, 0.93f))
        }
        publish("Renderizando vídeo…", 0f)

        val glExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "lyron-video-gl") }
        val glDispatcher = glExecutor.asCoroutineDispatcher()
        try {
            val audioJob = async(Dispatchers.Default) {
                AudioClipEncoder(app).encode(
                    uri = track.uri,
                    startMs = startMs,
                    endMs = endMs,
                    muxer = AudioMuxer(audioTmp),
                    fadeEdges = !isWhole,
                    cancelled = cancelled
                )
            }
            val videoJob = async(glDispatcher) {
                renderVideo(
                    app, track, lyrics, options, w, h, startMs, totalFrames, bitrate,
                    videoTmp, cancelled
                ) { f -> publish("Renderizando vídeo…", f) }
            }
            videoJob.await()
            audioJob.await()

            _state.value = State.Running(track.title, "Juntando áudio e vídeo…", 0.95f)
            withContext(Dispatchers.IO) { AvMuxer.mux(videoTmp, audioTmp, out, cancelled) }
            if (!out.exists() || out.length() < 1024L) throw IOException("Arquivo de vídeo inválido")
            out
        } catch (t: Throwable) {
            out.delete()
            throw t
        } finally {
            glDispatcher.close()
            videoTmp.delete()
            audioTmp.delete()
        }
    }

    private fun renderVideo(
        app: Context,
        track: Track,
        lyrics: List<LyricLine>,
        options: Options,
        w: Int,
        h: Int,
        startMs: Long,
        totalFrames: Int,
        bitrate: Int,
        videoTmp: File,
        cancelled: () -> Boolean,
        onProgress: (Float) -> Unit
    ) {
        val muxer = VideoMuxer(videoTmp)
        var renderer: LyricsFrameRenderer? = null
        var enc: GlVideoEncoder? = null
        var finished = false
        try {
            renderer = LyricsFrameRenderer(app, track, lyrics, options.style, w, h)
            enc = GlVideoEncoder(w, h, options.fps, bitrate, muxer)
            for (i in 0 until totalFrames) {
                if (cancelled()) throw CancellationException("cancelled")
                val tMs = startMs + i * 1000L / options.fps
                enc.encodeFrame(renderer.render(tMs), i * 1_000_000_000L / options.fps)
                if (i % 6 == 0) onProgress((i + 1f) / totalFrames)
            }
            enc.finish()
            finished = true
            onProgress(1f)
        } finally {
            if (!finished) enc?.release()
            renderer?.close()
            muxer.finish()
        }
    }

    /** Copia o vídeo para Lyron/Lyrics Videos e avisa a galeria. */
    private fun saveToFolder(app: Context, file: File): File? = try {
        val root = com.tolete.player.data.LyronStorage.lyricsVideosDir(app)
        root.mkdirs()
        val dest = File(root, file.name)
        file.inputStream().use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
        MediaScannerConnection.scanFile(app, arrayOf(dest.absolutePath), arrayOf("video/mp4"), null)
        dest
    } catch (e: Exception) {
        Log.e(TAG, "Falha ao salvar vídeo", e)
        null
    }

    private fun friendly(t: Throwable): String = when (t) {
        is OutOfMemoryError -> "Memória insuficiente. Tente uma resolução menor."
        is IOException -> t.message ?: "Erro de arquivo ao gerar o vídeo"
        else -> "Erro ao gerar o vídeo: ${t.message ?: t.javaClass.simpleName}"
    }

    private fun toast(context: Context, msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
}
