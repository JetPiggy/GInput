package com.jetpiggy.ginput.keyboard

import androidx.annotation.DrawableRes
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jetpiggy.ginput.R

/** 手機橫向版面左右的側欄最少這麼闊。 */
private val SideColumnWidth = 72.dp

/** 側欄按鈕的闊度，按下時的底色就是這麼闊。 */
private val SideButtonWidth = 52.dp

/** 側欄按鈕和螢幕開孔之間最少留的空位。 */
private val SideButtonMargin = 4.dp

/** 手機橫向版面收起了系統的按鈕列時，鍵盤底部留的位置：加上鍵盤的邊距，最底一行離螢幕底部約 10dp。 */
internal val LandscapeBottomGap = 6.dp

/** 側欄的闊度：有開孔的一邊加闊，按鈕避開開孔。 */
internal fun sideColumnWidth(cutout: Dp): Dp = maxOf(SideColumnWidth, cutout + SideButtonWidth + SideButtonMargin * 2)

/**
 * 鍵盤的三欄：中間是候選列和按鍵，最闊 middleMaxWidth；左右兩欄跟中間一樣高，最少 leftWidth 和 rightWidth。
 * 中間放不滿的話，餘下的位置平分給左右兩欄，中間置中。
 * 左右兩欄按下不算任何鍵：打字的按鍵把觸控範圍伸到整行（KeyRow），所以兩欄要自己接住觸控。
 */
@Composable
internal fun KeyboardColumns(
    leftWidth: Dp,
    rightWidth: Dp,
    middleMaxWidth: Dp,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
    middle: @Composable () -> Unit,
) {
    Layout(
        contents = listOf(
            { Box(Modifier.blockTouches()) { left() } },
            { Box(propagateMinConstraints = true) { middle() } },
            { Box(Modifier.blockTouches()) { right() } },
        ),
    ) { (leftMeasurables, middleMeasurables, rightMeasurables), constraints ->
        val total = constraints.maxWidth
        val sides = leftWidth.roundToPx() + rightWidth.roundToPx()
        val maxMiddle = if (middleMaxWidth == Dp.Infinity) Int.MAX_VALUE else middleMaxWidth.roundToPx()
        val middleWidth = (total - sides).coerceIn(0, maxMiddle)
        val leftActual = leftWidth.roundToPx() + (total - sides - middleWidth) / 2
        val rightActual = (total - leftActual - middleWidth).coerceAtLeast(0)
        val middle = middleMeasurables.single().measure(
            Constraints(minWidth = middleWidth, maxWidth = middleWidth, maxHeight = constraints.maxHeight),
        )
        val leftColumn = leftMeasurables.single().measure(Constraints.fixed(leftActual, middle.height))
        val rightColumn = rightMeasurables.single().measure(Constraints.fixed(rightActual, middle.height))
        layout(total, middle.height) {
            leftColumn.place(0, 0)
            middle.place(leftActual, 0)
            rightColumn.place(leftActual + middleWidth, 0)
        }
    }
}

/** 接住按在這裏的觸控，不交給附近的按鍵。 */
private fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitEachGesture { awaitFirstDown().consume() }
}

/**
 * 手機橫向版面的左側欄：左上角是工具鍵（和候選列同一行），左下角是切換輸入法和收起鍵盤（和最底兩行同一行）。
 * cutout 是開孔在左邊佔的位置。
 */
@Composable
internal fun LeftSideColumn(
    cutout: Dp,
    toolsKey: Boolean,
    toolsOpen: Boolean,
    settingsBadge: Boolean,
    switchInputMethod: Boolean,
    hideKeyboard: Boolean,
    colors: KeyboardColors,
    onToggleTools: () -> Unit,
    onKey: (KeyAction) -> Unit,
) {
    val dimensions = LocalKeyboardDimensions.current
    SideColumn(Modifier.padding(start = cutout)) {
        if (toolsKey) {
            SideButton(
                icon = R.drawable.ic_grid_view,
                description = stringResource(if (settingsBadge) R.string.side_tools_badge else R.string.side_tools),
                height = dimensions.barHeight,
                colors = colors,
                selected = toolsOpen,
                badge = settingsBadge,
                stateDescription = stringResource(if (toolsOpen) R.string.state_toolbar_open else R.string.state_toolbar_closed),
                onTap = onToggleTools,
            )
        }
        Spacer(Modifier.weight(1f))
        if (switchInputMethod) {
            SideButton(R.drawable.ic_keyboard, stringResource(R.string.key_switch_keyboard), dimensions.rowHeight, colors) {
                onKey(KeyAction.ShowInputMethodPicker)
            }
        }
        if (hideKeyboard) {
            SideButton(R.drawable.ic_keyboard_arrow_down, stringResource(R.string.side_hide_keyboard), dimensions.rowHeight, colors) {
                onKey(KeyAction.HideKeyboard)
            }
        }
    }
}

/** 手機橫向版面的右側欄：右下角是語音輸入。cutout 是開孔在右邊佔的位置。 */
@Composable
internal fun RightSideColumn(cutout: Dp, voiceInput: Boolean, colors: KeyboardColors, onVoiceInput: () -> Unit) {
    SideColumn(Modifier.padding(end = cutout)) {
        Spacer(Modifier.weight(1f))
        if (voiceInput) {
            SideButton(
                R.drawable.ic_mic,
                stringResource(R.string.toolbar_voice_input),
                LocalKeyboardDimensions.current.rowHeight,
                colors,
                onTap = onVoiceInput,
            )
        }
    }
}

/** 側欄的按鈕上下對齊候選列和按鍵的各行，所以上下的邊距和中間一樣。 */
@Composable
private fun SideColumn(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier)
            .padding(vertical = KeyboardPaddingY),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** 側欄的按鈕：只有圖示。selected 是工具鍵打開了工具列，顯示為已按下。 */
@Composable
private fun SideButton(
    @DrawableRes icon: Int,
    description: String,
    height: Dp,
    colors: KeyboardColors,
    selected: Boolean = false,
    badge: Boolean = false,
    stateDescription: String? = null,
    onTap: () -> Unit,
) {
    PressableBox(
        modifier = Modifier
            .size(SideButtonWidth, height)
            .padding(vertical = 2.dp),
        color = if (selected) colors.accentSoft else Color.Transparent,
        pressedColor = colors.pressedKey,
        description = description,
        stateDescription = stateDescription,
        onTap = onTap,
    ) {
        Box {
            KeyIcon(icon, if (selected) colors.onAccentSoft else colors.label)
            if (badge) IconBadge(colors)
        }
    }
}
