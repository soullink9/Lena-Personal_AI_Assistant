package io.github.soullink9.lena

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Material You: colours come from your wallpaper. */
@Composable
fun LenaTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
        content = content,
    )
}

/** Open WebUI's own page background, so the top bar runs straight into the chat. */
@Composable
fun pageBackground(): Color = if (isSystemInDarkTheme()) Color(0xFF171717) else Color.White
