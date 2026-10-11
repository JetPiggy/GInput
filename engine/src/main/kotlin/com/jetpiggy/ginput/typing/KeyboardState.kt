package com.jetpiggy.ginput.typing

import com.jetpiggy.ginput.engine.Candidate

/** 鍵盤畫面需要的狀態；TypingSession 每次變動後送出一份新的。 */
data class KeyboardState(
    val composition: String = "",
    val candidates: List<Candidate> = emptyList(),
    val englishSuggestions: List<String> = emptyList(),
    val associations: List<String> = emptyList(),
    /** Emoji 關鍵字找到的 Emoji，固定在候選列最右邊、聯想後面；和聯想一起收起。 */
    val keywordEmoji: List<String> = emptyList(),
    /** 「空白鍵選中文」模式下，要標示首選字。 */
    val markTopCandidate: Boolean = false,
    /** 智能全形標點的鍵（「)」除外）現在出全形還是半形。 */
    val fullWidthPunctuation: Boolean = true,
    /** 「)」現在出全形還是半形：跟前面未配對的括號，找不到就和其他鍵一樣。 */
    val fullWidthCloseParenthesis: Boolean = true,
    /** 剛打出的符號的相關符號，剛打的排第一；沒有就是空的。 */
    val relatedSymbols: List<String> = emptyList(),
    /** 相關符號裏現在寫在文字框的那一個，候選列標示它。 */
    val writtenSymbol: String? = null,
    val shift: ShiftState = ShiftState.Off,
) {
    /** 現在按 key 會打出的標點，也是鍵上的標示。 */
    fun punctuation(key: PunctuationKey): String = Punctuation.of(
        key,
        if (key == PunctuationKey.CloseParenthesis) fullWidthCloseParenthesis else fullWidthPunctuation,
    )
}
