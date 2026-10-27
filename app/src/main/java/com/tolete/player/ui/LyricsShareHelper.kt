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
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.tolete.player.R
import com.tolete.player.data.Track
import java.io.File
import java.io.FileOutputStream

/**
 * Gera e compartilha um card visual com trecho de letra.
 * Usa cache/updates/ (já liberado no FileProvider) e authority ".updates".
 */
object LyricsShareHelper {

    private const val MAX_LINES = 8

    // Foto em 9:16 (1080x1920)
    private const val CARD_W = 1080
    private const val CARD_H = 1920
    private const val TAG = "LyricsShareHelper"

    fun shareSnippet(context: Context, track: Track, lines: List<String>) {
        val appCtx = context.applicationContext
        val clipped = lines.map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_LINES)
        if (clipped.isEmpty()) {
            Toast.makeText(appCtx, "Nenhuma linha para compartilhar", Toast.LENGTH_SHORT).show()
            return
        }

        // Preferências de aparência da foto (fonte + cores) — mesmas chaves do MusicViewModel
        val prefs = appCtx.getSharedPreferences("tolete", Context.MODE_PRIVATE)
        val fontPath = prefs.getString("share_font_path", null)
        val textColor = prefs.getInt("share_text_color", 0xFFF5F5FA.toInt())
        val brandColor = prefs.getInt("share_brand_color", 0xFFFFFFFF.toInt())

        // Render + I/O em thread de fundo; UI callback na main
        Thread {
            try {
                val bmp = renderCard(appCtx, track, clipped, fontPath, textColor, brandColor)
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

    private fun renderCard(
        context: Context,
        track: Track,
        lines: List<String>,
        fontPath: String? = null,
        textColorArgb: Int = 0xFFF5F5FA.toInt(),
        brandColorArgb: Int = 0xFFFFFFFF.toInt()
    ): Bitmap? {
        return try {
            val w = CARD_W
            val h = CARD_H
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

            val customTypeface: Typeface? = try {
                if (!fontPath.isNullOrBlank() && !fontPath.startsWith("builtin:")) {
                    val f = File(fontPath)
                    if (f.exists()) Typeface.createFromFile(f) else null
                } else null
            } catch (_: Exception) { null }

            // Fundo: capa cortada (center-crop, sem esticar) + blur suave, ou sólido
            val coverSrc = loadCoverBitmap(track)
            if (coverSrc != null) {
                val blurred = buildBlurredBackground(coverSrc, w, h)
                canvas.drawBitmap(blurred, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), filterPaint)
                if (!blurred.isRecycled) blurred.recycle()
                canvas.drawColor(0x33000000)
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

            // ---- Medidas gerais (ajustadas para 1080x1920) ----
            val pillH = 120f
            val pillCenterY = h - 200f
            val topMargin = 140f
            val availBottom = pillCenterY - pillH / 2f - 70f
            val maxCardH = availBottom - topMargin

            val cardLeft = 56f
            val cardRight = w - 56f
            val cardRadius = 40f
            val pad = 40f
            val coverSize = 180f
            val contentLeft = cardLeft + pad
            val contentWidth = cardRight - cardLeft - pad * 2

            // ---- Paints de texto (fonte da letra um pouco maior) ----
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColorArgb
                textSize = 44f
                typeface = if (customTypeface != null)
                    Typeface.create(customTypeface, Typeface.BOLD)
                else
                    Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = (textColorArgb and 0x00FFFFFF) or 0xCC000000.toInt()
                textSize = 32f
                if (customTypeface != null) typeface = customTypeface
            }
            val lyricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColorArgb
                textSize = 48f
                if (customTypeface != null) typeface = customTypeface
            }

            // ---- Layout das letras (altura do card acompanha o conteúdo) ----
            val lineHeight = 64f
            val paraGap = 10f
            val headerH = pad + coverSize + 32f + 52f // topo do card -> 1ª linha de letra
            val laidOut = ArrayList<Pair<String, Float>>() // texto, baseline relativa ao topo do card
            var yRel = headerH
            outer@ for (line in lines) {
                for (wline in wrapText(line, lyricPaint, contentWidth)) {
                    if (yRel + lineHeight > maxCardH - pad) break@outer
                    laidOut.add(wline to yRel)
                    yRel += lineHeight
                }
                yRel += paraGap
            }
            val lastBaseline = laidOut.lastOrNull()?.second ?: headerH
            val minCardH = 760f
            val cardH = (lastBaseline + 24f + pad).coerceIn(minCardH, maxCardH)
            val cardTop = topMargin + (maxCardH - cardH) / 2f
            val cardBottom = cardTop + cardH
            val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)

            // ---- Card (vidro simplificado, sem setShadowLayer) ----
            val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF.toInt() }
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

