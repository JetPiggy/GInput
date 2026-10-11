package com.jetpiggy.ginput.clipboard

import com.jetpiggy.ginput.clipboard.SavedPhrases.Companion.MAX_PHRASES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedPhrasesTest {

    private fun phrases(vararg items: String) = SavedPhrases(items.toList())

    @Test
    fun newPhrasesGoFirstAndExistingOnesMoveToTheFront() {
        val saved = SavedPhrases().add("一").add("二").add("三").add("一")
        assertEquals(listOf("一", "三", "二"), saved.items)
    }

    @Test
    fun blankAndVeryLongTextIsNotSaved() {
        val saved = phrases("一")
        assertSame(saved, saved.add(" \n "))
        assertSame(saved, saved.add("字".repeat(SavedPhrases.MAX_LENGTH + 1)))
    }

    @Test
    fun atMostOneHundredPhrases() {
        val full = SavedPhrases((1..MAX_PHRASES).map { "$it" })
        assertTrue(full.full)
        assertSame(full, full.add("新"))
        // 已經有的仍然可以移到最前。
        assertEquals("50", full.add("50").items.first())
        assertFalse(full.remove("1").full)
        assertEquals("新", full.remove("1").add("新").items.first())
    }

    @Test
    fun editingKeepsThePositionAndMergesDuplicates() {
        assertEquals(listOf("一", "貳", "三"), phrases("一", "二", "三").edit("二", "貳").items)
        assertEquals(listOf("一", "二"), phrases("一", "二", "三").edit("三", "二").items)
        val saved = phrases("一")
        assertSame(saved, saved.edit("一", "  "))
        assertSame(saved, saved.edit("不在", "二"))
    }

    @Test
    fun movingChangesTheOrder() {
        val saved = phrases("一", "二", "三")
        assertEquals(listOf("二", "一", "三"), saved.move("二", -1).items)
        assertEquals(listOf("一", "三", "二"), saved.move("二", 1).items)
        assertSame(saved, saved.move("一", -1))
        assertSame(saved, saved.move("三", 1))
    }

    @Test
    fun storageKeepsTextExactly() {
        val saved = phrases("地址：\n九龍\t旺角 \"一號\"", "😀 emoji", "[]")
        assertEquals(saved, SavedPhrases.fromJson(saved.toJson()))
    }

    @Test
    fun storedPhrasesFollowTheRules() {
        assertEquals(SavedPhrases(), SavedPhrases.fromJson(null))
        assertEquals(SavedPhrases(), SavedPhrases.fromJson("not json"))
        assertEquals(listOf("一", "二"), SavedPhrases.fromJson("""["一", " ", 5, null, "一", "二"]""").items)
        val tooMany = SavedPhrases.fromJson((1..MAX_PHRASES + 1).joinToString(",", "[", "]") { "\"$it\"" })
        assertEquals(MAX_PHRASES, tooMany.items.size)
    }
}
