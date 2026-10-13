package com.jetpiggy.ginput.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetpiggy.ginput.R
import com.jetpiggy.ginput.engine.Candidate
import com.jetpiggy.ginput.engine.Scheme
import com.jetpiggy.ginput.keyboard.ClipboardContent
import com.jetpiggy.ginput.keyboard.KeyboardColors
import com.jetpiggy.ginput.keyboard.KeyboardScreen
import com.jetpiggy.ginput.keyboard.NavigationBarSpace
import com.jetpiggy.ginput.keyboard.colors
import com.jetpiggy.ginput.typing.FieldKind
import com.jetpiggy.ginput.typing.KeyboardState

private const val THEMES_PER_ROW = 3
private val SampleShape = RoundedCornerShape(12.dp)
private val PreviewShape = RoundedCornerShape(16.dp)
private val MiniKeyCorner = 3.dp
private val ChipShape = RoundedCornerShape(MiniKeyCorner)
private val SwatchShape = RoundedCornerShape(6.dp)
private val SwatchKeyCorner = 1.5.dp
private const val THEME_FADE_MILLIS = 200

/** 鍵盤預覽的候選列：打字打到一半，第一個是記憶字，看得到記憶字的顏色。 */
private val PreviewState = KeyboardState(
    composition = "nei",
    candidates = listOf("你", "妳", "尼", "膩", "餌", "伱").mapIndexed { index, text ->
        Candidate(text, Scheme.Jyutping, codeLength = 3, remembered = index == 0)
    },
)

/**
 * 鍵盤預覽：真正的鍵盤畫面，用現在的主題、鍵盤高度、按鍵標示和數字行，改了即時看到。
 * 只供觀看：上面蓋了一層接收觸控的空位，按鍵按不到；預覽跟着設定一起捲動時，在上面拖動仍然可以捲動。
 * TalkBack 只讀「鍵盤預覽」。
 */
@Composable
internal fun KeyboardPreview(keyboard: KeyboardSettings, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.keyboard_preview)
    val navigationBar = remember { NavigationBarSpace() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(PreviewShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PreviewShape)
                .clearAndSetSemantics { contentDescription = description },
        ) {
            Crossfade(keyboard.theme, animationSpec = tween(THEME_FADE_MILLIS), label = "theme") { theme ->
                KeyboardScreen(
                    state = PreviewState,
                    settings = keyboard.copy(theme = theme, keyPreview = false, keySound = false),
                    field = FieldKind.Text,
                    emojiCategories = emptyList(),
                    recentEmoji = emptyList(),
                    clipboard = ClipboardContent(),
                    onKey = {},
                    navigationBar = navigationBar,
                )
            }
            // 觸控只到這一層（不會傳給下面的按鍵），沒有用掉的拖動交給外面捲動。
            Spacer(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
            )
        }
        Text(
            text = stringResource(R.string.keyboard_preview_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 主題：每套主題一個小鍵盤樣本，按一下就套用，上面的鍵盤預覽即時換上新主題。樣本用系統目前的深色或淺色，和鍵盤一樣。
 */
@Composable
internal fun ThemePicker(selected: KeyboardTheme, onSelect: (KeyboardTheme) -> Unit) {
    val dark = isSystemInDarkTheme()
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RowText(stringResource(R.string.keyboard_theme), stringResource(R.string.keyboard_theme_summary))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in KeyboardTheme.entries.chunked(THEMES_PER_ROW)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (theme in row) {
                        ThemeOption(theme, theme.colors(dark), selected = theme == selected) { onSelect(theme) }
                    }
                    repeat(THEMES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.ThemeOption(theme: KeyboardTheme, colors: KeyboardColors, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(SampleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        MiniKeyboard(
            colors = colors,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = SampleShape,
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(
                text = themeName(theme),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** 小鍵盤樣本：候選列的記憶字和候選字，下面三行按鍵。Shift 用大寫鎖定的顏色，看得出主題的強調色。 */
@Composable
private fun MiniKeyboard(colors: KeyboardColors, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(SampleShape)
            .background(colors.background)
            .padding(6.dp)
            // 樣本只是圖案；TalkBack 讀主題名稱就夠。
            .clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "後",
                color = colors.remembered,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier
                    .clip(ChipShape)
                    .background(colors.rememberedChip)
                    .padding(horizontal = 3.dp),
            )
            Text("段", color = colors.label, fontSize = 12.sp, maxLines = 1)
            Text("般", color = colors.label, fontSize = 12.sp, maxLines = 1)
        }
        MiniRow {
            repeat(5) { MiniKey(colors.key, colors) }
        }
        MiniRow {
            MiniKey(colors.accent, colors, weight = 1.5f)
            repeat(3) { MiniKey(colors.key, colors) }
            MiniKey(colors.functionKey, colors, weight = 1.5f)
        }
        MiniRow {
            MiniKey(colors.functionKey, colors, weight = 1.5f)
            MiniKey(colors.key, colors, weight = 3f)
            MiniKey(colors.functionKey, colors, weight = 1.5f)
        }
    }
}

/** 設定主頁「鍵盤外觀」旁邊的小樣本：只有三行按鍵，看得出主題的顏色。只是圖案，TalkBack 不讀。 */
@Composable
internal fun ThemeSwatch(theme: KeyboardTheme) {
    val colors = theme.colors(isSystemInDarkTheme())
    Column(
        modifier = Modifier
            .width(48.dp)
            .clip(SwatchShape)
            .background(colors.background)
            .padding(4.dp)
            .clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        MiniRow(height = 6.dp, gap = 2.dp) {
            repeat(5) { MiniKey(colors.key, colors, corner = SwatchKeyCorner) }
        }
        MiniRow(height = 6.dp, gap = 2.dp) {
            MiniKey(colors.accent, colors, weight = 1.5f, corner = SwatchKeyCorner)
            repeat(3) { MiniKey(colors.key, colors, corner = SwatchKeyCorner) }
            MiniKey(colors.functionKey, colors, weight = 1.5f, corner = SwatchKeyCorner)
        }
        MiniRow(height = 6.dp, gap = 2.dp) {
            MiniKey(colors.functionKey, colors, weight = 1.5f, corner = SwatchKeyCorner)
            MiniKey(colors.key, colors, weight = 3f, corner = SwatchKeyCorner)
            MiniKey(colors.functionKey, colors, weight = 1.5f, corner = SwatchKeyCorner)
        }
    }
}

@Composable
private fun MiniRow(height: Dp = 12.dp, gap: Dp = 3.dp, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/** 一粒小按鍵，連底部 1dp 的實色陰影。 */
@Composable
private fun RowScope.MiniKey(color: Color, colors: KeyboardColors, weight: Float = 1f, corner: Dp = MiniKeyCorner) {
    Spacer(
        Modifier
            .weight(weight)
            .fillMaxHeight()
            .drawBehind {
                val radius = CornerRadius(corner.toPx())
                drawRoundRect(colors.keyShadow, topLeft = Offset(0f, 1.dp.toPx()), size = size, cornerRadius = radius)
                drawRoundRect(color, size = size, cornerRadius = radius)
            },
    )
}

@Composable
internal fun themeName(theme: KeyboardTheme): String = stringResource(
    when (theme) {
        KeyboardTheme.Default -> R.string.theme_default
        KeyboardTheme.MilkTea -> R.string.theme_milk_tea
        KeyboardTheme.Tram -> R.string.theme_tram
        KeyboardTheme.Harbour -> R.string.theme_harbour
        KeyboardTheme.Sakura -> R.string.theme_sakura
        KeyboardTheme.Neon -> R.string.theme_neon
    },
)
