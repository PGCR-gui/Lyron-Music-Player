package com.tolete.player.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.VisualTransformation
import com.tolete.player.data.LastFmScrobbler
import com.tolete.player.data.LyronStorage
import com.tolete.player.data.ScrobbleEdits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** Página de configurações: login no Last.fm e liga/desliga do scrobbling. */
@Composable
internal fun ScrobblePage() {
    val context = LocalContext.current
    remember { LastFmScrobbler.init(context) } // idempotente; precisa estar pronto antes de ler o secret
    LaunchedEffect(Unit) { LastFmScrobbler.init(context) }

    val username by LastFmScrobbler.username.collectAsState()
    val enabled by LastFmScrobbler.enabled.collectAsState()
    val pending by LastFmScrobbler.pending.collectAsState()
    val status by LastFmScrobbler.status.collectAsState()
    val busy by LastFmScrobbler.busy.collectAsState()
    val scope = rememberCoroutineScope()

    val hasSecret by LastFmScrobbler.hasSecretFlow.collectAsState()
    val editsVersion by ScrobbleEdits.version.collectAsState()

    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf(LastFmScrobbler.getApiSecret()) }
    var apiKey by remember { mutableStateOf(LastFmScrobbler.getApiKey()) }
    var showSecret by remember { mutableStateOf(false) }
    val editsCount = remember(editsVersion) { ScrobbleEdits.count() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val n = withContext(Dispatchers.IO) {
                try {
                    val text = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                    if (text == null) -1 else ScrobbleEdits.importFrom(text)
                } catch (_: Exception) { -1 }
            }
            Toast.makeText(
                context,
                when {
                    n < 0 -> "Arquivo de edições inválido"
                    n == 0 -> "Nenhuma edição encontrada no arquivo"
                    else -> "$n edição(ões) importada(s)"
                },
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text("API Secret do Last.fm", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(
            "Cadastre um app em last.fm/api/account/create e cole aqui o “Shared secret”. " +
                "A API Key e o secret ficam salvos só neste aparelho.",
            fontSize = 13.sp,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = secret,
            onValueChange = { secret = it },
            label = { Text("API Secret") },
            singleLine = true,
            visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { LastFmScrobbler.setApiKey(apiKey); LastFmScrobbler.setApiSecret(secret) },
                enabled = secret.trim() != LastFmScrobbler.getApiSecret() ||
                    apiKey.trim() != LastFmScrobbler.getApiKey()
            ) { Text("Salvar") }
            OutlinedButton(onClick = { showSecret = !showSecret }) {
                Text(if (showSecret) "Ocultar" else "Mostrar")
            }
            if (hasSecret) {
                OutlinedButton(onClick = { secret = ""; LastFmScrobbler.setApiSecret("") }) { Text("Remover") }
            }
        }
        if (!hasSecret) {
            Text(
                "Sem o API Secret o Last.fm recusa o login e os scrobbles.",
                fontSize = 13.sp,
                color = Color(0xFFFFB74D),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Spacer(Modifier.height(24.dp))

        if (username == null) {
            Text("Entrar no Last.fm", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                "Use o usuário e a senha da sua conta. A senha é usada só para entrar e não fica salva no app.",
                fontSize = 13.sp,
                color = Color.White.copy(0.55f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text("Usuário") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("Senha") },
                singleLine = true,
                enabled = !busy,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val ok = LastFmScrobbler.login(user, pass)
                        if (ok) { pass = "" }
                    }
                },
                enabled = !busy && hasSecret && user.isNotBlank() && pass.isNotEmpty()
            ) {
                Text(if (busy) "Entrando…" else "Entrar")
            }
        } else {
            Text("Conectado como $username", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Enviar scrobbles", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Envia “ouvindo agora” e, quando o play é contado, o scrobble. " +
                            "Faixas com menos de 30 s não são enviadas.",
                        fontSize = 13.sp,
                        color = Color.White.copy(0.55f)
                    )
                }
                Switch(checked = enabled, onCheckedChange = { LastFmScrobbler.setEnabled(it) })
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (pending > 0) "Scrobbles aguardando envio: $pending" else "Nenhum scrobble pendente",
                fontSize = 13.sp,
                color = Color.White.copy(0.7f)
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pending > 0) {
                    OutlinedButton(onClick = { LastFmScrobbler.flushNow() }) { Text("Enviar pendentes") }
                }
                OutlinedButton(onClick = { LastFmScrobbler.logout() }) { Text("Sair da conta") }
            }
            Text(
                "Ao sair, os scrobbles pendentes são descartados.",
                fontSize = 12.sp,
                color = Color.White.copy(0.45f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (!status.isNullOrBlank()) {
            Text(
                status!!,
                fontSize = 13.sp,
                color = Color.White.copy(0.75f),
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        Spacer(Modifier.height(24.dp))
        Text("Edições automáticas", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(
            "Aplicadas só no envio ao Last.fm. Não alteram as tags do arquivo e não gravam edição permanente. " +
                "Faixas que já têm edição manual salva são ignoradas. Você ainda pode editar qualquer faixa pela notificação.",
            fontSize = 13.sp,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        val autoFirst by ScrobbleEdits.autoFirstArtistFlow.collectAsState()
        val autoParens by ScrobbleEdits.autoStripParensFlow.collectAsState()
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Só o primeiro artista", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Em faixas com vários artistas, usa apenas o primeiro (separadores iguais aos da Biblioteca, ex.: ; / feat.).",
                    fontSize = 12.sp,
                    color = Color.White.copy(0.5f)
                )
            }
            Switch(
                checked = autoFirst,
                onCheckedChange = { ScrobbleEdits.setAutoFirstArtist(it) }
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Remover texto entre parênteses", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Tira trechos como (Ao Vivo), (Remastered) e [Bonus] do título, artista e álbum antes de scrobblar.",
                    fontSize = 12.sp,
                    color = Color.White.copy(0.5f)
                )
            }
            Switch(
                checked = autoParens,
                onCheckedChange = { ScrobbleEdits.setAutoStripParens(it) }
            )
        }

        Spacer(Modifier.height(24.dp))
        Text("Edições de tags", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(
            "Salvas em ${remember { LyronStorage.root.name }}/${ScrobbleEdits.FILE_NAME}. Use “Editar tags” na notificação " +
                "do scrobbler para criar uma. Também dá para importar um arquivo no mesmo formato.",
            fontSize = 13.sp,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            if (editsCount > 0) "$editsCount edição(ões) salva(s)" else "Nenhuma edição salva",
            fontSize = 13.sp,
            color = Color.White.copy(0.7f)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
            Text("Importar edições")
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "O scrobble é enviado quando o play é contado (veja Player › Histórico / Mais tocadas), " +
                "com a hora em que a faixa começou a tocar.",
            fontSize = 12.sp,
            color = Color.White.copy(0.45f)
        )
    }
}
