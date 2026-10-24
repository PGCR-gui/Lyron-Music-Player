package com.tolete.player.data

/** Configurações da aba Biblioteca relacionadas a separar artistas e gêneros das tags. */
data class LibrarySettings(
    val splitArtists: Boolean = false,
    /** Separadores de artistas, separados por espaço. Ex.: "; / feat." */
    val artistSeparators: String = "; /",
    /**
     * Nomes de artistas (um por linha ou separados por vírgula/ponto-e-vírgula)
     * que NÃO devem ser divididos mesmo com "separar artistas" ativo.
     * Comparação case-insensitive; se o valor bruto da tag bater com algum,
     * a tag inteira é mantida como um único artista.
     */
    val artistExceptions: String = "",
    val splitGenres: Boolean = false,
    /** Separadores de gêneros, separados por espaço. */
    val genreSeparators: String = "; /",
    /** Pastas a ignorar (uma por linha; compara com o caminho do arquivo). */
    val ignoredFolders: String = "",
    /** Duração mínima em segundos (2–50). Faixas menores são excluídas. 0 = desligado. */
    val minDurationSec: Int = 0
)

/**
 * Divide um valor de tag ("Artista A; Artista B") em vários nomes.
 * [separators] é uma lista de separadores separados por espaço (ex.: "; / feat.").
 * Sem separadores, ou se nada casar, devolve o valor original como único item.
 * [exceptions] lista de nomes brutos que não devem ser divididos (case-insensitive).
 */
class TagSplitter(
    separators: String,
    exceptions: String = ""
) {

    private val exceptionSet: Set<String> = exceptions
        .split(Regex("\\n+"))  // um artista por linha
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { it.lowercase() }
        .toSet()

    private val regex: Regex? = separators
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .sortedByDescending { it.length } // "&amp;" antes de "&"
        .takeIf { it.isNotEmpty() }
        ?.joinToString("|") { Regex.escape(it) }
        ?.let { Regex(it, RegexOption.IGNORE_CASE) }

    fun split(raw: String): List<String> {
        if (raw.trim().lowercase() in exceptionSet) return listOf(raw.trim())
        val r = regex ?: return listOf(raw)
        val parts = raw.split(r)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        return if (parts.isEmpty()) listOf(raw) else parts
    }
}
