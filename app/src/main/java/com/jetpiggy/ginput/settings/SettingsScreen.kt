package com.jetpiggy.ginput.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jetpiggy.ginput.BuildConfig
import com.jetpiggy.ginput.R
import com.jetpiggy.ginput.engine.Scheme
import com.jetpiggy.ginput.typing.SpaceMode
import com.jetpiggy.ginput.update.UpdateNotices
import kotlinx.coroutines.coroutineScope

/** 分類摘要裏各項之間的分隔。 */
private const val SUMMARY_SEPARATOR = " · "
private val CategoryOuterCorner = 20.dp
private val CategoryInnerCorner = 4.dp

/** 設定主頁的分類；每個分類是一個子頁（SettingsPages.kt）。 */
internal enum class SettingsCategory { Schemes, Typing, Appearance, Clipboard, Privacy, About }

/**
 * 設定主頁：最頂是品牌橫額，不用標題列；下面是分類，每個分類寫出主要設定的現況，按一下打開子頁。
 * 設定項目和預設值見 docs/spec/v1.md 的「設定 App」。
 */
@Composable
internal fun SettingsScreen(settings: UserSettings, phraseCount: Int, onOpen: (SettingsCategory) -> Unit) {
    val imeStatus = rememberImeStatus()
    Scaffold(bottomBar = { TryKeyboardBar(imeStatus.isDefault) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            BrandBanner(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp))
            ImeStatusCard(imeStatus)
            UpdateCards()
            CategoryList(settings, phraseCount, onOpen)
        }
    }
}

/**
 * 設定主頁和「輸入方案」「打字」子頁底部的試打欄：按一下就升起鍵盤。按設定項目不會拿走文字框的焦點，所以改設定時鍵盤留着，
 * 改動即時看得到。試打的文字不儲存。
 */
@Composable
internal fun TryKeyboardBar(isDefault: Boolean) {
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

/**
 * 不是從 Google Play 安裝的版本：網站的 latest.json 有公告或新版本時，在設定主頁最上面顯示（規格「發佈」）。
 * 新版本只打開下載頁，不在 App 內下載。在這裏看過之後，鍵盤工具列的「設定」不再加小點。
 */
@Composable
private fun UpdateCards() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val chinese = LocalConfiguration.current.locales[0].language == "zh"
    val notices = remember { UpdateNotices.get(context) }
    // 打開設定 App 時讀一次；由子頁返回主頁不再讀，除非上次未讀完就離開了主頁。
    var checked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(notices) {
        if (!checked) {
            coroutineScope { notices.checkNow(this) }
            checked = true
        }
    }
    val state by notices.state.collectAsState()
    val update = remember(state, chinese) { state.update(BuildConfig.VERSION_CODE, chinese) }
    val notice = remember(state, chinese) { state.notice(chinese) }
    LaunchedEffect(update, notice) {
        if (update != null || notice != null) notices.markSeen()
    }
    if (notice != null) {
        UpdateCard {
            Text(stringResource(R.string.notice_title), style = MaterialTheme.typography.titleMedium)
            Text(notice.text, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                notice.url?.let { url -> Button(onClick = { uriHandler.openUri(url) }) { Text(stringResource(R.string.notice_more)) } }
                TextButton(onClick = { notices.dismiss(notice.id) }) { Text(stringResource(R.string.notice_dismiss)) }
            }
        }
    }
    if (update != null) {
        UpdateCard {
            Text(stringResource(R.string.update_available, update.versionName), style = MaterialTheme.typography.titleMedium)
            if (update.notes != null) Text(update.notes, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { uriHandler.openUri(update.pageUrl) }) { Text(stringResource(R.string.update_open)) }
        }
    }
}

@Composable
private fun UpdateCard(content: @Composable () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

private class CategoryEntry(
    val category: SettingsCategory,
    @DrawableRes val icon: Int,
    val title: String,
    val summary: String,
    val trailing: (@Composable () -> Unit)? = null,
)

/** 六個分類連成一組圓角列表，每行一個圖示、名稱和現況摘要。 */
@Composable
private fun CategoryList(settings: UserSettings, phraseCount: Int, onOpen: (SettingsCategory) -> Unit) {
    val enabledSchemes = settings.schemeOrder.filter { it in settings.enabledSchemes }
    val entries = listOf(
        CategoryEntry(
            SettingsCategory.Schemes,
            R.drawable.ic_language_chinese_cangjie,
            stringResource(R.string.section_schemes),
            summary(
                enabledSchemes.map { schemeName(it) }.joinToString(stringResource(R.string.list_separator)),
                if (enabledSchemes.any { it != Scheme.Jyutping }) settingState(R.string.continuous_typing, settings.continuousTyping) else null,
            ),
        ),
        CategoryEntry(
            SettingsCategory.Typing,
            R.drawable.ic_keyboard,
            stringResource(R.string.section_typing),
            summary(
                stringResource(
                    when (settings.spaceMode) {
                        SpaceMode.WriteEnglish -> R.string.space_mode_short_english
                        SpaceMode.SelectChinese -> R.string.space_mode_short_chinese
                    },
                ),
                settingState(R.string.auto_capitalize, settings.autoCapitalize),
            ),
        ),
        CategoryEntry(
            SettingsCategory.Appearance,
            R.drawable.ic_palette,
            stringResource(R.string.section_appearance),
            summary(
                themeName(settings.keyboard.theme),
                stringResource(
                    R.string.summary_setting,
                    stringResource(R.string.keyboard_height),
                    stringResource(settings.keyboard.height.label),
                ),
            ),
            trailing = { ThemeSwatch(settings.keyboard.theme) },
        ),
        CategoryEntry(
            SettingsCategory.Clipboard,
            R.drawable.ic_assignment,
            stringResource(R.string.section_clipboard),
            summary(
                settingState(R.string.clipboard_history, settings.clipboardHistory),
                pluralStringResource(R.plurals.phrase_count, phraseCount, phraseCount),
            ),
        ),
        CategoryEntry(
            SettingsCategory.Privacy,
            R.drawable.ic_shield,
            stringResource(R.string.section_privacy),
            summary(
                settingState(R.string.crash_reports, settings.crashReports),
                settingState(R.string.usage_stats, settings.usageStats),
            ),
        ),
        CategoryEntry(
            SettingsCategory.About,
            R.drawable.ic_info,
            stringResource(R.string.section_about),
            stringResource(R.string.version_name, BuildConfig.VERSION_NAME),
        ),
    )
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        entries.forEachIndexed { index, entry ->
            val top = if (index == 0) CategoryOuterCorner else CategoryInnerCorner
            val bottom = if (index == entries.lastIndex) CategoryOuterCorner else CategoryInnerCorner
            CategoryRow(entry, RoundedCornerShape(top, top, bottom, bottom)) { onOpen(entry.category) }
        }
    }
}

@Composable
private fun CategoryRow(entry: CategoryEntry, shape: RoundedCornerShape, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(entry.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        RowText(entry.title, entry.summary, Modifier.weight(1f))
        entry.trailing?.invoke()
    }
}

private fun summary(vararg parts: String?): String = parts.filterNotNull().joinToString(SUMMARY_SEPARATOR)

/** 「剪貼簿歷史：開」這類摘要。 */
@Composable
private fun settingState(@StringRes name: Int, on: Boolean): String = stringResource(
    R.string.summary_setting,
    stringResource(name),
    stringResource(if (on) R.string.setting_on else R.string.setting_off),
)

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

internal fun privacyPolicyUrl(): String = "${BuildConfig.SITE_URL}privacy.html"
