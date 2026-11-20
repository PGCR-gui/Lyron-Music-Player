package com.tolete.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tolete.player.data.ImageProbe
import kotlinx.coroutines.launch

/** "1000×1000" */
internal fun resolutionLabel(size: Pair<Int, Int>): String = "${size.first}×${size.second}"

/** Total de pixels, para ordenar por resolução. */
internal fun resolutionPixels(size: Pair<Int, Int>?): Long =
    if (size == null) 0L else size.first.toLong() * size.second.toLong()

/**
 * Mede (em segundo plano) a resolução de cada URL. O mapa é observável: os valores aparecem
 * na tela conforme chegam.
 */
@Composable
internal fun rememberImageResolutions(urls: List<String>): Map<String, Pair<Int, Int>> {
    val sizes = remember { mutableStateMapOf<String, Pair<Int, Int>>() }
    LaunchedEffect(urls) {
        urls.filter { it.isNotBlank() && it !in sizes }.distinct().forEach { u ->
            launch { ImageProbe.size(u)?.let { sizes[u] = it } }
        }
    }
    return sizes
}

/**
 * Filtro por fonte: "Todas" + uma pílula por fonte (com contagem) e, opcionalmente, um botão
 * para ordenar pela maior resolução.
 */
@Composable
internal fun SourceFilterChips(
    sources: List<String>,
    counts: Map<String, Int>,
    selected: String?,
    onSelect: (String?) -> Unit,
    accent: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    sortByResolution: Boolean = false,
    onToggleSort: (() -> Unit)? = null
) {
    if (sources.size < 2 && onToggleSort == null) return
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (sources.size >= 2) {
            FilterPill("Todas · ${counts.values.sum()}", selected == null, accent, contentColor) { onSelect(null) }
            sources.forEach { s ->
                FilterPill("$s · ${counts[s] ?: 0}", selected == s, accent, contentColor) { onSelect(s) }
            }
        }
        if (onToggleSort != null) {
            FilterPill("Maior resolução", sortByResolution, accent, contentColor, onClick = onToggleSort)
        }
    }
}

@Composable
private fun FilterPill(
    text: String,
    selected: Boolean,
    accent: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.28f) else contentColor.copy(alpha = 0.08f))
            .border(1.dp, if (selected) accent else contentColor.copy(alpha = 0.18f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            fontSize = 12.sp,
            maxLines = 1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) contentColor else contentColor.copy(alpha = 0.75f)
        )
    }
}
