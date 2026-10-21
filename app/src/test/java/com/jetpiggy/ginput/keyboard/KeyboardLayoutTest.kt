package com.jetpiggy.ginput.keyboard

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jetpiggy.ginput.settings.KeyboardHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutTest {

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
        val dimensions = keyboardDimensions(KeyboardLayout.PhoneLandscape, KeyboardHeight.Standard.scale, numberRow = true, 411.dp)

        assertEquals(38.dp, dimensions.barHeight)
        assertEquals(38.dp, dimensions.rowHeight)
    }

    @Test
    fun tallTabletScreenKeepsThePhonePortraitSizes() {
        for (height in KeyboardHeight.entries) {
            val dimensions = keyboardDimensions(KeyboardLayout.Tablet, height.scale, numberRow = true, 1280.dp)

            assertEquals(PortraitDimensions.withHeight(height.scale), dimensions)
        }
    }

    @Test
    fun shortTabletScreenShrinksRowsToHalfTheScreen() {
        // 7 吋平板打橫大約高 600dp。
        for (height in KeyboardHeight.entries) {
            for (numberRow in listOf(false, true)) {
                val dimensions = keyboardDimensions(KeyboardLayout.Tablet, height.scale, numberRow, 600.dp)

                assertTrue(keyboardHeight(dimensions, numberRow) <= 300.dp + 0.01.dp)
                assertTrue(dimensions.rowHeight <= PortraitDimensions.rowHeight * height.scale + 0.01.dp)
                assertEquals(PortraitDimensions.barHeight, dimensions.barHeight)
            }
        }
    }

    @Test
    fun tabletRowsStopShrinkingAt38dp() {
        val dimensions = keyboardDimensions(KeyboardLayout.Tablet, KeyboardHeight.Standard.scale, numberRow = true, 400.dp)

        assertEquals(38f, dimensions.rowHeight.value, 0.01f)
    }

    private fun keyboardHeight(dimensions: KeyboardDimensions, numberRow: Boolean): Dp =
        KeyboardPaddingY * 2 + dimensions.barHeight + dimensions.rowHeight * 4 +
            if (numberRow) dimensions.numberRowHeight else 0.dp
}
