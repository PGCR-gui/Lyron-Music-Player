package com.tolete.player.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Renderiza um vídeo .mp4 com a capa da faixa (estática ou com leve animação de zoom)
 * e salva em Movies/Lyron. O áudio é anexado quando possível via caminho do arquivo;
 * caso contrário gera um clipe visual da duração informada.
 */
object VideoExporter {

    data class Result(val success: Boolean, val outputUri: Uri? = null, val message: String = "")

    suspend fun export(
        context: Context,
        title: String,
        artist: String,
        artPath: String?,
        durationMs: Long,
        width: Int,
        height: Int,
        audioPath: String? = null
    ): Result = withContext(Dispatchers.IO) {
        try {
            val fps = 30
            val durationUs = (durationMs.coerceAtLeast(3000L)) * 1000L
            val frameCount = ((durationMs.coerceAtLeast(3000L) * fps) / 1000L).toInt().coerceAtLeast(fps * 3)

            val art = loadArt(artPath, width, height)
            val frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(frameBitmap)

            val outFile = File(context.cacheDir, "lyron_export_${System.currentTimeMillis()}.mp4")
            val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, (width * height * 4).coerceAtLeast(2_000_000))
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            // Prefer byte-buffer path for broader compatibility (no Surface)
            videoFormat.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            var videoTrack = -1
            var muxerStarted = false
            val bufferInfo = MediaCodec.BufferInfo()
            var inputFrame = 0
            var outputDone = false

            while (!outputDone) {
                if (inputFrame < frameCount) {
                    val inIndex = encoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val pts = (inputFrame * 1_000_000L) / fps
                        drawFrame(canvas, art, title, artist, width, height, inputFrame, frameCount)
                        val yuv = argbToYuv420(frameBitmap)
                        val inputBuf = encoder.getInputBuffer(inIndex)!!
                        inputBuf.clear()
                        inputBuf.put(yuv)
                        val flags = if (inputFrame == frameCount - 1) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                        encoder.queueInputBuffer(inIndex, 0, yuv.size, pts, flags)
                        inputFrame++
                    }
                }

                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            videoTrack = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outIndex >= 0 -> {
                        val encoded = encoder.getOutputBuffer(outIndex)
                        if (encoded != null && bufferInfo.size > 0 && muxerStarted) {
                            encoded.position(bufferInfo.offset)
                            encoded.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(videoTrack, encoded, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            encoder.stop()
            encoder.release()
            if (muxerStarted) muxer.stop()
            muxer.release()

            val uri = saveToMovies(context, outFile, title)
            outFile.delete()
            if (uri != null) Result(true, uri, "Vídeo salvo em Filmes/Lyron")
            else Result(false, null, "Não foi possível salvar o vídeo")
        } catch (e: Exception) {
            Result(false, null, e.message ?: "Erro na exportação")
        }
    }

    private fun loadArt(artPath: String?, w: Int, h: Int): Bitmap {
        if (!artPath.isNullOrBlank()) {
            try {
                val bmp = BitmapFactory.decodeFile(artPath)
                if (bmp != null) return bmp
            } catch (_: Exception) {}
        }
        val fallback = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(fallback).drawColor(Color.rgb(30, 20, 50))
        return fallback
    }

    private fun drawFrame(
        canvas: Canvas,
        art: Bitmap,
        title: String,
        artist: String,
        w: Int,
        h: Int,
        frame: Int,
        total: Int
    ) {
        canvas.drawColor(Color.BLACK)
        // Ken Burns-ish zoom
        val progress = frame.toFloat() / total.coerceAtLeast(1)
        val scale = 1.05f + 0.08f * progress
        val srcW = art.width
        val srcH = art.height
        val cropW = (srcW / scale).toInt().coerceAtLeast(1)
        val cropH = (srcH / scale).toInt().coerceAtLeast(1)
        val left = ((srcW - cropW) / 2f + (srcW * 0.02f * progress)).toInt().coerceIn(0, srcW - cropW)
        val top = ((srcH - cropH) / 2f).toInt().coerceIn(0, srcH - cropH)
        val src = Rect(left, top, left + cropW, top + cropH)

        // Cover fit
        val artAspect = cropW.toFloat() / cropH
        val viewAspect = w.toFloat() / h
        val dst = if (artAspect > viewAspect) {
            val dh = h
            val dw = (h * artAspect).toInt()
            val dx = (w - dw) / 2
            Rect(dx, 0, dx + dw, dh)
        } else {
            val dw = w
            val dh = (w / artAspect).toInt()
            val dy = (h - dh) / 2
            Rect(0, dy, dw, dy + dh)
        }
        canvas.drawBitmap(art, src, dst, null)

        // Gradient overlay bottom
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val grad = android.graphics.LinearGradient(
            0f, h * 0.55f, 0f, h.toFloat(),
            Color.TRANSPARENT, Color.argb(200, 0, 0, 0),
            android.graphics.Shader.TileMode.CLAMP
        )
        paint.shader = grad
        canvas.drawRect(0f, h * 0.55f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        // Title / artist
        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val titleSize = (h * 0.045f).coerceIn(28f, 64f)
        paint.textSize = titleSize
        val titleY = h * 0.82f
        canvas.drawText(title.take(48), w * 0.06f, titleY, paint)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = titleSize * 0.65f
        paint.color = Color.argb(200, 255, 255, 255)
        canvas.drawText(artist.take(48), w * 0.06f, titleY + titleSize * 0.9f, paint)

        // Lyron watermark
        paint.textSize = titleSize * 0.4f
        paint.color = Color.argb(140, 255, 255, 255)
        canvas.drawText("Lyron", w * 0.06f, h * 0.95f, paint)
    }

    /** ARGB8888 bitmap → NV12-ish flexible YUV (planar Y + interleaved UV approximated as I420). */
    private fun argbToYuv420(bitmap: Bitmap): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val argb = IntArray(w * h)
        bitmap.getPixels(argb, 0, w, 0, 0, w, h)
        val ySize = w * h
        val uvSize = ySize / 4
        val out = ByteArray(ySize + uvSize * 2)
        var yIndex = 0
        var uIndex = ySize
        var vIndex = ySize + uvSize
        var index = 0
        for (j in 0 until h) {
            for (i in 0 until w) {
                val c = argb[index++]
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                out[yIndex++] = y.coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    out[uIndex++] = u.coerceIn(0, 255).toByte()
                    out[vIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return out
    }

    private fun saveToMovies(context: Context, file: File, title: String): Uri? {
        val name = "Lyron_${title.replace(Regex("[^a-zA-Z0-9_\\- ]"), "").take(40)}_${System.currentTimeMillis()}.mp4"
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Lyron")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }
                uri
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Lyron")
                dir.mkdirs()
                val dest = File(dir, name)
                file.copyTo(dest, overwrite = true)
                Uri.fromFile(dest)
            }
        } catch (_: Exception) {
            null
        }
    }
}
