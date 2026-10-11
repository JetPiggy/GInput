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
}
