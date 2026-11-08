package com.tolete.player.video

import android.content.Context
import android.util.Log
import com.tolete.player.visualizer.MilkPresetStore
import com.tolete.player.visualizer.ProjectMNative
import java.io.IOException

/**
 * Fundo do vídeo feito pelo projectM (preset .milk). Use SEMPRE na thread do GL do encoder,
 * com o contexto EGL (OpenGL ES 3) ativo: [init] -> [draw] por frame -> [close].
 *
 * Offline o projectM não pode usar o relógio real, senão o movimento sairia na velocidade
 * errada. Por isso o tempo de cada frame é informado ([ProjectMNative.nativeSetFrameTime]) e o
 * áudio vem do [MilkPcmClip], na posição exata do frame. Se a libprojectM do app não tiver essa
 * função, o código cai para o modo "tempo real": espera 1/fps entre os frames (mais lento,
 * mas o movimento fica certo).
 */
class MilkBackground(
    context: Context,
    private val presetPath: String,
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val pcm: MilkPcmClip?
) {
    private companion object {
        const val TAG = "MilkBackground"
        const val MAX_CHUNK = 4096
    }

    private val texturePath = MilkPresetStore.dir(context).absolutePath
    private var handle = 0L
    private var timeApi = true
    private var startNs = 0L
    private var lastEnd = 0L
    private val tmpShort = ShortArray(MAX_CHUNK)
    private val tmpFloat = FloatArray(MAX_CHUNK * 2)

    fun init() {
        if (!ProjectMNative.isAvailable) throw IOException("libprojectM não está disponível neste app")
        handle = ProjectMNative.nativeCreate(width, height, texturePath, 32, 24, fps)
        if (handle == 0L) {
            throw IOException("Não foi possível iniciar o visualizador (o aparelho precisa de OpenGL ES 3)")
        }
        ProjectMNative.nativeLoadPreset(handle, presetPath, false)
    }

    /** Desenha o fundo do frame [frameIndex] no framebuffer atual (a Surface do encoder). */
    fun draw(frameIndex: Int) {
        val h = handle
        if (h == 0L) return

        if (timeApi) {
            val ok = try {
                ProjectMNative.nativeSetFrameTime(h, frameIndex.toDouble() / fps)
            } catch (_: UnsatisfiedLinkError) {
                false
            }
            if (!ok) {
                timeApi = false
                startNs = System.nanoTime() - frameIndex * 1_000_000_000L / fps
                Log.w(TAG, "projectm_set_frame_time indisponível: gerando em tempo real")
            }
        }
        if (!timeApi) {
            val waitNs = startNs + frameIndex * 1_000_000_000L / fps - System.nanoTime()
            if (waitNs > 1_000_000L) {
                try {
                    Thread.sleep(waitNs / 1_000_000L, (waitNs % 1_000_000L).toInt())
                } catch (_: InterruptedException) { }
            }
        }

        feedPcm(h, frameIndex)
        ProjectMNative.nativeRender(h)
    }

    private fun feedPcm(h: Long, frameIndex: Int) {
        val clip = pcm ?: return
        val rate = clip.sampleRate
        val end = maxOf(1024L, frameIndex.toLong() * rate / fps)
        var from = if (frameIndex == 0) 0L else lastEnd
        if (end - from > MAX_CHUNK) from = end - MAX_CHUNK
        val n = clip.read(from, (end - from).toInt().coerceAtLeast(0), tmpShort)
        if (n > 0) {
            for (i in 0 until n) {
                val v = tmpShort[i] / 32768f
                tmpFloat[i * 2] = v
                tmpFloat[i * 2 + 1] = v
            }
            ProjectMNative.nativeAddPcm(h, tmpFloat, n)
        }
        lastEnd = end
    }

    fun close() {
        val h = handle
        if (h != 0L) {
            handle = 0L
            try {
                ProjectMNative.nativeDestroy(h)
            } catch (_: Throwable) { }
        }
    }
}
