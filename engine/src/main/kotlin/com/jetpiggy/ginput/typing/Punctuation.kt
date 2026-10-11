package com.jetpiggy.ginput.typing

/** 智能全形標點、相關符號和智能刪空格的規則。 */
object Punctuation {

    /** 長按「，」「。」時列出的更多標點。 */
    val moreFullWidth = listOf("、", "；", "：", "？", "！", "「", "」", "『", "』", "（", "）", "《", "》", "……", "——", "～", "·")
    val moreHalfWidth = listOf(";", ":", "?", "!", "'", "\"", "(", ")", "-", "...", "/", "&", "@")

    fun more(fullWidth: Boolean): List<String> = if (fullWidth) moreFullWidth else moreHalfWidth

    fun of(key: PunctuationKey, fullWidth: Boolean): String = if (fullWidth) key.fullWidth else key.halfWidth

    /**
     * 游標前的文字決定 key 出全形還是半形。「)」跟前面未配對的「(」或「（」用同一個寬度，
     * 找不到才和其他鍵一樣按 fullWidthAfter。
     */
    fun fullWidth(key: PunctuationKey, textBefore: String): Boolean {
        if (key == PunctuationKey.CloseParenthesis) {
            var depth = 0
            for (index in textBefore.indices.reversed()) {
                when (textBefore[index]) {
                    ')', '）' -> depth++
                    '(', '（' -> if (depth == 0) return textBefore[index] == '（' else depth--
                }
            }
        }
        return fullWidthAfter(textBefore)
    }

    /**
     * 游標前的文字決定出全形還是半形：略過空格和半形標點，看最近的那個字；
     * 是英文字母或數字就出半形，其餘（中文、全形標點、欄位開頭）出全形。
     */
    fun fullWidthAfter(textBefore: String): Boolean {
        val last = textBefore.lastOrNull { !it.isWhitespace() && !it.isAsciiPunctuation() } ?: return true
        return !(last in 'a'..'z' || last in 'A'..'Z' || last in '0'..'9')
    }

    /**
     * 在 field 打出 symbol 之後，候選列列出的相關符號：symbol 排第一，其餘按表的次序；沒有相關符號就是空的。
     * 電郵和網址欄位只有「.」有相關符號（.com）；密碼和數字欄位沒有。
     */
    fun relatedTo(symbol: String, field: FieldKind = FieldKind.Text): List<String> {
        val table = when (field) {
            FieldKind.Text -> RELATED
            FieldKind.Email, FieldKind.Url -> ADDRESS_RELATED
            else -> return emptyList()
        }
        val group = table[symbol] ?: return emptyList()
        return listOf(symbol) + (group - symbol)
    }

    /** 寫入這段文字之前，要不要刪掉空白鍵自動加上的空格：中文和標點要，其他不要。 */
    fun removesAutoSpace(text: String): Boolean {
        if (text.isEmpty()) return false
        val first = text.codePointAt(0)
        return Character.UnicodeScript.of(first) == Character.UnicodeScript.HAN ||
            first in 0x3000..0x303F ||
            first in 0xFF00..0xFFEF ||
            first in FULL_WIDTH_EXTRAS ||
            (first < 0x80 && first.toChar() in HALF_WIDTH_REMOVERS)
    }

    private fun Char.isAsciiPunctuation() = this in '!'..'/' || this in ':'..'@' || this in '['..'`' || this in '{'..'~'

    /** 全形破折號、刪節號、間隔號和彎引號不在上面兩個區段。 */
    private val FULL_WIDTH_EXTRAS = setOf(0x2014, 0x2026, 0x00B7, 0x201C, 0x201D, 0x2018, 0x2019)
    private const val HALF_WIDTH_REMOVERS = ",.;:?!)]}"

    /** 相關符號：智能全形標點的鍵打出全形或半形都列出同一組；引號沒有全形，只有 " 和 ' 會列出。 */
    private val RELATED: Map<String, List<String>> = buildMap {
        fun group(vararg symbols: String, triggers: Int = 2) {
            symbols.take(triggers).forEach { put(it, symbols.toList()) }
        }
        group("，", ",", "、")
        group("。", ".", "……", "·")
        group("！", "!", "！！", "‼", "❗")
        group("？", "?", "？！", "⁉", "❓")
        group("：", ":")
        group("；", ";")
        group("（", "(", "【", "〔", "［")
        group("）", ")", "】", "〕", "］")
        group("～", "~", "〜")
        group("\"", "“", "”", "「", "」", triggers = 1)
        group("'", "‘", "’", "『", "』", triggers = 1)
    }

    /** 電郵和網址欄位的標點一律半形，打「.」之後方便打 .com。 */
    private val ADDRESS_RELATED: Map<String, List<String>> = mapOf("." to listOf(".", ".com"))
}
