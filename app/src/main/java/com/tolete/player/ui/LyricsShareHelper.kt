package com.tolete.player.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.tolete.player.data.Track
import java.io.File
import java.io.FileOutputStream

/**
 * Gera uma imagem com capa, artista, título e trecho de letra selecionado,
 * e abre o share sheet do sistema.
 */
object LyricsShareHelper {

    fun shareSnippet(
        context: Context,
        track: Track,
        lines: List<String>
    ) {
        if (lines.isEmpty()) return
        try {
            val bmp = renderCard(context, track, lines) ?: return
            val cache = File(context.cacheDir, "share").apply { mkdirs() }
            val outFile = File(cache, "lyron_lyric_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { fos ->
                bmp.compress(Bitmap.CompressFormat.PNG, 95, fos)
            }
            bmp.recycle()

            val uri: Uri = try {
                FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    outFile
                )
            } catch (_: Exception) {
                Uri.fromFile(outFile)
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "${track.title} — ${track.artist}\n\n${lines.joinToString("\n")}"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // Usa FLAG_ACTIVITY_NEW_TASK se o contexto não for Activity (ex.: fullscreen overlay)
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Compartilhar trecho"))
        } catch (e: Exception) {
            // Evita crash na tela de letras em tela cheia
            android.util.Log.e("LyricsShareHelper", "Falha ao compartilhar trecho", e)
            try {
                val textIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "${track.title} — ${track.artist}\n\n${lines.joinToString("\n")}"
                    )
                    if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(textIntent, "Compartilhar trecho"))
            } catch (_: Exception) {}
        }
    }

    private fun renderCard(context: Context, track: Track, lines: List<String>): Bitmap? {
        val w = 1080
        val pad = 64f
        val coverSize = 280f
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 52f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFB0B0C0.toInt()
            textSize = 36f
        }
        val lyricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE8E8F0.toInt()
            textSize = 40f
        }
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF69F0AE.toInt()
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val lineHeight = 56f
        val lyricsBlockH = lines.size * lineHeight + 40f
        val h = (pad * 3 + coverSize + 120f + lyricsBlockH + 80f).toInt().coerceAtLeast(800)

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFF0B0B14.toInt())

        // accent bar
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF69F0AE.toInt() }
        canvas.drawRect(0f, 0f, 12f, h.toFloat(), accent)

        // cover
        val coverLeft = pad
        val coverTop = pad
        var coverBmp: Bitmap? = null
        track.artPath?.let { path ->
            try {
                coverBmp = BitmapFactory.decodeFile(path)
            } catch (_: Exception) {}
        }
        val coverRect = RectF(coverLeft, coverTop, coverLeft + coverSize, coverTop + coverSize)
        val round = Paint(Paint.ANTI_ALIAS_FLAG)
        if (coverBmp != null) {
            val src = coverBmp!!
            val scaled = Bitmap.createScaledBitmap(src, coverSize.toInt(), coverSize.toInt(), true)
            canvas.drawBitmap(scaled, null, coverRect, round)
            if (scaled !== src) scaled.recycle()
            src.recycle()
        } else {
            round.color = 0xFF2A2A3A.toInt()
            canvas.drawRoundRect(coverRect, 24f, 24f, round)
        }

        // title / artist next to cover
        val textLeft = coverLeft + coverSize + 36f
        val maxTextW = w - textLeft - pad
        canvas.drawText(ellipsize(track.title, titlePaint, maxTextW), textLeft, coverTop + 80f, titlePaint)
        canvas.drawText(ellipsize(track.artist, artistPaint, maxTextW), textLeft, coverTop + 130f, artistPaint)
        canvas.drawText("Lyron Music Player", textLeft, coverTop + coverSize - 12f, brandPaint)

        // divider
        val divY = coverTop + coverSize + pad
        val divPaint = Paint().apply { color = 0x33FFFFFF }
        canvas.drawRect(pad, divY, w - pad, divY + 2f, divPaint)

        // lyrics
        var y = divY + 56f
        for (line in lines) {
            val wrapped = wrapText(line, lyricPaint, w - pad * 2)
            for (wline in wrapped) {
                canvas.drawText(wline, pad, y, lyricPaint)
                y += lineHeight
            }
        }

        return bmp
    }

    private fun ellipsize(text: String, paint: Paint, maxW: Float): String {
        if (paint.measureText(text) <= maxW) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText("$t…") > maxW) {
            t = t.dropLast(1)
        }
        return "$t…"
    }

    private fun wrapText(text: String, paint: Paint, maxW: Float): List<String> {
        if (paint.measureText(text) <= maxW) return listOf(text)
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var cur = ""
        for (w in words) {
            val trial = if (cur.isEmpty()) w else "$cur $w"
            if (paint.measureText(trial) <= maxW) {
                cur = trial
            } else {
                if (cur.isNotEmpty()) lines.add(cur)
                cur = w
            }
        }
        if (cur.isNotEmpty()) lines.add(cur)
        return lines.ifEmpty { listOf(text) }
    }
}
