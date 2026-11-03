package com.tolete.player.video

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Muxer de uma única trilha (MP4). Os encoders chamam [start] quando o formato de saída
 * fica conhecido e depois [write] para cada amostra codificada.
 */
abstract class SingleTrackMuxer(val file: File) {
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1

    @Volatile
    var isStarted: Boolean = false
        private set

    @Volatile
    var samplesWritten: Long = 0L
        private set

    fun start(format: MediaFormat) {
        check(!isStarted) { "Muxer já iniciado" }
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()
        val m = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        trackIndex = m.addTrack(format)
        m.start()
        muxer = m
        isStarted = true
    }

    /** [buf] já deve estar com position/limit apontando para a amostra. */
    fun write(buf: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (!isStarted || info.size <= 0) return
        muxer?.writeSampleData(trackIndex, buf, info)
        samplesWritten++
    }

    fun finish() {
        try {
            if (isStarted) muxer?.stop()
        } catch (_: Exception) {
            // stop() lança se nenhuma amostra foi escrita; o arquivo é descartado depois
        }
        try {
            muxer?.release()
        } catch (_: Exception) { }
        muxer = null
        isStarted = false
    }
}

/** Muxer só de áudio (AAC em .m4a/.mp4). */
class AudioMuxer(file: File) : SingleTrackMuxer(file)

/** Muxer só de vídeo (H.264 em .mp4). */
class VideoMuxer(file: File) : SingleTrackMuxer(file)

/**
 * Junta o vídeo e o áudio já codificados num único MP4 sem recodificar (apenas remux),
 * intercalando as amostras por timestamp.
 */
object AvMuxer {

    private const val BUFFER_SIZE = 4 * 1024 * 1024

    fun mux(
        video: File,
        audio: File?,
        out: File,
        cancelled: () -> Boolean = { false }
    ) {
        val vEx = MediaExtractor()
        var aEx: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var started = false
        try {
            vEx.setDataSource(video.absolutePath)
            val vIdx = findTrack(vEx, "video/")
            if (vIdx < 0) throw IOException("Trilha de vídeo ausente")
            vEx.selectTrack(vIdx)

            var aIdx = -1
            if (audio != null && audio.exists() && audio.length() > 0L) {
                val ex = MediaExtractor()
                ex.setDataSource(audio.absolutePath)
                aIdx = findTrack(ex, "audio/")
                if (aIdx >= 0) {
                    ex.selectTrack(aIdx)
                    aEx = ex
                } else {
                    ex.release()
                }
            }

            if (out.exists()) out.delete()
            val m = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            val vOut = m.addTrack(vEx.getTrackFormat(vIdx))
            val aOut = aEx?.let { m.addTrack(it.getTrackFormat(aIdx)) } ?: -1
            m.start()
            started = true

            val buf = ByteBuffer.allocateDirect(BUFFER_SIZE)
            val info = MediaCodec.BufferInfo()

            while (true) {
                if (cancelled()) throw kotlinx.coroutines.CancellationException("cancelled")
                val vt = vEx.sampleTime
                val at = aEx?.sampleTime ?: -1L
                val useVideo = when {
                    vt < 0 && at < 0 -> break
                    vt < 0 -> false
                    at < 0 -> true
                    else -> vt <= at
                }
                val ex = if (useVideo) vEx else aEx!!
                val track = if (useVideo) vOut else aOut
                buf.clear()
                val size = ex.readSampleData(buf, 0)
                if (size < 0) {
                    // esta fonte acabou; força o outro lado a terminar
                    if (useVideo) {
                        if (at < 0) break
                        // drena o áudio restante
                        drain(aEx!!, m, aOut, buf, info)
                        break
                    } else {
                        drain(vEx, m, vOut, buf, info)
                        break
                    }
                }
                info.set(0, size, ex.sampleTime, ex.sampleFlags)
                m.writeSampleData(track, buf, info)
                ex.advance()
            }
        } finally {
            try {
                if (started) muxer?.stop()
            } catch (_: Exception) { }
            try {
                muxer?.release()
            } catch (_: Exception) { }
            try {
                vEx.release()
            } catch (_: Exception) { }
            try {
                aEx?.release()
            } catch (_: Exception) { }
        }
    }

    private fun drain(
        ex: MediaExtractor,
        m: MediaMuxer,
        track: Int,
        buf: ByteBuffer,
        info: MediaCodec.BufferInfo
    ) {
        while (true) {
            buf.clear()
            val size = ex.readSampleData(buf, 0)
            if (size < 0) return
            info.set(0, size, ex.sampleTime, ex.sampleFlags)
            m.writeSampleData(track, buf, info)
            ex.advance()
        }
    }

    private fun findTrack(ex: MediaExtractor, prefix: String): Int {
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(prefix)) return i
        }
        return -1
    }
}
