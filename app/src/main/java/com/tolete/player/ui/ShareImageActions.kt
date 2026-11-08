package com.tolete.player.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
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
        shareText: String = "",
        mime: String = "image/png",
        dialogTitle: String = "Foto gerada"
    ) {
        val act = findActivity(context)
        val appCtx = context.applicationContext
        val run = Runnable {
            val ctx: Context = act ?: context
            val builder = AlertDialog.Builder(ctx)
            // Mesmo visual dos outros menus: cor do tema com transparência e texto adaptável.
            val pal = menuPalette(LiveThemeHolder.colors ?: DefaultAppColors)
            val density = ctx.resources.displayMetrics.density
            val options = arrayOf("Salvar", "Compartilhar")
            val adapter = object : ArrayAdapter<String>(
                ctx, android.R.layout.simple_list_item_1, options
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val v = super.getView(position, convertView, parent)
                    (v as? TextView)?.setTextColor(pal.content.toArgb())
                    return v
                }
            }
            val titleView = TextView(ctx).apply {
                text = dialogTitle
                textSize = 22f
                setTextColor(pal.content.toArgb())
                setPadding((24 * density).toInt(), (22 * density).toInt(), (24 * density).toInt(), (8 * density).toInt())
            }
            val dialog = builder
                .setCustomTitle(titleView)
                .setAdapter(adapter) { _, which ->
                    when (which) {
                        0 -> saveToLyron(appCtx, cacheFile, subdir)
                        1 -> shareFile(appCtx, cacheFile, shareTitle, shareText, mime)
                    }
                }
                .setNegativeButton("Cancelar", null)
                .create()
            dialog.window?.setBackgroundDrawable(
                GradientDrawable().apply {
                    setColor(pal.container.toArgb())
                    cornerRadius = 28 * density
                }
            )
            dialog.show()
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(pal.accent.toArgb())
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

    fun shareFile(context: Context, file: File, title: String, text: String, mime: String = "image/png") {
        try {
            val authority = context.packageName + ".updates"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
                clipData = ClipData.newRawUri(if (mime.startsWith("video")) "video" else "image", uri)
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
