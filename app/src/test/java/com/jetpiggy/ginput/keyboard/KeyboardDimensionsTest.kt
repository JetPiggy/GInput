package com.jetpiggy.ginput.keyboard

import com.jetpiggy.ginput.settings.KeyboardHeight
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardDimensionsTest {

    @Test
    fun compactKeysShrinkTheirTextWithTheKeyFace() {
        for (base in listOf(PortraitDimensions, LandscapeDimensions)) {
            val compact = base.withHeight(KeyboardHeight.Compact.scale)
            val ratio = (compact.rowHeight - KeyPaddingY * 2) / (base.rowHeight - KeyPaddingY * 2)

            assertEquals(base.rowHeight.value * 0.85f, compact.rowHeight.value, 0.01f)
            assertEquals(base.numberRowHeight.value * 0.85f, compact.numberRowHeight.value, 0.01f)
            assertEquals(base.letterSize.value * ratio, compact.letterSize.value, 0.01f)
            assertEquals(base.radicalSize.value * ratio, compact.radicalSize.value, 0.01f)
            assertEquals(base.lettersOnlySize.value * ratio, compact.lettersOnlySize.value, 0.01f)
            assertEquals(base.numberRowSize.value * ratio, compact.numberRowSize.value, 0.01f)
            assertEquals(base.cornerSize.value * ratio, compact.cornerSize.value, 0.01f)
            assertEquals(base.digitHintSize.value * ratio, compact.digitHintSize.value, 0.01f)
            // 候選列不受鍵盤高度影響。
            assertEquals(base.barHeight, compact.barHeight)
            assertEquals(base.candidateSize, compact.candidateSize)
        }
    }

    @Test
    fun standardAndTallKeysKeepTheDesignTextSizes() {
        for (height in listOf(KeyboardHeight.Standard, KeyboardHeight.Tall)) {
            val scaled = PortraitDimensions.withHeight(height.scale)

            assertEquals(PortraitDimensions.rowHeight * height.scale, scaled.rowHeight)
            assertEquals(PortraitDimensions.letterSize, scaled.letterSize)
            assertEquals(PortraitDimensions.radicalSize, scaled.radicalSize)
            assertEquals(PortraitDimensions.cornerSize, scaled.cornerSize)
            assertEquals(PortraitDimensions.digitHintSize, scaled.digitHintSize)
        }
    }
}
