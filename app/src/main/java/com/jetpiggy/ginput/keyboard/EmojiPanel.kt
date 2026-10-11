package com.jetpiggy.ginput.keyboard

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetpiggy.ginput.R
import com.jetpiggy.ginput.emoji.EmojiCategory
import com.jetpiggy.ginput.emoji.EmojiItem

private const val RECENT = "recent"

/**
 * Emoji 面板：取代按鍵區，高度相同（keyArea）。上面是 Emoji 格，下面一行是返回鍵（回到字母頁）、分類分頁和 Delete。
 * 長按有膚色變體的 Emoji，候選列會列出各種膚色。
 */
@Composable
internal fun EmojiPanel(
    categories: List<EmojiCategory>,
    recents: List<String>,
    keyArea: Dp,
    colors: KeyboardColors,
    onKey: (KeyAction) -> Unit,
    onShowVariants: (List<String>) -> Unit,
    onBackToLetters: () -> Unit,
) {
    val dimensions = LocalKeyboardDimensions.current
    val skinTones = stringResource(R.string.emoji_skin_tones)
    // 未選分類時：有最近使用就先看最近使用；分類資料可能稍後才載入完成。
    var selected by remember { mutableStateOf<String?>(null) }
    val current = selected ?: if (recents.isNotEmpty()) RECENT else categories.firstOrNull()?.id
    val shown = when (current) {
        RECENT -> recents.map { EmojiItem(it, emptyList()) }
        else -> categories.firstOrNull { it.id == current }?.items.orEmpty()
    }

    key(current) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 44.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(keyArea - dimensions.rowHeight),
        ) {
            items(shown) { item ->
                PressableBox(
                    modifier = Modifier
                        .height(44.dp)
                        .padding(2.dp),
                    color = Color.Transparent,
                    pressedColor = colors.pressedKey,
                    description = item.emoji,
                    onTap = { onKey(KeyAction.CommitEmoji(item.emoji)) },
                    onLongPress = if (item.variants.isEmpty()) null else ({ onShowVariants(listOf(item.emoji) + item.variants) }),
                    longPressLabel = if (item.variants.isEmpty()) null else skinTones,
                ) {
                    Text(text = item.emoji, fontSize = 26.sp)
                }
            }
        }
    }

    KeyRow {
        BackToLettersKey(colors, onBackToLetters)
        LazyRow(
            modifier = Modifier
                .weight(7f)
                .fillMaxHeight(),
        ) {
            item {
                CategoryTab("🕘", stringResource(R.string.emoji_recent), current == RECENT, colors) { selected = RECENT }
            }
            items(categories) { category ->
                CategoryTab(category.icon, categoryName(category.id), current == category.id, colors) {
                    selected = category.id
                }
            }
        }
        DeleteKey(colors, weight = 1.5f, onKey = onKey)
    }
}

/** TalkBack 讀出的分類名稱；代號和 EmojiCatalog 一致。 */
@Composable
private fun categoryName(id: String): String = stringResource(
    when (id) {
        "smileys" -> R.string.emoji_smileys
        "people" -> R.string.emoji_people
        "nature" -> R.string.emoji_nature
        "food" -> R.string.emoji_food
        "travel" -> R.string.emoji_travel
        "activities" -> R.string.emoji_activities
        "objects" -> R.string.emoji_objects
        "symbols" -> R.string.emoji_symbols
        else -> R.string.emoji_flags
    },
)

@Composable
private fun CategoryTab(icon: String, name: String, selected: Boolean, colors: KeyboardColors, onTap: () -> Unit) {
    PressableBox(
        modifier = Modifier
            .fillMaxHeight()
            .width(44.dp)
            .padding(vertical = 5.dp, horizontal = 2.dp),
        color = if (selected) colors.functionKey else Color.Transparent,
        pressedColor = colors.pressedKey,
        description = name,
        stateDescription = if (selected) stringResource(R.string.state_selected) else null,
        onTap = onTap,
    ) {
        Text(text = icon, fontSize = 20.sp)
    }
}
