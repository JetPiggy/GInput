package com.jetpiggy.ginput.keyboard

import com.jetpiggy.ginput.handwriting.InkPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InkPadTest {

    private fun InkPad.stroke(start: Long, end: Long): List<List<InkPoint>> {
        down(InkPoint(0f, 0f, start))
        move(InkPoint(10f, 10f, end))
        return up(end)
    }

    @Test
    fun strokesCollectUntilCleared() {
        val pad = InkPad()
        assertTrue(pad.isEmpty)
        pad.stroke(0, 100)
        val strokes = pad.stroke(200, 300)
        assertEquals(2, strokes.size)
        assertEquals(2, strokes[1].size)
        assertFalse(pad.isEmpty)
        pad.clear()
        assertTrue(pad.isEmpty)
    }

    @Test
    fun pausingStartsTheNextCharacter() {
        val pad = InkPad()
        pad.stroke(0, 100)
        // 停筆不夠 1 秒：同一個字的下一筆。
        assertFalse(pad.startsNewCharacter(100 + InkPad.AUTO_COMMIT_MILLIS, hasCandidates = true))
        // 停筆超過 1 秒：先寫入第一個候選字。
        assertTrue(pad.startsNewCharacter(101 + InkPad.AUTO_COMMIT_MILLIS, hasCandidates = true))
    }

    @Test
    fun nothingIsWrittenWithoutInkOrCandidates() {
        val pad = InkPad()
        assertFalse(pad.startsNewCharacter(10_000, hasCandidates = true))
        pad.stroke(0, 100)
        // 仍然在辨識，未有候選字：照樣算同一個字。
        assertFalse(pad.startsNewCharacter(10_000, hasCandidates = false))
    }
}
