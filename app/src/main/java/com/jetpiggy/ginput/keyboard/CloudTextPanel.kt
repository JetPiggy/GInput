package com.jetpiggy.ginput.keyboard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetpiggy.ginput.R
import com.jetpiggy.ginput.cloudtext.CloudTextState
import com.jetpiggy.ginput.cloudtext.CloudTextStatus
import com.jetpiggy.ginput.cloudtext.CloudTool
import com.jetpiggy.ginput.cloudtext.DailyAllowance
import com.jetpiggy.ginput.cloudtext.FailReason
import com.jetpiggy.ginput.cloudtext.SourceText
import com.jetpiggy.ginput.cloudtext.TextTask

/**
 * 翻譯面板和改寫面板：取代按鍵區，高度相同（keyArea）。最頂一行是選項；中間是原文、進度或結果；
 * 最底一行是返回鍵、今日尚餘的次數和「取代」。第一次使用時先顯示說明，按「同意」後才可以用。
 * 規則見 docs/spec/v1.md 的「雲端翻譯和改寫」。
 */
@Composable
internal fun CloudTextPanel(
    tool: CloudTool,
    state: CloudTextState,
    keyArea: Dp,
    colors: KeyboardColors,
    onKey: (KeyAction) -> Unit,
    onApply: () -> Unit,
    onBackToLetters: () -> Unit,
) {
    val dimensions = LocalKeyboardDimensions.current
    // 離開面板（回到字母頁、打開剪貼簿、換了文字框）就不再理會未完成的處理。
    val currentOnKey by rememberUpdatedState(onKey)
    DisposableEffect(Unit) {
        onDispose { currentOnKey(KeyAction.CloseCloudTool) }
    }

    if (!state.noticeAccepted) {
        // 行距比預設的 24sp 密，沒有數字行的鍵盤也大致放得下整段說明；放不下（例如橫向）就捲動。
        Text(
            text = stringResource(R.string.cloud_notice, DailyAllowance.LIMIT),
            color = colors.label,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            modifier = Modifier
                .fillMaxWidth()
                .height(keyArea - dimensions.rowHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        KeyRow {
            BackToLettersKey(colors, onBackToLetters)
            Spacer(Modifier.weight(5f))
            ActionKey(stringResource(R.string.cloud_accept), enabled = true, colors) { onKey(KeyAction.AcceptCloudNotice) }
        }
        return
    }

    val status = state.status
    val sourceProblem = status == CloudTextStatus.NoText || status == CloudTextStatus.TooLong ||
        status == CloudTextStatus.Unreadable
    val canRun = state.remaining > 0 && !sourceProblem
    val activeTask = when (status) {
        is CloudTextStatus.Working -> status.task
        is CloudTextStatus.Done -> status.task
        is CloudTextStatus.Failed -> status.task
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(dimensions.barHeight)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (task in TextTask.of(tool)) {
            TaskOption(task, active = task == activeTask, enabled = canRun, colors) { onKey(KeyAction.RunTextTask(task)) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(keyArea - dimensions.rowHeight - dimensions.barHeight),
    ) {
        when {
            status is CloudTextStatus.Done -> Text(
                text = status.result,
                color = colors.label,
                fontSize = 17.sp,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            status is CloudTextStatus.Working -> Message(
                stringResource(if (tool == CloudTool.Translation) R.string.cloud_translating else R.string.cloud_rewriting),
                colors,
            )
            state.remaining == 0 -> Message(stringResource(R.string.cloud_used_up, DailyAllowance.LIMIT), colors)
            status is CloudTextStatus.Failed -> Message(
                stringResource(
                    when (status.reason) {
                        FailReason.Offline -> R.string.cloud_offline
                        FailReason.Busy -> R.string.cloud_busy
                        FailReason.Error -> R.string.cloud_failed
                    },
                ),
                colors,
            )
            status == CloudTextStatus.NoText -> Message(stringResource(R.string.cloud_no_text), colors)
            status == CloudTextStatus.TooLong -> Message(stringResource(R.string.cloud_too_long, SourceText.MAX_LENGTH), colors)
            status == CloudTextStatus.Unreadable -> Message(stringResource(R.string.cloud_unreadable), colors)
            // 未按選項：顯示原文的開頭，讓使用者知道會處理哪段文字。
            else -> Text(
                text = state.source,
                color = colors.secondaryLabel,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }

    KeyRow {
        BackToLettersKey(colors, onBackToLetters)
        Box(
            modifier = Modifier
                .weight(5f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.cloud_remaining, state.remaining),
                color = colors.secondaryLabel,
                fontSize = 13.sp,
                maxLines = 1,
            )
        }
        ActionKey(stringResource(R.string.cloud_apply), enabled = status is CloudTextStatus.Done, colors, onTap = onApply)
    }
}

/** 選項：處理中或顯示着結果的那個有底色；用完每日次數或原文有問題時停用。 */
@Composable
private fun TaskOption(task: TextTask, active: Boolean, enabled: Boolean, colors: KeyboardColors, onTap: () -> Unit) {
    val label = stringResource(task.label)
    PressableBox(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        color = if (active) colors.functionKey else Color.Transparent,
        pressedColor = colors.pressedKey,
        description = label,
        stateDescription = when {
            !enabled -> stringResource(R.string.state_unavailable)
            active -> stringResource(R.string.state_selected)
            else -> null
        },
        onTap = { if (enabled) onTap() },
    ) {
        Text(
            text = label,
            color = if (enabled) colors.label else colors.secondaryLabel,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

private val TextTask.label: Int
    get() = when (this) {
        TextTask.ToEnglish -> R.string.translate_english
        TextTask.ToWritten -> R.string.translate_written
        TextTask.ToColloquial -> R.string.translate_colloquial
        TextTask.Written -> R.string.rewrite_written
        TextTask.Colloquial -> R.string.rewrite_colloquial
        TextTask.Polite -> R.string.rewrite_polite
        TextTask.Casual -> R.string.rewrite_casual
        TextTask.Fix -> R.string.rewrite_fix
    }

/** 最底一行右邊的「同意」或「取代」；停用時是灰字。 */
@Composable
private fun RowScope.ActionKey(label: String, enabled: Boolean, colors: KeyboardColors, onTap: () -> Unit) {
    Key(
        colors,
        weight = 3.5f,
        description = label,
        style = if (enabled) KeyStyle.Accent else KeyStyle.Function,
        stateDescription = if (enabled) null else stringResource(R.string.state_unavailable),
        onTap = { if (enabled) onTap() },
    ) {
        Text(
            text = label,
            color = if (enabled) colors.onAccent else colors.secondaryLabel,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun Message(text: String, colors: KeyboardColors) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = colors.secondaryLabel,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}
