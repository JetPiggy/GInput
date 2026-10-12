package com.jetpiggy.ginput.usagestats

import com.jetpiggy.ginput.settings.KeyboardSettings
import com.jetpiggy.ginput.settings.SettingsPreferences
import com.jetpiggy.ginput.settings.UserSettings
import java.io.File
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageStatsTest {

    private val today = LocalDate.of(2026, 10, 10)

    @Test
    fun onlyFinishedDaysAreSentAndOldOnesAreDropped() {
        val counts = UsageCounts()
        counts.record(today.minusDays(8), UsageEvent.Chinese)
        counts.record(today.minusDays(7), UsageEvent.Emoji)
        counts.record(today.minusDays(1), UsageEvent.Chinese, times = 3)
        counts.record(today, UsageEvent.English)

        val finished = counts.finishedDays(today)

        assertEquals(listOf(today.minusDays(7), today.minusDays(1)), finished.map { it.first })
        assertEquals(mapOf(UsageEvent.Chinese to 3), finished.last().second)
        counts.remove(today.minusDays(7))
        counts.remove(today.minusDays(1))
        assertTrue(counts.finishedDays(today).isEmpty())
        assertFalse(counts.isEmpty())
    }

    @Test
    fun countsSurviveTheFileAndSkipBrokenLines() {
        val counts = UsageCounts()
        counts.record(today, UsageEvent.Chinese, times = 2)
        counts.record(today, UsageEvent.CursorSwipe)
        val text = counts.format() + "垃圾\n2026-13-01\tchinese\t1\n2026-10-10\tunknown\t1\n2026-10-10\temoji\t-1\n"

        val loaded = UsageCounts.parse(text)

        assertEquals(counts.format(), loaded.format())
    }

    @Test
    fun countsRecordedBeforeLoadingAreKept() {
        val saved = UsageCounts.parse("2026-10-10\tchinese\t2\n")
        val early = UsageCounts().apply { record(today, UsageEvent.Chinese) }
        saved.addAll(early)
        assertEquals("2026-10-10\tchinese\t3\n", saved.format())
    }

    @Test
    fun reportHasNoIdentifiersOnlyTheDaySettingsAndCounts() {
        val settings = UserSettings(
            continuousTyping = true,
            keyboard = KeyboardSettings(numberRow = false),
            usageStats = true,
            setupDone = true,
            whatsNewSeen = 8,
        )
        val json = UsageReport.json(
            day = today.minusDays(1),
            counts = mapOf(UsageEvent.Chinese to 12, UsageEvent.Emoji to 0, UsageEvent.EnglishSuggestion to 3),
            settings = SettingsPreferences.forUsageStats(settings),
            appVersion = "0.6.0",
            sdk = 34,
        )

        val report = JSONObject(json)
        assertEquals(setOf("schema", "day", "app", "sdk", "settings", "counts"), report.keySet())
        assertEquals(1, report.getInt("schema"))
        assertEquals("2026-10-09", report.getString("day"))
        assertEquals("0.6.0", report.getString("app"))
        assertEquals(34, report.getInt("sdk"))
        val reportedSettings = report.getJSONObject("settings")
        assertEquals("quick", reportedSettings.getString("schemes_enabled"))
        assertFalse(reportedSettings.getBoolean("number_row"))
        assertFalse(reportedSettings.getBoolean("emoji_keywords"))
        assertTrue(reportedSettings.getBoolean("auto_capitalize"))
        assertTrue(reportedSettings.getBoolean("continuous_typing"))
        assertTrue(reportedSettings.getBoolean("clipboard_history"))
        assertFalse(reportedSettings.has("usage_stats"))
        assertFalse(reportedSettings.has("crash_reports"))
        assertFalse(reportedSettings.has("setup_done"))
        assertFalse(reportedSettings.has("whats_new_seen"))
        val counts = report.getJSONObject("counts")
        assertEquals(setOf("chinese", "englishSuggestion"), counts.keySet())
        assertEquals(12, counts.getInt("chinese"))
    }

    /** backend/usage-stats 的 src/report.ts 要接受 App 送出的全部設定和次數（它的 README「改動報告格式」）。 */
    @Test
    fun workerAcceptsEverySettingAndCount() {
        val source = File("../backend/usage-stats/src/report.ts").readText()

        /** `export const name ... = [ ... ];` 或 `= { ... };` 括號裏的內容。 */
        fun block(name: String, open: Char, close: Char): String {
            val start = source.indexOf("= $open", source.indexOf("export const $name")) + 2
            return source.substring(start, source.indexOf("$close;", start))
        }

        val counts = Regex(""""(\w+)"""").findAll(block("COUNTS", '[', ']')).map { it.groupValues[1] }.toSet()
        assertEquals(UsageEvent.entries.map { it.code }.toSet(), counts)
        val settings = Regex("""^\s*(\w+):""", RegexOption.MULTILINE)
            .findAll(block("SETTINGS", '{', '}'))
            .map { it.groupValues[1] }
            .toSet()
        assertEquals(SettingsPreferences.forUsageStats(UserSettings()).keys, settings)
    }
}
