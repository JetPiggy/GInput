package com.jetpiggy.ginput.keyboard

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.offset
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.doOnAttach

/**
 * 導航列在鍵盤視窗裏佔的位置（px）：鍵盤的底色伸到導航列下面，按鍵讓出這些位置（Modifier.navigationBarSpace）。
 *
 * Android 15 起，輸入法視窗伸到導航列下面，鍵盤的高度要連導航列一起算。Compose 的 navigationBarsPadding()
 * 收到導航列的高度後，不一定在同一次量度就用上（例如系統的動畫期間要等動畫完結）；打開鍵盤時視窗可能先用了
 * 不計導航列的高度，之後再長高（閃一下），或者停在矮了的高度，最底一行被擋住、壓扁。所以這裏自己接收視窗的
 * insets，在量度時才讀，收到就在同一次量度用上；鍵盤視窗顯示之前，也先向系統讀一次導航列現在的高度。
 *
 * 手勢導航時，系統在鍵盤底部加上輸入法的按鈕（收起鍵盤、切換輸入法）。較新的 Android 把這排按鈕當作 caption bar
 * 報給鍵盤，導航列的 insets 只有手勢條的高度（例如 Pixel），所以兩樣都要讓出，否則最底一行被按鈕擋住。
 * 手機橫向版面在 Android 15 起收起這排按鈕（見 GInputImeService），改由側欄提供，鍵盤貼近螢幕底部。
 *
 * 另外記下螢幕開孔（瀏海）在鍵盤左右佔的位置：鍵盤的底色伸到開孔旁邊，按鍵避開開孔。
 */
@Stable
class NavigationBarSpace {

    /** 左、右和下面要讓出的位置；在量度時讀，改了就即時重新量度。 */
    internal var insets by mutableStateOf(Insets.NONE)
        private set

    /**
     * 系統有沒有在鍵盤旁邊顯示輸入法的按鈕（收起鍵盤、切換輸入法）：三按鈕導航的導航列，或手勢導航時鍵盤底部的按鈕列。
     * Android 15 之前一律當作有：手勢導航時系統也有這些按鈕，而且不讓輸入法收起。在量度時讀。
     */
    internal var systemButtons by mutableStateOf(true)
        private set

    /** 螢幕開孔在鍵盤左右佔的位置（px），已扣除導航列讓出的位置；上下不用理會。 */
    internal var cutout by mutableStateOf(Insets.NONE)
        private set

    private var view: View? = null

    /** 鍵盤畫面最近收到的導航列 insets（連輸入法的按鈕）；未收到過是 null。 */
    private var received: Insets? = null

    /** 輸入法的按鈕最近佔的高度（caption bar）；導航列的 insets 已經包括按鈕的話是 0。 */
    private var buttonsBottom = 0

    /**
     * 導航列在畫面底部時，鍵盤視窗收不收到它的高度：Android 15 起輸入法視窗伸到導航列下面，會收到；
     * 之前的版本由系統讓出位置，收到的是 0。未知是 null。
     */
    private var receivesBar: Boolean? = null

