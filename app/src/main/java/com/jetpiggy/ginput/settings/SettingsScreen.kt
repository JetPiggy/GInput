package com.jetpiggy.ginput.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jetpiggy.ginput.BuildConfig
import com.jetpiggy.ginput.R
import com.jetpiggy.ginput.clipboard.ClipboardHistoryFile
import com.jetpiggy.ginput.dictionary.CandidateMemoryFile
import com.jetpiggy.ginput.dictionary.PairMemoryFile
import com.jetpiggy.ginput.engine.CangjieGeneration
import com.jetpiggy.ginput.engine.Scheme
import com.jetpiggy.ginput.engine.SharedCodeOrder
import com.jetpiggy.ginput.typing.SpaceMode
import com.jetpiggy.ginput.update.AvailableUpdate
import com.jetpiggy.ginput.update.UpdateCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CONTACT_EMAIL = "ginput@jetpiggy.com"

/** 設定頁。設定項目和預設值見 docs/spec/v1.md 的「設定 App」。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    settings: UserSettings,
    onChange: ((UserSettings) -> UserSettings) -> Unit,
    onOpenPhrases: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val imeStatus = rememberImeStatus()
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        bottomBar = { TryKeyboardBar(imeStatus.isDefault) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            ImeStatusCard(imeStatus)
            UpdateCard()
            SchemeSection(settings, onChange, snackbar)
            TypingSection(settings, onChange)
            ClipboardSection(settings, onChange, onOpenPhrases)
            KeyboardSection(settings.keyboardOnScreen) { transform -> onChange { it.copy(keyboard = transform(it.keyboard)) } }
            PrivacySection(settings, onChange)
            AboutSection(snackbar, onOpenLicenses)
        }
    }
}

/**
 * 設定頁底部的試打欄：按一下就升起鍵盤。按設定項目不會拿走文字框的焦點，所以改設定時鍵盤留着，
 * 改動即時看得到。試打的文字不儲存。
 */
@Composable
private fun TryKeyboardBar(isDefault: Boolean) {
    var text by remember { mutableStateOf("") }
    Surface(tonalElevation = 3.dp) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = {
                Text(stringResource(if (isDefault) R.string.try_keyboard_hint else R.string.try_keyboard_not_default))
            },
            trailingIcon = if (text.isEmpty()) {
                null
            } else {
                {
                    IconButton(onClick = { text = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.try_keyboard_clear))
                    }
                }
            },
            maxLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                // 鍵盤升起時留在鍵盤上面，收起時留在導航列上面。
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** GInput 未啟用或不是目前的輸入法時，在最上面提示。 */
@Composable
private fun ImeStatusCard(status: ImeStatus) {
    if (status.enabled && status.isDefault) return
    val context = LocalContext.current
    Card(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!status.enabled) {
                Text(stringResource(R.string.status_not_enabled), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { openKeyboardSettings(context) }) { Text(stringResource(R.string.setup_enable_button)) }
            } else {
                Text(stringResource(R.string.status_not_default), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { showKeyboardPicker(context) }) { Text(stringResource(R.string.setup_default_button)) }
            }
        }
    }
}

/** 不是從 Google Play 安裝的版本，有新版本時提示，並打開下載頁（規格「發佈」）。 */
@Composable
private fun UpdateCard() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val chinese = LocalConfiguration.current.locales[0].language == "zh"
    val update by produceState<AvailableUpdate?>(null) {
        if (UpdateCheck.isFromPlay(context)) return@produceState
        value = withContext(Dispatchers.IO) {
            UpdateCheck.fetch("${BuildConfig.SITE_URL}latest.json", BuildConfig.VERSION_CODE, chinese)
        }
    }
    val available = update ?: return
    Card(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.update_available, available.versionName), style = MaterialTheme.typography.titleMedium)
            if (available.notes != null) Text(available.notes, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { uriHandler.openUri(available.pageUrl) }) { Text(stringResource(R.string.update_open)) }
        }
    }
}

