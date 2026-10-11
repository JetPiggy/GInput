package com.jetpiggy.ginput.keyboard

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import com.jetpiggy.ginput.R
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 空白鍵上每掃過這個距離，游標移一格。 */
private val CursorStep = 12.dp

/** Delete 鍵向左掃，每掃過這個距離多選取一個字詞。 */
private val WordStep = 28.dp

/** 手指掃到離鍵盤左邊這個距離以內，就選取整行。 */
private val LineEdge = 32.dp

private const val REPEAT_DELAY_MILLIS = 400L
private const val REPEAT_FIRST_INTERVAL_MILLIS = 90L
private const val REPEAT_SPEEDUP_MILLIS = 6L
private const val REPEAT_FASTEST_INTERVAL_MILLIS = 30L

/** 空白鍵：按一下打空格；按住左右掃就移動游標，每次開始掃都通知 onSwipe。 */
@Composable
internal fun RowScope.SpaceKey(
    colors: KeyboardColors,
    weight: Float,
    onKey: (KeyAction) -> Unit,
    onSwipe: () -> Unit = {},
) {
    val view = LocalView.current
    val stepPx = with(LocalDensity.current) { CursorStep.toPx() }
    val currentOnKey by rememberUpdatedState(onKey)
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val feedback by rememberUpdatedState(LocalKeyFeedback.current)
    val description = stringResource(R.string.key_space)
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .padding(horizontal = KeyPaddingX, vertical = KeyPaddingY)
            .keyShadow(colors.keyShadow)
            .clip(KeyShape)
            .background(if (pressed) colors.pressedKey else colors.key)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick { currentOnKey(KeyAction.Space); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    feedback.press(view, KeySound.Space)
                    var dragging = false
                    var pending = 0f
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (!dragging) currentOnKey(KeyAction.Space)
                                break
                            }
                            pending += change.positionChange().x
                            if (!dragging && abs(pending) > viewConfiguration.touchSlop) {
                                dragging = true
                                pending = 0f
                                currentOnSwipe()
                            }
                            if (dragging) {
                                change.consume()
                                val steps = (pending / stepPx).toInt()
                                if (steps != 0) {
                                    currentOnKey(KeyAction.MoveCursor(steps))
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    pending -= steps * stepPx
                                }
                            }
                        }
                    } finally {
                        pressed = false
                    }
                }
            },
    )
}

/**
 * Delete 鍵：按一下刪一個字；按住連續刪除並逐漸加速；
 * 向左掃逐個字詞選取，放手就刪除，掃到最左邊選取整行，掃回原位就取消。
 */
@Composable
internal fun RowScope.DeleteKey(colors: KeyboardColors, weight: Float, onKey: (KeyAction) -> Unit) {
    val view = LocalView.current
    val density = LocalDensity.current
    val wordStepPx = with(density) { WordStep.toPx() }
    val edgePx = with(density) { LineEdge.toPx() }
    val scope = rememberCoroutineScope()
    val currentOnKey by rememberUpdatedState(onKey)
    val feedback by rememberUpdatedState(LocalKeyFeedback.current)
    val description = stringResource(R.string.key_delete)
    var pressed by remember { mutableStateOf(false) }
    var keyLeft by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .padding(horizontal = KeyPaddingX, vertical = KeyPaddingY)
            .onGloballyPositioned { keyLeft = it.positionInRoot().x }
            .keyShadow(colors.keyShadow)
            .clip(KeyShape)
            .background(if (pressed) colors.pressedKey else colors.functionKey)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick { currentOnKey(KeyAction.Delete); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    feedback.press(view, KeySound.Delete)
                    var repeating = false
                    var selecting = false
                    var selection = 0 to false
                    val repeat = scope.launch {
                        delay(REPEAT_DELAY_MILLIS)
                        repeating = true
                        var interval = REPEAT_FIRST_INTERVAL_MILLIS
                        while (true) {
                            currentOnKey(KeyAction.Delete)
                            feedback.press(view, KeySound.Delete)
                            delay(interval)
                            interval = (interval - REPEAT_SPEEDUP_MILLIS).coerceAtLeast(REPEAT_FASTEST_INTERVAL_MILLIS)
                        }
                    }
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val leftward = down.position.x - change.position.x
                            val slop = viewConfiguration.touchSlop
                            if (!repeating && !selecting && leftward > slop) {
                                selecting = true
                                repeat.cancel()
                            }
                            if (selecting) {
                                change.consume()
                                val wholeLine = keyLeft + change.position.x < edgePx
                                val words = when {
                                    wholeLine || leftward <= slop -> 0
                                    else -> ((leftward - slop) / wordStepPx).toInt() + 1
                                }
                                val next = words to wholeLine
                                if (next != selection) {
                                    selection = next
                                    currentOnKey(KeyAction.SelectBackward(words, wholeLine))
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        }
                    } finally {
                        repeat.cancel()
                        pressed = false
                    }
                    when {
                        selecting -> currentOnKey(
                            KeyAction.FinishBackwardSelection(delete = selection.first > 0 || selection.second),
                        )
                        !repeating -> currentOnKey(KeyAction.Delete)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        KeyIcon(R.drawable.ic_backspace, colors.label)
    }
}
