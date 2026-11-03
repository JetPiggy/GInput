package com.jetpiggy.ginput.clipboard

import com.jetpiggy.ginput.clipboard.ClipboardHistory.Companion.MAX_ITEMS
import com.jetpiggy.ginput.settings.ClipboardRetention
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun itemsExpireAfterTheChosenTime() {
        val hour = ClipboardRetention.Hour.millis!!
        val history = ClipboardHistory().add("舊", 0).add("新", 10_000)
        assertEquals(hour, history.nextExpiry(hour))
        assertSame(history, history.fresh(hour - 1, hour))
        val fresh = history.fresh(hour, hour)
        assertEquals(listOf("新"), fresh.texts)
        assertEquals(10_000 + hour, fresh.nextExpiry(hour))
        assertNull(fresh.fresh(10_000 + hour, hour).nextExpiry(hour))
        // 時鐘向後調了超過保留時間，也當作過期。
        assertEquals(emptyList<String>(), history.fresh(-hour, hour).texts)
        // 保留 7 日的話，一小時後仍在。
        val week = ClipboardRetention.Week.millis!!
        assertSame(history, history.fresh(hour, week))
        assertEquals(week, history.nextExpiry(week))
    }

    @Test
    fun unlimitedItemsNeverExpire() {
        val history = ClipboardHistory().add("舊", 0)
        assertSame(history, history.fresh(Long.MAX_VALUE, keepMillis = null))
        assertNull(history.nextExpiry(keepMillis = null))
        assertFalse(ClipboardHistory.expired(0, Long.MAX_VALUE, keepMillis = null))
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
