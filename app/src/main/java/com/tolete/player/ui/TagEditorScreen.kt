package com.tolete.player.ui

import androidx.compose.foundation.clickable

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tolete.player.data.TagReader
import com.tolete.player.data.TagWriter
import com.tolete.player.data.Track
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Campos do editor experimental completo. */
data class TagEdits(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val genre: String = "",
    val year: String = "",
    val trackNumber: String = "",
    val trackTotal: String = "",
    val disc: String = "",
    val discTotal: String = "",
    val composer: String = "",
    val lyricist: String = "",
    val comment: String = "",
    val lyrics: String = "",
    val bpm: String = "",
    val isrc: String = "",
    val copyright: String = "",
    val encoder: String = "",
    val publisher: String = "",
    val originalArtist: String = "",
    val originalYear: String = ""
)

fun TagEdits.toWriterFields() = TagWriter.Fields(
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    genre = genre,
    year = year,
    track = trackNumber,
    trackTotal = trackTotal,
    disc = disc,
    discTotal = discTotal,
    composer = composer,
    lyricist = lyricist,
    comment = comment,
    lyrics = lyrics,
    bpm = bpm,
    isrc = isrc,
    copyright = copyright,
    encoder = encoder,
    publisher = publisher,
    originalArtist = originalArtist,
    originalYear = originalYear
)

