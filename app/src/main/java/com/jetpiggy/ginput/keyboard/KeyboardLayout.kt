package com.jetpiggy.ginput.keyboard

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 鍵盤的版面，按螢幕大小和方向決定。 */
internal enum class KeyboardLayout {
    /** 手機直向：候選列、數字行和四行按鍵分開，按鍵拉到全闊。 */
    Phone,

    /** 手機打橫：候選列和四行按鍵，左右有側欄；沒有獨立的數字行。 */
    PhoneLandscape,

    /** 平板（包括打開了的摺機）：直向和打橫都用手機直向的版面，按鍵區限闊、置中。 */
    Tablet,
}

/** 螢幕最短邊到這個闊度（dp）就算平板。 */
internal const val TABLET_SMALLEST_WIDTH_DP = 600

internal fun keyboardLayout(smallestWidthDp: Int, landscape: Boolean): KeyboardLayout = when {
    smallestWidthDp >= TABLET_SMALLEST_WIDTH_DP -> KeyboardLayout.Tablet
    landscape -> KeyboardLayout.PhoneLandscape
    else -> KeyboardLayout.Phone
}

/** 平板的按鍵區最闊到這裏（連鍵盤左右的邊距），螢幕較闊時置中。 */
internal val TabletMaxWidth = 800.dp

/** 平板螢幕較矮時行高縮小，最矮到這裏。 */
private val TabletMinRowHeight = 38.dp

/**
 * 各個版面的尺寸；scale 是鍵盤高度的設定，numberRow 是有沒有開數字行，screenHeight 是螢幕的高度。
 * 平板用手機直向的尺寸，螢幕較矮時（例如 7 吋平板打橫）縮小行高，令鍵盤（不計導航列）不超過螢幕高度的一半。
 */
internal fun keyboardDimensions(layout: KeyboardLayout, scale: Float, numberRow: Boolean, screenHeight: Dp): KeyboardDimensions =
    when (layout) {
        KeyboardLayout.Phone -> PortraitDimensions.withHeight(scale)
        KeyboardLayout.PhoneLandscape -> LandscapeDimensions.withHeight(scale)
        KeyboardLayout.Tablet -> {
            val base = PortraitDimensions
            // 候選列和邊距不變，只縮按鍵的行高。
            val rows = base.rowHeight * 4 + if (numberRow) base.numberRowHeight else 0.dp
            val fits = (screenHeight / 2 - base.barHeight - KeyboardPaddingY * 2) / rows
            base.withHeight(minOf(scale, fits).coerceAtLeast(TabletMinRowHeight / base.rowHeight))
        }
    }
