package com.jetpiggy.ginput.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetpiggy.ginput.R

/** 剪貼簿面板的內容。historyEnabled 是剪貼簿歷史的設定，關了的話面板說明怎樣開啟。 */
data class ClipboardContent(
    val history: List<String> = emptyList(),
    val historyEnabled: Boolean = true,
    val phrases: List<String> = emptyList(),
)

internal enum class ClipboardTab { History, Phrases }

/**
 * 剪貼簿面板：取代按鍵區，高度相同（keyArea）。最頂一行是複製、貼上和全選；中間是剪貼簿歷史或常用短語，
 * 每項最多顯示兩行；最底一行是返回鍵（回到字母頁）、兩個分頁（常用短語分頁另有「管理」）和 Delete。
 * 按一項就寫入，長按交給 onLongPress。
 */
@Composable
internal fun ClipboardPanel(
    content: ClipboardContent,
    keyArea: Dp,
    colors: KeyboardColors,
    onKey: (KeyAction) -> Unit,
    onLongPress: (ClipboardTab, String) -> Unit,
    onBackToLetters: () -> Unit,
) {
    val dimensions = LocalKeyboardDimensions.current
    val (history, historyEnabled, phrases) = content
    // 未選分頁時：剪貼簿歷史是空的，就先看常用短語。
    var selected by remember { mutableStateOf<ClipboardTab?>(null) }
    val tab = selected ?: if (history.isEmpty() && phrases.isNotEmpty()) ClipboardTab.Phrases else ClipboardTab.History
    val items = if (tab == ClipboardTab.History) history else phrases
    val longPressLabel = stringResource(
        if (tab == ClipboardTab.History) R.string.clipboard_item_actions else R.string.clipboard_delete,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(dimensions.barHeight),
    ) {
        val edits = listOf(
            ToolbarItem(R.drawable.ic_content_copy, stringResource(R.string.clipboard_copy)) { onKey(KeyAction.Copy) },
            ToolbarItem(R.drawable.ic_content_paste_go, stringResource(R.string.clipboard_paste)) { onKey(KeyAction.Paste) },
            ToolbarItem(R.drawable.ic_select_all, stringResource(R.string.clipboard_select_all)) { onKey(KeyAction.SelectAll) },
        )
        edits.forEach { ToolbarButton(it, showLabel = true, colors = colors, weight = 1f) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(keyArea - dimensions.rowHeight - dimensions.barHeight),
    ) {
        if (items.isEmpty()) {
            Text(
                text = stringResource(
                    when {
                        tab == ClipboardTab.Phrases -> R.string.clipboard_phrases_empty
                        historyEnabled -> R.string.clipboard_history_empty
                        else -> R.string.clipboard_history_off
                    },
                ),
                color = colors.secondaryLabel,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
            )
        } else {
            key(tab) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(items) { text ->
                        PressableBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .keyShadow(colors.keyShadow),
                            color = colors.key,
                            pressedColor = colors.pressedKey,
                            description = text,
                            onTap = {
                                onKey(
                                    if (tab == ClipboardTab.History) KeyAction.WriteClipboardItem(text) else KeyAction.WritePhrase(text),
                                )
                            },
                            onLongPress = { onLongPress(tab, text) },
                            longPressLabel = longPressLabel,
                        ) {
                            Text(
                                text = text,
                                color = colors.label,
                                fontSize = 16.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    KeyRow(bottom = true) {
        BackToLettersKey(colors, onBackToLetters)
        Row(
            modifier = Modifier
                .weight(7f)
                .fillMaxHeight(),
        ) {
            PanelTab(stringResource(R.string.clipboard_tab_history), tab == ClipboardTab.History, colors) {
                selected = ClipboardTab.History
            }
            PanelTab(stringResource(R.string.clipboard_tab_phrases), tab == ClipboardTab.Phrases, colors) {
                selected = ClipboardTab.Phrases
            }
            if (tab == ClipboardTab.Phrases) {
                val manage = stringResource(R.string.clipboard_manage)
                PressableBox(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(vertical = 5.dp, horizontal = 2.dp),
                    color = Color.Transparent,
                    pressedColor = colors.pressedKey,
                    description = manage,
                    onTap = { onKey(KeyAction.ManagePhrases) },
                ) {
                    Text(text = manage, color = colors.secondaryLabel, fontSize = 15.sp, maxLines = 1)
                }
            } else {
                Box(Modifier.weight(1f))
            }
        }
        DeleteKey(colors, weight = 1.5f, onKey = onKey)
    }
}

@Composable
private fun RowScope.PanelTab(
    name: String,
    selected: Boolean,
    colors: KeyboardColors,
    onTap: () -> Unit,
) {
    PressableBox(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(vertical = 5.dp, horizontal = 2.dp),
        color = if (selected) colors.functionKey else Color.Transparent,
        pressedColor = colors.pressedKey,
        description = name,
        stateDescription = if (selected) stringResource(R.string.state_selected) else null,
        onTap = onTap,
    ) {
        Text(text = name, color = colors.label, fontSize = 15.sp, maxLines = 1)
    }
}
