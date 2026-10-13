package com.jetpiggy.ginput.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateStateTest {

    private val newVersion = """{"versionCode": 9, "versionName": "0.11.1", "pageUrl": "https://ginput.jetpiggy.com/"}"""
    private val withNotice = """
        {"versionCode": 8, "versionName": "0.11.0", "pageUrl": "https://ginput.jetpiggy.com/",
         "notice": {"id": "n1", "zh-Hant": "公告", "en": "Notice"}}
    """.trimIndent()

    @Test
    fun aNewVersionIsUnseenUntilTheSettingsPageShowsIt() {
        val state = UpdateState().checked(newVersion, now = 1)
        assertTrue(state.unseen(currentVersionCode = 8))
        val seen = state.seen()
        assertFalse(seen.unseen(currentVersionCode = 8))
        // 看過了，設定頁仍然顯示，直到更新為止。
        assertEquals("0.11.1", seen.update(currentVersionCode = 8, chinese = true)?.versionName)
        assertNull(seen.update(currentVersionCode = 9, chinese = true))
        assertFalse(state.unseen(currentVersionCode = 9))
    }

    @Test
    fun aNewerVersionAfterTheSeenOneIsUnseenAgain() {
        val seen = UpdateState().checked(newVersion, now = 1).seen()
        val newer = newVersion.replace("\"versionCode\": 9", "\"versionCode\": 10")
        assertTrue(seen.checked(newer, now = 2).unseen(currentVersionCode = 8))
    }

    @Test
    fun aNoticeStaysUntilDismissedButIsSeenOnce() {
        val state = UpdateState().checked(withNotice, now = 1)
        assertTrue(state.unseen(currentVersionCode = 8))
        val seen = state.seen()
        assertFalse(seen.unseen(currentVersionCode = 8))
        assertEquals("公告", seen.notice(chinese = true)?.text)
        val dismissed = seen.dismissed("n1")
        assertNull(dismissed.notice(chinese = true))
        assertFalse(dismissed.unseen(currentVersionCode = 8))
        val next = withNotice.replace("\"n1\"", "\"n2\"")
        assertTrue(dismissed.checked(next, now = 2).unseen(currentVersionCode = 8))
    }

    @Test
    fun checksAreDueADayAfterTheLastOneOrWhenTheClockGoesBack() {
        val day = UpdateState.DAY_MILLIS
        val state = UpdateState().checked(newVersion, now = 10 * day)
        assertFalse(state.due(10 * day + day - 1))
        assertTrue(state.due(11 * day))
        assertTrue(state.due(9 * day))
        assertTrue(UpdateState().due(1))
    }

    @Test
    fun stateSurvivesSavingAndLoading() {
        val state = UpdateState().checked(withNotice, now = 123).seen().dismissed("n1")
        assertEquals(state, UpdateState.parse(state.format()))
        assertEquals(UpdateState(), UpdateState.parse(UpdateState().format()))
    }
}
