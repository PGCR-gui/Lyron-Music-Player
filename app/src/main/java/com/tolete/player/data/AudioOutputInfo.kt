package com.tolete.player.data

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build

/**
 * Informações da **saída de áudio do dispositivo** (não do arquivo).
 * Amostragem = taxa nativa do dispositivo (Android reamostra para ela).
 * Bit depth = maior profundidade PCM suportada pelos sinks ativos (16/24/32).
 */
data class OutputFormat(
    val sampleRateHz: Int,
    val bitDepth: Int,
    val deviceLabel: String = ""
) {
    val label: String
        get() {
            val bits = if (bitDepth > 0) "${bitDepth}-bit" else "—"
            val sr = when {
                sampleRateHz >= 1000 -> {
                    val k = sampleRateHz / 1000.0
                    if (sampleRateHz % 1000 == 0) "${sampleRateHz / 1000} kHz" else String.format("%.1f kHz", k)
                }
                sampleRateHz > 0 -> "$sampleRateHz Hz"
                else -> "—"
            }
            return "$bits  ·  $sr"
        }
}

object AudioOutputInfo {

    fun probe(context: Context): OutputFormat {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val outSr = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: 48_000

        var bitDepth = 16
        var label = ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                // Prefere dispositivos "ativos"/externos quando existirem
                val sinks = devices.filter { it.isSink }
                val preferred = sinks.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                        (Build.VERSION.SDK_INT >= 28 && it.type == AudioDeviceInfo.TYPE_HEARING_AID)
                } ?: sinks.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                } ?: sinks.firstOrNull()

                preferred?.let { d ->
                    label = d.productName?.toString().orEmpty()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        for (enc in d.encodings) {
                            when (enc) {
                                AudioFormat.ENCODING_PCM_16BIT -> bitDepth = maxOf(bitDepth, 16)
                                AudioFormat.ENCODING_PCM_FLOAT -> bitDepth = maxOf(bitDepth, 32)
                                // 21 = PCM_24BIT_PACKED, 22 = PCM_32BIT (API 31)
                                21 -> bitDepth = maxOf(bitDepth, 24)
                                22 -> bitDepth = maxOf(bitDepth, 32)
                            }
                        }
                    }
                }
            } catch (_: Exception) { }
        }

        return OutputFormat(sampleRateHz = outSr, bitDepth = bitDepth, deviceLabel = label)
    }

    /**
     * Profundidade efetiva da reprodução: o menor entre o arquivo e a saída do dispositivo
     * (o que o pipeline tipicamente entrega), com preferência ao valor real do arquivo quando > 0.
     */
    fun effectiveBitDepth(fileBitDepth: Int, outputBitDepth: Int): Int {
        val file = when {
            fileBitDepth >= 32 -> 32
            fileBitDepth >= 24 -> 24
            fileBitDepth >= 16 -> 16
            fileBitDepth > 0 -> fileBitDepth
            else -> 0
        }
        val out = when {
            outputBitDepth >= 32 -> 32
            outputBitDepth >= 24 -> 24
            outputBitDepth >= 16 -> 16
            else -> 16
        }
        return when {
            file > 0 -> minOf(file, out)
            else -> out
        }
    }
}
