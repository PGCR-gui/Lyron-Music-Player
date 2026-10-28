package com.tolete.player.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.util.Log

/**
 * Controla a taxa de amostragem REAL do DAC USB.
 *
 * Só rotear o AudioTrack para o USB (setPreferredDevice) não muda a taxa do hardware:
 * o Android continua abrindo a saída USB no formato padrão do mixer (normalmente 48 kHz)
 * e reamostra tudo para ela. A partir do Android 14 (API 34) existe a API oficial
 * [AudioManager.setPreferredMixerAttributes], que reabre a saída USB na taxa/formato pedidos
 * (e opcionalmente em modo BIT_PERFECT). Em Android < 14 não há como um app mudar isso.
 *
 * CORREÇÃO nesta versão:
 * 1) Bit-perfect não exige mais encoding idêntico ao do AudioTrack (que só pode ser 16-bit
 *    ou float no Media3). DACs que publicam BIT_PERFECT só em 24/32-bit packed agora engajam.
 * 2) Status honesto: a tela de informações técnicas passa a dizer exatamente o que o
 *    sistema aceitou ou recusou — nada de fingir que está tudo bem.
 */
object UsbMixer {

    private const val TAG = "UsbMixer"

    /** Estado aplicado (lido pela tela de Informações técnicas). */
    @Volatile var trackSampleRate: Int = 0
        private set
    @Volatile var trackEncoding: Int = 0
        private set
    @Volatile var mixerSampleRate: Int = 0
        private set
    @Volatile var mixerEncoding: Int = 0
        private set
    @Volatile var mixerBitPerfect: Boolean = false
        private set
    @Volatile var status: String = ""
        private set

    private var lastKey: String? = null
    private var lastDeviceId: Int = -1

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= 34

    private fun mediaAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

    /**
     * Chamado na thread de reprodução ANTES do AudioTrack ser criado (AudioSink.configure).
     * @param sampleRate taxa do PCM que o app vai entregar ao AudioTrack
     * @param channelCount canais do PCM
     * @param encoding encoding android.media.AudioFormat do AudioTrack (16-bit, float, 24/32-bit)
     * @param preferredHz taxa preferida do usuário (0 = automática)
     * @param bitPerfect pede modo bit-perfect quando o DAC publicar suporte
     */
    fun applyForTrack(
        context: Context,
        sampleRate: Int,
        channelCount: Int,
        encoding: Int,
        preferredHz: Int,
        bitPerfect: Boolean
    ) {
        trackSampleRate = sampleRate
        trackEncoding = encoding
        if (Build.VERSION.SDK_INT < 34) {
            status = "Android < 14: o sistema define a taxa do USB"
            return
        }
        val usb = UsbAudioRouter.findUsbOutput(context)
        if (usb == null) {
            status = "Nenhum DAC USB conectado"
            resetState()
            return
        }
        applyApi34(context, usb, sampleRate, channelCount, encoding, preferredHz, bitPerfect)
    }

    @androidx.annotation.RequiresApi(34)
    private fun applyApi34(
        context: Context,
        usb: AudioDeviceInfo,
        sampleRate: Int,
        channelCount: Int,
        encoding: Int,
        preferredHz: Int,
        bitPerfect: Boolean
    ) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            val supported = am.getSupportedMixerAttributes(usb)
            if (supported.isEmpty()) {
                status = "DAC/aparelho não publica formatos de mixer (fica no padrão do sistema)"
                Log.w(TAG, "getSupportedMixerAttributes vazio para ${usb.productName}")
                return
            }
            val rates = supported.map { it.format.sampleRate }.filter { it > 0 }.distinct().sorted()
            // 1) taxa da faixa; 2) taxa preferida; 3) a mais próxima acima; 4) a maior
            val target = when {
                sampleRate in rates -> sampleRate
                preferredHz > 0 && preferredHz in rates -> preferredHz
                else -> rates.firstOrNull { it >= sampleRate } ?: rates.last()
            }
            val atRate = supported.filter { it.format.sampleRate == target }
            val sameCh = atRate.filter { it.format.channelCount == channelCount }.ifEmpty { atRate }