            // ---- Capa (cortada em quadrado, sem esticar) ----
            val contentTop = cardTop + pad
            val coverRect = RectF(contentLeft, contentTop, contentLeft + coverSize, contentTop + coverSize)
            val clipPath = Path().apply { addRoundRect(coverRect, 28f, 28f, Path.Direction.CW) }
            val save = canvas.save()
            canvas.clipPath(clipPath)
            if (coverSrc != null) {
                val side = minOf(coverSrc.width, coverSrc.height)
                val sx = (coverSrc.width - side) / 2
                val sy = (coverSrc.height - side) / 2
                canvas.drawBitmap(
                    coverSrc, Rect(sx, sy, sx + side, sy + side), coverRect, filterPaint
                )
            } else {
                val ph = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A2A3A.toInt() }
                canvas.drawRoundRect(coverRect, 28f, 28f, ph)
            }
            canvas.restoreToCount(save)

            // ---- Título / artista ----
            val textLeft = contentLeft + coverSize + 32f
            val maxTextW = cardRight - pad - textLeft
            canvas.drawText(
                ellipsize(track.title.ifBlank { "Sem título" }, titlePaint, maxTextW),
                textLeft, contentTop + 72f, titlePaint
            )
            canvas.drawText(
                ellipsize(track.artist.ifBlank { "Desconhecido" }, artistPaint, maxTextW),
                textLeft, contentTop + 124f, artistPaint
            )

            // ---- Divisor ----
            val divY = contentTop + coverSize + 36f
            val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x44FFFFFF.toInt()
                strokeWidth = 2f
            }
            canvas.drawLine(contentLeft, divY, cardRight - pad, divY, divPaint)

            // ---- Letras ----
            for ((text, rel) in laidOut) {
                canvas.drawText(text, contentLeft, cardTop + rel, lyricPaint)
            }

            if (coverSrc != null && !coverSrc.isRecycled) coverSrc.recycle()

            // ---- Marca: pílula de vidro (ícone + nome) desenhada junto com a imagem ----
            val drawn = drawBrandPill(canvas, context, w / 2f, pillCenterY, pillH)
            if (!drawn) {
                // Fallback: só texto, caso o ícone não carregue
                val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = brandColorArgb
                    textSize = 40f
                    typeface = if (customTypeface != null)
                        Typeface.create(customTypeface, Typeface.BOLD)
                    else
                        Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val brand = "Lyron Music Player"
                canvas.drawText(brand, (w - brandPaint.measureText(brand)) / 2f, pillCenterY + 14f, brandPaint)
            }

            bmp
        } catch (e: Exception) {
            android.util.Log.e(TAG, "renderCard falhou", e)
            null
        }
    }

    /**
     * Desenha a pílula transparente (vidro) com o ícone da lira + "LYRON".
     * Tudo em Canvas — sem PNG pronto de marca d'água. Retorna false se o ícone não carregar.
     */
    private fun drawBrandPill(
        canvas: Canvas,
        context: Context,
        centerX: Float,
        centerY: Float,
        pillH: Float
    ): Boolean {
        val icon: Bitmap = (try {
            BitmapFactory.decodeResource(context.resources, R.drawable.lyron_logo)
        } catch (_: Exception) { null }) ?: return false

        val wordmark: Typeface = (try {
            ResourcesCompat.getFont(context, R.font.lyron_wordmark)
        } catch (_: Exception) { null }) ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)

        val label = "LYRON"
        val ts = pillH * 0.60f
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFC2D1CB.toInt()
            textSize = ts
            typeface = wordmark
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF000000.toInt()
            textSize = ts
            typeface = wordmark
            style = Paint.Style.STROKE
            strokeWidth = ts * 0.13f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        val textW = fillPaint.measureText(label)
        val tb = Rect()
        fillPaint.getTextBounds(label, 0, label.length, tb)

        val iconH = pillH * 0.70f
        val iconW = iconH * icon.width / icon.height.coerceAtLeast(1)
        val padL = pillH * 0.30f
        val gap = pillH * 0.20f
        val padR = pillH * 0.34f
        val pillW = padL + iconW + gap + textW + strokePaint.strokeWidth + padR

        val rect = RectF(centerX - pillW / 2f, centerY - pillH / 2f, centerX + pillW / 2f, centerY + pillH / 2f)
        val r = pillH / 2f
        val pillPath = Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) }

        // 1) Sombra projetada (camadas empilhadas, sem BlurMaskFilter), só fora da pílula
        val sh = canvas.save()
        canvas.clipOutPath(pillPath)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val layers = 14
        for (i in 0 until layers) {
            val t = i / layers.toFloat()
            val grow = i * 3.4f
            shadowPaint.color = ((6 + 7 * (1f - t)).toInt() shl 24)
            val rr = RectF(rect.left - grow, rect.top - grow, rect.right + grow, rect.bottom + grow)
            rr.offset(0f, pillH * 0.14f)
            canvas.drawRoundRect(rr, r + grow, r + grow, shadowPaint)
        }
        canvas.restoreToCount(sh)

        // 2) Corpo de vidro translúcido
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, rect.top, 0f, rect.bottom,
                intArrayOf(0x66E6ECEE, 0x40D5DDE0, 0x5ABAC5C9),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, r, r, body)

        val inner = canvas.save()
        canvas.clipPath(pillPath)

        // 3) Sombreado interno nas bordas (dá volume de "gota")
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = pillH * 0.16f
            shader = LinearGradient(
                0f, rect.top, 0f, rect.bottom,
                intArrayOf(0x40283234, 0x12283234, 0x55202C30),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, r, r, edge)

        // 4) Faixa de brilho superior
        val hl = RectF(
            rect.left + pillH * 0.55f, rect.top + pillH * 0.10f,
            rect.right - pillH * 0.45f, rect.top + pillH * 0.25f
        )
        val hlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                hl.left, 0f, hl.right, 0f,
                intArrayOf(0x78FFFFFF, 0x40FFFFFF, 0x28FFFFFF),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(hl, hl.height() / 2f, hl.height() / 2f, hlPaint)

        // 5) Luz suave na base
        val bl = RectF(
            rect.left + pillH * 0.55f, rect.bottom - pillH * 0.22f,
            rect.right - pillH * 0.55f, rect.bottom - pillH * 0.07f
        )
        val blPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, bl.top, 0f, bl.bottom,
                intArrayOf(0x00FFFFFF, 0x38FFFFFF),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(bl, bl.height() / 2f, bl.height() / 2f, blPaint)

        // 6) Reflexo especular no canto inferior direito
        val sx = rect.right - pillH * 0.62f
        val sy = rect.bottom - pillH * 0.30f
        val spec = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                sx, sy, pillH * 0.30f,
                intArrayOf(0xB0FFFFFF.toInt(), 0x00FFFFFF),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(sx, sy, pillH * 0.30f, spec)
        canvas.restoreToCount(inner)

        // 7) Aro fino de luz
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            shader = LinearGradient(
                0f, rect.top, 0f, rect.bottom,
                intArrayOf(0xCCFFFFFF.toInt(), 0x33FFFFFF, 0x80FFFFFF.toInt()),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, r, r, rim)

        // 8) Ícone (lira)
        val iconLeft = rect.left + padL
        val iconRect = RectF(iconLeft, centerY - iconH / 2f, iconLeft + iconW, centerY + iconH / 2f)
        canvas.drawBitmap(
            icon, null, iconRect,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        icon.recycle()

        // 9) Nome: preenchimento claro + contorno preto + sombra sólida deslocada
        val textX = iconRect.right + gap + strokePaint.strokeWidth / 2f
        val baseline = centerY - (tb.top + tb.bottom) / 2f
        val dx = ts * 0.05f
        val dy = ts * 0.07f
        strokePaint.color = 0xFF000000.toInt()
        canvas.drawText(label, textX + dx, baseline + dy, strokePaint)
        canvas.drawText(label, textX, baseline, strokePaint)
        canvas.drawText(label, textX, baseline, fillPaint)
        return true
    }

    private fun loadCoverBitmap(track: Track): Bitmap? {
        val path = track.artPath ?: return null
        return try {
            val file = File(path)
            if (!file.isFile) return null
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            var sample = 1
            val maxSide = 2048 // mais resolução na capa (antes 1280)
            while (opts.outWidth / sample > maxSide || opts.outHeight / sample > maxSide) {
                sample *= 2
            }
            val decode = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(path, decode)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Recorta a capa no centro na proporção w:h (sem esticar), reduz para 1/4,
     * aplica blur de caixa (3 passadas) e devolve o bitmap pequeno; quem desenha escala com filtro.
     * Blur real em resolução menor evita os "blocos" do antigo downscale/upscale.
     */
    private fun buildBlurredBackground(src: Bitmap, w: Int, h: Int): Bitmap {
        val dstRatio = w.toFloat() / h
        val srcRatio = src.width.toFloat() / src.height
        val cropW: Int
        val cropH: Int
        if (srcRatio > dstRatio) {
            cropH = src.height
            cropW = (src.height * dstRatio).toInt().coerceIn(1, src.width)
        } else {
            cropW = src.width
            cropH = (src.width / dstRatio).toInt().coerceIn(1, src.height)
        }
        val cropX = (src.width - cropW) / 2
        val cropY = (src.height - cropH) / 2

        val bw = w / 4
        val bh = h / 4
        var cur = Bitmap.createBitmap(src, cropX, cropY, cropW, cropH)
        // Reduz em passos de 1/2 para não serrilhar, depois ajusta para bw x bh
        while (cur.width / 2 >= bw && cur.height / 2 >= bh) {
            val next = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
            if (cur !== src) cur.recycle()
            cur = next
        }
        val small = Bitmap.createScaledBitmap(cur, bw, bh, true)
        if (small !== cur && cur !== src) cur.recycle()

        val result = if (small.isMutable) small else small.copy(Bitmap.Config.ARGB_8888, true).also { small.recycle() }
        boxBlur(result, radius = 9, passes = 3)
        return result
    }

    private fun boxBlur(bmp: Bitmap, radius: Int, passes: Int) {
        val w = bmp.width
        val h = bmp.height
        var a = IntArray(w * h)
        var b = IntArray(w * h)
        bmp.getPixels(a, 0, w, 0, 0, w, h)
        repeat(passes) {
            blurPass(a, b, w, h, radius, horizontal = true)
            blurPass(b, a, w, h, radius, horizontal = false)
        }
        bmp.setPixels(a, 0, w, 0, 0, w, h)
    }

    private fun blurPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val stride = if (horizontal) 1 else w
        val div = 2 * r + 1
        for (l in 0 until lines) {
            val base = if (horizontal) l * w else l
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val p = src[base + i.coerceIn(0, len - 1) * stride]
                sa += p ushr 24; sr += (p shr 16) and 255; sg += (p shr 8) and 255; sb += p and 255
            }
            for (i in 0 until len) {
                dst[base + i * stride] =
                    ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = src[base + (i + r + 1).coerceAtMost(len - 1) * stride]
                val rem = src[base + (i - r).coerceAtLeast(0) * stride]
                sa += (add ushr 24) - (rem ushr 24)
                sr += ((add shr 16) and 255) - ((rem shr 16) and 255)
                sg += ((add shr 8) and 255) - ((rem shr 8) and 255)
                sb += (add and 255) - (rem and 255)
            }
        }
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
