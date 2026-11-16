package com.tolete.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.MediaStore
import android.app.Activity
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.material3.darkColorScheme
import com.tolete.player.ui.MusicViewModel
import com.tolete.player.ui.SplashScreen
import com.tolete.player.ui.TolereApp
import com.tolete.player.ui.UpdateHost
import com.tolete.player.ui.ChangelogOnUpdateDialog
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    private var showSplash by mutableStateOf(true)
    private var openNowPlayingRequested by mutableStateOf(false)
    private var openNowPlayingTrackId by mutableStateOf<String?>(null)
    private var openNowPlayingLastFmInfo by mutableStateOf(false)
    /** Toque na notificação do scrobbler: abre o dashboard do Last.fm. */
    private var openLastFmDashboard by mutableStateOf(false)
    /** Toque na notificação de retrospectiva: diálogo Last.fm vs histórico local. */
    private var openRetrospectiveChoice by mutableStateOf(false)
    private var retrospectivePeriod by mutableStateOf<String?>(null)

    companion object {
        const val EXTRA_OPEN_NOW_PLAYING = "com.tolete.player.extra.OPEN_NOW_PLAYING"
        const val EXTRA_OPEN_NOW_PLAYING_TRACK_ID = "com.tolete.player.extra.OPEN_NOW_PLAYING_TRACK_ID"
    }

    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                return@registerForActivityResult
            }
            viewModel.addFolder(uri)
        }
    }

    private val pickIgnoredFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) { }
        val docId = try {
            android.provider.DocumentsContract.getTreeDocumentId(uri)
        } catch (_: Exception) {
            uri.toString()
        }
        // primary:WhatsApp/Media -> "WhatsApp/Media" ou docId completo
        val rel = docId.substringAfter(':', docId).trim('/')
        val label = rel.ifBlank { docId }
        val current = viewModel.librarySettings.value.ignoredFolders
        val lines = current.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (lines.none { it.equals(label, ignoreCase = true) }) {
            lines.add(label)
            viewModel.setIgnoredFolders(lines.joinToString("\n"))
            Toast.makeText(this, "Pasta ignorada: $label", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Pasta já está na lista", Toast.LENGTH_SHORT).show()
        }
    }


    private val importM3u = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/')?.removeSuffix(".m3u")?.removeSuffix(".M3U")
            ?: "Importada"
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Não foi possível ler o arquivo")
                    var decoded = String(bytes, Charsets.UTF_8)
                    // Playlists antigas (Windows) podem estar em ISO-8859-1.
                    if (decoded.contains('\uFFFD')) decoded = String(bytes, Charsets.ISO_8859_1)
                    decoded
                }
                val r = viewModel.importPlaylistM3u(name, text)
                val msg = when {
                    r.id != null && r.matched < r.total -> "Playlist importada: ${r.matched} de ${r.total} faixas encontradas"
                    r.id != null -> "Playlist importada (${r.matched} faixas)"
                    r.total == 0 -> "O arquivo não tem faixas"
                    else -> "Nenhuma das ${r.total} faixas foi encontrada"
                }
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Falha ao importar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var pendingExportName: String = "playlist"
    private var pendingExportContent: String = ""

    private val exportM3u = registerForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(pendingExportContent.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, "Playlist exportada", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Falha ao exportar", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickFont = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        var lastPath: String? = null
        var ok = 0
        for (uri in uris) {
            try {
                val raw = uri.lastPathSegment?.substringAfterLast('/') ?: "custom.ttf"
                val name = raw.substringAfterLast(':').ifBlank { "custom.ttf" }
                val lower = name.lowercase()
                if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) continue
                val dest = File(filesDir, "fonts").apply { mkdirs() }.let { dir ->
                    File(dir, name)
                }
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
                lastPath = dest.absolutePath
                ok++
            } catch (_: Exception) { }
        }
        if (lastPath != null) {
            viewModel.setLyricsFontPath(lastPath)
            Toast.makeText(
                this,
                if (ok == 1) "Fonte das letras carregada" else "$ok fontes carregadas",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(this, "Falha ao carregar fonte", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickUiFont = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        var lastPath: String? = null
        var ok = 0
        for (uri in uris) {
            try {
                val raw = uri.lastPathSegment?.substringAfterLast('/') ?: "ui_font.ttf"
                val name = raw.substringAfterLast(':').ifBlank { "ui_font.ttf" }
                val lower = name.lowercase()
                if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) continue
                val dest = File(filesDir, "fonts").apply { mkdirs() }.let { dir ->
                    // Mantém o nome original para aparecer na lista de fontes do usuário
                    File(dir, name)
                }
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
                lastPath = dest.absolutePath
                ok++
            } catch (_: Exception) { }
        }
        if (lastPath != null) {
            viewModel.setUiFontPath(lastPath)
            // Também disponível para a foto (share) e botões — lista usa o mesmo diretório
            Toast.makeText(
                this,
                if (ok == 1) "Fonte carregada" else "$ok fontes carregadas",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(this, "Falha ao carregar fonte", Toast.LENGTH_SHORT).show()
        }
    }

    private var pendingBackupJson: String = ""


    // Presets do visualizador: .milk, texturas (jpg/png/tga...) ou .zip com tudo junto
    private val pickMilkFiles = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        Toast.makeText(this, "Importando…", Toast.LENGTH_SHORT).show()
        viewModel.importMilkFiles(uris) { added ->
            val msg = if (added > 0) "$added preset(s) importado(s)" else "Nenhum arquivo .milk encontrado"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private val exportBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val tmp = java.io.File(cacheDir, "lyron_backup.zip")
            val ok = viewModel.exportBackupZip(tmp)
            if (ok) {
                contentResolver.openOutputStream(uri)?.use { out ->
                    tmp.inputStream().use { it.copyTo(out) }
                }
                Toast.makeText(this, "Backup ZIP exportado", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Falha ao gerar backup", Toast.LENGTH_SHORT).show()
            }
            tmp.delete()
        } catch (e: Exception) {
            Toast.makeText(this, "Falha ao exportar backup", Toast.LENGTH_SHORT).show()
        }
    }

    private val importBackup = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val name = uri.lastPathSegment?.lowercase().orEmpty()
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            val ok = if (name.endsWith(".zip") || (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte())) {
                // ZIP: extrai preferences.json e fotos
                viewModel.importBackupZip(bytes)
            } else {
                viewModel.importBackup(bytes.toString(Charsets.UTF_8))
            }
            Toast.makeText(
                this,
                if (ok) "Backup importado" else "Arquivo de backup inválido",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Falha ao importar backup", Toast.LENGTH_SHORT).show()
        }
    }


    private var pendingDeleteIds: Set<String> = emptySet()
    private var deleteInProgress = false

    private val deleteMediaRequest = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Sistema confirmou a exclusão permanente
            viewModel.removeTracksFromLibrary(pendingDeleteIds)
            Toast.makeText(this, "Música(s) excluída(s)", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Exclusão cancelada", Toast.LENGTH_SHORT).show()
        }
        pendingDeleteIds = emptySet()
        deleteInProgress = false
    }

    /** Apaga permanentemente; no Android 11+ pode abrir diálogo do sistema. */
    private fun requestPermanentDelete(tracks: List<com.tolete.player.data.Track>) {
        if (tracks.isEmpty() || deleteInProgress) return
        deleteInProgress = true
        lifecycleScope.launch {
            var awaitingSystemConfirmation = false
            try {
                val needConfirm = withContext(Dispatchers.IO) {
                    viewModel.deleteTracksPermanent(tracks)
                }
                if (needConfirm.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Música(s) excluída(s)", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    pendingDeleteIds = tracks.map { it.id }.toSet()
                    val pi = MediaStore.createDeleteRequest(contentResolver, needConfirm)
                    awaitingSystemConfirmation = true
                    deleteMediaRequest.launch(
                        IntentSenderRequest.Builder(pi.intentSender).build()
                    )
                } else {
                    // API < 30: tenta de novo e remove da lib mesmo assim
                    viewModel.removeTracksFromLibrary(tracks.map { it.id }.toSet())
                    Toast.makeText(this@MainActivity, "Removido da biblioteca", Toast.LENGTH_SHORT).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Não foi possível excluir: ${e.message}", Toast.LENGTH_LONG).show()
                pendingDeleteIds = emptySet()
                awaitingSystemConfirmation = false
            } finally {
                if (!awaitingSystemConfirmation) deleteInProgress = false
            }
        }
    }


    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            com.tolete.player.scrobble.RetrospectiveReminder.checkAndNotify(this)
        }
    }

    private val requestReadAudio = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val ok = grants.values.any { it }
        val wasAllFilesRequest = pendingLegacyAllFilesEnable
        pendingLegacyAllFilesEnable = false
        if (ok) {
            if (wasAllFilesRequest) {
                viewModel.setAllFilesMode(true)
                Toast.makeText(this, "Permissão concedida — escaneando armazenamento", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.setMediaStoreMode(true)
                Toast.makeText(this, "Permissão concedida — lendo o índice de áudio", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Permissão negada", Toast.LENGTH_SHORT).show()
        }
    }

    /** Após voltar das configurações de "Todos os arquivos". */
    private var pendingAllFilesEnable = false
    /** Diferencia o READ_EXTERNAL_STORAGE legado solicitado pelo modo amplo do modo MediaStore. */
    private var pendingLegacyAllFilesEnable = false

    override fun onResume() {
        super.onResume()
        if (pendingAllFilesEnable) {
            pendingAllFilesEnable = false
            if (hasAllFilesAccess()) {
                viewModel.setAllFilesMode(true)
                Toast.makeText(this, "Acesso total ativado — escaneando músicas", Toast.LENGTH_SHORT).show()
            }
        }
        // Catch-up: se já passou das 20h no dia alvo e a notificação ainda não saiu, dispara agora.
        try {
            com.tolete.player.scrobble.RetrospectiveReminder.checkAndNotify(this)
        } catch (_: Exception) { }
    }

    private fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasMediaAudioAccess(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestMediaStorePermission() {
        if (hasMediaAudioAccess()) {
            viewModel.setMediaStoreMode(true)
            Toast.makeText(this, "Permissão já concedida — lendo o índice de áudio", Toast.LENGTH_SHORT).show()
            return
        }
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        requestReadAudio.launch(arrayOf(permission))
    }

    private fun requestAllFilesAccess() {
        if (hasAllFilesAccess()) {
            viewModel.setAllFilesMode(true)
            Toast.makeText(this, "Já autorizado — escaneando músicas", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                pendingAllFilesEnable = true
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                pendingAllFilesEnable = true
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            pendingLegacyAllFilesEnable = true
            requestReadAudio.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeOpenNowPlayingRequest(intent)
        handleIncomingIntent(intent)
    }

    private fun consumeOpenNowPlayingRequest(intent: Intent?) {
        val requested = intent?.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false) == true
        val trackId = intent?.getStringExtra(EXTRA_OPEN_NOW_PLAYING_TRACK_ID)
        if (requested || !trackId.isNullOrBlank()) {
            openNowPlayingRequested = true
            openNowPlayingTrackId = trackId?.takeIf { it.isNotBlank() }
            showSplash = false
        }
        intent?.removeExtra(EXTRA_OPEN_NOW_PLAYING)
        intent?.removeExtra(EXTRA_OPEN_NOW_PLAYING_TRACK_ID)
    }

    private fun handleIncomingIntent(intent: Intent?, recreated: Boolean = false) {
        if (intent == null) return
        val action = intent.action
        if (action == com.tolete.player.scrobble.RetrospectiveReminder.ACTION_OPEN_RETROSPECTIVE) {
            if (!recreated) {
                openRetrospectiveChoice = true
                retrospectivePeriod = intent.getStringExtra(
                    com.tolete.player.scrobble.RetrospectiveReminder.EXTRA_PERIOD
                )
            }
            intent.action = null
            return
        }
        if (action == com.tolete.player.scrobble.ScrobbleNotifier.ACTION_OPEN_NOW_PLAYING_LASTFM_INFO) {
            if (!recreated) {
                openNowPlayingRequested = true
                openNowPlayingTrackId = intent.getStringExtra(
                    com.tolete.player.scrobble.ScrobbleNotifier.EXTRA_NOTIFICATION_TRACK_ID
                )
                    ?.takeIf { it.isNotBlank() }
                openNowPlayingLastFmInfo = true
                showSplash = false
            }
            intent.action = null
            return
        }
        if (action == com.tolete.player.scrobble.ScrobbleNotifier.ACTION_OPEN_LASTFM_DASHBOARD ||
            action == com.tolete.player.scrobble.ScrobbleNotifier.ACTION_SHOW_TRACK_INFO
        ) {
            if (!recreated) openLastFmDashboard = true
            intent.action = null
            return
        }
        val uri: Uri? = when {
            action == Intent.ACTION_VIEW -> intent.data
            action == Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            else -> null
        }
        if (uri != null) {
            val externalIntent = Intent(this, ExternalPlayerActivity::class.java).apply {
                this.action = action
                addFlags(intent.flags and (
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                    ))
                if (action == Intent.ACTION_VIEW) {
                    setDataAndType(uri, intent.type)
                } else {
                    type = intent.type
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = intent.clipData
                }
                intent.getStringExtra(Intent.EXTRA_TITLE)?.let { putExtra(Intent.EXTRA_TITLE, it) }
            }
            startActivity(externalIntent)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        com.tolete.player.data.LyronStorage.init(this)
        com.tolete.player.data.ArtistImageResolver.initialize(this)
        com.tolete.player.data.LastFmClient.initialize(this)
        consumeOpenNowPlayingRequest(intent)
        handleIncomingIntent(intent, recreated = savedInstanceState != null)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        try {
            com.tolete.player.scrobble.RetrospectiveReminder.checkAndNotify(this)
        } catch (_: Exception) { }

        // Preferência de splash lida antes do primeiro frame (evita side-effect na composição)
        if (!getSharedPreferences("tolete", MODE_PRIVATE).getBoolean("splash_enabled", true)) {
            showSplash = false
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box {
                // A splash permanece visualmente intacta; o ViewModel é aquecido após o primeiro frame.
                if (showSplash) {
                    val splashSound = remember {
                        getSharedPreferences("tolete", MODE_PRIVATE)
                            .getBoolean("splash_sound_enabled", true)
                    }
                    SplashScreen(
                        onFinished = { showSplash = false },
                        playSound = splashSound
                    )
                } else {
                    TolereApp(
                        vm = viewModel,
                        onPickFolder = { pickFolder.launch(null) },
                        onPickIgnoredFolder = { pickIgnoredFolder.launch(null) },
                        onExportM3u = { name, content ->
                            pendingExportName = name
                            pendingExportContent = content
                            exportM3u.launch("$name.m3u")
                        },
                        onImportM3u = {
                            importM3u.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "text/plain", "*/*"))
                        },
                        onPickFont = {
                            pickFont.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "*/*"))
                        },
                        onPickUiFont = {
                            pickUiFont.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "*/*"))
                        },
                        onPickMilkFiles = { pickMilkFiles.launch(arrayOf("*/*")) },
                        onDeleteTracks = { tracks -> requestPermanentDelete(tracks) },
                        onRequestAllFilesPermission = { requestAllFilesAccess() },
                        onRequestMediaStorePermission = { requestMediaStorePermission() },
                        onExportBackup = { opts ->
                            viewModel.pendingBackupOptions = opts
                            pendingBackupJson = viewModel.exportBackup(opts)
                            exportBackup.launch("lyron_backup.zip")
                        },
                        onImportBackup = { opts ->
                            viewModel.pendingBackupOptions = opts
                            importBackup.launch(arrayOf("application/zip", "application/json", "*/*"))
                        },
                        openLastFmDashboard = openLastFmDashboard,
                        onOpenLastFmDashboardConsumed = { openLastFmDashboard = false },
                        openRetrospectiveChoice = openRetrospectiveChoice,
                        retrospectivePeriod = retrospectivePeriod,
                        onRetrospectiveChoiceConsumed = {
                            openRetrospectiveChoice = false
                            retrospectivePeriod = null
                        },
                        openNowPlaying = openNowPlayingRequested,
                        openNowPlayingTrackId = openNowPlayingTrackId,
                        openNowPlayingLastFmInfo = openNowPlayingLastFmInfo,
                        onOpenNowPlayingConsumed = {
                            openNowPlayingRequested = false
                            openNowPlayingTrackId = null
                        },
                        onOpenNowPlayingLastFmInfoConsumed = { openNowPlayingLastFmInfo = false }
                    )
                }

                if (!showSplash) {
                    ChangelogOnUpdateDialog()
                    UpdateHost()
                }

                } // Box
            }
        }

        if (showSplash) {
            window.decorView.postOnAnimation {
                if (!isFinishing) prewarmViewModel()
            }
        }
    }

    private fun prewarmViewModel() {
        viewModel
    }
}
