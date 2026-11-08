@file:OptIn(UnstableApi::class)

package com.tolete.player.visualizer

import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer

/**
 * Fica por fora de todo o resto (DefaultAudioSink e wrapper USB) e "espia" o PCM de entrada.
 * Não altera o áudio: só lê os bytes que o sink acabou de consumir.
 *
 * Vantagem sobre o TeeAudioProcessor: funciona também com saída float (bit-perfect/hi-res),
 * caso em que o Media3 ignora AudioProcessors customizados. Também informa a posição de
 * reprodução, usada pelo AudioTap para sincronizar o visual com o som.
 */
class AudioTapSink(sink: AudioSink) : ForwardingAudioSink(sink) {

    private var tapEnabled = false

    // Media3 1.11: configure(Format, Int, IntArray?) virou `final` e deprecated em
    // ForwardingAudioSink. O ponto de override agora é configure(AudioSinkConfig).
    override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) {
        val inputFormat = audioSinkConfig.format
        tapEnabled = MimeTypes.AUDIO_RAW == inputFormat.sampleMimeType &&
            inputFormat.sampleRate > 0 && inputFormat.channelCount > 0
        if (tapEnabled) {
            AudioTap.onConfigure(inputFormat.sampleRate, inputFormat.channelCount, inputFormat.pcmEncoding)
        }
        super.configure(audioSinkConfig)
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int
    ): Boolean {
        if (!tapEnabled) return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        val start = buffer.position()
        val done = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        val end = buffer.position()
        if (end > start) {
            val consumed = buffer.duplicate()
            consumed.position(start)
            consumed.limit(end)
            AudioTap.onBuffer(consumed, presentationTimeUs)
        }
        return done
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val p = super.getCurrentPositionUs(sourceEnded)
        if (p != AudioSink.CURRENT_POSITION_NOT_SET) AudioTap.onPosition(p)
        return p
    }

    override fun flush() {
        AudioTap.onFlush()
        super.flush()
    }

    override fun reset() {
        AudioTap.onFlush()
        super.reset()
    }
}
