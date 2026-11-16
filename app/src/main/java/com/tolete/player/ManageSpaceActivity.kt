package com.tolete.player

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tolete.player.data.LyronStorage
import com.tolete.player.data.StorageManager
import com.tolete.player.ui.FloatingIconsBackground
import com.tolete.player.ui.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Gerenciar espaço — categorias detalhadas (como no app completo) + ícones flutuantes managespace.
 */
class ManageSpaceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val vm: MusicViewModel = viewModel()
                val floating by vm.floatingIconsEnabled.collectAsState()
                ManageSpaceScreen(
                    floating = floating,
                    onBack = { finish() },
                    onAfterClearLibrary = {
                        try { vm.refreshLibrary() } catch (_: Exception) { }
                    }
                )
            }
        }
    }
}

private enum class SpaceCategory {
    CACHE, LIBRARY, PLAYLISTS, FONTS_VIZ, SETTINGS, EXPORTED_LYRICS
}

private data class CategoryStat(
    val category: SpaceCategory,
    val title: String,
    val description: String,
    val bytes: Long
)

private data class SpaceBreakdown(
    val totalBytes: Long = 0L,
    val items: List<CategoryStat> = emptyList()
)

@Composable
private fun ManageSpaceScreen(
    floating: Boolean,
    onBack: () -> Unit,
    onAfterClearLibrary: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var breakdown by remember { mutableStateOf(SpaceBreakdown()) }
    var refreshing by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<SpaceCategory?>(null) }
    var confirmAll by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            refreshing = true
            breakdown = withContext(Dispatchers.IO) { computeBreakdown(context) }
            refreshing = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun clearCategory(cat: SpaceCategory) {
        busy = true
        scope.launch {
            withContext(Dispatchers.IO) { performClear(context, cat) }
            if (cat == SpaceCategory.LIBRARY) onAfterClearLibrary()
            Toast.makeText(context, "Limpeza concluída", Toast.LENGTH_SHORT).show()
            busy = false
            refresh()
        }
    }

    fun clearAll() {
        busy = true
        scope.launch {
            withContext(Dispatchers.IO) {
                SpaceCategory.entries
                    .filter { it != SpaceCategory.EXPORTED_LYRICS }
                    .forEach { performClear(context, it) }
            }
            onAfterClearLibrary()
            Toast.makeText(context, "Dados do app limpos", Toast.LENGTH_SHORT).show()
            busy = false
            refresh()
        }
    }

    val cacheBytes = breakdown.items.firstOrNull { it.category == SpaceCategory.CACHE }?.bytes ?: 0L

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E14))
    ) {
        if (floating) {
            FloatingIconsBackground(kind = "managespace")
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.40f)))

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Voltar",
                        tint = Color.White
                    )
                }
                Text(
                    "Gerenciar espaço",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                "Dados do app",
                color = Color.White.copy(0.5f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                if (refreshing) "Calculando..." else formatBytes(breakdown.totalBytes),
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )
            Text(
                "Toque em um item para limpá-lo separadamente.",
                color = Color.White.copy(0.45f),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            breakdown.items.forEach { item ->
                CategoryRow(
                    title = item.title,
                    description = item.description,
                    sizeLabel = if (refreshing) "…" else formatBytes(item.bytes),
                    enabled = !busy && !refreshing,
                    onDelete = { confirm = item.category }
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { clearCategory(SpaceCategory.CACHE) },
                enabled = !busy && !refreshing,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(50)
            ) {
                Text("Limpar cache (${formatBytes(cacheBytes)})")
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { confirmAll = true },
                enabled = !busy && !refreshing,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE53935),
                    contentColor = Color.White
                )
            ) {
                Text("Limpar todos os dados", fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Suas músicas nunca são apagadas. \"Limpar todos os dados\" também preserva as imagens e vídeos de letras exportados; para removê-los, toque em \"Letras exportadas\".",
                color = Color.White.copy(0.45f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 28.dp)
            )
        }
    }

    confirm?.let { cat ->
        val title = breakdown.items.firstOrNull { it.category == cat }?.title ?: "item"
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Limpar $title?") },
            text = {
                Text(
                    when (cat) {
                        SpaceCategory.LIBRARY -> "Remove a lista de músicas e capas em cache do app. As faixas no armazenamento não são apagadas; a biblioteca será reescaneada."
                        SpaceCategory.EXPORTED_LYRICS -> "Apaga imagens e vídeos de letras que você gerou."
                        SpaceCategory.SETTINGS -> "Remove preferências salvas (tema, equalizador, Last.fm local, etc.)."
                        else -> "Esta ação não pode ser desfeita."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    clearCategory(cat)
                }) { Text("Limpar", color = Color(0xFFE53935)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text("Cancelar") }
            }
        )
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Limpar todos os dados?") },
            text = {
                Text(
                    "Cache, biblioteca em cache, playlists/histórico, fontes/presets e configurações serão apagados. " +
                        "Músicas no armazenamento e letras exportadas são preservadas."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmAll = false
                    clearAll()
                }) { Text("Limpar tudo", color = Color(0xFFE53935)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmAll = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun CategoryRow(
    title: String,
    description: String,
    sizeLabel: String,
    enabled: Boolean,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(0.07f))
            .clickable(enabled = enabled, onClick = onDelete)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                description,
                color = Color.White.copy(0.45f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            sizeLabel,
            color = Color.White.copy(0.85f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(end = 8.dp)
        )
        Icon(
            Icons.Rounded.Delete,
            contentDescription = "Limpar",
            tint = Color.White.copy(0.55f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Usa o StorageManager (mesma conta da linha "Gerenciar espaço" em Avançado), em vez de ter
 * um cálculo próprio: antes cada tela olhava pastas diferentes e os totais não batiam.
 */
private fun spaceCategoryOf(kind: StorageManager.Kind): SpaceCategory = when (kind) {
    StorageManager.Kind.CACHE -> SpaceCategory.CACHE
    StorageManager.Kind.LIBRARY -> SpaceCategory.LIBRARY
    StorageManager.Kind.LYRON_DATA -> SpaceCategory.PLAYLISTS
    StorageManager.Kind.FONTS_PRESETS -> SpaceCategory.FONTS_VIZ
    StorageManager.Kind.SETTINGS -> SpaceCategory.SETTINGS
    StorageManager.Kind.EXPORTS -> SpaceCategory.EXPORTED_LYRICS
}

private fun computeBreakdown(context: android.content.Context): SpaceBreakdown {
    val usage = StorageManager.computeUsage(context)
    val items = (usage.items + usage.exports).map {
        CategoryStat(spaceCategoryOf(it.kind), it.label, it.description, it.bytes)
    }
    return SpaceBreakdown(usage.totalBytes, items)
}

private fun performClear(context: android.content.Context, cat: SpaceCategory) {
    val kind = when (cat) {
        SpaceCategory.CACHE -> StorageManager.Kind.CACHE
        SpaceCategory.LIBRARY -> StorageManager.Kind.LIBRARY
        SpaceCategory.PLAYLISTS -> StorageManager.Kind.LYRON_DATA
        SpaceCategory.FONTS_VIZ -> StorageManager.Kind.FONTS_PRESETS
        SpaceCategory.SETTINGS -> StorageManager.Kind.SETTINGS
        SpaceCategory.EXPORTED_LYRICS -> StorageManager.Kind.EXPORTS
    }
    try { StorageManager.clearItem(context, kind) } catch (_: Exception) { }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f kB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.0f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}
