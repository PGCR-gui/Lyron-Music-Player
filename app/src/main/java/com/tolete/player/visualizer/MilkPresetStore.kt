package com.tolete.player.visualizer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Guarda os presets do usuário em  <filesDir>/milk/
 *
 * Os .milk e as texturas que eles usam (jpg/png/tga/...) ficam na MESMA pasta,
 * que também é passada ao projectM como pasta de texturas.
 * Aceita arquivos soltos e .zip (pacotes de presets): o zip é extraído "achatado".
 */
object MilkPresetStore {

    private val TEXTURE_EXT = setOf("jpg", "jpeg", "png", "tga", "bmp", "dds")

    fun dir(context: Context): File =
        File(context.filesDir, "milk").apply { mkdirs() }

    fun list(context: Context): List<String> =
        dir(context).listFiles { f -> f.isFile && f.extension.equals("milk", true) }
            ?.map { it.absolutePath }
            ?.sortedBy { it.lowercase() }
            ?: emptyList()

    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }

    /** Retorna quantos presets (.milk) foram adicionados. Rodar fora da thread principal. */
    fun import(context: Context, uris: List<Uri>): Int {
        val out = dir(context)
        var presets = 0
        for (uri in uris) {
            val name = displayName(context, uri) ?: continue
            val ext = name.substringAfterLast('.', "").lowercase()
            try {
                when {
                    ext == "zip" -> context.contentResolver.openInputStream(uri)?.use {
                        presets += extractZip(it, out)
                    }
                    ext == "milk" || ext in TEXTURE_EXT -> {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(File(out, safeName(name))).use { input.copyTo(it) }
                        }
                        if (ext == "milk") presets++
                    }
                }
            } catch (_: Exception) { /* ignora o arquivo problemático e segue */ }
        }
        return presets
    }

    private fun extractZip(input: java.io.InputStream, out: File): Int {
        var presets = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                val name = safeName(e.name)           // achata e bloqueia "../" (zip-slip)
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext != "milk" && ext !in TEXTURE_EXT) continue
                FileOutputStream(File(out, name)).use { zip.copyTo(it) }
                if (ext == "milk") presets++
            }
        }
        return presets
    }

    private fun safeName(raw: String): String =
        File(raw.replace('\\', '/')).name.ifBlank { "arquivo" }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?: uri.lastPathSegment?.substringAfterLast('/')
}
