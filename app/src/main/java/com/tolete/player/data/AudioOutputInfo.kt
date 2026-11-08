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
    val deviceLabel: String = "",
    /** De onde veio a taxa: mixer USB configurado, mixer do sistema etc. */
    val source: String = ""
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

/** Preferência "Saída de áudio" (Configurações > Player), compartilhada entre UI e serviço. */
object AudioOutputPrefs {
    const val KEY = "audio_output_backend"
    const val DEFAULT = "DEFAULT"
    const val HIGH_QUALITY = "HIGH_QUALITY"

    /** Valores antigos (USB_EXCLUSIVE etc.) de versões anteriores continuam funcionando. */
    fun isHighQuality(prefs: android.content.SharedPreferences): Boolean =
        isHighQuality(prefs.getString(KEY, DEFAULT))

    fun isHighQuality(value: String?): Boolean =
        value == HIGH_QUALITY || value == "USB_EXCLUSIVE"
}

/** Resumo da saída em uso, pronto para a tela de configurações. */
data class OutputSummary(
    val deviceName: String,
    val deviceType: String,
    val format: String,
    val route: String,
    val notes: List<String>
)

object AudioOutputInfo {

    /**
     * Saída preferida no modo "Alta qualidade": DAC/headset USB primeiro, depois fone com fio.
     * Null = nenhum dos dois conectado (o sistema escolhe a rota).
     */
    fun findHighQualityOutput(context: Context): AudioDeviceInfo? {
        UsbAudioRouter.findUsbOutput(context)?.let { return it }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        return try {
            val sinks = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.isSink }
            sinks.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES }
                ?: sinks.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }
                ?: sinks.firstOrNull { it.type == AudioDeviceInfo.TYPE_LINE_ANALOG }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Dispositivo por onde o áudio de música sai agora. No Android 13+ pergunta ao sistema qual rota
     * seria usada; antes disso segue a ordem de prioridade do Android (USB > fio > Bluetooth > alto-falante).
     */
    fun activeDevice(context: Context): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                val attrs = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                am.getAudioDevicesForAttributes(attrs).firstOrNull { it.isSink }?.let { return it }
            } catch (_: Throwable) { }
        }
        return try {
            val sinks = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.isSink }
            fun first(vararg types: Int) = sinks.firstOrNull { it.type in types }
            first(AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET)
                ?: first(
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_LINE_ANALOG
                )
                ?: first(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
                ?: (if (Build.VERSION.SDK_INT >= 28) first(AudioDeviceInfo.TYPE_HEARING_AID) else null)
                ?: first(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
                ?: sinks.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    fun typeLabel(d: AudioDeviceInfo?): String = when (d?.type) {
        null -> "Desconhecida"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "DAC USB"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "Headset USB"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Fone com fio"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Fone com fio (com microfone)"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "Saída analógica"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth (A2DP)"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Alto-falante do aparelho"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Auricular do aparelho"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        else -> if (Build.VERSION.SDK_INT >= 28 && d.type == AudioDeviceInfo.TYPE_HEARING_AID)
            "Aparelho auditivo" else "Saída de áudio"
    }

    /** "Tipo · Nome do produto" (ex.: "DAC USB · FiiO KA3"). */
    fun describeDevice(d: AudioDeviceInfo): String {
        val name = d.productName?.toString()?.trim().orEmpty()
        val type = typeLabel(d)
        return if (name.isNotBlank() && !name.equals(Build.MODEL, ignoreCase = true)) "$type · $name" else type
    }

    /** Monta o resumo exibido no campo de informação da tela de configurações. */
    fun summary(context: Context, highQualityMode: Boolean, playerFloat: Boolean): OutputSummary {
        val dev = activeDevice(context)
        val fmt = probe(context)
        val name = dev?.productName?.toString()?.trim().orEmpty()
            .takeIf { it.isNotBlank() && !it.equals(Build.MODEL, ignoreCase = true) }
            ?: fmt.deviceLabel.takeIf { it.isNotBlank() && !it.equals(Build.MODEL, ignoreCase = true) }
            ?: "—"
        val isUsb = dev != null && UsbAudioRouter.isUsbType(dev.type)
        val isWired = dev != null && (
            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                dev.type == AudioDeviceInfo.TYPE_LINE_ANALOG
            )
        val notes = mutableListOf<String>()
        notes += if (highQualityMode) {
            "Modo: Alta qualidade" + if (playerFloat) " · PCM float 32-bit" else ""
        } else "Modo: Padrão do sistema"
        if (highQualityMode && !isUsb && !isWired) {
            notes += "Conecte um DAC USB ou fone com fio para aproveitar este modo."
        }
        if (isUsb) {
            if (Build.VERSION.SDK_INT < 34) {
                notes += "Android abaixo do 14: o sistema define a taxa do USB e o app não consegue mudá-la."
            } else if (highQualityMode) {
                if (UsbMixer.status.isNotBlank()) notes += UsbMixer.status
                notes += "Bit-perfect: " + UsbMixer.bitPerfectSupport(context)
            }
        } else if (isWired && highQualityMode) {
            notes += "Saída analógica: o Android costuma reamostrar para a taxa do mixer do sistema."
        }
        return OutputSummary(
            deviceName = name,
            deviceType = typeLabel(dev),
            format = fmt.label,
            route = fmt.source,
            notes = notes
        )
    }

    fun probe(context: Context): OutputFormat {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // 1) DAC USB com mixer configurado (Android 14+): é a taxa REAL do hardware.
        //    PROPERTY_OUTPUT_SAMPLE_RATE descreve só a saída primária (alto-falante),
        //    por isso antes sempre aparecia 48 kHz mesmo com o DAC conectado.
        val usb = UsbAudioRouter.findUsbOutput(context)
        if (usb != null) {
            val usbLabel = usb.productName?.toString()?.trim().orEmpty()
                .ifBlank { UsbAudioRouter.label(usb) }
            val mixer = UsbMixer.currentMixerFormat(context, usb)
            if (mixer != null && mixer.sampleRate > 0) {
                return OutputFormat(
                    sampleRateHz = mixer.sampleRate,
                    bitDepth = UsbMixer.bitsOf(mixer.encoding).takeIf { it > 0 } ?: 16,
                    deviceLabel = usbLabel,
                    source = if (UsbMixer.mixerBitPerfect) "Mixer USB · bit-perfect" else "Mixer USB configurado"
                )
            }
            // 2) USB sem mixer configurado: o Android usa o formato padrão do mixer
            //    (o app não consegue ler a taxa do hardware nesse caso).
            val sysSr = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 0
            return OutputFormat(
                sampleRateHz = sysSr,
                bitDepth = maxBits(usb),
                deviceLabel = usbLabel,
                source = when {
                    android.os.Build.VERSION.SDK_INT < 34 ->
                        "Padrão do sistema (Android < 14 não permite mudar a taxa do USB)"
                    UsbMixer.status.isNotBlank() -> "Padrão do sistema · ${UsbMixer.status}"
                    else -> "Padrão do sistema (ative \"USB exclusivo\" para seguir a faixa)"
                }
            )
        }

        // 3) Sem USB: taxa nativa da saída primária (o Android reamostra para ela)
        val outSr = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: 0

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
                        (Build.VERSION.SDK_INT >= 28 && it.type == AudioDeviceInfo.TYPE_HEARING_AID)
                } ?: sinks.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                } ?: sinks.firstOrNull()

                preferred?.let { d ->
                    label = d.productName?.toString().orEmpty()
                    bitDepth = maxBits(d)
                }
            } catch (_: Exception) { }
        }

        return OutputFormat(sampleRateHz = outSr, bitDepth = bitDepth, deviceLabel = label, source = "Mixer do sistema")
    }

    private fun maxBits(d: AudioDeviceInfo): Int {
        var bitDepth = 16
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
        return bitDepth
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