@Composable
private fun SchemeSection(
    settings: UserSettings,
    onChange: ((UserSettings) -> UserSettings) -> Unit,
    snackbar: SnackbarHostState,
) {
    SectionTitle(stringResource(R.string.section_schemes))
    settings.schemeOrder.forEachIndexed { index, scheme ->
        val enabled = scheme in settings.enabledSchemes
        SchemeRow(
            name = schemeName(scheme),
            summary = schemeSummary(scheme),
            enabled = enabled,
            // 至少要啟用一個輸入方案。
            canToggle = !enabled || settings.enabledSchemes.size > 1,
            canMoveUp = index > 0,
            canMoveDown = index < settings.schemeOrder.lastIndex,
            onToggle = { on -> onChange { it.withSchemeEnabled(scheme, on) } },
            onMove = { offset -> onChange { it.withSchemeMoved(scheme, offset) } },
        )
    }
    Text(
        text = stringResource(R.string.scheme_order_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    ChoiceRow(
        title = stringResource(R.string.cangjie_generation),
        options = listOf(
            CangjieGeneration.entries.toSet() to stringResource(R.string.generation_both),
            setOf(CangjieGeneration.Third) to stringResource(R.string.generation_third),
            setOf(CangjieGeneration.Fifth) to stringResource(R.string.generation_fifth),
        ),
        selected = settings.cangjieGenerations,
        onSelect = { value -> onChange { it.copy(cangjieGenerations = value) } },
    )
    if (Scheme.Jyutping in settings.enabledSchemes) {
        SwitchRow(
            title = stringResource(R.string.loose_spelling),
            summary = stringResource(R.string.loose_spelling_summary),
            checked = settings.looseSpelling,
            onCheckedChange = { value -> onChange { it.copy(looseSpelling = value) } },
        )
    }
    if (settings.enabledSchemes.any { it != Scheme.Jyutping }) {
        SwitchRow(
            title = stringResource(R.string.continuous_typing),
            summary = stringResource(R.string.continuous_typing_summary),
            checked = settings.continuousTyping,
            onCheckedChange = { value -> onChange { it.copy(continuousTyping = value) } },
        )
    }
    ChoiceRow(
        title = stringResource(R.string.shared_code_order),
        options = listOf(
            SharedCodeOrder.Frequency to stringResource(R.string.order_frequency),
            SharedCodeOrder.Windows to stringResource(R.string.order_windows),
        ),
        selected = settings.sharedCodeOrder,
        onSelect = { value -> onChange { it.copy(sharedCodeOrder = value) } },
    )
    SwitchRow(
        title = stringResource(R.string.remember_candidates),
        summary = stringResource(R.string.remember_candidates_summary),
        checked = settings.rememberCandidates,
        onCheckedChange = { value -> onChange { it.copy(rememberCandidates = value) } },
    )
    ClearMemoryRow(snackbar)
}

@Composable
private fun SchemeRow(
    name: String,
    summary: String,
    enabled: Boolean,
    canToggle: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .toggleable(value = enabled, enabled = canToggle, role = Role.Checkbox, onValueChange = onToggle)
                .padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = enabled, onCheckedChange = null, enabled = canToggle)
            RowText(name, summary, Modifier.padding(start = 4.dp))
        }
        IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.scheme_move_up, name))
        }
        IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.scheme_move_down, name))
        }
    }
}

@Composable
internal fun schemeName(scheme: Scheme): String = stringResource(
    when (scheme) {
        Scheme.Quick -> R.string.scheme_quick
        Scheme.Cangjie -> R.string.scheme_cangjie
        Scheme.Jyutping -> R.string.scheme_jyutping
    },
)

@Composable
internal fun schemeSummary(scheme: Scheme): String = stringResource(
    when (scheme) {
        Scheme.Quick -> R.string.scheme_quick_summary
        Scheme.Cangjie -> R.string.scheme_cangjie_summary
        Scheme.Jyutping -> R.string.scheme_jyutping_summary
    },
)

