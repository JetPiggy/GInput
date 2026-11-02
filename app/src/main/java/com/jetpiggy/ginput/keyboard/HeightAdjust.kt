package com.jetpiggy.ginput.keyboard

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetpiggy.ginput.R
import com.jetpiggy.ginput.settings.KeyboardHeight

/**
 * 調整高度模式（docs/spec/v1.md「外觀和手感」的鍵盤高度）：長按工具列的「設定」打開，候選列換成 HeightAdjustBar，
 * 按鍵區蓋上 HeightAdjustCover。max 是這部裝置最高可以拉到的百分比（平板受螢幕高度限制）。
 */
@Stable
internal class HeightAdjustment(initial: Int, val max: Int) {
    /** 正在顯示的百分比：拖動時即時改變，鍵盤跟着變高變矮。 */
    var percent by mutableIntStateOf(initial.coerceAtMost(max))
}

/** 調整高度模式的候選列：目前的百分比、「還原」（回到 100%）和「完成」。 */
@Composable
internal fun HeightAdjustBar(percent: Int, colors: KeyboardColors, onReset: () -> Unit, onDone: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LocalKeyboardDimensions.current.barHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.adjust_height_value, percent),
            color = colors.label,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 4.dp),
        )
        BarItem(stringResource(R.string.adjust_height_reset), colors, english = true, onTap = onReset)
        BarItem(stringResource(R.string.adjust_height_done), colors, marked = true, english = true, onTap = onDone)
    }
}

/** 按鍵區最頂這個高度是橫條的觸控範圍；在下面按不會開始拖動。 */
private val HandleTouchHeight = 32.dp
private val HandleSize = 48.dp
private val HandleThickness = 5.dp

/** 蓋住按鍵的一層有多不透明：按鍵仍然隱約看得到。 */
private const val COVER_ALPHA = 0.85f

private val HintShape = RoundedCornerShape(percent = 50)

/**
 * 一隻手指拖動橫條：按下時手指在螢幕上的 y、當時的百分比和按鍵區的高度（px）。
 * 鍵盤底部不動，按鍵區的高度和百分比成正比，所以手指移了多少，按鍵區就高或矮多少。
 */
private class HeightDrag(private val startY: Float, val start: Int, private val startHeight: Float) {
    fun percentAt(y: Float): Int = KeyboardHeight.nearest(start * (startHeight - (y - startY)) / startHeight)
}

/** 蓋住按鍵那一層的觸控狀態，只在觸控事件裏讀寫，不用重組。 */
private class CoverTouch {
    var height = 0
    var drag: HeightDrag? = null
}

/**
 * 蓋住按鍵區的一層：觸控只到這一層，按鍵按不到。最頂是橫條，用一隻手指按住上下拖動，鍵盤上邊跟着手指，每過一格輕震一下；
 * 放手就用 onSave 存入設定。鍵盤變高變矮時鍵盤視窗也跟着移動，視窗裏的座標會追不上，所以用手指在螢幕上的位置計算。
 * TalkBack 把橫條當作調整桿，每次加減一格，即時存入。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun BoxScope.HeightAdjustCover(adjustment: HeightAdjustment, colors: KeyboardColors, onSave: (Int) -> Unit) {
    val view = LocalView.current
    val feedback by rememberUpdatedState(LocalKeyFeedback.current)
    val currentOnSave by rememberUpdatedState(onSave)
    val handlePx = with(LocalDensity.current) { HandleTouchHeight.toPx() }
    val touch = remember(adjustment) { CoverTouch() }
    val percent = adjustment.percent
    val handleLabel = stringResource(R.string.keyboard_height)
    Box(
        Modifier
            .matchParentSize()
            .keyboardBackground(colors, alpha = COVER_ALPHA)
            .onSizeChanged { touch.height = it.height }
            // Compose 的觸控事件不提供螢幕座標，所以直接讀 MotionEvent 的 rawY。
            .pointerInteropFilter { motion ->
                when (motion.actionMasked) {
                    // 按在橫條上才開始拖動；按在其他地方甚麼都不做。
                    MotionEvent.ACTION_DOWN -> touch.drag = if (motion.y <= handlePx) {
                        HeightDrag(motion.rawY, adjustment.percent, touch.height.toFloat())
                    } else {
                        null
                    }
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
            },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(HandleTouchHeight)
                .clearAndSetSemantics {
                    contentDescription = handleLabel
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
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(HandleSize, HandleThickness)
                    .background(colors.label, CircleShape),
            )
        }
        // 說明加上底色，不會和下面按鍵的字混在一起。
        Text(
            text = stringResource(R.string.adjust_height_hint),
            color = colors.label,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 16.dp)
                .background(colors.background, HintShape)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
