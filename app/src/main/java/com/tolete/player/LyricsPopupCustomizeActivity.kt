package com.tolete.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tela leve de personalização do popup de letras (aberta pela notificação). */
class LyricsPopupCustomizeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("tolete", MODE_PRIVATE)
        setContent {
            var fontSize by remember {
                mutableFloatStateOf(
                    prefs.getFloat(
                        LyricsPopupOverlay.KEY_FONT_SIZE,
                        prefs.getFloat("lyrics_font_size", 22f)
                    ).coerceIn(12f, 40f)
                )
            }
            var bgAlpha by remember {
                mutableIntStateOf(prefs.getInt(LyricsPopupOverlay.KEY_BG_ALPHA, 90).coerceIn(0, 100))
            }
            var themeId by remember {
                mutableStateOf(
                    prefs.getString(
                        LyricsPopupOverlay.KEY_THEME,
                        LyricsPopupOverlay.PopupTheme.DYNAMIC.id
                    ) ?: LyricsPopupOverlay.PopupTheme.DYNAMIC.id
                )
            }

            fun persist() {
                prefs.edit()
                    .putFloat(LyricsPopupOverlay.KEY_FONT_SIZE, fontSize)
                    .putInt(LyricsPopupOverlay.KEY_BG_ALPHA, bgAlpha)
                    .putString(LyricsPopupOverlay.KEY_THEME, themeId)
                    .apply()
                LyricsPopupOverlay.applyChrome(this@LyricsPopupCustomizeActivity)
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF121218))
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    "Personalizar popup de letras",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(20.dp))

                Text("Tamanho da letra", color = Color.White.copy(0.7f), fontSize = 13.sp)
                Text("${fontSize.toInt()} sp", color = Color.White, fontWeight = FontWeight.Medium)
                Slider(
                    value = fontSize,
                    onValueChange = {
                        fontSize = it
                        persist()
                    },
                    valueRange = 12f..40f
                )

                Spacer(Modifier.height(12.dp))
                Text("Opacidade do fundo", color = Color.White.copy(0.7f), fontSize = 13.sp)
                Text("$bgAlpha%", color = Color.White, fontWeight = FontWeight.Medium)
                Slider(
                    value = bgAlpha.toFloat(),
                    onValueChange = {
                        bgAlpha = it.toInt().coerceIn(0, 100)
                        persist()
                    },
                    valueRange = 0f..100f
                )

                Spacer(Modifier.height(16.dp))
                Text("Tema", color = Color.White.copy(0.7f), fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                LyricsPopupOverlay.PopupTheme.entries.forEach { theme ->
                    val selected = themeId == theme.id
                    val colors = LyricsPopupOverlay.themeColors(theme.id, prefs)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) Color.White.copy(0.12f) else Color.White.copy(0.05f))
                            .border(
                                1.dp,
                                if (selected) Color(0xFF80CBC4) else Color.White.copy(0.12f),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                themeId = theme.id
                                persist()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier
                                .size(40.dp, 28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(colors.first), Color(colors.second))
                                    )
                                )
                        )
                        Column(Modifier.weight(1f)) {
                            Text(theme.label, color = Color.White, fontWeight = FontWeight.Medium)
                            if (theme == LyricsPopupOverlay.PopupTheme.DYNAMIC) {
                                Text(
                                    "Usa as cores das letras do app",
                                    color = Color.White.copy(0.5f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { finish() }, modifier = Modifier.align(Alignment.End)) {
                    Text("Concluir")
                }
            }
        }
    }
}
