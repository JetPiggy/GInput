package com.jetpiggy.ginput.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairMemoryTest {

    @Test
    fun recordsNeighboursAndTheCharacterBefore() {
        val memory = PairMemory()
        memory.record(before = "我話你", text = "好嗎")
        assertEquals(1, memory.count("你", "好"))
        assertEquals(1, memory.count("好", "嗎"))
        assertEquals(0, memory.count("話", "你"))
        assertTrue(memory.hasPairsBefore("好"))
        assertFalse(memory.hasPairsBefore("你"))
    }

    @Test
    fun onlyPairsOfChineseCharactersAreRecorded() {
        val saved = mutableListOf<List<LearnedPair>>()
        val memory = PairMemory(onChanged = { saved += it })
        memory.record(before = "ok ", text = "好")
        memory.record(before = "", text = "好，嗎")
        assertTrue(saved.isEmpty())
        assertEquals(0, memory.count("，", "嗎"))
    }

    @Test
    fun charactersOutsideTheBmpCountAsOne() {
        val saved = mutableListOf<List<LearnedPair>>()
        val memory = PairMemory(onChanged = { saved += it })
        memory.record(before = "", text = "𠮶度")
        assertEquals(1, memory.count("𠮶", "度"))
        assertEquals(listOf(LearnedPair("𠮶", "度", 1)), saved.last())
    }

    @Test
    fun repeatedPairsCountUpAndMoveToTheEnd() {
        val saved = mutableListOf<List<LearnedPair>>()
        val memory = PairMemory(onChanged = { saved += it })
        memory.record(before = "", text = "你好")
        memory.record(before = "", text = "早晨")
        memory.record(before = "", text = "你好")
        assertEquals(listOf(LearnedPair("早", "晨", 1), LearnedPair("你", "好", 2)), saved.last())
    }

    @Test
    fun aFullMemoryDropsTheLeastUsedThenTheOldest() {
        val memory = PairMemory(maxPairs = 10)
        memory.record(before = "", text = "你好")
        memory.record(before = "", text = "你好")
        val characters = "一二三四五六七八九十百千"
        for (index in 0 until 10) memory.record(before = "", text = characters.substring(index, index + 2))
        // 11 對超過 10 對，刪到 9 對：次數只有 1 的最舊兩對（一二、二三）先刪。
        assertEquals(2, memory.count("你", "好"))
        assertEquals(0, memory.count("一", "二"))
        assertEquals(0, memory.count("二", "三"))
        assertEquals(1, memory.count("三", "四"))
        assertEquals(1, memory.count("十", "百"))
        assertFalse(memory.hasPairsBefore("二"))
    }

    @Test
    fun loadingSkipsBrokenLinesAndClearSavesNothing() {
        val saved = mutableListOf<List<LearnedPair>>()
        val memory = PairMemory(
            initial = listOf(LearnedPair("你", "好", 3), LearnedPair("ab", "好", 1), LearnedPair("早", "晨", 0)),
            onChanged = { saved += it },
        )
        assertEquals(3, memory.count("你", "好"))
        assertEquals(0, memory.count("早", "晨"))
        memory.clear()
        assertEquals(0, memory.count("你", "好"))
        assertFalse(memory.hasPairsBefore("好"))
        assertEquals(listOf(emptyList<LearnedPair>()), saved)
    }
}
