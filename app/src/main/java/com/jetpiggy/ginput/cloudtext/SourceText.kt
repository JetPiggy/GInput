package com.jetpiggy.ginput.cloudtext

/** 對方 App 的文字框，給讀取和取代原文用。Android 的實作是 InputConnectionEditor；測試用假的。 */
interface SourceField {
    /** 選取的文字；沒有選取或讀不到就回傳 null。 */
    fun selectedText(): String?

    /** 游標前最多 length 個 UTF-16 字元；讀不到就回傳空字串。 */
    fun textBeforeCursor(length: Int): String

    /** 游標後最多 length 個 UTF-16 字元；讀不到就回傳空字串。 */
    fun textAfterCursor(length: Int): String

    /** 用 text 取代選取的文字。 */
    fun replaceSelection(text: String)

    /** 刪掉游標前 before 個和游標後 after 個 UTF-16 字元，再在游標處寫入 text。 */
    fun replaceAroundCursor(before: Int, after: Int, text: String)
}

/**
 * 原文：選取了文字就是選取的部分；沒有選取就是整個文字框，即游標前 before 個和游標後 after 個字元。
 * 送出的是去掉前後空白的 trimmed，取代時保留原來前後的空白。規則見 docs/spec/v1.md 的「雲端翻譯和改寫」。
 */
data class SourceText(val text: String, val selection: Boolean, val before: Int = 0, val after: Int = 0) {

    val trimmed: String
        get() = text.trim()

    /** 取代原文的文字：結果加上原文前後的空白。 */
    fun withResult(result: String): String =
        text.takeWhile(Char::isWhitespace) + result + text.takeLastWhile(Char::isWhitespace)

    companion object {
        /** 原文最多幾個 UTF-16 字元，和 backend/ai 的 MAX_TEXT_LENGTH 一樣。 */
        const val MAX_LENGTH = 1000

        /** hasSelection 是輸入法最近收到的選取範圍是否有文字。 */
        fun read(field: SourceField, hasSelection: Boolean): SourceRead {
            if (hasSelection) {
                // 選取了卻讀不到的話，不可以當作整個文字框，否則取代時會連選取的文字一起蓋掉。
                val selected = field.selectedText()?.takeIf { it.isNotEmpty() } ?: return SourceRead.Unreadable
                return check(SourceText(selected, selection = true))
            }
            // 多讀一個字元：讀滿了就表示文字框可能還有更多。
            val before = field.textBeforeCursor(MAX_LENGTH + 1)
            val after = field.textAfterCursor(MAX_LENGTH + 1)
            if (before.length + after.length > MAX_LENGTH) return SourceRead.TooLong
            return check(SourceText(before + after, selection = false, before = before.length, after = after.length))
        }

        private fun check(source: SourceText): SourceRead = when {
            source.text.length > MAX_LENGTH -> SourceRead.TooLong
            source.trimmed.isEmpty() -> SourceRead.Empty
            else -> SourceRead.Found(source)
        }

        /**
         * 原文沒有改動才用 result 取代它，回傳是否已經取代。整個文字框的話，游標移動過也可以，
         * 按現在游標前後的長度刪除。
         */
        fun replace(field: SourceField, source: SourceText, result: String, hasSelection: Boolean): Boolean {
            val current = (read(field, hasSelection) as? SourceRead.Found)?.source ?: return false
            if (current.text != source.text || current.selection != source.selection) return false
            val text = source.withResult(result)
            if (current.selection) {
                field.replaceSelection(text)
            } else {
                field.replaceAroundCursor(current.before, current.after, text)
            }
            return true
        }
    }
}

sealed interface SourceRead {
    data class Found(val source: SourceText) : SourceRead

    /** 文字框沒有文字，或只有空白。 */
    data object Empty : SourceRead

    /** 超過 SourceText.MAX_LENGTH。 */
    data object TooLong : SourceRead

    /** 選取了文字，但文字框不肯交出來。 */
    data object Unreadable : SourceRead
}
