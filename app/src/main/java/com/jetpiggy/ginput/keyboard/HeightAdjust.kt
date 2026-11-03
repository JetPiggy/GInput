package com.jetpiggy.ginput.keyboard

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetpiggy.ginput.R
import com.jetpiggy.ginput.settings.KeyboardHeight

/**
 * 調整高度模式（docs/spec/v1.md「外觀和手感」的鍵盤高度）：長按工具列的「設定」打開，候選列上面多了 HeightAdjustStrip，
 * 下面的候選列和按鍵照常可以打字，一邊調整一邊試打。max 是這部裝置最高可以拉到的百分比（平板受螢幕高度限制）。
 */
@Stable
internal class HeightAdjustment(initial: Int, val max: Int) {
    /** 正在顯示的百分比：拖動時即時改變，鍵盤跟着變高變矮。 */
    var percent by mutableIntStateOf(initial.coerceAtMost(max))
}

/** 調整列左邊的握把：兩條短橫線。 */
private val GripWidth = 18.dp
private val GripThickness = 2.dp
private val GripGap = 4.dp

/**
 * 一隻手指拖動調整列：按下時手指在螢幕上的 y、當時的百分比和按鍵區的高度（px）。
 * 鍵盤底部不動，按鍵區的高度和百分比成正比，所以手指移了多少，按鍵區就高或矮多少。
 */
private class HeightDrag(private val startY: Float, val start: Int, private val startHeight: Float) {
    fun percentAt(y: Float): Int = KeyboardHeight.nearest(start * (startHeight - (y - startY)) / startHeight)
}

/** 調整列的觸控狀態，只在觸控事件裏讀寫，不用重組。 */
private class StripTouch {
    var drag: HeightDrag? = null
}

/**
 * 調整列：左邊是握把、目前的百分比和說明，用一隻手指按住上下拖動，鍵盤上邊跟着手指，每過一格輕震一下，放手就用 onSave 存入設定；
 * 右邊是「還原」（回到 100%）和「完成」。keyArea 是按鍵區（不計候選列）的高度，和百分比成正比。
 * 鍵盤變高變矮時鍵盤視窗也跟着移動，視窗裏的座標會追不上，所以用手指在螢幕上的位置計算。
 * TalkBack 把拖動的範圍當作調整桿，每次加減一格，即時存入。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HeightAdjustStrip(
    adjustment: HeightAdjustment,
    keyArea: Dp,
    colors: KeyboardColors,
    onSave: (Int) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    val view = LocalView.current
    val feedback by rememberUpdatedState(LocalKeyFeedback.current)
    val currentOnSave by rememberUpdatedState(onSave)
    val keyAreaPx by rememberUpdatedState(with(LocalDensity.current) { keyArea.toPx() })
    val touch = remember(adjustment) { StripTouch() }
    val percent = adjustment.percent
    val label = stringResource(R.string.keyboard_height)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LocalKeyboardDimensions.current.barHeight)
            // 底部一條分隔線，和下面的候選列分開。
            .drawBehind {
                val line = 1.dp.toPx()
                drawRect(colors.divider, Offset(0f, size.height - line), Size(size.width, line))
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                // Compose 的觸控事件不提供螢幕座標，所以直接讀 MotionEvent 的 rawY。
                .pointerInteropFilter { motion ->
                    when (motion.actionMasked) {
                        MotionEvent.ACTION_DOWN -> touch.drag = HeightDrag(motion.rawY, adjustment.percent, keyAreaPx)
                        MotionEvent.ACTION_MOVE -> touch.drag?.let { drag ->
                            val next = drag.percentAt(motion.rawY).coerceAtMost(adjustment.max)
                            if (next != adjustment.percent) {
                                adjustment.percent = next
                                feedback.step(view)
                            }
                        }
                        // 放手、取消或多了一隻手指：停止拖動，有改動就存入。
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> touch.drag?.let { drag ->
                            touch.drag = null
                            if (adjustment.percent != drag.start) currentOnSave(adjustment.percent)
                        }
                    }
                    true
                }
                .clearAndSetSemantics {
                    contentDescription = label
                    stateDescription = "$percent%"
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = percent.toFloat(),
                        range = KeyboardHeight.MIN.toFloat()..adjustment.max.toFloat(),
                        steps = ((adjustment.max - KeyboardHeight.MIN) / KeyboardHeight.STEP - 1).coerceAtLeast(0),
                    )
                    setProgress { target ->
                        val next = KeyboardHeight.nearest(target).coerceAtMost(adjustment.max)
                        if (next != adjustment.percent) {
                            adjustment.percent = next
                            currentOnSave(next)
                        }
                        true
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(GripGap)) {
                repeat(2) {
                    Box(
                        Modifier
                            .size(GripWidth, GripThickness)
                            .background(colors.label, CircleShape),
                    )
                }
            }
            Text(
                text = stringResource(R.string.height_percent, percent),
                color = colors.label,
                fontSize = 16.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 10.dp),
            )
            Text(
                text = stringResource(R.string.adjust_height_hint),
                color = colors.secondaryLabel,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        BarItem(stringResource(R.string.adjust_height_reset), colors, english = true, onTap = onReset)
        BarItem(stringResource(R.string.adjust_height_done), colors, marked = true, english = true, onTap = onDone)
    }
}
