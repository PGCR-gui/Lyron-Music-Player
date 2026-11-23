package com.tolete.player.visualizer

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Guarda os últimos frames de PCM (estéreo, float) que o player manda para a saída de áudio
 * e entrega ao renderizador do visualizador.
 *
 * Mudanças em relação à versão anterior:
 *  1) Alimentado pelo [AudioTapSink] (na frente do DefaultAudioSink) e não mais por um
 *     TeeAudioProcessor. Com saída float (bit-perfect / hi-res) o Media3 pula os
 *     AudioProcessors customizados, então o Tee ficava mudo.
 *  2) Compensa o atraso: o sink recebe o áudio ~250-750 ms ANTES de ele tocar. O [drain] entrega
 *     o trecho que está soando agora, não o que acabou de entrar no buffer. Sem isso as batidas
 *     do visualizador ficam fora de sincronia e parecem "genéricas".
 *  3) Decimação para ~48 kHz: o projectM só analisa as últimas 512 amostras; em 96/192 kHz isso
 *     cobria poucos ms e os graves sumiam.
 *  4) Logs de diagnóstico (tag "AudioTap") a cada 1 s enquanto DEBUG = true.
 */
object AudioTap {

    private const val TAG = "AudioTap"
    private const val DEBUG = true

    private const val CAPACITY = 1 shl 18            // frames (potência de 2), ~5,9 s a 44,1 kHz
    private const val MASK = CAPACITY - 1L
    private const val TARGET_RATE = 48_000
    private const val MAX_AHEAD_US = 4_000_000L
    private const val NONE = Long.MIN_VALUE

    private val ring = FloatArray(CAPACITY * 2)      // L,R intercalados

    @Volatile private var writeFrames = 0L
    private var readFrames = 0L                      // só a thread do GL mexe aqui

    @Volatile private var channels = 2
    @Volatile private var encoding = C.ENCODING_INVALID
    @Volatile private var sampleRate = TARGET_RATE
    @Volatile private var step = 1

    /**
     * Ligado enquanto existe pelo menos um consumidor (visualizer milk ou waveform ao vivo da seekbar).
     * Use [acquire] / [release] em vez de gravar [active] diretamente.
     */
    @Volatile var active = false
        private set

    private val consumers = java.util.concurrent.atomic.AtomicInteger(0)

    /** Marca interesse em PCM (milk, live seekbar…). Quando o contador volta a 0, para de encher o ring. */
    fun acquire() {
        if (consumers.incrementAndGet() == 1) active = true
    }

    fun release() {
        val n = consumers.decrementAndGet()
        if (n <= 0) {
            consumers.set(0)
            active = false
        }
    }

    // sincronia: fim (em tempo de mídia) do que já entrou no sink x posição que está tocando
    @Volatile private var writtenEndUs = NONE
    @Volatile private var playedUs = NONE

    // acumulador da decimação (só a thread do player)
    private var accL = 0f
    private var accR = 0f
    private var accN = 0

    // diagnóstico
    @Volatile private var dbgBuffers = 0L
    private var dbgLastLog = 0L
    private var dbgDrained = 0L
    private var dbgSumSq = 0.0
    private var dbgSamples = 0L

    // ---- lado do player (chamado pelo AudioTapSink) ----

    fun onConfigure(sampleRateHz: Int, channelCount: Int, enc: Int) {
        sampleRate = sampleRateHz.coerceAtLeast(8000)
        channels = channelCount.coerceAtLeast(1)
        encoding = enc
        step = ((sampleRateHz + TARGET_RATE / 2) / TARGET_RATE).coerceAtLeast(1)
        accL = 0f; accR = 0f; accN = 0
        if (DEBUG) Log.i(TAG, "configure: ${sampleRateHz}Hz ${channelCount}ch enc=$enc decimação=1/$step")
    }

    fun onFlush() {
        accL = 0f; accR = 0f; accN = 0
        writtenEndUs = NONE
        playedUs = NONE
    }

    /** Posição (tempo de mídia, µs) que está soando agora. */
    fun onPosition(us: Long) {
        playedUs = us
    }

    /** [buffer] = exatamente os bytes que o sink consumiu nesta chamada (position..limit). */
    fun onBuffer(buffer: ByteBuffer, ptsUs: Long) {
        val enc = encoding
        val bytesPerSample = when (enc) {
            C.ENCODING_PCM_8BIT -> 1
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
            else -> return // formato que não sabemos ler: ignora (o áudio segue normal)
        }
        val ch = channels
        val frameBytes = bytesPerSample * ch
        var pos = buffer.position()
        val totalFrames = (buffer.limit() - pos) / frameBytes
        if (totalFrames <= 0) return

        dbgBuffers++
        writtenEndUs = ptsUs + totalFrames * 1_000_000L / sampleRate
        if (!active) return

        val b = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN) // duplicate() volta p/ BIG_ENDIAN
        val step = this.step
        var frames = totalFrames
        val maxIn = (CAPACITY / 2) * step              // buffer gigante: só interessa o final
        if (frames > maxIn) {
            pos += (frames - maxIn) * frameBytes
            frames = maxIn
            accN = 0
        }

