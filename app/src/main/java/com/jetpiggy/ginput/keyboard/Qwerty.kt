package com.jetpiggy.ginput.keyboard

/** 鍵盤上三行字母的次序。 */
val QWERTY_ROWS: List<String> = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

/** 長按第一行字母輸入的數字；其他字母回傳 null。 */
fun topRowDigit(letter: Char): Char? {
    val index = QWERTY_ROWS[0].indexOf(letter.lowercaseChar())
    return if (index < 0) null else "1234567890"[index]
}

/** 倉頡的 26 個字根，依 A 到 Z 排列；X 是「難」，Z 是「重」。 */
private const val CANGJIE_RADICALS = "日月金木水火土竹戈十大中一弓人心手口尸廿山女田難卜重"

fun cangjieRadical(letter: Char): Char {
    val index = letter.lowercaseChar() - 'a'
    require(index in CANGJIE_RADICALS.indices) { "不是英文字母：$letter" }
    return CANGJIE_RADICALS[index]
}
