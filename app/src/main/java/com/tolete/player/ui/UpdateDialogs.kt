package com.tolete.player.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tolete.player.data.UpdateManager
import com.tolete.player.data.UpdateManager.State

private const val PREFS = "tolete"
private const val KEY_LAST_SEEN_CODE = "changelog_last_seen_code"

private val DialogBg = Color(0xFF1C1C28)

/**
 * Hospeda os diálogos de atualização (nova versão / download / instalar) e dispara a
 * checagem automática uma única vez por abertura do app.
 */
@Composable
internal fun UpdateHost() {
    val context = LocalContext.current
    val state by UpdateManager.state.collectAsState()

    LaunchedEffect(Unit) { UpdateManager.check(context, manual = false) }

    // Resultados que só interessam quando o usuário pediu a checagem manualmente
    val currentContext by rememberUpdatedState(context)
    LaunchedEffect(state) {
        when (val s = state) {
            is State.UpToDate -> {
                if (s.manual) {
                    Toast.makeText(currentContext, "Você já está na versão mais recente", Toast.LENGTH_SHORT).show()
                }
                UpdateManager.dismiss()
            }
            is State.Failed -> {
                if (s.manual) Toast.makeText(currentContext, s.message, Toast.LENGTH_LONG).show()
                UpdateManager.dismiss()
            }
            else -> Unit
        }
    }

    when (val s = state) {
        is State.Available -> AlertDialog(
            onDismissRequest = { UpdateManager.dismiss() },
            containerColor = DialogBg,
            title = { Text("Nova versão disponível") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "v${UpdateManager.installedVersion(context)}  →  v${s.info.version}",
                        fontSize = 14.sp, color = Color.White.copy(0.7f)
                    )
                    if (s.info.apkSize > 0) {
                        Text(
                            "Tamanho: %.1f MB".format(s.info.apkSize / 1_048_576.0),
                            fontSize = 12.sp, color = Color.White.copy(0.5f)
                        )
                    }
                    if (s.info.notes.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(s.info.notes.trim(), fontSize = 13.sp, color = Color.White.copy(0.85f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { UpdateManager.download(context, s.info) }) { Text("Baixar e instalar") }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = { UpdateManager.dismiss() }) { Text("Agora não") }
                    TextButton(onClick = { UpdateManager.skipVersion(context, s.info) }) { Text("Ignorar esta versão") }
                }
            }
        )

        is State.Downloading -> AlertDialog(
            onDismissRequest = { /* não fecha durante o download */ },
            containerColor = DialogBg,
            title = { Text("Baixando v${s.info.version}…") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("${(s.progress * 100).toInt()}%", fontSize = 13.sp, color = Color.White.copy(0.7f))
                }
            },
            confirmButton = {}
        )

        is State.ReadyToInstall -> AlertDialog(
            onDismissRequest = { UpdateManager.dismiss() },
            containerColor = DialogBg,
            title = { Text("Download concluído") },
            text = {
                Text(
                    "Toque em Instalar para atualizar para a v${s.info.version}. " +
                        "Se o Android pedir, permita que o Lyron instale apps nesta tela de ajustes e volte aqui.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { UpdateManager.install(context, s.file) }) { Text("Instalar") }
            },
            dismissButton = {
                TextButton(onClick = { UpdateManager.dismiss() }) { Text("Depois") }
            }
        )

        else -> Unit
    }
}

/**
 * Pop-up de changelog: aparece uma vez após o app ser atualizado (versionCode maior
 * que o último visto). Na primeira instalação só grava a versão, sem mostrar nada.
 */
@Composable
internal fun ChangelogOnUpdateDialog() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val currentCode = remember { UpdateManager.installedCode(context).toInt() }
    val lastSeen = remember {
        val saved = prefs.getInt(KEY_LAST_SEEN_CODE, -1)
        if (saved >= 0) saved else {
            // Nunca gravou: instalação nova (não mostra nada) ou app atualizado de uma versão
            // anterior a este recurso (mostra só as novidades da versão atual).
            val fresh = try {
                val pi = context.packageManager.getPackageInfo(context.packageName, 0)
                pi.firstInstallTime == pi.lastUpdateTime
            } catch (_: Exception) { true }
            if (fresh) -1 else currentCode - 1
        }
    }

    val entries = remember {
        if (lastSeen < 0) emptyList() else changelogEntries.filter {
            val c = it.code.toIntOrNull() ?: return@filter false
            c > lastSeen && c <= currentCode
        }
    }
    var show by remember { mutableStateOf(entries.isNotEmpty()) }

    LaunchedEffect(Unit) {
        // Grava já: se o usuário fechar o app sem tocar em "OK", não repete para sempre
        if (prefs.getInt(KEY_LAST_SEEN_CODE, -1) != currentCode) prefs.edit().putInt(KEY_LAST_SEEN_CODE, currentCode).apply()
    }

    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            containerColor = DialogBg,
            title = { Text("O que há de novo") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    entries.forEach { e ->
                        Text("v${e.version}", fontSize = 15.sp, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        e.changes.forEach { Text("•  $it", fontSize = 13.sp, color = Color.White.copy(0.85f)) }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { show = false }) { Text("OK") } }
        )
    }
}
