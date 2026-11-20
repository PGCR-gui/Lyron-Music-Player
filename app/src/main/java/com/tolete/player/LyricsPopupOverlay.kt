package com.tolete.player

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.tolete.player.data.LyricAnimation
import com.tolete.player.data.LyricLine
import com.tolete.player.data.LyricWord
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Overlay de letras sobre outros apps com karaoke e animações ELRC+.
 * Preferências: tamanho, opacidade do fundo, tema, posição bloqueável.
 */
internal object LyricsPopupOverlay {
    private const val PREFS = "tolete"
    const val KEY_LOCKED = "lyrics_popup_locked"
    private const val KEY_POS_X = "lyrics_popup_x"
    private const val KEY_POS_Y = "lyrics_popup_y"
    const val KEY_FONT_SIZE = "lyrics_popup_font_size"
    const val KEY_BG_ALPHA = "lyrics_popup_bg_alpha" // 0..100
    const val KEY_THEME = "lyrics_popup_theme"
    const val KEY_HIDE_ON_NP = "lyrics_popup_hide_on_now_playing"

    enum class PopupTheme(val id: String, val label: String) {
        DYNAMIC("dynamic", "Tema dinâmico"),
        SUNSET("sunset", "Pôr do sol"),
        OCEAN("ocean", "Oceano"),
        NEON("neon", "Neon"),
        FOREST("forest", "Floresta"),
        MONO("mono", "Mono"),
        AURORA("aurora", "Aurora")
    }

    private var windowManager: WindowManager? = null
    private var rootView: FrameLayout? = null
    private var drawView: LyricsDrawView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var dragStartX = 0
    private var dragStartY = 0
    private var touchStartRawX = 0f
    private var touchStartRawY = 0f
    private var moved = false

