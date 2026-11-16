package com.tolete.player.ui

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Registro persistente de uma letra que já foi preparada para contribuição. */
data class LyricsContribution(
    val id: String,
    val title: String,
    val artist: String,
    val syncBy: String,
    val createdAt: Long,
    val relativePath: String,
    val lrcFile: File,
    val txtFile: File
)

/** Armazena no diretório privado do app os arquivos que podem ser compartilhados novamente. */
object LyricsContributionRepository {
    private const val ROOT_DIRECTORY = "lyric_contributions"
    private const val METADATA_FILE = "contribution.json"
    private const val LEGACY_CACHE_DIRECTORY = "updates/lyric_contributions"

    fun save(
        context: Context,
        title: String,
        artist: String,
        syncBy: String,
        relativePath: String,
        lrcName: String,
        txtName: String,
        lrcText: String,
        txtText: String
    ): LyricsContribution {
        val id = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        val directory = File(root(context), id)
        check(directory.mkdirs()) { "Não foi possível criar a pasta da contribuição" }
        val createdAt = System.currentTimeMillis()
        val lrcFile = File(directory, lrcName).apply { writeText(lrcText, Charsets.UTF_8) }
        val txtFile = File(directory, txtName).apply { writeText(txtText, Charsets.UTF_8) }
        writeMetadata(
            directory = directory,
            id = id,
            title = title,
            artist = artist,
            syncBy = syncBy,
            createdAt = createdAt,
            relativePath = relativePath,
            lrcName = lrcName,
            txtName = txtName
        )
        return LyricsContribution(
            id = id,
            title = title,
            artist = artist,
            syncBy = syncBy,
            createdAt = createdAt,
            relativePath = relativePath,
            lrcFile = lrcFile,
            txtFile = txtFile
        )
    }

    fun list(context: Context): List<LyricsContribution> {
        migrateLegacyCache(context)
        return root(context).listFiles()?.asSequence()
            ?.filter { it.isDirectory }
            ?.mapNotNull(::readContribution)
            ?.sortedByDescending { it.createdAt }
            ?.toList()
            .orEmpty()
    }

    fun delete(context: Context, contribution: LyricsContribution): Boolean {
        val base = root(context).canonicalFile
        val target = File(base, contribution.id).canonicalFile
        if (target.parentFile != base || !target.isDirectory) return false
        val deleted = target.deleteRecursively()
        return deleted || !target.exists()
    }

    private fun root(context: Context): File = File(context.filesDir, ROOT_DIRECTORY)

    private fun readContribution(directory: File): LyricsContribution? {
        return try {
            val metadata = JSONObject(File(directory, METADATA_FILE).readText(Charsets.UTF_8))
            val lrcName = metadata.getString("lrcFile")
            val txtName = metadata.getString("txtFile")
            val lrcFile = safeChild(directory, lrcName) ?: return null
            val txtFile = safeChild(directory, txtName) ?: return null
            if (!lrcFile.isFile || !txtFile.isFile) return null
            LyricsContribution(
                id = metadata.getString("id"),
                title = metadata.optString("title"),
                artist = metadata.optString("artist"),
                syncBy = metadata.optString("sync_by"),
                createdAt = metadata.optLong("createdAt", directory.lastModified()),
                relativePath = metadata.optString("path"),
                lrcFile = lrcFile,
                txtFile = txtFile
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun safeChild(directory: File, name: String): File? {
        if (name.isBlank()) return null
        val parent = directory.canonicalFile
        val child = File(parent, name).canonicalFile
        return child.takeIf { it.parentFile == parent }
    }

    private fun writeMetadata(
        directory: File,
        id: String,
        title: String,
        artist: String,
        syncBy: String,
        createdAt: Long,
        relativePath: String,
        lrcName: String,
        txtName: String
    ) {
        val metadata = JSONObject()
            .put("id", id)
            .put("title", title)
            .put("artist", artist)
            .put("sync_by", syncBy)
            .put("createdAt", createdAt)
            .put("path", relativePath)
            .put("lrcFile", lrcName)
            .put("txtFile", txtName)
        File(directory, METADATA_FILE).writeText(metadata.toString(2), Charsets.UTF_8)
    }

    /** Migra para o armazenamento persistente exportações da versão anterior ainda no cache. */
    private fun migrateLegacyCache(context: Context) {
        val legacyRoot = File(context.cacheDir, LEGACY_CACHE_DIRECTORY)
        val legacyFolders = legacyRoot.listFiles()?.filter { it.isDirectory }.orEmpty()
        if (legacyFolders.isEmpty()) return

        val persistentRoot = root(context).apply { mkdirs() }
        legacyFolders.forEach { sourceDirectory ->
            val lrcFiles = sourceDirectory.listFiles()?.filter { it.isFile && it.extension.equals("lrc", true) }.orEmpty()
            val txtFiles = sourceDirectory.listFiles()?.filter { it.isFile && it.extension.equals("txt", true) }.orEmpty()
            lrcFiles.forEach lrcLoop@ { sourceLrc ->
                val sourceTxt = txtFiles.firstOrNull {
                    it.nameWithoutExtension.equals(sourceLrc.nameWithoutExtension, ignoreCase = true)
                } ?: return@lrcLoop
                val stableKey = "legacy_${sourceDirectory.name}_${Integer.toHexString(sourceLrc.nameWithoutExtension.hashCode())}"
                val destination = File(persistentRoot, stableKey)
                if (destination.exists() && readContribution(destination) != null) {
                    // A cópia persistente já existe. Remova a origem antiga para que ela não
                    // recrie o item caso o usuário exclua a contribuição mais tarde.
                    sourceLrc.delete()
                    sourceTxt.delete()
                    return@lrcLoop
                }
                if (destination.exists()) destination.deleteRecursively()
                try {
                    destination.mkdirs()
                    val lrcFile = File(destination, sourceLrc.name)
                    val txtFile = File(destination, sourceTxt.name)
                    sourceLrc.copyTo(lrcFile, overwrite = true)
                    sourceTxt.copyTo(txtFile, overwrite = true)

                    val snippet = extractIndexEntry(sourceTxt)
                    val splitName = sourceLrc.nameWithoutExtension.split(" - ", limit = 2)
                    val artist = splitName.firstOrNull().orEmpty()
                    val title = splitName.getOrNull(1).orEmpty()
                    writeMetadata(
                        directory = destination,
                        id = stableKey,
                        title = title,
                        artist = artist,
                        syncBy = snippet?.optString("sync_by").orEmpty(),
                        createdAt = sourceDirectory.name.toLongOrNull() ?: sourceDirectory.lastModified(),
                        relativePath = snippet?.optString("path")?.takeIf { it.isNotBlank() }
                            ?: "letras/${sourceLrc.name}",
                        lrcName = lrcFile.name,
                        txtName = txtFile.name
                    )
                    // Só removemos a origem depois que arquivos e metadados persistentes foram gravados.
                    sourceLrc.delete()
                    sourceTxt.delete()
                } catch (_: Exception) {
                    destination.deleteRecursively()
                }
            }
            if (sourceDirectory.listFiles().isNullOrEmpty()) sourceDirectory.delete()
        }
        if (legacyRoot.listFiles().isNullOrEmpty()) legacyRoot.deleteRecursively()
    }

    private fun extractIndexEntry(txtFile: File): JSONObject? = try {
        val text = txtFile.readText(Charsets.UTF_8)
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end < start) null else JSONObject(text.substring(start, end + 1))
    } catch (_: Exception) {
        null
    }
}
