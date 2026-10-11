package com.jetpiggy.ginput.keyboard

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.jetpiggy.ginput.settings.KeyboardTheme

@Immutable
data class KeyboardColors(
    /** 鍵盤和候選列的底色。 */
    val background: Color,
    /** 字母鍵、數字鍵、空白鍵和「，」「。」。 */
    val key: Color,
    /** Shift、Delete、Emoji、123、Enter；也用作首選字的底色。 */
    val functionKey: Color,
    /** 按下時的底色：所有按鍵、候選字和工具列。 */
    val pressedKey: Color,
    /** 主標示、按鍵圖示和候選字。 */
    val label: Color,
    /** 角落小字、工具列和候選列最右的按鈕。 */
    val secondaryLabel: Color,
    /** 記憶字的綠色。 */
    val remembered: Color,
    /** 記憶字的淺綠底（深色主題是深綠底），候選列和候選字格都用。 */
    val rememberedChip: Color,
    /** 「空白鍵選中文」模式下，首選字剛好是記憶字時的底色；和首選字的灰底一樣比一般的底色突出一級。 */
    val rememberedChipMarked: Color,
    /** 按鍵底部 1dp 的實色陰影。 */
    val keyShadow: Color,
    /** 候選列的分隔線。 */
    val divider: Color,
    /** 大寫鎖定時 Shift 鍵的底色和圖示。 */
    val accent: Color,
    val onAccent: Color,
    /** 單次大寫時 Shift 鍵的底色和圖示。 */
    val accentSoft: Color,
    val onAccentSoft: Color,
    /** 按鍵放大預覽的底色；深色時比按鍵亮一級。 */
    val preview: Color,
)

/** 一套主題的淺色和深色兩組顏色。 */
private class ThemePalette(val light: KeyboardColors, val dark: KeyboardColors)

// 各主題的顏色按 Claude Design 的「GInput 鍵盤設計稿」（GInput Keyboard Themes 頁，2026-10-11）。
// 「預設」是原來的冷灰；只有淺色的記憶字比設計稿的 #2E7D32 深，首選字有底色時也看得清楚。
// 記憶字的底色（rememberedChip、rememberedChipMarked）不在設計稿裏：用各主題記憶字的色相計算，
// 綠字對它至少 4.5:1，和候選列的底色也有明顯分別。
// 改了顏色要跑 KeyboardColorsTest，確認對比仍然合格。

private val DefaultPalette = ThemePalette(
    light = KeyboardColors(
        background = Color(0xFFE6E8EC),
        key = Color(0xFFFFFFFF),
        functionKey = Color(0xFFC8CDD4),
        pressedKey = Color(0xFFB4BAC3),
        label = Color(0xFF1B1B1F),
        secondaryLabel = Color(0xFF5E636B),
        remembered = Color(0xFF1B5E20),
        rememberedChip = Color(0xFFCFECCB),
        rememberedChipMarked = Color(0xFFB6D5B2),
        keyShadow = Color(0xFFABB1BA),
        divider = Color(0xFFBCC1C9),
        accent = Color(0xFF2F62D1),
        onAccent = Color(0xFFFFFFFF),
        accentSoft = Color(0xFFD4E0FA),
        onAccentSoft = Color(0xFF1D4CB3),
        preview = Color(0xFFFFFFFF),
    ),
    dark = KeyboardColors(
        background = Color(0xFF17181B),
        key = Color(0xFF3A3B40),
        functionKey = Color(0xFF2A2B30),
        pressedKey = Color(0xFF55575D),
        label = Color(0xFFE6E6EA),
        secondaryLabel = Color(0xFFA8ACB3),
        remembered = Color(0xFF81C784),
        rememberedChip = Color(0xFF1D371F),
        rememberedChipMarked = Color(0xFF2A4A2C),
        keyShadow = Color(0xFF0C0D0F),
        divider = Color(0xFF3F4147),
        accent = Color(0xFF8FB2FF),
        onAccent = Color(0xFF0D1B3D),
        accentSoft = Color(0xFF2B3A5C),
        onAccentSoft = Color(0xFFB5CBFF),
        preview = Color(0xFF4A4B51),
    ),
)

