package com.jetpiggy.ginput.keyboard

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.jetpiggy.ginput.R
import com.jetpiggy.ginput.typing.FieldKind

/** 數字、電話和日期時間欄位用的數字鍵盤。右邊一行和最底一行的符號按欄位類型而定。 */
@Composable
internal fun NumberPad(field: FieldKind, colors: KeyboardColors, onKey: (KeyAction) -> Unit) {
    val extras = when (field) {
        FieldKind.Phone -> listOf("+", "-", "*", "#")
        FieldKind.DateTime -> listOf("/", ":", "-", " ")
        else -> listOf("-", ".", ",", "+")
    }
    KeyRow {
        PadKey("1", colors, onKey)
        PadKey("2", colors, onKey)
        PadKey("3", colors, onKey)
        DeleteKey(colors, weight = 1f, onKey = onKey)
    }
    KeyRow {
        PadKey("4", colors, onKey)
        PadKey("5", colors, onKey)
        PadKey("6", colors, onKey)
        PadKey(extras[0], colors, onKey, style = KeyStyle.Function)
    }
    KeyRow {
        PadKey("7", colors, onKey)
        PadKey("8", colors, onKey)
        PadKey("9", colors, onKey)
        PadKey(extras[1], colors, onKey, style = KeyStyle.Function)
    }
    KeyRow {
        PadKey(extras[2], colors, onKey, style = KeyStyle.Function)
        PadKey("0", colors, onKey)
        PadKey(extras[3], colors, onKey, style = KeyStyle.Function)
        Key(
            colors,
            weight = 1f,
            description = stringResource(R.string.key_enter),
            style = KeyStyle.Function,
            sound = KeySound.Enter,
            onTap = { onKey(KeyAction.Enter) },
        ) {
            KeyIcon(R.drawable.ic_keyboard_return, colors.label)
        }
    }
}

@Composable
private fun RowScope.PadKey(
    text: String,
    colors: KeyboardColors,
    onKey: (KeyAction) -> Unit,
    style: KeyStyle = KeyStyle.Character,
) {
    Key(
        colors,
        weight = 1f,
        description = if (text == " ") stringResource(R.string.key_space) else text,
        style = style,
        onTap = { onKey(KeyAction.Text(text)) },
    ) {
        KeyLabel(if (text == " ") "␣" else text, colors, fontSize = 24.sp)
    }
}
