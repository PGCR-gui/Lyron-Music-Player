package com.tolete.player.data

import java.text.Normalizer
import java.util.Locale

/**
 * Chaves usadas para organizar músicas, artistas e álbuns em ordem alfabética
 * (listas, grupos e o índice lateral A–Z).
 *
 * Com `firstLetterOrder` ativo — opção "Ordem alfabética pela primeira letra" em
 * Configurações › Biblioteca — textos que começam com caracteres que não são letras
 * nem números (`&`, `(`, `'`, `-`, `♪`…) são organizados pela primeira letra, e as
 * letras acentuadas valem pela letra base ("Ébola" conta como E, "Ñ" como N).
 * Números não mudam: um texto que começa por dígito continua ordenado pelo dígito.
 *
 * Desligado, vale o texto como está (só minúsculas), exatamente como antes.
 */
object AlphaSort {

    /** Diacríticos que sobram depois de decompor (NFD): acentos, cedilha, til… */
    private val marks = Regex("\\p{M}")

    /** Minúsculas sem acentos/diacríticos. */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(marks, "")
            .lowercase(Locale.ROOT)

    /**
     * Chave de ordenação de [text]. Sempre minúscula; com [firstLetterOrder] ativo,
     * desconsidera os caracteres iniciais que não são letras nem números.
     */
    fun key(text: String?, firstLetterOrder: Boolean): String {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return raw
        if (!firstLetterOrder) return raw.lowercase(Locale.ROOT)
        val norm = normalize(raw)
        var start = 0
        while (start < norm.length && !norm[start].isLetterOrDigit()) start++
        return if (start == 0) norm else norm.substring(start)
    }

    /**
     * Letra do índice lateral (A–Z; qualquer outra coisa vira `#`) da posição [position]
     * da chave de [text]. Usado pelo trilho de letras e pelo trilho de prefixos (DA, DB…).
     */
    fun indexLetter(text: String?, position: Int, firstLetterOrder: Boolean): Char {
        val c = key(text, firstLetterOrder).getOrNull(position) ?: return '#'
        // "É" vale como E no índice também quando a opção está desligada (como sempre foi).
        val base = normalize(c.toString()).firstOrNull() ?: return '#'
        return if (base in 'a'..'z') base.uppercaseChar() else '#'
    }
}
