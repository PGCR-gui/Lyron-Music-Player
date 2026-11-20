package com.tolete.player.data

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Descobre a resolução (largura x altura) de uma imagem remota lendo só o cabeçalho
 * (inJustDecodeBounds), sem decodificar os pixels. Resultados ficam em cache em memória.
 */
object ImageProbe {

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36"

    private val gate = Semaphore(4)
    private val cache = ConcurrentHashMap<String, Pair<Int, Int>>()

    suspend fun size(url: String): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        cache[url]?.let { return@withContext it }
        gate.withPermit {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", UA)
                }
                try {
                    if (conn.responseCode !in 200..299) return@withPermit null
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    conn.inputStream.use { BitmapFactory.decodeStream(it, null, opts) }
                    if (opts.outWidth > 0 && opts.outHeight > 0) {
                        val size = opts.outWidth to opts.outHeight
                        cache[url] = size
                        size
                    } else null
                } finally {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
