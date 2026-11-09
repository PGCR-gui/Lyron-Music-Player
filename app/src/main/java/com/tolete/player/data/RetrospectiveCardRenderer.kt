package com.tolete.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import com.tolete.player.ui.LyricsShareHelper
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Renderizador único dos cards da retrospectiva (fotos e vídeos), inspirado no "Rewind":
 * fundo sólido na cor primária da capa/foto, formas onduladas translúcidas à deriva,
 * ícones flutuantes (mesma animação do [com.tolete.player.ui.FloatingIconsBackground]),
 * destaque com foto e listas com foto ao lado do nome.
 *
 * [timeSec] = tempo em segundos (vídeo). Para foto use [STILL_TIME] (estado final, sem entrada).
 */
object RetrospectiveCardRenderer {

    const val W = 1080
    const val H = 1920
    const val STILL_TIME = -1f

    /** Margem lateral segura: telas mais altas que 9:16 cortam as bordas ao preencher. */
    private const val MX = 110f
    private const val BRAND_W = 900
    private const val BRAND_H = 260

    /** Uma linha de ranking com imagem opcional. */
    class Row(val title: String, val subtitle: String, val plays: Int, val imagePath: String?)

    enum class Layout { INTRO, HERO, LIST, TEXT, GENRES }

    /** Faixa tocando no vídeo (nome + disco girando no topo). */
    class NowPlaying(val title: String, val artist: String)

    /** Gênero e participação (%) nos plays com tag de gênero. */
    class GenreStat(val name: String, val percent: Int)

    class Model(
        val id: String,
        val title: String,
        val body: String,
        val icons: String,
        val layout: Layout,
        /** Cor de fundo já resolvida (cor primária da capa ou cor do tema). */
        val background: Int,
        val heroImagePath: String? = null,
        val heroCircle: Boolean = false,
        val heroCaption: String = "",
        val rows: List<Row> = emptyList(),
        val genres: List<GenreStat> = emptyList()
    )

    /** Cores do tema do app (ARGB) usadas nos cards sem capa e como contraste. */
    class ThemeColors(val primary: Int, val secondary: Int, val surface: Int, val isLight: Boolean)

    // ---------- cores ----------

