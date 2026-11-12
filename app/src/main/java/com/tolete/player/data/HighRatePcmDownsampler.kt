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
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** Resampling sinc contínuo, redução FIR DSF e downmix multicanal para a saída compatível. */
@UnstableApi
class HighRatePcmDownsampler(
    private val resample44100To48000: Boolean = false,
    private val dsfSourceProvider: () -> Boolean = { false },
    private val forceStereoProvider: (Int) -> Boolean = { false },
    private val supportedOutputSampleRatesProvider: () -> Set<Int> = { emptySet() }
) : BaseAudioProcessor() {
    private var decimators: List<Decimator> = emptyList()
    private var outputEncoding = C.ENCODING_INVALID
    private var outputChannelCount = 0
    private var downmixMatrix: Array<FloatArray>? = null
    private var resampleOutputRate = 0
    private var resampleInputRate = 0
    private var nextResamplePosition = 0.0
    private var resampleKernel: Array<FloatArray>? = null
    private var resampleHistory = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.channelCount <= 0 ||
            inputAudioFormat.encoding !in SUPPORTED_ENCODINGS
        ) {
            decimators = emptyList()
            outputChannelCount = inputAudioFormat.channelCount
            downmixMatrix = null
            resampleOutputRate = 0
            resampleInputRate = 0
            nextResamplePosition = 0.0
            resampleKernel = null
            resampleHistory = FloatArray(0)
            return AudioProcessor.AudioFormat.NOT_SET
        }

        val isDsfSource = try { dsfSourceProvider() } catch (_: Throwable) { false }
        val forceStereo = if (isDsfSource && inputAudioFormat.channelCount > 2) {
            try { forceStereoProvider(inputAudioFormat.channelCount) } catch (_: Throwable) { true }
        } else false
        val channelCount = outputChannelCount(
            inputAudioFormat.sampleRate,
            inputAudioFormat.channelCount,
            forceStereo
        )
        val supportedOutputSampleRates = try {
            supportedOutputSampleRatesProvider().filter { it > 0 }.toSet()
        } catch (_: Throwable) {
            emptySet()
        }
        val outputRate = outputSampleRate(
            inputAudioFormat.sampleRate,
            channelCount,
            inputAudioFormat.encoding,
            resample44100To48000,
            isDsfSource,
            supportedOutputSampleRates
        )
        val stageInputRates = mutableListOf<Int>()
        var processedInputRate = inputAudioFormat.sampleRate
        // Evita executar o FIR longo em 352,8 kHz ou acima: faz primeiro estágios de
        // decimação FIR anti-alias e aplica o corte DSD de 18 kHz no estágio final.
        val decimationTargetRate = if (isDsfSource) {
            minOf(outputRate, DSF_FILTER_STAGE_RATE_HZ)
        } else outputRate
        if (processedInputRate > 0 && outputRate > 0) {
            while (processedInputRate > decimationTargetRate && processedInputRate % 2 == 0 &&
                processedInputRate / 2 >= decimationTargetRate
            ) {
                stageInputRates += processedInputRate
                processedInputRate /= 2
            }
        }
        val needsDownmix = channelCount < inputAudioFormat.channelCount
        val needsDecimate = outputRate < inputAudioFormat.sampleRate
        val needsDsfFilter = isDsfSource && inputAudioFormat.sampleRate > 0
        // A etapa sinc recebe a taxa resultante dos FIRs e conserva histórico entre buffers.
        val needsResample = outputRate != processedInputRate
        val needsPcmTransform = outputRate != inputAudioFormat.sampleRate || needsDownmix || needsDsfFilter
        val outputEncoding = if (
            (needsPcmTransform && inputAudioFormat.encoding in CONVERT_TO_FLOAT_ENCODINGS)
        ) C.ENCODING_PCM_FLOAT else inputAudioFormat.encoding
        val needsFormatConversion = outputEncoding != inputAudioFormat.encoding

        // Nada a fazer (taxa e canais já ok para o device)
        if (!needsDownmix && !needsDecimate && !needsResample && !needsDsfFilter && !needsFormatConversion) {
            decimators = emptyList()
            outputChannelCount = inputAudioFormat.channelCount
            downmixMatrix = null
            resampleOutputRate = 0
            resampleInputRate = 0
            nextResamplePosition = 0.0
            resampleKernel = null
            resampleHistory = FloatArray(0)
            return AudioProcessor.AudioFormat.NOT_SET
        }

        downmixMatrix = if (needsDownmix) {
            createStereoDownmixMatrix(inputAudioFormat.channelCount)
        } else {
            null
        }
        outputChannelCount = channelCount
        resampleOutputRate = outputRate.takeIf { needsResample } ?: 0
        resampleInputRate = processedInputRate.takeIf { needsResample } ?: 0
        nextResamplePosition = 0.0
        resampleHistory = FloatArray(0)
        resampleKernel = if (needsResample) createResampleKernel(processedInputRate, outputRate) else null
        decimators = when {
            stageInputRates.isNotEmpty() -> stageInputRates.mapIndexed { index, stageRate ->
                val cutoff = if (needsDsfFilter && index == stageInputRates.lastIndex) {
                    minOf(CUTOFF_CYCLES_PER_SAMPLE, DSF_AUDIO_CUTOFF_HZ / stageRate.toDouble())
                } else if (needsDsfFilter) 0.10 else CUTOFF_CYCLES_PER_SAMPLE
                val tapCount = when {
                    needsDsfFilter && index == stageInputRates.lastIndex ->
                        scaledDsfTapCount(stageRate).coerceAtMost(127)
                    needsDsfFilter -> 39
                    else -> FILTER_TAP_COUNT
                }
                Decimator(channelCount, createLowPassTaps(cutoff, tapCount))
            }
            needsDsfFilter -> listOf(
                Decimator(
                    channelCount,
                    createLowPassTaps(
                        minOf(0.45, DSF_AUDIO_CUTOFF_HZ / inputAudioFormat.sampleRate),
                        scaledDsfTapCount(inputAudioFormat.sampleRate).coerceAtMost(127)
                    ),
                    downsampleByTwo = false
                )
            )
            else -> emptyList()
        }
        this.outputEncoding = outputEncoding
        Log.i(
            "DsfPcmProcessor",
                "Configured ${inputAudioFormat.sampleRate}Hz/${inputAudioFormat.channelCount}ch " +
                "enc=${inputAudioFormat.encoding} -> ${outputRate}Hz/${channelCount}ch " +
                "stages=${stageInputRates.size} downmix=$needsDownmix dsf=$isDsfSource " +
                "resample=${processedInputRate}->$outputRate forcedStereo=$forceStereo " +
                "supportedRates=${supportedOutputSampleRates.sorted().joinToString(",")}"
        )
        return AudioProcessor.AudioFormat(outputRate, channelCount, outputEncoding)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val inputChannels = inputAudioFormat.channelCount
        val inputBytesPerSample = bytesPerSample(inputAudioFormat.encoding)
        val frameBytes = inputBytesPerSample * inputChannels
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
            C.ENCODING_PCM_24BIT -> {
                for (i in source.indices) {
                    val offset = inputBuffer.position() + i * 3
                    val value = (nativeInput.get(offset).toInt() and 0xFF) or
                        ((nativeInput.get(offset + 1).toInt() and 0xFF) shl 8) or
                        (nativeInput.get(offset + 2).toInt() shl 16)
                    source[i] = value / 8_388_608f
                }
            }
            C.ENCODING_PCM_32BIT -> {
                val ints = nativeInput.asIntBuffer()
                for (i in source.indices) source[i] = ints.get().toFloat() / 2_147_483_648f
            }
            C.ENCODING_PCM_8BIT -> {
                for (i in source.indices) {
                    source[i] = ((nativeInput.get(inputBuffer.position() + i).toInt() and 0xFF) - 128) / 128f
                }
            }
            else -> return
        }
        inputBuffer.position(inputBuffer.limit())

        val matrix = downmixMatrix
        var processed = if (matrix == null) source else mixToStereo(source, frameCount, matrix)
        for (decimator in decimators) processed = decimator.process(processed)
        if (resampleOutputRate > 0 && resampleInputRate > 0) {
            processed = resampleSinc(
                processed,
                outputChannelCount,
                resampleInputRate,
                resampleOutputRate
            )
        }

        val outputBytes = processed.size * bytesPerSample(outputEncoding)
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
        nextResamplePosition = 0.0
        resampleHistory = FloatArray(0)
    }

    override fun onReset() {
        decimators = emptyList()
        outputEncoding = C.ENCODING_INVALID
        outputChannelCount = 0
        downmixMatrix = null
        resampleOutputRate = 0
        resampleInputRate = 0
        nextResamplePosition = 0.0
        resampleKernel = null
        resampleHistory = FloatArray(0)
    }

    private fun bytesPerSample(encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_8BIT -> 1
        C.ENCODING_PCM_FLOAT -> 4
        C.ENCODING_PCM_16BIT -> 2
        C.ENCODING_PCM_24BIT -> 3
        C.ENCODING_PCM_32BIT -> 4
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

    /** Sinc windowed entre a taxa intermediária FIR e a taxa DAC, com continuidade entre buffers. */
    private fun resampleSinc(input: FloatArray, channels: Int, inputRate: Int, outputRate: Int): FloatArray {
        if (channels <= 0 || input.isEmpty()) return input
        val inputFrames = input.size / channels
        if (inputFrames <= 0) return input
        val ratio = inputRate.toDouble() / outputRate
        if (ratio <= 0.0) return input
        val historyFrames = if (resampleHistory.size % channels == 0) resampleHistory.size / channels else 0
        val combined = FloatArray(resampleHistory.size + input.size)
        if (resampleHistory.isNotEmpty()) resampleHistory.copyInto(combined)
        input.copyInto(combined, destinationOffset = resampleHistory.size)
        val combinedFrames = combined.size / channels
        val estimatedFrames = ceil(
            (inputFrames + RESAMPLE_RADIUS * 2 + kotlin.math.abs(nextResamplePosition)) / ratio
        ).toInt() + 2
        val output = FloatArray(estimatedFrames.coerceAtLeast(1) * channels)
        val kernel = resampleKernel ?: createResampleKernel(inputRate, outputRate)
        var samplePosition = nextResamplePosition + historyFrames
        var outputFrame = 0
        while (outputFrame < estimatedFrames) {
            val center = floor(samplePosition).toInt()
            // Mantém lookahead de um raio; a borda final será processada no próximo buffer.
            if (center + RESAMPLE_RADIUS >= combinedFrames) break
            val first = center - RESAMPLE_RADIUS + 1
            val fraction = samplePosition - center
            val phase = (fraction * RESAMPLE_PHASES).toInt().coerceIn(0, RESAMPLE_PHASES - 1)
            val coefficients = kernel[phase]
            for (channel in 0 until channels) {
                var weighted = 0.0
                for (tap in coefficients.indices) {
                    val frame = first + tap
                    val sourceFrame = frame.coerceIn(0, combinedFrames - 1)
                    weighted += combined[sourceFrame * channels + channel] * coefficients[tap]
                }
                output[outputFrame * channels + channel] = weighted.toFloat()
            }
            outputFrame++
            samplePosition += ratio
        }
        nextResamplePosition += outputFrame * ratio - inputFrames
        val keepFrames = minOf(RESAMPLE_RADIUS * 2, combinedFrames)
        resampleHistory = combined.copyOfRange(
            (combinedFrames - keepFrames) * channels,
            combinedFrames * channels
        )
        return output.copyOf(outputFrame * channels)
    }

    private fun createResampleKernel(inputRate: Int, outputRate: Int): Array<FloatArray> {
        val cutoff = minOf(1.0, outputRate.toDouble() / inputRate)
        val tapCount = RESAMPLE_RADIUS * 2
        return Array(RESAMPLE_PHASES) { phase ->
            val fraction = phase.toDouble() / RESAMPLE_PHASES
            val taps = FloatArray(tapCount)
            var sum = 0.0
            for (tap in 0 until tapCount) {
                val relativeFrame = tap - RESAMPLE_RADIUS + 1
                val distance = fraction - relativeFrame
                val scaledDistance = distance * cutoff
                val sinc = if (kotlin.math.abs(scaledDistance) < 1e-9) 1.0
                else sin(PI * scaledDistance) / (PI * scaledDistance)
                val window = if (kotlin.math.abs(distance) >= RESAMPLE_RADIUS) 0.0
                else 0.5 + 0.5 * cos(PI * distance / RESAMPLE_RADIUS)
                val coefficient = cutoff * sinc * window
                taps[tap] = coefficient.toFloat()
                sum += coefficient
            }
            if (kotlin.math.abs(sum) > 1e-12) {
                for (tap in taps.indices) taps[tap] = (taps[tap] / sum).toFloat()
            }
            taps
        }
    }

    private class Decimator(
        private val channels: Int,
        private val taps: FloatArray,
        private val downsampleByTwo: Boolean = true
    ) {
        private val history = Array(channels) { FloatArray(taps.size) }
        private var writeIndex = 0
        private var phase = 0

        fun process(input: FloatArray): FloatArray {
            val frames = input.size / channels
            val maximumOutputFrames = if (downsampleByTwo) ceil(frames / 2.0).toInt() + 1 else frames
            val output = FloatArray(maximumOutputFrames * channels)
            var outputFrame = 0

            for (frame in 0 until frames) {
                for (channel in 0 until channels) {
                    history[channel][writeIndex] = input[frame * channels + channel]
                }
                if (!downsampleByTwo || phase == 0) {
                    // Os pares são simétricos em torno da amostra atrasada pelo centro do FIR.
                    // Multiplicar o coeficiente central pela amostra atual quebrava essa simetria,
                    // deixando passar ruído ultrassônico do DSD que virava chiado audível.
                    val rawCenterIndex = writeIndex - taps.size / 2
                    val centerIndex = if (rawCenterIndex < 0) rawCenterIndex + taps.size else rawCenterIndex
                    for (channel in 0 until channels) {
                        var sum = history[channel][centerIndex] * taps[taps.size / 2]
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
                if (downsampleByTwo) phase = phase xor 1
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
        // Teto do PCM de taxa muito alta antes da prova de suporte do AudioTrack.
        const val MAX_ANDROID_PCM_RATE = 384_000
        const val INPUT_RATE_HZ = 44_100
        const val OUTPUT_RATE_HZ = 48_000
        private const val RESAMPLE_RADIUS = 12
        private const val RESAMPLE_PHASES = 256
        // Taxas ≤ isto são quase universais no Android — não precisam de probe.
        private const val SAFE_PCM_RATE = 192_000
        /** Limite da última etapa antes do sinc de saída: reduz custo sem sacrificar banda audível. */
        private const val DSF_FILTER_STAGE_RATE_HZ = 88_200
        // Saída conservadora para PCM vindo de DSD/DSF (DSD64 pode decodificar a 176,4 kHz).
        /** Preserva a banda audível e remove o ruído ultrassônico antes de entregar PCM DSF ao DAC. */
        private const val DSF_AUDIO_CUTOFF_HZ = 18_000.0
        private const val MAX_DSF_FIR_TAP_COUNT = 511
        // Mais taps + cutoff um pouco mais baixo: melhor rejeição do ruído ultrassônico
        // do DSD antes da decimação (menos aliasing/“ruído branco” na banda audível).
        const val FILTER_TAP_COUNT = 191
        const val CUTOFF_CYCLES_PER_SAMPLE = 0.20
        const val KAISER_BETA = 9.0
        private const val MIN_FALLBACK_RATE = 44_100
        val NON_FLOAT_PCM_ENCODINGS = setOf(
            C.ENCODING_PCM_8BIT,
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_32BIT
        )
        val CONVERT_TO_FLOAT_ENCODINGS = setOf(
            C.ENCODING_PCM_8BIT,
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_32BIT
        )
        val SUPPORTED_ENCODINGS = NON_FLOAT_PCM_ENCODINGS + C.ENCODING_PCM_FLOAT

        // Multicanal em taxa alta costuma falhar no AudioTrack; no DSF também se força
        // estéreo quando a rota informa não suportar a quantidade de canais da fonte.
        fun outputChannelCount(
            inputSampleRate: Int,
            inputChannelCount: Int,
            forceStereo: Boolean = false
        ): Int = if (
            inputChannelCount > 2 &&
            (forceStereo || inputSampleRate > SAFE_PCM_RATE || inputChannelCount == 3 || inputChannelCount == 5)
        ) 2 else inputChannelCount

        fun outputSampleRate(inputSampleRate: Int): Int =
            outputSampleRate(inputSampleRate, 2, C.ENCODING_PCM_FLOAT)

        fun outputSampleRate(
            inputSampleRate: Int,
            channelCount: Int,
            encoding: Int,
            resample44100To48000: Boolean = false,
            dsfSource: Boolean = false,
            supportedOutputSampleRates: Set<Int> = emptySet()
        ): Int {
            val probeEncoding = audioTrackEncoding(encoding)
            if (dsfSource) {
                val candidates = if (supportedOutputSampleRates.isNotEmpty()) {
                    supportedOutputSampleRates.filter { it in 16_000..MAX_ANDROID_PCM_RATE }.sortedDescending()
                } else {
                    listOf(48_000, 44_100, 32_000, 22_050, 16_000)
                }
                // Taxas publicadas pela rota/mixer são a capacidade relevante. O probe
                // genérico do AudioTrack pode rejeitar 192 kHz mesmo quando o mixer USB aceita.
                if (supportedOutputSampleRates.isNotEmpty()) {
                    candidates.firstOrNull()?.let { return it }
                }
                candidates.firstOrNull {
                    isAudioTrackRateUsable(it, channelCount, probeEncoding)
                }?.let { return it }
                throw IllegalStateException(
                    "Nenhuma taxa PCM DSF compatível foi anunciada/testada " +
                        "(${inputSampleRate}Hz, ${channelCount}ch, encoding=$probeEncoding)"
                )
            }
            if (resample44100To48000 && inputSampleRate == INPUT_RATE_HZ) {
                return if (isAudioTrackRateUsable(OUTPUT_RATE_HZ, channelCount, probeEncoding)) {
                    OUTPUT_RATE_HZ
                } else inputSampleRate
            }
            // ≤ 192 kHz: aceito na prática em qualquer device moderno
            if (inputSampleRate <= SAFE_PCM_RATE) return inputSampleRate

            // Preserva a maior taxa PCM normal que a rota realmente aceita. Se recusada,
            // reduz por fatores de 2 e retorna apenas um candidato aprovado pelo AudioTrack.
            var rate = inputSampleRate
            while (rate > MAX_ANDROID_PCM_RATE && rate % 2 == 0) rate /= 2
            while (rate >= MIN_FALLBACK_RATE) {
                if (isAudioTrackRateUsable(rate, channelCount, probeEncoding)) return rate
                if (rate % 2 != 0) break
                rate /= 2
            }
            return findSupportedRate(
                channelCount,
                probeEncoding,
                listOf(48_000, 44_100, 32_000, 24_000, 22_050, 16_000)
            ) ?: throw IllegalStateException(
                "AudioTrack rejeitou todas as taxas PCM testadas para " +
                    "${inputSampleRate}Hz/${channelCount}ch/encoding=$probeEncoding"
            )
        }

        private fun findSupportedRate(channelCount: Int, encoding: Int, candidates: List<Int>): Int? =
            candidates.firstOrNull { isAudioTrackRateUsable(it, channelCount, encoding) }

        private fun isAudioTrackRateUsable(sampleRate: Int, channelCount: Int, encoding: Int): Boolean {
            val mask = when (channelCount) {
                1 -> AudioFormat.CHANNEL_OUT_MONO
                2 -> AudioFormat.CHANNEL_OUT_STEREO
                4 -> AudioFormat.CHANNEL_OUT_QUAD
                6 -> AudioFormat.CHANNEL_OUT_5POINT1
                7 -> AudioFormat.CHANNEL_OUT_6POINT1
                8 -> AudioFormat.CHANNEL_OUT_7POINT1
                else -> return false
            }
            return try {
                AudioTrack.getMinBufferSize(sampleRate, mask, encoding) > 0
            } catch (_: Throwable) {
                false
            }
        }

        private fun audioTrackEncoding(encoding: Int): Int = when (encoding) {
            C.ENCODING_PCM_16BIT -> AudioFormat.ENCODING_PCM_16BIT
            else -> AudioFormat.ENCODING_PCM_FLOAT
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

        fun createLowPassTaps(
            cutoffCyclesPerSample: Double = CUTOFF_CYCLES_PER_SAMPLE,
            tapCount: Int = FILTER_TAP_COUNT
        ): FloatArray {
            val count = tapCount.coerceAtLeast(3).let { if (it % 2 == 0) it + 1 else it }
            val center = (count - 1) / 2.0
            val denominator = besselI0(KAISER_BETA)
            val coefficients = FloatArray(count)
            var sum = 0.0

            for (index in coefficients.indices) {
                val x = index - center
                val ideal = if (x == 0.0) {
                    2.0 * cutoffCyclesPerSample
                } else {
                    sin(2.0 * PI * cutoffCyclesPerSample * x) / (PI * x)
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

        /** Mantém a resolução do filtro em Hz quando a taxa de entrada sobe; o teto limita CPU. */
        private fun scaledDsfTapCount(inputRate: Int): Int =
            (FILTER_TAP_COUNT * (inputRate / 176_400.0))
                .toInt()
                .coerceIn(FILTER_TAP_COUNT, MAX_DSF_FIR_TAP_COUNT)

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