private val MilkTeaPalette = ThemePalette(
    light = KeyboardColors(
        background = Color(0xFFE8DED1),
        key = Color(0xFFFCF8F2),
        functionKey = Color(0xFFD3C2AE),
        pressedKey = Color(0xFFB9A48D),
        label = Color(0xFF2A1F17),
        secondaryLabel = Color(0xFF6B5A4B),
        remembered = Color(0xFF215C28),
        rememberedChip = Color(0xFFC4E4C2),
        rememberedChipMarked = Color(0xFFACCDAB),
        keyShadow = Color(0xFFC2B19C),
        divider = Color(0xFFCDBDA9),
        accent = Color(0xFF7A4626),
        onAccent = Color(0xFFFFF6EA),
        accentSoft = Color(0xFFF2C2A2),
        onAccentSoft = Color(0xFF6B3414),
        preview = Color(0xFFFCF8F2),
    ),
    dark = KeyboardColors(
        background = Color(0xFF1B1511),
        key = Color(0xFF3D3229),
        functionKey = Color(0xFF29211B),
        pressedKey = Color(0xFF5A4A3E),
        label = Color(0xFFF1E7DB),
        secondaryLabel = Color(0xFFB9A999),
        remembered = Color(0xFF8FCB8A),
        rememberedChip = Color(0xFF1E361D),
        rememberedChipMarked = Color(0xFF2B492A),
        keyShadow = Color(0xFF0B0806),
        divider = Color(0xFF45392F),
        accent = Color(0xFFEBD5B5),
        onAccent = Color(0xFF3A2414),
        accentSoft = Color(0xFF5A3A2A),
        onAccentSoft = Color(0xFFF2C4A2),
        preview = Color(0xFF4D4035),
    ),
)

private val TramPalette = ThemePalette(
    light = KeyboardColors(
        background = Color(0xFFE6E2CF),
        key = Color(0xFFFCFAF1),
        functionKey = Color(0xFFCCC6AC),
        pressedKey = Color(0xFFB1AA8D),
        label = Color(0xFF16221B),
        secondaryLabel = Color(0xFF5A6157),
        remembered = Color(0xFF3D5903),
        rememberedChip = Color(0xFFD5E4B0),
        rememberedChipMarked = Color(0xFFBCCD93),
        keyShadow = Color(0xFFBAB49A),
        divider = Color(0xFFC6C0A6),
        accent = Color(0xFF1D5A3B),
        onAccent = Color(0xFFFBF6E3),
        accentSoft = Color(0xFFC8E3CE),
        onAccentSoft = Color(0xFF16492D),
        preview = Color(0xFFFCFAF1),
    ),
    dark = KeyboardColors(
        background = Color(0xFF0F1713),
        key = Color(0xFF27342D),
        functionKey = Color(0xFF18211C),
        pressedKey = Color(0xFF405048),
        label = Color(0xFFEEEAD9),
        secondaryLabel = Color(0xFFAAB4A9),
        remembered = Color(0xFFB0D45F),
        rememberedChip = Color(0xFF2B3416),
        rememberedChipMarked = Color(0xFF3B4621),
        keyShadow = Color(0xFF050806),
        divider = Color(0xFF2F3C35),
        accent = Color(0xFF86CFA2),
        onAccent = Color(0xFF0E2A1B),
        accentSoft = Color(0xFF1F4231),
        onAccentSoft = Color(0xFFA9DFBB),
        preview = Color(0xFF35443C),
    ),
)

private val HarbourPalette = ThemePalette(
    light = KeyboardColors(
        background = Color(0xFFD5E2F0),
        key = Color(0xFFF6FAFF),
        functionKey = Color(0xFFAFC4DB),
        pressedKey = Color(0xFF93A9C2),
        label = Color(0xFF0F1B2A),
        secondaryLabel = Color(0xFF4A5A6C),
        remembered = Color(0xFF105A2C),
        rememberedChip = Color(0xFFC2E5C8),
        rememberedChipMarked = Color(0xFFA5CBAC),
        keyShadow = Color(0xFFA0B4CA),
        divider = Color(0xFFAABED4),
        accent = Color(0xFFA2690E),
        onAccent = Color(0xFFFFFFFF),
        accentSoft = Color(0xFFF5DDB0),
        onAccentSoft = Color(0xFF6A4100),
        preview = Color(0xFFF6FAFF),
    ),
    dark = KeyboardColors(
        background = Color(0xFF0B1220),
        key = Color(0xFF22304A),
        functionKey = Color(0xFF131D30),
        pressedKey = Color(0xFF36476A),
        label = Color(0xFFE3EAF5),
        secondaryLabel = Color(0xFFA2B0C6),
        remembered = Color(0xFF82C98E),
        rememberedChip = Color(0xFF1A3620),
        rememberedChipMarked = Color(0xFF264A2D),
        keyShadow = Color(0xFF03060C),
        divider = Color(0xFF26334A),
        accent = Color(0xFFF2B85B),
        onAccent = Color(0xFF231600),
        accentSoft = Color(0xFF41371F),
        onAccentSoft = Color(0xFFF3CF8A),
        preview = Color(0xFF2E3D5A),
    ),
)