@Composable
fun TagEditorScreen(
    track: Track,
    appColors: AppColors,
    onSave: (TagEdits) -> Unit,
    onClose: () -> Unit,
    isPlaying: Boolean = false,
    getPosition: () -> Long = { 0L },
    onSeek: (Long) -> Unit = {},
    onToggle: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    var album by remember(track.id) { mutableStateOf(track.album) }
    var albumArtist by remember(track.id) { mutableStateOf("") }
    var genre by remember(track.id) { mutableStateOf(track.genre) }
    var year by remember(track.id) { mutableStateOf(track.year?.toString().orEmpty()) }
    var trackNumber by remember(track.id) { mutableStateOf(track.trackNumber?.toString().orEmpty()) }
    var trackTotal by remember(track.id) { mutableStateOf("") }
    var disc by remember(track.id) { mutableStateOf("") }
    var discTotal by remember(track.id) { mutableStateOf("") }
    var composer by remember(track.id) { mutableStateOf("") }
    var lyricist by remember(track.id) { mutableStateOf("") }
    var comment by remember(track.id) { mutableStateOf("") }
    var lyrics by remember(track.id) { mutableStateOf("") }
    var bpm by remember(track.id) { mutableStateOf("") }
    var isrc by remember(track.id) { mutableStateOf("") }
    var copyright by remember(track.id) { mutableStateOf("") }
    var encoder by remember(track.id) { mutableStateOf("") }
    var publisher by remember(track.id) { mutableStateOf("") }
    var originalArtist by remember(track.id) { mutableStateOf("") }
    var originalYear by remember(track.id) { mutableStateOf("") }

    var techLine by remember(track.id) { mutableStateOf<String?>(null) }
    var loading by remember(track.id) { mutableStateOf(true) }
    var showLyricsSync by remember { mutableStateOf(false) }
    var fileHint by remember(track.id) { mutableStateOf<String?>(null) }

    fun currentEdits() = TagEdits(
        title, artist, album, albumArtist, genre, year,
        trackNumber, trackTotal, disc, discTotal,
        composer, lyricist, comment, lyrics,
        bpm, isrc, copyright, encoder, publisher,
        originalArtist, originalYear
    )

    fun applyTags(t: TagReader.Tags) {
        title = t.title ?: title
        artist = t.artist ?: artist
        album = t.album ?: album
        albumArtist = t.albumArtist.orEmpty()
        genre = t.genre ?: genre
        year = t.year ?: year
        trackNumber = t.track ?: trackNumber
        trackTotal = t.trackTotal.orEmpty()
        disc = t.disc.orEmpty()
        discTotal = t.discTotal.orEmpty()
        composer = t.composer.orEmpty()
        lyricist = t.lyricist.orEmpty()
        comment = t.comment.orEmpty()
        lyrics = t.lyrics.orEmpty()
        bpm = t.bpm.orEmpty()
        isrc = t.isrc.orEmpty()
        copyright = t.copyright.orEmpty()
        encoder = t.encoder.orEmpty()
        publisher = t.publisher.orEmpty()
        originalArtist = t.originalArtist.orEmpty()
        originalYear = t.originalYear.orEmpty()

        val parts = mutableListOf<String>()
        t.format?.let { parts += it }
        if (t.sampleRate > 0) parts += "${t.sampleRate} Hz"
        if (t.bitDepth > 0) parts += "${t.bitDepth}-bit"
        if (t.bitRate > 0) parts += "${t.bitRate / 1000} kbps"
        if (t.channels > 0) parts += if (t.channels == 1) "Mono" else "${t.channels} ch"
        if (t.durationMs > 0) {
            val s = (t.durationMs / 1000).toInt()
            parts += "%d:%02d".format(s / 60, s % 60)
        }
        if (t.hasArtwork) parts += "capa"
        techLine = parts.joinToString(" · ").ifBlank { null }
    }

    fun reloadFromFile() {
        loading = true
        scope.launch {
            val tags = withContext(Dispatchers.IO) {
                TagReader.read(context, track)
            }
            if (tags != null) {
                applyTags(tags)
                fileHint = "Lido do ficheiro via JAudioTagger"
            } else {
                fileHint = "Não foi possível ler o ficheiro (path real / “Todos os ficheiros”)"
                Toast.makeText(context, fileHint, Toast.LENGTH_SHORT).show()
            }
            loading = false
        }
    }

    LaunchedEffect(track.id) {
        reloadFromFile()
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White.copy(0.92f),
        focusedBorderColor = appColors.accent,
        unfocusedBorderColor = Color.White.copy(0.25f),
        cursorColor = appColors.accent,
        focusedLabelColor = appColors.accent,
        unfocusedLabelColor = Color.White.copy(0.55f),
        focusedContainerColor = Color.White.copy(0.06f),
        unfocusedContainerColor = Color.White.copy(0.04f)
    )

    Box(Modifier.fillMaxSize()) {
        // Fundo: capa borrada da faixa em edição
        val art = track.artPath
        if (art != null) {
            AsyncImage(
                model = File(art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(28.dp, BlurredEdgeTreatment.Unbounded)
            )
        } else {
            Box(Modifier.fillMaxSize().background(appColors.gradient))
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(0.55f),
                            Color.Black.copy(0.70f),
                            Color.Black.copy(0.85f)
                        )
                    )
                )
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text("Editor de tags", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "Experimental · JAudioTagger",
                    fontSize = 12.sp,
                    color = Color.White.copy(0.55f)
                )
            }
            IconButton(onClick = { reloadFromFile() }, enabled = !loading) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Reler do ficheiro", tint = Color.White)
            }
            IconButton(
                onClick = { onSave(currentEdits()) },
                enabled = !loading
            ) {
                Icon(Icons.Rounded.Check, contentDescription = "Salvar", tint = appColors.accent)
            }
        }

        if (loading) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = appColors.accent)
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                track.path.ifBlank { track.uri.toString() },
                fontSize = 11.sp,
                color = Color.White.copy(0.4f),
                maxLines = 2
            )
            var renomearPorTags by remember { mutableStateOf(false) }
            var renamePattern by remember { mutableStateOf("%ARTIST% - %TITLE%") }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { renomearPorTags = !renomearPorTags },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = renomearPorTags,
                    onCheckedChange = { renomearPorTags = it }
                )
                Text("Renomear arquivo por tags ao salvar", fontSize = 14.sp)
            }
            if (renomearPorTags) {
                OutlinedTextField(
                    value = renamePattern,
                    onValueChange = { renamePattern = it },
                    label = { Text("Padrão") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    "%ARTIST% %TITLE% %ALBUM% %ALBUM_ARTIST% %TRACK% %YEAR%",
                    fontSize = 11.sp,
                    color = Color.White.copy(0.45f)
                )
            }
            fileHint?.let {
                Text(it, fontSize = 12.sp, color = Color.White.copy(0.5f), modifier = Modifier.padding(top = 4.dp))
            }
            techLine?.let {
                Text(
                    it,
                    fontSize = 12.sp,
                    color = appColors.accent.copy(0.9f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
            }

            SectionTitle("Básico")
            TagField("Título", title, fieldColors) { title = it }
            TagField("Artista", artist, fieldColors) { artist = it }
            TagField("Álbum", album, fieldColors) { album = it }
            TagField("Artista do álbum", albumArtist, fieldColors) { albumArtist = it }
            TagField("Género", genre, fieldColors) { genre = it }

            SectionTitle("Números")
            Row(Modifier.fillMaxWidth()) {
                TagField("Faixa", trackNumber, fieldColors, Modifier.weight(1f)) { trackNumber = it }
                Spacer(Modifier.width(10.dp))
                TagField("Total faixas", trackTotal, fieldColors, Modifier.weight(1f)) { trackTotal = it }
            }
            Row(Modifier.fillMaxWidth()) {
                TagField("Disco", disc, fieldColors, Modifier.weight(1f)) { disc = it }
                Spacer(Modifier.width(10.dp))
                TagField("Total discos", discTotal, fieldColors, Modifier.weight(1f)) { discTotal = it }
            }
            Row(Modifier.fillMaxWidth()) {
                TagField("Ano", year, fieldColors, Modifier.weight(1f)) { year = it }
                Spacer(Modifier.width(10.dp))
                TagField("BPM", bpm, fieldColors, Modifier.weight(1f)) { bpm = it }
            }

            SectionTitle("Pessoas")
            TagField("Compositor", composer, fieldColors) { composer = it }
            TagField("Letrista", lyricist, fieldColors) { lyricist = it }
            TagField("Artista original", originalArtist, fieldColors) { originalArtist = it }
            TagField("Ano original", originalYear, fieldColors) { originalYear = it }

            SectionTitle("Extra")
            TagField("Editora / selo", publisher, fieldColors) { publisher = it }
            TagField("ISRC", isrc, fieldColors) { isrc = it }
            TagField("Copyright", copyright, fieldColors) { copyright = it }
            TagField("Encoder", encoder, fieldColors) { encoder = it }
            TagField("Comentário", comment, fieldColors, singleLine = false, minLines = 2) { comment = it }
            TagField("Letras (embed)", lyrics, fieldColors, singleLine = false, minLines = 4) { lyrics = it }
            Button(
                onClick = { showLyricsSync = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = appColors.accent,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Sincronizar letras", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Campos vazios apagam a tag no ficheiro (quando o formato permite). " +
                    "Nem todos os formatos suportam todos os campos (ex.: WAV tem suporte limitado).",
                fontSize = 11.sp,
                color = Color.White.copy(0.45f)
            )
            Spacer(Modifier.height(24.dp))
        }

        Button(
            onClick = { onSave(currentEdits()) },
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = appColors.accent,
                contentColor = Color.Black
            )
        ) {
            Text("Salvar no arquivo", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        } // Column content

        if (showLyricsSync) {
            val initialLines = remember(lyrics) {
                val parsed = com.tolete.player.data.LyricsExtractor.parseLrcText(lyrics)
                if (parsed.isNotEmpty()) parsed
                else lyrics.lines().filter { it.isNotBlank() }.map {
                    com.tolete.player.data.LyricLine(-1L, it)
                }
            }
            LyricsEditorScreen(
                track = track,
                initialLines = initialLines,
                isPlaying = isPlaying,
                getPosition = getPosition,
                onSeek = onSeek,
                onToggle = onToggle,
                onSave = { lines ->
                    lyrics = lyricLinesToTagText(lines)
                    showLyricsSync = false
                },
                onClose = { showLyricsSync = false },
                appColors = appColors
            )
        }
    } // Box root
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White.copy(0.55f),
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
    )
    HorizontalDivider(color = Color.White.copy(0.12f), thickness = 1.dp)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun TagField(
    label: String,
    value: String,
    colors: androidx.compose.material3.TextFieldColors,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
    minLines: Int = 1,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        modifier = modifier.padding(bottom = 8.dp),
        colors = colors,
        shape = RoundedCornerShape(12.dp)
    )
}
