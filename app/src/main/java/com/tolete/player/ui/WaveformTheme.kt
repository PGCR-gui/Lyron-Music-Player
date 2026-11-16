package com.tolete.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Estilos visuais da forma de onda dos cortadores de áudio (editor de trecho dos vídeos).
 *
 * - [MULTICOLOR]: linhas finas em degradê azul-petróleo → verde → laranja (referência: foto 1);
 * - [SILHOUETTE]: silhueta suave em camadas, na cor dominante do tema (referência: foto 2);
 * - [MESH]: fitas entrelaçadas de linhas finas, na cor dominante do tema (referência: foto 3).
 * - [SPIKES], [WAVE_LINE], [STUDIO], [HILLS], [MIRROR], [STRIPES]: baseados na cartela de formas de onda
 *   (foto 4). Todos usam a cor de destaque do tema e são calculados a partir das amplitudes reais
 *   do áudio (nada de padrão repetido), inclusive as irregularidades das bordas.
 *
 * "Cor dominante do tema" = cor de destaque (accent) do tema atual do app.
 */
enum class WaveformTheme(val label: String, val description: String) {
    BARS("Barras", "Barras na cor de destaque do tema (padrão)."),
    MULTICOLOR("Multicolor", "Linhas finas em degradê de azul‑petróleo, verde e laranja."),
    SILHOUETTE("Silhueta", "Silhueta suave em camadas, na cor dominante do tema."),
    MESH("Malha", "Fitas entrelaçadas de linhas finas, na cor dominante do tema."),
    SPIKES("Pulso", "Linhas verticais finas e densas, com núcleo mais claro, na cor do tema."),
    WAVE_LINE("Linha", "Uma única linha ondulada que acompanha a música, na cor do tema."),
    STUDIO("Estúdio", "Forma de onda cheia com bordas irregulares, estilo editor de áudio."),
    HILLS("Colinas", "Duas camadas de colinas sobrepostas, apoiadas na base, na cor do tema."),
    MIRROR("Espelho", "Metade de cima clara e metade de baixo sólida, como reflexo."),
    STRIPES("Listras", "Listras verticais em camadas translúcidas, na cor do tema.");

    companion object {
        fun fromName(name: String?): WaveformTheme =
            values().firstOrNull { it.name == name } ?: BARS
    }
}

internal val LocalWaveformTheme = compositionLocalOf { WaveformTheme.BARS }

/** Fração da altura do quadro ocupada pela barra mais alta (< 1 deixa folga e evita "estourar"). */
private const val AMP_SCALE = 0.82f

private val MULTI_STOPS: List<Pair<Float, Color>> = listOf(
    0.00f to Color(0xFF3FA7C4),
    0.30f to Color(0xFF3DB5A0),
    0.50f to Color(0xFF9BC9A6),
    0.62f to Color(0xFFE6B891),
    0.82f to Color(0xFFE0916A),
    1.00f to Color(0xFFD9805F)
)

/** Cor do degradê multicolor em [t] (0..1, da esquerda para a direita). */
internal fun multicolorAt(t: Float): Color {
    val x = t.coerceIn(0f, 1f)
    for (i in 0 until MULTI_STOPS.size - 1) {
        val (p0, c0) = MULTI_STOPS[i]
        val (p1, c1) = MULTI_STOPS[i + 1]
        if (x <= p1) {
            val f = if (p1 > p0) ((x - p0) / (p1 - p0)).coerceIn(0f, 1f) else 0f
            return lerp(c0, c1, f)
        }
    }
    return MULTI_STOPS.last().second
}

private fun smoothAmps(src: FloatArray, passes: Int): FloatArray {
    var cur = src
    repeat(passes) {
        val n = cur.size
        val out = FloatArray(n)
        for (i in 0 until n) {
            val l = cur[max(0, i - 1)]
            val r = cur[min(n - 1, i + 1)]
            out[i] = (l + 2f * cur[i] + r) / 4f
        }
        cur = out
    }
    return cur
}

