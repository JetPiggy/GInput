package com.jetpiggy.ginput.keyboard

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp

/** 游標滑動時，手指每左右移過這個距離，游標移一格。 */
internal val CursorStep = 12.dp

/**
 * 在空白鍵以外的按鍵上，手指左右移過這個距離才開始游標滑動；移得少一點，放手時仍然算按了那個鍵。
 * 比空白鍵開始掃的距離（touch slop）長，快速打字時手指輕微移動不會變成移動游標。
 */
internal val SlideStart = 24.dp

/**
 * 游標滑動：在按鍵上按住左右滑動就移動游標，不打字；按住 Shift 時滑動就伸展選取。
 * 打字的按鍵區（字母頁、123 頁、最底一行和數字鍵盤）共用一個，記住 Shift 鍵是否正被按住。
 * onMove 收到移動的格數和是否選取；onStart 在每次開始滑動時呼叫（給使用統計）。
 */
@Stable
internal class CursorSlide(
    private val onMove: (steps: Int, select: Boolean) -> Unit,
    private val onStart: () -> Unit,
) {
    /** Shift 鍵正被按住；這時在其他鍵上的游標滑動是選取。 */
    var shiftHeld = false
        private set

    /** 按住 Shift 期間，有沒有游標滑動用它來選取。 */
    private var shiftUsed = false

    fun pressShift() {
        shiftHeld = true
        shiftUsed = false
    }

    /** 放開 Shift；按住期間用來選取過就回傳 true，這時 Shift 不切換大小寫。 */
    fun releaseShift(): Boolean {
        shiftHeld = false
        return shiftUsed
    }

    fun start() = onStart()

    fun move(steps: Int, select: Boolean) {
        if (select && shiftHeld) shiftUsed = true
        onMove(steps, select)
    }
}

/** 打字的按鍵區才有；面板裏的按鍵是 null，不能游標滑動。 */
internal val LocalCursorSlide = staticCompositionLocalOf<CursorSlide?> { null }

/**
 * 開始游標滑動，然後跟着手指移動游標，直到放手：每左右移過 CursorStep 移一格，並輕輕震一下。
 * firstStep 是開始時先移的格數。select 就一直選取；否則按住了 Shift 就開始選取，之後放開 Shift 也繼續選取，
 * 兩隻手指不是同時放開，選取也不會被取消。這隻手指的事件都會用掉。
 */
internal suspend fun AwaitPointerEventScope.followCursorSlide(
    pointer: PointerId,
    slide: CursorSlide,
    view: View,
    select: Boolean = false,
    firstStep: Int = 0,
) {
    val stepPx = CursorStep.toPx()
    var selecting = select
    fun move(steps: Int) {
        selecting = selecting || slide.shiftHeld
        slide.move(steps, selecting)
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    slide.start()
    if (firstStep != 0) move(firstStep)
    var pending = 0f
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == pointer } ?: return
        if (!change.pressed) {
            change.consume()
            return
        }
        pending += change.positionChange().x
        change.consume()
        val steps = (pending / stepPx).toInt()
        if (steps != 0) {
            move(steps)
            pending -= steps * stepPx
        }
    }
}
