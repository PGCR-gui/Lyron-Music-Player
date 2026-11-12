package com.tolete.player.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.text.format.Formatter
import com.tolete.player.PlaybackService
import com.tolete.player.visualizer.MilkPresetStore
import java.io.File

/**
 * Cálculo de uso de espaço e limpeza de dados do app (tela "Gerenciar espaço").
 *
 * Importante: a pasta de dados do Lyron (LyronStorage.root) pode ser uma pasta escolhida pelo
 * usuário (inclusive uma que contenha músicas). Por isso NUNCA apagamos a pasta inteira:
 * só os itens que o próprio app cria (playlists, favoritos, histórico, fotos e infos).
 * As músicas do usuário e as imagens/vídeos de letras exportados não são tocados.
 */
object StorageManager {

    /**
     * Cada linha da tela "Gerenciar espaço". [closesApp] = depois de limpar, o app é fechado para
     * recarregar tudo do zero (o que está em memória, como favoritos ou configurações, voltaria a ser gravado).
     */
    enum class Kind(val closesApp: Boolean) {
        CACHE(false),
        LIBRARY(true),
        LYRON_DATA(true),
        FONTS_PRESETS(false),
        SETTINGS(true),
        EXPORTS(false)
    }

    data class Item(val kind: Kind, val label: String, val description: String, val bytes: Long)

    data class Usage(
        val items: List<Item>,
        /** Imagens e vídeos de letras exportados: mostrados, mas preservados na limpeza. */
        val exports: Item,
        val cacheBytes: Long
    ) {
        val totalBytes: Long get() = items.sumOf { it.bytes }
    }

    // Itens que o app cria dentro de LyronStorage.root.
    private val LYRON_DATA_NAMES = listOf(
        "playlists",
        "artist_photos",
        "artist_infos",
        "album_infos",
        "favorites.json",
        "playback history.csv"
    )

    fun formatSize(context: Context, bytes: Long): String =
        Formatter.formatFileSize(context, bytes)

