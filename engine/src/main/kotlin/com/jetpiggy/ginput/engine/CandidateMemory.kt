package com.jetpiggy.ginput.engine

/**
 * 記憶字：使用者選過的字和次數。選過 REMEMBER_AFTER 次或以上的字，在常用度次序下排在候選字最前。
 * 只存在手機上；onChanged 收到最新的全部內容，由呼叫者負責儲存。所有方法都要在同一個執行緒呼叫。
 */
class CandidateMemory(
    initial: Map<String, Int> = emptyMap(),
    private val onChanged: (Map<String, Int>) -> Unit = {},
) {
    private val counts = HashMap(initial)

    fun isRemembered(character: String): Boolean = (counts[character] ?: 0) >= REMEMBER_AFTER

    fun record(character: String) {
        counts[character] = (counts[character] ?: 0) + 1
        onChanged(HashMap(counts))
    }

    fun forget(character: String) {
        if (counts.remove(character) != null) onChanged(HashMap(counts))
    }

    /** 設定頁的「清除全部記憶」。即使本來已經是空的，也通知儲存，確保檔案清空。 */
    fun clear() {
        counts.clear()
        onChanged(emptyMap())
    }

    /**
     * 把記憶字移到最前（最多 MAX_FRONT 個，選得越多越前）並標示；其餘維持原來次序。
     * 只移用盡全部 codeLength 個字母的，免得只用前面幾個字母的粵拼字排到整個詞前面。
     */
    internal fun promote(candidates: List<Candidate>, codeLength: Int): List<Candidate> {
        val front = candidates.withIndex()
            .filter { it.value.codeLength == codeLength && isRemembered(it.value.text) }
            .sortedWith(compareByDescending<IndexedValue<Candidate>> { counts.getValue(it.value.text) }.thenBy { it.index })
            .take(MAX_FRONT)
        if (front.isEmpty()) return candidates
        val moved = front.mapTo(HashSet()) { it.index }
        return front.map { it.value.copy(remembered = true) } + candidates.filterIndexed { index, _ -> index !in moved }
    }

    companion object {
        const val REMEMBER_AFTER = 2
        const val MAX_FRONT = 5
    }
}
