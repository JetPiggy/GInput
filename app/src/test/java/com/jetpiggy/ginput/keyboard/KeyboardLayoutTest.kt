package com.jetpiggy.ginput.keyboard

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jetpiggy.ginput.settings.KeyboardHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutTest {

    private val scales = (KeyboardHeight.MIN..KeyboardHeight.MAX step KeyboardHeight.STEP).map { it / 100f }

    @Test
    fun smallestWidthDecidesPhoneOrTablet() {
        assertEquals(KeyboardLayout.Phone, keyboardLayout(smallestWidthDp = 411, landscape = false))
        assertEquals(KeyboardLayout.PhoneLandscape, keyboardLayout(smallestWidthDp = 411, landscape = true))
        assertEquals(KeyboardLayout.PhoneLandscape, keyboardLayout(smallestWidthDp = 599, landscape = true))
        // 平板和打開了的摺機：直向和打橫都一樣。
        assertEquals(KeyboardLayout.Tablet, keyboardLayout(smallestWidthDp = 600, landscape = false))
        assertEquals(KeyboardLayout.Tablet, keyboardLayout(smallestWidthDp = 600, landscape = true))
        assertEquals(KeyboardLayout.Tablet, keyboardLayout(smallestWidthDp = 800, landscape = true))
    }

    @Test
    fun phoneLandscapeHasFiveEqualRows() {
        val dimensions = keyboardDimensions(KeyboardLayout.PhoneLandscape, 1f, numberRow = true, 411.dp)

        assertEquals(38.dp, dimensions.barHeight)
        assertEquals(38.dp, dimensions.rowHeight)
    }

    @Test
    fun tallTabletScreenKeepsThePhonePortraitSizes() {
        for (scale in scales) {
            val dimensions = keyboardDimensions(KeyboardLayout.Tablet, scale, numberRow = true, 1280.dp)

            assertEquals(PortraitDimensions.withHeight(scale), dimensions)
        }
    }

    @Test
    fun shortTabletScreenShrinksRowsToHalfTheScreen() {
        // 7 吋平板打橫大約高 600dp。
        for (scale in scales) {
            for (numberRow in listOf(false, true)) {
                val dimensions = keyboardDimensions(KeyboardLayout.Tablet, scale, numberRow, 600.dp)

                assertTrue(keyboardHeight(dimensions, numberRow) <= 300.dp + 0.01.dp)
                assertTrue(dimensions.rowHeight <= PortraitDimensions.rowHeight * scale + 0.01.dp)
                assertEquals(PortraitDimensions.barHeight, dimensions.barHeight)
            }
        }
    }

    @Test
    fun tabletRowsStopShrinkingAt38dp() {
        val dimensions = keyboardDimensions(KeyboardLayout.Tablet, 1f, numberRow = true, 400.dp)

        assertEquals(38f, dimensions.rowHeight.value, 0.01f)
    }

    @Test
    fun phonesCanUseTheWholeRange() {
        for (layout in listOf(KeyboardLayout.Phone, KeyboardLayout.PhoneLandscape)) {
            assertEquals(KeyboardHeight.MAX, maxHeightPercent(layout, numberRow = true, 640.dp))
        }
    }

    @Test
    fun tabletHeightStopsWhereTheKeyboardFillsHalfTheScreen() {
        assertEquals(KeyboardHeight.MAX, maxHeightPercent(KeyboardLayout.Tablet, numberRow = true, 1280.dp))
        for (numberRow in listOf(false, true)) {
            val max = maxHeightPercent(KeyboardLayout.Tablet, numberRow, 600.dp)
            assertTrue(max in KeyboardHeight.MIN until KeyboardHeight.MAX)
            assertEquals(0, max % KeyboardHeight.STEP)
            // 再高一格就會超過螢幕高度的一半，鍵盤不會跟着變高。
            val atMax = keyboardDimensions(KeyboardLayout.Tablet, max / 100f, numberRow, 600.dp)
            val above = keyboardDimensions(KeyboardLayout.Tablet, (max + KeyboardHeight.STEP) / 100f, numberRow, 600.dp)
            assertEquals(PortraitDimensions.withHeight(max / 100f), atMax)
            assertTrue(keyboardHeight(above, numberRow) <= 300.dp + 0.01.dp)
        }
        // 螢幕太矮，連最低的百分比也放不下。
        assertEquals(KeyboardHeight.MIN, maxHeightPercent(KeyboardLayout.Tablet, numberRow = true, 400.dp))
    }

    private fun keyboardHeight(dimensions: KeyboardDimensions, numberRow: Boolean): Dp =
        KeyboardPaddingY * 2 + dimensions.barHeight + dimensions.rowHeight * 4 +
            if (numberRow) dimensions.numberRowHeight else 0.dp
}
