package com.jetpiggy.ginput.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jetpiggy.ginput.R
import com.jetpiggy.ginput.engine.Scheme

private enum class SetupStep { Enable, Default, Scheme, Stats, Try }

/**
 * 首次設定精靈：啟用 GInput → 設為預設輸入法 → 選速成、倉頡或粵拼 → 問是否參加使用統計（預設不參加）→ 試打。
 * 啟用和設為預設兩步，在系統設定做好後自動到下一步。
 */
@Composable
internal fun SetupWizard(
    settings: UserSettings,
    onChange: ((UserSettings) -> UserSettings) -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val status = rememberImeStatus()
    var step by rememberSaveable {
        mutableStateOf(
            when {
                !status.enabled -> SetupStep.Enable
                !status.isDefault -> SetupStep.Default
                else -> SetupStep.Scheme
            },
        )
    }
    // 只在狀態改變時自動前進，按「上一步」回來的話可以停留。
    LaunchedEffect(status.enabled) {
        if (status.enabled && step == SetupStep.Enable) step = SetupStep.Default
    }
    LaunchedEffect(status.isDefault) {
        if (status.isDefault && step == SetupStep.Default) step = SetupStep.Scheme
    }
    val previous = SetupStep.entries.getOrNull(step.ordinal - 1)
    BackHandler(enabled = previous != null) { previous?.let { step = it } }
    val next = { SetupStep.entries.getOrNull(step.ordinal + 1)?.let { step = it } ?: onFinish() }
    val scheme = settings.typing.engine.schemes.firstOrNull() ?: Scheme.Quick

    Scaffold { padding ->
        // 試打時鍵盤升起，按鈕要留在鍵盤上面。
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.setup_welcome), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.setup_step, step.ordinal + 1, SetupStep.entries.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (step) {
                    SetupStep.Enable -> StepText(
                        title = stringResource(R.string.setup_enable_title),
                        body = stringResource(if (status.enabled) R.string.setup_enable_done else R.string.setup_enable_body),
                    )
                    SetupStep.Default -> StepText(
                        title = stringResource(R.string.setup_default_title),
                        body = stringResource(if (status.isDefault) R.string.setup_default_done else R.string.setup_default_body),
                    )
                    SetupStep.Scheme -> {
                        StepText(stringResource(R.string.setup_scheme_title), stringResource(R.string.setup_scheme_body))
                        SchemeChoice(scheme) { chosen -> onChange { it.withOnlyScheme(chosen) } }
                    }
                    SetupStep.Stats -> {
                        StepText(stringResource(R.string.setup_stats_title), stringResource(R.string.setup_stats_body))
                        SwitchRow(
                            title = stringResource(R.string.setup_stats_switch),
                            summary = null,
                            checked = settings.usageStats,
                            onCheckedChange = { value -> onChange { it.copy(usageStats = value) } },
                        )
                        TextButton(onClick = { uriHandler.openUri(privacyPolicyUrl()) }) {
                            Text(stringResource(R.string.privacy_policy))
                        }
                    }
                    SetupStep.Try -> {
                        StepText(
                            title = stringResource(R.string.setup_try_title),
                            body = stringResource(
                                when (scheme) {
                                    Scheme.Quick -> R.string.setup_try_quick
                                    Scheme.Cangjie -> R.string.setup_try_cangjie
                                    Scheme.Jyutping -> R.string.setup_try_jyutping
                                },
                            ),
                        )
                        var sample by rememberSaveable { mutableStateOf("") }
                        OutlinedTextField(
                            value = sample,
                            onValueChange = { sample = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.setup_try_hint)) },
                            minLines = 3,
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (previous != null) {
                    TextButton(onClick = { step = previous }) { Text(stringResource(R.string.action_previous)) }
                }
                Spacer(Modifier.weight(1f))
                when {
                    step == SetupStep.Enable && !status.enabled ->
                        Button(onClick = { openKeyboardSettings(context) }) {
                            Text(stringResource(R.string.setup_enable_button))
                        }
                    step == SetupStep.Default && !status.isDefault -> {
                        TextButton(onClick = next) { Text(stringResource(R.string.action_skip)) }
                        Button(onClick = { showKeyboardPicker(context) }) {
                            Text(stringResource(R.string.setup_default_button))
                        }
                    }
                    step == SetupStep.Try -> Button(onClick = onFinish) { Text(stringResource(R.string.action_done)) }
                    else -> Button(onClick = next) { Text(stringResource(R.string.action_next)) }
                }
            }
        }
    }
}

@Composable
private fun StepText(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Text(body, style = MaterialTheme.typography.bodyLarge)
}

/** 首次設定只選一種輸入方案；之後可以在設定加開其他的。 */
@Composable
private fun SchemeChoice(selected: Scheme, onSelect: (Scheme) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (scheme in UserSettings.DEFAULT_SCHEME_ORDER) {
            OutlinedCard(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = scheme == selected, role = Role.RadioButton, onClick = { onSelect(scheme) }),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = scheme == selected, onClick = null)
                    RowText(schemeName(scheme), schemeSummary(scheme), Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}
