package com.tolete.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exporta o manifesto + textos dos cards da retrospectiva para
 * Lyron/Retrospectives/<periodo>_<timestamp>/.
 *
 * Os 10 cards seguem o roteiro de Retrospectiva.zip (intro, tempo, tops, fechamento).
 * A geração de vídeo 8s pode ser enfileirada depois a partir deste manifesto.
 */
object RetrospectiveExporter {

    data class ChartRow(val name: String, val artist: String, val plays: Int)

    private val fullWeekdays = mapOf(
        "Dom" to "Domingo", "Seg" to "Segunda-feira", "Ter" to "Terça-feira", "Qua" to "Quarta-feira",
        "Qui" to "Quinta-feira", "Sex" to "Sexta-feira", "Sáb" to "Sábado"
    )
    private val fullMonths = mapOf(
        "Jan" to "Janeiro", "Fev" to "Fevereiro", "Mar" to "Março", "Abr" to "Abril", "Mai" to "Maio",
        "Jun" to "Junho", "Jul" to "Julho", "Ago" to "Agosto", "Set" to "Setembro", "Out" to "Outubro",
        "Nov" to "Novembro", "Dez" to "Dezembro"
    )

    /** Semana e ano usam abreviações nos gráficos; nos cards o nome aparece por extenso. */
    private fun fullPeriodName(label: String): String =
        fullWeekdays[label] ?: fullMonths[label] ?: label

    /** Frase do card de tempo total, conforme as horas estimadas; null fora das faixas definidas. */
    fun timeMessage(hours: Double): String? = when {
        hours >= 1461.0 -> "Car----! Você ouviu o equivalente a dois meses direto de Música!!"
        hours >= 730.5 -> "Uau! Você ouviu música o bastante pra completar mais de um mês só ouvindo música!"
        hours >= 336.0 -> "Caramba! Você passou tempo o suficiente pra completar 2 semanas direto ouvindo música!"
        hours > 168.0 && hours < 336.0 -> "É como se você tivesse passado mais de uma semana inteira direto ouvindo música!"
        hours > 24.0 && hours <= 40.0 -> "É como se você tivesse passado pelo menos um dia inteiro só escutando música direto!"
        else -> null
    }

