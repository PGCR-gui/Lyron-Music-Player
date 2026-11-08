package com.tolete.player.scrobble

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tolete.player.PlaybackService
import com.tolete.player.data.LastFmScrobbler
import com.tolete.player.data.ScrobbleEdits
import com.tolete.player.ui.AppAlertDialog
import com.tolete.player.ui.AppColors
import com.tolete.player.ui.DefaultAppColors
import com.tolete.player.ui.LiveThemeHolder
import com.tolete.player.ui.LocalAppColors
import com.tolete.player.ui.MusicViewModel
import com.tolete.player.ui.colorsFromSeed
import com.tolete.player.ui.staticTheme
import kotlin.concurrent.thread

/**
 * Diálogo para editar as tags da faixa atual só no que é enviado ao Last.fm
 * (o arquivo de música não é alterado). Salva em /Lyron/scrobble_edits.json.
 */
class ScrobbleEditActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LastFmScrobbler.init(this)
        val track = LastFmScrobbler.current.value
        if (track == null) {
            Toast.makeText(this, "Nenhuma faixa tocando", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val original = ScrobbleEdits.Meta(track.title, track.artist, track.album, track.albumArtist)
        val current = LastFmScrobbler.metaFor(track)
        val hasEdit = ScrobbleEdits.findFor(original) != null

        val themeColors = LiveThemeHolder.colors ?: themeFromPrefs()

        setContent {
            CompositionLocalProvider(LocalAppColors provides themeColors) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var title by remember { mutableStateOf(current.track) }
                var artist by remember { mutableStateOf(current.artist) }
                var album by remember { mutableStateOf(current.album) }
                var albumArtist by remember { mutableStateOf(current.albumArtist) }

                AppAlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text("Editar tags do scrobbling") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text(
                                "Muda só o que é enviado ao Last.fm. O arquivo da música não é alterado.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            field("Título", title) { title = it }
                            field("Artista", artist) { artist = it }
                            field("Álbum", album) { album = it }
                            field("Artista do álbum", albumArtist) { albumArtist = it }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = title.isNotBlank() && artist.isNotBlank(),
                            onClick = {
                                val edited = ScrobbleEdits.Meta(title, artist, album, albumArtist)
                                thread {
                                    val ok = ScrobbleEdits.saveEdit(original, edited)
                                    runOnUiThread {
                                        Toast.makeText(
                                            this@ScrobbleEditActivity,
                                            if (ok) "Edição salva em Lyron/${ScrobbleEdits.FILE_NAME}"
                                            else "Edição aplicada, mas não foi possível gravar o arquivo",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        if (PlaybackService.exoPlayerRef?.isPlaying == true) {
                                            LastFmScrobbler.nowPlaying(track)
                                        }
                                        finish()
                                    }
                                }
                            }
                        ) { Text("Salvar") }
                    },
                    dismissButton = {
                        Column {
                            if (hasEdit) {
                                TextButton(onClick = {
                                    thread {
                                        ScrobbleEdits.removeEditFor(original)
                                        runOnUiThread {
                                            Toast.makeText(
                                                this@ScrobbleEditActivity,
                                                "Edição removida: voltam as tags originais",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            finish()
                                        }
                                    }
                                }) { Text("Remover edição") }
                            }
                            TextButton(onClick = { finish() }) { Text("Cancelar") }
                        }
                    }
                )
            }
            }
        }
    }

    /** Se o app não está aberto (sem tema ao vivo), recria o tema salvo nas preferências. */
    private fun themeFromPrefs(): AppColors {
        val prefs = getSharedPreferences("tolete", MODE_PRIVATE)
        val id = runCatching {
            MusicViewModel.AppThemeId.valueOf(
                prefs.getString("app_theme", MusicViewModel.AppThemeId.DYNAMIC.name)!!
            )
        }.getOrDefault(MusicViewModel.AppThemeId.DYNAMIC)
        return if (id == MusicViewModel.AppThemeId.DYNAMIC) {
            colorsFromSeed(Color(prefs.getInt("dynamic_default_color", 0xFFBB86FC.toInt())))
        } else staticTheme(id)
    }

    @androidx.compose.runtime.Composable
    private fun field(label: String, value: String, onChange: (String) -> Unit) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
    }
}
