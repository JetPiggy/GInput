package com.jetpiggy.ginput.clipboard

import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

/** 剪貼簿歷史的一項；copiedAt 是複製的時間（System.currentTimeMillis）。 */
data class ClipboardItem(val text: String, val copiedAt: Long)

/**
 * 剪貼簿歷史（docs/spec/v1.md 的「剪貼簿和常用短語」）：最新複製的在前，最多 MAX_ITEMS 項，複製後 KEEP_MILLIS 過期。
 * lastSeen 是見過的最新一次複製的時間，不論有沒有記下；GInput 啟動時用來判斷系統剪貼簿裏的文字是否見過。
 * 不可變，每次改動回傳新的一份。
 */
data class ClipboardHistory(val items: List<ClipboardItem> = emptyList(), val lastSeen: Long = 0) {

    /** 記下 copiedAt 複製的文字：同樣的文字移到最前。空白或太長的文字不記，只當作見過。 */
    fun add(text: String, copiedAt: Long): ClipboardHistory {
        val seen = seen(copiedAt)
        if (!accepts(text)) return seen
        val items = (listOf(ClipboardItem(text, copiedAt)) + items.filter { it.text != text })
            .sortedByDescending { it.copiedAt }
            .take(MAX_ITEMS)
        return seen.copy(items = items)
    }

    /** 見過 copiedAt 的那次複製，但不記下（例如在密碼欄位複製的）。 */
    fun seen(copiedAt: Long): ClipboardHistory = if (copiedAt > lastSeen) copy(lastSeen = copiedAt) else this

    /** 刪除一項；不影響系統剪貼簿。 */
    fun remove(text: String): ClipboardHistory = copy(items = items.filter { it.text != text })

    /** 刪去 now 時已過期的項目。 */
    fun fresh(now: Long): ClipboardHistory {
        val kept = items.filter { !expired(it.copiedAt, now) }
        return if (kept.size == items.size) this else copy(items = kept)
    }

    /** 最早複製的一項過期的時間；沒有項目就是 null。 */
    val nextExpiry: Long?
        get() = items.minOfOrNull { it.copiedAt }?.plus(KEEP_MILLIS)

    fun toJson(): String = JSONObject()
        .put(LAST_SEEN, lastSeen)
        .put(ITEMS, JSONArray(items.map { JSONObject().put(TEXT, it.text).put(COPIED_AT, it.copiedAt) }))
        .toString()

    companion object {
        const val MAX_ITEMS = 20

        /** 超過這個字數的文字不記。 */
        const val MAX_LENGTH = 10_000
        const val KEEP_MILLIS = 60 * 60 * 1000L

        private const val LAST_SEEN = "lastSeen"
        private const val ITEMS = "items"
        private const val TEXT = "text"
        private const val COPIED_AT = "copiedAt"

        fun accepts(text: String): Boolean = text.isNotBlank() && text.codePointCount(0, text.length) <= MAX_LENGTH

        /** 複製超過 KEEP_MILLIS 就過期；時鐘向後調了超過 KEEP_MILLIS 的話也當作過期，免得留得太久。 */
        fun expired(copiedAt: Long, now: Long): Boolean = abs(now - copiedAt) >= KEEP_MILLIS

        /** 讀 toJson 的內容；格式不對的項目略過。內容可能是使用者複製的文字，出錯時不要記錄例外的訊息。 */
        fun fromJson(json: String): ClipboardHistory {
            val root = JSONObject(json)
            val array = root.optJSONArray(ITEMS) ?: JSONArray()
            val items = (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val text = item.opt(TEXT) as? String ?: return@mapNotNull null
                ClipboardItem(text, item.optLong(COPIED_AT, -1)).takeIf { it.copiedAt >= 0 }
            }
            // 由舊到新逐項加入，同樣套用去重和上限。
            return items.sortedBy { it.copiedAt }
                .fold(ClipboardHistory(lastSeen = root.optLong(LAST_SEEN, 0))) { history, item ->
                    history.add(item.text, item.copiedAt)
                }
        }
    }
}
