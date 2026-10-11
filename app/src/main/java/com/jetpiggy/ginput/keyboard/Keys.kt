package com.jetpiggy.ginput.keyboard

import android.media.AudioManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.PressGestureScope
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetpiggy.ginput.R
import com.jetpiggy.ginput.settings.KeyLabels
import kotlin.math.roundToInt

@Immutable
internal data class KeyboardDimensions(
    val rowHeight: Dp,
    /** 數字行比其他行矮。 */
    val numberRowHeight: Dp,
    val barHeight: Dp,
    /** 字母鍵的大字：英文字母（字根小）、倉頡字根（英文小）和只有英文字母時。字根的字形較大，所以小一點。 */
    val letterSize: TextUnit,
    val radicalSize: TextUnit,
    val lettersOnlySize: TextUnit,
    /** 數字行的數字。 */
    val numberRowSize: TextUnit,
    /** 字母鍵角落的小字：右上角的字根或字母，以及左上角長按數字的提示。 */
    val cornerSize: TextUnit,
    val digitHintSize: TextUnit,
    val candidateSize: TextUnit,
    /** 候選列的英文建議。 */
    val englishSize: TextUnit,
    /** 按鍵放大預覽的最小闊度、高度和字級。 */
    val previewWidth: Dp,
    val previewHeight: Dp,
    val previewSize: TextUnit,
)

internal val PortraitDimensions = KeyboardDimensions(
    rowHeight = 54.dp,
    numberRowHeight = 46.dp,
    barHeight = 44.dp,
    letterSize = 21.sp,
    radicalSize = 20.sp,
    lettersOnlySize = 22.sp,
    numberRowSize = 18.sp,
    cornerSize = 11.sp,
    digitHintSize = 10.sp,
    candidateSize = 21.sp,
    englishSize = 17.sp,
    previewWidth = 46.dp,
    previewHeight = 52.dp,
    previewSize = 30.sp,
)

/** 橫向時螢幕矮，按鍵也矮一點，鍵盤大約佔螢幕一半。 */
internal val LandscapeDimensions = KeyboardDimensions(
    rowHeight = 38.dp,
    numberRowHeight = 32.dp,
    barHeight = 34.dp,
    letterSize = 17.sp,
    radicalSize = 16.sp,
    lettersOnlySize = 18.sp,
    numberRowSize = 15.sp,
    cornerSize = 11.sp,
    digitHintSize = 10.sp,
    candidateSize = 18.sp,
    englishSize = 15.sp,
    previewWidth = 46.dp,
    previewHeight = 40.dp,
    previewSize = 26.sp,
)

internal val LocalKeyboardDimensions = staticCompositionLocalOf { PortraitDimensions }

/** 鍵面四周的空位：左右各 3dp（鍵與鍵相隔 6dp），上下各 5dp。 */
internal val KeyPaddingX = 3.dp
internal val KeyPaddingY = 5.dp

/**
 * 按鍵盤高度的設定（scale）調整按鍵的高度。矮鍵盤的鍵面較矮，鍵面上的字也按鍵面高度同比例縮小，
 * 角落的小字和下面的大字才不會碰到；標準和高鍵盤的字保持設計稿的大小。候選列不受影響。
 */
internal fun KeyboardDimensions.withHeight(scale: Float): KeyboardDimensions {
    val scaledRow = rowHeight * scale
    val text = ((scaledRow - KeyPaddingY * 2) / (rowHeight - KeyPaddingY * 2)).coerceAtMost(1f)
    return copy(
        rowHeight = scaledRow,
        numberRowHeight = numberRowHeight * scale,
        letterSize = letterSize * text,
        radicalSize = radicalSize * text,
        lettersOnlySize = lettersOnlySize * text,
        numberRowSize = numberRowSize * text,
        cornerSize = cornerSize * text,
        digitHintSize = digitHintSize * text,
    )
}

private val KeyCorner = 6.dp

