package com.tolete.player

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
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
import java.util.Random
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
    /** Letras Expressivas: palavras soltas em posições aleatórias (só para letras palavra por palavra). */
    const val KEY_EXPRESSIVE = "lyrics_popup_expressive"
    /** Fonte de destaque ({des}), configurada em Aparência. */
    const val KEY_EMPHASIS_FONT = "lyrics_emphasis_font_path"

    /** True enquanto a tela Tocando agora está aberta no app (o popup se esconde só nela). */
    @Volatile
    var nowPlayingOpen: Boolean = false

    /** Onda triangular 0..1..0 com período 2 (usada pelo tremor do {pop}, igual à tela Tocando agora). */
    private fun tri(x: Float): Float {
        val m = x % 2f
        return if (m > 1f) 2f - m else m
    }

    /** Quanto a palavra {des} cresce em relação ao texto normal (igual à tela Tocando agora e ao vídeo). */
    private const val DES_SCALE = 1.24f
    /** Tempo (ms) que o destaque leva para crescer até o tamanho final (igual ao vídeo). */
    private const val DES_GROW_MS = 160f

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
    private var expressiveRoot: ExpressiveView? = null
    private var expressiveWm: WindowManager? = null
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

    fun isExpressive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_EXPRESSIVE, false)

    fun setExpressive(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_EXPRESSIVE, enabled).apply()
        // Se o popup está na tela, atualiza o texto da notificação de controle.
        if (rootView != null || expressiveRoot != null) LyricsPopupNotifier.show(context)
    }

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
        val translationBorderWidthDp: Float = borderWidthDp,
        /** Fonte da palavra em destaque ({des}); null = mesma da linha. */
        val emphasisTypeface: Typeface? = null
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


    // ---------------------------------------------------------------------------------------------
    // Letras Expressivas
    // ---------------------------------------------------------------------------------------------

    /**
     * Dados de um quadro das Letras Expressivas. Não há linha de tradução aqui de propósito: as
     * Letras Expressivas nunca exibem traduções ({tr}).
     */
    data class ExpressiveFrame(
        /** Identifica a faixa (muda de música = limpa a tela e sorteia tudo de novo). */
        val trackKey: String,
        val main: LyricLine?,
        val backing: LyricLine?,
        val positionMs: Long,
        val primaryColor: Int,
        val secondaryColor: Int,
        val backingColor: Int,
        val fontSizeSp: Float,
        val mainTypeface: Typeface? = null,
        val backingTypeface: Typeface? = null,
        val emphasisTypeface: Typeface? = null,
        val borderColor: Int = 0xFF000000.toInt(),
        val borderWidthDp: Float = 0f,
        val backingBorderColor: Int = borderColor,
        val backingBorderWidthDp: Float = borderWidthDp
    )

    /**
     * True se a letra tem tempo palavra por palavra (ELRC, TTML, {sil}...). Linhas de tradução não
     * contam, e uma linha em que só a primeira palavra tem tempo (letra sincronizada por linha com
     * marcadores de efeito) também não é "word by word".
     */
    fun isWordByWord(lines: List<LyricLine>): Boolean =
        lines.any { l -> !l.isTranslation && l.words.count { it.timeMs >= 0L } >= 2 }

    /** Mostra a janela de tela cheia das Letras Expressivas (toques sempre atravessam). */
    fun showExpressive(context: Context) {
        if (expressiveRoot != null) return
        val appContext = context.applicationContext
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val view = ExpressiveView(appContext)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        try {
            wm.addView(view, params)
            expressiveRoot = view
            expressiveWm = wm
        } catch (_: Throwable) {
            expressiveRoot = null
            expressiveWm = null
        }
    }

    fun updateExpressive(frame: ExpressiveFrame) {
        expressiveRoot?.setFrame(frame)
    }

    fun hide() {
        hideSimple()
        hideExpressive()
    }

    /** Esconde só o popup simples (caixa com a linha atual). */
    fun hideSimple() {
        val view = rootView ?: return
        try { windowManager?.removeView(view) } catch (_: Throwable) { }
        drawView?.stop()
        rootView = null
        drawView = null
        windowManager = null
        layoutParams = null
    }

    /** Esconde só as Letras Expressivas (tela cheia). */
    fun hideExpressive() {
        val view = expressiveRoot ?: return
        try { expressiveWm?.removeView(view) } catch (_: Throwable) { }
        view.stop()
        expressiveRoot = null
        expressiveWm = null
    }

    /** True se as Letras Expressivas estão na tela agora. */
    fun isExpressiveShowing(): Boolean = expressiveRoot != null

    private val typefaceCache = java.util.concurrent.ConcurrentHashMap<String, Typeface>()

    /** Versão com cache: o serviço chama isto a cada ~50 ms e recriar o Typeface do arquivo toda vez é caro. */
    fun resolveTypeface(context: Context, path: String?): Typeface? {
        if (path.isNullOrBlank() || path == "system") return null
        val key = if (path.startsWith("builtin:")) path else "$path@${File(path).lastModified()}"
        typefaceCache[key]?.let { return it }
        val tf = resolveTypefaceUncached(context, path)
        if (tf != null) typefaceCache[key] = tf
        return tf
    }

    private fun resolveTypefaceUncached(context: Context, path: String): Typeface? {
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


    // ---------------------------------------------------------------------------------------------
    // View das Letras Expressivas
    // ---------------------------------------------------------------------------------------------

    /** Sílaba com tempo próprio dentro de uma palavra (alimenta o preenchimento secundária → primária). */
    private class XSyl(val text: String, var startMs: Long, var endMs: Long)

    /** Palavra (ou palavra + sílabas coladas) já posicionada na tela. */
    private class XWord(
        val text: String,
        val startMs: Long,
        val endMs: Long,
        val animation: LyricAnimation,
        val cx: Float,
        val cy: Float,
        val rotation: Float,
        val sizePx: Float,
        /** Só preenchida em palavras com 2+ sílabas. */
        val syl: List<XSyl>
    )

    /** Uma linha da letra inteira espalhada na tela. */
    private class XStage(
        val key: String,
        val isBacking: Boolean,
        val words: List<XWord>,
        val lineEndMs: Long,
        val rects: List<RectF>
    ) {
        var fadeStartUptime = -1L
        /** Opacidade da pilha no instante em que ela saiu de cena (a saída parte dela, não de 100%). */
        var fadeFromAlpha = 1f
    }

    private class XGroup(var text: String, var startMs: Long, var endMs: Long, var anim: LyricAnimation) {
        val syl = ArrayList<XSyl>()
    }

    /**
     * Tela cheia, transparente e sem toque. Cada linha da letra vira uma "pilha": as palavras surgem
     * no tempo delas, em posições aleatórias (uma abaixo da outra), com tamanho e rotação próprios.
     * Ao começar a próxima linha, a anterior some e a tela é limpa.
     */
    private class ExpressiveView(context: Context) : View(context) {
        private var frame: ExpressiveFrame? = null
        private var frameUptime = 0L
        private val density = resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
        private var running = false
        private var lastTrackKey: String? = null
        private var mainStage: XStage? = null
        private var backingStage: XStage? = null
        private val leaving = ArrayList<XStage>()

        private val tick = object : Runnable {
            override fun run() {
                if (!running) return
                invalidate()
                if (mainStage != null || backingStage != null || leaving.isNotEmpty()) postOnAnimation(this)
                else postDelayed(this, 100L)
            }
        }

        fun setFrame(f: ExpressiveFrame) {
            frame = f
            frameUptime = SystemClock.uptimeMillis()
            if (lastTrackKey != f.trackKey) {
                lastTrackKey = f.trackKey
                retire(mainStage)
                retire(backingStage)
                mainStage = null
                backingStage = null
            }
            if (!running) {
                running = true
                postOnAnimation(tick)
            }
            invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            // Rotação de tela etc.: as posições sorteadas dependem do tamanho, então refaz a linha atual.
            if (oldw != 0 || oldh != 0) {
                mainStage = null
                backingStage = null
            }
        }

        fun stop() {
            running = false
            removeCallbacks(tick)
            leaving.clear()
            mainStage = null
            backingStage = null
        }

        private fun currentPos(): Long {
            val f = frame ?: return 0L
            return f.positionMs + (SystemClock.uptimeMillis() - frameUptime).coerceIn(0L, 150L)
        }

        private fun retire(stage: XStage?) {
            if (stage == null || stage.fadeStartUptime >= 0L) return
            // Se a linha já tinha sumido (passou do fim dela), não há o que desvanecer: some de vez.
            // Antes ela voltava a 100% de opacidade no instante da troca e só então desvanecia.
            val a = stageAlpha(stage, currentPos())
            if (a <= 0f) return
            stage.fadeStartUptime = SystemClock.uptimeMillis()
            stage.fadeFromAlpha = a
            leaving.add(stage)
        }

        private fun keyOf(line: LyricLine) = "${line.timeMs}|${line.text.hashCode()}"

        /** Cria/troca as pilhas quando a linha atual muda (feito no onDraw, quando o tamanho da tela já é conhecido). */
        private fun syncStages(f: ExpressiveFrame) {
            val m = f.main?.takeIf { it.text.isNotBlank() }
            val mKey = m?.let { keyOf(it) }
            if (mainStage?.key != mKey) {
                retire(mainStage)
                mainStage = m?.let { buildStage(f, it, false, emptyList()) }
            }
            val b = f.backing?.takeIf { it.text.isNotBlank() }
            val bKey = b?.let { keyOf(it) }
            if (backingStage?.key != bKey) {
                retire(backingStage)
                backingStage = b?.let { buildStage(f, it, true, mainStage?.rects ?: emptyList()) }
            }
        }

        /** Junta sílabas ({sil}) na palavra anterior e resolve os tempos de cada palavra. */
        private fun groupWords(line: LyricLine): List<XGroup> {
            val out = ArrayList<XGroup>()
            val src = if (line.words.isNotEmpty()) line.words
            else line.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                .map { LyricWord(timeMs = -1L, text = it) }
            for (w in src) {
                if (w.text.isBlank()) continue
                val last = out.lastOrNull()
                if (w.isSyllable && last != null) {
                    last.text += w.text.trim()
                    last.syl.add(XSyl(w.text.trim(), w.timeMs, w.endMs))
                    if (w.endMs > last.endMs) last.endMs = w.endMs
                    if (last.anim == LyricAnimation.NONE) last.anim = w.animation
                } else {
                    val g = XGroup(w.text.trim(), w.timeMs, w.endMs, w.animation)
                    g.syl.add(XSyl(w.text.trim(), w.timeMs, w.endMs))
                    out.add(g)
                }
            }
            if (out.isEmpty()) return out
            val lineStart = if (line.timeMs >= 0L) line.timeMs else 0L
            if (out.none { it.startMs >= 0L }) {
                // Linha sem tempo por palavra dentro de uma letra word by word: distribui pelo tamanho das palavras.
                val span = if (line.endMs > lineStart) line.endMs - lineStart else 2600L
                val usable = (span * 0.8f).toLong().coerceAtLeast(400L)
                val totalChars = out.sumOf { it.text.length.coerceAtLeast(1) }
                var acc = 0
                for (g in out) {
                    g.startMs = lineStart + usable * acc / totalChars
                    acc += g.text.length.coerceAtLeast(1)
                }
            } else {
                var prev = lineStart
                for (g in out) {
                    if (g.startMs < 0L) g.startMs = prev
                    prev = g.startMs
                }
            }
            // Tempos das sílabas (só palavras com 2+ sílabas ganham o preenchimento).
            for (g in out) {
                if (g.syl.size < 2) { g.syl.clear(); continue }
                var prevStart = g.startMs
                for (s in g.syl) {
                    if (s.startMs < 0L) s.startMs = prevStart
                    prevStart = s.startMs
                }
                for (i in g.syl.indices) {
                    val s = g.syl[i]
                    if (s.endMs <= s.startMs) {
                        s.endMs = if (i < g.syl.lastIndex) g.syl[i + 1].startMs else max(g.endMs, s.startMs + 450L)
                    }
                    if (s.endMs <= s.startMs) s.endMs = s.startMs + 1L
                }
                g.endMs = max(g.endMs, g.syl.last().endMs)
            }
            return out
        }

        private fun statusBarHeight(): Float {
            val id = resources.getIdentifier("status_bar_height", "dimen", "android")
            return if (id > 0) resources.getDimensionPixelSize(id).toFloat() else 24f * density
        }

        private fun overlapArea(a: RectF, b: RectF): Float {
            val w = min(a.right, b.right) - max(a.left, b.left)
            val h = min(a.bottom, b.bottom) - max(a.top, b.top)
            return if (w > 0f && h > 0f) w * h else 0f
        }

        private fun buildStage(f: ExpressiveFrame, line: LyricLine, isBacking: Boolean, avoid: List<RectF>): XStage {
            val key = keyOf(line)
            val groups = groupWords(line)
            if (groups.isEmpty()) return XStage(key, isBacking, emptyList(), 0L, emptyList())

            val screenW = width.toFloat()
            val screenH = height.toFloat()
            // Sorteio determinístico por faixa + linha: a mesma linha sempre cai igual (ex.: ao voltar a música).
            val rnd = Random(
                f.trackKey.hashCode().toLong() * 1_000_003L + line.timeMs * 31L +
                    line.text.hashCode() + if (isBacking) 7L else 0L
            )
            val baseFace: Typeface = (if (isBacking) f.backingTypeface ?: f.mainTypeface else f.mainTypeface)
                ?: Typeface.DEFAULT
            val emphFace: Typeface = f.emphasisTypeface ?: baseFace
            val baseSp = f.fontSizeSp * 1.9f * (if (isBacking) 0.7f else 1f)
            val basePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, baseSp, resources.displayMetrics)
            val marginX = 14f * density
            val topInset = statusBarHeight() + 20f * density
            val bottomInset = 72f * density
            val availH = (screenH - topInset - bottomInset).coerceAtLeast(120f * density)
            val availW = screenW - 2f * marginX

            val n = groups.size
            val sizes = FloatArray(n) { basePx * (0.78f + rnd.nextFloat() * 0.74f) }
            val rots = FloatArray(n) { (rnd.nextFloat() * 2f - 1f) * 17f }
            val boxW = FloatArray(n)   // largura do texto
            val bbW = FloatArray(n)    // largura da caixa já girada
            val bbH = FloatArray(n)    // altura da caixa já girada
            val finalSizes = FloatArray(n)

            fun measureAll(scale: Float) {
                for (i in 0 until n) {
                    val g = groups[i]
                    val des = g.anim == LyricAnimation.DES
                    val mul = if (des) DES_SCALE else 1f
                    paint.typeface = if (des) emphFace else baseFace
                    var size = sizes[i] * scale
                    paint.textSize = size * mul
                    var w = paint.measureText(g.text)
                    val maxW = availW * 0.94f
                    if (w > maxW) {
                        size *= maxW / w
                        paint.textSize = size * mul
                        w = paint.measureText(g.text)
                    }
                    val fm = paint.fontMetrics
                    val h = fm.descent - fm.ascent
                    val rad = Math.toRadians(abs(rots[i]).toDouble())
                    finalSizes[i] = size
                    boxW[i] = w
                    bbW[i] = (w * cos(rad) + h * sin(rad)).toFloat()
                    bbH[i] = (w * sin(rad) + h * cos(rad)).toFloat()
                }
            }

            // Se a pilha não cabe na altura da tela, encolhe as palavras (até 45%) e, por fim, aperta o espaçamento.
            var scale = 1f
            var total = 0f
            var guard = 0
            while (true) {
                measureAll(scale)
                total = 0f
                for (i in 0 until n) total += bbH[i] * 0.9f
                if (total <= availH || scale <= 0.45f || guard++ >= 3) break
                scale = max(0.45f, scale * availH / total * 0.97f)
            }
            val squeeze = if (total > availH) availH / total else 1f

            var bestScore = Float.MAX_VALUE
            var bestCx = FloatArray(n)
            var bestCy = FloatArray(n)
            var bestRects: List<RectF> = emptyList()
            val tries = if (avoid.isEmpty()) 1 else 8
            for (attempt in 0 until tries) {
                val cx = FloatArray(n)
                val cy = FloatArray(n)
                val rects = ArrayList<RectF>(n)
                var cursor = topInset + rnd.nextFloat() * max(0f, availH - total * squeeze)
                var left = rnd.nextBoolean()
                for (i in 0 until n) {
                    val half = bbW[i] / 2f
                    val minC = marginX + half
                    val maxC = screenW - marginX - half
                    val t = if (left) rnd.nextFloat() * 0.5f else 0.5f + rnd.nextFloat() * 0.5f
                    cx[i] = if (maxC > minC) minC + (maxC - minC) * t else screenW / 2f
                    cy[i] = cursor + bbH[i] / 2f
                    cursor += bbH[i] * 0.9f * squeeze
                    // Quase sempre alterna esquerda/direita (como no vídeo), mas nem sempre.
                    left = if (rnd.nextFloat() < 0.8f) !left else left
                    rects.add(RectF(cx[i] - half, cy[i] - bbH[i] / 2f, cx[i] + half, cy[i] + bbH[i] / 2f))
                }
                var score = 0f
                for (r in rects) for (a in avoid) score += overlapArea(r, a)
                if (score < bestScore) {
                    bestScore = score
                    bestCx = cx
                    bestCy = cy
                    bestRects = rects
                }
            }

            val words = ArrayList<XWord>(n)
            for (i in 0 until n) {
                val g = groups[i]
                words.add(XWord(g.text, g.startMs, g.endMs, g.anim, bestCx[i], bestCy[i], rots[i], finalSizes[i], g.syl.toList()))
            }
            // A linha some um pouco depois da última palavra (não fica pendurada durante um trecho instrumental).
            val wordsEnd = groups.maxOf { if (it.endMs > it.startMs) it.endMs else it.startMs + 900L }
            val timed = line.words.any { it.timeMs >= 0L }
            val lineEnd = if (timed) wordsEnd else max(wordsEnd, line.endMs)
            return XStage(key, isBacking, words, lineEnd, bestRects)
        }

        override fun onDraw(canvas: Canvas) {
            val f = frame ?: return
            if (width <= 0 || height <= 0) return
            syncStages(f)
            val nowUp = SystemClock.uptimeMillis()
            // O serviço atualiza o quadro a cada ~50 ms; entre as atualizações a posição é extrapolada.
            val pos = f.positionMs + (nowUp - frameUptime).coerceIn(0L, 150L)
            val iter = leaving.iterator()
            while (iter.hasNext()) {
                val st = iter.next()
                val k = (nowUp - st.fadeStartUptime) / 220f
                if (k >= 1f) { iter.remove(); continue }
                drawStage(canvas, f, st, pos, st.fadeFromAlpha * (1f - k))
            }
            mainStage?.let { drawStage(canvas, f, it, pos, stageAlpha(it, pos)) }
            backingStage?.let { drawStage(canvas, f, it, pos, stageAlpha(it, pos)) }
        }

        private fun stageAlpha(st: XStage, pos: Long): Float {
            val fadeAt = st.lineEndMs + 1400L
            return if (pos <= fadeAt) 1f else (1f - (pos - fadeAt) / 300f).coerceIn(0f, 1f)
        }

        private fun drawStage(canvas: Canvas, f: ExpressiveFrame, st: XStage, pos: Long, stageAlpha: Float) {
            if (stageAlpha <= 0f) return
            val d = density
            val isB = st.isBacking
            val baseFace: Typeface = (if (isB) f.backingTypeface ?: f.mainTypeface else f.mainTypeface)
                ?: Typeface.DEFAULT
            val emphFace: Typeface = f.emphasisTypeface ?: baseFace
            val fill = if (isB) f.backingColor else f.primaryColor
            val sec = if (isB) Color.argb(120, 255, 255, 255) else f.secondaryColor
            val outlineColor = if (isB) f.backingBorderColor else f.borderColor
            val outlineDp = if (isB) f.backingBorderWidthDp else f.borderWidthDp
            val baseStrokePx = outlineDp * d * 2f
            // O contorno do popup simples é medido para o texto dele (fonte configurada). As palavras expressivas são
            // bem maiores, então o mesmo valor absoluto parecia mais fino: escala o contorno pelo tamanho da palavra
            // em relação ao texto do popup, mantendo a mesma proporção visual.
            val refSp = if (isB) (f.fontSizeSp - 4f).coerceAtLeast(11f) else f.fontSizeSp
            val refPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, refSp, resources.displayMetrics)
            val nowAnim = SystemClock.uptimeMillis()

            for (w in st.words) {
                if (pos < w.startMs) continue
                val elapsed = (pos - w.startMs).toFloat()
                val size = w.sizePx
                var dx = 0f
                var dy = 0f
                var scale = 1f
                var alpha = (elapsed / 120f).coerceIn(0f, 1f)   // entrada: surge em fade
                var color = fill
                var shadow = false
                var text = w.text
                var drawSize = size
                var face = baseFace
                var ghost = false
                val strokePx = baseStrokePx * (size / refPx).coerceAtLeast(1f)

                // Entrada: um "pulinho" de escala, exceto nos efeitos que já controlam a própria escala.
                val anim = w.animation
                if (anim != LyricAnimation.ZIN && anim != LyricAnimation.ZOU && anim != LyricAnimation.DES) {
                    val x = (elapsed / 190f).coerceIn(0f, 1f) - 1f
                    val easeOutBack = 1f + 2.70158f * x * x * x + 1.70158f * x * x
                    scale = 0.55f + 0.45f * easeOutBack
                }

                // Mesmas fórmulas do popup simples, para cada efeito ser reconhecível nos dois modos.
                when (anim) {
                    LyricAnimation.POP -> {
                        // {pop} = tremor contínuo (igual à tela Tocando agora e ao vídeo)
                        val ph = (tri(elapsed / 110f) * 2f * Math.PI).toFloat()
                        dx += sin(ph) * size * 0.07f
                        dy += sin(ph * 2f) * size * 0.03f
                    }
                    LyricAnimation.VIB -> if (elapsed < 450f) dx += sin(elapsed / 30f) * 3.5f * d
                    LyricAnimation.WAV -> dy += sin(elapsed / 180f + w.startMs / 100f) * size * 0.18f
                    LyricAnimation.SLI -> { val k = (elapsed / 320f).coerceIn(0f, 1f); dy += (1f - k) * size * 0.6f; alpha *= k }
                    LyricAnimation.ZOU -> { val k = (elapsed / 300f).coerceIn(0f, 1f); scale = 1.55f - 0.55f * (1f - (1f - k) * (1f - k)) }
                    LyricAnimation.ZIN -> { val k = (elapsed / 300f).coerceIn(0f, 1f); scale = 0.55f + 0.45f * (1f - (1f - k) * (1f - k)) }
                    LyricAnimation.DED -> alpha *= when {
                        elapsed < 1000f -> 1f
                        elapsed < 1200f -> 1f - (elapsed - 1000f) / 200f
                        else -> 0f
                    }
                    LyricAnimation.GLO -> { val k = ((elapsed / 600f) % 2f).let { if (it > 1f) 2f - it else it }; color = lerpColor(fill, sec, k) }
                    LyricAnimation.RNB -> { val hue = ((nowAnim / 12L) + w.startMs / 10L) % 360L; color = Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.75f, 1f)) }
                    LyricAnimation.FLA -> if (elapsed < 220f) color = lerpColor(fill, Color.WHITE, (1f - elapsed / 220f) * 0.85f)
                    LyricAnimation.SHD -> if (elapsed < 500f) shadow = true
                    LyricAnimation.ECO -> ghost = true
                    LyricAnimation.TYP -> text = w.text.take(((elapsed / 40f).toInt() + 1).coerceIn(0, w.text.length))
                    LyricAnimation.DES -> {
                        // {des}: cresce até DES_SCALE e usa a fonte de destaque das configurações.
                        val k = (elapsed / DES_GROW_MS).coerceIn(0f, 1f)
                        drawSize = size * (1f + (DES_SCALE - 1f) * (1f - (1f - k) * (1f - k)))
                        face = emphFace
                    }
                    else -> Unit
                }

                val a = alpha * stageAlpha
                if (a <= 0f) continue

                paint.typeface = face
                paint.textSize = drawSize
                val fullW = paint.measureText(w.text)   // largura da palavra inteira: o texto digitado (TYP) não "anda"
                val fm = paint.fontMetrics
                val baseline = -(fm.ascent + fm.descent) / 2f
                val left = -fullW / 2f

                // Preenchimento por sílaba: borda (em px, a partir da esquerda da palavra) até onde a cor primária já chegou.
                val syllableFill = w.syl.size >= 2 && anim != LyricAnimation.TYP
                var fillBoundary = 0f
                if (syllableFill) {
                    var charPos = 0
                    for (sy in w.syl) {
                        val startX = if (charPos == 0) 0f else paint.measureText(w.text, 0, charPos)
                        charPos = (charPos + sy.text.length).coerceAtMost(w.text.length)
                        val endX = paint.measureText(w.text, 0, charPos)
                        val t = ((pos - sy.startMs).toFloat() / (sy.endMs - sy.startMs).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                        if (t > 0f) fillBoundary = startX + (endX - startX) * t
                    }
                }

                canvas.save()
                canvas.translate(w.cx + dx, w.cy + dy)
                canvas.rotate(w.rotation)
                canvas.scale(scale, scale)

                if (ghost) {
                    paint.style = Paint.Style.FILL
                    paint.clearShadowLayer()
                    paint.color = color
                    paint.alpha = (Color.alpha(color) * a * 0.32f).toInt().coerceIn(0, 255)
                    canvas.drawText(text, left + 6f * d, baseline + 4f * d, paint)
                }
                if (strokePx > 0f) {
                    paint.clearShadowLayer()
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = strokePx
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.color = outlineColor
                    paint.alpha = (Color.alpha(outlineColor) * a * Color.alpha(color) / 255f).toInt().coerceIn(0, 255)
                    canvas.drawText(text, left, baseline, paint)
                    paint.style = Paint.Style.FILL
                    paint.strokeWidth = 0f
                }
                // Sem contorno configurado, uma sombra suave mantém a letra legível sobre qualquer fundo.
                if (shadow) paint.setShadowLayer(12f * d, 0f, 4f * d, Color.argb((200 * a).toInt().coerceIn(0, 255), 0, 0, 0))
                else if (strokePx <= 0f) paint.setShadowLayer(5f * d, 0f, 2f * d, Color.argb((190 * a).toInt().coerceIn(0, 255), 0, 0, 0))
                else paint.clearShadowLayer()
                paint.style = Paint.Style.FILL
                if (syllableFill) {
                    // Palavra inteira na cor secundária e, por cima, a primária avançando sílaba a sílaba (karaokê).
                    paint.color = sec
                    paint.alpha = (Color.alpha(sec) * a).toInt().coerceIn(0, 255)
                    canvas.drawText(text, left, baseline, paint)
                    paint.clearShadowLayer()
                    if (fillBoundary > 0f) {
                        canvas.save()
                        canvas.clipRect(left - 4f * d, baseline + fm.ascent - 6f * d, left + fillBoundary, baseline + fm.descent + 6f * d)
                        paint.color = color
                        paint.alpha = (Color.alpha(color) * a).toInt().coerceIn(0, 255)
                        canvas.drawText(text, left, baseline, paint)
                        canvas.restore()
                    }
                } else {
                    paint.color = color
                    paint.alpha = (Color.alpha(color) * a).toInt().coerceIn(0, 255)
                    canvas.drawText(text, left, baseline, paint)
                }
                paint.clearShadowLayer()
                canvas.restore()
            }
        }

        private fun lerpColor(a: Int, b: Int, t: Float): Int {
            val u = t.coerceIn(0f, 1f)
            return Color.argb(
                (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * u).toInt(),
                (Color.red(a) + (Color.red(b) - Color.red(a)) * u).toInt(),
                (Color.green(a) + (Color.green(b) - Color.green(a)) * u).toInt(),
                (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * u).toInt()
            )
        }
    }

    /** View que desenha karaoke + animações e se redesenha continuamente. */
    private class LyricsDrawView(context: Context) : View(context) {
        private var frame: PopupFrame? = null
        private var frameUptime = 0L
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
            frameUptime = SystemClock.uptimeMillis()
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
            return wrapWordParts(parts, maxWidth.toFloat()).sumOf { rowHeightFor(it, sizePx).toDouble() }.toFloat()
        }

        /** Altura da linha visual: linhas com {des} reservam espaço para a palavra ampliada. */
        private fun rowHeightFor(row: List<WordPart>, sizePx: Float): Float {
            val hasDes = row.any { it.word.animation == LyricAnimation.DES }
            return sizePx * 1.35f * (if (hasDes) DES_SCALE else 1f)
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
            // O serviço manda a posição a cada ~50 ms; entre os quadros ela é extrapolada para os efeitos
            // rápidos ({pop}, {vib}...) ficarem fluidos.
            val pos = f.positionMs + (SystemClock.uptimeMillis() - frameUptime).coerceIn(0L, 150L)
            var top = paddingTop.toFloat()
            f.backing?.takeIf { it.text.isNotBlank() }?.let { line ->
                top += drawLine(canvas, line, pos, f.backingColor, Color.argb(120, 255, 255, 255), bgSize, f.backingTypeface, width / 2f, top, maxTextWidth, f.backingBorderColor, f.backingBorderWidthDp)
                if (f.main?.text?.isNotBlank() == true) top += bgSize * 0.35f
            }
            f.main?.takeIf { it.text.isNotBlank() }?.let { line ->
                top += drawLine(canvas, line, pos, f.primaryColor, f.secondaryColor, mainSize, f.mainTypeface, width / 2f, top, maxTextWidth, f.borderColor, f.borderWidthDp)
                if (f.translation?.text?.isNotBlank() == true) top += mainSize * 0.4f
            }
            f.translation?.takeIf { it.text.isNotBlank() }?.let { line ->
                drawLine(canvas, line, pos, f.translationColor, Color.argb(100, Color.red(f.translationColor), Color.green(f.translationColor), Color.blue(f.translationColor)), trSize, f.translationTypeface, width / 2f, top, maxTextWidth, f.translationBorderColor, f.translationBorderWidthDp)
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
            val baseFace: Typeface = typeface ?: Typeface.DEFAULT
            val emphasisFace: Typeface = frame?.emphasisTypeface ?: baseFace
            val rows = wrapWordParts(buildWordParts(line, positionMs, primary, secondary), maxWidth)
            val baseRowHeight = textSizePx * 1.35f
            val nowAnim = SystemClock.uptimeMillis()
            var rowTop = top
            rows.forEach { row ->
                val rowHeight = rowHeightFor(row, textSizePx)
                val totalW = row.sumOf { it.width.toDouble() }.toFloat()
                var x = centerX - totalW / 2f
                paint.typeface = baseFace
                paint.textSize = textSizePx
                // O espaço extra de uma linha com {des} fica acima: a palavra cresce para cima e para baixo do centro
                val baseline = rowTop + (rowHeight - baseRowHeight) - paint.fontMetrics.ascent
                for (part in row) {
                    val word = part.word
                    // Igual à tela Tocando agora e ao vídeo: palavra sem tempo próprio anima a partir do início da
                    // linha (antes ela nunca iniciava o efeito); sem nenhum tempo, o efeito vale já "assentado".
                    val animStart = if (word.timeMs >= 0L) word.timeMs else line.timeMs
                    val noClock = animStart < 0L
                    val elapsed = when {
                        noClock -> 100_000f
                        word.timeMs >= 0L && positionMs < word.timeMs -> -1f
                        else -> (positionMs - animStart).coerceAtLeast(0L).toFloat()
                    }
                    val started = elapsed >= 0f
                    var drawX = x + part.width / 2f
                    var drawY = baseline
                    var drawStr = part.text
                    var scale = 1f
                    var alpha = 255
                    var color = part.color
                    var shadowOn = false
                    if (started) {
                        when (word.animation) {
                            LyricAnimation.POP -> {
                                // {pop} = tremor contínuo, igual à tela Tocando agora e ao vídeo (antes era um pulinho quase invisível)
                                val ph = (tri(elapsed / 110f) * 2f * Math.PI).toFloat()
                                drawX += sin(ph) * 2.4f * resources.displayMetrics.density
                                drawY += sin(ph * 2f) * 1.1f * resources.displayMetrics.density
                            }
                            LyricAnimation.VIB -> if (elapsed < 450f) drawX += sin(elapsed / 30f) * 3.5f
                            LyricAnimation.WAV -> drawY += sin(elapsed / 180f + word.timeMs / 100f) * (textSizePx * 0.18f)
                            LyricAnimation.SLI -> { val k = (elapsed / 320f).coerceIn(0f, 1f); drawY += (1f - k) * textSizePx * 0.6f; alpha = (k * 255).toInt() }
                            LyricAnimation.ZOU -> { val k = (elapsed / 300f).coerceIn(0f, 1f); scale = 1.55f - 0.55f * (1f - (1f - k) * (1f - k)) }
                            LyricAnimation.ZIN -> { val k = (elapsed / 300f).coerceIn(0f, 1f); scale = 0.55f + 0.45f * (1f - (1f - k) * (1f - k)) }
                            LyricAnimation.DED -> alpha = when { noClock || elapsed < 1000f -> 255; elapsed < 1200f -> ((1f - (elapsed - 1000f) / 200f) * 255).toInt().coerceIn(0, 255); else -> 0 }
                            LyricAnimation.GLO -> { val k = ((elapsed / 600f) % 2f).let { if (it > 1f) 2f - it else it }; color = lerpColor(primary, secondary, k) }
                            LyricAnimation.RNB -> { val hue = ((nowAnim / 12L) + word.timeMs / 10L) % 360L; color = Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.75f, 1f)) }
                            LyricAnimation.FLA -> if (elapsed < 220f) color = lerpColor(primary, Color.WHITE, (1f - elapsed / 220f) * 0.85f)
                            LyricAnimation.SHD -> if (elapsed < 500f) { shadowOn = true; paint.setShadowLayer(12f, 0f, 4f, Color.argb(180, 0, 0, 0)) }
                            LyricAnimation.ECO -> { paint.color = Color.argb(80, Color.red(color), Color.green(color), Color.blue(color)); canvas.save(); canvas.translate(4f, 2f); canvas.drawText(part.text, drawX, drawY, paint); canvas.restore() }
                            else -> Unit
                        }
                    }
                    // {des}: a palavra CRESCE (até DES_SCALE) e passa a usar a fonte de destaque das configurações.
                    // O espaço da palavra ampliada já foi reservado em buildWordParts, então ela cresce no lugar,
                    // centralizada no próprio espaço, sem empurrar nem invadir as vizinhas.
                    var finalSize = textSizePx
                    if (word.animation == LyricAnimation.DES) {
                        val grow = if (started) {
                            val k = if (noClock) 1f else (elapsed / DES_GROW_MS).coerceIn(0f, 1f)
                            1f - (1f - k) * (1f - k)
                        } else 0f
                        finalSize = textSizePx * (1f + (DES_SCALE - 1f) * grow)
                        paint.typeface = if (started) emphasisFace else baseFace
                        drawStr = part.core
                        drawX = x + part.leadW + part.coreW / 2f
                    }
                    paint.textSize = finalSize
                    if (strokePx > 0f && alpha > 0) {
                        // Contorno primeiro (sem a sombra do efeito SHD), preenchimento por cima.
                        if (shadowOn) paint.clearShadowLayer()
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = strokePx
                        paint.strokeJoin = Paint.Join.ROUND
                        paint.color = strokeColor
                        paint.alpha = (Color.alpha(strokeColor) * (alpha / 255f) * (Color.alpha(color) / 255f)).toInt().coerceIn(0, 255)
                        if (scale != 1f) { canvas.save(); canvas.scale(scale, scale, drawX, drawY); canvas.drawText(drawStr, drawX, drawY, paint); canvas.restore() }
                        else canvas.drawText(drawStr, drawX, drawY, paint)
                        paint.style = Paint.Style.FILL
                        paint.strokeWidth = 0f
                        if (shadowOn) paint.setShadowLayer(12f, 0f, 4f, Color.argb(180, 0, 0, 0))
                    }
                    paint.color = color
                    paint.alpha = alpha
                    if (scale != 1f) { canvas.save(); canvas.scale(scale, scale, drawX, drawY); canvas.drawText(drawStr, drawX, drawY, paint); canvas.restore() }
                    else canvas.drawText(drawStr, drawX, drawY, paint)
                    paint.setShadowLayer(0f, 0f, 0f, 0)
                    paint.alpha = 255
                    paint.textSize = textSizePx
                    paint.typeface = baseFace
                    x += part.width
                }
                rowTop += rowHeight
            }
            return rowTop - top
        }

        private data class WordPart(
            val word: LyricWord,
            val text: String,
            val width: Float,
            val color: Int,
            /** Só em {des}: texto sem espaços, e larguras (já ampliadas) do espaço à esquerda e do texto. */
            val core: String = text,
            val leadW: Float = 0f,
            val coreW: Float = width
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
                if (word.animation == LyricAnimation.DES) {
                    // Reserva o espaço do texto JÁ ampliado e na fonte de destaque (como na tela Tocando agora/vídeo).
                    val core = spaced.trim()
                    val lead = spaced.substring(0, spaced.length - spaced.trimStart().length)
                    val trail = if (core.isEmpty()) "" else spaced.substring(spaced.trimEnd().length)
                    val leadW = if (lead.isEmpty()) 0f else paint.measureText(lead)
                    val trailW = if (trail.isEmpty()) 0f else paint.measureText(trail)
                    val savedFace = paint.typeface
                    val savedSize = paint.textSize
                    paint.typeface = frame?.emphasisTypeface ?: savedFace
                    paint.textSize = savedSize * DES_SCALE
                    val coreW = paint.measureText(core)
                    paint.typeface = savedFace
                    paint.textSize = savedSize
                    out.add(WordPart(word, spaced, (leadW + coreW + trailW).coerceAtLeast(1f), color, core, leadW, coreW))
                } else {
                    out.add(WordPart(word, spaced, paint.measureText(spaced).coerceAtLeast(w * 0.5f), color))
                }
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
