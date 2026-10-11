package com.jetpiggy.ginput.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JetPiggyTeal = Color(0xFF2EB8A6)
private val JetPiggyInk = Color(0xFF1C1C1C)

private val LightColors = lightColorScheme(
    primary = JetPiggyInk,
    onPrimary = Color.White,
    secondary = JetPiggyTeal,
    onSecondary = JetPiggyInk,
)

private val DarkColors = darkColorScheme(
    primary = JetPiggyTeal,
    onPrimary = JetPiggyInk,
    secondary = JetPiggyTeal,
    onSecondary = JetPiggyInk,
)

@Composable
fun GInputTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
