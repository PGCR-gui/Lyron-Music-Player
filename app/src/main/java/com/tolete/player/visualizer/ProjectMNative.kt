package com.tolete.player.visualizer

import android.util.Log

/**
 * Ponte JNI com a libprojectM (ver app/src/main/cpp).
 * Se a lib nativa não estiver no APK, [isAvailable] fica false e o app segue normal
 * (o visualizador só avisa que não está disponível).
 *
 * TODAS as funções abaixo devem ser chamadas na thread do GL.
 */
object ProjectMNative {

    val isAvailable: Boolean = try {
        System.loadLibrary("lyron_projectm")
        true
    } catch (t: Throwable) {
        Log.w("ProjectM", "libprojectM não encontrada: ${t.message}")
        false
    }

    external fun nativeCreate(width: Int, height: Int, texturePath: String, meshW: Int, meshH: Int, fps: Int): Long
    external fun nativeResize(handle: Long, width: Int, height: Int)
    external fun nativeAddPcm(handle: Long, interleavedStereo: FloatArray, frames: Int)
    external fun nativeLoadPreset(handle: Long, path: String, smooth: Boolean)
    external fun nativeRender(handle: Long)

    /** Define o tempo do frame (s) para renderização offline. false = a lib não tem essa função. */
    external fun nativeSetFrameTime(handle: Long, seconds: Double): Boolean
    external fun nativeDestroy(handle: Long)
}
