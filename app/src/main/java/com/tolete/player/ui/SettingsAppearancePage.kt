package com.tolete.player.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tolete.player.data.LibrarySettings
import com.tolete.player.data.SourceFolder
import com.tolete.player.ui.MusicViewModel.AppThemeId
import com.tolete.player.ui.MusicViewModel.ShuffleType
import java.io.File

internal fun themeLabel(id: AppThemeId) = when (id) {
    AppThemeId.DYNAMIC -> "Dinâmico (capa do álbum)"
    AppThemeId.DEFAULT -> "Padrão"
    AppThemeId.AQUA -> "Aqua"
    AppThemeId.SUNSET -> "Sunset"
    AppThemeId.FOREST -> "Forest"
    AppThemeId.MIDNIGHT -> "Midnight"
    AppThemeId.ROSE -> "Rose"
    AppThemeId.EMBER -> "Ember"
    AppThemeId.AMOLED -> "AMOLED"
}


@Composable
internal fun AppearancePage(
    themeId: AppThemeId,
    uiFontPath: String?,
    onThemeSelected: (AppThemeId) -> Unit,
    dynamicDefaultColor: Int = 0xFFBB86FC.toInt(),
    onDynamicDefaultColorChange: (Int) -> Unit = {},
    onPickUiFont: () -> Unit,
    onClearUiFont: () -> Unit,
    onSelectUiFont: (String?) -> Unit = {},
    onOpenLyricsViewer: () -> Unit,
    splashEnabled: Boolean = true,
    onSplashEnabledChange: (Boolean) -> Unit = {},
    splashSoundEnabled: Boolean = true,
    onSplashSoundChange: (Boolean) -> Unit = {}
) {
    val themes = listOf(
        AppThemeId.DYNAMIC to "Dinâmico",
        AppThemeId.DEFAULT to "Padrão",
        AppThemeId.AQUA to "Aqua",
        AppThemeId.SUNSET to "Sunset",
        AppThemeId.FOREST to "Forest",
        AppThemeId.MIDNIGHT to "Midnight",
        AppThemeId.ROSE to "Rose",
        AppThemeId.EMBER to "Ember",
        AppThemeId.AMOLED to "AMOLED"
    )
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item {
            Text(
                "Tema",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(0.55f),
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
            )
        }
        items(themes) { (id, label) ->
            val selected = themeId == id
            val preview = staticTheme(id)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) Color.White.copy(0.14f) else Color.Transparent)
                    .clickable { onThemeSelected(id) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(preview.gradient)
                        .border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(10.dp))
                )
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(label, fontSize = 16.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    if (id == AppThemeId.DYNAMIC) {
                        Text(
                            "Duas cores da capa enquanto toca",
                            fontSize = 12.sp,
                            color = Color.White.copy(0.5f)
                        )
                    }
                }
                if (selected) {
                    Text("✓", color = preview.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (themeId == AppThemeId.DYNAMIC) {
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Cor padrão do dinâmico (sem música tocando)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(0.55f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                val seedColors = listOf(
                    0xFFBB86FC.toInt(),
                    0xFF00D2FF.toInt(),
                    0xFFFF6B4A.toInt(),
                    0xFF4ADE80.toInt(),
                    0xFF60A5FA.toInt(),
                    0xFFF472B6.toInt(),
                    0xFFFBBF24.toInt(),
                    0xFFE53935.toInt(),
                    0xFF26A69A.toInt(),
                    0xFFAB47BC.toInt()
                )
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    seedColors.forEach { argb ->
                        val sel = dynamicDefaultColor == argb
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    if (sel) 3.dp else 1.dp,
                                    if (sel) Color.White else Color.White.copy(0.3f),
                                    CircleShape
                                )
                                .clickable { onDynamicDefaultColorChange(argb) }
                        )
                    }
                }
                Text(
                    "Usada quando o tema dinâmico está ativo e nenhuma faixa está tocando.",
                    fontSize = 12.sp,
                    color = Color.White.copy(0.45f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Espectro RGB",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(0.55f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                RgbSpectrumPicker(
                    selected = dynamicDefaultColor,
                    onColorChange = onDynamicDefaultColorChange,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
            }
        }
        item {
            Spacer(Modifier.height(20.dp))
            Text(
                "Fonte do player",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(0.55f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                "Selecionada: ${BuiltInFonts.displayLabel(uiFontPath)}",
                fontSize = 12.sp,
                color = Color.White.copy(0.5f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            // Sistema
            FontOptionRow(
                label = "Padrão do sistema",
                selected = uiFontPath.isNullOrBlank() || uiFontPath == "system",
                onClick = onClearUiFont
            )
            BuiltInFonts.ALL.forEach { font ->
                FontOptionRow(
                    label = font.displayName,
                    selected = uiFontPath == font.storageKey,
                    onClick = { onSelectUiFont(font.storageKey) }
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(0.08f))
                    .clickable(onClick = onPickUiFont)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.FontDownload, contentDescription = null)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Carregar fonte personalizada", fontSize = 16.sp)
                    Text(
                        if (uiFontPath != null && !uiFontPath.startsWith("builtin:") && uiFontPath != "system")
                            File(uiFontPath).name else "Arquivo .ttf / .otf",
                        fontSize = 12.sp,
                        color = Color.White.copy(0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (uiFontPath != null && !uiFontPath.startsWith("builtin:") && uiFontPath != "system") {
                Text(
                    "Remover fonte personalizada",
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clickable(onClick = onClearUiFont)
                        .padding(vertical = 6.dp),
                    fontSize = 14.sp,
                    color = Color(0xFFFF8A80)
                )
            }
            Text(
                "Afeta títulos, listas e interface — não altera a fonte das letras.",
                fontSize = 12.sp,
                color = Color.White.copy(0.4f),
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )

            Text(
                "Splash screen",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(0.55f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(0.08f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Mostrar splash", fontSize = 16.sp)
                    Text(
                        "Desative para abrir o app direto na biblioteca",
                        fontSize = 12.sp,
                        color = Color.White.copy(0.5f)
                    )
                }
                Switch(
                    checked = splashEnabled,
                    onCheckedChange = onSplashEnabledChange
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(0.08f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Som do splash", fontSize = 16.sp)
                    Text(
                        "Reproduz o som ao abrir o app",
                        fontSize = 12.sp,
                        color = Color.White.copy(0.5f)
                    )
                }
                Switch(
                    checked = splashSoundEnabled,
                    onCheckedChange = onSplashSoundChange,
                    enabled = splashEnabled
                )
            }
            Spacer(Modifier.height(16.dp))

            Text(
                "Legendas",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(0.55f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(0.08f))
                    .clickable(onClick = onOpenLyricsViewer)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.FontDownload, contentDescription = null)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Visualizador de legendas", fontSize = 16.sp)
                    Text(
                        "Fonte, tamanho, cores e borda",
                        fontSize = 12.sp,
                        color = Color.White.copy(0.5f)
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(0.5f))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}


@Composable
internal fun LyricsViewerPage(
    lyricsFontPath: String?,
    lyricsFontSize: Float,
    lyricsPrimaryColor: Int,
    lyricsSecondaryColor: Int,
    lyricsBorderColor: Int,
    lyricsBorderWidth: Float,
    onPickFont: () -> Unit,
    onClearFont: () -> Unit,
    onSelectFont: (String?) -> Unit = {},
    onFontSizeChange: (Float) -> Unit,
    onPrimaryColorChange: (Int) -> Unit,
    onSecondaryColorChange: (Int) -> Unit,
    onBorderColorChange: (Int) -> Unit,
    onBorderWidthChange: (Float) -> Unit
) {
    val primaryPresets = listOf(
        0xFFFFFFFF.toInt(),
        0xFFBB86FC.toInt(),
        0xFF00D2FF.toInt(),
        0xFFFF6B4A.toInt(),
        0xFF4ADE80.toInt(),
        0xFFF472B6.toInt(),
        0xFFFBBF24.toInt(),
        0xFF60A5FA.toInt(),
        0xFFFFEB3B.toInt(),
        0xFFE91E63.toInt(),
        0xFF00BCD4.toInt(),
        0xFFFF9800.toInt()
    )
    val secondaryPresets = listOf(
        0x66FFFFFF.toInt(),
        0x99FFFFFF.toInt(),
        0x44FFFFFF.toInt(),
        0xFF888888.toInt(),
        0xFFB0BEC5.toInt(),
        0xFF90CAF9.toInt(),
        0xFFCE93D8.toInt(),
        0xFFA5D6A7.toInt()
    )
    val borderPresets = listOf(
        0xFF000000.toInt() to "Preto",
        0xFFFFFFFF.toInt() to "Branco",
        0xFF000000.toInt() to "Preto",
        0x88000000.toInt() to "Preto 50%"
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Text(
            "Fonte das letras",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
        )
        Text(
            "Selecionada: ${BuiltInFonts.displayLabel(lyricsFontPath)}",
            fontSize = 12.sp,
            color = Color.White.copy(0.5f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        FontOptionRow(
            label = "Padrão do sistema",
            selected = lyricsFontPath.isNullOrBlank() || lyricsFontPath == "system",
            onClick = onClearFont
        )
        BuiltInFonts.ALL.forEach { font ->
            FontOptionRow(
                label = font.displayName,
                selected = lyricsFontPath == font.storageKey,
                onClick = { onSelectFont(font.storageKey) }
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(0.08f))
                .clickable(onClick = onPickFont)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.FontDownload, contentDescription = null)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Carregar fonte personalizada", fontSize = 16.sp)
                Text(
                    if (lyricsFontPath != null && !lyricsFontPath.startsWith("builtin:") && lyricsFontPath != "system")
                        File(lyricsFontPath).name else "Arquivo .ttf / .otf",
                    fontSize = 12.sp,
                    color = Color.White.copy(0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (lyricsFontPath != null && !lyricsFontPath.startsWith("builtin:") && lyricsFontPath != "system") {
            Text(
                "Remover fonte personalizada",
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clickable(onClick = onClearFont)
                    .padding(vertical = 6.dp),
                fontSize = 14.sp,
                color = Color(0xFFFF8A80)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Tamanho da fonte: ${lyricsFontSize.toInt()} sp",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("12", fontSize = 12.sp, color = Color.White.copy(0.5f))
            androidx.compose.material3.Slider(
                value = lyricsFontSize,
                onValueChange = onFontSizeChange,
                valueRange = 12f..48f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text("48", fontSize = 12.sp, color = Color.White.copy(0.5f))
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Cor primária (ativa / sílaba atual — eLRC/TTML)",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            primaryPresets.forEach { color ->
                val selected = lyricsPrimaryColor == color
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.White.copy(0.3f), CircleShape)
                        .clickable { onPrimaryColorChange(color) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Cor secundária (inativa / sílabas restantes)",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            secondaryPresets.forEach { color ->
                val selected = lyricsSecondaryColor == color
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.White.copy(0.3f), CircleShape)
                        .clickable { onSecondaryColorChange(color) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Cor da borda",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0x88000000.toInt(), 0x00000000).forEach { color ->
                val selected = lyricsBorderColor == color
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (color == 0) Color.Transparent else Color(color))
                        .border(if (selected) 3.dp else 1.dp, Color.White.copy(0.5f), CircleShape)
                        .clickable { onBorderColorChange(color) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Grossura da borda: ${"%.1f".format(lyricsBorderWidth)} px",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("0", fontSize = 12.sp, color = Color.White.copy(0.5f))
            androidx.compose.material3.Slider(
                value = lyricsBorderWidth,
                onValueChange = onBorderWidthChange,
                valueRange = 0f..8f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text("8", fontSize = 12.sp, color = Color.White.copy(0.5f))
        }
        Text(
            "Suporte a sincronização silábica (eLRC e TTML).",
            fontSize = 12.sp,
            color = Color.White.copy(0.4f),
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
        )
    }
}


@Composable
internal fun FontOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color.White.copy(0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
        if (selected) {
            Text("✓", color = Color(0xFFBB86FC), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}


@Composable
internal fun RgbSpectrumPicker(
    selected: Int,
    onColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val hues = remember {
        List(36) { i ->
            android.graphics.Color.HSVToColor(floatArrayOf(i * 10f, 0.85f, 1f))
        }
    }
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(12.dp))
    ) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val frac = (offset.x / size.width).coerceIn(0f, 1f)
                        val hue = frac * 360f
                        val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.85f, 1f))
                        onColorChange(argb)
                    }
                    detectDragGestures { change, _ ->
                        change.consume()
                        val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                        val hue = frac * 360f
                        val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.85f, 1f))
                        onColorChange(argb)
                    }
                }
        ) {
            val step = size.width / hues.size
            hues.forEachIndexed { i, c ->
                drawRect(
                    color = Color(c),
                    topLeft = androidx.compose.ui.geometry.Offset(i * step, 0f),
                    size = androidx.compose.ui.geometry.Size(step + 1f, size.height)
                )
            }
            // marker
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(selected, hsv)
            val mx = (hsv[0] / 360f) * size.width
            drawCircle(
                color = Color.White,
                radius = 10f,
                center = androidx.compose.ui.geometry.Offset(mx, size.height / 2f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
            )
        }
    }
}
