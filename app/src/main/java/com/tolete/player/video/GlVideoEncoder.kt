package com.tolete.player.video

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Encoder H.264 que recebe frames como [Bitmap]. Cada bitmap vira textura GL e é desenhado na
 * Surface de entrada do MediaCodec com timestamp exato (eglPresentationTimeANDROID), então a
 * duração/sincronia do vídeo não depende da velocidade de renderização.
 *
 * IMPORTANTE: o contexto EGL pertence à thread que criou o objeto. Construa e chame
 * [encodeFrame]/[finish]/[release] sempre da mesma thread.
 *
 * As amostras codificadas são lidas por uma thread própria (evita travar quando a fila de saída
 * do codec enche) e gravadas no [VideoMuxer].
 */
class GlVideoEncoder(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    bitrate: Int,
    private val muxer: VideoMuxer
) {
    private val codec: MediaCodec
    private val inputSurface: Surface

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var program = 0
    private var texId = 0
    private var texReady = false
    private var aPos = 0
    private var aTex = 0
    private var uTex = 0
    private val vertices: FloatBuffer

    @Volatile
    private var drainError: Throwable? = null

    @Volatile
    private var stopRequested = false
    private var released = false
    private var drainThread: Thread? = null

    init {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = codec.createInputSurface()
            codec.start()
        } catch (e: Exception) {
            try {
                codec.release()
            } catch (_: Exception) { }
            throw IOException("Este aparelho não consegue codificar vídeo em ${width}x$height", e)
        }

        // x, y, u, v  (triangle strip; v=0 no topo do bitmap)
        val data = floatArrayOf(
            -1f, -1f, 0f, 1f,
            1f, -1f, 1f, 1f,
            -1f, 1f, 0f, 0f,
            1f, 1f, 1f, 0f
        )
        vertices = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(data); position(0) }

        try {
            setupEgl()
            setupGl()
        } catch (e: Exception) {
            release()
            throw e
        }

        drainThread = Thread({ drainLoop() }, "lyron-video-drain").apply { start() }
    }

    /** Desenha [bitmap] (mesmas dimensões do vídeo) com o timestamp [ptsNs]. */
    fun encodeFrame(bitmap: Bitmap, ptsNs: Long) {
        drainError?.let { throw it }
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        if (!texReady) {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            texReady = true
        } else {
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap)
        }

        GLES20.glUseProgram(program)
        GLES20.glUniform1i(uTex, 0)
        vertices.position(0)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(aPos)
        vertices.position(2)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)

        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs)
        if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
            throw IOException("Falha ao enviar frame ao encoder (EGL ${EGL14.eglGetError()})")
        }
    }

    /** Sinaliza fim de stream, espera o encoder esvaziar e libera tudo. */
    fun finish() {
        try {
            codec.signalEndOfInputStream()
            drainThread?.join()
            drainError?.let { throw it }
            if (muxer.samplesWritten == 0L) throw IOException("O encoder de vídeo não produziu frames")
        } finally {
            release()
        }
    }

    fun release() {
        if (released) return
        released = true
        stopRequested = true
        try {
            drainThread?.join(3000)
        } catch (_: Exception) { }
        try {
            codec.stop()
        } catch (_: Exception) { }
        try {
            codec.release()
        } catch (_: Exception) { }
        try {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
                )
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(eglDisplay)
            }
        } catch (_: Exception) { }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
        try {
            inputSurface.release()
        } catch (_: Exception) { }
    }

    // ---------------------------------------------------------------------------------------

    private fun drainLoop() {
        val info = MediaCodec.BufferInfo()
        try {
            while (!stopRequested) {
                val oi = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxer.isStarted) muxer.start(codec.outputFormat)
                    }
                    oi >= 0 -> {
                        val buf = codec.getOutputBuffer(oi)!!
                        if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0
                        if (info.size > 0 && muxer.isStarted) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            muxer.write(buf, info)
                        }
                        codec.releaseOutputBuffer(oi, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                    }
                }
            }
        } catch (t: Throwable) {
            if (!stopRequested) drainError = t
        }
    }

    private fun setupEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) throw IOException("EGL indisponível")
        val ver = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, ver, 0, ver, 1)) throw IOException("eglInitialize falhou")

        val recordable = 0x3142 // EGL_RECORDABLE_ANDROID
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            recordable, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, num, 0) || num[0] <= 0) {
            throw IOException("Nenhuma configuração EGL compatível")
        }
        val ctxAttrs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttrs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) throw IOException("eglCreateContext falhou")
        val surfAttrs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], inputSurface, surfAttrs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) throw IOException("eglCreateWindowSurface falhou")
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw IOException("eglMakeCurrent falhou")
        }
    }

    private fun setupGl() {
        val vs = compile(
            GLES20.GL_VERTEX_SHADER,
            "attribute vec4 aPos;attribute vec2 aTex;varying vec2 vTex;" +
                "void main(){gl_Position=aPos;vTex=aTex;}"
        )
        val fs = compile(
            GLES20.GL_FRAGMENT_SHADER,
            "precision mediump float;varying vec2 vTex;uniform sampler2D uTex;" +
                "void main(){gl_FragColor=texture2D(uTex,vTex);}"
        )
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) throw IOException("Falha ao ligar o programa GL")
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aTex = GLES20.glGetAttribLocation(program, "aTex")
        uTex = GLES20.glGetUniformLocation(program, "uTex")

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texId = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(s)
            GLES20.glDeleteShader(s)
            throw IOException("Shader GL inválido: $log")
        }
        return s
    }
}
