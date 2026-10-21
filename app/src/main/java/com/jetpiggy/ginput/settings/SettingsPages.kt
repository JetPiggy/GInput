package com.jetpiggy.ginput.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jetpiggy.ginput.R
import com.jetpiggy.ginput.clipboard.ClipboardHistoryFile
import com.jetpiggy.ginput.dictionary.CandidateMemoryFile
import com.jetpiggy.ginput.dictionary.PairMemoryFile
import com.jetpiggy.ginput.engine.CangjieGeneration
import com.jetpiggy.ginput.engine.Scheme
import com.jetpiggy.ginput.engine.SharedCodeOrder
import com.jetpiggy.ginput.keyboard.KeyHaptic
import com.jetpiggy.ginput.keyboard.KeyHaptics
import com.jetpiggy.ginput.typing.SpaceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CONTACT_EMAIL = "ginput@jetpiggy.com"

/** 螢幕有這麼高（dp）才把鍵盤預覽固定在頁頂；矮的螢幕（例如橫放）預覽跟着設定一起捲動。 */
private const val PIN_PREVIEW_MIN_HEIGHT_DP = 700

/**
 * 分類的子頁：標題列有返回鍵，內容可以捲動。pinned 固定在標題列下面，不跟着捲動；overlay 蓋在整頁上面。
 * tryField 的子頁底部有試打欄，改設定時可以即時試打。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubPage(
    title: String,
    onBack: () -> Unit,
    tryField: Boolean = false,
    snackbar: SnackbarHostState? = null,
    pinned: @Composable () -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { BackButton(onBack) },
            )
        },
        bottomBar = { if (tryField) TryKeyboardBar(rememberImeStatus().isDefault) },
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize()) {
                pinned()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                    content = content,
                )
            }
            overlay()
        }
    }
}

/** 輸入方案：啟用和排序、倉頡版本、寬鬆拼法、連打、重碼次序、記憶字。 */
@Composable
internal fun SchemesPage(settings: UserSettings, onChange: ((UserSettings) -> UserSettings) -> Unit, onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    SubPage(stringResource(R.string.section_schemes), onBack, tryField = true, snackbar = snackbar) {
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

/** 打字：口語優先、空白鍵模式、智能刪空格、自動大寫、Emoji 關鍵字；下面是手感（按鍵放大預覽、按鍵震動、按鍵音）。 */
@Composable
internal fun TypingPage(settings: UserSettings, onChange: ((UserSettings) -> UserSettings) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    SubPage(stringResource(R.string.section_typing), onBack, tryField = true) {
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
        SectionTitle(stringResource(R.string.section_feel))
        SwitchRow(
            title = stringResource(R.string.key_preview),
            summary = stringResource(R.string.key_preview_summary),
            checked = settings.keyboard.keyPreview,
            onCheckedChange = { value -> onChange { it.copy(keyboard = it.keyboard.copy(keyPreview = value)) } },
        )
        ChoiceRow(
            title = stringResource(R.string.key_vibration),
            options = KeyVibration.entries.map { it to stringResource(it.label) },
            selected = settings.keyboard.vibration,
            onSelect = { value ->
                onChange { it.copy(keyboard = it.keyboard.copy(vibration = value)) }
                // 即時震一下，讓使用者感受揀了的強弱。
                KeyHaptics.create(context, value).perform(view, KeyHaptic.Press)
            },
        )
        SwitchRow(
            title = stringResource(R.string.key_sound),
            summary = stringResource(R.string.key_sound_summary),
            checked = settings.keyboard.keySound,
            onCheckedChange = { value -> onChange { it.copy(keyboard = it.keyboard.copy(keySound = value)) } },
        )
    }
}

/**
 * 鍵盤外觀：鍵盤預覽、主題，下面是版面（鍵盤高度、按鍵標示、數字行）。螢幕夠高的話，預覽固定在頁頂，
 * 捲到下面改版面時仍然看得到。這頁沒有試打欄，預覽已經即時顯示改動。
 */
@Composable
internal fun AppearancePage(
    keyboard: KeyboardSettings,
    onChange: ((KeyboardSettings) -> KeyboardSettings) -> Unit,
    onBack: () -> Unit,
) {
    val pinPreview = LocalConfiguration.current.screenHeightDp >= PIN_PREVIEW_MIN_HEIGHT_DP
    SubPage(
        title = stringResource(R.string.section_appearance),
        onBack = onBack,
        pinned = {
            if (pinPreview) {
                KeyboardPreview(keyboard, Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp))
                HorizontalDivider()
            }
        },
    ) {
        if (!pinPreview) KeyboardPreview(keyboard, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        ThemePicker(keyboard.theme) { value -> onChange { it.copy(theme = value) } }
        SectionTitle(stringResource(R.string.section_layout))
        ChoiceRow(
            title = stringResource(R.string.keyboard_height),
            options = KeyboardHeight.entries.map { it to stringResource(it.label) },
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
    }
}

/** 鍵盤高度的名稱，例如「標準」。 */
@get:StringRes
internal val KeyboardHeight.label: Int
    get() = when (this) {
        KeyboardHeight.Compact -> R.string.height_compact
        KeyboardHeight.Standard -> R.string.height_standard
        KeyboardHeight.Tall -> R.string.height_tall
    }

/** 按鍵震動的名稱，例如「跟隨系統」。 */
@get:StringRes
internal val KeyVibration.label: Int
    get() = when (this) {
        KeyVibration.System -> R.string.vibration_system
        KeyVibration.Off -> R.string.vibration_off
        KeyVibration.Light -> R.string.vibration_light
        KeyVibration.Standard -> R.string.vibration_standard
        KeyVibration.Strong -> R.string.vibration_strong
    }

/** 剪貼簿和常用短語：剪貼簿歷史開關，以及打開常用短語頁。 */
@Composable
internal fun ClipboardPage(
    settings: UserSettings,
    onChange: ((UserSettings) -> UserSettings) -> Unit,
    onOpenPhrases: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    SubPage(stringResource(R.string.section_clipboard), onBack) {
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
}

/** 私隱：當機回報、使用統計和私隱政策。 */
@Composable
internal fun PrivacyPage(settings: UserSettings, onChange: ((UserSettings) -> UserSettings) -> Unit, onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    SubPage(stringResource(R.string.section_privacy), onBack) {
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
        ActionRow(stringResource(R.string.privacy_policy), null) { uriHandler.openUri(privacyPolicyUrl()) }
    }
}

/** 關於：品牌卡（版本、網站、原始碼、聯絡我們），下面是私隱政策和開源授權。連按品牌卡的飛豬，牠會飛過畫面。 */
@Composable
internal fun AboutPage(onOpenLicenses: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val noEmailApp = stringResource(R.string.no_email_app, CONTACT_EMAIL)
    var flying by remember { mutableStateOf(false) }
    SubPage(
        title = stringResource(R.string.section_about),
        onBack = onBack,
        snackbar = snackbar,
        overlay = { if (flying) FlyingPig(onFinished = { flying = false }, Modifier.matchParentSize()) },
    ) {
        Spacer(Modifier.height(8.dp))
        AboutCard(
            onContact = {
                try {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$CONTACT_EMAIL")))
                } catch (e: ActivityNotFoundException) {
                    scope.launch { snackbar.showSnackbar(noEmailApp) }
                }
            },
            onEasterEgg = { flying = true },
        )
        Spacer(Modifier.height(8.dp))
        ActionRow(stringResource(R.string.privacy_policy), null) { uriHandler.openUri(privacyPolicyUrl()) }
        ActionRow(stringResource(R.string.licenses), stringResource(R.string.licenses_summary), onOpenLicenses)
    }
}
