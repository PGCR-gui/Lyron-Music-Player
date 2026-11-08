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

    /** Abaixo disto o mixer USB não é reconfigurado (usa o padrão do sistema). */
    private const val LOW_RATE_HZ = 48_000

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
    /** Combinações que o sistema já recusou (evita repetir a tentativa a cada faixa). */
    private val rejectedKeys = mutableSetOf<String>()
    private var dumpedDeviceId: Int = -1

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

            fun isBp(m: AudioMixerAttributes) = m.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
            fun keyOf(m: AudioMixerAttributes) = "${usb.id}:${m.format.sampleRate}:${m.format.encoding}:" +
                "${m.format.channelMask}:${m.mixerBehavior}"

            // Registra uma vez por DAC tudo o que ele publica (ajuda a diagnosticar recusas).
            if (dumpedDeviceId != usb.id) {
                dumpedDeviceId = usb.id
                Log.i(TAG, "Mixer suportado por ${usb.productName}: " + supported.joinToString(" | ") {
                    "${it.format.sampleRate}Hz/${bitsOf(it.format.encoding)}b" +
                        "(enc=${it.format.encoding})/ch${it.format.channelCount}/" + (if (isBp(it)) "BP" else "DEF")
                })
            }

            /** Pede o formato ao sistema; lembra o que foi recusado para não repetir a cada faixa. */
            fun tryApply(m: AudioMixerAttributes): Boolean {
                val k = keyOf(m)
                if (k in rejectedKeys) return false
                val ok = am.setPreferredMixerAttributes(mediaAttributes(), usb, m)
                Log.i(
                    TAG,
                    "setPreferredMixerAttributes ok=$ok rate=${m.format.sampleRate} enc=${m.format.encoding} " +
                        "behavior=${if (isBp(m)) "BP" else "DEF"} bitPerfect=$bitPerfect track=${sampleRate}Hz"
                )
                if (!ok) rejectedKeys += k
                return ok
            }

            fun remember(m: AudioMixerAttributes) {
                lastKey = keyOf(m)
                lastDeviceId = usb.id
                mixerSampleRate = m.format.sampleRate
                mixerEncoding = m.format.encoding
                mixerBitPerfect = isBp(m)
            }

            // 1) Bit-perfect (opcional). Pela documentação do Android, só engata se a reprodução tiver
            //    exatamente a mesma codificação, canais e taxa do mixer escolhido; por isso o filtro é estrito.
            var bpNote = ""
            if (bitPerfect) {
                val exactBp = supported.filter {
                    isBp(it) && it.format.sampleRate == sampleRate &&
                        it.format.encoding == encoding && it.format.channelCount == channelCount
                }
                if (exactBp.isNotEmpty()) {
                    exactBp.firstOrNull { keyOf(it) == lastKey }?.let { return }
                    val m = exactBp.firstOrNull { tryApply(it) }
                    if (m != null) {
                        remember(m)
                        status = "Bit-perfect ativo · ${formatHz(m.format.sampleRate)} · ${encodingLabel(m.format.encoding)}"
                        return
                    }
                    bpNote = "O sistema recusou o bit-perfect · "
                } else {
                    val published = supported.filter { isBp(it) }
                        .map { "${formatHz(it.format.sampleRate)}/${encodingLabel(it.format.encoding)}" }
                        .distinct()
                    bpNote = if (published.isEmpty()) {
                        "Este DAC/aparelho não publica bit-perfect · "
                    } else {
                        "Bit-perfect indisponível para ${formatHz(sampleRate)}/${encodingLabel(encoding)} " +
                            "(o DAC publica: ${published.take(6).joinToString(", ")}) · "
                    }
                }
            }

            // 2) Taxas baixas (abaixo de 48 kHz): fica no mixer padrão do sistema. Pedir mixer USB
            //    nessas taxas deixava o som distorcido neste tipo de DAC.
            if (sampleRate < LOW_RATE_HZ) {
                if (lastKey != null) {
                    try { am.clearPreferredMixerAttributes(mediaAttributes(), usb) } catch (_: Throwable) { }
                    resetState()
                }
                status = bpNote + "Faixa de ${formatHz(sampleRate)}: mixer padrão do sistema"
                return
            }

            // 3) Mixer padrão na taxa da faixa (a partir de 48 kHz), com fallback de formato.
            val rates = supported.map { it.format.sampleRate }.filter { it > 0 }.distinct().sorted()
            // taxa da faixa; senão a preferida; senão a mais próxima acima; senão a maior
            val target = when {
                sampleRate in rates -> sampleRate
                preferredHz > 0 && preferredHz in rates -> preferredHz
                else -> rates.firstOrNull { it >= sampleRate } ?: rates.last()
            }
            val atRate = supported.filter { it.format.sampleRate == target }
            val sameCh = atRate.filter { it.format.channelCount == channelCount }.ifEmpty { atRate }

            fun byPreference(list: List<AudioMixerAttributes>) = list.sortedWith(
                compareByDescending<AudioMixerAttributes> { it.format.encoding == encoding }
                    .thenByDescending { encodingRank(it.format.encoding) }
            )
            // 16-bit só entra se a própria faixa for 16-bit: reduzir hi-res para 16 bits anularia o objetivo.
            val minRank = if (encoding == AudioFormat.ENCODING_PCM_16BIT) 1 else 2
            val candidates = byPreference(
                sameCh.filter { !isBp(it) && encodingRank(it.format.encoding) >= minRank }
            )
            if (candidates.isEmpty()) {
                status = bpNote + "Nenhum formato compatível em ${formatHz(target)}"
                return
            }

            // Já aplicado e ainda válido? Não reabre a saída (evita clique/glitch).
            candidates.firstOrNull { keyOf(it) == lastKey }?.let { return }

            val refused = mutableListOf<String>()
            var applied: AudioMixerAttributes? = null
            for (m in candidates) {
                if (tryApply(m)) { applied = m; break }
                refused += encodingLabel(m.format.encoding)
            }
            val chosen = applied
            if (chosen == null) {
                status = bpNote + "O sistema recusou todos os formatos testados em ${formatHz(target)} " +
                    "(${refused.distinct().joinToString(", ")}); fica no padrão"
                return
            }
            remember(chosen)
            val fallbackNote = if (refused.isNotEmpty()) " (recusados antes: ${refused.distinct().joinToString(", ")})" else ""
            status = bpNote + "Mixer USB configurado · ${formatHz(chosen.format.sampleRate)} · " +
                "${encodingLabel(chosen.format.encoding)}$fallbackNote"
        } catch (e: Throwable) {
            status = "Falha ao configurar mixer USB: ${e.message}"
            Log.w(TAG, "setPreferredMixerAttributes falhou", e)
        }
    }

    /**
     * Resumo de suporte a bit-perfect para a tela de configurações:
     * diz se o DAC conectado publica algum formato bit-perfect (não precisa de driver extra:
     * usa o driver USB de áudio do próprio Android 14+).
     */
    fun bitPerfectSupport(context: Context): String {
        if (Build.VERSION.SDK_INT < 34) return "Requer Android 14 ou mais novo"
        val usb = UsbAudioRouter.findUsbOutput(context) ?: return "Conecte um DAC USB para verificar"
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return "Indisponível"
        return try {
            val bp = am.getSupportedMixerAttributes(usb)
                .filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
            if (bp.isEmpty()) {
                "Este DAC/aparelho não publica bit-perfect"
            } else {
                val fmts = bp.map { "${formatHz(it.format.sampleRate)}/${encodingLabel(it.format.encoding)}" }
                    .distinct()
                "Disponível: " + fmts.take(6).joinToString(", ") + if (fmts.size > 6) "…" else ""
            }
        } catch (_: Throwable) {
            "Não foi possível consultar o DAC"
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
        rejectedKeys.clear()
        dumpedDeviceId = -1
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