            val exact = sameCh.filter { it.format.encoding == encoding }
            val defaultEntries = sameCh.filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_DEFAULT }
            val bitPerfectEntries = sameCh.filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }

            // CORREÇÃO: antes, o bit-perfect só engajava se o encoding do mixer batesse
            // EXATAMENTE com o do AudioTrack (que só pode ser 16-bit ou float). DACs que
            // publicam BIT_PERFECT apenas em 24/32-bit packed ficavam sempre no modo
            // padrão. Agora aceitamos qualquer entrada BIT_PERFECT na taxa da faixa:
            // encoding exato em primeiro lugar, depois o de maior precisão.
            val chosen: AudioMixerAttributes? = if (bitPerfect) {
                bitPerfectEntries.firstOrNull { it.format.encoding == encoding }
                    ?: bitPerfectEntries.maxByOrNull { encodingRank(it.format.encoding) }
                    ?: exact.firstOrNull()
                    ?: defaultEntries.maxByOrNull { encodingRank(it.format.encoding) }
            } else {
                exact.firstOrNull { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_DEFAULT }
                    ?: defaultEntries.maxByOrNull { encodingRank(it.format.encoding) }
            }

            if (chosen == null) {
                status = "Nenhum formato compatível em ${formatHz(target)}"
                return
            }

            val key = "${usb.id}:${chosen.format.sampleRate}:${chosen.format.encoding}:" +
                "${chosen.format.channelMask}:${chosen.mixerBehavior}"
            if (key == lastKey) return // já aplicado — evita reabrir a saída (clique/glitch)

            val ok = am.setPreferredMixerAttributes(mediaAttributes(), usb, chosen)
            if (ok) {
                lastKey = key
                lastDeviceId = usb.id
                mixerSampleRate = chosen.format.sampleRate
                mixerEncoding = chosen.format.encoding
                mixerBitPerfect = chosen.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
                // Status honesto: diz exatamente o que o sistema aceitou (ou recusou).
                status = when {
                    mixerBitPerfect ->
                        "Mixer USB bit-perfect · ${formatHz(chosen.format.sampleRate)} · ${encodingLabel(chosen.format.encoding)}"
                    bitPerfect ->
                        "DAC não publica bit-perfect em ${formatHz(target)} · saída no mixer padrão ${encodingLabel(chosen.format.encoding)}"
                    else ->
                        "Mixer USB configurado · ${formatHz(chosen.format.sampleRate)} · ${encodingLabel(chosen.format.encoding)}"
                }
            } else {
                status = "O sistema recusou o formato pedido (fica no padrão)"
            }
            Log.i(
                TAG,
                "setPreferredMixerAttributes ok=$ok rate=${chosen.format.sampleRate} " +
                    "enc=${chosen.format.encoding} bitPerfect=$mixerBitPerfect track=${sampleRate}Hz"
            )
        } catch (e: Throwable) {
            status = "Falha ao configurar mixer USB: ${e.message}"
            Log.w(TAG, "setPreferredMixerAttributes falhou", e)
        }
    }

    /** Taxa/encoding efetivos do mixer USB, consultados no sistema (API 34+). */
    fun currentMixerFormat(context: Context, usb: AudioDeviceInfo?): AudioFormat? {
        if (usb == null || Build.VERSION.SDK_INT < 34) return null
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        return try {
            am.getPreferredMixerAttributes(mediaAttributes(), usb)?.format
        } catch (_: Throwable) {
            null
        }
    }

    /** Devolve o USB ao padrão do sistema (ao fechar o player / trocar de backend). */
    fun clear(context: Context) {
        if (Build.VERSION.SDK_INT >= 34) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val usb = UsbAudioRouter.usbOutputs(context).firstOrNull { it.id == lastDeviceId }
                ?: UsbAudioRouter.findUsbOutput(context)
            if (am != null && usb != null) {
                try {
                    am.clearPreferredMixerAttributes(mediaAttributes(), usb)
                } catch (_: Throwable) { }
            }
        }
        resetState()
    }

    /** Esquece o último formato aplicado (ex.: DAC reconectado) para reaplicar no próximo configure. */
    fun invalidate() {
        lastKey = null
    }

    private fun resetState() {
        lastKey = null
        lastDeviceId = -1
        mixerSampleRate = 0
        mixerEncoding = 0
        mixerBitPerfect = false
    }

    fun bitsOf(encoding: Int): Int = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> 8
        AudioFormat.ENCODING_PCM_16BIT -> 16
        21 /* ENCODING_PCM_24BIT_PACKED */ -> 24
        22 /* ENCODING_PCM_32BIT */ -> 32
        AudioFormat.ENCODING_PCM_FLOAT -> 32
        else -> 0
    }

    fun encodingLabel(encoding: Int): String = when (encoding) {
        AudioFormat.ENCODING_PCM_FLOAT -> "32-bit float"
        else -> bitsOf(encoding).takeIf { it > 0 }?.let { "$it-bit" } ?: "—"
    }

    private fun encodingRank(encoding: Int): Int = when (encoding) {
        AudioFormat.ENCODING_PCM_FLOAT -> 4
        22 -> 3
        21 -> 2
        AudioFormat.ENCODING_PCM_16BIT -> 1
        else -> 0
    }

    private fun formatHz(hz: Int): String =
        if (hz % 1000 == 0) "${hz / 1000} kHz" else String.format("%.1f kHz", hz / 1000.0)
}