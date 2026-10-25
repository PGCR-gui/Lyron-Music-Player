package com.tolete.player.ui

import android.content.ClipData
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
import android.widget.Toast
import androidx.core.content.FileProvider
import com.tolete.player.data.Track
import java.io.File
import java.io.FileOutputStream

/**
 * Gera e compartilha um card visual com trecho de letra.
 * Usa cache/updates/ (já liberado no FileProvider) e authority ".updates".
 */
object LyricsShareHelper {

    private const val MAX_LINES = 5
    private const val TAG = "LyricsShareHelper"

    fun shareSnippet(context: Context, track: Track, lines: List<String>) {
        val appCtx = context.applicationContext
        val clipped = lines.map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_LINES)
        if (clipped.isEmpty()) {
            Toast.makeText(appCtx, "Nenhuma linha para compartilhar", Toast.LENGTH_SHORT).show()
            return
        }

        // Render + I/O em thread de fundo; UI callback na main
        Thread {
            try {
                val bmp = renderCard(appCtx, track, clipped)
                if (bmp == null) {
                    postToast(appCtx, "Falha ao gerar a imagem")
                    shareTextOnly(appCtx, track, clipped)
                    return@Thread
                }

                // Pasta já mapeada no FileProvider (cache/updates/)
                val dir = File(appCtx.cacheDir, "updates").apply { mkdirs() }
                val outFile = File(dir, "lyric_${System.currentTimeMillis()}.png")
                FileOutputStream(outFile).use { fos ->
                    if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)) {
                        throw IllegalStateException("compress() retornou false")
                    }
                }
                if (!bmp.isRecycled) bmp.recycle()

                if (!outFile.exists() || outFile.length() < 100L) {
                    postToast(appCtx, "Arquivo de imagem inválido")
                    shareTextOnly(appCtx, track, clipped)
                    return@Thread
                }

