package com.tolete.player.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
internal fun LyricsContributionsPage(appColors: AppColors) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var contributions by remember { mutableStateOf<List<LyricsContribution>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var pendingDelete by remember { mutableStateOf<LyricsContribution?>(null) }
    var isDeleting by remember { mutableStateOf(false) }

    fun refreshContributions() {
        scope.launch {
            isLoading = true
            contributions = withContext(Dispatchers.IO) {
                LyricsContributionRepository.list(context)
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { refreshContributions() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            "Contribuições exportadas",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Cada item guarda o .lrc e o .txt com o trecho de index.json. Use Compartilhar novamente para escolher o Telegram, GitHub ou outro destino.",
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                .clickable {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+21ytHwZf375jYmYx"))
                        )
                    } catch (_: Exception) {
                        Toast.makeText(context, "Não foi possível abrir o Telegram", Toast.LENGTH_LONG).show()
                    }
                }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = appColors.accent)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Grupo de compartilhamento de letras", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text("Abrir o grupo no Telegram", fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
            }
        }
        TextButton(onClick = { refreshContributions() }, enabled = !isLoading) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = appColors.accent)
            Text("Atualizar lista", color = appColors.accent, modifier = Modifier.padding(start = 6.dp))
        }

        when {
            isLoading -> Box(
                Modifier.fillMaxWidth().padding(vertical = 30.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = appColors.accent)
            }
            contributions.isEmpty() -> Text(
                "Ainda não há contribuições salvas. Ao compartilhar uma letra pela tela de reprodução, ela aparecerá aqui para você reenviar depois.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 14.dp)
            )
            else -> contributions.forEach { contribution ->
                ContributionCard(
                    contribution = contribution,
                    accent = appColors.accent,
                    onShare = { LyricsContributionShareHelper.shareSaved(context, contribution) },
                    onDelete = { pendingDelete = contribution }
                )
            }
        }
    }

    pendingDelete?.let { contribution ->
        AppAlertDialog(
            onDismissRequest = { if (!isDeleting) pendingDelete = null },
            title = { Text("Excluir contribuição?") },
            text = {
                Text("Os arquivos .lrc e .txt de ${contribution.title} serão removidos do histórico deste aparelho.")
            },
            confirmButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        isDeleting = true
                        scope.launch {
                            val deleted = withContext(Dispatchers.IO) {
                                runCatching {
                                    LyricsContributionRepository.delete(context, contribution)
                                }.getOrDefault(false)
                            }
                            if (deleted) {
                                contributions = contributions.filterNot { it.id == contribution.id }
                                pendingDelete = null
                            } else {
                                Toast.makeText(context, "Não foi possível excluir esta contribuição", Toast.LENGTH_LONG).show()
                            }
                            isDeleting = false
                        }
                    }
                ) {
                    Text(if (isDeleting) "Excluindo…" else "Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }, enabled = !isDeleting) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun ContributionCard(
    contribution: LyricsContribution,
    accent: Color,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Text(
            contribution.title.ifBlank { contribution.lrcFile.nameWithoutExtension },
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            contribution.artist,
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "sync_by: ${contribution.syncBy.ifBlank { "—" }} · ${formatContributionDate(contribution.createdAt)}",
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 12.sp
        )
        Text(
            "index.json: ${contribution.relativePath}",
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 3.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onShare) {
                Icon(Icons.Rounded.Share, contentDescription = null, tint = accent)
                Text("Compartilhar novamente", color = accent, modifier = Modifier.padding(start = 5.dp))
            }
            TextButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text("Excluir", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 5.dp))
            }
        }
    }
}

private fun formatContributionDate(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
