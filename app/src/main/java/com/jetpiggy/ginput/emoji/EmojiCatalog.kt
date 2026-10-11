package com.jetpiggy.ginput.emoji

import android.content.Context
import android.graphics.Paint
import com.jetpiggy.ginput.engine.EmojiKeywords

data class EmojiItem(val emoji: String, val variants: List<String>)

data class EmojiCategory(val id: String, val icon: String, val items: List<EmojiItem>)

/** Emoji 面板的資料（tools/dictgen/build_emoji.py 建置）。 */
object EmojiCatalog {

    /** 分類的次序和分頁上的圖示；代號和 build_emoji.py 一致。 */
    private val CategoryIcons = linkedMapOf(
        "smileys" to "😀",
        "people" to "👋",
        "nature" to "🐶",
        "food" to "🍔",
        "travel" to "🚗",
        "activities" to "⚽",
        "objects" to "💡",
        "symbols" to "❤️",
        "flags" to "🏁",
    )

    /** 會讀檔，不要在主執行緒呼叫。手機字型顯示不到的 Emoji 會被篩走。 */
    fun load(context: Context): List<EmojiCategory> {
        val paint = Paint()
        val byCategory = LinkedHashMap<String, MutableList<EmojiItem>>()
        context.assets.open("emoji/emoji.tsv").bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.isEmpty() || line.startsWith("#")) continue
                val parts = line.split('\t')
                val emoji = parts[1]
                if (!paint.hasGlyph(emoji)) continue
                val variants = parts.getOrNull(2).orEmpty().split(' ').filter { it.isNotEmpty() && paint.hasGlyph(it) }
                byCategory.getOrPut(parts[0]) { mutableListOf() } += EmojiItem(emoji, variants)
            }
        }
        return CategoryIcons.mapNotNull { (id, icon) -> byCategory[id]?.let { EmojiCategory(id, icon, it) } }
    }

    /** Emoji 關鍵字（build_emoji.py 建置）。只留 Emoji 面板有的，即是手機字型顯示得到的。會讀檔，不要在主執行緒呼叫。 */
    fun loadKeywords(context: Context, categories: List<EmojiCategory>): EmojiKeywords {
        val shown = categories.flatMapTo(HashSet()) { category -> category.items.map { it.emoji } }
        return context.assets.open("emoji/keywords.tsv").bufferedReader().useLines { lines ->
            EmojiKeywords.parse(lines) { it in shown }
        }
    }
}
