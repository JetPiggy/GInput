package com.jetpiggy.ginput.keyboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.jetpiggy.ginput.settings.KeyboardTheme
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 每套主題的淺色和深色都要看得清楚：按 WCAG 2.x 計算對比，和設計稿的「對比」表用同一算法。
 * 漸變主題逐個色位計算，半透明的顏色先疊在色位上；高對比主題的文字和 Shift 圖示要 7:1。
 */
class KeyboardColorsTest {

    /** background 是 null 的話，對的是鍵盤的底色（漸變主題的每個色位）。highContrast 是高對比主題的要求。 */
    private class Check(
        val name: String,
        val minimum: Double,
        val foreground: (KeyboardColors) -> Color,
        val background: ((KeyboardColors) -> Color)?,
        val highContrast: Double = 7.0,
    )

    private val checks = listOf(
        // 文字至少 4.5:1。
        Check("label 對 key", 4.5, { it.label }, { it.key }),
        Check("label 對 functionKey", 4.5, { it.label }, { it.functionKey }),
        Check("label 對 pressedKey", 4.5, { it.label }, { it.pressedKey }),
        Check("label 對 preview", 4.5, { it.label }, { it.preview }),
        Check("label 對 background", 4.5, { it.label }, null),
        Check("secondaryLabel 對 key", 4.5, { it.secondaryLabel }, { it.key }),
        Check("secondaryLabel 對 background", 4.5, { it.secondaryLabel }, null),
        // 記憶字在候選列和候選字格都有淺綠底；「空白鍵選中文」模式的首選字用突出一級的底。
        Check("remembered 對 rememberedChip", 4.5, { it.remembered }, { it.rememberedChip }),
        Check("remembered 對 rememberedChipMarked", 4.5, { it.remembered }, { it.rememberedChipMarked }),
        // Shift 的圖示和大寫鎖定的 Shift 本身至少 3:1。
        Check("onAccent 對 accent", 3.0, { it.onAccent }, { it.accent }),
        Check("onAccentSoft 對 accentSoft", 3.0, { it.onAccentSoft }, { it.accentSoft }),
        Check("accent 對 background", 3.0, { it.accent }, null, highContrast = 3.0),
    )

    @Test
    fun everyThemeIsReadable() {
        val failures = buildList {
            for (theme in KeyboardTheme.entries) {
                for (dark in listOf(false, true)) {
                    val colors = theme.colors(dark)
                    for (check in checks) {
                        val ratio = (colors.gradient?.colors ?: listOf(colors.background)).minOf { stop ->
                            val back = check.background?.invoke(colors)?.over(stop) ?: stop
                            contrast(check.foreground(colors).over(back), back)
                        }
                        val minimum = if (theme == KeyboardTheme.HighContrast) check.highContrast else check.minimum
                        if (ratio < minimum) {
                            add("$theme ${if (dark) "深色" else "淺色"}：${check.name} %.2f".format(ratio))
                        }
                    }
                }
            }
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun gradientBackgroundStartsWithItsFirstColor() {
        for (theme in KeyboardTheme.entries) {
            for (dark in listOf(false, true)) {
                val colors = theme.colors(dark)
                val gradient = colors.gradient ?: continue
                assertEquals("$theme", gradient.colors.first(), colors.background)
            }
        }
    }

    @Test
    fun navigationIconsAreDarkOnLightKeyboards() {
        for (theme in KeyboardTheme.entries) {
            for (dark in listOf(false, true)) {
                val colors = theme.colors(dark)
                val bottom = colors.gradient?.colors?.last() ?: colors.background
                // 揀對比較高的一種；淺色主題的鍵盤全部用深色圖示，深色主題全部用白色。
                val blackWins = contrast(Color.Black, bottom) > contrast(Color.White, bottom)
                assertEquals("$theme dark=$dark", blackWins, colors.darkNavigationIcons)
                assertEquals("$theme dark=$dark", !dark, colors.darkNavigationIcons)
            }
        }
    }

    @Test
    fun gradientRunsLikeCss() {
        // 180° 由上到下；135° 由左上角到右下角；90° 由左到右。
        assertEquals(Offset(50f, 0f) to Offset(50f, 200f), KeyboardGradient(180f, emptyList()).line(Size(100f, 200f)).rounded())
        assertEquals(Offset(0f, 0f) to Offset(100f, 100f), KeyboardGradient(135f, emptyList()).line(Size(100f, 100f)).rounded())
        assertEquals(Offset(0f, 50f) to Offset(300f, 50f), KeyboardGradient(90f, emptyList()).line(Size(300f, 100f)).rounded())
    }

    private fun Pair<Offset, Offset>.rounded(): Pair<Offset, Offset> {
        fun Offset.round() = Offset(x.roundToInt().toFloat(), y.roundToInt().toFloat())
        return first.round() to second.round()
    }

    @Test
    fun contrastMatchesWcagExamples() {
        assertEquals(21.0, contrast(Color.Black, Color.White), 0.01)
        assertEquals(1.0, contrast(Color(0xFF777777), Color(0xFF777777)), 0.01)
        // 和設計稿算出的值相同：設計稿「預設」淺色的記憶字 #2E7D32 對底色 4.18、對功能鍵 3.21（所以 App 改用較深的綠）。
        assertEquals(4.18, contrast(Color(0xFF2E7D32), Color(0xFFE6E8EC)), 0.01)
        assertEquals(3.21, contrast(Color(0xFF2E7D32), Color(0xFFC8CDD4)), 0.01)
    }

    /** 疊在 base 上看到的顏色，用 8 位元計算，和設計稿一樣。 */
    private fun Color.over(base: Color): Color {
        if (alpha >= 1f) return this
        fun mix(top: Float, bottom: Float) = (top * 255 * alpha + bottom * 255 * (1 - alpha)).roundToInt() / 255f
        return Color(mix(red, base.red), mix(green, base.green), mix(blue, base.blue))
    }

    private fun contrast(a: Color, b: Color): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }

    /** sRGB 的相對亮度，用 8 位元的色值計算，和設計稿一樣。 */
    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = (value * 255).roundToInt() / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }
}
