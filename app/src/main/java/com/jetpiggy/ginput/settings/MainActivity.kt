package com.jetpiggy.ginput.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jetpiggy.ginput.clipboard.PhraseStore
import com.jetpiggy.ginput.ui.theme.GInputTheme

/** 設定 App：未完成首次設定精靈就先顯示精靈，之後是設定主頁和各分類的子頁。 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = SettingsStore.get(this)
        val phrases = PhraseStore.get(this)
        val start = if (intent.getBooleanExtra(EXTRA_PHRASES, false)) Screen.Phrases else Screen.Settings
        setContent {
            GInputTheme {
                SettingsApp(store, phrases, start)
            }
        }
    }

    companion object {
        private const val EXTRA_PHRASES = "com.jetpiggy.ginput.PHRASES"

        /** 鍵盤剪貼簿面板的「管理」：直接打開常用短語頁。 */
        fun phrasesIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_PHRASES, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}

private enum class Screen {
    Settings, Schemes, Typing, Appearance, Clipboard, Phrases, Privacy, About, Licenses, Notice;

    /** 按返回回到的一頁。 */
    val parent: Screen
        get() = when (this) {
            Phrases -> Clipboard
            Licenses -> About
            Notice -> Licenses
            else -> Settings
        }
}

private fun SettingsCategory.screen(): Screen = when (this) {
    SettingsCategory.Schemes -> Screen.Schemes
    SettingsCategory.Typing -> Screen.Typing
    SettingsCategory.Appearance -> Screen.Appearance
    SettingsCategory.Clipboard -> Screen.Clipboard
    SettingsCategory.Privacy -> Screen.Privacy
    SettingsCategory.About -> Screen.About
}

@Composable
private fun SettingsApp(store: SettingsStore, phrases: PhraseStore, start: Screen) {
    val settings by store.settings.collectAsStateWithLifecycle()
    val savedPhrases by phrases.phrases.collectAsStateWithLifecycle()
    if (!settings.setupDone) {
        SetupWizard(settings, store::update, onFinish = { store.update { it.copy(setupDone = true) } })
        return
    }
    var screen by rememberSaveable { mutableStateOf(start) }
    var notice by rememberSaveable { mutableIntStateOf(0) }
    // 每頁的捲動位置等狀態分開保存，由子頁返回主頁時仍在原來的位置。
    val pageStates = rememberSaveableStateHolder()
    val back = { screen = screen.parent }
    BackHandler(enabled = screen != Screen.Settings, onBack = back)
    pageStates.SaveableStateProvider(screen.name) {
        when (screen) {
            Screen.Settings -> SettingsScreen(settings, savedPhrases.items.size, onOpen = { screen = it.screen() })
            Screen.Schemes -> SchemesPage(settings, store::update, back)
            Screen.Typing -> TypingPage(settings, store::update, back)
            Screen.Appearance -> AppearancePage(
                settings.keyboardOnScreen,
                onChange = { transform -> store.update { it.copy(keyboard = transform(it.keyboard)) } },
                onBack = back,
            )
            Screen.Clipboard -> ClipboardPage(settings, store::update, onOpenPhrases = { screen = Screen.Phrases }, onBack = back)
            Screen.Phrases -> PhrasesScreen(phrases, onBack = back)
            Screen.Privacy -> PrivacyPage(settings, store::update, back)
            Screen.About -> AboutPage(onOpenLicenses = { screen = Screen.Licenses }, onBack = back)
            Screen.Licenses -> LicensesScreen(
                onBack = back,
                onOpenNotice = { index ->
                    notice = index
                    screen = Screen.Notice
                },
            )
            Screen.Notice -> NoticeScreen(Licenses[notice], onBack = back)
        }
    }
}