    fun isLocked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOCKED, false)

    fun setLocked(context: Context, locked: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCKED, locked).apply()
        applyTouchFlags(context)
    }

    fun toggleLocked(context: Context): Boolean {
        val next = !isLocked(context)
        setLocked(context, next)
        return next
    }

    fun setHideOnNowPlaying(context: Context, hide: Boolean) {
        prefs(context).edit().putBoolean(KEY_HIDE_ON_NP, hide).apply()
        if (hide) hide()
    }

    fun isHiddenOnNowPlaying(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIDE_ON_NP, false)

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun show(context: Context) {
        if (rootView != null) {
            applyTouchFlags(context)
            applyChrome(context)
            return
        }
        val appContext = context.applicationContext
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val density = appContext.resources.displayMetrics.density
        val p = prefs(appContext)

        val drawer = LyricsDrawView(appContext)
        val root = FrameLayout(appContext).apply {
            val padH = (16 * density).toInt()
            val padV = (12 * density).toInt()
            setPadding(padH, padV, padH, padV)
            addView(
                drawer,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        applyBackground(root, appContext)

        val widthFraction = 0.90f
        val width = (appContext.resources.displayMetrics.widthPixels * widthFraction).toInt()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val locked = p.getBoolean(KEY_LOCKED, false)
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            baseFlags(locked),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = p.getInt(KEY_POS_X, ((appContext.resources.displayMetrics.widthPixels - width) / 2).coerceAtLeast(0))
            y = p.getInt(KEY_POS_Y, (72 * density).toInt())
        }

        root.setOnTouchListener { _, event ->
            if (isLocked(appContext)) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = params.x
                    dragStartY = params.y
                    touchStartRawX = event.rawX
                    touchStartRawY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartRawX).toInt()
                    val dy = (event.rawY - touchStartRawY).toInt()
                    if (abs(dx) > 4 || abs(dy) > 4) moved = true
                    params.x = dragStartX + dx
                    params.y = (dragStartY + dy).coerceAtLeast(0)
                    try { wm.updateViewLayout(root, params) } catch (_: Throwable) { }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (moved) {
                        prefs(appContext).edit()
                            .putInt(KEY_POS_X, params.x)
                            .putInt(KEY_POS_Y, params.y)
                            .apply()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            wm.addView(root, params)
            windowManager = wm
            rootView = root
            drawView = drawer
            layoutParams = params
        } catch (_: Throwable) {
            windowManager = null
            rootView = null
            drawView = null
            layoutParams = null
        }
    }

    private fun baseFlags(locked: Boolean): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        flags = if (locked) {
            flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
        return flags
    }

    private fun applyTouchFlags(context: Context) {
        val root = rootView ?: return
        val params = layoutParams ?: return
        val wm = windowManager ?: return
        params.flags = baseFlags(isLocked(context))
        try { wm.updateViewLayout(root, params) } catch (_: Throwable) { }
    }

    fun applyChrome(context: Context) {
        rootView?.let { applyBackground(it, context) }
        drawView?.invalidate()
    }

    private fun applyBackground(root: FrameLayout, context: Context) {
        val p = prefs(context)
        val alphaPct = p.getInt(KEY_BG_ALPHA, 90).coerceIn(0, 100)
        val alpha = (alphaPct * 255 / 100).coerceIn(0, 255)
        val theme = p.getString(KEY_THEME, PopupTheme.DYNAMIC.id) ?: PopupTheme.DYNAMIC.id
        val density = context.resources.displayMetrics.density
        val colors = themeColors(theme, p)
        val bg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.argb(alpha, Color.red(colors.first), Color.green(colors.first), Color.blue(colors.first)),
                Color.argb(alpha, Color.red(colors.second), Color.green(colors.second), Color.blue(colors.second))
            )
        ).apply {
            cornerRadius = 20 * density
            setStroke((1 * density).toInt(), Color.argb(min(180, alpha + 40), 255, 255, 255))
        }
        root.background = bg
        root.elevation = 10 * density
    }

    /** Cores de fundo do tema (sem alpha). */
    fun themeColors(themeId: String, prefs: SharedPreferences): Pair<Int, Int> {
        return when (themeId) {
            PopupTheme.SUNSET.id -> 0xFF3B1D2A.toInt() to 0xFF6B2D1F.toInt()
            PopupTheme.OCEAN.id -> 0xFF0D2137.toInt() to 0xFF123C5A.toInt()
            PopupTheme.NEON.id -> 0xFF1A0A2E.toInt() to 0xFF2D0A3A.toInt()
            PopupTheme.FOREST.id -> 0xFF0F2418.toInt() to 0xFF163528.toInt()
            PopupTheme.MONO.id -> 0xFF1A1A1A.toInt() to 0xFF2A2A2A.toInt()
            PopupTheme.AURORA.id -> 0xFF0E1B2A.toInt() to 0xFF1A3A3A.toInt()
            else -> {
                // Dinâmico: usa cor primária das letras + base escura
                val primary = prefs.getInt("lyrics_primary_color", 0xFFFFFFFF.toInt())
                val r = (Color.red(primary) * 0.25f).toInt().coerceIn(10, 80)
                val g = (Color.green(primary) * 0.25f).toInt().coerceIn(10, 80)
                val b = (Color.blue(primary) * 0.30f).toInt().coerceIn(15, 90)
                Color.rgb(r, g, b) to Color.rgb(
                    (r * 1.3f).toInt().coerceAtMost(100),
                    (g * 1.2f).toInt().coerceAtMost(100),
                    (b * 1.4f).toInt().coerceAtMost(120)
                )
            }
        }
    }

    fun themeTextColors(themeId: String, prefs: SharedPreferences): Triple<Int, Int, Int> {
        // primary (linha ativa / karaoke), secondary (linhas inativas), translation
        // Secondary bem mais escuro/transparente para contrastar com a linha ativa,
        // exceto NEON que já diferencia bem.
        return when (themeId) {
            PopupTheme.SUNSET.id -> Triple(0xFFFFF3E0.toInt(), 0x55FFAB40.toInt(), 0xFFFFAB91.toInt())
            PopupTheme.OCEAN.id -> Triple(0xFFE1F5FE.toInt(), 0x554FC3F7.toInt(), 0xFF81D4FA.toInt())
            PopupTheme.NEON.id -> Triple(0xFFE040FB.toInt(), 0x9969F0AE.toInt(), 0xFF18FFFF.toInt())
            PopupTheme.FOREST.id -> Triple(0xFFE8F5E9.toInt(), 0x554CAF50.toInt(), 0xFFA5D6A7.toInt())
            PopupTheme.MONO.id -> Triple(0xFFFFFFFF.toInt(), 0x44FFFFFF, 0xFFBDBDBD.toInt())
            PopupTheme.AURORA.id -> Triple(0xFFCCFF90.toInt(), 0x4480D8FF.toInt(), 0xFFEA80FC.toInt())
            else -> {
                val primary = prefs.getInt("lyrics_primary_color", 0xFFFFFFFF.toInt())
                // Secondary bem mais apagado que o default antigo (0x66) para contraste
                val secondary = prefs.getInt("lyrics_secondary_color", 0x44FFFFFF)
                val translation = prefs.getInt("lyrics_translation_color", 0xFFB0BEC5.toInt())
                Triple(primary, secondary, translation)
            }
        }
    }

    data class PopupFrame(
        val main: LyricLine?,
        val translation: LyricLine?,
        val backing: LyricLine?,
        val positionMs: Long,
        val primaryColor: Int,
        val secondaryColor: Int,
        val translationColor: Int,
        val backingColor: Int,
        val fontSizeSp: Float,
        val mainTypeface: Typeface? = null,
        val translationTypeface: Typeface? = null,
        val backingTypeface: Typeface? = null,
        /** Contorno das letras (configurado em Aparência). Largura em dp; 0 = sem contorno. */
        val borderColor: Int = 0xFF000000.toInt(),
        val borderWidthDp: Float = 0f,
        val backingBorderColor: Int = borderColor,
        val backingBorderWidthDp: Float = borderWidthDp,
        val translationBorderColor: Int = borderColor,
        val translationBorderWidthDp: Float = borderWidthDp
    )

    fun updateFrame(frame: PopupFrame) {
        showIfNeeded()
        drawView?.setFrame(frame)
    }

    private fun showIfNeeded() {
        // no-op; caller calls show() first
    }

    /** Compatibilidade com chamadas antigas. */
    fun update(text: String) {
        val line = LyricLine(timeMs = 0L, text = text)
        updateFrame(
            PopupFrame(
                main = line,
                translation = null,
                backing = null,
                positionMs = 0L,
                primaryColor = 0xFFFFFFFF.toInt(),
                secondaryColor = 0x66FFFFFF,
                translationColor = 0xFFB0BEC5.toInt(),
                backingColor = 0xFFCE93D8.toInt(),
                fontSizeSp = 18f
            )
        )
    }

    fun hide() {
        val view = rootView ?: return
        try { windowManager?.removeView(view) } catch (_: Throwable) { }
        drawView?.stop()
        rootView = null
        drawView = null
        windowManager = null
        layoutParams = null
    }

    fun resolveTypeface(context: Context, path: String?): Typeface? {
        if (path.isNullOrBlank() || path == "system") return null
        return try {
            if (path.startsWith("builtin:")) {
                val id = path.removePrefix("builtin:")
                val aliases = mapOf(
                    "juliametal" to "juliametal_regular",
                    "buba" to "buba_outline",
                    "grim_creeper" to "grim_creeper",
                    "terminal_16" to "terminal_16_unicode",
                    "better_vcr" to "better_vcr",
                    "matcha_style" to "matcha_style",
                    "gak_tahu" to "gak_tahu",
                    "unifontexmono" to "unifontexmono",
                    "shareit" to "shareit",
                    "pixelise" to "pixelise",
                    "ryga" to "ryga"
                )
                val name = aliases[id] ?: id
                val rid = context.resources.getIdentifier(name, "font", context.packageName)
                if (rid != 0 && Build.VERSION.SDK_INT >= 26) context.resources.getFont(rid) else null
            } else {
                val f = File(path)
                if (f.isFile) Typeface.createFromFile(f) else null
            }
        } catch (_: Throwable) {
            null
        }
    }

    /** View que desenha karaoke + animações e se redesenha continuamente. */
    private class LyricsDrawView(context: Context) : View(context) {
        private var frame: PopupFrame? = null
        private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
        }
        private var running = false
        private val tick = object : Runnable {
            override fun run() {
                if (!running) return
                invalidate()
                postOnAnimation(this)
            }
        }

        fun setFrame(f: PopupFrame) {
            frame = f
            if (!running) {
                running = true
                postOnAnimation(tick)
            }
            requestLayout()
            invalidate()
        }

        fun stop() {
            running = false
            removeCallbacks(tick)
        }

        private fun makeTextLayout(
            text: String,
            maxWidth: Int,
            sizePx: Float,
            typeface: Typeface?,
            textColor: Int = Color.WHITE
        ): StaticLayout {
            paint.textSize = sizePx
            paint.typeface = typeface ?: Typeface.DEFAULT
            paint.textAlign = Paint.Align.LEFT
            paint.color = textColor
            paint.alpha = 255
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.setShadowLayer(0f, 0f, 0f, 0)
            return StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth.coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .build()
        }

        private fun wrapWordParts(parts: List<WordPart>, maxWidth: Float): List<List<WordPart>> {
            if (parts.isEmpty()) return listOf(emptyList())
            val rows = mutableListOf<MutableList<WordPart>>()
            var row = mutableListOf<WordPart>()
            var rowWidth = 0f
            for (part in parts) {
                val partWidth = part.width.coerceAtMost(maxWidth)
                if (row.isNotEmpty() && rowWidth + partWidth > maxWidth) {
                    rows += row
                    row = mutableListOf()
                    rowWidth = 0f
                }
                row += part.copy(width = partWidth)
                rowWidth += partWidth
            }
            if (row.isNotEmpty()) rows += row
            return rows.ifEmpty { listOf(emptyList()) }
        }

        private fun measuredLineHeight(
            line: LyricLine?, sizePx: Float, typeface: Typeface?, maxWidth: Int, positionMs: Long
        ): Float {
            if (line == null || line.text.isBlank()) return 0f
            if (!line.hasSyllableSync) {
                return makeTextLayout(line.text, maxWidth, sizePx, typeface, Color.WHITE).height.toFloat()
            }
            paint.textSize = sizePx
            paint.typeface = typeface ?: Typeface.DEFAULT
            val parts = buildWordParts(line, positionMs, Color.WHITE, Color.WHITE)
            return wrapWordParts(parts, maxWidth.toFloat()).size * sizePx * 1.35f
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val f = frame
            val w = MeasureSpec.getSize(widthMeasureSpec)
            if (f == null) {
                setMeasuredDimension(w, 0)
                return
            }
            val density = resources.displayMetrics.density
            val mainSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, f.fontSizeSp, resources.displayMetrics)
            val trSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, (f.fontSizeSp - 3f).coerceAtLeast(12f), resources.displayMetrics)
            val bgSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, (f.fontSizeSp - 4f).coerceAtLeast(11f), resources.displayMetrics)
            val maxTextWidth = (w - 20 * density).toInt().coerceAtLeast(1)
            var h = 0f
            h += measuredLineHeight(f.backing?.takeIf { it.text.isNotBlank() }, bgSize, f.backingTypeface, maxTextWidth, f.positionMs)
            if (f.backing?.text?.isNotBlank() == true && f.main?.text?.isNotBlank() == true) h += bgSize * 0.35f
            h += measuredLineHeight(f.main?.takeIf { it.text.isNotBlank() }, mainSize, f.mainTypeface, maxTextWidth, f.positionMs)
            if (f.main?.text?.isNotBlank() == true && f.translation?.text?.isNotBlank() == true) h += mainSize * 0.4f
            h += measuredLineHeight(f.translation?.takeIf { it.text.isNotBlank() }, trSize, f.translationTypeface, maxTextWidth, f.positionMs)
            h += 8 * density
            setMeasuredDimension(w, max(1, h.toInt()))
        }

        override fun onDraw(canvas: Canvas) {
            val f = frame ?: return
            val density = resources.displayMetrics.density
            val maxTextWidth = (width - 20 * density).coerceAtLeast(1f)
            val mainSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, f.fontSizeSp, resources.displayMetrics)
            val trSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, (f.fontSizeSp - 3f).coerceAtLeast(12f), resources.displayMetrics)
            val bgSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, (f.fontSizeSp - 4f).coerceAtLeast(11f), resources.displayMetrics)
            var top = paddingTop.toFloat()
            f.backing?.takeIf { it.text.isNotBlank() }?.let { line ->
                top += drawLine(canvas, line, f.positionMs, f.backingColor, Color.argb(120, 255, 255, 255), bgSize, f.backingTypeface, width / 2f, top, maxTextWidth, f.backingBorderColor, f.backingBorderWidthDp)
                if (f.main?.text?.isNotBlank() == true) top += bgSize * 0.35f
            }
            f.main?.takeIf { it.text.isNotBlank() }?.let { line ->
                top += drawLine(canvas, line, f.positionMs, f.primaryColor, f.secondaryColor, mainSize, f.mainTypeface, width / 2f, top, maxTextWidth, f.borderColor, f.borderWidthDp)
                if (f.translation?.text?.isNotBlank() == true) top += mainSize * 0.4f
            }
            f.translation?.takeIf { it.text.isNotBlank() }?.let { line ->
                drawLine(canvas, line, f.positionMs, f.translationColor, Color.argb(100, Color.red(f.translationColor), Color.green(f.translationColor), Color.blue(f.translationColor)), trSize, f.translationTypeface, width / 2f, top, maxTextWidth, f.translationBorderColor, f.translationBorderWidthDp)
            }
        }

        private fun drawLine(
            canvas: Canvas,
            line: LyricLine,
            positionMs: Long,
            primary: Int,
            secondary: Int,
            textSizePx: Float,
            typeface: Typeface?,
            centerX: Float,
            top: Float,
            maxWidth: Float,
            outlineColor: Int,
            outlineDp: Float
        ): Float {
            val strokeColor = outlineColor
            val strokePx = outlineDp * resources.displayMetrics.density * 2f
            paint.textSize = textSizePx
            paint.typeface = typeface ?: Typeface.DEFAULT
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.setShadowLayer(0f, 0f, 0f, 0)

            if (!line.hasSyllableSync) {
                val layout = makeTextLayout(line.text, maxWidth.toInt(), textSizePx, typeface, primary)
                canvas.save()
                canvas.translate(centerX - layout.width / 2f, top)
                if (strokePx > 0f) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = strokePx
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.color = strokeColor
                    layout.draw(canvas)
                    paint.style = Paint.Style.FILL
                    paint.strokeWidth = 0f
                    paint.color = primary
                }
                layout.draw(canvas)
                canvas.restore()
                paint.textAlign = Paint.Align.CENTER
                return layout.height.toFloat()
            }

            paint.textAlign = Paint.Align.CENTER
            val rows = wrapWordParts(buildWordParts(line, positionMs, primary, secondary), maxWidth)
            val rowHeight = textSizePx * 1.35f
            val nowAnim = SystemClock.uptimeMillis()
            rows.forEachIndexed { rowIndex, row ->
                val totalW = row.sumOf { it.width.toDouble() }.toFloat()
                var x = centerX - totalW / 2f
                val baseline = top + rowIndex * rowHeight - paint.fontMetrics.ascent
                for (part in row) {
                    val word = part.word
                    val elapsed = if (word.timeMs >= 0L && positionMs >= word.timeMs) (positionMs - word.timeMs).toFloat() else -1f
                    val started = elapsed >= 0f
                    var drawX = x + part.width / 2f
                    var drawY = baseline
                    var scale = 1f
                    var alpha = 255
                    var color = part.color
                    var shadowOn = false
                    if (started) {
                        when (word.animation) {
                            LyricAnimation.POP -> { val k = (elapsed / 280f).coerceIn(0f, 1f); scale = 1f + 0.35f * sin((1f - k) * Math.PI).toFloat() }
                            LyricAnimation.VIB -> if (elapsed < 450f) drawX += sin(elapsed / 30f) * 3.5f
                            LyricAnimation.WAV -> drawY += sin(elapsed / 180f + word.timeMs / 100f) * (textSizePx * 0.18f)
                            LyricAnimation.SLI -> { val k = (elapsed / 320f).coerceIn(0f, 1f); drawY += (1f - k) * textSizePx * 0.6f; alpha = (k * 255).toInt() }
                            LyricAnimation.ZOU -> { val k = (elapsed / 300f).coerceIn(0f, 1f); scale = 1.55f - 0.55f * (1f - (1f - k) * (1f - k)) }
                            LyricAnimation.ZIN -> { val k = (elapsed / 300f).coerceIn(0f, 1f); scale = 0.55f + 0.45f * (1f - (1f - k) * (1f - k)) }
                            LyricAnimation.DED -> alpha = when { elapsed < 1000f -> 255; elapsed < 1200f -> ((1f - (elapsed - 1000f) / 200f) * 255).toInt().coerceIn(0, 255); else -> 0 }
                            LyricAnimation.GLO -> { val k = ((elapsed / 600f) % 2f).let { if (it > 1f) 2f - it else it }; color = lerpColor(primary, secondary, k) }
                            LyricAnimation.RNB -> { val hue = ((nowAnim / 12L) + word.timeMs / 10L) % 360L; color = Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.75f, 1f)) }
                            LyricAnimation.FLA -> if (elapsed < 220f) color = lerpColor(primary, Color.WHITE, (1f - elapsed / 220f) * 0.85f)
                            LyricAnimation.SHD -> if (elapsed < 500f) { shadowOn = true; paint.setShadowLayer(12f, 0f, 4f, Color.argb(180, 0, 0, 0)) }
                            LyricAnimation.ECO -> { paint.color = Color.argb(80, Color.red(color), Color.green(color), Color.blue(color)); canvas.save(); canvas.translate(4f, 2f); canvas.drawText(part.text, drawX, drawY, paint); canvas.restore() }
                            else -> Unit
                        }
                    }
                    val finalSize = if (word.animation == LyricAnimation.DES && started) textSizePx * (1f - 0.25f * (elapsed / 400f).coerceIn(0f, 1f)) else textSizePx
                    paint.textSize = finalSize
                    if (strokePx > 0f && alpha > 0) {
                        // Contorno primeiro (sem a sombra do efeito SHD), preenchimento por cima.
                        if (shadowOn) paint.clearShadowLayer()
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = strokePx
                        paint.strokeJoin = Paint.Join.ROUND
                        paint.color = strokeColor
                        paint.alpha = (Color.alpha(strokeColor) * (alpha / 255f) * (Color.alpha(color) / 255f)).toInt().coerceIn(0, 255)
                        if (scale != 1f) { canvas.save(); canvas.scale(scale, scale, drawX, drawY); canvas.drawText(part.text, drawX, drawY, paint); canvas.restore() }
                        else canvas.drawText(part.text, drawX, drawY, paint)
                        paint.style = Paint.Style.FILL
                        paint.strokeWidth = 0f
                        if (shadowOn) paint.setShadowLayer(12f, 0f, 4f, Color.argb(180, 0, 0, 0))
                    }
                    paint.color = color
                    paint.alpha = alpha
                    if (scale != 1f) { canvas.save(); canvas.scale(scale, scale, drawX, drawY); canvas.drawText(part.text, drawX, drawY, paint); canvas.restore() }
                    else canvas.drawText(part.text, drawX, drawY, paint)
                    paint.setShadowLayer(0f, 0f, 0f, 0)
                    paint.alpha = 255
                    paint.textSize = textSizePx
                    x += part.width
                }
            }
            return rows.size * rowHeight
        }

        private data class WordPart(
            val word: LyricWord,
            val text: String,
            val width: Float,
            val color: Int
        )

        private fun buildWordParts(line: LyricLine, positionMs: Long, primary: Int, secondary: Int): List<WordPart> {
            val out = ArrayList<WordPart>(line.words.size)
            line.words.forEachIndexed { index, word ->
                var text = word.text
                if (word.animation == LyricAnimation.TYP && word.timeMs <= positionMs) {
                    val elapsed = (positionMs - word.timeMs).toFloat()
                    val chars = text.length
                    val shown = if (chars == 0) 0 else ((elapsed / 40f).toInt()).coerceIn(0, chars)
                    text = text.take(shown)
                }
                // karaoke color with partial progress if endMs known
                val active = word.timeMs <= positionMs
                val color = if (active) {
                    if (word.endMs > word.timeMs && positionMs < word.endMs) {
                        val t = ((positionMs - word.timeMs).toFloat() / (word.endMs - word.timeMs).toFloat()).coerceIn(0f, 1f)
                        lerpColor(secondary, primary, 0.35f + 0.65f * t)
                    } else primary
                } else secondary
                val w = paint.measureText(if (text.isEmpty()) word.text else text) +
                    if (index < line.words.lastIndex) {
                        val next = line.words[index + 1]
                        if (!next.isSyllable && !word.text.endsWith(" ") && !next.text.startsWith(" ")) paint.measureText(" ") else 0f
                    } else 0f
                val spaced = if (index < line.words.lastIndex) {
                    val next = line.words[index + 1]
                    if (!next.isSyllable && !word.text.endsWith(" ") && !next.text.startsWith(" ")) "$text " else text
                } else text
                out.add(WordPart(word, spaced, paint.measureText(spaced).coerceAtLeast(w * 0.5f), color))
            }
            return out
        }

        private fun lerpColor(a: Int, b: Int, t: Float): Int {
            val u = t.coerceIn(0f, 1f)
            val ar = Color.red(a); val ag = Color.green(a); val ab = Color.blue(a); val aa = Color.alpha(a)
            val br = Color.red(b); val bg = Color.green(b); val bb = Color.blue(b); val ba = Color.alpha(b)
            return Color.argb(
                (aa + (ba - aa) * u).toInt(),
                (ar + (br - ar) * u).toInt(),
                (ag + (bg - ag) * u).toInt(),
                (ab + (bb - ab) * u).toInt()
            )
        }
    }
}
