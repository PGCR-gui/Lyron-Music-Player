package com.tolete.player.data

/** Configurações da aba Biblioteca relacionadas a separar artistas e gêneros das tags. */
data class LibrarySettings(
    val splitArtists: Boolean = false,
    /** Separadores de artistas, separados por espaço. Ex.: "; / feat." */
    val artistSeparators: String = "; /",
    /**
     * Nomes de artistas que NÃO devem ser divididos (um por linha somente).
     * Não use vírgula como separador — artistas como "Tyler, the Creator" precisam
     * da vírgula no nome. Comparação case-insensitive por segmento da tag.
     */
    val artistExceptions: String = "",
    val splitGenres: Boolean = false,
    /** Separadores de gêneros, separados por espaço. */
    val genreSeparators: String = "; /",
    /** Pastas a ignorar (uma por linha; compara com o caminho do arquivo). */
    val ignoredFolders: String = "",
    /** Duração mínima em segundos (2–50). Faixas menores são excluídas. 0 = desligado. */
    val minDurationSec: Int = 0,
    /**
     * Ordem alfabética pela primeira letra: textos (títulos, artistas, álbuns) que começam
     * com caracteres que não são letras nem números são organizados pela primeira letra,
     * e letras acentuadas valem pela letra base. Desligado, vale o texto como está.
     * @see com.tolete.player.data.AlphaSort
     */
    val firstLetterOrder: Boolean = false,
    /** ON, WIFI_ONLY ou OFF; Ligado preserva o comportamento atual. */
    val artistAutoFetchMode: String = "ON"
)

/**
 * Divide um valor de tag (por exemplo, "Artista A; Artista B") em vários nomes.
 * Separadores são tokens separados por espaço (ex.: "; / feat."). Exceções, uma por linha,
 * são preservadas quando ocupam um segmento, mesmo que a tag também tenha outros artistas.
 */
class TagSplitter(
    separators: String,
    exceptions: String = ""
) {
    private val separatorTokens = separators
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .distinct()
        .sortedByDescending { it.length }

    private val exceptionNames = exceptions
        .split(Regex("\\r?\\n+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .sortedByDescending { it.length }

    fun split(raw: String): List<String> {
        if (raw.isBlank() || separatorTokens.isEmpty()) return listOf(raw)
        val result = mutableListOf<String>()
        val segment = StringBuilder()

        fun flushSegment() {
            val value = segment.toString().trim()
            if (value.isNotEmpty()) result += value
            segment.setLength(0)
        }

        var index = 0
        while (index < raw.length) {
            val exception = exceptionNames.firstOrNull { candidate ->
                raw.regionMatches(index, candidate, 0, candidate.length, ignoreCase = true) &&
                    segment.toString().isBlank() &&
                    isSegmentEnd(raw, index + candidate.length)
            }
            if (exception != null) {
                val end = index + exception.length
                result += raw.substring(index, end).trim()
                index = end
                continue
            }

            val separator = separatorTokens.firstOrNull { raw.startsWith(it, index, ignoreCase = true) }
            if (separator != null) {
                flushSegment()
                index += separator.length
            } else {
                segment.append(raw[index])
                index++
            }
        }
        flushSegment()
        return result.distinctBy { it.lowercase() }.ifEmpty { listOf(raw) }
    }

    /** Uma exceção só protege um segmento inteiro, não parte de um nome maior. */
    private fun isSegmentEnd(raw: String, from: Int): Boolean {
        var index = from
        while (index < raw.length && raw[index].isWhitespace()) index++
        return index >= raw.length || separatorTokens.any { raw.startsWith(it, index, ignoreCase = true) }
    }
}
