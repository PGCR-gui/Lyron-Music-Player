package com.tolete.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class NavItem(
    val tab: MusicViewModel.Tab,
    val icon: ImageVector,
    val label: String
)

private val navItems = listOf(
    NavItem(MusicViewModel.Tab.HOME, Icons.Rounded.Home, "Início"),
    NavItem(MusicViewModel.Tab.ALL, Icons.Rounded.LibraryMusic, "Todas"),
    NavItem(MusicViewModel.Tab.ARTISTS, Icons.Rounded.Person, "Artistas"),
    NavItem(MusicViewModel.Tab.ALBUMS, Icons.Rounded.Album, "Álbuns"),
    NavItem(MusicViewModel.Tab.GENRES, Icons.Rounded.Category, "Gêneros")
)

@Composable
fun LiquidGlassNavigation(
    currentTab: MusicViewModel.Tab,
    onTabSelected: (MusicViewModel.Tab) -> Unit,
    backdrop: GraphicsLayer,
    modifier: Modifier = Modifier
) {
    val selectedIndex = navItems.indexOfFirst { it.tab == currentTab }.coerceAtLeast(0)

    GlassSurface(
        backdrop = backdrop,
        shape = RoundedCornerShape(32.dp),
        modifier = modifier.fillMaxWidth().height(64.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val itemWidth = maxWidth / navItems.size
            val indicatorWidth = itemWidth - 8.dp
            val targetOffset = itemWidth * selectedIndex + 4.dp

            val animatedOffset by animateDpAsState(
                targetValue = targetOffset,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "indicatorOffset"
            )

            // Indicador deslizável de vidro (liquid glass pill)
            Box(
                modifier = Modifier
                    .offset(x = animatedOffset)
                    .width(indicatorWidth)
                    .fillMaxHeight()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.32f),
                                Color.White.copy(alpha = 0.14f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.10f)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
            )

            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    val tint by animateColorAsState(
                        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.55f),
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "tint"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onTabSelected(item.tab) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = tint,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            color = tint,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
