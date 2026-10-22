package com.jetpiggy.ginput.keyboard

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jetpiggy.ginput.R
import com.jetpiggy.ginput.typing.KeyboardState

/**
 * 候選字格：按候選列的 ▼ 後鋪滿鍵盤的位置，列出全部候選字，英漢詞典的譯詞排在後面；
 * 改字時列出全部可以換上的字，原本的字加框。
 * 高度和按鍵區（keyArea）相同，鍵盤不會跳動。
 */
@Composable
internal fun CandidateGrid(
    state: KeyboardState,
    keyArea: Dp,
    colors: KeyboardColors,
    onKey: (KeyAction) -> Unit,
    onAskForget: (String) -> Unit,
) {
    val dimensions = LocalKeyboardDimensions.current
    val forget = stringResource(R.string.forget_button)
    val remembered = stringResource(R.string.candidate_remembered)
    val current = stringResource(R.string.fix_current)
    val fix = state.characterFix
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 52.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(keyArea),
    ) {
        // 整句候選長，佔一整行。
        itemsIndexed(
            fix?.choices ?: state.candidates,
            span = { _, candidate -> GridItemSpan(if (candidate.sentence) maxLineSpan else 1) },
        ) { index, candidate ->
            val framed = fix != null && candidate.text == fix.part.text
            PressableBox(
                modifier = Modifier
                    .height(dimensions.rowHeight)
                    .padding(2.dp)
                    .keyShadow(colors.keyShadow)
                    .then(if (framed) Modifier.border(1.dp, colors.secondaryLabel, KeyShape) else Modifier),
                color = colors.candidateBackground(
                    remembered = candidate.remembered,
                    marked = fix == null && state.markTopCandidate && index == 0,
                    plain = colors.key,
                ),
                pressedColor = colors.pressedKey,
                description = candidate.text,
                onTap = { onKey(if (fix != null) KeyAction.FixCharacter(candidate.text) else KeyAction.PickCandidate(candidate.text)) },
                onLongPress = if (fix == null && candidate.remembered) ({ onAskForget(candidate.text) }) else null,
                longPressLabel = if (fix == null && candidate.remembered) forget else null,
                stateDescription = listOfNotNull(if (candidate.remembered) remembered else null, if (framed) current else null)
                    .joinToString(", ")
                    .ifEmpty { null },
            ) {
                Text(
                    text = candidate.text,
                    color = if (candidate.remembered) colors.remembered else colors.label,
                    fontSize = dimensions.candidateSize,
                    maxLines = if (candidate.sentence) 1 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (fix == null) {
            items(state.translations) { text ->
                PressableBox(
                    modifier = Modifier
                        .height(dimensions.rowHeight)
                        .padding(2.dp)
                        .keyShadow(colors.keyShadow),
                    color = colors.key,
                    pressedColor = colors.pressedKey,
                    description = text,
                    onTap = { onKey(KeyAction.PickTranslation(text)) },
                ) {
                    Text(text = text, color = colors.label, fontSize = dimensions.candidateSize)
                }
            }
        }
    }
}
