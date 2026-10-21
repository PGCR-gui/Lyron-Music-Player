package com.tolete.player.data

import android.content.Context
import android.media.audiofx.Equalizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gerencia o Equalizer nativo do Android ligado à sessão de áudio do ExoPlayer.
 * Persiste bandas e estado enabled em SharedPreferences ("tolete").
 */
object EqualizerManager {
    private const val TAG = "EqualizerManager"
    private const val PREFS = "tolete"
    private const val KEY_ENABLED = "eq_enabled"
    private const val KEY_BANDS = "eq_bands"
    private const val KEY_PRESET = "eq_preset"

    // Frequências típicas de um EQ de 10 bandas (Hz)
    val BAND_FREQS = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    data class BandState(
        val level: Short = 0,          // milibels relativos ao center
        val minLevel: Short = -1500,
        val maxLevel: Short = 1500
    )

    private var equalizer: Equalizer? = null
    private var currentSessionId: Int = 0

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _bands = MutableStateFlow(List(10) { BandState() })
    val bands: StateFlow<List<BandState>> = _bands.asStateFlow()

    private val _presetName = MutableStateFlow("Padrão")
    val presetName: StateFlow<String> = _presetName.asStateFlow()

    // Presets embutidos (níveis em dB * 100, centrados em 0)
    val BUILTIN_PRESETS = mapOf(
        "Padrão" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Grave" to floatArrayOf(6f, 5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Agudo" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 1f, 3f, 5f, 6f, 7f),
        "Voz" to floatArrayOf(-2f, -1f, 0f, 2f, 4f, 4f, 2f, 0f, -1f, -2f),
        "Dance" to floatArrayOf(5f, 4f, 2f, 0f, -1f, -1f, 0f, 2f, 3f, 4f),
        "Clássica" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, -1f, -2f, -2f, -1f),
        "Flat" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Grave Extremo" to floatArrayOf(9f, 7f, 4f, 1f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Clareza" to floatArrayOf(-3f, -2f, 0f, 1f, 3f, 4f, 3f, 2f, 1f, 0f)
    )

    fun attach(sessionId: Int, context: Context) {
        if (sessionId == 0 || sessionId == currentSessionId) return
        release()
        try {
            val eq = Equalizer(0, sessionId)
            equalizer = eq
            currentSessionId = sessionId
            val numBands = eq.numberOfBands.toInt().coerceAtMost(10)
            val range = eq.bandLevelRange
            val min = range[0]
            val max = range[1]

            // Restaura ou inicializa bandas
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val saved = prefs.getString(KEY_BANDS, null)
            val levels = if (saved != null) {
                saved.split(",").mapNotNull { it.toFloatOrNull() }
            } else null

            val states = MutableList(10) { BandState(minLevel = min, maxLevel = max) }
            for (i in 0 until numBands) {
                val level = if (levels != null && i < levels.size) {
                    (levels[i] * 100).toInt().toShort().coerceIn(min, max)
                } else 0
                try {
                    eq.setBandLevel(i.toShort(), level)
                } catch (_: Exception) {}
                states[i] = BandState(level = level, minLevel = min, maxLevel = max)
            }
            _bands.value = states

            val en = prefs.getBoolean(KEY_ENABLED, false)
            eq.enabled = en
            _enabled.value = en
            _presetName.value = prefs.getString(KEY_PRESET, "Padrão") ?: "Padrão"

            Log.i(TAG, "Equalizer anexado à sessão $sessionId ($numBands bandas)")
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao criar Equalizer: ${e.message}")
            equalizer = null
            currentSessionId = 0
        }
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (_: Exception) {}
        equalizer = null
        currentSessionId = 0
    }

    fun setEnabled(enabled: Boolean, context: Context) {
        equalizer?.enabled = enabled
        _enabled.value = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun setBandLevel(band: Int, levelDb: Float, context: Context) {
        val eq = equalizer ?: return
        if (band !in 0 until eq.numberOfBands) return
        val range = eq.bandLevelRange
        val level = (levelDb * 100).toInt().toShort().coerceIn(range[0], range[1])
        try {
            eq.setBandLevel(band.toShort(), level)
            val list = _bands.value.toMutableList()
            if (band < list.size) {
                list[band] = list[band].copy(level = level)
                _bands.value = list
            }
            persistBands(context)
            _presetName.value = "Personalizado"
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_PRESET, "Personalizado").apply()
        } catch (e: Exception) {
            Log.w(TAG, "setBandLevel: ${e.message}")
        }
    }

    fun applyPreset(name: String, context: Context) {
        val values = BUILTIN_PRESETS[name] ?: return
        val eq = equalizer
        val range = eq?.bandLevelRange ?: shortArrayOf(-1500, 1500)
        val states = _bands.value.toMutableList()
        for (i in values.indices) {
            val level = (values[i] * 100).toInt().toShort().coerceIn(range[0], range[1])
            try {
                eq?.setBandLevel(i.toShort(), level)
            } catch (_: Exception) {}
            if (i < states.size) {
                states[i] = states[i].copy(level = level, minLevel = range[0], maxLevel = range[1])
            }
        }
        _bands.value = states
        _presetName.value = name
        persistBands(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PRESET, name).apply()
        if (!_enabled.value) setEnabled(true, context)
    }

    /** Ajusta grave (bandas 0-2) e agudo (bandas 7-9) de forma relativa. */
    fun setBassTreble(bassDb: Float, trebleDb: Float, context: Context) {
        val eq = equalizer ?: return
        val range = eq.bandLevelRange
        val current = _bands.value
        fun set(i: Int, extra: Float) {
            val base = current.getOrNull(i)?.level?.toFloat()?.div(100f) ?: 0f
            val level = ((base + extra) * 100).toInt().toShort().coerceIn(range[0], range[1])
            try { eq.setBandLevel(i.toShort(), level) } catch (_: Exception) {}
        }
        // Simplificado: aplica diretamente nos extremos
        val b = (bassDb * 100).toInt().toShort().coerceIn(range[0], range[1])
        val t = (trebleDb * 100).toInt().toShort().coerceIn(range[0], range[1])
        for (i in 0..2) try { eq.setBandLevel(i.toShort(), b) } catch (_: Exception) {}
        for (i in 7..9) try { eq.setBandLevel(i.toShort(), t) } catch (_: Exception) {}
        val states = current.toMutableList()
        for (i in 0..2) if (i < states.size) states[i] = states[i].copy(level = b)
        for (i in 7..9) if (i < states.size) states[i] = states[i].copy(level = t)
        _bands.value = states
        persistBands(context)
        _presetName.value = "Personalizado"
    }

    private fun persistBands(context: Context) {
        val str = _bands.value.joinToString(",") { (it.level / 100f).toString() }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_BANDS, str).apply()
    }

    fun bandLevelDb(band: Int): Float {
        val s = _bands.value.getOrNull(band) ?: return 0f
        return s.level / 100f
    }
}
