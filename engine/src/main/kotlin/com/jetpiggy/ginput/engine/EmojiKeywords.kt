package com.jetpiggy.ginput.engine

/**
 * Emoji 關鍵字（tools/dictgen/build_emoji.py 建置）：文字以某個關鍵字結尾，就提示相關的 Emoji。
 * 規則見 docs/spec/v1.md 的「Emoji 關鍵字」。
 */
class EmojiKeywords(emojiByKeyword: Map<String, List<String>>) {
    private val emojiByKeyword = emojiByKeyword.filterValues { it.isNotEmpty() }
    private val longestKeyword = this.emojiByKeyword.keys.maxOfOrNull { it.length } ?: 0

    /** text 結尾的關鍵字之中最長那個的 Emoji，最多 limit 個；沒有就是空的。 */
    fun emojiAfter(text: String, limit: Int = DEFAULT_LIMIT): List<String> {
        for (length in minOf(longestKeyword, text.length) downTo 1) {
            emojiByKeyword[text.substring(text.length - length)]?.let { return it.take(limit) }
        }
        return emptyList()
    }

    companion object {
        const val DEFAULT_LIMIT = 3

        /**
         * keywords.tsv 的內容：每行「關鍵字<Tab>Emoji」，Emoji 以空格分隔、排前的先出；# 開頭的是註解。
         * displayable 篩走手機字型顯示不到的 Emoji；Emoji 全部篩走了的關鍵字不收。
         */
        fun parse(lines: Sequence<String>, displayable: (String) -> Boolean = { true }): EmojiKeywords =
            EmojiKeywords(
                lines.filter { it.isNotEmpty() && !it.startsWith("#") }.associate { line ->
                    val emoji = line.substringAfter('\t').split(' ').filter { it.isNotEmpty() && displayable(it) }
                    line.substringBefore('\t') to emoji
                },
            )
    }
}
