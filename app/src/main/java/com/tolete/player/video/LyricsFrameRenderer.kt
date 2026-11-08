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
import com.tolete.player.data.LyricAnimation
import com.tolete.player.data.LyricLine
import com.tolete.player.data.Track
import com.tolete.player.ui.BuiltInFonts
import com.tolete.player.ui.LyricsShareHelper
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Aparência da letra no vídeo (mesmos valores usados na tela Tocando agora). */
data class LyricsVideoStyle(
    val fontPath: String? = null,
    val translationFontPath: String? = null,
    val primaryColor: Int = 0xFFFFFFFF.toInt(),
    val secondaryColor: Int = 0x66FFFFFF,
    val borderColor: Int = 0xFF000000.toInt(),
    val borderWidth: Float = 0f,
    val translationColor: Int = 0xFFB0BEC5.toInt(),
    /** Fonte usada nas palavras com efeito {des}; null = mesma da letra. */
    val emphasisFontPath: String? = null
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
    val height: Int,
    /** true = sem fundo (transparente): o fundo vem de um preset milk desenhado por baixo. */
    private val transparentBackground: Boolean = false
) {
    private val portrait = height >= width
    private val scale = min(width, height) / 1080f
    private val dw = width / scale
    private val dh = height / scale

    /** Nenhuma linha de letra sincronizada (música instrumental ou sem letra). */
    private val noLyrics: Boolean =
        rawLines.none { it.timeMs >= 0 && it.text.isNotBlank() && !it.isBackground }

    /** Sem letra e sem preset milk: a capa grande ocupa o centro do vídeo. */
    private val bigCover: Boolean = noLyrics && !transparentBackground

    // Área da letra (unidades de projeto)
    private val areaLeft = if (portrait) 72f else 820f
    private val areaRight = if (portrait) dw - 72f else dw - 100f
    private val areaTop = if (portrait) 400f else 70f
    private val areaBottom = if (portrait) dh - 400f else dh - 190f
    private val areaCx = (areaLeft + areaRight) / 2f
    private val areaCy = (areaTop + areaBottom) / 2f

    // Largura da coluna da capa/marca na horizontal (com capa grande ela usa a tela toda)
    private val leftColW = if (!portrait && bigCover) dw else areaLeft

    // Barra de progresso
    private val barX1 = if (portrait) 96f else if (bigCover) dw * 0.22f else areaLeft
    private val barX2 = if (portrait) dw - 96f else if (bigCover) dw * 0.78f else areaRight
    private val barY = if (portrait) dh - 290f else if (bigCover) dh - 190f else dh - 110f

    private val customTypeface: Typeface? = resolveTypeface(style.fontPath)
    private val lyricTypeface: Typeface =
        customTypeface ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val translationTypeface: Typeface =
        resolveTypeface(style.translationFontPath) ?: lyricTypeface
    private val emphasisTypeface: Typeface =
        resolveTypeface(style.emphasisFontPath) ?: lyricTypeface

    private val primary = (style.primaryColor and 0x00FFFFFF) or (0xF2 shl 24)
    private val dim = (style.secondaryColor and 0x00FFFFFF) or (0x94 shl 24)

    private val baseSize = 58f
    private val TRANSLATION_SIZE_MUL = 0.88f
    private val lineGap = 44f
    private val rowFactor = 1.28f

    // Efeitos de palavra — mesmos valores da tela Tocando agora (SyllableLine), convertidos de dp
    // para unidades de projeto (~2,6 unidades por dp num celular de ~411dp de largura).
    private val DES_SCALE = 1.24f
    /** Tempo (ms) que o destaque leva para crescer até o tamanho final. */
    private val DES_GROW_MS = 160f
    private val GLO_HALF_PERIOD_MS = 460f
    private val POP_HALF_PERIOD_MS = 110f
    private val POP_DX = 6.2f
    private val POP_DY = 2.8f
    private val DED_DELAY_MS = 1000f
    private val DED_DELAY_TR_MS = 1500f
    private val DED_FADE_MS = 200f
    private val ZOOM_MS = 300f
    private val SLI_MS = 280f
    private val FLA_MS = 220f
    private val VIB_MS = 450f
    private val SHD_MS = 500f
    private val TYP_CHAR_MS = 55f
    private val VIB_DX = 2.6f
    private val VIB_DY = 1.6f
    private val WAV_AMP = 6.5f
    private val ECO_DX = 7f
    private val ECO_DY = 5f

    // ---- layout das linhas ----
    /**
     * [w] é a largura reservada no layout. Em palavras {des} ela já considera o texto ampliado
     * (fonte de ênfase × DES_SCALE): [leadW] = espaço inicial (tamanho normal), [coreW] = texto ampliado.
     */
    private class Seg(
        val text: String,
        val x: Float,
        val w: Float,
        val wordIdx: Int,
        val leadW: Float = 0f,
        val coreW: Float = 0f,
        val des: Boolean = false
    )

    /** [width] = largura com os espaços reservados; [plainWidth] = largura do texto liso (linha inativa). */
    private class Row(val text: String, val width: Float, val segs: List<Seg>?, val plainWidth: Float = width)
    private class Layout(
        val line: LyricLine,
        val rows: List<Row>,
        val size: Float,
        val rowH: Float,
        val baselineOff: Float
    ) {
        val height: Float get() = rows.size * rowH
    }

    // Mantém principal + tradução; backing fica para desenho paralelo quando no mesmo instante.
    private val lines: List<LyricLine> = rawLines
        .filter { it.timeMs >= 0 && it.text.isNotBlank() && !it.isBackground }
        .sortedBy { it.timeMs }
    private val bgLines: List<LyricLine> = rawLines
        .filter { it.timeMs >= 0 && it.text.isNotBlank() && it.isBackground }
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
        if (transparentBackground) frame.eraseColor(0)
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
        // Prefere linha principal (não-tradução) para o scroll; tradução acompanha logo abaixo
        if (ans >= 0 && lines[ans].isTranslation) {
            var i = ans - 1
            while (i >= 0 && lines[i].isTranslation) i--
            if (i >= 0 && lines[i].timeMs == lines[ans].timeMs) return i
        }
        return ans
    }

    /** Última linha de tradução com timeMs <= tMs (para cor ativa da tradução). */
    private fun activeTranslationIndex(tMs: Long): Int {
        var ans = -1
        for (i in lines.indices) {
            if (starts[i] > tMs) break
            if (lines[i].isTranslation) ans = i
        }
        return ans
    }

    private fun centerOf(i: Int): Float = cumY[i] + layouts[i].height / 2f

    private fun drawLyrics(c: Canvas, tMs: Long) {
        val n = layouts.size
        if (n == 0) return

        val a = activeIndex(tMs)
        val aTr = activeTranslationIndex(tMs)
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
            val isTr = lay.line.isTranslation
            // Efeitos ({sil}/{glo}/{pop}/{des}) valem também nas linhas de tradução: a tradução fica
            // "ativa" enquanto for a vigente (aTr); a principal, enquanto for a ativa (a).
            val karaoke = lay.line.hasSyllableSync && (if (isTr) j == aTr else j == a)
            val sizeMul = if (isTr) TRANSLATION_SIZE_MUL else 1f
            // Fonte separada para tradução, se configurada
            val face = if (isTr) translationTypeface else lyricTypeface
            fillPaint.typeface = face
            strokePaint.typeface = face
            measure.typeface = face
            fillPaint.textSize = lay.size * sizeMul
            strokePaint.textSize = lay.size * sizeMul
            strokePaint.strokeWidth = style.borderWidth * 2f
            val lineAlpha = if (isTr) alpha * 0.95f else alpha
            // Tradução: cor escolhida só enquanto está no timestamp ativo; senão secundária
            val trColor = (style.translationColor and 0x00FFFFFF) or (0xF2 shl 24)
            val lineMix = mix

            for ((r, row) in lay.rows.withIndex()) {
                val baseline = top + r * lay.rowH + lay.baselineOff
                if (karaoke && row.segs != null) {
                    drawKaraokeRow(
                        c, lay, row, areaCx - row.width / 2f, baseline, tMs, lineAlpha,
                        face, lay.size * sizeMul, if (isTr) trColor else primary
                    )
                } else {
                    // Linha inativa: texto liso, centralizado pela largura dele (sem os espaços
                    // reservados para os destaques).
                    val x0 = areaCx - row.plainWidth / 2f
                    val color = if (isTr) {
                        // Coriva só enquanto esta tradução é a vigente (timeMs <= tMs)
                        // — independente do activeIndex, que aponta para a linha principal
                        val trMix = if (j == aTr) 1f else 0f
                        ColorUtils.blendARGB(dim, trColor, trMix)
                    } else {
                        ColorUtils.blendARGB(dim, primary, lineMix)
                    }
                    drawText(c, row.text, x0, baseline, color, lineAlpha)
                }
            }
            c.restore()
        }
    }

    /** Onda triangular 0→1→0 (equivale ao animateFloat com RepeatMode.Reverse do player). */
    private fun tri(x: Float): Float {
        val m = ((x % 2f) + 2f) % 2f
        return if (m <= 1f) m else 2f - m
    }

    private fun setPaintStyle(face: Typeface, size: Float) {
        fillPaint.typeface = face
        strokePaint.typeface = face
        fillPaint.textSize = size
        strokePaint.textSize = size
    }

    private fun drawKaraokeRow(
        c: Canvas,
        lay: Layout,
        row: Row,
        x0: Float,
        baseline: Float,
        tMs: Long,
        alpha: Float,
        /** Fonte e tamanho desta linha (letra principal ou tradução). */
        baseFace: Typeface,
        baseSize: Float,
        /** Cor da parte já cantada (primária na letra, cor da tradução na tradução). */
        lit: Int
    ) {
        val words = lay.line.words
        for (seg in row.segs!!) {
            val w = words[seg.wordIdx]
            val end = wordEnd(lay.line, seg.wordIdx)
            // O efeito começa no tempo da palavra e dura enquanto a linha estiver ativa.
            val started = w.timeMs < 0L || tMs >= w.timeMs
            val anim = if (started) w.animation else LyricAnimation.NONE
            val t0 = if (w.timeMs >= 0L) w.timeMs else lay.line.timeMs
            val elapsed = (tMs - t0).coerceAtLeast(0L).toFloat()

            var text = seg.text
            var x = x0 + seg.x
            var segW = seg.w
            var fontSize = baseSize
            if (seg.des) {
                // Destacar: o layout já reservou o espaço do texto ampliado (leadW + coreW), então a
                // palavra cresce no lugar, centralizada no espaço dela, sem invadir as vizinhas.
                val core = seg.text.trim()
                val grow = if (started) {
                    val k = ((tMs - t0) / DES_GROW_MS).coerceIn(0f, 1f)
                    1f - (1f - k) * (1f - k)
                } else 0f
                fontSize = baseSize * (1f + (DES_SCALE - 1f) * grow)
                setPaintStyle(if (started) emphasisTypeface else baseFace, fontSize)
                segW = fillPaint.measureText(core)
                val center = x0 + seg.x + seg.leadW + seg.coreW / 2f
                text = core
                x = center - segW / 2f
            }

            // Dim da tradução: versão mais apagada da própria cor de tradução (não a secundária da letra)
            val localDim = if (lay.line.isTranslation) {
                ColorUtils.setAlphaComponent(lit, 0x94)
            } else dim

            c.save()
            // Transforms por efeito
            if (anim == LyricAnimation.POP) {
                val ph = (tri(elapsed / POP_HALF_PERIOD_MS) * 2f * Math.PI).toFloat()
                c.translate(sin(ph) * POP_DX, sin(ph * 2f) * POP_DY)
            } else if (anim == LyricAnimation.VIB && elapsed < VIB_MS) {
                val ph = (tri(elapsed / POP_HALF_PERIOD_MS) * 2f * Math.PI).toFloat()
                c.translate(sin(ph) * VIB_DX, sin(ph * 1.7f) * VIB_DY)
            } else if (anim == LyricAnimation.WAV) {
                c.translate(0f, sin((elapsed / 160f) * 2f * Math.PI.toFloat()) * WAV_AMP)
            } else if (anim == LyricAnimation.SLI) {
                val k = (elapsed / SLI_MS).coerceIn(0f, 1f)
                val e = 1f - (1f - k) * (1f - k)
                c.translate(0f, (1f - e) * 18f)
            }
            // Zoom in/out em torno do centro do segmento
            val zScale = when (anim) {
                LyricAnimation.ZOU -> {
                    val k = (elapsed / ZOOM_MS).coerceIn(0f, 1f)
                    1.55f - 0.55f * (1f - (1f - k) * (1f - k))
                }
                LyricAnimation.ZIN -> {
                    val k = (elapsed / ZOOM_MS).coerceIn(0f, 1f)
                    0.55f + 0.45f * (1f - (1f - k) * (1f - k))
                }
                else -> 1f
            }
            if (zScale != 1f) {
                val cx = x + segW / 2f
                val cy = baseline - fontSize * 0.35f
                c.translate(cx, cy)
                c.scale(zScale, zScale)
                c.translate(-cx, -cy)
            }

            // Alpha: Sumir {ded} 1s / 1,5s tradução
            var drawAlpha = alpha
            if (anim == LyricAnimation.DED && t0 >= 0L) {
                // Mesmo tempo na letra e na tradução
                val delay = DED_DELAY_MS
                val e = (tMs - t0).toFloat()
                drawAlpha = when {
                    e < delay -> alpha
                    e < delay + DED_FADE_MS -> alpha * (1f - (e - delay) / DED_FADE_MS)
                    else -> 0f
                }
            }

            // Typewriter: corta o texto
            if (anim == LyricAnimation.TYP && text.isNotEmpty()) {
                val chars = ((elapsed / TYP_CHAR_MS).toInt() + 1).coerceIn(0, text.length)
                text = text.take(chars)
                segW = fillPaint.measureText(text)
            }

            val span = (end - w.timeMs).coerceAtLeast(1L).toFloat()
            val lin = ((tMs - w.timeMs) / span).coerceIn(0f, 1f)
            // Tradução: palavras sem efeito ficam na cor cheia (sem preenchimento de karaokê)
            val trNoKaraoke = lay.line.isTranslation && w.animation == LyricAnimation.NONE

            // Eco ghost
            if (anim == LyricAnimation.ECO) {
                val ghostPhase = (elapsed / 180f) % 1f
                c.save()
                c.translate(ECO_DX, ECO_DY + ghostPhase * 4f)
                drawText(c, text, x, baseline, lit, drawAlpha * 0.32f * (1f - ghostPhase * 0.4f))
                c.restore()
            }

            when {
                anim == LyricAnimation.GLO -> {
                    val col = ColorUtils.blendARGB(lit, localDim, tri(elapsed / GLO_HALF_PERIOD_MS))
                    drawText(c, text, x, baseline, col, drawAlpha)
                }
                anim == LyricAnimation.RNB -> {
                    val hue = ((elapsed / 12f) % 360f)
                    val col = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.85f, 1f))
                    drawText(c, text, x, baseline, col, drawAlpha)
                }
                anim == LyricAnimation.FLA && elapsed < FLA_MS -> {
                    val boost = 1f - (elapsed / FLA_MS)
                    val col = ColorUtils.blendARGB(lit, android.graphics.Color.WHITE, boost * 0.85f)
                    drawText(c, text, x, baseline, col, drawAlpha)
                }
                anim == LyricAnimation.FRZ -> {
                    drawText(c, text, x, baseline, lit, drawAlpha)
                }
                anim == LyricAnimation.SHD && elapsed < SHD_MS -> {
                    val k = 1f - (elapsed / SHD_MS).coerceIn(0f, 1f)
                    // sombra
                    val shCol = ColorUtils.setAlphaComponent(lit, (90 * k).toInt().coerceIn(0, 255))
                    drawText(c, text, x + 3f * k, baseline + 4f * k, shCol, drawAlpha * k)
                    drawText(c, text, x, baseline, lit, drawAlpha)
                }
                trNoKaraoke -> drawText(c, text, x, baseline, lit, drawAlpha)
                tMs < w.timeMs -> drawText(c, text, x, baseline, localDim, drawAlpha)
                lin >= 1f || anim == LyricAnimation.FRZ -> drawText(c, text, x, baseline, lit, drawAlpha)
                else -> {
                    val p = 1f - (1f - lin) * (1f - lin)
                    drawText(c, text, x, baseline, localDim, drawAlpha)
                    c.save()
                    c.clipRect(x, baseline - fontSize * 1.3f, x + segW * p, baseline + fontSize * 0.5f)
                    drawText(c, text, x, baseline, lit, drawAlpha)
                    c.restore()
                }
            }
            c.restore()

            if (seg.des) setPaintStyle(baseFace, baseSize)
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
        if (transparentBackground) {
            // sem fundo aqui: o preset milk é desenhado por baixo deste layer
        } else if (cover != null) {
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
                if (transparentBackground) intArrayOf(0x40000000, 0x59000000, 0x99000000.toInt())
                else intArrayOf(0x66000000, 0x88000000.toInt(), 0xCC000000.toInt()),
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
        if (bigCover) {
            // Sem letra: capa grande no centro, título e artista centralizados logo abaixo.
            val side = if (portrait) min(areaRight - areaLeft, areaBottom - areaTop) * 0.82f else 520f
            val cx = dw / 2f
            coverRect = if (portrait) {
                RectF(cx - side / 2f, areaCy - side / 2f - 40f, cx + side / 2f, areaCy + side / 2f - 40f)
            } else {
                RectF(cx - side / 2f, 130f, cx + side / 2f, 130f + side)
            }
            // Sombra suave atrás da capa (camadas translúcidas, sem BlurMaskFilter)
            val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x07000000 }
            val rad = coverRect.width() * 0.14f
            for (i in 10 downTo 1) {
                val grow = i * 6f
                val rr = RectF(coverRect).apply { inset(-grow, -grow); offset(0f, 14f) }
                c.drawRoundRect(rr, rad + grow, rad + grow, shadow)
            }
            titlePaint.textSize = if (portrait) 54f else 46f
            artistPaint.textSize = if (portrait) 38f else 34f
            titlePaint.textAlign = Paint.Align.CENTER
            artistPaint.textAlign = Paint.Align.CENTER
            val maxW = if (portrait) dw - 144f else dw * 0.6f
            c.drawText(ellipsize(title, titlePaint, maxW), cx, coverRect.bottom + 92f, titlePaint)
            c.drawText(ellipsize(artist, artistPaint, maxW), cx, coverRect.bottom + 148f, artistPaint)
        } else if (portrait) {
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
        val pillCx = if (portrait) dw / 2f else leftColW / 2f
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
        // A tradução é DESENHADA com fonte própria e 88% do tamanho (ver drawLyrics). A largura das
        // linhas (usada para centralizar e quebrar) precisa ser medida com exatamente o mesmo
        // typeface/tamanho, senão o texto sai deslocado do centro.
        val isTr = line.isTranslation
        measure.typeface = if (isTr) translationTypeface else lyricTypeface
        measure.textSize = if (isTr) size * TRANSLATION_SIZE_MUL else size
        val maxW = areaRight - areaLeft
        val rows = ArrayList<Row>()

        if (line.hasSyllableSync) {
            var cur = ArrayList<Seg>()
            var x = 0f

            // Monta o segmento medindo-o como será desenhado. Em {des}, o texto é medido com a fonte
            // de ênfase ampliada, para o espaço reservado bater com o que aparece no vídeo.
            fun makeSeg(t: String, xPos: Float, idx: Int): Seg {
                if (line.words[idx].animation != LyricAnimation.DES) {
                    return Seg(t, xPos, measure.measureText(t), idx)
                }
                val core = t.trim()
                val lead = t.substring(0, t.length - t.trimStart().length)
                val trail = t.substring(t.trimEnd().length)
                val leadW = if (lead.isEmpty()) 0f else measure.measureText(lead)
                val trailW = if (trail.isEmpty() || core.isEmpty()) 0f else measure.measureText(trail)
                val savedFace = measure.typeface
                val savedSize = measure.textSize
                measure.typeface = emphasisTypeface
                measure.textSize = savedSize * DES_SCALE
                val coreW = measure.measureText(core)
                measure.typeface = savedFace
                measure.textSize = savedSize
                return Seg(t, xPos, leadW + coreW + trailW, idx, leadW, coreW, true)
            }

            for ((i, w) in line.words.withIndex()) {
                var t = w.text
                if (i > 0 && !w.isSyllable) {
                    val prev = line.words[i - 1].text
                    val needSpace = prev.isNotEmpty() && t.isNotEmpty() &&
                        !prev.last().isWhitespace() && !t.first().isWhitespace() &&
                        !",.;:!?)]}".contains(t.first())
                    // Espaço maior entre palavras normais para diferenciar de sílabas (coladas)
                    if (needSpace) t = "  $t"
                }
                if (cur.isEmpty()) t = t.trimStart()
                var seg = makeSeg(t, x, i)
                if (cur.isNotEmpty() && x + seg.w > maxW) {
                    rows.add(finishRow(cur, x))
                    cur = ArrayList()
                    x = 0f
                    t = t.trimStart()
                    seg = makeSeg(t, 0f, i)
                }
                cur.add(seg)
                x += seg.w
            }
            if (cur.isNotEmpty()) rows.add(finishRow(cur, x))
        } else {
            for (s in wrapPlain(line.text, measure, maxW)) {
                rows.add(Row(s, measure.measureText(s), null))
            }
        }
        if (rows.isEmpty()) rows.add(Row(line.text, measure.measureText(line.text), null))

        // Métricas da fonte realmente usada (centraliza verticalmente também a tradução)
        val fm = measure.fontMetrics
        val rowH = size * rowFactor
        val baselineOff = (rowH - (fm.descent - fm.ascent)) / 2f - fm.ascent
        // Restaura o paint de medição para a fonte da letra principal
        measure.typeface = lyricTypeface
        measure.textSize = size
        return Layout(line, rows, size, rowH, baselineOff)
    }

    private fun finishRow(segs: MutableList<Seg>, x: Float): Row {
        val last = segs.last()
        val trimmed = last.text.trimEnd()
        var width = x
        if (trimmed != last.text) {
            val w = if (last.des) last.leadW + last.coreW else measure.measureText(trimmed)
            segs[segs.lastIndex] = Seg(trimmed, last.x, w, last.wordIdx, last.leadW, last.coreW, last.des)
            width = last.x + w
        }
        val joined = segs.joinToString("") { it.text }
        // measure ainda está com a fonte/tamanho base desta linha: serve para a largura da versão lisa
        return Row(joined, width, segs.toList(), measure.measureText(joined))
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
