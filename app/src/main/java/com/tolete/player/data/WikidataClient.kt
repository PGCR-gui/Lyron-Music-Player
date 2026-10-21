package com.tolete.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente simples da API Wikidata / Wikimedia Commons.
 * Busca entidades (artista, álbum), descrição e imagem (P18).
 */
object WikidataClient {

    private const val WD_API = "https://www.wikidata.org/w/api.php"
    private const val WP_API = "https://en.wikipedia.org/w/api.php"
    private const val USER_AGENT = "LyronMusicPlayer/0.40 (https://linktr.ee/pgcrgui)"

    data class EntityInfo(
        val id: String,
        val label: String,
        val description: String,
        val imageUrl: String?,
        val extract: String?
    )

    suspend fun searchArtist(name: String): EntityInfo? = searchEntity(name, preferMusician = true)

    suspend fun searchAlbum(albumName: String, artistName: String? = null): EntityInfo? {
        val q = if (!artistName.isNullOrBlank()) "$albumName $artistName" else albumName
        return searchEntity(q, preferMusician = false)
    }

    private suspend fun searchEntity(query: String, preferMusician: Boolean): EntityInfo? =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext null
            try {
                val searchUrl = "$WD_API?action=wbsearchentities" +
                    "&search=${enc(query)}&language=en&uselang=pt&format=json&type=item&limit=8"
                val root = getJson(searchUrl) ?: return@withContext null
                val results = root.optJSONArray("search") ?: return@withContext null
                if (results.length() == 0) return@withContext null

                // Prefer entities that look like music-related
                var bestId: String? = null
                var bestLabel = ""
                var bestDesc = ""
                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    val id = item.optString("id")
                    val label = item.optString("label")
                    val desc = item.optString("description", "")
                    val descLower = desc.lowercase()
                    val musicHint = listOf(
                        "musician", "singer", "band", "rapper", "composer", "artist",
                        "album", "record", "song", "group", "cantor", "banda", "álbum",
                        "músico", "rapper", "grupo"
                    ).any { descLower.contains(it) }
                    if (preferMusician && musicHint) {
                        bestId = id
                        bestLabel = label
                        bestDesc = desc
                        break
                    }
                    if (bestId == null) {
                        bestId = id
                        bestLabel = label
                        bestDesc = desc
                    }
                }
                val id = bestId ?: return@withContext null

                val imageUrl = fetchImageUrl(id)
                val extract = fetchWikipediaExtract(id) ?: bestDesc.takeIf { it.isNotBlank() }

                EntityInfo(
                    id = id,
                    label = bestLabel.ifBlank { query },
                    description = bestDesc,
                    imageUrl = imageUrl,
                    extract = extract
                )
            } catch (_: Exception) {
                null
            }
        }

    private fun fetchImageUrl(entityId: String): String? {
        return try {
            val url = "$WD_API?action=wbgetclaims&entity=$entityId&property=P18&format=json"
            val root = getJson(url) ?: return null
            val claims = root.optJSONObject("claims")?.optJSONArray("P18") ?: return null
            if (claims.length() == 0) return null
            val mainsnak = claims.getJSONObject(0).optJSONObject("mainsnak")
            val datavalue = mainsnak?.optJSONObject("datavalue")
            val filename = datavalue?.optString("value") ?: return null
            if (filename.isBlank()) return null
            // Commons Special:FilePath redirects to actual file; use thumb via Special:Redirect
            val encoded = filename.replace(" ", "_")
            "https://commons.wikimedia.org/wiki/Special:FilePath/${enc(encoded)}?width=640"
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchWikipediaExtract(entityId: String): String? {
        return try {
            // Get sitelink to enwiki or ptwiki
            val entityUrl = "$WD_API?action=wbgetentities&ids=$entityId&props=sitelinks&format=json"
            val root = getJson(entityUrl) ?: return null
            val entity = root.optJSONObject("entities")?.optJSONObject(entityId) ?: return null
            val sitelinks = entity.optJSONObject("sitelinks") ?: return null
            val title = sitelinks.optJSONObject("ptwiki")?.optString("title")
                ?: sitelinks.optJSONObject("enwiki")?.optString("title")
                ?: return null
            val lang = if (sitelinks.optJSONObject("ptwiki") != null) "pt" else "en"
            val extractUrl = "https://$lang.wikipedia.org/w/api.php?action=query" +
                "&prop=extracts&exintro=1&explaintext=1&titles=${enc(title)}&format=json"
            val wp = getJson(extractUrl) ?: return null
            val pages = wp.optJSONObject("query")?.optJSONObject("pages") ?: return null
            val keys = pages.keys()
            if (!keys.hasNext()) return null
            val page = pages.getJSONObject(keys.next())
            page.optString("extract").trim().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun getJson(urlStr: String): JSONObject? {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }.let { JSONObject(it) }
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String): String =
        URLEncoder.encode(s, Charsets.UTF_8.name()).replace("+", "%20")
}
