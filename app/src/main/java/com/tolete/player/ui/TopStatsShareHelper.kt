package com.tolete.player.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.tolete.player.R
import com.tolete.player.ui.MusicViewModel.MostPlayedKind
import com.tolete.player.ui.MusicViewModel.RankedItem
import com.tolete.player.ui.MusicViewModel.StatsPeriod
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Top 10 com capas de músicas/álbuns e fotos de artistas (mesmas da lista).
 * FileProvider: package.updates + cache/updates/
 */
object TopStatsShareHelper {

    private const val TAG = "TopStatsShare"

    fun share(
        context: Context,
        kind: MostPlayedKind,
        period: StatsPeriod,
        items: List<RankedItem>
    ) {
        val appCtx = context.applicationContext
        val top = items.take(10)
        if (top.isEmpty()) {
            Toast.makeText(appCtx, "Nada para exportar", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(appCtx, "Gerando imagem…", Toast.LENGTH_SHORT).show()
        Thread {
        try {
            val bmp = render(appCtx, top, kind, period)
            val dir = File(appCtx.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, "top_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bmp.recycle()
            val authority = appCtx.packageName + ".updates"
            val uri = try {
                FileProvider.getUriForFile(appCtx, authority, file)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "FileProvider falhou ($authority): ${e.message}", e)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(appCtx, "Falha ao preparar compartilhamento", Toast.LENGTH_SHORT).show()
                }
                return@Thread
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                appCtx.startActivity(
                    Intent.createChooser(intent, "Compartilhar ranking")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "share failed", e)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(appCtx, "Falha ao gerar imagem: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        }.start()
    }

    private fun periodLabel(p: StatsPeriod): String = when (p) {
        StatsPeriod.ALL_TIME -> "Tempo todo"
        StatsPeriod.THIS_WEEK -> "Esta semana"
        StatsPeriod.LAST_WEEK -> "Última semana"
        StatsPeriod.THIS_MONTH -> "Este mês"
        StatsPeriod.LAST_MONTH -> "Último mês"
        StatsPeriod.THIS_YEAR -> "Este ano"
        StatsPeriod.LAST_YEAR -> "Último ano"
    }

    private fun kindLabel(k: MostPlayedKind): String = when (k) {
        MostPlayedKind.TRACKS -> "Músicas"
        MostPlayedKind.ARTISTS -> "Artistas"
        MostPlayedKind.ALBUMS -> "Álbuns"
    }

    private fun render(
        context: Context,
        items: List<RankedItem>,
        kind: MostPlayedKind,
        period: StatsPeriod
    ): Bitmap {
        val w = 1080
        val rowH = 110
        val headerH = 220
        val footerH = 120
        val pad = 48
        val artSize = 84
        val h = headerH + items.size * rowH + footerH
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF121212.toInt())

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFB0B0B0.toInt()
            textSize = 34f
        }
        val rowTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rowSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFAAAAAA.toInt()
            textSize = 30f
        }
        val rankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF69F0AE.toInt()
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF69F0AE.toInt()
            textSize = 32f
            textAlign = Paint.Align.RIGHT
        }
        val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2A2A2A.toInt()
        }

        c.drawText("Mais tocadas", pad.toFloat(), 90f, titlePaint)
        c.drawText("${kindLabel(kind)} · ${periodLabel(period)}", pad.toFloat(), 150f, subPaint)

        var y = headerH.toFloat()
        items.forEachIndexed { i, item ->
            val baseline = y + 48f
            val artLeft = pad + 50f
            val artTop = y + (rowH - artSize) / 2f
            val art = decodeArt(item.artPath)
            if (art != null) {
                drawArt(c, art, artLeft, artTop, artSize.toFloat(), circular = kind == MostPlayedKind.ARTISTS)
                if (!art.isRecycled) art.recycle()
            } else {
                val r = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)
                if (kind == MostPlayedKind.ARTISTS) {
                    c.drawOval(r, placeholder)
                } else {
                    c.drawRoundRect(r, 14f, 14f, placeholder)
                }
            }
            c.drawText("${i + 1}", pad.toFloat(), baseline, rankPaint)
            val textLeft = artLeft + artSize + 20f
            c.drawText(item.title.take(32), textLeft, baseline, rowTitle)
            c.drawText(item.subtitle.take(36), textLeft, baseline + 36f, rowSub)
            val countLabel = if (item.count == 1) "1 play" else "${item.count} plays"
            c.drawText(countLabel, (w - pad).toFloat(), baseline + 10f, countPaint)
            y += rowH
        }

        drawBrandPill(c, context, w / 2f, h - 55f, 56f)
        return bmp
    }

    private fun decodeArt(pathOrUrl: String?): Bitmap? {
        if (pathOrUrl.isNullOrBlank()) return null
        return try {
            when {
                pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://") -> {
                    val conn = (URL(pathOrUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8_000
                        readTimeout = 8_000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "LyronMusicPlayer/0.60")
                    }
                    conn.inputStream.use { BitmapFactory.decodeStream(it) }
                }
                else -> {
                    val f = File(pathOrUrl)
                    if (f.isFile) BitmapFactory.decodeFile(f.absolutePath) else null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun drawArt(
        canvas: Canvas,
        src: Bitmap,
        left: Float,
        top: Float,
        size: Float,
        circular: Boolean
    ) {
        val dst = RectF(left, top, left + size, top + size)
        val save = canvas.save()
        val path = Path()
        if (circular) path.addOval(dst, Path.Direction.CW)
        else path.addRoundRect(dst, 14f, 14f, Path.Direction.CW)
        canvas.clipPath(path)
        val srcRect = Rect(0, 0, src.width, src.height)
        // Center-crop
        val scale = maxOf(size / src.width, size / src.height)
        val sw = src.width * scale
        val sh = src.height * scale
        val dx = left + (size - sw) / 2f
        val dy = top + (size - sh) / 2f
        canvas.drawBitmap(src, srcRect, RectF(dx, dy, dx + sw, dy + sh), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        canvas.restoreToCount(save)
    }

    private fun drawBrandPill(
        canvas: Canvas,
        context: Context,
        centerX: Float,
        centerY: Float,
        pillH: Float
    ) {
        val icon: Bitmap? = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.lyron_logo)
        } catch (_: Exception) {
            null
        }
        val wordmark: Typeface = try {
            ResourcesCompat.getFont(context, R.font.lyron_wordmark)
        } catch (_: Exception) {
            null
        } ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)

        val label = "LYRON"
        val ts = pillH * 0.55f
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFC2D1CB.toInt()
            textSize = ts
            typeface = wordmark
        }
        val textW = fillPaint.measureText(label)
        val iconSize = pillH * 0.7f
        val gap = 12f
        val contentW = (if (icon != null) iconSize + gap else 0f) + textW
        val pillW = contentW + pillH * 0.9f
        val left = centerX - pillW / 2f
        val top = centerY - pillH / 2f

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x33FFFFFF
        }
        canvas.drawRoundRect(RectF(left, top, left + pillW, top + pillH), pillH / 2f, pillH / 2f, bg)

        var x = left + pillH * 0.4f
        if (icon != null) {
            val scaled = Bitmap.createScaledBitmap(
                icon,
                iconSize.toInt().coerceAtLeast(1),
                iconSize.toInt().coerceAtLeast(1),
                true
            )
            canvas.drawBitmap(scaled, x, centerY - iconSize / 2f, null)
            if (scaled != icon) scaled.recycle()
            x += iconSize + gap
        }
        canvas.drawText(label, x, centerY + ts * 0.35f, fillPaint)
    }
}
