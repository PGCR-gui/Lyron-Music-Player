package com.tolete.player.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.tolete.player.R
import java.io.File

/**
 * Fontes embutidas (res/font) + fontes do usuário (filesDir/fonts/).
 *
 * Identificadores:
 * - null / "" / "system" → fonte do sistema
 * - "builtin:<id>" → fonte embutida
 * - caminho absoluto → arquivo .ttf/.otf do usuário
 */
object BuiltInFonts {

    data class EmbeddedFont(
        val id: String,
        val label: String,
        val resId: Int
    )

    val EMBEDDED: List<EmbeddedFont> = listOf(
        EmbeddedFont("shareit", "Shareit", R.font.shareit),
        EmbeddedFont("juliametal", "JuliaMetal", R.font.juliametal_regular),
        EmbeddedFont("buba", "Buba Outline", R.font.buba_outline),
        EmbeddedFont("grim_creeper", "Grim Creeper", R.font.grim_creeper),
        EmbeddedFont("pixelise", "Pixelise", R.font.pixelise),
        EmbeddedFont("ryga", "Ryga", R.font.ryga),
        EmbeddedFont("terminal_16", "Terminal 16 Unicode", R.font.terminal_16_unicode),
        EmbeddedFont("better_vcr", "Better VCR", R.font.better_vcr),
        EmbeddedFont("matcha_style", "Matcha Style", R.font.matcha_style),
        EmbeddedFont("gak_tahu", "Gak Tahu", R.font.gak_tahu),
        EmbeddedFont("unifontexmono", "Unifont Ex Mono", R.font.unifontexmono),
    )

    fun builtinKey(id: String) = "builtin:$id"

    fun findEmbedded(key: String?): EmbeddedFont? {
        if (key.isNullOrBlank() || !key.startsWith("builtin:")) return null
        val id = key.removePrefix("builtin:")
        return EMBEDDED.firstOrNull { it.id == id }
    }

    fun displayLabel(key: String?): String {
        if (key.isNullOrBlank() || key == "system") return "Padrão do sistema"
        findEmbedded(key)?.let { return it.label }
        return try {
            File(key).name
        } catch (_: Exception) {
            key
        }
    }

    fun resolveFamily(key: String?): FontFamily? {
        if (key.isNullOrBlank() || key == "system") return null
        findEmbedded(key)?.let { emb ->
            return try {
                FontFamily(Font(emb.resId))
            } catch (_: Exception) {
                null
            }
        }
        return try {
            val f = File(key)
            if (f.exists() && f.isFile) FontFamily(Font(f)) else null
        } catch (_: Exception) {
            null
        }
    }

    fun fontsDir(context: Context): File =
        File(context.filesDir, "fonts").also { it.mkdirs() }

    fun listUserFonts(context: Context): List<File> {
        val dir = fontsDir(context)
        return dir.listFiles()
            ?.filter { f ->
                f.isFile && (
                    f.name.endsWith(".ttf", ignoreCase = true) ||
                    f.name.endsWith(".otf", ignoreCase = true)
                )
            }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    /** Fontes do splash: embutidas + personalizadas. */
    fun splashFontFamilies(context: Context): List<FontFamily> {
        val out = ArrayList<FontFamily>()
        for (emb in EMBEDDED) {
            try {
                out.add(FontFamily(Font(emb.resId)))
            } catch (_: Exception) { }
        }
        for (f in listUserFonts(context)) {
            try {
                out.add(FontFamily(Font(f)))
            } catch (_: Exception) { }
        }
        return out
    }

    @Deprecated("Use splashFontFamilies", ReplaceWith("splashFontFamilies(context)"))
    fun splashPool(context: Context): List<File> = listUserFonts(context)
}

@Composable
fun rememberFontFamily(pathOrKey: String?): FontFamily? {
    return remember(pathOrKey) { BuiltInFonts.resolveFamily(pathOrKey) }
}
