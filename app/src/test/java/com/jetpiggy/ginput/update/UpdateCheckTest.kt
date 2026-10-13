package com.jetpiggy.ginput.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckTest {

    private val latest = """
        {"versionCode": 5, "versionName": "0.6.0", "pageUrl": "https://ginput.jetpiggy.com/",
         "notes": {"zh-Hant": "改善英文建議", "en": "Better English suggestions"}}
    """.trimIndent()

    @Test
    fun newerVersionIsOfferedInTheUsersLanguage() {
        assertEquals(
            AvailableUpdate("0.6.0", "https://ginput.jetpiggy.com/", "改善英文建議"),
            UpdateCheck.parse(latest, currentVersionCode = 4, chinese = true),
        )
        assertEquals("Better English suggestions", UpdateCheck.parse(latest, 4, chinese = false)?.notes)
    }

    @Test
    fun sameOrOlderVersionIsNotOffered() {
        assertNull(UpdateCheck.parse(latest, currentVersionCode = 5, chinese = true))
        assertNull(UpdateCheck.parse(latest, currentVersionCode = 6, chinese = true))
    }

    @Test
    fun onlyHttpsDownloadPagesAreOpened() {
        val insecure = latest.replace("https://ginput.jetpiggy.com/", "http://example.com/")
        assertNull(UpdateCheck.parse(insecure, currentVersionCode = 4, chinese = true))
    }

    @Test
    fun notesAreOptional() {
        val bare = """{"versionCode": 5, "versionName": "0.6.0", "pageUrl": "https://ginput.jetpiggy.com/"}"""
        assertEquals(AvailableUpdate("0.6.0", "https://ginput.jetpiggy.com/", null), UpdateCheck.parse(bare, 4, true))
    }

    @Test
    fun brokenJsonIsNoUpdate() {
        assertNull(UpdateCheck.parse("<html>", currentVersionCode = 4, chinese = true))
        assertNull(UpdateCheck.announcement("<html>", chinese = true))
        assertEquals(0, UpdateCheck.versionCode("<html>"))
    }

    @Test
    fun announcementIsInTheUsersLanguageWithAnOptionalHttpsLink() {
        val withNotice = """
            {"versionCode": 5, "versionName": "0.6.0", "pageUrl": "https://ginput.jetpiggy.com/",
             "notice": {"id": "a1", "zh-Hant": "翻譯暫停", "en": "Translate is paused", "url": "https://ginput.jetpiggy.com/"}}
        """.trimIndent()
        assertEquals(Announcement("a1", "翻譯暫停", "https://ginput.jetpiggy.com/"), UpdateCheck.announcement(withNotice, true))
        assertEquals("Translate is paused", UpdateCheck.announcement(withNotice, false)?.text)
        val insecure = withNotice.replace("https://ginput.jetpiggy.com/\"}", "http://example.com/\"}")
        assertEquals(Announcement("a1", "翻譯暫停", null), UpdateCheck.announcement(insecure, true))
    }

    @Test
    fun announcementNeedsAnIdAndText() {
        assertNull(UpdateCheck.announcement(latest, chinese = true))
        assertNull(UpdateCheck.announcement("""{"notice": {"zh-Hant": "沒有編號"}}""", chinese = true))
        assertNull(UpdateCheck.announcement("""{"notice": {"id": "a1"}}""", chinese = true))
        assertEquals("只有中文", UpdateCheck.announcement("""{"notice": {"id": "a1", "zh-Hant": "只有中文"}}""", false)?.text)
    }
}