/**
 * Desenha [amps] (uma amplitude 0..1 por coluna) no estilo [theme].
 * [selL]..[selR] é a região selecionada (em px): dentro usa a cor cheia, fora fica esmaecido
 * ([offColor]). [barW]/[gap]/[minH] só valem para os estilos baseados em colunas (barras, multicolor).
 */
internal fun DrawScope.drawWaveAmps(
    theme: WaveformTheme,
    amps: FloatArray,
    selL: Float,
    selR: Float,
    accent: Color,
    offColor: Color,
    barW: Float,
    gap: Float,
    minH: Float
) {
    if (amps.isEmpty()) return
    when (theme) {
        WaveformTheme.BARS -> drawBarsTheme(amps, selL, selR, accent, offColor, barW, gap, minH)
        WaveformTheme.MULTICOLOR -> drawMulticolorTheme(amps, selL, selR, offColor, barW, gap, minH)
        WaveformTheme.SILHOUETTE -> drawSilhouetteTheme(amps, selL, selR, accent, offColor)
        WaveformTheme.MESH -> drawMeshTheme(amps, selL, selR, accent, offColor)
        WaveformTheme.SPIKES -> drawSpikesTheme(amps, selL, selR, accent, offColor, barW, gap, minH)
        WaveformTheme.WAVE_LINE -> drawWaveLineTheme(amps, selL, selR, accent, offColor)
        WaveformTheme.STUDIO -> drawStudioTheme(amps, selL, selR, accent, offColor)
        WaveformTheme.HILLS -> drawHillsTheme(amps, selL, selR, accent, offColor)
        WaveformTheme.MIRROR -> drawMirrorTheme(amps, selL, selR, accent, offColor)
        WaveformTheme.STRIPES -> drawStripesTheme(amps, selL, selR, accent, offColor, barW, gap, minH)
    }
}

private fun DrawScope.drawBarsTheme(
    amps: FloatArray, selL: Float, selR: Float, accent: Color, off: Color,
    barW: Float, gap: Float, minH: Float
) {
    val colW = barW + gap
    for (i in amps.indices) {
        val h = max(minH, amps[i] * size.height * AMP_SCALE)
        val x = i * colW
        val inSel = x + barW / 2f in selL..selR
        drawRoundRect(
            color = if (inSel) accent else off,
            topLeft = Offset(x, (size.height - h) / 2f),
            size = Size(barW, h),
            cornerRadius = CornerRadius(barW / 2f)
        )
    }
}

private fun DrawScope.drawMulticolorTheme(
    amps: FloatArray, selL: Float, selR: Float, off: Color,
    barW: Float, gap: Float, minH: Float
) {
    val colW = barW + gap
    val lineW = max(1f, barW * 0.55f)
    val mid = size.height / 2f
    val w = max(1f, size.width)
    for (i in amps.indices) {
        val h = max(minH, amps[i] * size.height * AMP_SCALE)
        val x = i * colW + barW / 2f
        val base = multicolorAt(x / w)
        val inSel = x in selL..selR
        if (inSel) {
            // brilho suave por baixo + linha fina por cima
            drawLine(base.copy(alpha = 0.14f), Offset(x, mid - h / 2f), Offset(x, mid + h / 2f), lineW * 3.4f, StrokeCap.Round)
        }
        drawLine(
            color = base.copy(alpha = if (inSel) 1f else max(0.30f, off.alpha)),
            start = Offset(x, mid - h / 2f),
            end = Offset(x, mid + h / 2f),
            strokeWidth = lineW,
            cap = StrokeCap.Round
        )
    }
}

/** Aplica [drawIn] dentro da seleção e [drawOut] fora dela (por recorte). */
private inline fun DrawScope.splitBySelection(
    selL: Float,
    selR: Float,
    drawIn: DrawScope.() -> Unit,
    drawOut: DrawScope.() -> Unit
) {
    val w = size.width
    val h = size.height
    val l = selL.coerceIn(0f, w)
    val r = selR.coerceIn(0f, w)
    if (r > l) {
        clipRect(l, 0f, r, h) { drawIn() }
        if (l > 0f) clipRect(0f, 0f, l, h) { drawOut() }
        if (r < w) clipRect(r, 0f, w, h) { drawOut() }
    } else {
        drawOut()
    }
}

