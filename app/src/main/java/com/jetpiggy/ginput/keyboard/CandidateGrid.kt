package com.jetpiggy.ginput.keyboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
 * 候選字格：按候選列的 ▼ 後鋪滿鍵盤的位置，列出全部候選字。
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
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 52.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(keyArea),
    ) {
        // 整句候選長，佔一整行。
        itemsIndexed(
            state.candidates,
            span = { _, candidate -> GridItemSpan(if (candidate.sentence) maxLineSpan else 1) },
        ) { index, candidate ->
            PressableBox(
                modifier = Modifier
                    .height(dimensions.rowHeight)
                    .padding(2.dp)
                    .keyShadow(colors.keyShadow),
                color = colors.candidateBackground(
                    remembered = candidate.remembered,
                    marked = state.markTopCandidate && index == 0,
                    plain = colors.key,
                ),
                pressedColor = colors.pressedKey,
                description = candidate.text,
                onTap = { onKey(KeyAction.PickCandidate(candidate.text)) },
                onLongPress = if (candidate.remembered) ({ onAskForget(candidate.text) }) else null,
                longPressLabel = if (candidate.remembered) forget else null,
                stateDescription = if (candidate.remembered) remembered else null,
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
    }
}