    /**
     * @return caminho absoluto da pasta gerada
     */
    fun exportFromRaw(
        context: Context,
        user: String,
        rangeLabel: String,
        periodTitle: String,
        totalPlays: Int,
        bucketLabels: List<String>,
        bucketCounts: List<Int>,
        topTracks: List<ChartRow>,
        topArtists: List<ChartRow>,
        topAlbums: List<ChartRow>
    ): String {
        LyronStorage.init(context)
        val root = File(LyronStorage.root, "Retrospectives")
        root.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safePeriod = periodTitle.replace(Regex("[^\\w\\-]+"), "_").take(40)
        val dir = File(root, "${rangeLabel}_${safePeriod}_$stamp")
        dir.mkdirs()

        val peakIdx = bucketCounts.indices.maxByOrNull { bucketCounts[it] } ?: 0
        val peakLabel = fullPeriodName(bucketLabels.getOrElse(peakIdx) { "—" })
        val peakCount = bucketCounts.getOrElse(peakIdx) { 0 }
        val activeDays = bucketCounts.count { it > 0 }

        // Se o weekly artist chart vier vazio, deriva artistas a partir das top tracks
        val artistsEffective = if (topArtists.isNotEmpty()) {
            topArtists
        } else {
            topTracks
                .filter { it.artist.isNotBlank() }
                .groupBy { it.artist.trim() }
                .map { (name, rows) -> ChartRow(name, "", rows.sumOf { it.plays }) }
                .sortedByDescending { it.plays }
        }
        val albumsEffective = if (topAlbums.isNotEmpty()) topAlbums else emptyList()

        // Last.fm não expõe duração: estima 3,5 min por play (pode ser refinado com a biblioteca local)
        val estMinutes = (totalPlays * 3.5).toInt()
        val timeLabel = if (estMinutes >= 60) {
            val h = estMinutes / 60
            val m = estMinutes % 60
            if (m == 0) "$h h" else "$h h $m min"
        } else {
            "$estMinutes min"
        }

        val cards = listOf(
            mapOf(
                "id" to "001",
                "title" to periodTitle,
                "body" to "Sua retrospectiva!\nVamos dar uma olhada no que você tem ouvido.",
                "icons" to "musicas",
                "colorFrom" to "theme"
            ),
            mapOf(
                "id" to "002",
                "title" to "Você ouviu por",
                "body" to buildString {
                    append("$timeLabel\n$totalPlays plays • ${topTracks.size.coerceAtLeast(1)} músicas no ranking • $activeDays períodos ativos")
                    timeMessage(estMinutes / 60.0)?.let { append("\n").append(it) }
                },
                "icons" to "musicas",
                "colorFrom" to "theme"
            ),
            mapOf(
                "id" to "003",
                "title" to "Seu artista mais ouvido",
                "body" to (artistsEffective.firstOrNull()?.let { "${it.name}\n${it.plays} plays" } ?: "—"),
                "icons" to "artistas",
                "colorFrom" to "artist"
            ),
            mapOf(
                "id" to "004",
                "title" to "Artistas mais ouvidos",
                "body" to artistsEffective.take(5).mapIndexed { i, a ->
                    "${i + 1}. ${a.name} — ${a.plays} plays"
                }.joinToString("\n").ifBlank { "—" },
                "icons" to "artistas",
                "colorFrom" to "artist_3"
            ),
            mapOf(
                "id" to "005",
                "title" to "Sua música mais ouvida",
                "body" to (topTracks.firstOrNull()?.let {
                    "${it.name}\n${it.artist}\n${it.plays} plays"
                } ?: "—"),
                "icons" to "musicas",
                "colorFrom" to "track"
            ),
            mapOf(
                "id" to "006",
                "title" to "Músicas mais ouvidas",
                "body" to topTracks.take(5).mapIndexed { i, t ->
                    "${i + 1}. ${t.name} — ${t.artist} (${t.plays})"
                }.joinToString("\n").ifBlank { "—" },
                "icons" to "musicas",
                "colorFrom" to "track_3"
            ),
            mapOf(
                "id" to "007",
                "title" to "Seu álbum mais ouvido",
                "body" to (albumsEffective.firstOrNull()?.let {
                    "${it.name}\n${it.artist}\n${it.plays} plays"
                } ?: "—"),
                "icons" to "albuns",
                "colorFrom" to "album"
            ),
            mapOf(
                "id" to "008",
                "title" to "Álbuns mais ouvidos",
                "body" to albumsEffective.take(5).mapIndexed { i, a ->
                    "${i + 1}. ${a.name} — ${a.artist} (${a.plays})"
                }.joinToString("\n").ifBlank { "—" },
                "icons" to "albuns",
                "colorFrom" to "album_3"
            ),
            mapOf(
                "id" to "009",
                "title" to "Seus gêneros favoritos",
                "body" to "Calculado a partir das tags de gênero das músicas mais ouvidas na sua biblioteca.",
                "icons" to "generos",
                "colorFrom" to "varied"
            ),
            mapOf(
                "id" to "010",
                "title" to "Essa é sua retrospectiva!",
                "body" to buildString {
                    append("Maior período: $peakLabel • $peakCount plays\n")
                    append("Artista: ${artistsEffective.firstOrNull()?.name ?: "—"}\n")
                    append("Música: ${topTracks.firstOrNull()?.name ?: "—"}\n")
                    append("Álbum: ${albumsEffective.firstOrNull()?.name ?: "—"}")
                },
                "icons" to "lastfm",
                "colorFrom" to "theme"
            )
        )

        cards.forEach { card ->
            val id = card["id"] as String
            File(dir, "$id.txt").writeText(
                "${card["title"]}\n\n${card["body"]}\n\nicons=${card["icons"]}\ncolorFrom=${card["colorFrom"]}\n"
            )
        }

        val manifest = JSONObject().apply {
            put("user", user)
            put("range", rangeLabel)
            put("period", periodTitle)
            put("totalPlays", totalPlays)
            put("estimatedMinutes", estMinutes)
            put("bucketLabels", JSONArray(bucketLabels))
            put("bucketCounts", JSONArray(bucketCounts))
            put("topTracks", JSONArray(topTracks.map {
                JSONObject().put("name", it.name).put("artist", it.artist).put("plays", it.plays)
            }))
            put("topArtists", JSONArray(artistsEffective.map {
                JSONObject().put("name", it.name).put("artist", it.artist).put("plays", it.plays)
            }))
            put("topAlbums", JSONArray(albumsEffective.map {
                JSONObject().put("name", it.name).put("artist", it.artist).put("plays", it.plays)
            }))
            put("cards", JSONArray(cards.map { c ->
                JSONObject().apply { c.forEach { (k, v) -> put(k, v) } }
            }))
            put(
                "note",
                "Cards de texto gerados. Vídeos 8s com ícones flutuantes e áudio podem ser renderizados a partir deste manifesto."
            )
        }
        File(dir, "manifest.json").writeText(manifest.toString(2))
        File(dir, "README.txt").writeText(
            "Retrospectiva Lyron — $periodTitle\n" +
                "Usuário Last.fm: @$user\n" +
                "Gerado em: $stamp\n\n" +
                "Cada 00x.txt é um card. Use manifest.json para renderizar vídeos de 8s " +
                "(ícones flutuantes + faixa das top 10 / regra especial por card).\n"
        )
        return dir.absolutePath
    }
}