/** 清除全部記憶：先問一次，再刪掉檔案並通知執行中的輸入法。 */
@Composable
private fun ClearMemoryRow(snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val done = stringResource(R.string.clear_memory_done)
    var confirming by rememberSaveable { mutableStateOf(false) }
    ActionRow(stringResource(R.string.clear_memory), stringResource(R.string.clear_memory_summary)) {
        confirming = true
    }
    if (!confirming) return
    AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text(stringResource(R.string.clear_memory_confirm_title)) },
        text = { Text(stringResource(R.string.clear_memory_summary)) },
        confirmButton = {
            TextButton(
                onClick = {
                    confirming = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            CandidateMemoryFile.delete(context)
                            PairMemoryFile.delete(context)
                        }
                        CandidateMemoryFile.clears.emit(Unit)
                        snackbar.showSnackbar(done)
                    }
                },
            ) {
                Text(stringResource(R.string.clear_memory_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun TypingSection(settings: UserSettings, onChange: ((UserSettings) -> UserSettings) -> Unit) {
    SectionTitle(stringResource(R.string.section_typing))
    SwitchRow(
        title = stringResource(R.string.colloquial_priority),
        summary = stringResource(R.string.colloquial_priority_summary),
        checked = settings.colloquialPriority,
        onCheckedChange = { value -> onChange { it.copy(colloquialPriority = value) } },
    )
    ChoiceRow(
        title = stringResource(R.string.space_mode),
        options = listOf(
            SpaceMode.WriteEnglish to stringResource(R.string.space_english),
            SpaceMode.SelectChinese to stringResource(R.string.space_chinese),
        ),
        selected = settings.spaceMode,
        onSelect = { value -> onChange { it.copy(spaceMode = value) } },
    )
    SwitchRow(
        title = stringResource(R.string.smart_space_removal),
        summary = stringResource(R.string.smart_space_removal_summary),
        checked = settings.smartSpaceRemoval,
        onCheckedChange = { value -> onChange { it.copy(smartSpaceRemoval = value) } },
    )
    SwitchRow(
        title = stringResource(R.string.auto_capitalize),
        summary = stringResource(R.string.auto_capitalize_summary),
        checked = settings.autoCapitalize,
        onCheckedChange = { value -> onChange { it.copy(autoCapitalize = value) } },
    )
    SwitchRow(
        title = stringResource(R.string.emoji_keywords),
        summary = stringResource(R.string.emoji_keywords_summary),
        checked = settings.emojiKeywords,
        onCheckedChange = { value -> onChange { it.copy(emojiKeywords = value) } },
    )
}

@Composable
private fun ClipboardSection(
    settings: UserSettings,
    onChange: ((UserSettings) -> UserSettings) -> Unit,
    onOpenPhrases: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    SectionTitle(stringResource(R.string.section_clipboard))
    SwitchRow(
        title = stringResource(R.string.clipboard_history),
        summary = stringResource(R.string.clipboard_history_summary),
        checked = settings.clipboardHistory,
        onCheckedChange = { value ->
            onChange { it.copy(clipboardHistory = value) }
            // 執行中的輸入法收到設定改變，也會清除手上的剪貼簿歷史；輸入法沒有執行的話，就靠這裏刪掉檔案。
            if (!value) scope.launch(Dispatchers.IO) { ClipboardHistoryFile.delete(context) }
        },
    )
    ActionRow(stringResource(R.string.saved_phrases), stringResource(R.string.saved_phrases_summary), onOpenPhrases)
}

@Composable
private fun KeyboardSection(keyboard: KeyboardSettings, onChange: ((KeyboardSettings) -> KeyboardSettings) -> Unit) {
    SectionTitle(stringResource(R.string.section_keyboard))
    ThemePicker(keyboard.theme) { value -> onChange { it.copy(theme = value) } }
    ChoiceRow(
        title = stringResource(R.string.keyboard_height),
        options = listOf(
            KeyboardHeight.Compact to stringResource(R.string.height_compact),
            KeyboardHeight.Standard to stringResource(R.string.height_standard),
            KeyboardHeight.Tall to stringResource(R.string.height_tall),
        ),
        selected = keyboard.height,
        onSelect = { value -> onChange { it.copy(height = value) } },
    )
    // 只啟用粵拼時，按鍵不顯示字根，這項沒有作用。
    if (keyboard.radicals) {
        ChoiceRow(
            title = stringResource(R.string.key_labels),
            options = listOf(
                KeyLabels.LettersLarge to stringResource(R.string.labels_letters),
                KeyLabels.RadicalsLarge to stringResource(R.string.labels_radicals),
                KeyLabels.LettersOnly to stringResource(R.string.labels_letters_only),
            ),
            selected = keyboard.labels,
            onSelect = { value -> onChange { it.copy(labels = value) } },
        )
    }
    SwitchRow(
        title = stringResource(R.string.number_row),
        summary = stringResource(R.string.number_row_summary),
        checked = keyboard.numberRow,
        onCheckedChange = { value -> onChange { it.copy(numberRow = value) } },
    )
    SwitchRow(
        title = stringResource(R.string.key_preview),
        summary = stringResource(R.string.key_preview_summary),
        checked = keyboard.keyPreview,
        onCheckedChange = { value -> onChange { it.copy(keyPreview = value) } },
    )
    SwitchRow(
        title = stringResource(R.string.key_sound),
        summary = stringResource(R.string.key_sound_summary),
        checked = keyboard.keySound,
        onCheckedChange = { value -> onChange { it.copy(keySound = value) } },
    )
}

@Composable
private fun PrivacySection(settings: UserSettings, onChange: ((UserSettings) -> UserSettings) -> Unit) {
    SectionTitle(stringResource(R.string.section_privacy))
    SwitchRow(
        title = stringResource(R.string.crash_reports),
        summary = stringResource(R.string.crash_reports_summary),
        checked = settings.crashReports,
        onCheckedChange = { value -> onChange { it.copy(crashReports = value) } },
    )
    SwitchRow(
        title = stringResource(R.string.usage_stats),
        summary = stringResource(R.string.usage_stats_summary),
        checked = settings.usageStats,
        onCheckedChange = { value -> onChange { it.copy(usageStats = value) } },
    )
}

@Composable
private fun AboutSection(snackbar: SnackbarHostState, onOpenLicenses: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val noEmailApp = stringResource(R.string.no_email_app)
    SectionTitle(stringResource(R.string.section_about))
    ActionRow(stringResource(R.string.version), BuildConfig.VERSION_NAME)
    ActionRow(stringResource(R.string.contact), CONTACT_EMAIL) {
        try {
            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$CONTACT_EMAIL")))
        } catch (e: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar(noEmailApp) }
        }
    }
    ActionRow(stringResource(R.string.privacy_policy), null) { uriHandler.openUri(privacyPolicyUrl()) }
    ActionRow(stringResource(R.string.licenses), stringResource(R.string.licenses_summary), onOpenLicenses)
}

internal fun privacyPolicyUrl(): String = "${BuildConfig.SITE_URL}privacy.html"
