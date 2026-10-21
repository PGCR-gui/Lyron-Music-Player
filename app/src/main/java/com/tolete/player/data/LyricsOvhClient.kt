package com.tolete.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fonte alternativa de letras: api.lyrics.ovh (texto plano, sem sincronização).
 */
object LyricsOvhClient {

    suspend fun fetchPlain(artist: String, title: String): String? = withContext(Dispatchers.IO) {
        if (artist.isBlank() || title.isBlank()) return@withContext null
        try {
            val a = URLEncoder.encode(artist.trim(), "UTF-8")
            val t = URLEncoder.encode(title.trim(), "UTF-8")
            val url = URL("https://api.lyrics.ovh/v1/$a/$t")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 10000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode != 200) return@withContext null
            val body = conn.inputStream.bufferedReader().readText()
            val lyrics = JSONObject(body).optString("lyrics").trim()
            lyrics.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    /** Converte texto plano em lista de LyricLine (sem timestamps). */
    fun toLines(plain: String): List<LyricLine> =
        plain.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { LyricLine(timeMs = -1, text = it) }
}
