package com.jetpiggy.ginput.engine

/**
 * 搭配記憶：開了連打時，使用者寫入的中文裏前後相連的兩個字和次數。組整句候選時，記下的搭配加分。
 * 只存在手機上；onChanged 收到最新的全部內容（最近記下的排最後），由呼叫者負責儲存。所有方法都要在同一個執行緒呼叫。
 */
class PairMemory(
    initial: List<LearnedPair> = emptyList(),
    private val onChanged: (List<LearnedPair>) -> Unit = {},
    /** 最多記多少對；超過就刪到九成。 */
    private val maxPairs: Int = MAX_PAIRS,
) {
    /** 鍵是兩個字連在一起；次序由舊到新。 */
    private val counts = LinkedHashMap<String, Int>()

    /** 每個字是多少對搭配的後一個字。 */
    private val asNext = HashMap<String, Int>()

    init {
        for (pair in initial) {
            if (pair.count > 0 && pair.previous.isSingleIdeograph() && pair.next.isSingleIdeograph()) {
                if (counts.put(pair.previous + pair.next, pair.count) == null) addNext(pair.next)
            }
        }
    }

    fun count(previous: String, next: String): Int = counts[previous + next] ?: 0

    /** 這個字有沒有作為後一個字記下過。 */
    fun hasPairsBefore(next: String): Boolean = next in asNext

    /** before 是寫入前游標前的文字，它最後一個字和 text 的第一個字也算一對。只記中文字之間的搭配。 */
    fun record(before: String, text: String) {
        val characters = text.characters()
        val previous = before.lastCharacter()
        val pairs = (listOfNotNull(previous) + characters).zipWithNext()
            .filter { (a, b) -> a.isSingleIdeograph() && b.isSingleIdeograph() }
        if (pairs.isEmpty()) return
        for ((a, b) in pairs) {
            val key = a + b
            val old = counts.remove(key)
            counts[key] = (old ?: 0) + 1
            if (old == null) addNext(b)
        }
        if (counts.size > maxPairs) trim()
        onChanged(snapshot())
    }

    /** 設定頁的「清除全部記憶」。即使本來已經是空的，也通知儲存，確保檔案清空。 */
    fun clear() {
        counts.clear()
        asNext.clear()
        onChanged(emptyList())
    }

    /** 太多的話，刪掉次數最少的；一樣少的，先刪最舊的。 */
    private fun trim() {
        val excess = counts.size - maxPairs * 9 / 10
        val oldest = counts.entries.withIndex()
            .sortedWith(compareBy<IndexedValue<Map.Entry<String, Int>>> { it.value.value }.thenBy { it.index })
            .take(excess)
            .map { it.value.key }
        for (key in oldest) {
            counts.remove(key)
            val next = key.substring(key.offsetByCodePoints(0, 1))
            val left = (asNext[next] ?: 1) - 1
            if (left > 0) asNext[next] = left else asNext.remove(next)
        }
    }

    private fun addNext(next: String) {
        asNext[next] = (asNext[next] ?: 0) + 1
    }

    private fun snapshot(): List<LearnedPair> = counts.map { (key, count) ->
        val split = key.offsetByCodePoints(0, 1)
        LearnedPair(key.substring(0, split), key.substring(split), count)
    }

    companion object {
        const val MAX_PAIRS = 20_000
    }
}

data class LearnedPair(val previous: String, val next: String, val count: Int)

/** 逐個字（code point）拆開，香港增補字符集裏 BMP 以外的字也算一個。 */
internal fun String.characters(): List<String> {
    val result = ArrayList<String>(length)
    var index = 0
    while (index < length) {
        val end = offsetByCodePoints(index, 1)
        result += substring(index, end)
        index = end
    }
    return result
}

internal fun String.lastCharacter(): String? =
    if (isEmpty()) null else substring(offsetByCodePoints(length, -1))

internal fun String.isSingleIdeograph(): Boolean =
    isNotEmpty() && Character.charCount(codePointAt(0)) == length && Character.isIdeographic(codePointAt(0))