    /** 跟着鍵盤畫面（onCreateInputView 回傳的 view）收到的 window insets。 */
    fun follow(view: View) {
        this.view = view
        received = null
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, windowInsets ->
            val buttons = windowInsets.getInsets(WindowInsetsCompat.Type.captionBar())
            val navigation = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val bar = Insets.max(navigation, buttons)
            // 手勢導航時左右兩邊有返回手勢的範圍；三按鈕導航時兩邊的系統手勢範圍最多只有導航列本身。
            // （導航列可以按的範圍 tappable element 分不出來：系統一律把導航列的位置報給鍵盤，讓它放按鈕。）
            val gestures = windowInsets.getInsets(WindowInsetsCompat.Type.systemGestures())
            val gestureNavigation = gestures.left > navigation.left && gestures.right > navigation.right
            val hole = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            received = bar
            buttonsBottom = buttons.bottom
            systemBarBottom(target)?.takeIf { it > 0 }?.let { receivesBar = bar.bottom > 0 }
            cutout = Insets.of(maxOf(0, hole.left - bar.left), 0, maxOf(0, hole.right - bar.right), 0)
            set(
                view = target,
                bar = bar,
                buttons = Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM || buttons.bottom > 0 || !gestureNavigation,
            )
            windowInsets
        }
        // 新加入視窗的 view 未必收到 insets，所以接上視窗時要求系統再派一次。
        view.doOnAttach { it.requestApplyInsets() }
    }

    /**
     * 鍵盤視窗由隱藏變成顯示之前：向系統讀導航列現在的高度，視窗第一次量度就計在內。
     * 例如上次在全螢幕的 App 打開鍵盤時導航列收起了，這次就不會沿用上次的高度。
     * 只估計底部：系統讀不到輸入法的按鈕，所以沿用上次按鈕的高度；全螢幕的 App 收起了導航列，按鈕仍然在。
     * 確實的數字以視窗之後收到的 insets 為準。
     */
    fun beforeShow() {
        val view = view ?: return
        val bottom = systemBarBottom(view) ?: return
        val estimate = if (receivesBar == false) 0 else maxOf(bottom, buttonsBottom)
        set(view, Insets.of(insets.left, 0, insets.right, estimate))
        // 估計錯了而視窗的 insets 沒有變的話，系統不會再派 insets；畫好之後改回視窗收到的。
        view.post { received?.let { set(view, it) } }
    }

    /** 系統現在的導航列在畫面底部佔多高（px）；Android 11 之前讀不到，是 null。 */
    private fun systemBarBottom(view: View): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val windowManager = view.context.getSystemService(WindowManager::class.java) ?: return null
        return windowManager.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.navigationBars()).bottom
    }

    private fun set(view: View, bar: Insets, buttons: Boolean = systemButtons) {
        val next = Insets.of(bar.left, 0, bar.right, bar.bottom)
        if (next == insets && buttons == systemButtons) return
        insets = next
        systemButtons = buttons
        // 讓在量度時讀 insets 的地方即時知道改了，並要求重新量度，不用等下一格畫面重組。
        Snapshot.sendApplyNotifications()
        view.remeasure()
        // 系統排版途中才收到的 insets，上面的要求會被那次排版吞掉：那次只在視窗現有的高度內量度，視窗不會長高。
        // 排版完成後再要求一次，視窗就按新的高度重新排版。
        view.post { view.remeasure() }
    }
}

/**
 * 要求連同裏面的 view 一起重新量度。只要求外層的話，Compose 裏面的 view 收到量過的尺寸要求時會沿用上次的結果
 * （View 的量度快取），讀不到新的 insets：視窗不會長高，最底一行被切走，要等到按鍵令 Compose 自己要求重新量度。
 */
private fun View.remeasure() {
    forceLayoutTree()
    requestLayout()
}

private fun View.forceLayoutTree() {
    forceLayout()
    if (this is ViewGroup) children.forEach { it.forceLayoutTree() }
}

/**
 * 讓出導航列的位置。在量度時才讀 space，導航列的高度一變就即時重新量度。
 * 有 bottomGap 而系統沒有顯示輸入法的按鈕時（手機橫向版面收起了按鈕列），底部只留 bottomGap，不讓出整個手勢條。
 */
internal fun Modifier.navigationBarSpace(space: NavigationBarSpace, bottomGap: Dp? = null): Modifier = layout { measurable, constraints ->
    val insets = space.insets
    val horizontal = insets.left + insets.right
    val bottom = if (bottomGap != null && !space.systemButtons) bottomGap.roundToPx() else insets.bottom
    val placeable = measurable.measure(constraints.offset(-horizontal, -bottom))
    layout(
        constraints.constrainWidth(placeable.width + horizontal),
        constraints.constrainHeight(placeable.height + bottom),
    ) {
        placeable.place(insets.left, 0)
    }
}