/** 鍵面的形狀；候選字和工具列按下時的底色也用這個圓角。 */
internal val KeyShape = RoundedCornerShape(KeyCorner)

private val PreviewShape = RoundedCornerShape(8.dp)

/** 按鍵底部 1dp 的實色陰影，不模糊。要放在 clip 之前，否則會被切掉。 */
internal fun Modifier.keyShadow(color: Color): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        topLeft = Offset(0f, 1.dp.toPx()),
        size = size,
        cornerRadius = CornerRadius(KeyCorner.toPx()),
    )
}

/** 按鍵的底色：字元鍵、功能鍵，以及 Shift 開了時的兩種藍色（Accent 是大寫鎖定，AccentSoft 是單次大寫）。 */
internal enum class KeyStyle { Character, Function, Accent, AccentSoft }

/** 按鍵音的種類，對應系統的按鍵音效。 */
internal enum class KeySound(val effect: Int) {
    Standard(AudioManager.FX_KEYPRESS_STANDARD),
    Space(AudioManager.FX_KEYPRESS_SPACEBAR),
    Delete(AudioManager.FX_KEYPRESS_DELETE),
    Enter(AudioManager.FX_KEYPRESS_RETURN),
}

/** 按鍵和候選字共用的觸感和聲音：震動跟隨系統的設定；audio 是 null 就不出按鍵音。 */
internal class KeyFeedback(private val audio: AudioManager?) {
    fun press(view: View, sound: KeySound = KeySound.Standard) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        // 音量 -1 表示用系統預設的按鍵音量；系統靜音時不會發聲。
        audio?.playSoundEffect(sound.effect, -1f)
    }
}

internal val LocalKeyFeedback = staticCompositionLocalOf { KeyFeedback(audio = null) }

/** 按鍵放大預覽：按住字母或符號鍵時，在按鍵上方放大顯示那個字。owner 是按着的那個鍵。 */
@Immutable
internal data class KeyPreview(val owner: Any, val label: String, val bounds: Rect)

@Stable
internal class KeyPreviewState {
    var shown by mutableStateOf<KeyPreview?>(null)
        private set

    fun show(preview: KeyPreview) {
        shown = preview
    }

    fun hide(owner: Any) {
        if (shown?.owner === owner) shown = null
    }
}

/** 關了按鍵放大預覽時是 null。 */
internal val LocalKeyPreview = staticCompositionLocalOf<KeyPreviewState?> { null }

/**
 * 畫在鍵盤最上層的預覽；origin 和 width 是鍵盤在畫面上的位置和闊度。
 * 預覽的底邊貼着鍵頂；橫向時按鍵比預覽闊，預覽就和按鍵一樣闊。
 */
