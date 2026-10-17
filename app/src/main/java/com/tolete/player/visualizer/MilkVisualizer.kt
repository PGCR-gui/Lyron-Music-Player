package com.tolete.player.visualizer

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Outline
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewOutlineProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay
import java.io.File
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.random.Random

/** Renderer: roda na thread do GL. Toda chamada ao projectM acontece aqui. */
internal class MilkRenderer(private val texturePath: String) : GLSurfaceView.Renderer {

    @Volatile var presets: List<String> = emptyList()
    @Volatile var intervalMs: Long = 30_000L
    @Volatile var onFailure: (() -> Unit)? = null

    private var handle = 0L
    private var width = 0
    private var height = 0
    private var loadedList: List<String> = emptyList()
    private var index = -1
    private var lastSwitch = 0L
    private var failed = false
    private val pcm = FloatArray(MAX_FRAMES * 2)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Contexto GL novo: o da instância antiga já foi destruído junto com o contexto antigo.
        handle = 0L
        loadedList = emptyList()
        index = -1
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w
        height = h
        if (failed || w <= 0 || h <= 0) return
        if (handle == 0L) {
            handle = try {
                ProjectMNative.nativeCreate(w, h, texturePath, 32, 24, 60)
            } catch (t: Throwable) {
                Log.e("ProjectM", "nativeCreate falhou", t); 0L
            }
            if (handle == 0L) {
                failed = true
                Handler(Looper.getMainLooper()).post { onFailure?.invoke() }
                return
            }
            AudioTap.resetReader()
        } else {
            ProjectMNative.nativeResize(handle, w, h)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        if (handle == 0L) {
            GLES30.glClearColor(0f, 0f, 0f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            return
        }

        // lista de presets mudou (ou primeira vez) -> escolhe um aleatório
        val list = presets
        if (list !== loadedList) {
            loadedList = list
            if (list.isNotEmpty()) loadPreset(Random.nextInt(list.size), smooth = false)
        } else if (list.size > 1 && intervalMs > 0 &&
            SystemClock.uptimeMillis() - lastSwitch >= intervalMs
        ) {
            var next = Random.nextInt(list.size)
            if (next == index) next = (next + 1) % list.size
            loadPreset(next, smooth = true)
        }

        val n = AudioTap.drain(pcm, MAX_FRAMES)
        if (n > 0) ProjectMNative.nativeAddPcm(handle, pcm, n)
        ProjectMNative.nativeRender(handle)
    }

    private fun loadPreset(i: Int, smooth: Boolean) {
        val path = loadedList.getOrNull(i) ?: return
        index = i
        lastSwitch = SystemClock.uptimeMillis()
        if (File(path).exists()) ProjectMNative.nativeLoadPreset(handle, path, smooth)
    }

    /** Chamar via GLSurfaceView.queueEvent (precisa do contexto GL ativo). */
    fun release() {
        if (handle != 0L) {
            try { ProjectMNative.nativeDestroy(handle) } catch (_: Throwable) {}
            handle = 0L
        }
    }

    private companion object {
        const val MAX_FRAMES = 2048
    }
}

private tailrec fun Context.findLifecycleOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> baseContext.findLifecycleOwner()
    else -> null
}

/**
 * Visualizador .milk. Ocupa o espaço que o Modifier der (use o mesmo tamanho da capa).
 * Quem chama decide quando mostrar/ocultar e trata o toque (para desligar).
 */
@Composable
fun MilkVisualizer(
    presets: List<String>,
    intervalSeconds: Int,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    onFailure: () -> Unit = {}
) {
    val context = LocalContext.current
    val radiusPx = with(LocalDensity.current) { cornerRadius.toPx() }

    val renderer = remember { MilkRenderer(MilkPresetStore.dir(context).absolutePath) }
    val glView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            preserveEGLContextOnPause = false
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY // quem dá o ritmo é o laço abaixo
            // cantos arredondados iguais aos da capa
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
                }
            }
            clipToOutline = true
        }
    }

    SideEffect {
        renderer.presets = presets
        renderer.intervalMs = intervalSeconds * 1000L
        renderer.onFailure = onFailure
    }

    // Só captura PCM enquanto o visualizador existe
    DisposableEffect(Unit) {
        AudioTap.resetReader()
        AudioTap.active = true
        onDispose { AudioTap.active = false }
    }

    // ~60 quadros/s (limita também em telas de 120 Hz)
    LaunchedEffect(glView) {
        while (true) {
            glView.requestRender()
            delay(16)
        }
    }

    // pausa/retoma o GL junto com a Activity
    val owner = context.findLifecycleOwner()
    DisposableEffect(owner, glView) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> glView.onPause()
                Lifecycle.Event.ON_RESUME -> glView.onResume()
                else -> {}
            }
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose {
            owner?.lifecycle?.removeObserver(obs)
            glView.queueEvent { renderer.release() } // destrói o projectM na thread do GL
            glView.onPause()
        }
    }

    AndroidView(factory = { glView }, modifier = modifier)
}
