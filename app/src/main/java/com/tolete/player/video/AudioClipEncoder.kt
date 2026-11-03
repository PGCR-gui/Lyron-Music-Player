package com.tolete.player.video

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Decodifica o trecho [startMs, endMs] da faixa (MediaExtractor + MediaCodec), converte para
 * estéreo/mono, reamostra se preciso e codifica em AAC-LC, gravando no [AudioMuxer].
 *
 * Só lê formatos que o MediaCodec do aparelho decodifica (MP3, AAC/M4A, FLAC, OGG, OPUS, WAV...).
 */
class AudioClipEncoder(private val context: Context) {

    fun encode(
        uri: Uri,
        startMs: Long,
        endMs: Long,
        muxer: AudioMuxer,
        fadeEdges: Boolean,
        cancelled: () -> Boolean,
        onProgress: (Float) -> Unit = {}
    ) {
        val ex = MediaExtractor()
        var dec: MediaCodec? = null
        var sink: AacSink? = null
        try {
            ex.setDataSource(context, uri, null)
            var aIdx = -1
            var inFmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val m = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (m.startsWith("audio/")) {
                    aIdx = i
                    inFmt = f
                    break
                }
            }
            if (aIdx < 0 || inFmt == null) throw IOException("O arquivo não tem trilha de áudio")
            ex.selectTrack(aIdx)

            val mime = inFmt.getString(MediaFormat.KEY_MIME)!!
            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            val spanUs = (endUs - startUs).coerceAtLeast(1L)
            ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val decoder = try {
                MediaCodec.createDecoderByType(mime)
            } catch (e: Exception) {
                throw IOException("Formato de áudio não suportado para exportação ($mime)")
            }
            dec = decoder
            decoder.configure(inFmt, null, null, 0)
            decoder.start()

            var srcRate = inFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var srcCh = inFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            var dstRate = 0
            var dstCh = 0
            var resampler: Resampler? = null
            var totalEstFrames = 1L
            var outFrames = 0L

            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false

            while (!outDone) {
                if (cancelled()) throw CancellationException("cancelled")

                if (!inDone) {
                    val ii = decoder.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val b = decoder.getInputBuffer(ii)!!
                        val sz = ex.readSampleData(b, 0)
                        val st = ex.sampleTime
                        if (sz < 0 || st > endUs) {
                            decoder.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inDone = true
                        } else {
                            decoder.queueInputBuffer(ii, 0, sz, st, 0)
                            ex.advance()
                        }
                    }
                }

                val oi = decoder.dequeueOutputBuffer(info, 10_000)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = decoder.outputFormat
                    if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) srcRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) srcCh = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    floatPcm = Build.VERSION.SDK_INT >= 24 &&
                        f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (oi >= 0) {
                    val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    val pUs = info.presentationTimeUs
                    if (info.size > 0 && pUs < endUs && srcCh > 0 && srcRate > 0) {
                        val bytesPer = if (floatPcm) 4 else 2
                        val frames = info.size / (bytesPer * srcCh)
                        val skip = if (pUs < startUs) ((startUs - pUs) * srcRate / 1_000_000L).toInt() else 0
                        val maxFrames = ((endUs - pUs) * srcRate / 1_000_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        val keep = min(frames, maxFrames) - skip
                        if (keep > 0) {
                            val buf = decoder.getOutputBuffer(oi)!!
                            buf.order(ByteOrder.nativeOrder())
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            val pcm = ShortArray(keep * srcCh)
                            if (floatPcm) {
                                val fb = buf.asFloatBuffer()
                                fb.position(skip * srcCh)
                                for (i in pcm.indices) {
                                    pcm[i] = (fb.get().coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                                }
                            } else {
                                val sb = buf.asShortBuffer()
                                sb.position(skip * srcCh)
                                sb.get(pcm)
                            }

                            if (sink == null) {
                                dstCh = if (srcCh >= 2) 2 else 1
                                dstRate = pickRate(srcRate)
                                sink = AacSink(dstRate, dstCh, muxer)
                                resampler = Resampler(srcRate, dstRate, dstCh)
                                totalEstFrames = (spanUs * dstRate / 1_000_000L).coerceAtLeast(1L)
                            }

                            val mixed = mixDown(pcm, keep, srcCh, dstCh)
                            val rs = resampler!!.process(mixed, keep)
                            val rsFrames = rs.size / dstCh
                            if (fadeEdges && rsFrames > 0) {
                                applyFades(rs, rsFrames, dstCh, dstRate, outFrames, totalEstFrames)
                            }
                            if (rsFrames > 0) {
                                sink.feed(rs, rsFrames)
                                outFrames += rsFrames
                                onProgress((outFrames.toFloat() / totalEstFrames).coerceIn(0f, 1f))
                            }
                        }
                    }
                    decoder.releaseOutputBuffer(oi, false)
                    if (eos) outDone = true
                }
            }

            val s = sink ?: throw IOException("Nenhum áudio foi decodificado neste intervalo")
            s.finish()
            onProgress(1f)
        } finally {
            try {
                dec?.stop()
            } catch (_: Exception) { }
            try {
                dec?.release()
            } catch (_: Exception) { }
            sink?.release()
            try {
                ex.release()
            } catch (_: Exception) { }
            muxer.finish()
        }
    }

    private fun pickRate(src: Int): Int = when {
        src in SUPPORTED_RATES -> src
        src % 44100 == 0 -> 44100
        else -> 48000
    }

    private fun mixDown(pcm: ShortArray, frames: Int, srcCh: Int, dstCh: Int): ShortArray {
        if (srcCh == dstCh) return pcm
        // dstCh == 2 e srcCh > 2
        val out = ShortArray(frames * 2)
        val k = 0.707f
        for (f in 0 until frames) {
            val b = f * srcCh
            var l = pcm[b].toFloat()
            var r = pcm[b + 1].toFloat()
            when (srcCh) {
                3 -> { // L R C
                    val c = pcm[b + 2] * k; l += c; r += c
                }
                4 -> { // L R Ls Rs
                    l += pcm[b + 2] * k; r += pcm[b + 3] * k
                }
                5 -> { // L R C Ls Rs
                    val c = pcm[b + 2] * k; l += c + pcm[b + 3] * k; r += c + pcm[b + 4] * k
                }
                else -> { // 5.1 ou mais: L R C LFE Ls Rs
                    val c = pcm[b + 2] * k
                    l += c; r += c
                    if (srcCh >= 6) {
                        l += pcm[b + 4] * k; r += pcm[b + 5] * k
                    }
                }
            }
            out[f * 2] = (l * 0.6f).toInt().coerceIn(-32768, 32767).toShort()
            out[f * 2 + 1] = (r * 0.6f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /** Fade curtíssimo nas pontas para o corte não estalar. */
    private fun applyFades(
        pcm: ShortArray,
        frames: Int,
        ch: Int,
        rate: Int,
        startIndex: Long,
        totalFrames: Long
    ) {
        val fi = (rate * 0.015).toInt().coerceAtLeast(1)
        val fo = (rate * 0.05).toInt().coerceAtLeast(1)
        for (f in 0 until frames) {
            val idx = startIndex + f
            var g = 1f
            if (idx < fi) g = idx / fi.toFloat()
            val rem = totalFrames - idx
            if (rem < fo) g = min(g, (rem / fo.toFloat()).coerceAtLeast(0f))
            if (g < 1f) {
                for (c in 0 until ch) {
                    val i = f * ch + c
                    pcm[i] = (pcm[i] * g).toInt().toShort()
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------

    /** Reamostrador em streaming: média simples para razões inteiras, linear nas demais. */
    private class Resampler(srcRate: Int, dstRate: Int, private val ch: Int) {
        private val ratio = srcRate.toDouble() / dstRate
        private val passthrough = srcRate == dstRate
        private val intRatio = if (!passthrough && srcRate % dstRate == 0) srcRate / dstRate else 0

        // linear
        private var pos = 0.0
        private val prev = FloatArray(ch)
        private var havePrev = false

        // decimação
        private var leftover = ShortArray(0)

        fun process(inp: ShortArray, frames: Int): ShortArray {
            if (passthrough) return inp
            return if (intRatio > 0) decimate(inp, frames) else linear(inp, frames)
        }

        private fun decimate(inp: ShortArray, frames: Int): ShortArray {
            val leftFrames = leftover.size / ch
            val total = leftFrames + frames
            val src = ShortArray(total * ch)
            System.arraycopy(leftover, 0, src, 0, leftover.size)
            System.arraycopy(inp, 0, src, leftover.size, frames * ch)
            val outFrames = total / intRatio
            val out = ShortArray(outFrames * ch)
            for (o in 0 until outFrames) {
                for (c in 0 until ch) {
                    var sum = 0
                    val base = o * intRatio
                    for (j in 0 until intRatio) sum += src[(base + j) * ch + c]
                    out[o * ch + c] = (sum / intRatio).toShort()
                }
            }
            val used = outFrames * intRatio
            leftover = src.copyOfRange(used * ch, total * ch)
            return out
        }

        private fun linear(inp: ShortArray, frames: Int): ShortArray {
            val n = frames
            if (n == 0) return ShortArray(0)
            if (!havePrev) {
                for (k in 0 until ch) prev[k] = inp[k].toFloat()
                havePrev = true
            }
            val cap = ((n - pos) / ratio).toInt() + 2
            val out = ShortArray(cap * ch)
            var o = 0
            while (true) {
                val i = pos.toInt()
                if (i >= n || o >= cap) break
                val f = (pos - i).toFloat()
                for (k in 0 until ch) {
                    val a = if (i == 0) prev[k] else inp[(i - 1) * ch + k].toFloat()
                    val b = inp[i * ch + k].toFloat()
                    out[o * ch + k] = (a + (b - a) * f).toInt().toShort()
                }
                o++
                pos += ratio
            }
            pos -= n
            for (k in 0 until ch) prev[k] = inp[(n - 1) * ch + k].toFloat()
            return out.copyOf(o * ch)
        }
    }

    // ---------------------------------------------------------------------------------------

    /** Encoder AAC-LC que empurra as amostras codificadas para o [AudioMuxer]. */
    private class AacSink(
        private val rate: Int,
        private val ch: Int,
        private val muxer: AudioMuxer
    ) {
        private val codec: MediaCodec
        private val info = MediaCodec.BufferInfo()
        private var framesFed = 0L
        private var released = false

        init {
            val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, ch).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, if (ch == 1) 128_000 else 192_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1 shl 16)
            }
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        }

        fun feed(pcm: ShortArray, frames: Int) {
            val total = frames * ch
            var offset = 0
            while (offset < total) {
                val idx = codec.dequeueInputBuffer(10_000)
                if (idx < 0) {
                    drain(0)
                    continue
                }
                val buf = codec.getInputBuffer(idx)!!
                buf.clear()
                buf.order(ByteOrder.LITTLE_ENDIAN)
                var n = min(buf.remaining() / 2, total - offset)
                n -= n % ch
                if (n <= 0) {
                    codec.queueInputBuffer(idx, 0, 0, framesFed * 1_000_000L / rate, 0)
                    continue
                }
                buf.asShortBuffer().put(pcm, offset, n)
                val ptsUs = framesFed * 1_000_000L / rate
                codec.queueInputBuffer(idx, 0, n * 2, ptsUs, 0)
                framesFed += n / ch
                offset += n
                drain(0)
            }
        }

        fun finish() {
            var queued = false
            while (!queued) {
                val idx = codec.dequeueInputBuffer(10_000)
                if (idx >= 0) {
                    codec.queueInputBuffer(
                        idx, 0, 0, framesFed * 1_000_000L / rate,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    queued = true
                } else {
                    drain(0)
                }
            }
            while (!drain(10_000)) { /* espera EOS */ }
            release()
        }

        /** @return true quando recebeu o fim de stream. */
        private fun drain(timeoutUs: Long): Boolean {
            while (true) {
                val oi = codec.dequeueOutputBuffer(info, timeoutUs)
                when {
                    oi == MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                    oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxer.isStarted) muxer.start(codec.outputFormat)
                    }
                    oi >= 0 -> {
                        val out = codec.getOutputBuffer(oi)!!
                        if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0
                        if (info.size > 0 && muxer.isStarted) {
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            muxer.write(out, info)
                        }
                        codec.releaseOutputBuffer(oi, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return true
                    }
                }
            }
        }

        fun release() {
            if (released) return
            released = true
            try {
                codec.stop()
            } catch (_: Exception) { }
            try {
                codec.release()
            } catch (_: Exception) { }
        }
    }

    private companion object {
        val SUPPORTED_RATES = setOf(8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000)
    }
}
