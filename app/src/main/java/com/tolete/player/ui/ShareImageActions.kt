package com.tolete.player.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object ShareImageActions {

    fun findActivity(context: Context): Activity? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    /**
     * Após gerar a imagem em [cacheFile], oferece Salvar ou Compartilhar.
     * [subdir] relativo a /storage/emulated/0/Lyron/ (ex.: "Lyrics Cards", "Retrospectives")
     */
    fun offerSaveOrShare(
        context: Context,
        cacheFile: File,
        subdir: String,
        shareTitle: String,
        shareText: String = ""
    ) {
        val act = findActivity(context)
        val appCtx = context.applicationContext
        val run = Runnable {
            val builder = if (act != null) AlertDialog.Builder(act) else AlertDialog.Builder(context)
            builder
                .setTitle("Foto gerada")
                .setItems(arrayOf("Salvar", "Compartilhar")) { _, which ->
                    when (which) {
                        0 -> saveToLyron(appCtx, cacheFile, subdir)
                        1 -> shareFile(appCtx, cacheFile, shareTitle, shareText)
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
        if (act != null) act.runOnUiThread(run)
        else android.os.Handler(android.os.Looper.getMainLooper()).post(run)
    }

    fun saveToLyron(context: Context, cacheFile: File, subdir: String) {
        try {
            val root = File(Environment.getExternalStorageDirectory(), "Lyron/$subdir")
            root.mkdirs()
            val dest = File(root, cacheFile.name)
            FileInputStream(cacheFile).use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            Toast.makeText(context, "Salvo em Lyron/$subdir", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao salvar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun shareFile(context: Context, file: File, title: String, text: String) {
        try {
            val authority = context.packageName + ".updates"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
                clipData = ClipData.newRawUri("image", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao compartilhar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
