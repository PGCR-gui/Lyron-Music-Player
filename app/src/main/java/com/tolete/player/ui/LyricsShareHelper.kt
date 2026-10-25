package com.tolete.player.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.tolete.player.R
import com.tolete.player.data.Track
import java.io.File
import java.io.FileOutputStream

/**
 * Gera e compartilha um card visual com trecho de letra:
 * fundo = capa borrada, card liquid-glass com capa + título/artista + até 5 linhas,
 * marca Lyron + logo abaixo.
 */
object LyricsShareHelper {

    private const val MAX_LINES = 5

    fun shareSnippet(context: Context, track: Track, lines: List<String>) {
        val clipped = lines.map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_LINES)
        if (clipped.isEmpty()) return
        try {
            val bmp = renderCard(context, track, clipped) ?: return
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val outFile = File(dir, "lyric_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!bmp.isRecycled) bmp.recycle()

            val uri = try {
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
                    "${track.title} — ${track.artist}\n\n${clipped.joinToString("\n")}"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Compartilhar trecho"))
        } catch (e: Exception) {
            android.util.Log.e("LyricsShareHelper", "Falha ao compartilhar trecho", e)
            try {
                val textIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "${track.title} — ${track.artist}\n\n${clipped.joinToString("\n")}"
                    )
                    if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(textIntent, "Compartilhar trecho"))
            } catch (_: Exception) {}
        }
    }

    private fun renderCard(context: Context, track: Track, lines: List<String>): Bitmap? {
        val w = 1080
        val h = 1440
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // ---- Fundo: capa borrada ----
        val coverSrc = loadCoverBitmap(track)
        if (coverSrc != null) {
            val blurred = softBlur(coverSrc, w, h)
            canvas.drawBitmap(blurred, 0f, 0f, null)
            if (blurred !== coverSrc && !blurred.isRecycled) blurred.recycle()
        } else {
            canvas.drawColor(0xFF12121A.toInt())
        }
        // vinheta escura
        val veil = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(0x66000000.toInt(), 0x88000000.toInt(), 0xCC000000.toInt()),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), veil)

        // ---- Card liquid glass ----
        val cardLeft = 72f
        val cardTop = 160f
        val cardRight = w - 72f
        val cardBottom = h - 280f
        val cardRadius = 48f
        val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)

        // sombra
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66000000
            setShadowLayer(28f, 0f, 16f, 0x88000000.toInt())
        }
        // precisa layer para shadow
        val sc = canvas.saveLayer(0f, 0f, w.toFloat(), h.toFloat(), null)
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, shadow)

        // vidro
        val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x55FFFFFF
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, glass)

        // brilho superior
        val gloss = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cardLeft, cardTop, cardLeft, cardTop + 220f,
                intArrayOf(0x55FFFFFF, 0x10FFFFFF, 0x00FFFFFF),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, gloss)

        // borda
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, border)
        canvas.restoreToCount(sc)

        // ---- Conteúdo do card ----
        val pad = 48f
        val contentLeft = cardLeft + pad
        val contentTop = cardTop + pad
        val contentWidth = cardRight - cardLeft - pad * 2

        // capa arredondada
        val coverSize = 220f
        val coverRect = RectF(contentLeft, contentTop, contentLeft + coverSize, contentTop + coverSize)
        val clipPath = Path().apply {
            addRoundRect(coverRect, 28f, 28f, Path.Direction.CW)
        }
        val save = canvas.save()
        canvas.clipPath(clipPath)
        if (coverSrc != null) {
            val scaled = Bitmap.createScaledBitmap(coverSrc, coverSize.toInt(), coverSize.toInt(), true)
            canvas.drawBitmap(scaled, null, coverRect, Paint(Paint.ANTI_ALIAS_FLAG))
            if (scaled !== coverSrc) scaled.recycle()
        } else {
            val ph = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A2A3A.toInt().toInt() }
            canvas.drawRoundRect(coverRect, 28f, 28f, ph)
        }
        canvas.restoreToCount(save)

        // título / artista ao lado da capa
        val textLeft = contentLeft + coverSize + 32f
        val maxTextW = cardRight - pad - textLeft
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt().toInt()
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCE8E8F0.toInt().toInt()
            textSize = 34f
        }
        canvas.drawText(ellipsize(track.title, titlePaint, maxTextW), textLeft, contentTop + 72f, titlePaint)
        canvas.drawText(ellipsize(track.artist, artistPaint, maxTextW), textLeft, contentTop + 124f, artistPaint)

        // divisor
        val divY = contentTop + coverSize + 36f
        val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x44FFFFFF
            strokeWidth = 2f
        }
        canvas.drawLine(contentLeft, divY, cardRight - pad, divY, divPaint)

        // letras (até 5 linhas, com wrap)
        val lyricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF5F5FA.toInt().toInt()
            textSize = 40f
        }
        val lineHeight = 56f
        var y = divY + 56f
        val maxLyricBottom = cardBottom - pad
        outer@ for (line in lines) {
            val wrapped = wrapText(line, lyricPaint, contentWidth)
            for (wline in wrapped) {
                if (y + lineHeight > maxLyricBottom) break@outer
                canvas.drawText(wline, contentLeft, y, lyricPaint)
                y += lineHeight
            }
            y += 8f // espaço entre estrofes/linhas originais
        }

        if (coverSrc != null && !coverSrc.isRecycled) coverSrc.recycle()

        // ---- Marca + logo abaixo do card ----
        val logoSize = 64
        val logoBmp = loadAppLogo(context, logoSize)
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt().toInt()
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val brand = "Lyron Music Player"
        val brandWidth = brandPaint.measureText(brand)
        val totalW = (if (logoBmp != null) logoSize + 20f else 0f) + brandWidth
        val baseY = cardBottom + 72f
        val startX = (w - totalW) / 2f
        if (logoBmp != null) {
            val logoRect = RectF(startX, baseY - logoSize + 8f, startX + logoSize, baseY + 8f)
            canvas.drawBitmap(logoBmp, null, logoRect, Paint(Paint.ANTI_ALIAS_FLAG))
            canvas.drawText(brand, startX + logoSize + 20f, baseY, brandPaint)
            logoBmp.recycle()
        } else {
            canvas.drawText(brand, startX, baseY, brandPaint)
        }

        return bmp
    }

    private fun loadCoverBitmap(track: Track): Bitmap? {
        val path = track.artPath ?: return null
        return try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeFile(path, opts)
        } catch (_: Exception) {
            null
        }
    }

    private fun loadAppLogo(context: Context, size: Int): Bitmap? {
        return try {
            val resId = context.resources.getIdentifier("ic_launcher_foreground", "mipmap", context.packageName)
                .takeIf { it != 0 }
                ?: context.resources.getIdentifier("ic_launcher", "mipmap", context.packageName)
                    .takeIf { it != 0 }
                ?: R.mipmap.ic_launcher
            val src = BitmapFactory.decodeResource(context.resources, resId) ?: return null
            Bitmap.createScaledBitmap(src, size, size, true).also {
                if (it !== src) src.recycle()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Blur barato: downscale forte + upscale (efeito fosco). */
    private fun softBlur(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val smallW = (targetW / 16).coerceAtLeast(32)
        val smallH = (targetH / 16).coerceAtLeast(32)
        val small = Bitmap.createScaledBitmap(src, smallW, smallH, true)
        val out = Bitmap.createScaledBitmap(small, targetW, targetH, true)
        if (small !== src && !small.isRecycled) small.recycle()
        // escurece um pouco
        val canvas = Canvas(out)
        canvas.drawColor(0x33000000.toInt())
        return out
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
