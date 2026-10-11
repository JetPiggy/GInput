package com.jetpiggy.ginput.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetpiggy.ginput.R
import com.jetpiggy.ginput.keyboard.KeyboardColors
import com.jetpiggy.ginput.keyboard.colors

private const val THEMES_PER_ROW = 3
private val SampleShape = RoundedCornerShape(12.dp)
private val MiniKeyCorner = 3.dp
private val ChipShape = RoundedCornerShape(MiniKeyCorner)

/**
 * 主題：每套主題一個小鍵盤樣本，按一下就套用。樣本用系統目前的深色或淺色，和鍵盤一樣；
 * 用試打欄升起了鍵盤的話，鍵盤即時換上新主題。
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

@Composable
private fun MiniRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        content = content,
    )
}

/** 一粒小按鍵，連底部 1dp 的實色陰影。 */
@Composable
private fun RowScope.MiniKey(color: Color, colors: KeyboardColors, weight: Float = 1f) {
    Spacer(
        Modifier
            .weight(weight)
            .fillMaxHeight()
            .drawBehind {
                val corner = CornerRadius(MiniKeyCorner.toPx())
                drawRoundRect(colors.keyShadow, topLeft = Offset(0f, 1.dp.toPx()), size = size, cornerRadius = corner)
                drawRoundRect(color, size = size, cornerRadius = corner)
            },
    )
}

@Composable
private fun themeName(theme: KeyboardTheme): String = stringResource(
    when (theme) {
        KeyboardTheme.Default -> R.string.theme_default
        KeyboardTheme.MilkTea -> R.string.theme_milk_tea
        KeyboardTheme.Tram -> R.string.theme_tram
        KeyboardTheme.Harbour -> R.string.theme_harbour
        KeyboardTheme.Sakura -> R.string.theme_sakura
        KeyboardTheme.Neon -> R.string.theme_neon
    },
)