private val SakuraPalette = ThemePalette(
    light = KeyboardColors(
        background = Color(0xFFEFE2E4),
        key = Color(0xFFFFF9FA),
        functionKey = Color(0xFFD5C6C8),
        pressedKey = Color(0xFFBDABAE),
        label = Color(0xFF2B1A1F),
        secondaryLabel = Color(0xFF6E5960),
        remembered = Color(0xFF1E6131),
        rememberedChip = Color(0xFFC7EACB),
        rememberedChipMarked = Color(0xFFAED3B2),
        keyShadow = Color(0xFFCDB8BC),
        divider = Color(0xFFD7C3C7),
        accent = Color(0xFFB0456A),
        onAccent = Color(0xFFFFFFFF),
        accentSoft = Color(0xFFF9C2D4),
        onAccentSoft = Color(0xFF7A2445),
        preview = Color(0xFFFFF9FA),
    ),
    dark = KeyboardColors(
        background = Color(0xFF1B1416),
        key = Color(0xFF3C2F34),
        functionKey = Color(0xFF2A2125),
        pressedKey = Color(0xFF59474E),
        label = Color(0xFFF4E7EB),
        secondaryLabel = Color(0xFFC0AAB1),
        remembered = Color(0xFF8FCB95),
        rememberedChip = Color(0xFF1B361F),
        rememberedChipMarked = Color(0xFF284A2C),
        keyShadow = Color(0xFF0B0607),
        divider = Color(0xFF45363C),
        accent = Color(0xFFF2A9C0),
        onAccent = Color(0xFF3A0F1F),
        accentSoft = Color(0xFF58303F),
        onAccentSoft = Color(0xFFF6C6D5),
        preview = Color(0xFF4C3C42),
    ),
)

private val NeonPalette = ThemePalette(
    light = KeyboardColors(
        background = Color(0xFFE3DCEB),
        key = Color(0xFFFCFAFF),
        functionKey = Color(0xFFCBC0DC),
        pressedKey = Color(0xFFAC9EC3),
        label = Color(0xFF17121F),
        secondaryLabel = Color(0xFF594F69),
        remembered = Color(0xFF0F5B30),
        rememberedChip = Color(0xFFBFE4C7),
        rememberedChipMarked = Color(0xFFA6CEB0),
        keyShadow = Color(0xFFB5A9C7),
        divider = Color(0xFFC0B4D2),
        accent = Color(0xFFC0157A),
        onAccent = Color(0xFFFFFFFF),
        accentSoft = Color(0xFFC3ECEF),
        onAccentSoft = Color(0xFF005A63),
        preview = Color(0xFFFCFAFF),
    ),
    dark = KeyboardColors(
        background = Color(0xFF000000),
        key = Color(0xFF26222F),
        functionKey = Color(0xFF110F15),
        pressedKey = Color(0xFF3F394B),
        label = Color(0xFFF2EEF8),
        secondaryLabel = Color(0xFFACA6B9),
        remembered = Color(0xFF7CDE9E),
        rememberedChip = Color(0xFF163722),
        rememberedChipMarked = Color(0xFF214A30),
        keyShadow = Color(0xFF0D0B11),
        divider = Color(0xFF2A2632),
        accent = Color(0xFFFF4FB0),
        onAccent = Color(0xFF2A0019),
        accentSoft = Color(0xFF003B41),
        onAccentSoft = Color(0xFF5CE6EE),
        preview = Color(0xFF322D3D),
    ),
)

/** theme 的淺色或深色那組顏色。 */
internal fun KeyboardTheme.colors(dark: Boolean): KeyboardColors {
    val palette = when (this) {
        KeyboardTheme.Default -> DefaultPalette
        KeyboardTheme.MilkTea -> MilkTeaPalette
        KeyboardTheme.Tram -> TramPalette
        KeyboardTheme.Harbour -> HarbourPalette
        KeyboardTheme.Sakura -> SakuraPalette
        KeyboardTheme.Neon -> NeonPalette
    }
    return if (dark) palette.dark else palette.light
}

/** 主題的顏色；淺色或深色跟隨系統。 */
@Composable
fun keyboardColors(theme: KeyboardTheme): KeyboardColors = theme.colors(isSystemInDarkTheme())