private fun DrawScope.drawSilhouetteTheme(
    amps: FloatArray, selL: Float, selR: Float, accent: Color, off: Color
) {
    val w = size.width
    val h = size.height
    val mid = h / 2f
    val sm = smoothAmps(amps, 2)
    val n = sm.size
    val step = w / n
    val layers = floatArrayOf(1f, 0.72f, 0.46f)
    val alphaOn = floatArrayOf(0.30f, 0.55f, 1f)
    val alphaOff = floatArrayOf(0.35f, 0.60f, 1f)

    fun pathFor(scale: Float): Path {
        val half = FloatArray(n) { max(1f, sm[it] * h * AMP_SCALE * 0.5f * scale) }
        val xs = FloatArray(n) { (it + 0.5f) * step }
        val p = Path()
        p.moveTo(0f, mid - half[0])
        for (i in 0 until n - 1) {
            p.quadraticBezierTo(
                xs[i], mid - half[i],
                (xs[i] + xs[i + 1]) / 2f, mid - (half[i] + half[i + 1]) / 2f
            )
        }
        p.lineTo(w, mid - half[n - 1])
        p.lineTo(w, mid + half[n - 1])
        for (i in n - 2 downTo 0) {
            p.quadraticBezierTo(
                xs[i + 1], mid + half[i + 1],
                (xs[i] + xs[i + 1]) / 2f, mid + (half[i] + half[i + 1]) / 2f
            )
        }
        p.lineTo(0f, mid + half[0])
        p.close()
        return p
    }

    for (l in layers.indices) {
        val path = pathFor(layers[l])
        splitBySelection(
            selL, selR,
            drawIn = { drawPath(path, accent.copy(alpha = alphaOn[l])) },
            drawOut = { drawPath(path, off.copy(alpha = (off.alpha * alphaOff[l]).coerceIn(0f, 1f))) }
        )
    }
}