                val authority = appCtx.packageName + ".updates"
                val uri = try {
                    FileProvider.getUriForFile(appCtx, authority, outFile)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "FileProvider falhou ($authority): ${e.message}", e)
                    null
                }

                if (uri == null) {
                    postToast(appCtx, "FileProvider falhou — enviando só texto")
                    shareTextOnly(appCtx, track, clipped)
                    return@Thread
                }

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "${track.title} — ${track.artist}\n\n${clipped.joinToString("\n")}"
                    )
                    clipData = ClipData.newRawUri("lyrics", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(intent, "Compartilhar foto do trecho").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appCtx.startActivity(chooser)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Falha ao gerar/compartilhar foto", e)
                postToast(appCtx, "Erro: ${e.javaClass.simpleName}")
                shareTextOnly(appCtx, track, clipped)
            }
        }.start()
    }

    private fun postToast(context: Context, msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareTextOnly(context: Context, track: Track, lines: List<String>) {
        try {
            val textIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    "${track.title} — ${track.artist}\n\n${lines.joinToString("\n")}"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                Intent.createChooser(textIntent, "Compartilhar trecho")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Falha no fallback de texto", e)
        }
    }

    private fun renderCard(context: Context, track: Track, lines: List<String>): Bitmap? {
        return try {
            val w = 1080
            val h = 1440
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            // Fundo: capa escalada (blur barato) ou sólido
            val coverSrc = loadCoverBitmap(track)
            if (coverSrc != null) {
                val blurred = softBlur(coverSrc, w, h)
                canvas.drawBitmap(blurred, 0f, 0f, null)
                if (blurred !== coverSrc && !blurred.isRecycled) blurred.recycle()
            } else {
                canvas.drawColor(0xFF12121A.toInt())
            }

            // Vinheta
            val veil = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, h.toFloat(),
                    intArrayOf(0x66000000.toInt(), 0x88000000.toInt(), 0xCC000000.toInt()),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), veil)

            // Card (vidro simplificado, sem setShadowLayer — evita falhas em alguns devices)
            val cardLeft = 72f
            val cardTop = 160f
            val cardRight = w - 72f
            val cardBottom = h - 280f
            val cardRadius = 48f
            val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)

            val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x66FFFFFF.toInt()
            }
            canvas.drawRoundRect(cardRect, cardRadius, cardRadius, glass)

            val gloss = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    cardLeft, cardTop, cardLeft, cardTop + 220f,
                    intArrayOf(0x55FFFFFF.toInt(), 0x10FFFFFF.toInt(), 0x00FFFFFF.toInt()),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(cardRect, cardRadius, cardRadius, gloss)

            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x88FFFFFF.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
            }
            canvas.drawRoundRect(cardRect, cardRadius, cardRadius, border)

            val pad = 48f
            val contentLeft = cardLeft + pad
            val contentTop = cardTop + pad
            val contentWidth = cardRight - cardLeft - pad * 2

            // Capa
            val coverSize = 220f
            val coverRect = RectF(contentLeft, contentTop, contentLeft + coverSize, contentTop + coverSize)
            val clipPath = Path().apply {
                addRoundRect(coverRect, 28f, 28f, Path.Direction.CW)
            }
            val save = canvas.save()
            canvas.clipPath(clipPath)
            if (coverSrc != null) {
                val scaled = Bitmap.createScaledBitmap(
                    coverSrc, coverSize.toInt(), coverSize.toInt(), true
                )
                canvas.drawBitmap(scaled, null, coverRect, Paint(Paint.ANTI_ALIAS_FLAG))
                if (scaled !== coverSrc) scaled.recycle()
            } else {
                val ph = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A2A3A.toInt() }
                canvas.drawRoundRect(coverRect, 28f, 28f, ph)
            }
            canvas.restoreToCount(save)

            // Título / artista
            val textLeft = contentLeft + coverSize + 32f
            val maxTextW = cardRight - pad - textLeft
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                textSize = 48f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xCCE8E8F0.toInt()
                textSize = 34f
            }
            canvas.drawText(
                ellipsize(track.title.ifBlank { "Sem título" }, titlePaint, maxTextW),
                textLeft, contentTop + 72f, titlePaint
            )
            canvas.drawText(
                ellipsize(track.artist.ifBlank { "Desconhecido" }, artistPaint, maxTextW),
                textLeft, contentTop + 124f, artistPaint
            )

            // Divisor
            val divY = contentTop + coverSize + 36f
            val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x44FFFFFF.toInt()
                strokeWidth = 2f
            }
            canvas.drawLine(contentLeft, divY, cardRight - pad, divY, divPaint)

            // Letras
            val lyricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFF5F5FA.toInt()
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
                y += 8f
            }

            if (coverSrc != null && !coverSrc.isRecycled) coverSrc.recycle()

            // Marca
            val logoSize = 64
            val logoBmp = loadAppLogo(context, logoSize)
            val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                textSize = 34f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
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

            bmp
        } catch (e: Exception) {
            android.util.Log.e(TAG, "renderCard falhou", e)
            null
        }
    }

    private fun loadCoverBitmap(track: Track): Bitmap? {
        val path = track.artPath ?: return null
        return try {
            val file = File(path)
            if (!file.isFile) return null
            val opts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, opts)
            var sample = 1
            val maxSide = 1024
            while (opts.outWidth / sample > maxSide || opts.outHeight / sample > maxSide) {
                sample *= 2
            }
            val decode = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(path, decode)
        } catch (_: Exception) {
            null
        }
    }

    private fun loadAppLogo(context: Context, size: Int): Bitmap? {
        return try {
            val res = context.resources
            val pkg = context.packageName
            val resId = listOf("ic_launcher_foreground", "ic_launcher_round", "ic_launcher")
                .firstNotNullOfOrNull { name ->
                    val id = res.getIdentifier(name, "mipmap", pkg)
                    if (id != 0) id else {
                        val d = res.getIdentifier(name, "drawable", pkg)
                        if (d != 0) d else null
                    }
                } ?: return null
            val src = BitmapFactory.decodeResource(res, resId) ?: return null
            val scaled = Bitmap.createScaledBitmap(src, size, size, true)
            if (scaled !== src) src.recycle()
            scaled
        } catch (_: Exception) {
            null
        }
    }

    private fun softBlur(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val smallW = (targetW / 16).coerceAtLeast(32)
        val smallH = (targetH / 16).coerceAtLeast(32)
        val small = Bitmap.createScaledBitmap(src, smallW, smallH, true)
        val out = Bitmap.createScaledBitmap(small, targetW, targetH, true)
        if (small !== src && !small.isRecycled) small.recycle()
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
