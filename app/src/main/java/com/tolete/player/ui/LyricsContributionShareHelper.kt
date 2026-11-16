package com.tolete.player.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.tolete.player.data.LyricLine
import com.tolete.player.data.LyricsEmbedder
import com.tolete.player.data.Track
import org.json.JSONObject
import java.io.File

/** Prepara, guarda e compartilha os arquivos de contribuição de uma letra. */
object LyricsContributionShareHelper {
    const val SYNC_BY_PREFERENCE_KEY = "lyrics_sync_by"
    private const val INDEX_LYRICS_DIRECTORY = "letras"

    fun share(context: Context, track: Track, lines: List<LyricLine>, syncBy: String) {
        val author = syncBy.trim()
        if (author.isBlank()) {
            Toast.makeText(context, "Informe o nome para sync_by", Toast.LENGTH_SHORT).show()
            return
        }
        if (lines.none { it.text.isNotBlank() || it.words.any { word -> word.text.isNotBlank() } }) {
            Toast.makeText(context, "Nenhuma letra para compartilhar", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val baseName = "${safeFilePart(track.artist)} - ${safeFilePart(track.title)}"
            val lrcName = "$baseName.lrc"
            val txtName = "$baseName.txt"
            val relativeLrcPath = "$INDEX_LYRICS_DIRECTORY/$lrcName"
            val lrcText = LyricsEmbedder.linesToLrcText(lines)
            // Montado à mão: JSONObject.toString escapa "/" como "\\/", o que atrapalha na hora de colar no index.json.
            val indexEntry = buildString {
                appendLine("{")
                appendLine("  \"path\": ${jsonString(relativeLrcPath)},")
                appendLine("  \"sync_by\": ${jsonString(author)}")
                append("}")
            }
            val txtText = buildString {
                appendLine("Contribuição de letra para o repositório Lyron")
                appendLine()
                appendLine("1. Adicione o arquivo $lrcName à pasta $INDEX_LYRICS_DIRECTORY/ do repositório.")
                appendLine("2. Dentro da lista \"files\" de index.json, adicione este objeto:")
                appendLine()
                append(indexEntry)
                appendLine()
            }
            val contribution = LyricsContributionRepository.save(
                context = context,
                title = track.title,
                artist = track.artist,
                syncBy = author,
                relativePath = relativeLrcPath,
                lrcName = lrcName,
                txtName = txtName,
                lrcText = lrcText,
                txtText = txtText
            )
            shareSaved(context, contribution)
        } catch (e: Exception) {
            android.util.Log.e("LyricsContribution", "Falha ao preparar arquivos de letra", e)
            Toast.makeText(context, "Não foi possível preparar os arquivos de letra", Toast.LENGTH_LONG).show()
        }
    }

    /** Reenvia exatamente os arquivos guardados no histórico, sem regenerá-los. */
    fun shareSaved(context: Context, contribution: LyricsContribution) {
        try {
            if (!contribution.lrcFile.isFile || !contribution.txtFile.isFile) {
                Toast.makeText(context, "Os arquivos desta contribuição não estão mais disponíveis", Toast.LENGTH_LONG).show()
                return
            }
            val authority = "${context.packageName}.updates"
            val uris = arrayListOf(
                FileProvider.getUriForFile(context, authority, contribution.lrcFile),
                FileProvider.getUriForFile(context, authority, contribution.txtFile)
            )
            val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "text/plain"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Letra de ${contribution.title} — ${contribution.artist}. O arquivo .txt contém o trecho para adicionar em index.json."
                )
                clipData = ClipData.newUri(context.contentResolver, "Arquivos de letra", uris.first()).apply {
                    addItem(ClipData.Item(uris[1]))
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(sendIntent, "Compartilhar arquivos de letra")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            android.util.Log.e("LyricsContribution", "Falha ao compartilhar contribuição salva", e)
            Toast.makeText(context, "Não foi possível compartilhar esta contribuição", Toast.LENGTH_LONG).show()
        }
    }

    /** String JSON válida, mas sem escapar "/" (o org.json escapa, gerando "letras\\/arquivo.lrc"). */
    private fun jsonString(value: String): String = JSONObject.quote(value).replace("\\/", "/")

    private fun safeFilePart(value: String): String = value
        .trim()
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '.')
        .take(80)
        .ifBlank { "Desconhecido" }
}
