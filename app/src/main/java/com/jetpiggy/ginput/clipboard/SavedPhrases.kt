package com.jetpiggy.ginput.clipboard

import org.json.JSONArray

/**
 * 常用短語（docs/spec/v1.md 的「剪貼簿和常用短語」）：最多 MAX_PHRASES 個，每個最多 MAX_LENGTH 字，不重複。
 * 不可變，每次改動回傳新的一份。
 */
data class SavedPhrases(val items: List<String> = emptyList()) {

    val full: Boolean
        get() = items.size >= MAX_PHRASES

    /** 加到最前；已經有的移到最前。空白、太長，或滿了的時候加入新的，都原樣回傳。 */
    fun add(text: String): SavedPhrases = when {
        !accepts(text) -> this
        text in items -> copy(items = listOf(text) + (items - text))
        full -> this
        else -> copy(items = listOf(text) + items)
    }

    fun remove(text: String): SavedPhrases = copy(items = items - text)

    /** 把 old 改成 text，位置不變；text 本來是另一個短語的話，刪掉那一個。 */
    fun edit(old: String, text: String): SavedPhrases {
        if (!accepts(text) || old !in items) return this
        return copy(
            items = items.mapNotNull {
                when (it) {
                    old -> text
                    text -> null
                    else -> it
                }
            },
        )
    }

    /** 把 text 移前（offset 為負）或移後。 */
    fun move(text: String, offset: Int): SavedPhrases {
        val from = items.indexOf(text)
        if (from < 0) return this
        val to = (from + offset).coerceIn(items.indices)
        if (from == to) return this
        return copy(items = items.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun toJson(): String = JSONArray(items).toString()

    companion object {
        const val MAX_PHRASES = 100

        /** 每個短語最多的字數。 */
        const val MAX_LENGTH = 10_000

        fun accepts(text: String): Boolean = text.isNotBlank() && text.codePointCount(0, text.length) <= MAX_LENGTH

        /** 讀 toJson 的內容；格式不對就當作沒有，不合規則的項目略過。 */
        fun fromJson(json: String?): SavedPhrases {
            val array = json?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return SavedPhrases()
            val items = (0 until array.length())
                .mapNotNull { array.opt(it) as? String }
                .filter(::accepts)
                .distinct()
                .take(MAX_PHRASES)
            return SavedPhrases(items)
        }
    }
}
