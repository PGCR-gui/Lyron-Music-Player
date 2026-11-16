package com.tolete.player.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tolete.player.R
import com.tolete.player.data.TagReader
import com.tolete.player.data.TagWriter
import com.tolete.player.data.Track
import androidx.core.content.FileProvider
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FileManagerMode {
    /** Navegação livre */
    BROWSE,
    /** Escolher uma pasta e confirmar */
    PICK_FOLDER
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    mode: FileManagerMode = FileManagerMode.BROWSE,
    initialPath: String? = null,
    floatingIconsEnabled: Boolean = true,
    appColors: AppColors = DefaultAppColors,
    backdrop: GraphicsLayer? = null,
    onClose: () -> Unit,
    onFolderPicked: ((String) -> Unit)? = null,
    onTracksMayHaveChanged: (() -> Unit)? = null,
    onPlayFolderTracks: (List<Track>, Int) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val menuVisualStyle = LocalMenuVisualStyle.current
    val scope = rememberCoroutineScope()
    val root = remember {
        Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"
    }
    var currentPath by remember {
        mutableStateOf(
            initialPath?.takeIf { File(it).isDirectory }
                ?: initialPath?.let { File(it).parent }?.takeIf { File(it).isDirectory }
                ?: root
        )
    }
    var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selectionMode = selected.isNotEmpty()
    var clipboard by remember { mutableStateOf<ClipboardOp?>(null) }
    var renameTarget by remember { mutableStateOf<FileEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    var renameUpdateTags by remember { mutableStateOf(true) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMkdir by remember { mutableStateOf(false) }
    var mkdirName by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showTagRename by remember { mutableStateOf(false) }
    var tagRenameFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var showLyricsEditor by remember { mutableStateOf(false) }
    var lyricsEditFile by remember { mutableStateOf<File?>(null) }
    var lyricsEditText by remember { mutableStateOf("") }

    fun reload() {
        scope.launch {
            loading = true
            entries = withContext(Dispatchers.IO) {
                listEntries(currentPath)
            }
            loading = false
            selected = selected.filter { path -> entries.any { it.path == path } }.toSet()
        }
    }

    fun openAudioFromFolder(path: String) {
        scope.launch {
            val result: Pair<List<Track>, Int> = withContext(Dispatchers.IO) {
                val selectedFile = File(path)
                val folder = selectedFile.parentFile ?: return@withContext Pair(emptyList(), -1)
                val audioFiles = folder.listFiles()
                    ?.filter { it.isFile && !it.name.startsWith('.') && it.extension.lowercase() in AUDIO_EXT }
                    .orEmpty()
                val artByAlbum = mutableMapOf<String, String>()
                val folderTracks = audioFiles.map { file ->
                    val tags = TagReader.read(file)
                    val artKey = "${file.parent.orEmpty()}:${tags?.album.orEmpty()}".lowercase()
                    val artPath = artByAlbum[artKey] ?: if (tags?.hasArtwork == true) {
                        try {
                            TagReader.readArtworkBytes(file)?.let { bytes ->
                                val mime = when {
                                    bytes.size >= 8 && bytes[0] == 0x89.toByte() &&
                                        bytes.copyOfRange(1, 4).decodeToString() == "PNG" -> "image/png"
                                    bytes.size >= 12 && bytes.copyOfRange(0, 4).decodeToString() == "RIFF" &&
                                        bytes.copyOfRange(8, 12).decodeToString() == "WEBP" -> "image/webp"
                                    bytes.size >= 6 && bytes.copyOfRange(0, 3).decodeToString() == "GIF" -> "image/gif"
                                    else -> "image/jpeg"
                                }
                                TagWriter.saveCoverPreview(context, bytes, mime)
                            }
                        } catch (_: Exception) { null }
                    } else null
                    if (artPath != null) artByAlbum[artKey] = artPath
                    Track(
                        id = file.absolutePath,
                        title = tags?.title?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
                        artist = tags?.artist.orEmpty(),
                        album = tags?.album.orEmpty(),
                        albumArtist = tags?.albumArtist.orEmpty(),
                        genre = tags?.genre.orEmpty(),
                        duration = tags?.durationMs ?: 0L,
                        uri = android.net.Uri.fromFile(file),
                        path = file.absolutePath,
                        folder = file.parent.orEmpty(),
                        year = tags?.year?.take(4)?.toIntOrNull(),
                        trackNumber = tags?.track?.substringBefore('/')?.filter { it.isDigit() }?.toIntOrNull(),
                        discNumber = tags?.disc?.substringBefore('/')?.filter { it.isDigit() }?.toIntOrNull(),
                        composer = tags?.composer.orEmpty(),
                        artPath = artPath,
                        codec = tags?.format,
                        bitrate = tags?.bitRate ?: 0,
                        sampleRate = tags?.sampleRate ?: 0,
                        channels = tags?.channels ?: 0,
                        fileSize = file.length(),
                        lastModified = file.lastModified(),
                        bitDepth = tags?.bitDepth ?: 0
                    )
                }.sortedWith(
                    compareBy<Track> { it.discNumber ?: Int.MAX_VALUE }
                        .thenBy { it.trackNumber ?: Int.MAX_VALUE }
                        .thenBy { File(it.path).name.lowercase() }
                )
                folderTracks to folderTracks.indexOfFirst { it.path == selectedFile.absolutePath }
            }
            val (folderTracks, startIndex) = result
            if (folderTracks.isEmpty() || startIndex < 0) {
                Toast.makeText(context, "Não encontrei músicas reproduzíveis nesta pasta", Toast.LENGTH_SHORT).show()
                return@launch
            }
            onPlayFolderTracks(folderTracks, startIndex)
            onClose()
        }
    }

    fun openExternal(file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
            val viewIntent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooser = Intent.createChooser(viewIntent, "Abrir ${file.name} com…")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val ownComponents = context.packageManager.queryIntentActivities(viewIntent, 0)
                .filter { it.activityInfo.packageName == context.packageName }
                .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
                .toTypedArray()
            if (ownComponents.isNotEmpty()) {
                chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, ownComponents)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Não foi possível abrir esse arquivo em outro app: ${e.message ?: "sem aplicativo compatível"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(currentPath) {
        selected = emptySet()
        reload()
    }

    fun navigateUp() {
        val parent = File(currentPath).parentFile ?: return
        if (parent.absolutePath.length >= root.length || parent.absolutePath.startsWith(root)) {
            currentPath = parent.absolutePath
        } else {
            currentPath = root
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(appColors.gradient) // tema adaptável
    ) {
        if (floatingIconsEnabled) {
            FloatingIconsBackground(kind = "files")
        }
        // camada do tema por cima dos ícones para legibilidade
        Box(
            Modifier
                .fillMaxSize()
                .background(appColors.surface.copy(alpha = 0.55f))
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(appColors.surface.copy(alpha = 0.95f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (selectionMode) selected = emptySet()
                    else if (File(currentPath).absolutePath != File(root).absolutePath) navigateUp()
                    else onClose()
                }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        when (mode) {
                            FileManagerMode.PICK_FOLDER -> "Escolher pasta"
                            else -> if (selectionMode) "${selected.size} selecionado(s)" else "Arquivos"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                    Text(
                        currentPath.removePrefix(root).ifBlank { "/" },
                        color = Color.White.copy(0.55f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (mode == FileManagerMode.BROWSE && !selectionMode) {
                    IconButton(onClick = { showMkdir = true }) {
                        Icon(Icons.Rounded.CreateNewFolder, null, tint = Color.White)
                    }
                }
                IconButton(onClick = { reload() }) {
                    Icon(Icons.Rounded.Refresh, null, tint = Color.White)
                }
                if (mode == FileManagerMode.PICK_FOLDER) {
                    TextButton(onClick = {
                        onFolderPicked?.invoke(currentPath)
                        onClose()
                    }) {
                        Text("Usar esta pasta", color = appColors.accent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (errorMsg != null) {
                Text(
                    errorMsg!!,
                    color = Color(0xFFFF8A80),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            val listState = rememberLazyListState()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = if (selectionMode || clipboard != null) 88.dp else 16.dp)
                ) {
                    if (File(currentPath).absolutePath != File(root).absolutePath) {
                        item {
                            FileRow(
                                name = "..",
                                subtitle = "Pasta superior",
                                isDir = true,
                                selected = false,
                                selectionMode = false,
                                kind = FileKind.FOLDER,
                                onClick = { navigateUp() },
                                onLongClick = {}
                            )
                        }
                    }
                    items(entries, key = { it.path }) { entry ->
                        val isSelected = entry.path in selected
                        FileRow(
                            name = entry.name,
                            subtitle = entry.subtitle,
                            isDir = entry.isDirectory,
                            selected = isSelected,
                            selectionMode = selectionMode,
                            kind = entry.kind,
                            onClick = {
                                if (selectionMode) {
                                    selected = if (isSelected) selected - entry.path else selected + entry.path
                                } else if (entry.isDirectory) {
                                    currentPath = entry.path
                                } else if (mode == FileManagerMode.BROWSE && entry.isAudio) {
                                    openAudioFromFolder(entry.path)
                                } else {
                                    // Arquivos não reproduzíveis entram no modo de seleção.
                                    selected = setOf(entry.path)
                                }
                            },
                            onLongClick = {
                                selected = if (isSelected) selected - entry.path else selected + entry.path
                            }
                        )
                    }
                    if (entries.isEmpty() && !loading) {
                        item {
                            Text(
                                "Pasta vazia",
                                color = Color.White.copy(0.5f),
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                }
            }

            // Action bar (seleção / clipboard)
            if (selectionMode || clipboard != null) {
                val actionBarContent: @Composable () -> Unit = {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectionMode) {
                            ActionChip(Icons.Rounded.ContentCopy, "Copiar") {
                                val files = selected.map { File(it) }
                                clipboard = ClipboardOp(files, cut = false)
                                selected = emptySet()
                                Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
                            }
                            ActionChip(Icons.Rounded.ContentCut, "Mover") {
                                val files = selected.map { File(it) }
                                clipboard = ClipboardOp(files, cut = true)
                                selected = emptySet()
                                Toast.makeText(context, "Recortado", Toast.LENGTH_SHORT).show()
                            }
                            if (selected.size == 1) {
                                ActionChip(Icons.Rounded.Edit, "Renomear") {
                                    val e = entries.firstOrNull { it.path in selected }
                                    if (e != null) {
                                        renameTarget = e
                                        renameText = e.name
                                        renameUpdateTags = e.isAudio
                                    }
                                }
                                val selectedFile = File(selected.first())
                                if (selectedFile.isFile && selectedFile.extension.lowercase() !in AUDIO_EXT) {
                                    ActionChip(Icons.Rounded.OpenInNew, "Abrir externo") {
                                        openExternal(selectedFile)
                                    }
                                }
                            }
                            val selectedAudio = selected.map { File(it) }.filter {
                                it.isFile && it.extension.lowercase() in AUDIO_EXT
                            }
                            if (selectedAudio.isNotEmpty()) {
                                ActionChip(Icons.Rounded.Label, "Por tags") {
                                    tagRenameFiles = selectedAudio
                                    showTagRename = true
                                }
                            }
                            val selectedLyrics = selected.map { File(it) }.filter {
                                it.isFile && it.extension.lowercase() in LYRICS_EXT
                            }
                            if (selectedLyrics.size == 1) {
                                ActionChip(Icons.Rounded.Edit, "Letras") {
                                    lyricsEditFile = selectedLyrics.first()
                                    scope.launch {
                                        lyricsEditText = withContext(Dispatchers.IO) {
                                            try {
                                                lyricsEditFile!!.readText(Charsets.UTF_8)
                                            } catch (_: Exception) {
                                                try {
                                                    lyricsEditFile!!.readText(Charsets.ISO_8859_1)
                                                } catch (e: Exception) {
                                                    "Erro ao ler: ${e.message}"
                                                }
                                            }
                                        }
                                        showLyricsEditor = true
                                    }
                                }
                            }
                            ActionChip(Icons.Rounded.Delete, "Excluir") {
                                showDeleteConfirm = true
                            }
                        }
                        if (clipboard != null) {
                            ActionChip(Icons.Rounded.ContentPaste, "Colar") {
                                val op = clipboard ?: return@ActionChip
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        pasteFiles(op, File(currentPath))
                                    }
                                    errorMsg = result
                                    clipboard = if (op.cut) null else op
                                    reload()
                                    onTracksMayHaveChanged?.invoke()
                                    Toast.makeText(
                                        context,
                                        if (result == null) "Concluído" else result,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                }
                if (menuVisualStyle == MenuVisualStyle.GLASS && backdrop != null) {
                    GlassSurface(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth(),
                        style = GlassStyle.Bar.copy(
                            refraction = 6.dp,
                            edgeWidth = 14.dp,
                            magnification = 0.015f,
                            dispersion = 0.18f
                        )
                    ) { actionBarContent() }
                } else {
                    val barColor = when (menuVisualStyle) {
                        MenuVisualStyle.COLORFUL -> appColors.surface
                        MenuVisualStyle.SIMPLE -> Color.Black
                        MenuVisualStyle.GLASS -> Color(0xCC17171D)
                    }
                    Surface(color = barColor, modifier = Modifier.fillMaxWidth()) {
                        actionBarContent()
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AppAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Excluir?") },
            text = { Text("Excluir ${selected.size} item(ns) permanentemente?") },
            confirmButton = {
                TextButton(onClick = {
                    val paths = selected.toList()
                    showDeleteConfirm = false
                    selected = emptySet()
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            paths.forEach { p ->
                                val f = File(p)
                                if (f.isDirectory) f.deleteRecursively() else f.delete()
                            }
                        }
                        reload()
                        onTracksMayHaveChanged?.invoke()
                    }
                }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    if (renameTarget != null) {
        AppAlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Renomear") },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        label = { Text("Nome") }
                    )
                    if (renameTarget!!.isAudio) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = renameUpdateTags, onCheckedChange = { renameUpdateTags = it })
                            Text("Atualizar tag de título", fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = renameTarget ?: return@TextButton
                    val newName = renameText.trim()
                    if (newName.isBlank()) return@TextButton
                    scope.launch {
                        val err = withContext(Dispatchers.IO) {
                            renameEntry(target, newName, renameUpdateTags)
                        }
                        errorMsg = err
                        renameTarget = null
                        selected = emptySet()
                        reload()
                        onTracksMayHaveChanged?.invoke()
                        if (err != null) {
                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancelar") }
            }
        )
    }

    if (showMkdir) {
        AppAlertDialog(
            onDismissRequest = { showMkdir = false },
            title = { Text("Nova pasta") },
            text = {
                OutlinedTextField(
                    value = mkdirName,
                    onValueChange = { mkdirName = it },
                    singleLine = true,
                    label = { Text("Nome da pasta") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = mkdirName.trim()
                    showMkdir = false
                    mkdirName = ""
                    if (name.isBlank()) return@TextButton
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            File(currentPath, name).mkdirs()
                        }
                        reload()
                    }
                }) { Text("Criar") }
            },
            dismissButton = {
                TextButton(onClick = { showMkdir = false }) { Text("Cancelar") }
            }
        )
    }

    if (showTagRename && tagRenameFiles.isNotEmpty()) {
        var previewTracks by remember(tagRenameFiles) { mutableStateOf<List<Track>>(emptyList()) }
        var previewLoading by remember(tagRenameFiles) { mutableStateOf(true) }
        LaunchedEffect(tagRenameFiles) {
            val files = tagRenameFiles
            previewLoading = true
            previewTracks = withContext(Dispatchers.IO) {
                files.map { f ->
                    val tags = try {
                        com.tolete.player.data.TagReader.read(f)
                    } catch (_: Exception) { null }
                    com.tolete.player.data.Track(
                        id = f.absolutePath,
                        title = tags?.title ?: f.nameWithoutExtension,
                        artist = tags?.artist.orEmpty(),
                        album = tags?.album.orEmpty(),
                        albumArtist = tags?.albumArtist.orEmpty(),
                        genre = tags?.genre.orEmpty(),
                        duration = tags?.durationMs ?: 0L,
                        uri = android.net.Uri.fromFile(f),
                        path = f.absolutePath,
                        folder = f.parent ?: "",
                        year = tags?.year?.toIntOrNull(),
                        trackNumber = tags?.track?.filter { it.isDigit() }?.toIntOrNull(),
                        composer = tags?.composer.orEmpty()
                    )
                }
            }
            previewLoading = false
        }
        BatchRenameDialog(
            tracks = previewTracks,
            totalCount = tagRenameFiles.size,
            isLoading = previewLoading,
            onDismiss = {
                showTagRename = false
                tagRenameFiles = emptyList()
            },
            onConfirm = { pattern ->
                val files = tagRenameFiles.toList()
                showTagRename = false
                tagRenameFiles = emptyList()
                selected = emptySet()
                scope.launch {
                    withContext(Dispatchers.IO) {
                        for (src in files) {
                            try {
                                val tags = com.tolete.player.data.TagReader.read(src)
                                val trackNum = tags?.track?.filter { it.isDigit() }?.take(2)?.padStart(2, '0') ?: "00"
                                val base = pattern
                                    .replace("%ARTIST%", tags?.artist.orEmpty())
                                    .replace("%TITLE%", tags?.title ?: src.nameWithoutExtension)
                                    .replace("%ALBUM%", tags?.album.orEmpty())
                                    .replace("%ALBUM_ARTIST%", tags?.albumArtist ?: tags?.artist.orEmpty())
                                    .replace("%TRACK%", trackNum)
                                    .replace("%YEAR%", tags?.year.orEmpty())
                                    .replace("%GENRE%", tags?.genre.orEmpty())
                                    .replace("%COMPOSER%", tags?.composer.orEmpty())
                                    .trim()
                                    .ifBlank { src.nameWithoutExtension }
                                val safe = base.replace(Regex("[\\/:*?\"<>|]"), "_").trim()
                                val dest = File(src.parentFile, "$safe.${src.extension}")
                                if (dest.absolutePath != src.absolutePath && !dest.exists()) {
                                    src.renameTo(dest)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    reload()
                    onTracksMayHaveChanged?.invoke()
                    Toast.makeText(context, "Renomeação por tags concluída", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showLyricsEditor && lyricsEditFile != null) {
        AppAlertDialog(
            onDismissRequest = {
                showLyricsEditor = false
                lyricsEditFile = null
            },
            title = {
                Text(
                    "Editar letras — ${lyricsEditFile!!.name}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                OutlinedTextField(
                    value = lyricsEditText,
                    onValueChange = { lyricsEditText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val f = lyricsEditFile
                    val content = lyricsEditText
                    showLyricsEditor = false
                    lyricsEditFile = null
                    if (f == null) return@TextButton
                    scope.launch {
                        val err = withContext(Dispatchers.IO) {
                            try {
                                f.writeText(content, Charsets.UTF_8)
                                null
                            } catch (e: Exception) {
                                e.message
                            }
                        }
                        if (err != null) {
                            Toast.makeText(context, "Erro: $err", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Letras salvas", Toast.LENGTH_SHORT).show()
                            selected = emptySet()
                            reload()
                        }
                    }
                }) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLyricsEditor = false
                    lyricsEditFile = null
                }) { Text("Cancelar") }
            }
        )
    }

}


@Composable
private fun ActionChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Text(label, color = Color.White, fontSize = 11.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    name: String,
    subtitle: String,
    isDir: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    kind: FileKind = FileKind.OTHER,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val iconRes = when (kind) {
        FileKind.FOLDER -> R.drawable.ic_folders
        FileKind.MUSIC -> R.drawable.ic_music_files
        FileKind.IMAGE -> R.drawable.ic_image_files
        FileKind.VIDEO -> R.drawable.ic_video_files
        FileKind.LYRICS -> R.drawable.ic_lyrics_files
        FileKind.OTHER -> R.drawable.ic_files
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) Color.White.copy(0.12f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color(0xFF82B1FF) else Color.White.copy(0.2f)),
                contentAlignment = Alignment.Center
            ) {
                if (selected) Text("✓", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
        }
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = Color.White.copy(0.5f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (isDir && !selectionMode) {
            Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(0.35f))
        }
    }
}

private data class FileEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val subtitle: String,
    val isAudio: Boolean,
    val kind: FileKind = FileKind.OTHER
)

private enum class FileKind { FOLDER, MUSIC, IMAGE, VIDEO, LYRICS, OTHER }

private data class ClipboardOp(val files: List<File>, val cut: Boolean)

private val AUDIO_EXT = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "alac", "aiff", "aif", "wma", "dsf", "dff")
private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif")
private val VIDEO_EXT = setOf("mkv", "mp4", "mov", "avi", "webm", "m4v", "3gp", "wmv", "flv")
private val LYRICS_EXT = setOf("lrc", "ttml")

private fun listEntries(path: String): List<FileEntry> {
    val dir = File(path)
    val files = dir.listFiles() ?: return emptyList()
    val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return files
        .filter { !it.name.startsWith('.') }
        .map { f ->
            val ext = f.extension.lowercase()
            val audio = !f.isDirectory && ext in AUDIO_EXT
            val kind = when {
                f.isDirectory -> FileKind.FOLDER
                ext in AUDIO_EXT -> FileKind.MUSIC
                ext in IMAGE_EXT -> FileKind.IMAGE
                ext in VIDEO_EXT -> FileKind.VIDEO
                ext in LYRICS_EXT -> FileKind.LYRICS
                else -> FileKind.OTHER
            }
            val sub = when {
                f.isDirectory -> {
                    val n = f.list()?.size ?: 0
                    if (n == 1) "1 item" else "$n itens"
                }
                else -> {
                    val size = formatSize(f.length())
                    val date = fmt.format(Date(f.lastModified()))
                    "$size · $date"
                }
            }
            FileEntry(f.absolutePath, f.name, f.isDirectory, sub, audio, kind)
        }
        .sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

private fun pasteFiles(op: ClipboardOp, destDir: File): String? {
    try {
        for (src in op.files) {
            val target = uniqueName(destDir, src.name)
            if (src.isDirectory) {
                if (op.cut) {
                    if (!src.renameTo(target)) {
                        src.copyRecursively(target, overwrite = false)
                        src.deleteRecursively()
                    }
                } else {
                    src.copyRecursively(target, overwrite = false)
                }
            } else {
                if (op.cut) {
                    if (!src.renameTo(target)) {
                        src.copyTo(target, overwrite = false)
                        src.delete()
                    }
                } else {
                    src.copyTo(target, overwrite = false)
                }
            }
        }
        return null
    } catch (e: Exception) {
        return e.message ?: "Falha ao colar"
    }
}

private fun uniqueName(dir: File, name: String): File {
    var f = File(dir, name)
    if (!f.exists()) return f
    val base = name.substringBeforeLast('.', name)
    val ext = if (name.contains('.')) ".${name.substringAfterLast('.')}" else ""
    var i = 1
    while (f.exists()) {
        f = File(dir, "$base ($i)$ext")
        i++
    }
    return f
}

private fun renameEntry(entry: FileEntry, newName: String, updateTags: Boolean): String? {
    val src = File(entry.path)
    if (!src.exists()) return "Arquivo não existe"
    val dest = File(src.parentFile, newName)
    if (dest.exists()) return "Já existe um item com esse nome"
    if (!src.renameTo(dest)) return "Falha ao renomear"
    if (updateTags && entry.isAudio) {
        try {
            val title = newName.substringBeforeLast('.').ifBlank { newName }
            val audio = AudioFileIO.read(dest)
            val tag = audio.tagOrCreateAndSetDefault
            tag.setField(FieldKey.TITLE, title)
            audio.commit()
        } catch (_: Exception) {
            // rename ok; tag opcional
        }
    }
    return null
}