    private fun sizeOf(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        if (file.isFile) return file.length()
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.add(file)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = try { dir.listFiles() } catch (_: Exception) { null } ?: continue
            for (c in children) {
                if (c.isDirectory) stack.add(c) else total += c.length()
            }
        }
        return total
    }

    /** Mesmos caminhos de LyronStorage.lyricsImagesDir/lyricsVideosDir, sem criar pastas só para medir/limpar. */
    private fun exportDirs(): List<File> =
        if (LyronStorage.getMode() == LyronStorage.Mode.INTERNAL) {
            listOf(File("/storage/emulated/0/Pictures/Lyron"), File("/storage/emulated/0/Movies/Lyron"))
        } else {
            listOf(File(LyronStorage.root, "Lyrics Images"), File(LyronStorage.root, "Lyrics Videos"))
        }

    private fun lyronDataFiles(): List<File> {
        val root = LyronStorage.root
        return LYRON_DATA_NAMES.map { File(root, it) }
    }

    /** Roda em thread de fundo: percorre pastas. */
    fun computeUsage(context: Context): Usage {
        val app = context.applicationContext
        val filesDir = app.filesDir
        val dataDir = filesDir.parentFile

        val cache = sizeOf(app.cacheDir) + sizeOf(app.externalCacheDir)

        val library = sizeOf(File(filesDir, "library.json")) + sizeOf(File(filesDir, "art"))
        val fontsPresets = sizeOf(File(filesDir, "fonts")) + sizeOf(File(filesDir, "milk"))
        val settings = sizeOf(File(dataDir, "shared_prefs")) + sizeOf(File(dataDir, "databases"))

        // No modo Interno a pasta Lyron fica dentro de filesDir; ainda assim é listada à parte.
        val lyronData = lyronDataFiles().sumOf { sizeOf(it) }

        val exports = exportDirs().sumOf { sizeOf(it) }

        return Usage(
            items = listOf(
                Item(Kind.CACHE, "Cache", "Arquivos temporários (atualizações, edição de tags, vídeos)", cache),
                Item(Kind.LIBRARY, "Biblioteca e capas", "Lista de músicas e capas salvas pelo app", library),
                Item(Kind.LYRON_DATA, "Playlists, favoritos e histórico", "Pasta de dados do Lyron, fotos e infos de artistas", lyronData),
                Item(Kind.FONTS_PRESETS, "Fontes e visualizador", "Fontes importadas e presets .milk", fontsPresets),
                Item(Kind.SETTINGS, "Configurações", "Preferências, Last.fm, equalizador e ajustes", settings)
            ),
            exports = Item(
                Kind.EXPORTS,
                "Letras exportadas",
                "Imagens e vídeos de letras que você gerou (\"Limpar todos os dados\" não apaga)",
                exports
            ),
            cacheBytes = cache
        )
    }

    /** Apaga o conteúdo da pasta (mantém a pasta). Retorna quantos itens não puderam ser removidos. */
    private fun deleteContentsCount(dir: File?): Int {
        if (dir == null || !dir.exists()) return 0
        var failed = 0
        dir.listFiles()?.forEach { if (!it.deleteRecursively()) failed++ }
        return failed
    }

    private fun deleteContents(dir: File?): Boolean = deleteContentsCount(dir) == 0

    private fun deleteOne(file: File): Int =
        if (!file.exists() || file.deleteRecursively()) 0 else 1

    /**
     * Limpa só um item da tela "Gerenciar espaço". Retorna quantos arquivos/pastas NÃO puderam ser
     * removidos (0 = tudo certo). Roda em thread de fundo.
     *
     * Nunca apaga músicas do usuário. Para itens com [Kind.closesApp] o chamador deve chamar
     * [exitApp] quando o resultado for 0.
     */
    fun clearItem(context: Context, kind: Kind): Int {
        val app = context.applicationContext
        val filesDir = app.filesDir
        val dataDir = filesDir.parentFile
        return when (kind) {
            Kind.CACHE -> if (clearCache(app)) 0 else 1

            Kind.LIBRARY ->
                deleteOne(File(filesDir, "library.json")) +
                    deleteOne(File(filesDir, "library.json.tmp")) +
                    deleteContentsCount(File(filesDir, "art"))

            Kind.LYRON_DATA -> clearLyronFolderData()

            Kind.FONTS_PRESETS -> {
                val failed = deleteContentsCount(File(filesDir, "fonts")) +
                    deleteContentsCount(File(filesDir, "milk"))
                // Libera a recópia dos presets embutidos na próxima listagem (eles voltam sozinhos).
                try { MilkPresetStore.clear(app) } catch (_: Exception) { }
                failed
            }

            Kind.SETTINGS -> {
                val prefsDir = File(dataDir, "shared_prefs")
                // Limpa pela API (o SharedPreferences mantém cópia em memória) antes de apagar os arquivos.
                prefsDir.listFiles { f -> f.isFile && f.name.endsWith(".xml") }?.forEach { f ->
                    try {
                        app.getSharedPreferences(f.name.removeSuffix(".xml"), Context.MODE_PRIVATE)
                            .edit().clear().commit()
                    } catch (_: Exception) { }
                }
                deleteContentsCount(prefsDir) + deleteContentsCount(File(dataDir, "databases"))
            }

            Kind.EXPORTS -> {
                var failed = 0
                val removed = ArrayList<String>()
                for (dir in exportDirs()) {
                    dir.listFiles()?.forEach { f ->
                        val paths: List<String> = if (f.isFile) {
                            listOf(f.absolutePath)
                        } else {
                            f.walkTopDown().filter { it.isFile }.map { it.absolutePath }.toList()
                        }
                        if (f.deleteRecursively()) {
                            removed.addAll(paths)
                        } else {
                            failed++
                        }
                    }
                }
                // Avisa a galeria para não mostrar miniaturas de arquivos que já não existem.
                if (removed.isNotEmpty()) {
                    try { MediaScannerConnection.scanFile(app, removed.toTypedArray(), null, null) } catch (_: Exception) { }
                }
                failed
            }
        }
    }

    /** Para o player e encerra o processo (o app abre limpo da próxima vez). */
    fun exitApp(context: Context) {
        val app = context.applicationContext
        try {
            app.stopService(Intent(app, PlaybackService::class.java))
        } catch (_: Exception) { }
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    /** Apaga só o cache. Retorna true se tudo foi removido. */
    fun clearCache(context: Context): Boolean {
        val app = context.applicationContext
        val a = deleteContents(app.cacheDir)
        val b = deleteContents(app.externalCacheDir)
        return a && b
    }

    /** Apaga os arquivos de dados que o app guarda na pasta Lyron. Retorna quantos itens falharam. */
    fun clearLyronFolderData(): Int {
        var failed = 0
        for (f in lyronDataFiles()) {
            if (f.exists() && !f.deleteRecursively()) failed++
        }
        return failed
    }

    /**
     * Apaga os dados internos do app (equivale a "Limpar dados" do sistema) e encerra o processo.
     * Só retorna se o sistema recusar; nesse caso faz a limpeza manual e encerra mesmo assim.
     */
    fun wipeAppDataAndExit(context: Context) {
        val app = context.applicationContext
        try {
            app.stopService(Intent(app, PlaybackService::class.java))
        } catch (_: Exception) { }

        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val requested = try { am.clearApplicationUserData() } catch (_: Exception) { false }
        if (requested) return // o sistema apaga tudo e mata o processo

        // Plano B: limpeza manual.
        val dataDir = app.filesDir.parentFile
        deleteContents(app.filesDir)
        deleteContents(app.cacheDir)
        deleteContents(app.externalCacheDir)
        deleteContents(File(dataDir, "shared_prefs"))
        deleteContents(File(dataDir, "databases"))
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
