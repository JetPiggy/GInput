package com.jetpiggy.ginput.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jetpiggy.ginput.R
import com.jetpiggy.ginput.clipboard.PhraseStore
import com.jetpiggy.ginput.clipboard.SavedPhrases

/** 常用短語頁：加入、修改、刪除（先確認）和調整次序。規則見 docs/spec/v1.md 的「設定 App」。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhrasesScreen(store: PhraseStore, onBack: () -> Unit) {
    val phrases by store.phrases.collectAsStateWithLifecycle()
    // 正在修改的常用短語；adding 是正在加入新的。
    var draft by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.saved_phrases)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            if (!phrases.full) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.phrase_add)) },
                )
            }
        },
    ) { padding ->
        // 底部留空位，「加入」按鈕不會遮住最後一項。
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
        ) {
            item {
                val hint = when {
                    phrases.items.isEmpty() -> stringResource(R.string.phrases_empty)
                    phrases.full -> stringResource(R.string.phrases_full, SavedPhrases.MAX_PHRASES)
                    else -> stringResource(R.string.phrases_hint, SavedPhrases.MAX_PHRASES)
                }
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            itemsIndexed(phrases.items, key = { _, phrase -> phrase }) { index, phrase ->
                PhraseRow(
                    phrase = phrase,
                    canMoveUp = index > 0,
                    canMoveDown = index < phrases.items.lastIndex,
                    onEdit = { draft = phrase },
                    onDelete = { deleting = phrase },
                    onMove = { offset -> store.update { it.move(phrase, offset) } },
                )
                HorizontalDivider()
            }
        }
    }

    if (adding || draft != null) {
        val original = draft
        PhraseDialog(
            initial = original.orEmpty(),
            title = stringResource(if (original == null) R.string.phrase_add_title else R.string.phrase_edit_title),
            onDismiss = {
                adding = false
                draft = null
            },
            onSave = { text ->
                store.update { if (original == null) it.add(text) else it.edit(original, text) }
                adding = false
                draft = null
            },
        )
    }
    deleting?.let { phrase ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.phrase_delete_prompt)) },
            text = { Text(phrase, maxLines = 6, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.update { it.remove(phrase) }
                        deleting = null
                    },
                ) {
                    Text(stringResource(R.string.clipboard_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun PhraseRow(
    phrase: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
) {
    // TalkBack 讀出的名稱只用第一行，免得太長。
    val name = phrase.lineSequence().first().take(30)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = phrase,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable(onClickLabel = stringResource(R.string.phrase_edit, name), role = Role.Button, onClick = onEdit)
                .padding(16.dp),
        )
        IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.phrase_move_up, name))
        }
        IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.phrase_move_down, name))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.clipboard_delete))
        }
    }
}

@Composable
private fun PhraseDialog(initial: String, title: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val tooLong = text.codePointCount(0, text.length) > SavedPhrases.MAX_LENGTH
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                isError = tooLong,
                supportingText = if (tooLong) ({ Text(stringResource(R.string.phrase_too_long, SavedPhrases.MAX_LENGTH)) }) else null,
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = SavedPhrases.accepts(text)) {
                Text(stringResource(R.string.phrase_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