        var w = writeFrames
        var aL = accL
        var aR = accR
        var aN = accN
        for (f in 0 until frames) {
            val l = sample(b, pos, enc)
            val r = if (ch > 1) sample(b, pos + bytesPerSample, enc) else l
            pos += frameBytes
            if (step == 1) {
                val i = ((w and MASK).toInt()) * 2
                ring[i] = l
                ring[i + 1] = r
                w++
            } else {
                aL += l; aR += r; aN++
                if (aN >= step) {
                    val i = ((w and MASK).toInt()) * 2
                    ring[i] = aL / step
                    ring[i + 1] = aR / step
                    w++
                    aL = 0f; aR = 0f; aN = 0
                }
            }
        }
        accL = aL; accR = aR; accN = aN
        writeFrames = w // publica depois de escrever
    }

    private fun sample(b: ByteBuffer, i: Int, enc: Int): Float = when (enc) {
        C.ENCODING_PCM_16BIT -> b.getShort(i) / 32768f
        C.ENCODING_PCM_FLOAT -> b.getFloat(i)
        C.ENCODING_PCM_32BIT -> b.getInt(i) / 2147483648f
        C.ENCODING_PCM_24BIT -> {
            val v = (b.get(i).toInt() and 0xFF) or
                ((b.get(i + 1).toInt() and 0xFF) shl 8) or
                (b.get(i + 2).toInt() shl 16) // o último byte carrega o sinal
            v / 8388608f
        }
        C.ENCODING_PCM_8BIT -> ((b.get(i).toInt() and 0xFF) - 128) / 128f
        else -> 0f
    }

    // ---- lado do renderizador (thread do GL) ----

    /** Quantos frames (já decimados) o buffer está adiantado em relação ao que toca agora. */
    private fun aheadFrames(): Long {
        val w = writtenEndUs
        val p = playedUs
        if (w == NONE || p == NONE) return 0L
        val us = (w - p).coerceIn(0L, MAX_AHEAD_US)
        val rate = (sampleRate / step).coerceAtLeast(1)
        return us * rate / 1_000_000L
    }

    /** Descarta o que já estava no buffer (usar ao criar o visualizador). */
    fun resetReader() {
        readFrames = (writeFrames - aheadFrames()).coerceAtLeast(0L)
    }

    /**
     * Copia até [maxFrames] frames estéreo intercalados para [out], do trecho que está
     * tocando agora. Retorna quantos frames foram copiados (0 = nada novo / pausado).
     */
    fun drain(out: FloatArray, maxFrames: Int): Int {
        val end = (writeFrames - aheadFrames()).coerceAtLeast(0L)
        var r = readFrames
        if (end - r > maxFrames) r = end - maxFrames // atrasou: pula para o mais recente
        val n = (end - r).toInt()
        if (n <= 0) {
            debugTick()
            return 0
        }
        for (k in 0 until n) {
            val i = (((r + k) and MASK).toInt()) * 2
            val l = ring[i]
            val rr = ring[i + 1]
            out[k * 2] = l
            out[k * 2 + 1] = rr
            if (DEBUG) {
                dbgSumSq += (l * l + rr * rr).toDouble()
                dbgSamples += 2
            }
        }
        readFrames = r + n
        if (DEBUG) dbgDrained += n
        debugTick()
        return n
    }

    /**
     * Cópia não-destrutiva dos frames mais recentes que estão soando agora
     * (não avança o ponteiro de [drain] — seguro para usar junto com o milk).
     * [out] deve ter capacidade para pelo menos maxFrames*2 floats (L,R intercalados).
     * Retorna quantos frames foram copiados.
     */
    fun snapshotRecent(out: FloatArray, maxFrames: Int): Int {
        if (maxFrames <= 0 || out.size < 2) return 0
        val end = (writeFrames - aheadFrames()).coerceAtLeast(0L)
        val available = end.coerceAtMost(CAPACITY.toLong())
        val n = minOf(maxFrames, available.toInt())
        if (n <= 0) return 0
        val start = end - n
        for (k in 0 until n) {
            val i = (((start + k) and MASK).toInt()) * 2
            out[k * 2] = ring[i]
            out[k * 2 + 1] = ring[i + 1]
        }
        return n
    }

    /**
     * Como [snapshotRecent], mas deslocado [advanceFrames] para o futuro (adiantamento).
     * Útil para a waveform ao vivo da seekbar acompanhar o áudio com 0,5 s de antecipação.
     * Se não houver frames futuros suficientes, usa o máximo disponível.
     */
    fun snapshotRecentAhead(out: FloatArray, maxFrames: Int, advanceFrames: Int): Int {
        if (maxFrames <= 0 || out.size < 2) return 0
        val now = (writeFrames - aheadFrames()).coerceAtLeast(0L)
        val end = (now + advanceFrames.toLong()).coerceAtMost(writeFrames)
        val available = end.coerceAtMost(CAPACITY.toLong())
        val n = minOf(maxFrames, available.toInt())
        if (n <= 0) return 0
        val start = end - n
        for (k in 0 until n) {
            val i = (((start + k) and MASK).toInt()) * 2
            out[k * 2] = ring[i]
            out[k * 2 + 1] = ring[i + 1]
        }
        return n
    }

    /** Taxa efetiva após decimação (para quem calcula duração da janela). */
    fun effectiveSampleRate(): Int = (sampleRate / step).coerceAtLeast(1)

    private fun debugTick() {
        if (!DEBUG) return
        val now = SystemClock.uptimeMillis()
        if (now - dbgLastLog < 1000) return
        dbgLastLog = now
        val rms = if (dbgSamples > 0) sqrt(dbgSumSq / dbgSamples) else 0.0
        val aheadMs = aheadFrames() * 1000L / (sampleRate / step).coerceAtLeast(1)
        val w = writtenEndUs
        val p = playedUs
        val rawMs = if (w == NONE || p == NONE) -1L else (w - p) / 1000L
        Log.d(
            TAG,
            "ativo=$active buffers=$dbgBuffers drenados/s=$dbgDrained rms=${"%.3f".format(rms)} " +
                "adiantamento=${aheadMs}ms bruto=${rawMs}ms fmt=$encoding ${channels}ch ${sampleRate}Hz"
        )
        dbgDrained = 0; dbgSumSq = 0.0; dbgSamples = 0
    }
}
