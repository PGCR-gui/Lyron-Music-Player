package com.tolete.player.ui

import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tolete.player.R
import kotlin.random.Random
import kotlinx.coroutines.delay

private val SplashTextColors = listOf(
    Color(0xFFFFFFFF),
    Color(0xFFBB86FC),
    Color(0xFF00D2FF),
    Color(0xFFFF6B4A),
    Color(0xFF4ADE80),
    Color(0xFF60A5FA),
    Color(0xFFF472B6),
    Color(0xFFFBBF24),
    Color(0xFFFF8A80),
    Color(0xFF80CBC4),
    Color(0xFFFFD54F),
    Color(0xFFCE93D8),
)

/**
 * Splash ~5s com som opcional.
 * Ícone e texto ocultos no início; revelação aos 2,10s até o fim.
 * Cor e fonte do título são escolhidas aleatoriamente a cada abertura.
 * Toque na tela pula a animação.
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    playSound: Boolean = true
) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.72f) }
    val context = LocalContext.current
    var finished by remember { mutableStateOf(false) }

    fun finishSplash() {
        if (finished) return
        finished = true
        onFinished()
    }

    // Rotação entre fontes carregadas pelo usuário (filesDir/fonts); fallback sistema
    val splashFont = remember {
        val pool = BuiltInFonts.splashPool(context)
        if (pool.isNotEmpty()) {
            val chosen = pool[Random.nextInt(pool.size)]
            try {
                FontFamily(Font(chosen))
            } catch (_: Exception) {
                null
            }
        } else null
    }
    val splashColor = remember {
        SplashTextColors[Random.nextInt(SplashTextColors.size)]
    }

    DisposableEffect(playSound) {
        var player: MediaPlayer? = null
        if (playSound) {
            try {
                player = MediaPlayer.create(context, R.raw.splash_sound)?.apply {
                    setVolume(0.85f, 0.85f)
                    start()
                }
            } catch (_: Exception) {}
        }
        onDispose {
            try {
                player?.stop()
                player?.release()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        delay(2100)
        if (finished) return@LaunchedEffect
        alpha.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        if (finished) return@LaunchedEffect
        scale.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        if (finished) return@LaunchedEffect
        delay(2200)
        if (finished) return@LaunchedEffect
        alpha.animateTo(0f, tween(350))
        finishSplash()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { finishSplash() },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.splash_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .alpha(alpha.value)
                .scale(scale.value)
                .padding(horizontal = 32.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.app_icon_splash),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(28.dp))
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = "Lyron Music Player",
                color = splashColor,
                fontSize = 30.sp,
                fontFamily = splashFont,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }
    }
}