@Composable
internal fun KeyPreviewBubble(state: KeyPreviewState, origin: Offset, width: Float, colors: KeyboardColors) {
    val preview = state.shown ?: return
    val dimensions = LocalKeyboardDimensions.current
    val density = LocalDensity.current
    val bubbleWidth = maxOf(with(density) { dimensions.previewWidth.toPx() }, preview.bounds.width)
    val bubbleHeight = with(density) { dimensions.previewHeight.toPx() }
    val x = (preview.bounds.center.x - origin.x - bubbleWidth / 2).coerceIn(0f, (width - bubbleWidth).coerceAtLeast(0f))
    val y = (preview.bounds.top - origin.y - bubbleHeight).coerceAtLeast(0f)
    Box(
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(with(density) { bubbleWidth.toDp() }, with(density) { bubbleHeight.toDp() })
            .shadow(4.dp, PreviewShape)
            .background(colors.preview, PreviewShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = preview.label,
            color = colors.label,
            fontSize = previewFontSize(preview.label, dimensions.previewSize),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 漢字（例如倉頡字根）的字形較大，小 2sp；「……」這類兩個字的符號縮小，才放得進預覽。 */
private fun previewFontSize(label: String, size: TextUnit): TextUnit = when {
    label.codePointCount(0, label.length) != 1 -> size * 0.6f
    Character.UnicodeScript.of(label.codePointAt(0)) == Character.UnicodeScript.HAN -> (size.value - 2f).sp
    else -> size
}

@Composable
internal fun KeyRow(
    height: Dp = LocalKeyboardDimensions.current.rowHeight,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        content = content,
    )
}

/**
 * 按鍵和候選字共用的按壓反饋：按下時震動（和按鍵音），底色變成 pressedColor；
 * 有 preview 就顯示按鍵放大預覽。description 是 TalkBack 讀出的標籤。
 */
@Composable
internal fun PressableBox(
    modifier: Modifier,
    color: Color,
    pressedColor: Color,
    description: String,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    longPressLabel: String? = null,
    stateDescription: String? = null,
    preview: String? = null,
    sound: KeySound = KeySound.Standard,
    content: @Composable BoxScope.() -> Unit,
) {
    val view = LocalView.current
    // pointerInput 只在 hasLongPress 改變時重啟，避免重組時打斷正在進行的按壓；其他值經 State 讀最新的。
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val feedback by rememberUpdatedState(LocalKeyFeedback.current)
    val previews by rememberUpdatedState(if (preview != null) LocalKeyPreview.current else null)
    val currentPreview by rememberUpdatedState(preview)
    val currentSound by rememberUpdatedState(sound)
    val hasLongPress = onLongPress != null
    var pressed by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val owner = remember { Any() }
    DisposableEffect(owner) {
        onDispose { previews?.hide(owner) }
    }

    Box(
        modifier = modifier
            .then(if (preview != null) Modifier.onGloballyPositioned { bounds = it.boundsInRoot() } else Modifier)
            .clip(KeyShape)
            .background(if (pressed) pressedColor else color)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                if (stateDescription != null) this.stateDescription = stateDescription
                onClick { currentOnTap(); true }
                if (hasLongPress) onLongClick(label = longPressLabel) { currentOnLongPress?.invoke(); true }
            }
            .pointerInput(hasLongPress) {
                val onPress: suspend PressGestureScope.(Offset) -> Unit = {
                    pressed = true
                    feedback.press(view, currentSound)
                    val label = currentPreview
                    if (label != null) previews?.show(KeyPreview(owner, label, bounds))
                    tryAwaitRelease()
                    pressed = false
                    previews?.hide(owner)
                }
                if (hasLongPress) {
                    detectTapGestures(
                        onPress = onPress,
                        onLongPress = { currentOnLongPress?.invoke() },
                        onTap = { currentOnTap() },
                    )
                } else {
                    detectTapGestures(onPress = onPress, onTap = { currentOnTap() })
                }
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
internal fun RowScope.Key(
    colors: KeyboardColors,
    weight: Float,
    description: String,
    style: KeyStyle = KeyStyle.Character,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    longPressLabel: String? = null,
    stateDescription: String? = null,
    preview: String? = null,
    sound: KeySound = KeySound.Standard,
    content: @Composable BoxScope.() -> Unit,
) {
    PressableBox(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .padding(horizontal = KeyPaddingX, vertical = KeyPaddingY)
            .keyShadow(colors.keyShadow),
        color = when (style) {
            KeyStyle.Character -> colors.key
            KeyStyle.Function -> colors.functionKey
            KeyStyle.Accent -> colors.accent
            KeyStyle.AccentSoft -> colors.accentSoft
        },
        pressedColor = colors.pressedKey,
        description = description,
        onTap = onTap,
        onLongPress = onLongPress,
        longPressLabel = longPressLabel,
        stateDescription = stateDescription,
        preview = preview,
        sound = sound,
        content = content,
    )
}

/** 按鍵上的圖示，全部來自 Material Symbols Rounded。 */
@Composable
internal fun KeyIcon(@DrawableRes icon: Int, tint: Color, size: Dp = 22.dp) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(size),
    )
}

/** Emoji 面板和剪貼簿面板左下角的返回鍵，回到字母頁。 */
@Composable
internal fun RowScope.BackToLettersKey(colors: KeyboardColors, onTap: () -> Unit) {
    Key(
        colors,
        weight = 1.5f,
        description = stringResource(R.string.key_back_to_letters),
        style = KeyStyle.Function,
        onTap = onTap,
    ) {
        KeyIcon(R.drawable.ic_undo, colors.label)
    }
}

/**
 * 字母鍵：按按鍵標示的設定顯示英文字母和倉頡字根；digit 是長按輸入的數字，左上角有小字標示。
 * 鍵面分上下兩區：頂部放角落的小字，大字在下面置中，兩者不會重疊。
 * digitHints 表示第一行有長按數字的小字；只顯示英文字母時，每一行的大字都向下讓開一點，保持對齊。
 */
@Composable
internal fun RowScope.LetterKey(
    letter: Char,
    uppercase: Boolean,
    labels: KeyLabels,
    colors: KeyboardColors,
    onTap: () -> Unit,
    digit: Char? = null,
    digitHints: Boolean = false,
    onLongPress: (() -> Unit)? = null,
) {
    val dimensions = LocalKeyboardDimensions.current
    val shown = if (uppercase) letter.uppercase() else letter.toString()
    val radical = cangjieRadical(letter).toString()
    val (main, corner) = when (labels) {
        KeyLabels.LettersLarge -> shown to radical
        KeyLabels.RadicalsLarge -> radical to shown
        KeyLabels.LettersOnly -> shown to null
    }
    val mainSize = when (labels) {
        KeyLabels.LettersLarge -> dimensions.letterSize
        KeyLabels.RadicalsLarge -> dimensions.radicalSize
        KeyLabels.LettersOnly -> dimensions.lettersOnlySize
    }
    // 直向標準高度的鍵面高 44dp：角落小字佔頂部 12dp；只有英文字母時讓開 4dp。其他高度按比例。
    val face = dimensions.rowHeight - KeyPaddingY * 2
    val mainTop = face * when {
        corner != null -> 12f / 44f
        digitHints -> 4f / 44f
        else -> 0f
    }
    Key(
        colors,
        weight = 1f,
        description = if (corner == null) shown else stringResource(R.string.key_letter_radical, shown, radical),
        onTap = onTap,
        onLongPress = onLongPress,
        longPressLabel = digit?.toString(),
        preview = main,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = mainTop),
            contentAlignment = Alignment.Center,
        ) {
            // 橫向又選了矮鍵盤時，下區可能比字矮；讓字畫出界外，不要切掉頂部和底部。
            Text(text = main, color = colors.label, style = tightText(mainSize), overflow = TextOverflow.Visible)
        }
        if (corner != null) CornerLabel(corner, Alignment.TopEnd, dimensions.cornerSize, colors)
        if (digit != null) CornerLabel(digit.toString(), Alignment.TopStart, dimensions.digitHintSize, colors)
    }
}

/** 鍵面角落的小字：距頂 3dp，距左或右 4dp。 */
@Composable
private fun BoxScope.CornerLabel(text: String, alignment: Alignment, fontSize: TextUnit, colors: KeyboardColors) {
    Text(
        text = text,
        color = colors.secondaryLabel,
        style = tightText(fontSize),
        modifier = Modifier
            .align(alignment)
            .padding(start = 4.dp, top = 3.dp, end = 4.dp),
    )
}

/** 行高等於字級、上下不留空（像 CSS 的 line-height: 1），小字和大字的位置才準確。 */
private fun tightText(fontSize: TextUnit) = TextStyle(
    fontSize = fontSize,
    lineHeight = fontSize,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

@Composable
internal fun KeyLabel(
    text: String,
    colors: KeyboardColors,
    fontSize: TextUnit = 20.sp,
    fontWeight: FontWeight? = null,
) {
    Text(text = text, color = colors.label, fontSize = fontSize, fontWeight = fontWeight)
}
