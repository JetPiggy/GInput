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
        assertTrue(settings.typing.smartSpaceRemoval)
        assertTrue(settings.typing.autoCapitalize)
        assertFalse(settings.typing.engine.continuousTyping)
        assertFalse(settings.typing.emojiKeywords)
        assertTrue(settings.clipboardHistory)
        assertTrue(settings.crashReports)
        assertFalse(settings.usageStats)
        assertFalse(settings.cloudNoticeAccepted)
        assertEquals(KeyboardTheme.Default, settings.keyboard.theme)
        assertTrue(settings.keyboard.numberRow)
        assertTrue(settings.keyboard.keyPreview)
        assertEquals(KeyVibration.System, settings.keyboard.vibration)
        assertFalse(settings.keyboard.keySound)
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
            colloquialPriority = false,
            spaceMode = SpaceMode.SelectChinese,
            smartSpaceRemoval = false,
            autoCapitalize = false,
            emojiKeywords = true,
            clipboardHistory = false,
            keyboard = KeyboardSettings(
                theme = KeyboardTheme.MilkTea,
                height = KeyboardHeight.Tall,
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
        assertFalse(changed.typing.autoCapitalize)
    }

    @Test
    fun unknownValuesFallBackToDefaults() {
        val stored = mapOf(
            "space_mode" to "sideways",
            "number_row" to "yes",
            "cangjie_generations" to "fourth",
            "schemes_enabled" to "stroke",
            "keyboard_theme" to "rainbow",
            "key_vibration" to "buzz",
        )
        assertEquals(UserSettings(), SettingsPreferences.read(stored))
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
