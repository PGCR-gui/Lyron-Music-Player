package com.tolete.player.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.io.File

/**
 * Fontes do usuário (arquivos .ttf/.otf em filesDir/fonts/).
 * Fontes embutidas foram removidas para evitar problemas de licença.
 *
 * Identificadores:
 * - null / "" / "system" → fonte do sistema
 * - caminho absoluto → arquivo .ttf/.otf do usuário
 */
object BuiltInFonts {

    /** Lista vazia — sem fontes embutidas. */
    val ALL: List<Nothing> = emptyList()

    fun findByKey(key: String?): Nothing? = null

    fun displayLabel(key: String?): String {
        if (key.isNullOrBlank() || key == "system") return "Padrão do sistema"
        return try {
            File(key).name
        } catch (_: Exception) {
            key
        }
    }

    fun resolveFamily(key: String?): FontFamily? {
        if (key.isNullOrBlank() || key == "system") return null
        // Ignora chaves antigas builtin:*
        if (key.startsWith("builtin:")) return null
        return try {
            val f = File(key)
            if (f.exists() && f.isFile) FontFamily(Font(f)) else null
        } catch (_: Exception) {
            null
        }
    }

    /** Diretório onde as fontes personalizadas são salvas. */
    fun fontsDir(context: Context): File =
        File(context.filesDir, "fonts").also { it.mkdirs() }

    /** Lista arquivos de fonte do usuário (.ttf / .otf). */
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

    /** Pool para o splash: fontes do usuário; vazio se nenhuma carregada. */
    fun splashPool(context: Context): List<File> = listUserFonts(context)
}

@Composable
fun rememberFontFamily(pathOrKey: String?): FontFamily? {
    return remember(pathOrKey) { BuiltInFonts.resolveFamily(pathOrKey) }
}
