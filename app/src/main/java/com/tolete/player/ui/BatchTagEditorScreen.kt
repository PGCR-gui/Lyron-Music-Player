package com.tolete.player.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tolete.player.data.TagReader
import com.tolete.player.data.TagWriter
import com.tolete.player.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Campos que a edição em lote consegue alterar (os que fazem sentido para várias faixas). */
private data class BatchSpec(val key: String, val label: String)

private val SPEC_ARTIST = BatchSpec("artist", "Artista")
private val SPEC_ALBUM = BatchSpec("album", "Álbum")
private val SPEC_ALBUM_ARTIST = BatchSpec("albumArtist", "Artista do álbum")
private val SPEC_GENRE = BatchSpec("genre", "Género")
private val SPEC_TRACK_TOTAL = BatchSpec("trackTotal", "Total faixas")
private val SPEC_DISC = BatchSpec("disc", "Disco")
private val SPEC_DISC_TOTAL = BatchSpec("discTotal", "Total discos")
private val SPEC_YEAR = BatchSpec("year", "Ano")
private val SPEC_COMPOSER = BatchSpec("composer", "Compositor")
private val SPEC_LYRICIST = BatchSpec("lyricist", "Letrista")
private val SPEC_ORIGINAL_ARTIST = BatchSpec("originalArtist", "Artista original")
private val SPEC_ORIGINAL_YEAR = BatchSpec("originalYear", "Ano original")
private val SPEC_PUBLISHER = BatchSpec("publisher", "Editora / selo")
private val SPEC_COPYRIGHT = BatchSpec("copyright", "Copyright")
private val SPEC_COMMENT = BatchSpec("comment", "Comentário")

private val ALL_SPECS = listOf(
    SPEC_ARTIST, SPEC_ALBUM, SPEC_ALBUM_ARTIST, SPEC_GENRE,
    SPEC_TRACK_TOTAL, SPEC_DISC, SPEC_DISC_TOTAL, SPEC_YEAR,
    SPEC_COMPOSER, SPEC_LYRICIST, SPEC_ORIGINAL_ARTIST, SPEC_ORIGINAL_YEAR,
    SPEC_PUBLISHER, SPEC_COPYRIGHT, SPEC_COMMENT
)

private fun tagValue(t: TagReader.Tags, key: String): String? = when (key) {
    "artist" -> t.artist
    "album" -> t.album
    "albumArtist" -> t.albumArtist
    "genre" -> t.genre
    "year" -> t.year
    "trackTotal" -> t.trackTotal
    "disc" -> t.disc
    "discTotal" -> t.discTotal
    "composer" -> t.composer
    "lyricist" -> t.lyricist
    "comment" -> t.comment
    "publisher" -> t.publisher
    "copyright" -> t.copyright
    "originalArtist" -> t.originalArtist
    "originalYear" -> t.originalYear
    else -> null
}

/** Valor conhecido pela biblioteca quando o arquivo não pôde ser lido. */
private fun trackValue(t: Track, key: String): String? = when (key) {
    "artist" -> t.artist
    "album" -> t.album
    "albumArtist" -> t.albumArtist
    "genre" -> t.genre
    "year" -> t.year?.toString()
    "disc" -> t.discNumber?.toString()
    "composer" -> t.composer
    else -> null
}

/**
 * Editor de tags em lote. Mesmo visual do editor padrão; os campos já vêm preenchidos com o
 * que as faixas selecionadas têm EM COMUM (ex.: o mesmo artista). Campos que variam ficam
 * vazios com um aviso e atalhos para os valores existentes. Só o que for alterado é gravado.
 */
