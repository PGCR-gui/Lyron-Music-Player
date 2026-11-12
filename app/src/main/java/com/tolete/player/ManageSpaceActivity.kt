package com.tolete.player

import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tolete.player.data.LyronStorage
import com.tolete.player.data.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tela "Gerenciar espaço".
 *
 * Declarada como android:manageSpaceActivity no manifesto: nas configurações do sistema
 * (Info do app > Armazenamento) o botão "Limpar dados" passa a ser "Gerenciar espaço" e abre esta tela.
 * Também é aberta por Configurações > Avançado > Gerenciar espaço.
 * Tocar em um item da lista limpa só aquele item (sempre pedindo confirmação).
 */
class ManageSpaceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        // Esta activity pode ser aberta pelo sistema sem a MainActivity ter rodado.
        LyronStorage.init(this)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ManageSpaceScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun ManageSpaceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var usage by remember { mutableStateOf<StorageManager.Usage?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var partialFailures by remember { mutableIntStateOf(0) }
    var pendingClear by remember { mutableStateOf<StorageManager.Item?>(null) }
    var itemFailures by remember { mutableStateOf<Pair<String, Int>?>(null) }

    LaunchedEffect(refreshKey) {
        usage = withContext(Dispatchers.IO) { StorageManager.computeUsage(context) }
    }

    fun wipeInternal() {
        busy = true
        scope.launch(Dispatchers.IO) { StorageManager.wipeAppDataAndExit(context) }
    }

    fun clearOne(item: StorageManager.Item) {
        busy = true
        scope.launch {
            val failed = withContext(Dispatchers.IO) { StorageManager.clearItem(context, item.kind) }
            if (failed == 0 && item.kind.closesApp) {
                // Fecha o app para recarregar do zero o que estava em memória.
                withContext(Dispatchers.IO) { StorageManager.exitApp(context) }
                return@launch
            }
            busy = false
            refreshKey++
            if (failed > 0) {
                itemFailures = item.label to failed
            } else {
                Toast.makeText(context, "${item.label}: limpo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun onItemClick(item: StorageManager.Item) {
        if (item.bytes <= 0L) {
            Toast.makeText(context, "Nada para limpar em \"${item.label}\"", Toast.LENGTH_SHORT).show()
        } else {
            pendingClear = item
        }
    }

    fun startWipe() {
        busy = true
        scope.launch {
            val failed = withContext(Dispatchers.IO) { StorageManager.clearLyronFolderData() }
            if (failed > 0) {
                busy = false
                partialFailures = failed
            } else {
                wipeInternal()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF1B1530), Color(0xFF0D0D12)))
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                }
                Text(
                    "Gerenciar espaço",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                val u = usage
                Text(
                    "Dados do app",
                    fontSize = 13.sp,
                    color = Color.White.copy(0.55f)
                )
                Text(
                    if (u == null) "Calculando…" else StorageManager.formatSize(context, u.totalBytes),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
                )

                if (u != null) {
                    Text(
                        "Toque em um item para limpá-lo separadamente.",
                        fontSize = 12.sp,
                        color = Color.White.copy(0.45f),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    u.items.forEach { item ->
                        UsageRow(
                            item.label,
                            item.description,
                            StorageManager.formatSize(context, item.bytes),
                            enabled = !busy,
                            onClick = { onItemClick(item) }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    UsageRow(
                        u.exports.label,
                        u.exports.description,
                        StorageManager.formatSize(context, u.exports.bytes),
                        dimmed = true,
                        enabled = !busy,
                        onClick = { onItemClick(u.exports) }
                    )
                }

                Spacer(Modifier.height(20.dp))
                OutlinedButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { StorageManager.clearCache(context) }
                            busy = false
                            refreshKey++
                            Toast.makeText(
                                context,
                                if (ok) "Cache limpo" else "Alguns arquivos de cache não puderam ser removidos",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    enabled = !busy && u != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Limpar cache" + if (u != null) " (${StorageManager.formatSize(context, u.cacheBytes)})" else "",
                        color = Color.White
                    )
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { confirmWipe = true },
                    enabled = !busy && u != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD94B4B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Limpar todos os dados", color = Color.White)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Suas músicas nunca são apagadas. \"Limpar todos os dados\" também preserva as imagens e " +
                        "vídeos de letras exportados; para removê-los, toque em \"Letras exportadas\".",
                    fontSize = 12.sp,
                    color = Color.White.copy(0.45f)
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    pendingClear?.let { item ->
        val needsAllFiles = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager() &&
            (item.kind == StorageManager.Kind.LYRON_DATA || item.kind == StorageManager.Kind.EXPORTS)
        AlertDialog(
            onDismissRequest = { pendingClear = null },
            title = { Text("Limpar ${item.label}?") },
            text = {
                Text(
                    clearWarning(item.kind) +
                        "\n\nEspaço ocupado: ${StorageManager.formatSize(context, item.bytes)}." +
                        if (needsAllFiles) "\n\nAviso: sem a permissão \"Acesso a todos os arquivos\", " +
                            "alguns arquivos podem não ser apagados." else ""
                )
            },
            confirmButton = {
                TextButton(onClick = { pendingClear = null; clearOne(item) }) {
                    Text("Limpar", color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingClear = null }) { Text("Cancelar") }
            }
        )
    }

    itemFailures?.let { (label, count) ->
        AlertDialog(
            onDismissRequest = { itemFailures = null },
            title = { Text("Alguns arquivos ficaram") },
            text = {
                Text(
                    "$count item(ns) de \"$label\" não puderam ser apagados " +
                        "(talvez falte a permissão \"Acesso a todos os arquivos\")."
                )
            },
            confirmButton = {
                TextButton(onClick = { itemFailures = null }) { Text("OK") }
            }
        )
    }

    if (confirmWipe) {
        // Sem MANAGE_EXTERNAL_STORAGE a pasta Lyron em /storage/emulated/0 pode não ser apagável.
        val needsAllFiles = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Limpar todos os dados?") },
            text = {
                Text(
                    "Isso apaga playlists, favoritos, histórico de reprodução, fotos e infos de artistas, " +
                        "biblioteca em cache, fontes importadas, presets e todas as configurações, " +
                        "inclusive a conta do Last.fm. O app volta ao estado de recém-instalado e será fechado.\n\n" +
                        "Suas músicas e as letras exportadas não serão apagadas. " +
                        "Faça um Backup antes se quiser guardar seus dados." +
                        if (needsAllFiles) "\n\nAviso: sem a permissão \"Acesso a todos os arquivos\", " +
                            "a pasta Lyron do armazenamento pode não ser limpa." else ""
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmWipe = false; startWipe() }) {
                    Text("Limpar tudo", color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text("Cancelar") }
            }
        )
    }

    if (partialFailures > 0) {
        AlertDialog(
            onDismissRequest = { partialFailures = 0 },
            title = { Text("Alguns arquivos ficaram") },
            text = {
                Text(
                    "$partialFailures item(ns) da pasta Lyron não puderam ser apagados " +
                        "(talvez falte a permissão \"Acesso a todos os arquivos\"). " +
                        "Deseja limpar o restante dos dados do app mesmo assim?"
                )
            },
            confirmButton = {
                TextButton(onClick = { partialFailures = 0; wipeInternal() }) {
                    Text("Continuar", color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = { partialFailures = 0; refreshKey++ }) { Text("Cancelar") }
            }
        )
    }
}

private fun clearWarning(kind: StorageManager.Kind): String = when (kind) {
    StorageManager.Kind.CACHE ->
        "Apaga arquivos temporários (atualizações baixadas, arquivos da edição de tags e vídeos em geração). " +
            "Nada que você criou é perdido."
    StorageManager.Kind.LIBRARY ->
        "Apaga a lista de músicas em cache e as capas salvas pelo app. Suas músicas não são apagadas: " +
            "a biblioteca será escaneada de novo na próxima abertura.\n\nO app será fechado em seguida."
    StorageManager.Kind.LYRON_DATA ->
        "Apaga playlists, favoritos, histórico de reprodução e as fotos e infos de artistas e álbuns da pasta Lyron. " +
            "Suas músicas não são apagadas. Faça um Backup antes se quiser guardar esses dados.\n\n" +
            "O app será fechado em seguida."
    StorageManager.Kind.FONTS_PRESETS ->
        "Apaga as fontes importadas e os presets .milk do visualizador. Os presets que já vêm com o app voltam sozinhos."
    StorageManager.Kind.SETTINGS ->
        "Apaga todas as preferências e ajustes do app, inclusive a conta do Last.fm, o equalizador e as edições " +
            "de scrobble salvas. Faça um Backup antes se quiser guardar esses dados.\n\nO app será fechado em seguida."
    StorageManager.Kind.EXPORTS ->
        "Apaga de forma permanente as imagens e os vídeos de letras que você gerou. Essa ação não pode ser desfeita."
}

@Composable
private fun UsageRow(
    title: String,
    subtitle: String,
    size: String,
    dimmed: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(if (dimmed) 0.03f else 0.06f))
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(if (dimmed) 0.7f else 1f)
            )
            Text(subtitle, fontSize = 12.sp, color = Color.White.copy(0.5f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(size, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(if (dimmed) 0.6f else 0.9f))
            if (onClick != null) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Limpar $title",
                    tint = Color.White.copy(0.45f),
                    modifier = Modifier.padding(start = 10.dp).size(20.dp)
                )
            }
        }
    }
}
