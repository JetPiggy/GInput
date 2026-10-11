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
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jetpiggy.ginput.clipboard.PhraseStore
import com.jetpiggy.ginput.ui.theme.GInputTheme

/** 設定 App：未完成首次設定精靈就先顯示精靈，之後是設定頁。 */
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

private enum class Screen { Settings, Phrases, Licenses, Notice }

@Composable
private fun SettingsApp(store: SettingsStore, phrases: PhraseStore, start: Screen) {
    val settings by store.settings.collectAsStateWithLifecycle()
    if (!settings.setupDone) {
        SetupWizard(settings, store::update, onFinish = { store.update { it.copy(setupDone = true) } })
        return
    }
    var screen by rememberSaveable { mutableStateOf(start) }
    var notice by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = screen != Screen.Settings) {
        screen = if (screen == Screen.Notice) Screen.Licenses else Screen.Settings
    }
    when (screen) {
        Screen.Settings -> SettingsScreen(
            settings,
            store::update,
            onOpenPhrases = { screen = Screen.Phrases },
            onOpenLicenses = { screen = Screen.Licenses },
        )
        Screen.Phrases -> PhrasesScreen(phrases, onBack = { screen = Screen.Settings })
        Screen.Licenses -> LicensesScreen(
            onBack = { screen = Screen.Settings },
            onOpenNotice = { index ->
                notice = index
                screen = Screen.Notice
            },
        )
        Screen.Notice -> NoticeScreen(Licenses[notice], onBack = { screen = Screen.Licenses })
    }
}