@Composable
internal fun BatchTagEditorScreen(
    tracks: List<Track>,
    appColors: AppColors,
    onApply: (TagWriter.BatchFields) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val n = tracks.size
    BackHandler(onBack = onClose)

    val values = remember(tracks) { mutableStateMapOf<String, String>() }
    val initial = remember(tracks) { mutableStateMapOf<String, String>() }
    val variants = remember(tracks) { mutableStateMapOf<String, List<Pair<String, Int>>>() }
    val touched = remember(tracks) { mutableStateMapOf<String, Boolean>() }
    var loading by remember(tracks) { mutableStateOf(true) }
    var unreadable by remember(tracks) { mutableStateOf(0) }
    var coverBytes by remember(tracks) { mutableStateOf<ByteArray?>(null) }
    var coverPath by remember(tracks) { mutableStateOf<String?>(null) }
    var savingArt by remember(tracks) { mutableStateOf(false) }

    // Lê as tags de todas as faixas (4 por vez) e descobre o que é comum a todas
    LaunchedEffect(tracks) {
        loading = true
        val read: List<Pair<Boolean, Map<String, String>>> = withContext(Dispatchers.IO) {
            tracks.chunked(4).flatMap { chunk ->
                coroutineScope {
                    chunk.map { t ->
                        async {
                            val tags = TagReader.read(context, t)
                            val map = ALL_SPECS.associate { spec ->
                                val raw = if (tags != null) tagValue(tags, spec.key) else trackValue(t, spec.key)
                                spec.key to raw.orEmpty().trim()
                            }
                            (tags != null) to map
                        }
                    }.awaitAll()
                }
            }
        }
        unreadable = read.count { !it.first }
        ALL_SPECS.forEach { spec ->
            val vals = read.map { it.second[spec.key].orEmpty() }
            val counts = vals.groupingBy { it }.eachCount()
            val common = if (counts.size == 1) counts.keys.first() else ""
            initial[spec.key] = common
            variants[spec.key] = if (counts.size > 1) {
                counts.entries.sortedByDescending { it.value }.map { it.key to it.value }
            } else emptyList()
            if (touched[spec.key] != true) values[spec.key] = common
        }
        loading = false
    }

    fun changedValue(key: String): String? {
        val cur = values[key].orEmpty().trim()
        val ini = initial[key].orEmpty().trim()
        return if (cur != ini) cur else null
    }

    val changedCount = ALL_SPECS.count { changedValue(it.key) != null }
    val canApply = !loading && !savingArt && (changedCount > 0 || coverBytes != null)

    fun buildFields() = TagWriter.BatchFields(
        artist = changedValue("artist"),
        album = changedValue("album"),
        albumArtist = changedValue("albumArtist"),
        genre = changedValue("genre"),
        year = changedValue("year"),
        trackTotal = changedValue("trackTotal"),
        disc = changedValue("disc"),
        discTotal = changedValue("discTotal"),
        composer = changedValue("composer"),
        lyricist = changedValue("lyricist"),
        comment = changedValue("comment"),
        publisher = changedValue("publisher"),
        copyright = changedValue("copyright"),
        originalArtist = changedValue("originalArtist"),
        originalYear = changedValue("originalYear"),
        cover = coverBytes
    )

    val pickCoverLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            savingArt = true
            val prepared: Pair<ByteArray, String>? = withContext(Dispatchers.IO) {
                prepareBatchCover(context, uri)
            }
            savingArt = false
            if (prepared == null) {
                Toast.makeText(context, "Não foi possível ler a imagem", Toast.LENGTH_SHORT).show()
            } else {
                coverBytes = prepared.first
                coverPath = prepared.second
            }
        }
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

    val currentArt = coverPath ?: tracks.firstOrNull { !it.artPath.isNullOrBlank() }?.artPath

    @Composable
    fun Field(spec: BatchSpec, modifier: Modifier = Modifier.fillMaxWidth(), singleLine: Boolean = true, minLines: Int = 1) {
        BatchTagField(
            label = spec.label,
            value = values[spec.key].orEmpty(),
            initial = initial[spec.key].orEmpty(),
            variants = variants[spec.key].orEmpty(),
            trackCount = n,
            colors = fieldColors,
            accent = appColors.accent,
            modifier = modifier,
            singleLine = singleLine,
            minLines = minLines
        ) {
            touched[spec.key] = true
            values[spec.key] = it
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Fundo: capa borrada (a escolhida, ou a da primeira faixa)
        if (currentArt != null) {
            AsyncImage(
                model = File(currentArt),
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
                    Text("Editor de tags em lote", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        "$n faixas selecionadas",
                        fontSize = 12.sp,
                        color = Color.White.copy(0.55f)
                    )
                }
                IconButton(onClick = { onApply(buildFields()) }, enabled = canApply) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = "Aplicar",
                        tint = if (canApply) appColors.accent else Color.White.copy(0.3f)
                    )
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
                CommonTagsSummary(
                    loading = loading,
                    unreadable = unreadable,
                    trackCount = n,
                    initial = initial,
                    variants = variants,
                    accent = appColors.accent
                )

                BatchSectionTitle("Capa")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(0.08f))
                            .border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(14.dp))
                            .clickable(enabled = !savingArt) { pickCoverLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentArt != null) {
                            AsyncImage(
                                model = File(currentArt),
                                contentDescription = "Capa",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Image,
                                contentDescription = null,
                                tint = Color.White.copy(0.5f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        if (savingArt) {
                            Box(
                                Modifier.fillMaxSize().background(Color.Black.copy(0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = appColors.accent, modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (coverBytes != null) "Nova capa para $n faixas" else "Trocar capa de todas",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Text(
                            if (coverBytes != null) "Será gravada em todas as faixas ao aplicar."
                            else "Opcional. Escolha uma imagem para gravar em todas as faixas selecionadas.",
                            fontSize = 12.sp,
                            color = Color.White.copy(0.5f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { pickCoverLauncher.launch("image/*") },
                                enabled = !savingArt,
                                colors = ButtonDefaults.buttonColors(containerColor = appColors.accent)
                            ) {
                                Text("Escolher imagem", color = Color.Black)
                            }
                            if (coverBytes != null) {
                                TextButton(onClick = { coverBytes = null; coverPath = null }) {
                                    Text("Remover", color = Color.White.copy(0.8f))
                                }
                            }
                        }
                    }
                }

                BatchSectionTitle("Básico")
                Field(SPEC_ARTIST)
                Field(SPEC_ALBUM)
                Field(SPEC_ALBUM_ARTIST)
                Field(SPEC_GENRE)

                BatchSectionTitle("Números")
                Row(Modifier.fillMaxWidth()) {
                    Field(SPEC_TRACK_TOTAL, Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    Field(SPEC_YEAR, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    Field(SPEC_DISC, Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    Field(SPEC_DISC_TOTAL, Modifier.weight(1f))
                }

                BatchSectionTitle("Pessoas")
                Field(SPEC_COMPOSER)
                Field(SPEC_LYRICIST)
                Field(SPEC_ORIGINAL_ARTIST)
                Field(SPEC_ORIGINAL_YEAR)

                BatchSectionTitle("Extra")
                Field(SPEC_PUBLISHER)
                Field(SPEC_COPYRIGHT)
                Field(SPEC_COMMENT, singleLine = false, minLines = 2)

                Spacer(Modifier.height(16.dp))
                Text(
                    "Título, número da faixa, ISRC e letras são específicos de cada faixa e não aparecem " +
                        "aqui. Só os campos que você alterar são gravados; os demais ficam como estão. " +
                        "Esvaziar um campo que era igual em todas apaga a tag nas faixas.",
                    fontSize = 11.sp,
                    color = Color.White.copy(0.45f)
                )
                Spacer(Modifier.height(24.dp))
            }

            Button(
                onClick = { onApply(buildFields()) },
                enabled = canApply,
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
                val extra = changedCount + (if (coverBytes != null) 1 else 0)
                Text(
                    if (extra > 0) "Aplicar em $n faixas ($extra alterações)" else "Aplicar em $n faixas",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

/** Cartão no topo: o que as faixas têm em comum e quais campos variam. */
@Composable
private fun CommonTagsSummary(
    loading: Boolean,
    unreadable: Int,
    trackCount: Int,
    initial: Map<String, String>,
    variants: Map<String, List<Pair<String, Int>>>,
    accent: Color
) {
    val shape = RoundedCornerShape(16.dp)
    val common = ALL_SPECS.filter { initial[it.key].orEmpty().isNotBlank() }
    val varying = ALL_SPECS.filter { variants[it.key].orEmpty().isNotEmpty() }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(shape)
            .background(Color.White.copy(0.07f))
            .border(1.dp, Color.White.copy(0.14f), shape)
            .padding(14.dp)
    ) {
        Text("Tags em comum", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Spacer(Modifier.height(6.dp))
        when {
            loading -> Text("Lendo as tags de $trackCount faixas…", fontSize = 12.sp, color = Color.White.copy(0.6f))
            common.isEmpty() -> Text(
                "Nenhum campo tem o mesmo valor em todas as faixas.",
                fontSize = 12.sp,
                color = Color.White.copy(0.6f)
            )
            else -> common.forEach { spec ->
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                    Text(
                        spec.label,
                        fontSize = 12.sp,
                        color = Color.White.copy(0.55f),
                        modifier = Modifier.width(112.dp)
                    )
                    Text(
                        initial[spec.key].orEmpty(),
                        fontSize = 12.sp,
                        color = accent,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        if (!loading && varying.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Variam entre as faixas: " + varying.joinToString(", ") { it.label },
                fontSize = 11.sp,
                color = Color.White.copy(0.5f)
            )
        }
        if (!loading && unreadable > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "$unreadable ${if (unreadable == 1) "faixa não pôde" else "faixas não puderam"} ser " +
                    "${if (unreadable == 1) "lida" else "lidas"} do arquivo; usei os dados da biblioteca.",
                fontSize = 11.sp,
                color = Color(0xFFFFB74D)
            )
        }
    }
}

/**
 * Campo do editor em lote. Mostra se o valor é igual em todas as faixas, se varia (com atalhos
 * para os valores existentes) e quando já foi alterado.
 */
@Composable
private fun BatchTagField(
    label: String,
    value: String,
    initial: String,
    variants: List<Pair<String, Int>>,
    trackCount: Int,
    colors: TextFieldColors,
    accent: Color,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
    minLines: Int = 1,
    onChange: (String) -> Unit
) {
    val changed = value.trim() != initial.trim()
    val differs = variants.isNotEmpty()
    val hint: String
    val hintColor: Color
    when {
        changed && value.isBlank() -> {
            hint = "Será apagado em $trackCount faixas"
            hintColor = Color(0xFFFF8A80)
        }
        changed -> {
            hint = "Será aplicado em $trackCount faixas"
            hintColor = accent
        }
        differs -> {
            hint = "Varia entre as faixas · deixe vazio para manter"
            hintColor = Color.White.copy(0.5f)
        }
        initial.isNotBlank() -> {
            hint = "Igual em todas as faixas"
            hintColor = Color.White.copy(0.5f)
        }
        else -> {
            hint = "Vazio em todas as faixas"
            hintColor = Color.White.copy(0.35f)
        }
    }
    val placeholder: (@Composable () -> Unit)? =
        if (differs) ({ Text("Valores diferentes", color = Color.White.copy(0.4f)) }) else null

    Column(modifier.padding(bottom = 8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            placeholder = placeholder,
            singleLine = singleLine,
            minLines = minLines,
            modifier = Modifier.fillMaxWidth(),
            colors = colors,
            shape = RoundedCornerShape(12.dp),
            supportingText = { Text(hint, fontSize = 11.sp, color = hintColor) }
        )
        val suggestions = variants.filter { it.first.isNotBlank() }.take(6)
        if (differs && suggestions.isNotEmpty()) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                suggestions.forEach { (text, count) ->
                    val shape = RoundedCornerShape(50)
                    val selected = value.trim() == text
                    Box(
                        Modifier
                            .clip(shape)
                            .background(if (selected) accent.copy(0.28f) else Color.White.copy(0.08f))
                            .border(1.dp, if (selected) accent else Color.White.copy(0.18f), shape)
                            .clickable { onChange(text) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "$text · ${count}x",
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White.copy(0.85f)
                        )
                    }
                }
            }
        }
    }
}

/** Decodifica, reduz (máx. 1400 px) e converte para JPEG; devolve os bytes e a prévia em cache. */
private fun prepareBatchCover(context: android.content.Context, uri: Uri): Pair<ByteArray, String>? {
    return try {
        val cr = context.contentResolver
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        val maxSide = maxOf(opts.outWidth, opts.outHeight)
        while (maxSide / sample > 2800) sample *= 2
        val decodeOpts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = cr.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null
        val side = maxOf(bmp.width, bmp.height)
        if (side > 1400) {
            val k = 1400f / side
            val scaled = android.graphics.Bitmap.createScaledBitmap(
                bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true
            )
            if (scaled !== bmp) bmp.recycle()
            bmp = scaled
        }
        val out = java.io.ByteArrayOutputStream()
        // Fundo branco para PNGs com transparência
        val flat = android.graphics.Bitmap.createBitmap(bmp.width, bmp.height, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(flat).apply {
            drawColor(android.graphics.Color.WHITE)
            drawBitmap(bmp, 0f, 0f, null)
        }
        flat.compress(android.graphics.Bitmap.CompressFormat.JPEG, 93, out)
        bmp.recycle(); flat.recycle()
        val bytes = out.toByteArray()
        bytes to TagWriter.saveCoverPreview(context, bytes, "image/jpeg")
    } catch (_: Throwable) {
        null
    }
}

@Composable
private fun BatchSectionTitle(text: String) {
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
