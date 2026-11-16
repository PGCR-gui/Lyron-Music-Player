package com.tolete.player.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.tolete.player.data.RetrospectiveCardRenderer

/**
 * Card de foto (1080×1920) do info card do Last.fm para música, artista ou álbum.
 *
 * O fundo é um gradiente que mistura a cor dominante da foto do perfil com a da imagem da
 * música/artista/álbum; no rodapé fica a pill de vidro com a logo do Lyron.
 */
internal object EntityShareCard {

    const val W = 1080
    const val H = 1920

    enum class Kind(val label: String) {
        TRACK("MÚSICA"), ARTIST("ARTISTA"), ALBUM("ÁLBUM")
    }

    /** Cor dominante/vibrante de [bmp], ajustada para servir de fundo (mesma regra dos cards da retrospectiva). */
    fun dominantColor(bmp: Bitmap?): Int? {
        if (bmp == null || bmp.isRecycled) return null
        return try {
            val palette = Palette.from(bmp).maximumColorCount(24).generate()
            val raw = palette.getVibrantColor(
                palette.getDominantColor(palette.getMutedColor(0))
            )
            if (raw == 0) null else RetrospectiveCardRenderer.tuneForBackground(raw)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Cores do gradiente: [entityColor] em cima, [userColor] embaixo. Se uma delas faltar usa a
     * outra (variando o tom), e sem nenhuma das duas cai no [fallback] (cor de destaque do app).
     */
    fun gradientColors(entityColor: Int?, userColor: Int?, fallback: Int): Triple<Int, Int, Int> {
        val a = entityColor ?: userColor ?: RetrospectiveCardRenderer.tuneForBackground(fallback)
        val b = userColor ?: shiftHue(a, 40f)
        val mid = ColorUtils.blendARGB(a, b, 0.5f)
        return Triple(a, mid, b)
    }

    private fun shiftHue(color: Int, degrees: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[0] = (hsl[0] + degrees) % 360f
        return ColorUtils.HSLToColor(hsl)
    }

    fun build(
        context: Context,
        kind: Kind,
        username: String,
        entityName: String,
        entitySubtitle: String?,
        playCount: Int,
        userPhoto: Bitmap?,
        entityPhoto: Bitmap?,
        accent: Int
    ): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // ---- fundo: gradiente das cores da foto do perfil + imagem da entidade
        val (top, mid, bottom) = gradientColors(dominantColor(entityPhoto), dominantColor(userPhoto), accent)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, W * 0.35f, H.toFloat(),
                intArrayOf(top, mid, bottom), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
        // brilho suave atrás da imagem para dar profundidade
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                W / 2f, 640f, 760f,
                ColorUtils.setAlphaComponent(Color.WHITE, 46), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), glow)

        val avg = ColorUtils.blendARGB(ColorUtils.blendARGB(top, mid, 0.5f), bottom, 0.5f)
        val light = ColorUtils.calculateLuminance(avg) > 0.5
        val fg = if (light) 0xFF14101C.toInt() else Color.WHITE
        val fgSoft = ColorUtils.setAlphaComponent(fg, 200)

        val mx = 110f
        val contentW = W - 2 * mx

        // ---- rótulo do tipo
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(fg, 190)
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.2f
        }
        c.drawText(kind.label, mx, 170f, labelPaint)

        // ---- imagem da entidade (artista em círculo)
        val size = contentW
        val imgRect = RectF(mx, 215f, mx + size, 215f + size)
        val radius = if (kind == Kind.ARTIST) size / 2f else 56f
        // sombra leve
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 0, 0, 0) }
        c.drawRoundRect(RectF(imgRect).apply { offset(0f, 14f) }, radius, radius, shadow)
        if (entityPhoto != null) {
            drawCropped(c, entityPhoto, imgRect, radius)
        } else {
            val ph = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(fg, 40) }
            c.drawRoundRect(imgRect, radius, radius, ph)
        }

        // ---- nome (e subtítulo)
        var y = imgRect.bottom + 70f
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            textSize = 76f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        y = drawLayout(c, entityName, mx, y, contentW.toInt(), namePaint, 2)
        if (!entitySubtitle.isNullOrBlank()) {
            val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fgSoft
                textSize = 44f
            }
            y = drawLayout(c, entitySubtitle, mx, y + 6f, contentW.toInt(), subPaint, 1)
        }

        // ---- avatar + contagem
        val avSize = 150f
        val avTop = y + 50f
        val avRect = RectF(mx, avTop, mx + avSize, avTop + avSize)
        if (userPhoto != null) {
            drawCropped(c, userPhoto, avRect, avSize / 2f)
        } else {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(fg, 60) }
            c.drawCircle(avRect.centerX(), avRect.centerY(), avSize / 2f, p)
        }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = ColorUtils.setAlphaComponent(fg, 120)
        }
        c.drawCircle(avRect.centerX(), avRect.centerY(), avSize / 2f, ring)

        val tx = avRect.right + 36f
        val tw = (W - mx - tx).toInt()
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fgSoft
            textSize = 40f
        }
        val big = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            textSize = 84f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val who = drawLayout(c, "$username já ouviu", tx, avTop - 6f, tw, small, 1)
        drawLayout(c, "$playCount ${if (playCount == 1) "vez" else "vezes"}!", tx, who - 4f, tw, big, 1)

        // ---- pill com a logo do Lyron
        val drawn = try {
            LyricsShareHelper.drawBrandPill(c, context, W / 2f, H - 120f, 64f)
        } catch (_: Exception) {
            false
        }
        if (!drawn) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ColorUtils.setAlphaComponent(fg, 180)
                textSize = 36f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            c.drawText("Lyron Music Player", W / 2f, H - 108f, p)
        }
        return bmp
    }

    /** Desenha [src] preenchendo [dst] (corte central) com cantos de raio [radius]. */
    private fun drawCropped(c: Canvas, src: Bitmap, dst: RectF, radius: Float) {
        val scale = maxOf(dst.width() / src.width, dst.height() / src.height)
        val cw = (dst.width() / scale).toInt().coerceAtMost(src.width)
        val ch = (dst.height() / scale).toInt().coerceAtMost(src.height)
        val left = ((src.width - cw) / 2).coerceAtLeast(0)
        val topPx = ((src.height - ch) / 2).coerceAtLeast(0)
        val srcRect = Rect(left, topPx, left + cw, topPx + ch)
        val path = Path().apply { addRoundRect(dst, radius, radius, Path.Direction.CW) }
        c.save()
        c.clipPath(path)
        c.drawBitmap(src, srcRect, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        c.restore()
    }

    /** Texto com quebra e reticências; devolve o Y logo abaixo. Funciona com japonês/chinês. */
    private fun drawLayout(
        c: Canvas, text: String, x: Float, y: Float, width: Int, paint: TextPaint, maxLines: Int
    ): Float {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        c.save()
        c.translate(x, y)
        layout.draw(c)
        c.restore()
        return y + layout.height
    }
}
