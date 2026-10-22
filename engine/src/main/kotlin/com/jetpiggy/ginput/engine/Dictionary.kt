package com.jetpiggy.ginput.engine

/** 字庫：碼表、字頻和詞語。CandidateEngine 只透過這個介面讀資料。 */
interface Dictionary {
    /** 某個輸入方案裏碼完全相同的字；同一個字的三代和五代各佔一項。 */
    fun characters(scheme: Scheme, code: String): List<CharacterEntry>

    /** 以 head 這個字開頭的詞。 */
    fun wordsAfter(head: String): List<WordEntry>

    /**
     * 查找鍵以 prefix 開頭的英文詞，最常用的先出，一樣常用的較短先出；最多 limit 個。
     * 查找鍵是小寫、去掉撇號的詞（don't 的查找鍵是 dont）。
     */
    fun englishWords(prefix: String, limit: Int): List<String>

    /** 英文詞庫有沒有查找鍵剛好是 key 的詞。 */
    fun hasEnglishWord(key: String): Boolean

    /** 英漢詞典（ADR 0012）：查找鍵剛好是 key 的英文字的譯詞，排前的先出。 */
    fun translations(key: String): List<String>

    /** 字庫裏全部粵拼音節（不含聲調），例如 nei、hou。 */
    fun jyutpingSyllables(): Set<String>

    /** 粵拼完全是 syllables 的字和詞；syllables 不含聲調，用空格分隔，例如 "nei hou"。 */
    fun jyutping(syllables: String): List<JyutpingEntry>

    /**
     * 粵拼以 syllables 這幾個完整音節開頭的字和詞，包括完全相同的。
     * 例如 "nei" 會找到「你」和「你好」（nei hou），但不會找到 neoi 開頭的。
     */
    fun jyutpingStartingWith(syllables: String): List<JyutpingEntry>

    /** 某個輸入方案的全部碼和字，不包括符號碼。連打用，在背景執行緒一次載入記憶體。 */
    fun codeTable(scheme: Scheme): List<CodedCharacter>

    /** 全部字在 words.hk 語料裏出現的次數，不包括符號。 */
    fun characterFrequencies(): Map<String, Int>

    /** 符號碼的全部符號（ADR 0010），照 Big5 次序。 */
    fun symbols(): List<String>

    /** 全部詞（二至四個字）和詞頻。 */
    fun allWords(): List<WordFrequency>
}

data class CodedCharacter(val code: String, val entry: CharacterEntry)

data class WordFrequency(
    val word: String,
    /** 在 words.hk 語料裏出現的次數。 */
    val frequency: Int,
)

data class CharacterEntry(
    val character: String,
    val generation: CangjieGeneration,
    /** 在 words.hk 語料裏出現的次數。 */
    val frequency: Int,
    /** Windows 次序的名次，由 1 開始；0 表示沒有名次（不在 Big5 裏）。 */
    val windowsRank: Int,
    val colloquial: Boolean,
    /** 符號碼的符號（ADR 0010），不是中文字。 */
    val symbol: Boolean = false,
)

data class WordEntry(
    /** 詞去掉開頭那個字之後的部分。 */
    val rest: String,
    val frequency: Int,
    val colloquial: Boolean,
)

data class JyutpingEntry(
    /** 字或詞。 */
    val text: String,
    /** 不含聲調、用空格分隔的音節，每個字一個，例如 "nei hou"。 */
    val syllables: String,
    /** 在 words.hk 語料裏出現的次數；一個字有幾個讀音的話，次要讀音按比例打折。 */
    val frequency: Int,
    val colloquial: Boolean,
)
