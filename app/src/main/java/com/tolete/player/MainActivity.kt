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
import com.tolete.player.ui.ExternalMiniPlayer
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    private var showSplash by mutableStateOf(true)
    private var externalAudioUri by mutableStateOf<Uri?>(null)
    private var externalTitle by mutableStateOf<String?>("Lyron")
    private var externalArtist by mutableStateOf<String?>("")

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

    private val importM3u = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val name = uri.lastPathSegment?.substringAfterLast('/')?.removeSuffix(".m3u")?.removeSuffix(".M3U")
                ?: "Importada"
            contentResolver.openInputStream(uri)?.use { stream ->
                val text = BufferedReader(InputStreamReader(stream)).readText()
                val id = viewModel.importPlaylistM3u(name, text)
                Toast.makeText(
                    this,
                    if (id != null) "Playlist importada" else "Nenhuma faixa correspondente na biblioteca",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Falha ao importar", Toast.LENGTH_SHORT).show()
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
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "custom.ttf"
            val dest = File(filesDir, "fonts").apply { mkdirs() }.let { dir ->
                File(dir, name.substringAfterLast(':').ifBlank { "custom.ttf" })
            }
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            viewModel.setLyricsFontPath(dest.absolutePath)
            Toast.makeText(this, "Fonte das letras carregada", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Falha ao carregar fonte", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickUiFont = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "ui_font.ttf"
            val dest = File(filesDir, "fonts").apply { mkdirs() }.let { dir ->
                File(dir, "ui_" + name.substringAfterLast(':').ifBlank { "ui_font.ttf" })
            }
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            viewModel.setUiFontPath(dest.absolutePath)
            Toast.makeText(this, "Fonte do player carregada", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
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
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(pendingBackupJson.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, "Backup exportado", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Falha ao exportar backup", Toast.LENGTH_SHORT).show()
        }
    }

    private val importBackup = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val json = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            val ok = viewModel.importBackup(json)
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
    }

    /** Apaga permanentemente; no Android 11+ pode abrir diálogo do sistema. */
    private fun requestPermanentDelete(tracks: List<com.tolete.player.data.Track>) {
        if (tracks.isEmpty()) return
        val needConfirm = viewModel.deleteTracksPermanent(tracks)
        if (needConfirm.isEmpty()) {
            Toast.makeText(this, "Música(s) excluída(s)", Toast.LENGTH_SHORT).show()
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                pendingDeleteIds = tracks.map { it.id }.toSet()
                val pi = MediaStore.createDeleteRequest(contentResolver, needConfirm)
                deleteMediaRequest.launch(
                    IntentSenderRequest.Builder(pi.intentSender).build()
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Não foi possível excluir: ${e.message}", Toast.LENGTH_LONG).show()
                pendingDeleteIds = emptySet()
            }
        } else {
            // API < 30: tenta de novo e remove da lib mesmo assim
            viewModel.removeTracksFromLibrary(tracks.map { it.id }.toSet())
            Toast.makeText(this, "Removido da biblioteca", Toast.LENGTH_SHORT).show()
        }
    }


    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* sem ação */ }

    private val requestReadAudio = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val ok = grants.values.any { it }
        if (ok) {
            viewModel.setAllFilesMode(true)
            Toast.makeText(this, "Permissão concedida — escaneando músicas", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permissão negada", Toast.LENGTH_SHORT).show()
        }
    }

    /** Após voltar das configurações de "Todos os arquivos". */
    private var pendingAllFilesEnable = false

    override fun onResume() {
        super.onResume()
        if (pendingAllFilesEnable) {
            pendingAllFilesEnable = false
            if (hasAllFilesAccess()) {
                viewModel.setAllFilesMode(true)
                Toast.makeText(this, "Acesso total ativado — escaneando músicas", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
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
        } else if (Build.VERSION.SDK_INT >= 33) {
            requestReadAudio.launch(arrayOf(Manifest.permission.READ_MEDIA_AUDIO))
        } else {
            requestReadAudio.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
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
            externalAudioUri = uri
            externalTitle = intent.getStringExtra(Intent.EXTRA_TITLE) ?: uri.lastPathSegment ?: "Música"
            externalArtist = ""
            showSplash = false
            // Toca via ViewModel
            try {
                viewModel.playExternalUri(uri, externalTitle ?: "Música")
            } catch (_: Exception) {}
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Preferência de splash lida antes do primeiro frame (evita side-effect na composição)
        if (!getSharedPreferences("tolete", MODE_PRIVATE).getBoolean("splash_enabled", true)) {
            showSplash = false
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
                val position by viewModel.positionMs.collectAsStateWithLifecycle()
                val duration by viewModel.durationMs.collectAsStateWithLifecycle()
                Box {
                // ViewModel já inicia o carregamento da biblioteca no init (em paralelo à splash)
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
                        onExportBackup = {
                            pendingBackupJson = viewModel.exportBackup()
                            exportBackup.launch("lyron_backup.json")
                        },
                        onImportBackup = {
                            importBackup.launch(arrayOf("application/json", "text/plain", "*/*"))
                        }
                    )
                }

                if (externalAudioUri != null && !showSplash) {
                    ExternalMiniPlayer(
                        title = externalTitle ?: "Música",
                        artist = externalArtist ?: "",
                        isPlaying = isPlaying,
                        positionMs = position,
                        durationMs = duration,
                        onPlayPause = { viewModel.togglePlayPause() },
                        onSeek = { frac ->
                            val d = duration
                            if (d > 0) viewModel.seekTo((frac * d).toLong())
                        },
                        onDismiss = { externalAudioUri = null },
                        onOpenFull = { externalAudioUri = null }
                    )
                }
                } // Box
            }
        }
    }
}
