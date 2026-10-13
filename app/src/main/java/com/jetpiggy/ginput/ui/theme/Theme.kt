package com.jetpiggy.ginput.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 品牌青綠和墨黑：App 圖示的底色和設定頁的主題色。 */
internal val JetPiggyTeal = Color(0xFF2EB8A6)
internal val JetPiggyInk = Color(0xFF1C1C1C)

// secondaryContainer 是品牌青綠調淺（深色時調暗），設定主頁分類圖示的圓形底色。
private val LightColors = lightColorScheme(
    primary = JetPiggyInk,
    onPrimary = Color.White,
    secondary = JetPiggyTeal,
    onSecondary = JetPiggyInk,
    secondaryContainer = Color(0xFFD2EFEB),
    onSecondaryContainer = Color(0xFF0B5C52),
)

private val DarkColors = darkColorScheme(
    primary = JetPiggyTeal,
    onPrimary = JetPiggyInk,
    secondary = JetPiggyTeal,
    onSecondary = JetPiggyInk,
    secondaryContainer = Color(0xFF1E4A44),
    onSecondaryContainer = Color(0xFFA6E8DE),
)

@Composable
fun GInputTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
