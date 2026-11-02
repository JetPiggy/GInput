package com.jetpiggy.ginput.keyboard

import com.jetpiggy.ginput.settings.KeyboardHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardDimensionsTest {

    /** 全部可以揀的百分比，80 至 125。 */
    private val percents = (KeyboardHeight.MIN..KeyboardHeight.MAX step KeyboardHeight.STEP).toList()

    @Test
    fun shorterKeysShrinkTheirTextWithTheKeyFace() {
        for (base in listOf(PortraitDimensions, LandscapeDimensions)) {
            for (percent in percents.filter { it < KeyboardHeight.STANDARD }) {
                val scale = percent / 100f
                val short = base.withHeight(scale)
                val ratio = (short.rowHeight - KeyPaddingY * 2) / (base.rowHeight - KeyPaddingY * 2)

                assertEquals(base.rowHeight.value * scale, short.rowHeight.value, 0.01f)
                assertEquals(base.numberRowHeight.value * scale, short.numberRowHeight.value, 0.01f)
                assertEquals(base.letterSize.value * ratio, short.letterSize.value, 0.01f)
                assertEquals(base.radicalSize.value * ratio, short.radicalSize.value, 0.01f)
                assertEquals(base.lettersOnlySize.value * ratio, short.lettersOnlySize.value, 0.01f)
                assertEquals(base.numberRowSize.value * ratio, short.numberRowSize.value, 0.01f)
                assertEquals(base.cornerSize.value * ratio, short.cornerSize.value, 0.01f)
                assertEquals(base.digitHintSize.value * ratio, short.digitHintSize.value, 0.01f)
                // 候選列不受鍵盤高度影響。
                assertEquals(base.barHeight, short.barHeight)
                assertEquals(base.candidateSize, short.candidateSize)
            }
        }
    }

    @Test
    fun cornerTextStaysReadableAtTheLowestHeight() {
        for (base in listOf(PortraitDimensions, LandscapeDimensions)) {
            val lowest = base.withHeight(KeyboardHeight.MIN / 100f)
            assertTrue(lowest.cornerSize.value >= 7.9f)
            assertTrue(lowest.digitHintSize.value >= 7f)
        }
    }

    @Test
    fun standardAndTallerKeysKeepTheDesignTextSizes() {
        for (percent in percents.filter { it >= KeyboardHeight.STANDARD }) {
            val scale = percent / 100f
            val scaled = PortraitDimensions.withHeight(scale)

            assertEquals(PortraitDimensions.rowHeight * scale, scaled.rowHeight)
            assertEquals(PortraitDimensions.letterSize, scaled.letterSize)
            assertEquals(PortraitDimensions.radicalSize, scaled.radicalSize)
            assertEquals(PortraitDimensions.cornerSize, scaled.cornerSize)
            assertEquals(PortraitDimensions.digitHintSize, scaled.digitHintSize)
        }
    }
}
