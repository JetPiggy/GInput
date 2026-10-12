package com.jetpiggy.ginput.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorSlideTest {

    private val moves = mutableListOf<Pair<Int, Boolean>>()
    private var starts = 0
    private val slide = CursorSlide(onMove = { steps, select -> moves += steps to select }, onStart = { starts++ })

    @Test
    fun movesArePassedOnWithWhetherToSelect() {
        slide.start()
        slide.move(-2, select = false)
        slide.move(1, select = true)
        assertEquals(1, starts)
        assertEquals(listOf(-2 to false, 1 to true), moves)
    }

    @Test
    fun shiftUsedForSelectingDoesNotToggleCaseWhenReleased() {
        slide.pressShift()
        assertTrue(slide.shiftHeld)
        slide.move(-1, select = true)
        assertTrue(slide.releaseShift())
        assertFalse(slide.shiftHeld)
    }

    @Test
    fun shiftReleasedWithoutSelectingStillTogglesCase() {
        slide.pressShift()
        assertFalse(slide.releaseShift())
    }

    @Test
    fun selectingWithoutShiftHeldDoesNotMarkTheNextShiftAsUsed() {
        slide.move(1, select = true)
        slide.pressShift()
        assertFalse(slide.releaseShift())
    }

    @Test
    fun pressingShiftAgainStartsAfresh() {
        slide.pressShift()
        slide.move(1, select = true)
        slide.releaseShift()
        slide.pressShift()
        assertFalse(slide.releaseShift())
    }
}
