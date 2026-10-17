package com.tolete.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.tolete.player.R
import java.io.File

/**
 * Fontes embutidas no APK (res/font) + suporte a caminho de arquivo personalizado.
 *
 * Identificadores:
 * - null / "" / "system" → fonte do sistema
 * - "builtin:<name>" → recurso R.font.<name>
 * - caminho absoluto → arquivo .ttf/.otf do usuário
 */
data class BuiltInFont(
    val id: String,
    val displayName: String,
    val resId: Int
) {
    val storageKey: String get() = "builtin:$id"
}

object BuiltInFonts {
    val ALL: List<BuiltInFont> = listOf(
        BuiltInFont("burnout", "BurnOut", R.font.burnout),
        BuiltInFont("asphalt_crack", "Asphalt Crack", R.font.asphalt_crack),
        BuiltInFont("better_outline", "Better Outline", R.font.better_outline),
        BuiltInFont("beer_money", "Beer Money", R.font.beer_money),
        BuiltInFont("braaap_s2", "Braaap S2", R.font.braaap_s2),
        BuiltInFont("braaap_s4", "Braaap S4", R.font.braaap_s4),
        BuiltInFont("braaap_s11", "Braaap S11", R.font.braaap_s11),
        BuiltInFont("cannabis", "Cannabis", R.font.cannabis),
        BuiltInFont("cc_pulp_fiction", "CC Pulp Fiction", R.font.cc_pulp_fiction),
        BuiltInFont("early_sunday", "Early Sunday", R.font.early_sunday),
        BuiltInFont("komikab", "Komika (AB)", R.font.komikab),
        BuiltInFont("komikabg", "Komika (ABG)", R.font.komikabg),
        BuiltInFont("komikabs", "Komika (ABS)", R.font.komikabs),
        BuiltInFont("komikagl", "Komika (AGL)", R.font.komikagl),
        BuiltInFont("komikap", "Komika (AP)", R.font.komikap),
        BuiltInFont("komikatr", "Komika (ATR)", R.font.komikatr),
        BuiltInFont("komikax", "Komika (AX)", R.font.komikax),
        BuiltInFont("komikrak", "Komika (RAK)", R.font.komikrak),
        BuiltInFont("komikskt", "Komika (SKT)", R.font.komikskt),
        BuiltInFont("mandarino_party", "Mandarino Party", R.font.mandarino_party),
        BuiltInFont("my_true_love", "My True Love", R.font.my_true_love),
        BuiltInFont("orange_juice", "Orange Juice", R.font.orange_juice),
        BuiltInFont("planetbe", "Planet Be", R.font.planetbe),
        BuiltInFont("red_lamp_pub", "Red Lamp Pub", R.font.red_lamp_pub),
        BuiltInFont("retro_display", "Retro Display", R.font.retro_display),
        BuiltInFont("skylens_italic", "Skylens Italic", R.font.skylens_italic),
        BuiltInFont("speedrace", "Speedrace", R.font.speedrace),
        BuiltInFont("studio_place", "Studio Place", R.font.studio_place),
        BuiltInFont("trap_airlines", "Trap Airlines", R.font.trap_airlines),
    )

    fun findByKey(key: String?): BuiltInFont? {
        if (key.isNullOrBlank() || key == "system") return null
        if (key.startsWith("builtin:")) {
            val id = key.removePrefix("builtin:")
            return ALL.firstOrNull { it.id == id }
        }
        return null
    }

    fun displayLabel(key: String?): String {
        if (key.isNullOrBlank() || key == "system") return "Padrão do sistema"
        findByKey(key)?.let { return it.displayName }
        return try {
            File(key).name
        } catch (_: Exception) {
            key
        }
    }

    fun resolveFamily(key: String?): FontFamily? {
        if (key.isNullOrBlank() || key == "system") return null
        findByKey(key)?.let { return FontFamily(Font(it.resId)) }
        return try {
            val f = File(key)
            if (f.exists()) FontFamily(Font(f)) else null
        } catch (_: Exception) {
            null
        }
    }

    /** Fontes leves para o splash (evita as muito decorativas/grandes). */
    val SPLASH_POOL: List<BuiltInFont> = listOf(
        ALL.first { it.id == "burnout" },
        ALL.first { it.id == "speedrace" },
        ALL.first { it.id == "retro_display" },
        ALL.first { it.id == "studio_place" },
        ALL.first { it.id == "trap_airlines" },
        ALL.first { it.id == "orange_juice" },
        ALL.first { it.id == "early_sunday" },
        ALL.first { it.id == "cc_pulp_fiction" },
        ALL.first { it.id == "mandarino_party" },
        ALL.first { it.id == "beer_money" },
        ALL.first { it.id == "asphalt_crack" },
        ALL.first { it.id == "braaap_s4" },
    )
}

@Composable
fun rememberFontFamily(pathOrKey: String?): FontFamily? {
    return remember(pathOrKey) { BuiltInFonts.resolveFamily(pathOrKey) }
}
