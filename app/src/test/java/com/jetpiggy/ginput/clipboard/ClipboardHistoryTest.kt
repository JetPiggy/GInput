package com.jetpiggy.ginput.clipboard

import com.jetpiggy.ginput.clipboard.ClipboardHistory.Companion.KEEP_MILLIS
import com.jetpiggy.ginput.clipboard.ClipboardHistory.Companion.MAX_ITEMS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ClipboardHistoryTest {

    private val ClipboardHistory.texts get() = items.map { it.text }

    @Test
    fun newestFirstAndCopyingAgainMovesToTheFront() {
        val history = ClipboardHistory()
            .add("一", 1_000)
            .add("二", 2_000)
            .add("一", 3_000)
        assertEquals(listOf("一", "二"), history.texts)
        assertEquals(3_000, history.items.first().copiedAt)
        assertEquals(3_000, history.lastSeen)
    }

    @Test
    fun keepsAtMostTwentyItems() {
        val history = (1..MAX_ITEMS + 5).fold(ClipboardHistory()) { history, n -> history.add("$n", n * 1_000L) }
        assertEquals(MAX_ITEMS, history.items.size)
        assertEquals("${MAX_ITEMS + 5}", history.items.first().text)
        assertEquals("6", history.items.last().text)
    }

    @Test
    fun blankAndVeryLongTextIsOnlySeen() {
        val history = ClipboardHistory()
            .add("  \n", 1_000)
            .add("字".repeat(ClipboardHistory.MAX_LENGTH + 1), 2_000)
        assertEquals(emptyList<String>(), history.texts)
        assertEquals(2_000, history.lastSeen)
        assertEquals(listOf("字".repeat(ClipboardHistory.MAX_LENGTH)), history.add("字".repeat(ClipboardHistory.MAX_LENGTH), 3_000).texts)
    }

    @Test
    fun seenCopiesAreNotRecorded() {
        val history = ClipboardHistory().add("一", 1_000).seen(5_000).seen(4_000)
        assertEquals(listOf("一"), history.texts)
        assertEquals(5_000, history.lastSeen)
    }

    @Test
    fun itemsExpireAnHourAfterCopying() {
        val history = ClipboardHistory().add("舊", 0).add("新", 10_000)
        assertEquals(KEEP_MILLIS, history.nextExpiry)
        assertSame(history, history.fresh(KEEP_MILLIS - 1))
        val fresh = history.fresh(KEEP_MILLIS)
        assertEquals(listOf("新"), fresh.texts)
        assertEquals(10_000 + KEEP_MILLIS, fresh.nextExpiry)
        assertNull(fresh.fresh(10_000 + KEEP_MILLIS).nextExpiry)
        // 時鐘向後調了超過一小時，也當作過期。
        assertEquals(emptyList<String>(), history.fresh(-KEEP_MILLIS).texts)
    }

    @Test
    fun removingDoesNotForgetWhatWasSeen() {
        val history = ClipboardHistory().add("一", 1_000).add("二", 2_000).remove("二")
        assertEquals(listOf("一"), history.texts)
        assertEquals(2_000, history.lastSeen)
    }

    @Test
    fun storageKeepsTextExactly() {
        val history = ClipboardHistory()
            .add("地址：\n九龍\t旺角 \"一號\"", 1_000)
            .add("😀 emoji", 2_000)
            .seen(3_000)
        assertEquals(history, ClipboardHistory.fromJson(history.toJson()))
    }

    @Test
    fun storedItemsFollowTheRules() {
        val json = """
            {"lastSeen": 9000, "items": [
                {"text": "一", "copiedAt": 1000},
                {"text": "一", "copiedAt": 3000},
                {"text": "   ", "copiedAt": 2000},
                {"text": 5, "copiedAt": 2000},
                {"copiedAt": 2000},
                {"text": "二", "copiedAt": 2000}
            ]}
        """
        val history = ClipboardHistory.fromJson(json)
        assertEquals(listOf("一", "二"), history.texts)
        assertEquals(3_000, history.items.first().copiedAt)
        assertEquals(9_000, history.lastSeen)
    }
}
