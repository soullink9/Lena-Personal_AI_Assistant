package io.github.soullink9.lena

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

/**
 * Material You: colours come from your wallpaper. Before Android 12 (Android TV boxes) there's no
 * wallpaper to take them from, so it's Material's own colours.
 */
@Composable
fun LenaTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Always dark on a TV (Tv.darkOnTv), even if a screen was opened another way.
    val tv = remember(context) { Tv.isTv(context) }
    val dark = isSystemInDarkTheme() || tv
    // A plain if around the Android 12 calls, the form the build's version check (lint) is sure to follow.
    val scheme = if (Compat.wallpaperColours) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/**
 * Open WebUI's page background, so the top bar runs straight into the chat: with Lena's look
 * (assets/css/open_webui.css, --lena-page; also "bg" in res/values and res/values-night colors.xml), or
 * Open WebUI's own.
 */
@Composable
fun pageBackground(lenaLook: Boolean): Color = when {
    lenaLook && isSystemInDarkTheme() -> Color(0xFF262624)
    lenaLook -> Color(0xFFFAF9F5)
    isSystemInDarkTheme() -> Color(0xFF171717)
    else -> Color.White
}

/** Lena's logo as a round avatar, as on the app icon. */
@Composable
fun LenaAvatar(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.lena_avatar),
        contentDescription = null,
        modifier = modifier.size(size).clip(CircleShape),
    )
}
