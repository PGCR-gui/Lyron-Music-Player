package com.tolete.player.video

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Process
import kotlinx.coroutines.CancellationException
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Nível de energia (0..1) da faixa inteira, para desenhar a forma de onda no editor de trecho.
 * Decodifica a faixa uma vez (amostrando os quadros) e guarda o resultado em um cache pequeno.
 *
 * Usa o valor RMS de cada barra (e não o pico máximo): em músicas masterizadas alto, o pico de
 * quase toda barra encosta em 1,0 e a forma de onda ficava "estourada" (tudo no teto). O RMS
 * acompanha o volume percebido e preserva a diferença entre trechos calmos e intensos.
 */
object WaveformExtractor {

    private const val CACHE_SIZE = 6

    /** Contraste da curva de amplitude: 1 = linear; maior = forma de onda menos "cheia". */
    private const val WAVE_CONTRAST = 1.35f

    private val cache = object : LinkedHashMap<String, FloatArray>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?): Boolean =
            size > CACHE_SIZE
    }

    /** ~50 barras por segundo (20 ms por barra), entre 500 e 40.000. */
    fun binsFor(durationMs: Long): Int = (durationMs / 20L).toInt().coerceIn(500, 40_000)

    fun cached(key: String): FloatArray? = synchronized(cache) { cache[key] }

    // ---------------------------------------------------------------------------------------------
    // Waveform da seekbar: versão leve (480 barras, análise a 4 kHz) com cache em memória e em disco.
    // Decodificar a faixa é o único custo real; por isso cada música é analisada uma única vez
    // e o resultado (~2 KB) fica guardado em cacheDir/seekwave_v1, sobrevivendo ao app ser fechado.
    // ---------------------------------------------------------------------------------------------

    private const val SEEK_BINS = 480
    private const val SEEK_ANALYSIS_HZ = 4_000
    private const val SEEK_CACHE_SIZE = 80
    private const val SEEK_DISK_DIR = "seekwave_v1"
    private const val SEEK_DISK_MAX_FILES = 400
    private const val SEEK_MAGIC = 0x4C595731 // "LYW1"

    private val seekCache = object : LinkedHashMap<String, FloatArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?): Boolean =
            size > SEEK_CACHE_SIZE
    }
    private val seekFailed = HashSet<String>()

    private fun seekKey(key: String, durationMs: Long) = "$key#$durationMs"

    fun seekCached(key: String, durationMs: Long): FloatArray? =
        synchronized(seekCache) { seekCache[seekKey(key, durationMs)] }

    /** True se esta faixa já falhou nesta sessão (ex.: DSD): evita tentar decodificar de novo. */
    fun seekKnownFailed(key: String, durationMs: Long): Boolean =
        synchronized(seekCache) { seekKey(key, durationMs) in seekFailed }

    private fun seekFile(context: Context, key: String, durationMs: Long): File {
        val dir = File(context.cacheDir, SEEK_DISK_DIR)
        return File(dir, "${key.hashCode().toUInt().toString(16)}_${key.length}_$durationMs.bin")
    }

    private fun readSeekFile(file: File): FloatArray? = try {
        if (!file.isFile) null else DataInputStream(file.inputStream().buffered()).use { input ->
            if (input.readInt() != SEEK_MAGIC) return null
            val n = input.readInt()
            if (n !in 1..4_096) return null
            FloatArray(n) { input.readFloat() }
        }
    } catch (_: Exception) {
        null
    }

    private fun writeSeekFile(file: File, peaks: FloatArray) {
        try {
            val dir = file.parentFile ?: return
            dir.mkdirs()
            val tmp = File(dir, file.name + ".tmp")
            DataOutputStream(tmp.outputStream().buffered()).use { out ->
                out.writeInt(SEEK_MAGIC)
                out.writeInt(peaks.size)
                for (v in peaks) out.writeFloat(v)
            }
            if (!tmp.renameTo(file)) tmp.delete()
            // poda: mantém só os arquivos mais recentes
            val files = dir.listFiles { f -> f.name.endsWith(".bin") }
            if (files != null && files.size > SEEK_DISK_MAX_FILES) {
                files.sortedBy { it.lastModified() }
                    .take(files.size - SEEK_DISK_MAX_FILES + 40)
                    .forEach { it.delete() }
            }
        } catch (_: Exception) { }
    }

    /**
     * Picos da faixa para a seekbar (480 barras normalizadas 0..1) ou null se não der para
     * decodificar. Ordem: memória → disco → decodificação (em prioridade de thread baixa, para
     * não competir com a reprodução). Lança [CancellationException] se [cancelled] virar true.
     */
    fun extractForSeekBar(
        context: Context,
        uri: Uri,
        key: String,
        durationMs: Long,
        cancelled: () -> Boolean
    ): FloatArray? {
        if (durationMs <= 0L) return null
        seekCached(key, durationMs)?.let { return it }
        val file = seekFile(context, key, durationMs)
        readSeekFile(file)?.let { fromDisk ->
            synchronized(seekCache) { seekCache[seekKey(key, durationMs)] = fromDisk }
            return fromDisk
        }
        if (seekKnownFailed(key, durationMs)) return null

        val oldPriority = try {
            Process.getThreadPriority(Process.myTid())
        } catch (_: Exception) {
            Process.THREAD_PRIORITY_DEFAULT
        }
        try { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) } catch (_: Exception) { }
        try {
            val peaks = extract(
                context = context,
                uri = uri,
                key = key,
                durationMs = durationMs,
                cancelled = cancelled,
                binsOverride = SEEK_BINS,
                analysisRateHz = SEEK_ANALYSIS_HZ,
                useCache = false
            )
            if (peaks == null) {
                synchronized(seekCache) { seekFailed.add(seekKey(key, durationMs)) }
                return null
            }
            synchronized(seekCache) { seekCache[seekKey(key, durationMs)] = peaks }
            writeSeekFile(file, peaks)
            return peaks
        } finally {
            try { Process.setThreadPriority(oldPriority) } catch (_: Exception) { }
        }
    }

    /**
     * Retorna os picos normalizados ou null se o formato não puder ser decodificado
     * (ex.: DSD). Lança [CancellationException] se [cancelled] virar true.
     */
    fun extract(
        context: Context,
        uri: Uri,
        key: String,
        durationMs: Long,
        cancelled: () -> Boolean,
        onProgress: (Float) -> Unit = {},
        binsOverride: Int = 0,
        analysisRateHz: Int = 12_000,
        useCache: Boolean = true
    ): FloatArray? {
        if (useCache) cached(key)?.let { return it }
        if (durationMs <= 0L) return null

        val bins = if (binsOverride > 0) binsOverride else binsFor(durationMs)
        val sumSq = DoubleArray(bins)
        val counts = IntArray(bins)
        val binsPerMs = bins.toDouble() / durationMs.toDouble()

        val ex = MediaExtractor()
        var dec: MediaCodec? = null
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
            if (aIdx < 0 || inFmt == null) return null
            ex.selectTrack(aIdx)

            val mime = inFmt.getString(MediaFormat.KEY_MIME) ?: return null
            val decoder = try {
                MediaCodec.createDecoderByType(mime)
            } catch (_: Exception) {
                return null
            }
            dec = decoder
            decoder.configure(inFmt, null, null, 0)
            decoder.start()

            var srcRate = inFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var srcCh = inFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false

            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false
            var idleLoops = 0
            var lastReported = -1f
            val totalUs = durationMs * 1000L

            while (!outDone) {
                if (cancelled()) throw CancellationException("cancelled")

                if (!inDone) {
                    val ii = decoder.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val b = decoder.getInputBuffer(ii)!!
                        val sz = ex.readSampleData(b, 0)
                        if (sz < 0) {
                            decoder.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inDone = true
                        } else {
                            decoder.queueInputBuffer(ii, 0, sz, ex.sampleTime, 0)
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
                    idleLoops = 0
                    val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    if (info.size > 0 && srcCh > 0 && srcRate > 0) {
                        val bytesPer = if (floatPcm) 4 else 2
                        val frames = info.size / (bytesPer * srcCh)
                        val buf = decoder.getOutputBuffer(oi)!!
                        buf.order(ByteOrder.nativeOrder())
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val fb = if (floatPcm) buf.asFloatBuffer() else null
                        val sb = if (!floatPcm) buf.asShortBuffer() else null

                        // amostra ~ 12 mil quadros por segundo: suficiente para picos de 20 ms
                        val stride = max(1, srcRate / analysisRateHz.coerceAtLeast(1_000))
                        val startMs = info.presentationTimeUs / 1000.0
                        val msPerFrame = 1000.0 / srcRate
                        var f = 0
                        while (f < frames) {
                            // média quadrática dos canais (energia do quadro)
                            var e = 0f
                            for (c in 0 until srcCh) {
                                val s = if (fb != null) fb.get(f * srcCh + c) else sb!!.get(f * srcCh + c) / 32768f
                                e += s * s
                            }
                            val bin = ((startMs + f * msPerFrame) * binsPerMs).toInt()
                            if (bin in 0 until bins) {
                                sumSq[bin] += (e / srcCh).toDouble()
                                counts[bin]++
                            }
                            f += stride
                        }

                        val frac = (info.presentationTimeUs.toFloat() / totalUs.toFloat()).coerceIn(0f, 1f)
                        if (frac - lastReported >= 0.02f) {
                            lastReported = frac
                            onProgress(frac)
                        }
                    }
                    decoder.releaseOutputBuffer(oi, false)
                    if (eos) outDone = true
                } else if (inDone) {
                    // decoder parado depois do fim da entrada: evita laço infinito
                    if (++idleLoops > 300) outDone = true
                }
            }

            val peaks = FloatArray(bins)
            for (i in 0 until bins) {
                if (counts[i] > 0) peaks[i] = sqrt(sumSq[i] / counts[i]).toFloat()
            }
            // referência = percentil 99 (e não o máximo absoluto): um estalo isolado não achata
            // a faixa inteira, e só ~1% das barras chega ao teto.
            val sorted = peaks.filter { it > 0f }.sorted()
            if (sorted.isEmpty()) return null
            val ref = sorted[((sorted.size - 1) * 0.99f).toInt().coerceIn(0, sorted.size - 1)]
            if (ref <= 0f) return null
            // expoente > 1 aumenta o contraste: trechos calmos ficam baixos e os fortes se destacam
            for (i in peaks.indices) {
                peaks[i] = min(1f, peaks[i] / ref).pow(WAVE_CONTRAST)
            }
            onProgress(1f)
            if (useCache) synchronized(cache) { cache[key] = peaks }
            return peaks
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        } finally {
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
