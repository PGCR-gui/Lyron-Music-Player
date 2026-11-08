package com.tolete.player.video

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.CancellationException
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.nio.channels.FileChannel
import kotlin.math.min

/**
 * PCM mono (16 bits) do trecho do vídeo, guardado em arquivo temporário e lido por
 * memory-map (não ocupa heap). Serve só para alimentar o visualizador milk no vídeo.
 */
class MilkPcmClip(private val file: File, val sampleRate: Int, val sampleCount: Long) : Closeable {
    private var raf: RandomAccessFile? = null
    private var shorts: ShortBuffer? = null

    init {
        if (sampleCount > 0) {
            val r = RandomAccessFile(file, "r")
            raf = r
            shorts = r.channel.map(FileChannel.MapMode.READ_ONLY, 0, sampleCount * 2)
                .order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        }
    }

    /** Copia até [count] amostras a partir de [from]; retorna quantas foram copiadas. */
    fun read(from: Long, count: Int, out: ShortArray): Int {
        val sb = shorts ?: return 0
        val start = from.coerceAtLeast(0L)
        val end = min(from + count, sampleCount)
        val n = (end - start).toInt()
        if (n <= 0) return 0
        val d = sb.duplicate()
        d.position(start.toInt())
        d.get(out, 0, n)
        return n
    }

    override fun close() {
        shorts = null
        try {
            raf?.close()
        } catch (_: Exception) { }
        raf = null
        file.delete()
    }
}

/** Decodifica [startMs, endMs] da faixa para PCM mono (reduzido a ~48 kHz se for hi-res). */
object MilkPcmDecoder {

    private const val TARGET_RATE = 48_000
    private const val MAX_SAMPLES = 1_000_000_000L

    fun decode(
        context: Context,
        uri: Uri,
        startMs: Long,
        endMs: Long,
        outFile: File,
        cancelled: () -> Boolean
    ): MilkPcmClip {
        val ex = MediaExtractor()
        var dec: MediaCodec? = null
        var out: BufferedOutputStream? = null
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
            ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val decoder = try {
                MediaCodec.createDecoderByType(mime)
            } catch (e: Exception) {
                throw IOException("Formato de áudio não suportado ($mime)")
            }
            dec = decoder
            decoder.configure(inFmt, null, null, 0)
            decoder.start()

            var srcRate = inFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var srcCh = inFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            var step = ((srcRate + TARGET_RATE / 2) / TARGET_RATE).coerceAtLeast(1)
            var accum = 0f
            var accN = 0
            var written = 0L

            val os = BufferedOutputStream(FileOutputStream(outFile), 1 shl 16)
            out = os
            val chunk = ByteArray(8192)
            var cn = 0

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
                    step = ((srcRate + TARGET_RATE / 2) / TARGET_RATE).coerceAtLeast(1)
                    accum = 0f
                    accN = 0
                } else if (oi >= 0) {
                    val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    val pUs = info.presentationTimeUs
                    if (info.size > 0 && pUs < endUs && srcCh > 0 && srcRate > 0 && written < MAX_SAMPLES) {
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
                            val fb = if (floatPcm) buf.asFloatBuffer() else null
                            val sb = if (!floatPcm) buf.asShortBuffer() else null
                            fb?.position(skip * srcCh)
                            sb?.position(skip * srcCh)
                            for (f in 0 until keep) {
                                var sum = 0f
                                for (c in 0 until srcCh) {
                                    sum += if (fb != null) fb.get() else sb!!.get() / 32768f
                                }
                                accum += sum / srcCh
                                accN++
                                if (accN >= step) {
                                    val s = ((accum / step).coerceIn(-1f, 1f) * 32767f).toInt()
                                    chunk[cn++] = (s and 0xFF).toByte()
                                    chunk[cn++] = ((s shr 8) and 0xFF).toByte()
                                    written++
                                    accum = 0f
                                    accN = 0
                                    if (cn == chunk.size) {
                                        os.write(chunk, 0, cn)
                                        cn = 0
                                    }
                                }
                            }
                        }
                    }
                    decoder.releaseOutputBuffer(oi, false)
                    if (eos) outDone = true
                }
            }
            if (cn > 0) os.write(chunk, 0, cn)
            os.flush()
            if (written <= 0L) throw IOException("Nenhum áudio foi decodificado neste intervalo")
            return MilkPcmClip(outFile, (srcRate / step).coerceAtLeast(8000), written)
        } catch (t: Throwable) {
            outFile.delete()
            throw t
        } finally {
            try {
                out?.close()
            } catch (_: Exception) { }
            try {
                dec?.stop()
            } catch (_: Exception) { }
            try {
                dec?.release()
            } catch (_: Exception) { }
            try {
                ex.release()
            } catch (_: Exception) { }
        }
    }
}
