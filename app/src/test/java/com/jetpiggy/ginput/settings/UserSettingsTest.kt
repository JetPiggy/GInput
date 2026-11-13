package com.jetpiggy.ginput.settings

import com.jetpiggy.ginput.engine.CangjieGeneration
import com.jetpiggy.ginput.engine.Scheme
import com.jetpiggy.ginput.engine.SharedCodeOrder
import com.jetpiggy.ginput.typing.SpaceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserSettingsTest {

    @Test
    fun defaultsFollowTheSpec() {
        val settings = SettingsPreferences.read(emptyMap<String, Any>())
        assertEquals(UserSettings(), settings)
        assertEquals(listOf(Scheme.Quick), settings.typing.engine.schemes)
        assertEquals(SpaceMode.WriteEnglish, settings.typing.spaceMode)
        assertEquals(SplitSpaceOrder.ChineseLeft, settings.splitSpaceOrder)
        assertEquals(null, settings.keyboardOnScreen.splitSpace)
        assertTrue(settings.typing.smartSpaceRemoval)
        assertTrue(settings.typing.autoCapitalize)
        assertFalse(settings.typing.engine.continuousTyping)
        // 工具列預設有「手寫」，但未同意說明，不可以用 ML Kit（ADR 0015）。
        assertTrue(settings.handwriting)
        assertFalse(settings.handwritingAccepted)
        assertFalse(settings.handwritingActive)
        assertFalse(settings.typing.emojiKeywords)
        assertTrue(settings.typing.englishChinese)
        assertTrue(settings.clipboardHistory)
        assertEquals(ClipboardRetention.Hour, settings.clipboardRetention)
        assertTrue(settings.crashReports)
        assertFalse(settings.usageStats)
        assertFalse(settings.cloudNoticeAccepted)
        assertEquals(KeyboardTheme.Default, settings.keyboard.theme)
        assertEquals(ThemeMode.System, settings.keyboard.themeMode)
        assertTrue(settings.keyboard.numberRow)
        assertTrue(settings.keyboard.keyPreview)
        assertEquals(KeyVibration.System, settings.keyboard.vibration)
        assertFalse(settings.keyboard.keySound)
        assertEquals(KeyboardHeight(portrait = 100, landscape = 100), settings.keyboard.height)
        assertFalse(settings.setupDone)
        assertEquals(0, settings.whatsNewSeen)
    }

    @Test
    fun everySettingSurvivesStorage() {
        val changed = UserSettings(
            schemeOrder = listOf(Scheme.Cangjie, Scheme.Jyutping, Scheme.Quick),
            enabledSchemes = setOf(Scheme.Quick, Scheme.Jyutping),
            cangjieGenerations = setOf(CangjieGeneration.Fifth),
            sharedCodeOrder = SharedCodeOrder.Windows,
            rememberCandidates = false,
            looseSpelling = false,
            continuousTyping = true,
            handwriting = false,
            handwritingAccepted = true,
            colloquialPriority = false,
            spaceMode = SpaceMode.Split,
            splitSpaceOrder = SplitSpaceOrder.EnglishLeft,
            smartSpaceRemoval = false,
            autoCapitalize = false,
            emojiKeywords = true,
            englishChinese = false,
            clipboardHistory = false,
            clipboardRetention = ClipboardRetention.Week,
            keyboard = KeyboardSettings(
                theme = KeyboardTheme.MilkTea,
                themeMode = ThemeMode.Dark,
                height = KeyboardHeight(portrait = 110, landscape = 85),
                labels = KeyLabels.RadicalsLarge,
                numberRow = false,
                keyPreview = false,
                vibration = KeyVibration.Strong,
                keySound = true,
            ),
            crashReports = false,
            usageStats = true,
            cloudNoticeAccepted = true,
            setupDone = true,
            whatsNewSeen = 8,
        )
        assertEquals(changed, SettingsPreferences.read(SettingsPreferences.write(changed)))
        assertEquals(listOf(Scheme.Jyutping, Scheme.Quick), changed.typing.engine.schemes)
        assertFalse(changed.typing.engine.looseSpelling)
        assertTrue(changed.typing.engine.continuousTyping)
        assertTrue(changed.typing.emojiKeywords)
        assertFalse(changed.typing.englishChinese)
        assertFalse(changed.typing.autoCapitalize)
    }

    @Test
    fun theSpaceKeyIsSplitOnlyInSplitMode() {
        val split = UserSettings(spaceMode = SpaceMode.Split, splitSpaceOrder = SplitSpaceOrder.EnglishLeft)
        assertEquals(SplitSpaceOrder.EnglishLeft, split.keyboardOnScreen.splitSpace)
        assertEquals(SpaceMode.Split, split.typing.spaceMode)
        // 轉去其他模式，空白鍵不再分邊，但記住分邊次序。
        val other = split.copy(spaceMode = SpaceMode.SelectChinese)
        assertEquals(null, other.keyboardOnScreen.splitSpace)
        assertEquals(SplitSpaceOrder.EnglishLeft, other.splitSpaceOrder)
    }

    @Test
    fun unknownValuesFallBackToDefaults() {
        val stored = mapOf(
            "space_mode" to "sideways",
            "space_split_order" to "middle",
            "number_row" to "yes",
            "cangjie_generations" to "fourth",
            "schemes_enabled" to "stroke",
            "keyboard_theme" to "rainbow",
            "theme_mode" to "dim",
            "key_vibration" to "buzz",
            "clipboard_retention" to "forever",
        )
        assertEquals(UserSettings(), SettingsPreferences.read(stored))
    }

    @Test
    fun everyThemeSurvivesStorage() {
        for (theme in KeyboardTheme.entries) {
            val settings = UserSettings(keyboard = KeyboardSettings(theme = theme))
            assertEquals(theme, SettingsPreferences.read(SettingsPreferences.write(settings)).keyboard.theme)
        }
    }

    @Test
    fun themeModeFollowsTheSystemUnlessFixed() {
        assertTrue(ThemeMode.System.dark(systemDark = true))
        assertFalse(ThemeMode.System.dark(systemDark = false))
        assertFalse(ThemeMode.Light.dark(systemDark = true))
        assertTrue(ThemeMode.Dark.dark(systemDark = false))
        for (mode in ThemeMode.entries) {
            val settings = UserSettings(keyboard = KeyboardSettings(themeMode = mode))
            assertEquals(mode, SettingsPreferences.read(SettingsPreferences.write(settings)).keyboard.themeMode)
        }
        // 主題連深淺都不放進使用統計；手寫也不放。
        val handwriting = SettingsPreferences.forUsageStats(UserSettings(handwriting = true, handwritingAccepted = true))
        assertFalse(handwriting.keys.any { it.startsWith("handwriting") })
        assertFalse("theme_mode" in SettingsPreferences.forUsageStats(UserSettings()))
    }

    @Test
    fun everyClipboardRetentionSurvivesStorage() {
        for (retention in ClipboardRetention.entries) {
            val settings = UserSettings(clipboardRetention = retention)
            assertEquals(retention, SettingsPreferences.read(SettingsPreferences.write(settings)).clipboardRetention)
        }
        assertEquals(listOf(1L, 24L, 168L, null), ClipboardRetention.entries.map { it.millis?.div(60 * 60 * 1000L) })
    }

    @Test
    fun themesOfASeriesStayTogether() {
        // 主題頁按 KeyboardTheme 的次序分段，同一系列要排在一起，系列的次序和 ThemeSeries 一樣。
        assertEquals(ThemeSeries.entries, KeyboardTheme.entries.map { it.series }.distinct())
        assertEquals(KeyboardTheme.entries.sortedBy { it.series }, KeyboardTheme.entries)
    }

    @Test
    fun oldKeyboardHeightBecomesThePercentageForBothOrientations() {
        val expected = mapOf("compact" to 85, "standard" to 100, "tall" to 115, "huge" to 100)
        for ((code, percent) in expected) {
            val height = SettingsPreferences.read(mapOf("keyboard_height" to code)).keyboard.height
            assertEquals(code, KeyboardHeight(portrait = percent, landscape = percent), height)
        }
        // 已經存了百分比的方向不再理會舊的設定。
        val stored = mapOf("keyboard_height" to "tall", "keyboard_height_portrait" to "90")
        assertEquals(KeyboardHeight(portrait = 90, landscape = 115), SettingsPreferences.read(stored).keyboard.height)
    }

    @Test
    fun keyboardHeightIsSnappedToAStepWithinRange() {
        val stored = mapOf("keyboard_height_portrait" to "200", "keyboard_height_landscape" to "93")
        assertEquals(KeyboardHeight(portrait = 125, landscape = 95), SettingsPreferences.read(stored).keyboard.height)
        assertEquals(80, KeyboardHeight.nearest(40f))
        assertEquals(105, KeyboardHeight.nearest(103f))
    }

    @Test
    fun usageStatsSendBothKeyboardHeightsAsText() {
        val settings = UserSettings(keyboard = KeyboardSettings(height = KeyboardHeight(portrait = 110, landscape = 90)))
        val stats = SettingsPreferences.forUsageStats(settings)
        assertEquals("110", stats["keyboard_height_portrait"])
        assertEquals("90", stats["keyboard_height_landscape"])
        assertFalse("keyboard_height" in stats)
    }

    @Test
    fun keyboardHeightIsKeptPerOrientation() {
        val height = KeyboardHeight().withPercent(landscape = true, percent = 85)
        assertEquals(100, height.percent(landscape = false))
        assertEquals(85, height.percent(landscape = true))
    }

    @Test
    fun schemeOrderKeepsKnownSchemesAndAddsMissingOnes() {
        // 舊版只有速成和倉頡；粵拼補在最後。
        val settings = SettingsPreferences.read(mapOf("scheme_order" to "cangjie,pinyin,cangjie"))
        assertEquals(listOf(Scheme.Cangjie, Scheme.Quick, Scheme.Jyutping), settings.schemeOrder)
    }

    @Test
    fun theLastEnabledSchemeCannotBeTurnedOff() {
        val quickOnly = UserSettings()
        assertEquals(quickOnly, quickOnly.withSchemeEnabled(Scheme.Quick, false))
        val both = quickOnly.withSchemeEnabled(Scheme.Cangjie, true)
        assertEquals(setOf(Scheme.Quick, Scheme.Cangjie), both.enabledSchemes)
        assertEquals(setOf(Scheme.Cangjie), both.withSchemeEnabled(Scheme.Quick, false).enabledSchemes)
    }

    @Test
    fun movingASchemeChangesTheCandidateOrder() {
        val both = UserSettings(enabledSchemes = setOf(Scheme.Quick, Scheme.Cangjie))
        val moved = both.withSchemeMoved(Scheme.Cangjie, -1)
        assertEquals(listOf(Scheme.Cangjie, Scheme.Quick), moved.typing.engine.schemes)
        assertEquals(moved, moved.withSchemeMoved(Scheme.Cangjie, -1))
    }

    @Test
    fun setupChoiceEnablesOnlyThatScheme() {
        val cangjie = UserSettings(enabledSchemes = setOf(Scheme.Quick, Scheme.Cangjie)).withOnlyScheme(Scheme.Cangjie)
        assertEquals(listOf(Scheme.Cangjie), cangjie.typing.engine.schemes)
        assertEquals(listOf(Scheme.Cangjie, Scheme.Quick, Scheme.Jyutping), cangjie.schemeOrder)
        val jyutping = UserSettings().withOnlyScheme(Scheme.Jyutping)
        assertEquals(listOf(Scheme.Jyutping), jyutping.typing.engine.schemes)
        assertTrue(jyutping.typing.engine.looseSpelling)
    }

    @Test
    fun radicalsAreHiddenOnlyWhenJyutpingIsTheOnlyScheme() {
        assertTrue(UserSettings().keyboardOnScreen.radicals)
        assertTrue(UserSettings(enabledSchemes = setOf(Scheme.Cangjie, Scheme.Jyutping)).keyboardOnScreen.radicals)
        val jyutping = UserSettings(enabledSchemes = setOf(Scheme.Jyutping))
        assertFalse(jyutping.keyboardOnScreen.radicals)
        // 只影響畫面，儲存的設定不變。
        assertEquals(UserSettings().keyboard, jyutping.keyboard)
    }
}
