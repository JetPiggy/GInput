package com.jetpiggy.ginput.cloudtext

import com.jetpiggy.ginput.cloudtext.SourceText.Companion.MAX_LENGTH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceTextTest {

    /** 假的文字框：content 是整段文字，[start, end) 是選取範圍；start 等於 end 就只是游標。 */
    private class FakeField(var content: String, var start: Int = content.length, var end: Int = start) : SourceField {
        var selectionReadable = true

        override fun selectedText(): String? =
            if (selectionReadable && start != end) content.substring(start, end) else null

        override fun textBeforeCursor(length: Int): String = content.substring(maxOf(0, start - length), start)

        override fun textAfterCursor(length: Int): String = content.substring(end, minOf(content.length, end + length))

        override fun replaceSelection(text: String) = replace(start, end, text)

        override fun replaceAroundCursor(before: Int, after: Int, text: String) = replace(start - before, end + after, text)

        private fun replace(from: Int, to: Int, text: String) {
            content = content.substring(0, from) + text + content.substring(to)
            start = from + text.length
            end = start
        }

        fun select(text: String) {
            start = content.indexOf(text)
            end = start + text.length
        }
    }

    private fun found(read: SourceRead): SourceText = (read as SourceRead.Found).source

    @Test
    fun wholeFieldWhenNothingIsSelected() {
        val field = FakeField(" 今日好熱，\n去游水啦 ", start = 4)
        val source = found(SourceText.read(field, hasSelection = false))
        assertEquals(SourceText(" 今日好熱，\n去游水啦 ", selection = false, before = 4, after = 8), source)
        assertEquals("今日好熱，\n去游水啦", source.trimmed)
    }

    @Test
    fun selectedTextWhenSomethingIsSelected() {
        val field = FakeField("今日好熱，去游水啦")
        field.select("去游水啦")
        assertEquals(SourceText("去游水啦", selection = true), found(SourceText.read(field, hasSelection = true)))
    }

    @Test
    fun unreadableSelectionIsNotTreatedAsTheWholeField() {
        val field = FakeField("今日好熱，去游水啦")
        field.select("去游水啦")
        field.selectionReadable = false
        assertEquals(SourceRead.Unreadable, SourceText.read(field, hasSelection = true))
    }

    @Test
    fun blankTextIsNotSent() {
        assertEquals(SourceRead.Empty, SourceText.read(FakeField(""), hasSelection = false))
        assertEquals(SourceRead.Empty, SourceText.read(FakeField(" \n "), hasSelection = false))
        val field = FakeField("今日   好熱")
        field.select("   ")
        assertEquals(SourceRead.Empty, SourceText.read(field, hasSelection = true))
    }

    @Test
    fun atMostAThousandCharacters() {
        assertEquals(1000, MAX_LENGTH)
        val longest = "字".repeat(MAX_LENGTH)
        assertEquals(longest, found(SourceText.read(FakeField(longest, start = 600), hasSelection = false)).text)
        assertEquals(SourceRead.TooLong, SourceText.read(FakeField(longest + "字", start = 600), hasSelection = false))
        val field = FakeField("$longest。")
        field.select("$longest。")
        assertEquals(SourceRead.TooLong, SourceText.read(field, hasSelection = true))
    }

    @Test
    fun replacingTheWholeFieldKeepsItsSurroundingWhitespace() {
        val field = FakeField(" 你去左邊度？\n")
        val source = found(SourceText.read(field, hasSelection = false))
        assertTrue(SourceText.replace(field, source, "你去咗邊度？", hasSelection = false))
        assertEquals(" 你去咗邊度？\n", field.content)
    }

    @Test
    fun replacingASelectionLeavesTheRestAlone() {
        val field = FakeField("今日好熱，我哋去游水。")
        field.select("我哋去游水")
        val source = found(SourceText.read(field, hasSelection = true))
        assertTrue(SourceText.replace(field, source, "我們去游泳", hasSelection = true))
        assertEquals("今日好熱，我們去游泳。", field.content)
    }

    @Test
    fun theCursorMayMoveBeforeReplacingTheWholeField() {
        val field = FakeField("佢既電話壞咗")
        val source = found(SourceText.read(field, hasSelection = false))
        field.start = 2
        field.end = 2
        assertTrue(SourceText.replace(field, source, "佢嘅電話壞咗", hasSelection = false))
        assertEquals("佢嘅電話壞咗", field.content)
    }

    @Test
    fun changedTextIsNotReplaced() {
        val field = FakeField("今日好熱")
        val source = found(SourceText.read(field, hasSelection = false))
        field.content = "今日好熱！"
        field.start = field.content.length
        field.end = field.start
        assertFalse(SourceText.replace(field, source, "It's hot today.", hasSelection = false))
        assertEquals("今日好熱！", field.content)
    }

    @Test
    fun changedSelectionIsNotReplaced() {
        val field = FakeField("今日好熱，我哋去游水。")
        field.select("我哋去游水")
        val source = found(SourceText.read(field, hasSelection = true))
        field.select("今日好熱")
        assertFalse(SourceText.replace(field, source, "我們去游泳", hasSelection = true))
        // 取消了選取，原文就變成整個文字框，也不取代。
        field.start = field.content.length
        field.end = field.start
        assertFalse(SourceText.replace(field, source, "我們去游泳", hasSelection = false))
        assertEquals("今日好熱，我哋去游水。", field.content)
    }
}
