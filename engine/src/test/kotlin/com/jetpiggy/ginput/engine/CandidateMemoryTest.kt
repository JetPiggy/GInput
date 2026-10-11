package com.jetpiggy.ginput.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateMemoryTest {

    private fun entry(character: String, frequency: Int) =
        CharacterEntry(character, CangjieGeneration.Third, frequency, windowsRank = 0, colloquial = false)

    private val dictionary = FakeDictionary(
        mapOf((Scheme.Quick to "ab") to listOf(entry("明", 30), entry("晴", 20), entry("暗", 10))),
    )

    private fun texts(memory: Map<String, Int>, settings: EngineSettings = EngineSettings()) =
        CandidateEngine(dictionary, CandidateMemory(memory)).candidates("ab", settings).map { it.text }

    @Test
    fun rememberedCharactersComeFirstAndAreMarked() {
        val candidates = CandidateEngine(dictionary, CandidateMemory(mapOf("暗" to 2))).candidates("ab", EngineSettings())
        assertEquals(listOf("暗", "明", "晴"), candidates.map { it.text })
        assertEquals(listOf(true, false, false), candidates.map { it.remembered })
    }

    @Test
    fun oneSelectionIsNotEnough() {
        assertEquals(listOf("明", "晴", "暗"), texts(mapOf("暗" to 1)))
    }

    @Test
    fun charactersPickedMoreOftenComeFirst() {
        assertEquals(listOf("暗", "晴", "明"), texts(mapOf("晴" to 2, "暗" to 5)))
    }

    @Test
    fun windowsOrderAndTheSettingSwitchIgnoreMemory() {
        val memory = mapOf("暗" to 3)
        assertEquals(listOf("明", "晴", "暗"), texts(memory, EngineSettings(sharedCodeOrder = SharedCodeOrder.Windows)))
        assertEquals(listOf("明", "晴", "暗"), texts(memory, EngineSettings(rememberCandidates = false)))
    }

    @Test
    fun atMostFiveAreMovedToTheFront() {
        val characters = listOf("一", "二", "三", "四", "五", "六", "七")
        val dictionary = FakeDictionary(
            mapOf((Scheme.Quick to "ab") to characters.mapIndexed { index, ch -> entry(ch, 70 - index * 10) }),
        )
        val memory = CandidateMemory(characters.drop(1).withIndex().associate { (index, ch) -> ch to index + 2 })
        val texts = CandidateEngine(dictionary, memory).candidates("ab", EngineSettings()).map { it.text }
        assertEquals(listOf("七", "六", "五", "四", "三", "一", "二"), texts)
    }

    @Test
    fun onlyCandidatesUsingEveryLetterAreMovedToTheFront() {
        val dictionary = FakeDictionary(
            jyutping = listOf(
                JyutpingEntry("你", "nei", 100, colloquial = false),
                JyutpingEntry("你好", "nei hou", 5, colloquial = false),
            ),
        )
        val engine = CandidateEngine(dictionary, CandidateMemory(mapOf("你" to 3)))
        val settings = EngineSettings(schemes = listOf(Scheme.Jyutping))
        assertEquals(listOf("你好", "你"), engine.candidates("neihou", settings).map { it.text })
        assertEquals(listOf(true), engine.candidates("nei", settings).map { it.remembered })
    }

    @Test
    fun recordingAndForgettingNotifyTheStore() {
        val saved = mutableListOf<Map<String, Int>>()
        val memory = CandidateMemory(onChanged = { saved += it })
        memory.record("暗")
        memory.record("暗")
        assertTrue(memory.isRemembered("暗"))
        memory.forget("暗")
        assertFalse(memory.isRemembered("暗"))
        memory.forget("暗")
        assertEquals(listOf(mapOf("暗" to 1), mapOf("暗" to 2), emptyMap<String, Int>()), saved)
    }

    @Test
    fun clearingForgetsEverything() {
        val saved = mutableListOf<Map<String, Int>>()
        val memory = CandidateMemory(mapOf("暗" to 3, "晴" to 2), onChanged = { saved += it })
        memory.clear()
        assertFalse(memory.isRemembered("暗"))
        assertFalse(memory.isRemembered("晴"))
        assertEquals(listOf(emptyMap<String, Int>()), saved)
    }
}
