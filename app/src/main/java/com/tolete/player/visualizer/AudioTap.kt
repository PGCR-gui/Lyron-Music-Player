package com.tolete.player.visualizer

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * "Escuta" o PCM que o ExoPlayer manda para a saída de áudio e guarda os últimos
 * frames num buffer circular (estéreo, float). O renderizador do visualizador
 * lê daqui a cada quadro.
 *
 * - O ExoPlayer (PlaybackService) e a UI rodam no mesmo processo, então um object basta.
 * - O áudio NÃO é alterado: o TeeAudioProcessor repassa os bytes idênticos.
 * - Enquanto [active] for false, [handleBuffer] sai na primeira linha (custo ~zero).
 */
@OptIn(UnstableApi::class)
object AudioTap : TeeAudioProcessor.AudioBufferSink {

    private const val CAPACITY = 1 shl 15            // frames (potência de 2)
    private const val MASK = CAPACITY - 1L
    private val ring = FloatArray(CAPACITY * 2)      // L,R intercalados

    @Volatile private var writeFrames = 0L
    private var readFrames = 0L                      // só a thread do GL mexe aqui

    @Volatile private var channels = 2
    @Volatile private var encoding = C.ENCODING_INVALID

    /** Ligado enquanto existe um visualizador na tela. */
    @Volatile var active = false

    // ---- lado do player (thread de áudio) ----

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        this.channels = channelCount.coerceAtLeast(1)
        this.encoding = encoding
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (!active) return
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

        // asReadOnlyBuffer() volta para BIG_ENDIAN; o PCM do Android é little-endian
        val b = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        var pos = b.position()
        var frames = (b.limit() - pos) / frameBytes
        if (frames <= 0) return
        if (frames > CAPACITY) { // buffer gigante: só interessa o final
            pos += (frames - CAPACITY) * frameBytes
            frames = CAPACITY
        }

        var w = writeFrames
        for (f in 0 until frames) {
            val l = sample(b, pos, enc)
            val r = if (ch > 1) sample(b, pos + bytesPerSample, enc) else l
            val i = ((w and MASK).toInt()) * 2
            ring[i] = l
            ring[i + 1] = r
            w++
            pos += frameBytes
        }
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

    /** Descarta o que já estava no buffer (usar ao criar o visualizador). */
    fun resetReader() {
        readFrames = writeFrames
    }

    /**
     * Copia até [maxFrames] frames estéreo intercalados para [out].
     * Retorna quantos frames foram copiados (0 = nada novo / pausado).
     */
    fun drain(out: FloatArray, maxFrames: Int): Int {
        val w = writeFrames
        var r = readFrames
        if (w - r > maxFrames) r = w - maxFrames // atrasou: pula para o mais recente
        val n = (w - r).toInt()
        for (k in 0 until n) {
            val i = (((r + k) and MASK).toInt()) * 2
            out[k * 2] = ring[i]
            out[k * 2 + 1] = ring[i + 1]
        }
        readFrames = r + n
        return n
    }
}
