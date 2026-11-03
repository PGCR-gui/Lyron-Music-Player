package com.tolete.player.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.tolete.player.data.LyricLine
import com.tolete.player.data.Track
import com.tolete.player.ui.BuiltInFonts
import com.tolete.player.ui.LyricsShareHelper
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Aparência da letra no vídeo (mesmos valores usados na tela Tocando agora). */
data class LyricsVideoStyle(
    val fontPath: String? = null,
    val primaryColor: Int = 0xFFFFFFFF.toInt(),
    val secondaryColor: Int = 0x66FFFFFF,
    val borderColor: Int = 0xFF000000.toInt(),
    val borderWidth: Float = 0f
)

/**
 * Desenha os frames do vídeo. Toda a geometria está em "unidades de projeto" onde o lado menor
 * do vídeo vale 1080; o canvas é escalado para a resolução escolhida, então o resultado é o mesmo
 * em 480p ou 1440p.
 *
 * A camada estática (fundo borrado da capa, cabeçalho, marca) é desenhada uma vez; por frame só
 * se desenha a letra e a barra de progresso.
 */
class LyricsFrameRenderer(
    private val context: Context,
    private val track: Track,
    rawLines: List<LyricLine>,
    private val style: LyricsVideoStyle,
    val width: Int,
    val height: Int
) {
    private val portrait = height >= width
    private val scale = min(width, height) / 1080f
    private val dw = width / scale
    private val dh = height / scale

    // Área da letra (unidades de projeto)
    private val areaLeft = if (portrait) 72f else 820f
    private val areaRight = if (portrait) dw - 72f else dw - 100f
    private val areaTop = if (portrait) 400f else 70f
    private val areaBottom = if (portrait) dh - 400f else dh - 190f
    private val areaCx = (areaLeft + areaRight) / 2f
    private val areaCy = (areaTop + areaBottom) / 2f

    // Barra de progresso
    private val barX1 = if (portrait) 96f else areaLeft
    private val barX2 = if (portrait) dw - 96f else areaRight
    private val barY = if (portrait) dh - 290f else dh - 110f

    private val customTypeface: Typeface? = resolveTypeface(style.fontPath)
    private val lyricTypeface: Typeface =
        customTypeface ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    private val primary = (style.primaryColor and 0x00FFFFFF) or (0xF2 shl 24)
    private val dim = (style.secondaryColor and 0x00FFFFFF) or (0x94 shl 24)

    private val baseSize = 58f
    private val lineGap = 44f
    private val rowFactor = 1.28f

    // ---- layout das linhas ----
    private class Seg(val text: String, val x: Float, val w: Float, val wordIdx: Int)
    private class Row(val text: String, val width: Float, val segs: List<Seg>?)
    private class Layout(
        val line: LyricLine,
        val rows: List<Row>,
        val size: Float,
        val rowH: Float,
        val baselineOff: Float
    ) {
        val height: Float get() = rows.size * rowH
    }

    // Letras de fundo (backing vocals) ficam de fora: sobrepõem a linha principal no tempo
    // e fariam a linha ativa piscar entre uma e outra.
    private val lines: List<LyricLine> = rawLines
        .filter { it.timeMs >= 0 && it.text.isNotBlank() && !it.isBackground }
        .sortedBy { it.timeMs }
    private val starts = LongArray(lines.size) { lines[it].timeMs }

    private val measure = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = lyricTypeface
        isSubpixelText = true
    }
    private val layouts: List<Layout> = lines.map { buildLayout(it) }
    private val cumY: FloatArray = FloatArray(layouts.size).also { arr ->
        var y = 0f
        for (i in layouts.indices) {
            arr[i] = y
            y += layouts[i].height + lineGap
        }
    }

    // ---- bitmaps / paints ----
    val frame: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(frame)
    private val staticLayer: Bitmap = buildStaticLayer()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = lyricTypeface
        isSubpixelText = true
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = lyricTypeface
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        isSubpixelText = true
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
        textSize = 30f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    private val rectF = RectF()

    fun render(tMs: Long): Bitmap {
        canvas.drawBitmap(staticLayer, 0f, 0f, null)
        canvas.save()
        canvas.scale(scale, scale)
        drawLyrics(canvas, tMs)
        drawProgress(canvas, tMs)
        canvas.restore()
        return frame
    }

    fun close() {
        if (!frame.isRecycled) frame.recycle()
        if (!staticLayer.isRecycled) staticLayer.recycle()
    }

    // ---------------------------------------------------------------------------------------
    // Letra
    // ---------------------------------------------------------------------------------------

    private fun activeIndex(tMs: Long): Int {
        var lo = 0
        var hi = starts.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (starts[mid] <= tMs) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }

    private fun centerOf(i: Int): Float = cumY[i] + layouts[i].height / 2f

    private fun drawLyrics(c: Canvas, tMs: Long) {
        val n = layouts.size
        if (n == 0) return

        val a = activeIndex(tMs)
        val k: Float
        val scrollCenter: Float
        val focus: Float
        if (a < 0) {
            k = 0f
            scrollCenter = centerOf(0)
            focus = 0f
        } else {
            val x = ((tMs - starts[a]) / 380f).coerceIn(0f, 1f)
            k = 1f - (1f - x) * (1f - x) * (1f - x)
            val from = centerOf(max(a - 1, 0))
            val to = centerOf(a)
            scrollCenter = from + (to - from) * k
            focus = if (a <= 0) 0f else (a - 1) + k
        }

        for (j in 0 until n) {
            val lay = layouts[j]
            val top = areaCy - scrollCenter + cumY[j]
            val mid = top + lay.height / 2f
            if (mid < areaTop - 80f || mid > areaBottom + 80f) continue

            val d = abs(j - focus)
            var alpha = (1f - 0.24f * d).coerceIn(0.14f, 1f)
            val edge = min(mid - areaTop, areaBottom - mid)
            val ex = (edge / 150f).coerceIn(0f, 1f)
            alpha *= ex * ex * (3f - 2f * ex)
            if (alpha <= 0.02f) continue

            val mix = when {
                a < 0 -> 0f
                j == a -> k
                j == a - 1 -> 1f - k
                else -> 0f
            }
            val sc = 0.9f + 0.1f * mix

            c.save()
            c.scale(sc, sc, areaCx, mid)
            val karaoke = (j == a) && lay.line.hasSyllableSync
            fillPaint.textSize = lay.size
            strokePaint.textSize = lay.size
            strokePaint.strokeWidth = style.borderWidth * 2f

            for ((r, row) in lay.rows.withIndex()) {
                val baseline = top + r * lay.rowH + lay.baselineOff
                val x0 = areaCx - row.width / 2f
                if (karaoke && row.segs != null) {
                    drawKaraokeRow(c, lay, row, x0, baseline, tMs, alpha)
                } else {
                    val color = ColorUtils.blendARGB(dim, primary, mix)
                    drawText(c, row.text, x0, baseline, color, alpha)
                }
            }
            c.restore()
        }
    }

    private fun drawKaraokeRow(
        c: Canvas,
        lay: Layout,
        row: Row,
        x0: Float,
        baseline: Float,
        tMs: Long,
        alpha: Float
    ) {
        val words = lay.line.words
        for (seg in row.segs!!) {
            val w = words[seg.wordIdx]
            val end = wordEnd(lay.line, seg.wordIdx)
            val x = x0 + seg.x
            val span = (end - w.timeMs).coerceAtLeast(1L).toFloat()
            val lin = ((tMs - w.timeMs) / span).coerceIn(0f, 1f)
            when {
                tMs < w.timeMs -> drawText(c, seg.text, x, baseline, dim, alpha)
                lin >= 1f -> drawText(c, seg.text, x, baseline, primary, alpha)
                else -> {
                    val p = 1f - (1f - lin) * (1f - lin)
                    drawText(c, seg.text, x, baseline, dim, alpha)
                    c.save()
                    c.clipRect(x, baseline - lay.size * 1.3f, x + seg.w * p, baseline + lay.size * 0.5f)
                    drawText(c, seg.text, x, baseline, primary, alpha)
                    c.restore()
                }
            }
        }
    }

    private fun wordEnd(line: LyricLine, i: Int): Long {
        val cur = line.words[i]
        return when {
            cur.endMs > cur.timeMs -> cur.endMs
            i < line.words.lastIndex -> line.words[i + 1].timeMs
            line.endMs > cur.timeMs -> line.endMs
            else -> cur.timeMs + 350L
        }
    }

    private fun drawText(c: Canvas, text: String, x: Float, y: Float, color: Int, alpha: Float) {
        if (text.isEmpty()) return
        if (style.borderWidth > 0f) {
            strokePaint.color = withAlpha(style.borderColor, alpha)
            c.drawText(text, x, y, strokePaint)
        }
        fillPaint.color = withAlpha(color, alpha)
        c.drawText(text, x, y, fillPaint)
    }

    private fun withAlpha(color: Int, mul: Float): Int {
        val a = (((color ushr 24) and 0xFF) * mul).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    // ---------------------------------------------------------------------------------------
    // Progresso
    // ---------------------------------------------------------------------------------------

    private fun drawProgress(c: Canvas, tMs: Long) {
        val dur = track.duration.coerceAtLeast(1L)
        val pos = tMs.coerceIn(0L, dur)
        val frac = pos.toFloat() / dur

        barPaint.style = Paint.Style.FILL
        barPaint.color = 0x44FFFFFF
        rectF.set(barX1, barY - 5f, barX2, barY + 5f)
        c.drawRoundRect(rectF, 5f, 5f, barPaint)

        val fillX = barX1 + (barX2 - barX1) * frac
        barPaint.color = 0xFFFFFFFF.toInt()
        rectF.set(barX1, barY - 5f, max(fillX, barX1 + 10f), barY + 5f)
        c.drawRoundRect(rectF, 5f, 5f, barPaint)
        c.drawCircle(fillX, barY, 12f, barPaint)

        val left = fmt(pos)
        val right = fmt(dur)
        timePaint.textAlign = Paint.Align.LEFT
        c.drawText(left, barX1, barY + 52f, timePaint)
        timePaint.textAlign = Paint.Align.RIGHT
        c.drawText(right, barX2, barY + 52f, timePaint)
        timePaint.textAlign = Paint.Align.LEFT
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    // ---------------------------------------------------------------------------------------
    // Camada estática
    // ---------------------------------------------------------------------------------------

    private fun buildStaticLayer(): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        val filter = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

        // Fundo: capa cortada (sem esticar) + blur, ou cor sólida
        val cover = LyricsShareHelper.loadCoverBitmap(track)
        if (cover != null) {
            val blurred = LyricsShareHelper.buildBlurredBackground(cover, dw.toInt(), dh.toInt())
            c.drawBitmap(blurred, null, RectF(0f, 0f, dw, dh), filter)
            if (!blurred.isRecycled) blurred.recycle()
            c.drawColor(0x33000000)
        } else {
            c.drawColor(0xFF12121A.toInt())
        }
        val veil = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, dh,
                intArrayOf(0x66000000, 0x88000000.toInt(), 0xCC000000.toInt()),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, dw, dh, veil)

        // Cabeçalho: capa + título/artista
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            typeface = if (customTypeface != null) Typeface.create(customTypeface, Typeface.BOLD)
            else Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCFFFFFF.toInt()
            typeface = customTypeface ?: Typeface.DEFAULT
        }
        val title = track.title.ifBlank { "Sem título" }
        val artist = track.artist.ifBlank { "Desconhecido" }

        val coverRect: RectF
        if (portrait) {
            coverRect = RectF(72f, 110f, 72f + 210f, 110f + 210f)
            titlePaint.textSize = 50f
            artistPaint.textSize = 36f
            val tx = coverRect.right + 32f
            val maxW = dw - 72f - tx
            c.drawText(ellipsize(title, titlePaint, maxW), tx, coverRect.top + 92f, titlePaint)
            c.drawText(ellipsize(artist, artistPaint, maxW), tx, coverRect.top + 148f, artistPaint)
        } else {
            val size = 520f
            val left = (areaLeft - size) / 2f
            coverRect = RectF(left, 130f, left + size, 130f + size)
            titlePaint.textSize = 46f
            artistPaint.textSize = 34f
            val maxW = size
            c.drawText(ellipsize(title, titlePaint, maxW), left, coverRect.bottom + 82f, titlePaint)
            c.drawText(ellipsize(artist, artistPaint, maxW), left, coverRect.bottom + 134f, artistPaint)
        }
        drawCover(c, cover, coverRect, filter)
        if (cover != null && !cover.isRecycled) cover.recycle()

        // Marca (pílula LYRON), a mesma da foto do trecho
        val pillH = if (portrait) 96f else 84f
        val pillCx = if (portrait) dw / 2f else areaLeft / 2f
        val pillCy = if (portrait) dh - 130f else dh - 100f
        try {
            LyricsShareHelper.drawBrandPill(c, context, pillCx, pillCy, pillH)
        } catch (_: Exception) { }

        return bmp
    }

    private fun drawCover(c: Canvas, cover: Bitmap?, rect: RectF, filter: Paint) {
        val radius = rect.width() * 0.14f
        val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        val save = c.save()
        c.clipPath(clip)
        if (cover != null) {
            val side = min(cover.width, cover.height)
            val sx = (cover.width - side) / 2
            val sy = (cover.height - side) / 2
            c.drawBitmap(cover, Rect(sx, sy, sx + side, sy + side), rect, filter)
        } else {
            c.drawRect(rect, Paint().apply { color = 0xFF2A2A3A.toInt() })
        }
        c.restoreToCount(save)
    }

    // ---------------------------------------------------------------------------------------
    // Layout de texto
    // ---------------------------------------------------------------------------------------

    private fun buildLayout(line: LyricLine): Layout {
        val size = baseSize
        measure.textSize = size
        val maxW = areaRight - areaLeft
        val rows = ArrayList<Row>()

        if (line.hasSyllableSync) {
            var cur = ArrayList<Seg>()
            var x = 0f
            for ((i, w) in line.words.withIndex()) {
                var t = w.text
                if (i > 0) {
                    val prev = line.words[i - 1].text
                    val needSpace = prev.isNotEmpty() && t.isNotEmpty() &&
                        !prev.last().isWhitespace() && !t.first().isWhitespace() &&
                        !",.;:!?)]}".contains(t.first())
                    if (needSpace) t = " $t"
                }
                if (cur.isEmpty()) t = t.trimStart()
                var tw = measure.measureText(t)
                if (cur.isNotEmpty() && x + tw > maxW) {
                    rows.add(finishRow(cur, x))
                    cur = ArrayList()
                    x = 0f
                    t = t.trimStart()
                    tw = measure.measureText(t)
                }
                cur.add(Seg(t, x, tw, i))
                x += tw
            }
            if (cur.isNotEmpty()) rows.add(finishRow(cur, x))
        } else {
            for (s in wrapPlain(line.text, measure, maxW)) {
                rows.add(Row(s, measure.measureText(s), null))
            }
        }
        if (rows.isEmpty()) rows.add(Row(line.text, measure.measureText(line.text), null))

        val fm = measure.fontMetrics
        val rowH = size * rowFactor
        val baselineOff = (rowH - (fm.descent - fm.ascent)) / 2f - fm.ascent
        return Layout(line, rows, size, rowH, baselineOff)
    }

    private fun finishRow(segs: MutableList<Seg>, x: Float): Row {
        val last = segs.last()
        val trimmed = last.text.trimEnd()
        var width = x
        if (trimmed != last.text) {
            val w = measure.measureText(trimmed)
            segs[segs.lastIndex] = Seg(trimmed, last.x, w, last.wordIdx)
            width = last.x + w
        }
        return Row(segs.joinToString("") { it.text }, width, segs.toList())
    }

    private fun wrapPlain(text: String, paint: Paint, maxW: Float): List<String> {
        val out = ArrayList<String>()
        var cur = ""
        for (word in text.trim().split(Regex("\\s+"))) {
            var w = word
            val trial = if (cur.isEmpty()) w else "$cur $w"
            if (paint.measureText(trial) <= maxW) {
                cur = trial
                continue
            }
            if (cur.isNotEmpty()) {
                out.add(cur)
                cur = ""
            }
            // palavra maior que a linha (ex.: CJK sem espaços) → quebra por caractere
            while (w.length > 1 && paint.measureText(w) > maxW) {
                var n = w.length
                while (n > 1 && paint.measureText(w, 0, n) > maxW) n--
                out.add(w.substring(0, n))
                w = w.substring(n)
            }
            cur = w
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out.ifEmpty { listOf(text) }
    }

    private fun ellipsize(text: String, paint: Paint, maxW: Float): String {
        if (paint.measureText(text) <= maxW) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    private fun resolveTypeface(key: String?): Typeface? {
        if (key.isNullOrBlank() || key == "system") return null
        BuiltInFonts.findEmbedded(key)?.let { emb ->
            return try {
                ResourcesCompat.getFont(context, emb.resId)
            } catch (_: Exception) {
                null
            }
        }
        return try {
            val f = File(key)
            if (f.isFile) Typeface.createFromFile(f) else null
        } catch (_: Exception) {
            null
        }
    }
}