private fun DrawScope.drawMeshTheme(
    amps: FloatArray, selL: Float, selR: Float, accent: Color, off: Color
) {
    val w = size.width
    val h = size.height
    val mid = h / 2f
    val sm = smoothAmps(amps, 3)
    val n = sm.size
    val step = w / n
    val lines = 11
    val thin = 0.9.dp.toPx()
    val rim = 1.5.dp.toPx()
    val twist = 9f // radianos de torção ao longo da largura: define quantos cruzamentos aparecem
    val lineColor = lerp(accent, Color.White, 0.30f)

    fun ribbon(k: Int): Path {
        val phase = (k.toFloat() / (lines - 1)) * PI.toFloat()
        val p = Path()
        for (i in 0 until n) {
            val tw = (i.toFloat() / n) * twist
            val y = mid - sm[i] * h * AMP_SCALE * 0.5f * cos(phase + tw)
            val x = (i + 0.5f) * step
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        return p
    }

    fun rimPath(sign: Float): Path {
        val p = Path()
        for (i in 0 until n) {
            val y = mid + sign * sm[i] * h * AMP_SCALE * 0.5f
            val x = (i + 0.5f) * step
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        return p
    }

    val ribbons = List(lines) { ribbon(it) }
    val top = rimPath(-1f)
    val bottom = rimPath(1f)

    splitBySelection(
        selL, selR,
        drawIn = {
            for (r in ribbons) drawPath(r, lineColor.copy(alpha = 0.50f), style = Stroke(width = thin))
            drawPath(top, lineColor.copy(alpha = 0.95f), style = Stroke(width = rim))
            drawPath(bottom, lineColor.copy(alpha = 0.95f), style = Stroke(width = rim))
        },
        drawOut = {
            val a = off.alpha.coerceIn(0f, 1f)
            for (r in ribbons) drawPath(r, off.copy(alpha = a * 0.65f), style = Stroke(width = thin))
            drawPath(top, off.copy(alpha = a), style = Stroke(width = rim))
            drawPath(bottom, off.copy(alpha = a), style = Stroke(width = rim))
        }
    )
}


// ---------------------------------------------------------------------------------------------
// Estilos da cartela (foto 4). Todos derivam das amplitudes reais: sem ruído aleatório nem padrões
// repetidos, então o desenho não "pisca" ao rolar/dar zoom e cada música gera um formato próprio.
// ---------------------------------------------------------------------------------------------

/** Posições X (centro de cada coluna) com um ponto extra em 0 e outro na largura total (bordas). */
private fun paddedXs(n: Int, w: Float): FloatArray {
    val step = w / n
    return FloatArray(n + 2) { i ->
        when (i) {
            0 -> 0f
            n + 1 -> w
            else -> (i - 0.5f) * step
        }
    }
}

/** Copia [a] para um vetor com [n]+2 posições, com [edge] nas duas pontas. */
private fun padded(a: FloatArray, edge: Float = 0f): FloatArray {
    val n = a.size
    return FloatArray(n + 2) { i ->
        when (i) {
            0, n + 1 -> edge
            else -> a[i - 1]
        }
    }
}

/** Acrescenta uma curva suave (Bézier quadrática por pontos médios) passando perto de xs/ys. */
private fun Path.addCurve(xs: FloatArray, ys: FloatArray, reverse: Boolean, startWithMove: Boolean) {
    val n = xs.size
    if (n == 0) return
    var prevX = 0f
    var prevY = 0f
    var first = true
    val order = if (reverse) (n - 1 downTo 0) else (0 until n)
    for (i in order) {
        val x = xs[i]
        val y = ys[i]
        if (first) {
            if (startWithMove) moveTo(x, y) else lineTo(x, y)
            first = false
        } else {
            quadraticBezierTo(prevX, prevY, (prevX + x) / 2f, (prevY + y) / 2f)
        }
        prevX = x
        prevY = y
    }
    lineTo(prevX, prevY)
}

/** Faixa fechada e suave entre a curva [topY] e a curva [botY]. */
private fun bandPath(xs: FloatArray, topY: FloatArray, botY: FloatArray): Path {
    val p = Path()
    p.addCurve(xs, topY, reverse = false, startWithMove = true)
    p.addCurve(xs, botY, reverse = true, startWithMove = false)
    p.close()
    return p
}

/** Preenche [path] com [on] dentro da seleção e [off] fora dela. */
private fun DrawScope.fillSplit(path: Path, selL: Float, selR: Float, on: Color, off: Color) {
    splitBySelection(selL, selR, drawIn = { drawPath(path, on) }, drawOut = { drawPath(path, off) })
}

/** Cor "apagada" (fora da seleção) com a opacidade multiplicada por [k]. */
private fun Color.dim(k: Float): Color = copy(alpha = (alpha * k).coerceIn(0f, 1f))

/** Suaviza as pontas (0..1): 0 no extremo, 1 a partir de [fraction] da largura. */
private fun edgeTaper(x: Float, w: Float, fraction: Float = 0.06f): Float {
    val d = min(x, w - x) / max(1f, w * fraction)
    val t = d.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Maior valor do vetor (mínimo 0.001 para não dividir por zero). */
private fun peakOf(a: FloatArray): Float {
    var m = 0.001f
    for (v in a) if (v > m) m = v
    return m
}

/** Amplitude interpolada linearmente na posição [x] (px), com uma amostra por coluna. */
private fun sampleAt(a: FloatArray, x: Float, step: Float): Float {
    val pos = (x / step - 0.5f).coerceIn(0f, (a.size - 1).toFloat())
    val i0 = pos.toInt()
    val i1 = min(a.size - 1, i0 + 1)
    val f = pos - i0
    return a[i0] * (1f - f) + a[i1] * f
}

/** PULSO: linhas verticais finas e densas; um núcleo mais claro e curto dentro de cada linha. */
private fun DrawScope.drawSpikesTheme(
    amps: FloatArray, selL: Float, selR: Float, accent: Color, off: Color,
    barW: Float, gap: Float, minH: Float
) {
    val n = amps.size
    val colW = barW + gap
    val mid = size.height / 2f
    val lineW = max(1f, barW * 0.5f)
    val core = lerp(accent, Color.White, 0.45f)
    for (i in 0 until n) {
        val x = i * colW + barW / 2f
        val inSel = x in selL..selR
        val h = max(minH, amps[i] * size.height * AMP_SCALE)
        // o núcleo segue o menor valor entre a coluna e as vizinhas: some nos picos isolados
        val calm = min(amps[i], min(amps[max(0, i - 1)], amps[min(n - 1, i + 1)]))
        val hc = max(minH * 0.5f, calm * size.height * AMP_SCALE * 0.55f)
        drawLine(
            color = if (inSel) accent else off,
            start = Offset(x, mid - h / 2f), end = Offset(x, mid + h / 2f),
            strokeWidth = lineW, cap = StrokeCap.Butt
        )
        drawLine(
            color = if (inSel) core.copy(alpha = 0.85f) else off.dim(0.6f),
            start = Offset(x, mid - hc / 2f), end = Offset(x, mid + hc / 2f),
            strokeWidth = lineW, cap = StrokeCap.Butt
        )
    }
}

/** LINHA: um único traço ondulado; a frequência local acompanha o "detalhe" do áudio. */
private fun DrawScope.drawWaveLineTheme(
    amps: FloatArray, selL: Float, selR: Float, accent: Color, off: Color
) {
    val w = size.width
    val h = size.height
    val mid = h / 2f
    val n = amps.size
    val step = w / n
    val sm = smoothAmps(amps, 2)
    val basePeriod = max(8.dp.toPx(), step * 4f)
    val sample = max(1f, 1.dp.toPx())
    val stroke = 1.7.dp.toPx()
    val axis = 0.8.dp.toPx()
    val path = Path()
    var phase = 0f
    var x = 0f
    var first = true
    while (x <= w + sample) {
        val xc = min(x, w)
        val env = sampleAt(sm, xc, step)
        val detail = abs(sampleAt(amps, xc, step) - env)
        // frequência varia com o detalhe real (0.75x .. ~1.75x): nada de senoide perfeita repetida
        phase += sample / basePeriod * 2f * PI.toFloat() * (0.75f + (detail * 4f).coerceIn(0f, 1f))
        val y = mid - env * h * AMP_SCALE * 0.5f * sin(phase) * edgeTaper(xc, w, 0.05f)
        if (first) { path.moveTo(xc, y); first = false } else path.lineTo(xc, y)
        x += sample
    }
    splitBySelection(
        selL, selR,
        drawIn = {
            drawLine(accent.copy(alpha = 0.35f), Offset(0f, mid), Offset(w, mid), axis)
            drawPath(path, accent, style = Stroke(width = stroke, cap = StrokeCap.Round))
        },
        drawOut = {
            drawLine(off.dim(0.5f), Offset(0f, mid), Offset(w, mid), axis)
            drawPath(path, off, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
    )
}

/** ESTÚDIO: forma de onda cheia; borda externa irregular (pico real) e núcleo sólido por dentro. */
private fun DrawScope.drawStudioTheme(
    amps: FloatArray, selL: Float, selR: Float, accent: Color, off: Color
) {
    val w = size.width
    val h = size.height
    val mid = h / 2f
    val n = amps.size
    val sm = smoothAmps(amps, 2)
    val xs = paddedXs(n, w)
    val half = h * AMP_SCALE * 0.5f

    // borda de cima usa o detalhe da coluna; a de baixo usa o da vizinha, deixando o desenho assimétrico
    val topOut = FloatArray(n + 2) { mid }
    val botOut = FloatArray(n + 2) { mid }
    for (i in 0 until n) {
        val d = amps[i] - sm[i]
        val dn = amps[min(n - 1, i + 1)] - sm[min(n - 1, i + 1)]
        val up = (sm[i] + 2.2f * d).coerceIn(0.02f, 1f)
        val dw = (sm[i] + 2.2f * dn).coerceIn(0.02f, 1f)
        topOut[i + 1] = mid - up * half
        botOut[i + 1] = mid + dw * half
    }
    val outer = Path().apply {
        moveTo(xs[0], topOut[0])
        for (i in 1 until xs.size) lineTo(xs[i], topOut[i])
        for (i in xs.size - 1 downTo 0) lineTo(xs[i], botOut[i])
        close()
    }
    val coreTop = FloatArray(n + 2) { i -> if (i == 0 || i == n + 1) mid else mid - sm[i - 1] * half * 0.5f }
    val coreBot = FloatArray(n + 2) { i -> if (i == 0 || i == n + 1) mid else mid + sm[i - 1] * half * 0.5f }
    val core = bandPath(xs, coreTop, coreBot)

    splitBySelection(
        selL, selR,
        drawIn = {
            drawPath(outer, accent.copy(alpha = 0.55f))
            drawPath(core, accent)
        },
        drawOut = {
            drawPath(outer, off.dim(0.7f))
            drawPath(core, off)
        }
    )
    val axis = 0.8.dp.toPx()
    splitBySelection(
        selL, selR,
        drawIn = { drawLine(accent.copy(alpha = 0.5f), Offset(0f, mid), Offset(w, mid), axis) },
        drawOut = { drawLine(off.dim(0.5f), Offset(0f, mid), Offset(w, mid), axis) }
    )
}

/** COLINAS: duas camadas apoiadas na base — uma larga e clara atrás, outra mais baixa e sólida na frente. */
private fun DrawScope.drawHillsTheme(
    amps: FloatArray, selL: Float, selR: Float, accent: Color, off: Color
) {
    val w = size.width
    val h = size.height
    val n = amps.size
    val xs = paddedXs(n, w)
    val base = h - 1.dp.toPx()
    val usable = h * AMP_SCALE

    val backRaw = smoothAmps(amps, 7)
    // a camada de trás é muito suavizada; reescala para ainda chegar perto do pico real
    val backScale = (peakOf(amps) / peakOf(backRaw)).coerceIn(1f, 2.5f)
    val frontRaw = smoothAmps(amps, 2)

    val baseline = FloatArray(n + 2) { base }
    val backTop = FloatArray(n + 2) { i ->
        if (i == 0 || i == n + 1) base else base - (backRaw[i - 1] * backScale).coerceIn(0.02f, 1f) * usable
    }
    val frontTop = FloatArray(n + 2) { i ->
        if (i == 0 || i == n + 1) base else base - (frontRaw[i - 1] * 0.7f).coerceIn(0.02f, 1f) * usable
    }
    val back = bandPath(xs, backTop, baseline)
    val front = bandPath(xs, frontTop, baseline)

    splitBySelection(
        selL, selR,
        drawIn = {
            drawPath(back, accent.copy(alpha = 0.32f))
            drawPath(front, accent)
        },
        drawOut = {
            drawPath(back, off.dim(0.5f))
            drawPath(front, off)
        }
    )
}

/** ESPELHO: metade de cima clara e translúcida, metade de baixo sólida e com formato levemente diferente. */
private fun DrawScope.drawMirrorTheme(
    amps: FloatArray, selL: Float, selR: Float, accent: Color, off: Color
) {
    val w = size.width
    val h = size.height
    val mid = h / 2f
    val n = amps.size
    val xs = paddedXs(n, w)
    val half = h * AMP_SCALE * 0.5f

    val upper = smoothAmps(amps, 2)
    val lowerSrc = smoothAmps(amps, 3)
    val shift = 2 // a parte de baixo "atrasa" duas colunas em relação à de cima
    val lower = FloatArray(n) { lowerSrc[max(0, it - shift)] * 0.9f }

    val axis = FloatArray(n + 2) { mid }
    val topCurve = FloatArray(n + 2) { i ->
        if (i == 0 || i == n + 1) mid else mid - upper[i - 1].coerceIn(0.02f, 1f) * half
    }
    val botCurve = FloatArray(n + 2) { i ->
        if (i == 0 || i == n + 1) mid else mid + lower[i - 1].coerceIn(0.02f, 1f) * half
    }
    val topPath = bandPath(xs, topCurve, axis)
    val botPath = bandPath(xs, axis, botCurve)
    val light = lerp(accent, Color.White, 0.35f)

    splitBySelection(
        selL, selR,
        drawIn = {
            drawPath(topPath, light.copy(alpha = 0.55f))
            drawPath(botPath, accent)
        },
        drawOut = {
            drawPath(topPath, off.dim(0.55f))
            drawPath(botPath, off)
        }
    )
}

/** LISTRAS: três envelopes concêntricos feitos de listras verticais, cada um mais opaco que o de fora. */
private fun DrawScope.drawStripesTheme(
    amps: FloatArray, selL: Float, selR: Float, accent: Color, off: Color,
    barW: Float, gap: Float, minH: Float
) {
    val n = amps.size
    val colW = barW + gap
    val mid = size.height / 2f
    val w = max(1f, size.width)
    val lineW = max(1f, barW * 0.45f)
    val usable = size.height * AMP_SCALE

    val outerRaw = smoothAmps(amps, 5)
    val outerScale = (peakOf(amps) / peakOf(outerRaw)).coerceIn(1f, 2.5f)
    val midEnv = smoothAmps(amps, 3)
    val inner = smoothAmps(amps, 1)

    val alphas = floatArrayOf(0.28f, 0.52f, 1f)
    for (i in 0 until n) {
        val x = i * colW + barW / 2f
        val inSel = x in selL..selR
        val taper = 0.25f + 0.75f * edgeTaper(x, w, 0.08f) // as listras das pontas encolhem, mas não somem
        val hs = floatArrayOf(
            (outerRaw[i] * outerScale).coerceIn(0f, 1f) * usable * taper,
            midEnv[i] * usable * 0.72f * taper,
            inner[i] * usable * 0.42f * taper
        )
        for (l in 0..2) {
            val hl = max(minH * (1f - 0.25f * l), hs[l])
            drawLine(
                color = if (inSel) accent.copy(alpha = alphas[l]) else off.dim(alphas[l]),
                start = Offset(x, mid - hl / 2f), end = Offset(x, mid + hl / 2f),
                strokeWidth = lineW, cap = StrokeCap.Butt
            )
        }
    }
}

/** Amplitudes de exemplo (formato parecido com música) usadas nas prévias das configurações. */
private fun sampleAmps(count: Int): FloatArray = FloatArray(count) { i ->
    val t = if (count > 1) i / (count - 1f) else 0f
    val env = 0.35f + 0.65f * abs(sin(t * 7.3f + 0.4f))
    val tex = 0.55f + 0.45f * abs(sin(t * 41f) * cos(t * 17f + 1f))
    (env * tex).coerceIn(0.08f, 1f)
}

/** Prévia pequena do estilo [theme] (toda a região "selecionada"), para a tela de aparência. */
@Composable
internal fun WaveformThemePreview(theme: WaveformTheme, accent: Color, modifier: Modifier = Modifier) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val barW = 2.dp.toPx()
            val gap = 1.dp.toPx()
            val cols = max(8, (size.width / (barW + gap)).toInt())
            val amps = sampleAmps(cols)
            drawWaveAmps(
                theme = theme,
                amps = amps,
                selL = 0f,
                selR = size.width,
                accent = accent,
                offColor = Color.White.copy(alpha = 0.3f),
                barW = barW,
                gap = gap,
                minH = 2.dp.toPx()
            )
        }
    }
}
