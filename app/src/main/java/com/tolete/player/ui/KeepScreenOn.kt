package com.tolete.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/** Conta quantas telas pedem a tela ligada ao mesmo tempo (Now Playing + letra em tela cheia). */
private object ScreenOnCounter { var count = 0 }

/**
 * Mantém a tela acesa enquanto este composable estiver na composição.
 * Usa FLAG_KEEP_SCREEN_ON (via View.keepScreenOn), que é o equivalente moderno e
 * recomendado de um wake lock de tela: não precisa de permissão, é liberado sozinho
 * quando a tela sai da composição ou o app vai para segundo plano.
 */
@Composable
internal fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        ScreenOnCounter.count++
        view.keepScreenOn = true
        onDispose {
            ScreenOnCounter.count = (ScreenOnCounter.count - 1).coerceAtLeast(0)
            if (ScreenOnCounter.count == 0) view.keepScreenOn = false
        }
    }
}