    /** Cor primária da imagem: vibrante/dominante, com saturação e brilho ajustados para um fundo de card. */
    fun primaryColor(path: String?): Int? {
        if (path.isNullOrBlank()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 96 && bounds.outHeight / (sample * 2) >= 96) sample *= 2
            val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null
            val palette = androidx.palette.graphics.Palette.from(bmp).maximumColorCount(24).generate()
            bmp.recycle()
            val raw = palette.getVibrantColor(
                palette.getDominantColor(palette.getMutedColor(0))
            )
            if (raw == 0) null else tuneForBackground(raw)
        } catch (_: Exception) {
            null
        }
    }

    /** Mantém o tom, mas evita cores lavadas/escuras demais para o texto ficar legível. */
    fun tuneForBackground(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[1] = hsl[1].coerceIn(0.30f, 0.85f)
        hsl[2] = hsl[2].coerceIn(0.22f, 0.72f)
        return ColorUtils.HSLToColor(hsl)
    }

    private fun isLight(color: Int) = ColorUtils.calculateLuminance(color) > 0.45

    private fun onBg(bg: Int, alpha: Int = 255): Int {
        val base = if (isLight(bg)) 0xFF14101C.toInt() else Color.WHITE
        return ColorUtils.setAlphaComponent(base, alpha)
    }

    private fun shade(bg: Int, lightnessDelta: Float, alpha: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(bg, hsl)
        hsl[2] = (hsl[2] + lightnessDelta).coerceIn(0f, 1f)
        return ColorUtils.setAlphaComponent(ColorUtils.HSLToColor(hsl), alpha)
    }

    // ---------- ícones flutuantes (mesma lógica do app) ----------

    private class Floater(
        val iconIndex: Int,
        val xFrac: Float, val yFrac: Float,
        val sizeDp: Float, val baseAlpha: Float,
        val vxDp: Float, val vyDp: Float,
        val rot0: Float, val rotSpeed: Float, val phase: Float
    )

    private val floaterCache = HashMap<String, List<Floater>>()

    private fun buildFloaters(kind: String): List<Floater> =
        synchronized(floaterCache) { floaterCache.getOrPut(kind) { makeFloaters(kind, 48) } }

    private fun makeFloaters(kind: String, count: Int): List<Floater> {
        val rnd = java.util.Random(kind.hashCode().toLong())
        return List(count) { i ->
            val ang = rnd.nextFloat() * (PI * 2).toFloat()
            val speed = 3f + rnd.nextFloat() * 9f
            val rs = (6f + rnd.nextFloat() * 18f) * (if (rnd.nextBoolean()) 1f else -1f)
            Floater(
                iconIndex = i,
                xFrac = rnd.nextFloat(), yFrac = rnd.nextFloat(),
                sizeDp = 20f + rnd.nextFloat() * 14f,
                baseAlpha = 0.14f + rnd.nextFloat() * 0.14f,
                vxDp = cos(ang) * speed, vyDp = sin(ang) * speed,
                rot0 = rnd.nextFloat() * 360f, rotSpeed = rs,
                phase = rnd.nextFloat() * (PI * 2).toFloat()
            )
        }
    }

    /** O app mede em dp (~392 dp de largura); a tela do card tem 1080 px. */
    private const val DP = W / 392f

    private fun drawFloatingIcons(
        c: Canvas, icons: List<Bitmap>, kind: String, t: Float, tint: Int, lightBg: Boolean
    ) {
        if (icons.isEmpty()) return
        val floaters = buildFloaters(kind)
        val wDp = W / DP
        val hDp = H / DP
        val count = ((wDp * hDp) / 7500f).toInt().coerceIn(24, floaters.size)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val time = if (t < 0f) 6f else t
        val tintFilter = android.graphics.PorterDuffColorFilter(tint, android.graphics.PorterDuff.Mode.SRC_ATOP)
        for (i in 0 until count) {
            val f = floaters[i]
            val bmp = icons[f.iconIndex % icons.size]
            val sz = f.sizeDp * DP * 1.35f
            val spanX = W + sz * 2f
            val spanY = H + sz * 2f
            var x = (f.xFrac * W + f.vxDp * DP * time + sz) % spanX
            var y = (f.yFrac * H + f.vyDp * DP * time + sz) % spanY
            if (x < 0f) x += spanX
            if (y < 0f) y += spanY
            x -= sz
            y -= sz
            val rot = f.rot0 + f.rotSpeed * time
            val a = f.baseAlpha * (0.8f + 0.2f * sin(time * 0.6f + f.phase)) * (if (lightBg) 1.6f else 1.3f)
            paint.alpha = (a.coerceIn(0f, 1f) * 255).toInt()
            paint.colorFilter = tintFilter
            c.save()
            c.rotate(rot, x + sz / 2f, y + sz / 2f)
            c.drawBitmap(bmp, null, RectF(x, y, x + sz, y + sz), paint)
            c.restore()
        }
        paint.colorFilter = null
    }

    // ---------- formas onduladas (estilo Rewind) ----------

    private fun scallopPath(cx: Float, cy: Float, r: Float, bumps: Int, depth: Float, rot: Float): Path {
        val p = Path()
        val steps = 160
        for (i in 0..steps) {
            val a = (i.toFloat() / steps) * (PI * 2).toFloat()
            val rr = r * (1f + depth * cos(a * bumps + rot))
            val x = cx + cos(a) * rr
            val y = cy + sin(a) * rr
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return p
    }

    private fun drawBlobs(c: Canvas, bg: Int, t: Float) {
        val time = if (t < 0f) 6f else t
        val light = isLight(bg)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        data class B(val x: Float, val y: Float, val r: Float, val n: Int, val d: Float, val spin: Float, val lift: Float)
        val blobs = listOf(
            B(0.22f, 0.10f, 520f, 9, 0.055f, 0.10f, if (light) -0.07f else 0.07f),
            B(0.95f, 0.78f, 170f, 11, 0.09f, -0.18f, if (light) -0.12f else 0.12f),
            B(0.05f, 0.88f, 330f, 8, 0.06f, 0.07f, if (light) -0.05f else 0.05f),
            B(0.10f, 0.30f, 90f, 12, 0.12f, 0.2f, if (light) -0.10f else 0.10f)
        )
        blobs.forEachIndexed { i, b ->
            val dx = sin(time * 0.35f + i * 1.7f) * 40f
            val dy = cos(time * 0.28f + i * 2.3f) * 46f
            paint.color = shade(bg, b.lift, 110)
            c.drawPath(scallopPath(b.x * W + dx, b.y * H + dy, b.r, b.n, b.d, time * b.spin + i), paint)
        }
    }

    // ---------- entrada animada ----------

    /** 0..1 com easing; [delay]/[dur] em segundos. Foto (STILL_TIME) = 1. */
    private fun enter(t: Float, delay: Float, dur: Float = 0.55f): Float {
        if (t < 0f) return 1f
        val x = ((t - delay) / dur).coerceIn(0f, 1f)
        return 1f - (1f - x) * (1f - x) * (1f - x)
    }

    // ---------- API ----------

    fun render(
        context: Context,
        bmp: Bitmap,
        model: Model,
        timeSec: Float,
        assets: Assets,
        nowPlaying: NowPlaying? = null
    ) {
        val c = Canvas(bmp)
        c.drawColor(model.background)
        drawBlobs(c, model.background, timeSec)
        drawFloatingIcons(c, assets.icons, model.icons, timeSec, onBg(model.background), isLight(model.background))

        val fg = onBg(model.background)
        val fgSoft = onBg(model.background, 190)
        when (model.layout) {
            Layout.INTRO, Layout.TEXT -> drawTextCard(c, model, timeSec, fg, fgSoft)
            Layout.HERO -> drawHero(c, model, timeSec, assets, fg, fgSoft)
            Layout.LIST -> drawList(c, model, timeSec, assets, fg, fgSoft)
            Layout.GENRES -> drawGenres(c, model, timeSec, fg)
        }
        if (nowPlaying != null && timeSec >= 0f) drawNowPlaying(c, nowPlaying, assets.disc, timeSec, fg, model.background)
        drawBrand(c, assets.brand, fg)
    }

    /** Imagens decodificadas uma vez por card (reutilizadas em todos os frames do vídeo). */
    class Assets(
        val icons: List<Bitmap>,
        val hero: Bitmap?,
        val rowImages: List<Bitmap?>,
        val brand: Bitmap? = null,
        val disc: Bitmap? = null
    ) {
        fun recycle() {
            disc?.recycle()
            brand?.recycle()
            hero?.recycle()
            rowImages.forEach { it?.recycle() }
            icons.forEach { it.recycle() }
        }
    }

    fun loadAssets(context: Context, model: Model, discArtPath: String? = null): Assets {
        val icons = RetrospectivePhotoGenerator.loadFloatIcons(context, model.icons)
        val hero = model.heroImagePath?.let { decode(it, 720) }
        val rows = model.rows.map { r -> r.imagePath?.let { decode(it, 200) } }
        // A pílula da marca é pesada de desenhar (decodifica logo/fonte); renderiza uma vez só.
        val brand = try {
            val b = Bitmap.createBitmap(BRAND_W, BRAND_H, Bitmap.Config.ARGB_8888)
            val ok = LyricsShareHelper.drawBrandPill(Canvas(b), context, BRAND_W / 2f, BRAND_H / 2f, 64f)
            if (ok) b else { b.recycle(); null }
        } catch (_: Exception) { null }
        val disc = discArtPath?.let { decode(it, 160) }
        return Assets(icons, hero, rows, brand, disc)
    }

    private fun decode(path: String, maxSide: Int): Bitmap? = try {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, b)
        var s = 1
        while (b.outWidth / (s * 2) >= maxSide && b.outHeight / (s * 2) >= maxSide) s *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
    } catch (_: Exception) {
        null
    }

    // ---------- layouts ----------

    private fun textPaint(size: Float, color: Int, bold: Boolean, align: Paint.Align) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
            textAlign = align
        }

    private fun drawTextCard(c: Canvas, m: Model, t: Float, fg: Int, fgSoft: Int) {
        val e1 = enter(t, 0.15f)
        val e2 = enter(t, 0.45f)
        val lines = m.body.split("\n")
        val first = lines.firstOrNull().orEmpty()
        val rest = lines.drop(1)
        val titleP = textPaint(84f, fg, true, Paint.Align.LEFT)
        var y = 700f
        y = drawWrapped(c, m.title, MX, y + (1f - e1) * 40f, titleP, W - 2 * MX, 100f, (e1 * 255).toInt())
        val bigP = textPaint(if (m.layout == Layout.TEXT) 64f else 46f, fg, m.layout == Layout.TEXT, Paint.Align.LEFT)
        val smallP = textPaint(40f, fgSoft, false, Paint.Align.LEFT)
        y += 30f
        y = drawWrapped(c, first, MX, y + (1f - e2) * 40f, bigP, W - 2 * MX, 78f, (e2 * 255).toInt())
        rest.forEachIndexed { i, ln ->
            val e = enter(t, 0.7f + i * 0.18f)
            y = drawWrapped(c, ln, MX, y + 6f + (1f - e) * 30f, smallP, W - 2 * MX, 60f, (e * 255).toInt())
        }
    }

    private fun drawHero(c: Canvas, m: Model, t: Float, assets: Assets, fg: Int, fgSoft: Int) {
        val eLabel = enter(t, 0.1f)
        val eImg = enter(t, 0.3f, 0.7f)
        val eName = enter(t, 0.7f)
        val label = textPaint(46f, fgSoft, false, Paint.Align.CENTER)
        label.alpha = (eLabel * 255).toInt()
        c.drawText(m.title, W / 2f, 430f, label)

        val size = 560f * (0.85f + 0.15f * eImg)
        val left = (W - size) / 2f
        val top = 520f + (560f - size) / 2f
        val rect = RectF(left, top, left + size, top + size)
        val r = if (m.heroCircle) size / 2f else 44f
        drawImageOrPlaceholder(c, assets.hero, rect, r, m.background, fg, (eImg * 255).toInt(), m.heroCircle)

        val bodyLines = m.body.split("\n")
        val nameP = textPaint(76f, fg, true, Paint.Align.CENTER)
        var y = 1230f + (1f - eName) * 40f
        y = drawWrapped(c, bodyLines.firstOrNull().orEmpty(), W / 2f, y, nameP, W - 2 * MX, 88f, (eName * 255).toInt(), 3)
        val subP = textPaint(44f, fgSoft, false, Paint.Align.CENTER)
        bodyLines.drop(1).forEachIndexed { i, ln ->
            val e = enter(t, 0.95f + i * 0.15f)
            y = drawWrapped(c, ln, W / 2f, y + 10f, subP, W - 2 * MX, 56f, (e * 255).toInt(), 1)
        }
        if (m.heroCaption.isNotBlank()) {
            val e = enter(t, 1.2f)
            val pill = textPaint(40f, fg, true, Paint.Align.CENTER)
            val w = pill.measureText(m.heroCaption) + 70f
            val pr = RectF((W - w) / 2f, y + 30f, (W + w) / 2f, y + 112f)
            val bgp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ColorUtils.setAlphaComponent(fg, (45 * e).toInt())
            }
            c.drawRoundRect(pr, 41f, 41f, bgp)
            pill.alpha = (e * 255).toInt()
            c.drawText(m.heroCaption, W / 2f, pr.centerY() + 14f, pill)
        }
    }

    private fun drawList(c: Canvas, m: Model, t: Float, assets: Assets, fg: Int, fgSoft: Int) {
        val eTitle = enter(t, 0.1f)
        val titleP = textPaint(72f, fg, true, Paint.Align.LEFT)
        titleP.alpha = (eTitle * 255).toInt()
        c.drawText(ellipsize(m.title, titleP, W - 2 * MX), MX, 420f + (1f - eTitle) * 30f, titleP)

        val rowH = 190f
        val img = 138f
        val round = m.icons == "artistas"
        m.rows.take(5).forEachIndexed { i, row ->
            val e = enter(t, 0.35f + i * 0.2f)
            val top = 520f + i * rowH + (1f - e) * 50f
            val a = (e * 255).toInt()
            val numP = textPaint(60f, fgSoft, true, Paint.Align.LEFT).apply { alpha = a }
            c.drawText("${i + 1}", MX, top + img / 2f + 22f, numP)
            val rect = RectF(MX + 100f, top, MX + 100f + img, top + img)
            val rr = if (round) img / 2f else 26f
            drawImageOrPlaceholder(c, assets.rowImages.getOrNull(i), rect, rr, m.background, fg, a, round)
            val tx = MX + 100f + img + 36f
            val maxW = W - tx - MX
            val tp = textPaint(48f, fg, true, Paint.Align.LEFT).apply { alpha = a }
            val sp = textPaint(36f, fgSoft, false, Paint.Align.LEFT).apply { alpha = a }
            val sub = buildString {
                if (row.subtitle.isNotBlank()) append(row.subtitle).append(" · ")
                append("${row.plays} plays")
            }
            val hasSub = sub.isNotBlank()
            val ty = if (hasSub) top + img / 2f - 4f else top + img / 2f + 16f
            c.drawText(ellipsize(row.title, tp, maxW), tx, ty, tp)
            if (hasSub) c.drawText(ellipsize(sub, sp, maxW), tx, ty + 52f, sp)
        }
    }

    /** Blocos escalonados (estilo “estilos favoritos”), nas cores do card, com altura pela participação. */
    private fun drawGenres(c: Canvas, m: Model, t: Float, fg: Int) {
        val eTitle = enter(t, 0.1f)
        val titleP = textPaint(72f, fg, true, Paint.Align.LEFT)
        titleP.alpha = (eTitle * 255).toInt()
        c.drawText(ellipsize(m.title, titleP, W - 2 * MX), MX, 420f + (1f - eTitle) * 30f, titleP)

        val g = m.genres.take(5)
        if (g.isEmpty()) return
        val maxP = g.maxOf { it.percent }.coerceAtLeast(1)
        val tail = 60f
        val hidden = 110f
        val rawVisible = g.map { 190f + 200f * (it.percent.toFloat() / maxP) }
        val avail = 1200f
        val scale = min(1f, avail / (rawVisible.sum() + tail))
        val visible = rawVisible.map { max(178f, it * scale) }
        val full = W - 2 * MX
        val deltas = floatArrayOf(0.16f, -0.13f, 0.10f, -0.18f, 0.22f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        var top = 520f
        g.forEachIndexed { i, gs ->
            val e = enter(t, 0.3f + i * 0.18f)
            val a = (e * 255).toInt()
            val w = full * (0.60f + 0.32f * gs.percent / maxP)
            val x0 = (if (i % 2 == 0) W - MX - w else MX) + (1f - e) * (if (i % 2 == 0) 140f else -140f)
            val h = visible[i] + if (i == g.lastIndex) tail else hidden
            val rect = RectF(x0, top, x0 + w, top + h)
            val col = shade(m.background, deltas[i], 255)
            paint.color = ColorUtils.setAlphaComponent(col, a)
            c.drawRoundRect(rect, 36f, 36f, paint)

            val pp = textPaint(68f, onBg(col, a), true, Paint.Align.LEFT)
            c.drawText("${gs.percent}%", rect.left + 40f, rect.top + 88f, pp)
            val np = textPaint(40f, onBg(col, (a * 0.85f).toInt()), false, Paint.Align.LEFT)
            c.drawText(ellipsize(gs.name, np, w - 80f), rect.left + 40f, rect.top + 142f, np)
            top += visible[i]
        }
    }

    /** Disco de vinil girando (capa no rótulo) + “Tocando agora” e o nome da música, no topo do vídeo. */
    private fun drawNowPlaying(c: Canvas, np: NowPlaying, art: Bitmap?, t: Float, fg: Int, bg: Int) {
        val e = enter(t, 0.05f, 0.6f)
        val a = (e * 255).toInt()
        val r = 56f
        val cx = MX + r
        val cy = 235f
        val vinyl = 0xFF121212.toInt()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // aro fixo para destacar o disco do fundo
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = ColorUtils.setAlphaComponent(fg, (a * 0.45f).toInt())
        c.drawCircle(cx, cy, r + 4f, p)

        c.save()
        c.rotate((t * 150f) % 360f, cx, cy) // ~2,4 s por volta
        p.style = Paint.Style.FILL
        p.color = ColorUtils.setAlphaComponent(vinyl, a)
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.6f
        p.color = ColorUtils.setAlphaComponent(Color.WHITE, (a * 0.16f).toInt())
        c.drawCircle(cx, cy, r * 0.88f, p)
        c.drawCircle(cx, cy, r * 0.74f, p)
        c.drawCircle(cx, cy, r * 0.60f, p)
        // reflexos (mostram a rotação)
        p.strokeWidth = 5f
        p.strokeCap = Paint.Cap.ROUND
        p.color = ColorUtils.setAlphaComponent(Color.WHITE, (a * 0.38f).toInt())
        val arc = RectF(cx - r * 0.92f, cy - r * 0.92f, cx + r * 0.92f, cy + r * 0.92f)
        c.drawArc(arc, 200f, 36f, false, p)
        c.drawArc(arc, 20f, 36f, false, p)

        // rótulo central: capa recortada em círculo, ou cor do texto
        val lr = r * 0.40f
        val label = RectF(cx - lr, cy - lr, cx + lr, cy + lr)
        p.style = Paint.Style.FILL
        if (art != null) {
            val path = Path().apply { addCircle(cx, cy, lr, Path.Direction.CW) }
            c.save()
            c.clipPath(path)
            val sc = max(label.width() / art.width, label.height() / art.height)
            val dw = art.width * sc
            val dh = art.height * sc
            val ip = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { alpha = a }
            c.drawBitmap(art, null, RectF(cx - dw / 2f, cy - dh / 2f, cx + dw / 2f, cy + dh / 2f), ip)
            c.restore()
        } else {
            p.color = ColorUtils.setAlphaComponent(fg, a)
            c.drawCircle(cx, cy, lr, p)
        }
        // furo + marca no rótulo (visível mesmo com capa)
        p.color = ColorUtils.setAlphaComponent(bg, a)
        c.drawCircle(cx, cy, r * 0.07f, p)
        p.color = ColorUtils.setAlphaComponent(Color.WHITE, (a * 0.9f).toInt())
        c.drawCircle(cx, cy - lr * 0.72f, r * 0.045f, p)
        c.restore()

        // textos
        val tx = cx + r + 30f
        val maxW = W - MX - tx
        val small = textPaint(26f, onBg(bg, (a * 0.75f).toInt()), false, Paint.Align.LEFT)
        small.letterSpacing = 0.08f
        c.drawText("TOCANDO AGORA", tx, cy - 12f, small)
        val line = buildString {
            append(np.title)
            if (np.artist.isNotBlank()) append(" · ").append(np.artist)
        }
        val big = textPaint(38f, onBg(bg, a), true, Paint.Align.LEFT)
        c.drawText(ellipsize(line, big, maxW), tx, cy + 38f, big)
    }

    private fun drawImageOrPlaceholder(
        c: Canvas, img: Bitmap?, rect: RectF, radius: Float, bg: Int, fg: Int, alpha: Int, circle: Boolean
    ) {
        val path = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        c.save()
        c.clipPath(path)
        if (img != null) {
            val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { this.alpha = alpha }
            val s = max(rect.width() / img.width, rect.height() / img.height)
            val dw = img.width * s
            val dh = img.height * s
            c.drawBitmap(
                img, null,
                RectF(rect.centerX() - dw / 2f, rect.centerY() - dh / 2f, rect.centerX() + dw / 2f, rect.centerY() + dh / 2f),
                p
            )
        } else {
            c.drawColor(Color.TRANSPARENT)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(fg, (alpha * 0.18f).toInt()) }
            c.drawRect(rect, p)
            // nota musical simples
            val n = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ColorUtils.setAlphaComponent(fg, (alpha * 0.7f).toInt())
                textAlign = Paint.Align.CENTER
                textSize = rect.height() * 0.55f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            c.drawText("♪", rect.centerX(), rect.centerY() + rect.height() * 0.2f, n)
        }
        c.restore()
    }

    private fun ellipsize(s: String, p: Paint, maxW: Float): String {
        if (p.measureText(s) <= maxW) return s
        var end = s.length
        while (end > 1 && p.measureText(s.substring(0, end) + "…") > maxW) end--
        return s.substring(0, end).trimEnd() + "…"
    }

    private fun drawWrapped(
        c: Canvas, text: String, x: Float, startY: Float, paint: Paint,
        maxW: Float, lineH: Float, alpha: Int, maxLines: Int = 6
    ): Float {
        val saved = paint.alpha
        paint.alpha = alpha.coerceIn(0, 255)
        val lines = mutableListOf<String>()
        var line = ""
        for (w in text.split(" ")) {
            val trial = if (line.isEmpty()) w else "$line $w"
            if (paint.measureText(trial) > maxW && line.isNotEmpty()) { lines += line; line = w } else line = trial
        }
        if (line.isNotEmpty()) lines += line
        var y = startY
        lines.take(maxLines).forEachIndexed { i, ln ->
            val out = if (i == maxLines - 1 && lines.size > maxLines) ellipsize(ln, paint, maxW) else ln
            c.drawText(out, x, y, paint)
            y += lineH
        }
        paint.alpha = saved
        return y
    }

    private fun drawBrand(c: Canvas, brand: Bitmap?, fg: Int) {
        if (brand != null) {
            c.drawBitmap(brand, W / 2f - BRAND_W / 2f, H - 120f - BRAND_H / 2f, null)
        } else {
            val p = textPaint(36f, ColorUtils.setAlphaComponent(fg, 180), true, Paint.Align.CENTER)
            c.drawText("Lyron Music Player", W / 2f, H - 100f, p)
        }
    }
}
