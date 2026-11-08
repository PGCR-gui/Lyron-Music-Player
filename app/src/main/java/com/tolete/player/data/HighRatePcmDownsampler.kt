package com.tolete.player.data

import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.math.sqrt

/** FIR 2:1 para PCM acima de 192 kHz; multicanal de alta taxa é downmixado para estéreo. */
@UnstableApi
class HighRatePcmDownsampler : BaseAudioProcessor() {
    private var decimators: List<Decimator> = emptyList()
    private var outputEncoding = C.ENCODING_INVALID
    private var outputChannelCount = 0
    private var downmixMatrix: Array<FloatArray>? = null

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.sampleRate <= MAX_ANDROID_PCM_RATE ||
            inputAudioFormat.channelCount <= 0 ||
            inputAudioFormat.encoding !in SUPPORTED_ENCODINGS
        ) {
            decimators = emptyList()
            outputChannelCount = inputAudioFormat.channelCount
            downmixMatrix = null
            return AudioProcessor.AudioFormat.NOT_SET
        }

        val channelCount = outputChannelCount(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        val outputRate = outputSampleRate(
            inputAudioFormat.sampleRate,
            channelCount,
            inputAudioFormat.encoding
        )
        var stageCount = 0
        var rate = inputAudioFormat.sampleRate
        while (rate > outputRate) {
            rate /= 2
            stageCount++
        }
        if (stageCount == 0) {
            decimators = emptyList()
            outputChannelCount = inputAudioFormat.channelCount
            downmixMatrix = null
            return AudioProcessor.AudioFormat.NOT_SET
        }

        downmixMatrix = if (channelCount == 2 && inputAudioFormat.channelCount > 2) {
            createStereoDownmixMatrix(inputAudioFormat.channelCount)
        } else {
            null
        }
        outputChannelCount = channelCount
        val taps = createLowPassTaps()
        decimators = List(stageCount) { Decimator(channelCount, taps) }
        outputEncoding = inputAudioFormat.encoding
        Log.i(
            "DsfPcmProcessor",
            "Configured ${inputAudioFormat.sampleRate}Hz/${inputAudioFormat.channelCount}ch " +
                "enc=${inputAudioFormat.encoding} -> ${outputRate}Hz/${channelCount}ch " +
                "stages=$stageCount"
        )
        return AudioProcessor.AudioFormat(outputRate, channelCount, inputAudioFormat.encoding)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val inputChannels = inputAudioFormat.channelCount
        val bytesPerSample = bytesPerSample(inputAudioFormat.encoding)
        val frameBytes = bytesPerSample * inputChannels
        if (frameBytes <= 0) return
        val frameCount = inputBuffer.remaining() / frameBytes
        if (frameCount <= 0) return

        val source = FloatArray(frameCount * inputChannels)
        val nativeInput = inputBuffer.order(ByteOrder.nativeOrder())
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_FLOAT -> nativeInput.asFloatBuffer().get(source)
            C.ENCODING_PCM_16BIT -> {
                val shorts = nativeInput.asShortBuffer()
                for (i in source.indices) source[i] = shorts.get().toFloat() / 32768f
            }
            else -> return
        }
        inputBuffer.position(inputBuffer.limit())

        val matrix = downmixMatrix
        var processed = if (matrix == null) source else mixToStereo(source, frameCount, matrix)
        for (decimator in decimators) processed = decimator.process(processed)

        val outputBytes = processed.size * bytesPerSample
        val output = replaceOutputBuffer(outputBytes).order(ByteOrder.nativeOrder())
        when (outputEncoding) {
            C.ENCODING_PCM_FLOAT -> processed.forEach { output.putFloat(it) }
            C.ENCODING_PCM_16BIT -> processed.forEach { sample ->
                val clipped = sample.coerceIn(-1f, 1f)
                val scaled = if (clipped < 0f) clipped * 32768f else clipped * 32767f
                output.putShort(scaled.toInt().toShort())
            }
        }
        output.flip()
    }

    override fun onFlush() {
        decimators.forEach(Decimator::reset)
    }

    override fun onReset() {
        decimators = emptyList()
        outputEncoding = C.ENCODING_INVALID
        outputChannelCount = 0
        downmixMatrix = null
    }

    private fun bytesPerSample(encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_FLOAT -> 4
        C.ENCODING_PCM_16BIT -> 2
        else -> 0
    }

    private fun mixToStereo(source: FloatArray, frames: Int, matrix: Array<FloatArray>): FloatArray {
        val inputChannels = matrix[0].size
        val output = FloatArray(frames * 2)
        for (frame in 0 until frames) {
            val base = frame * inputChannels
            var left = 0f
            var right = 0f
            for (channel in 0 until inputChannels) {
                val sample = source[base + channel]
                left += sample * matrix[0][channel]
                right += sample * matrix[1][channel]
            }
            output[frame * 2] = left
            output[frame * 2 + 1] = right
        }
        return output
    }

    private class Decimator(private val channels: Int, private val taps: FloatArray) {
        private val history = Array(channels) { FloatArray(taps.size) }
        private var writeIndex = 0
        private var phase = 0

        fun process(input: FloatArray): FloatArray {
            val frames = input.size / channels
            val output = FloatArray((ceil(frames / 2.0).toInt() + 1) * channels)
            var outputFrame = 0

            for (frame in 0 until frames) {
                for (channel in 0 until channels) {
                    history[channel][writeIndex] = input[frame * channels + channel]
                }
                if (phase == 0) {
                    for (channel in 0 until channels) {
                        var sum = history[channel][writeIndex] * taps[taps.size / 2]
                        for (tap in 0 until taps.size / 2) {
                            var firstIndex = writeIndex - tap
                            if (firstIndex < 0) firstIndex += taps.size
                            var secondIndex = writeIndex - (taps.lastIndex - tap)
                            if (secondIndex < 0) secondIndex += taps.size
                            sum += (history[channel][firstIndex] + history[channel][secondIndex]) * taps[tap]
                        }
                        output[outputFrame * channels + channel] = sum
                    }
                    outputFrame++
                }
                phase = phase xor 1
                writeIndex = if (writeIndex == taps.lastIndex) 0 else writeIndex + 1
            }
            return output.copyOf(outputFrame * channels)
        }

        fun reset() {
            history.forEach { it.fill(0f) }
            writeIndex = 0
            phase = 0
        }
    }

    companion object {
        const val MAX_ANDROID_PCM_RATE = 192_000
        const val FILTER_TAP_COUNT = 127
        const val CUTOFF_CYCLES_PER_SAMPLE = 0.225
        const val KAISER_BETA = 8.6
        private const val MIN_FALLBACK_RATE = 44_100
        val SUPPORTED_ENCODINGS = setOf(C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_16BIT)

        fun outputChannelCount(inputSampleRate: Int, inputChannelCount: Int): Int =
            if (inputSampleRate > MAX_ANDROID_PCM_RATE && inputChannelCount > 2) 2 else inputChannelCount

        fun outputSampleRate(inputSampleRate: Int): Int =
            outputSampleRate(inputSampleRate, 2, C.ENCODING_PCM_FLOAT)

        fun outputSampleRate(inputSampleRate: Int, channelCount: Int, encoding: Int): Int {
            if (inputSampleRate <= MAX_ANDROID_PCM_RATE) return inputSampleRate
            val channelConfig = when (channelCount) {
                1 -> AudioFormat.CHANNEL_OUT_MONO
                else -> AudioFormat.CHANNEL_OUT_STEREO
            }
            val androidEncoding = when (encoding) {
                C.ENCODING_PCM_16BIT -> AudioFormat.ENCODING_PCM_16BIT
                else -> AudioFormat.ENCODING_PCM_FLOAT
            }

            var rate = inputSampleRate
            while (rate > MAX_ANDROID_PCM_RATE && rate % 2 == 0) rate /= 2
            var lastCandidate = rate
            while (rate >= MIN_FALLBACK_RATE) {
                lastCandidate = rate
                val minBufferSize = try {
                    AudioTrack.getMinBufferSize(rate, channelConfig, androidEncoding)
                } catch (_: Exception) {
                    AudioTrack.ERROR_BAD_VALUE
                }
                if (minBufferSize > 0 && minBufferSize != AudioTrack.ERROR_BAD_VALUE) return rate
                if (rate % 2 != 0) break
                rate /= 2
            }
            return lastCandidate
        }

        private fun createStereoDownmixMatrix(channelCount: Int): Array<FloatArray> {
            val left = FloatArray(channelCount)
            val right = FloatArray(channelCount)
            when (channelCount) {
                3 -> {
                    left[0] = 0.707f; left[2] = 0.293f
                    right[1] = 0.707f; right[2] = 0.293f
                }
                4 -> {
                    left[0] = 0.5f; left[2] = 0.5f
                    right[1] = 0.5f; right[3] = 0.5f
                }
                5 -> {
                    left[0] = 0.414f; left[2] = 0.293f; left[3] = 0.293f
                    right[1] = 0.414f; right[2] = 0.293f; right[4] = 0.293f
                }
                6 -> {
                    // FFmpeg's default 5.1 order: FL, FR, FC, LFE, BL, BR.
                    left[0] = 0.414f; left[2] = 0.293f; left[3] = 0.086f; left[4] = 0.207f
                    right[1] = 0.414f; right[2] = 0.293f; right[3] = 0.086f; right[5] = 0.207f
                }
                7 -> {
                    left[0] = 0.38f; left[2] = 0.22f; left[3] = 0.05f; left[4] = 0.10f; left[5] = 0.25f
                    right[1] = 0.38f; right[2] = 0.22f; right[3] = 0.05f; right[4] = 0.10f; right[6] = 0.25f
                }
                8 -> {
                    left[0] = 0.30f; left[2] = 0.18f; left[3] = 0.04f; left[4] = 0.24f; left[6] = 0.24f
                    right[1] = 0.30f; right[2] = 0.18f; right[3] = 0.04f; right[5] = 0.24f; right[7] = 0.24f
                }
                else -> {
                    left[0] = 0.5f
                    right[1] = 0.5f
                    val extraWeight = 0.5f / (channelCount - 2)
                    for (channel in 2 until channelCount) {
                        if (channel % 2 == 0) left[channel] = extraWeight else right[channel] = extraWeight
                    }
                    normalize(left)
                    normalize(right)
                }
            }
            return arrayOf(left, right)
        }

        private fun normalize(weights: FloatArray) {
            val sum = weights.sum()
            if (sum > 0f) for (index in weights.indices) weights[index] /= sum
        }

        fun createLowPassTaps(): FloatArray {
            val center = (FILTER_TAP_COUNT - 1) / 2.0
            val denominator = besselI0(KAISER_BETA)
            val coefficients = FloatArray(FILTER_TAP_COUNT)
            var sum = 0.0

            for (index in coefficients.indices) {
                val x = index - center
                val ideal = if (x == 0.0) {
                    2.0 * CUTOFF_CYCLES_PER_SAMPLE
                } else {
                    sin(2.0 * PI * CUTOFF_CYCLES_PER_SAMPLE * x) / (PI * x)
                }
                val normalized = x / center
                val window = besselI0(KAISER_BETA * sqrt(1.0 - normalized * normalized)) / denominator
                val coefficient = ideal * window
                coefficients[index] = coefficient.toFloat()
                sum += coefficient
            }

            for (index in coefficients.indices) coefficients[index] = (coefficients[index] / sum).toFloat()
            return coefficients
        }

        fun besselI0(value: Double): Double {
            val quarterSquare = value * value / 4.0
            var term = 1.0
            var total = 1.0
            var index = 1
            while (index < 80) {
                term *= quarterSquare / (index * index)
                total += term
                if (term < total * 1e-14) break
                index++
            }
            return total
        }
    }
}
