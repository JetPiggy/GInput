package com.jetpiggy.ginput.engine

/** 組字裏的一個音節：使用者打的字母，以及它可能代表的粵拼音節。 */
internal data class TypedSyllable(
    val typed: String,
    /** 可能代表的粵拼音節（不含聲調）；未打完的音節是以它開頭的全部音節。 */
    val syllables: Set<String>,
)

/** 組字拆成音節的一種拆法。complete 為 false 時，最後一個音節未打完。 */
internal data class SyllableSplit(val syllables: List<TypedSyllable>, val complete: Boolean)

/**
 * 粵拼的拼法：把組字拆成音節，並找出每個音節代表哪些粵拼音節。
 * 寬鬆拼法開啟時，也接受懶音和港式拼法（looseSpellings）；規則見 docs/spec/v1.md 的「粵拼」。
 */
internal class JyutpingSpeller(syllables: Set<String>) {
    private val strict = Spellings()
    private val loose = Spellings()

    init {
        for (syllable in syllables) {
            strict.add(syllable, syllable)
            loose.add(syllable, syllable)
            for (spelling in looseSpellings(syllable)) loose.add(spelling, syllable)
        }
    }

    /**
     * 組字的拆法，只留音節最少的，最多 MAX_SPLITS 個；拆不到就是空的。
     * 最後一個音節可以未打完，但前面最少要有一個完整的音節，所以單獨一個未打完的音節（例如 n）拆不到。
     */
    fun split(input: String, looseSpelling: Boolean): List<SyllableSplit> {
        val spellings = if (looseSpelling) loose else strict
        val length = input.length
        // fewest[i]：由第 i 個字母到結尾最少要幾個音節。
        val fewest = IntArray(length + 1) { UNREACHABLE }
        fewest[length] = 0
        for (start in length - 1 downTo 0) {
            if (start > 0 && spellings.incomplete(input.substring(start)) != null) fewest[start] = 1
            for (end in start + 1..minOf(length, start + spellings.maxLength)) {
                if (fewest[end] != UNREACHABLE && spellings.complete(input.substring(start, end)) != null) {
                    fewest[start] = minOf(fewest[start], fewest[end] + 1)
                }
            }
        }
        if (fewest[0] == UNREACHABLE) return emptyList()

        val splits = mutableListOf<SyllableSplit>()
        fun walk(start: Int, taken: List<TypedSyllable>) {
            if (splits.size >= MAX_SPLITS) return
            if (start == length) {
                splits += SyllableSplit(taken, complete = true)
                return
            }
            for (end in minOf(length, start + spellings.maxLength) downTo start + 1) {
                if (fewest[end] != fewest[start] - 1) continue
                val typed = input.substring(start, end)
                val matches = spellings.complete(typed) ?: continue
                walk(end, taken + TypedSyllable(typed, matches))
            }
            val rest = input.substring(start)
            val completions = spellings.incomplete(rest)
            if (start > 0 && fewest[start] == 1 && completions != null && splits.size < MAX_SPLITS) {
                splits += SyllableSplit(taken + TypedSyllable(rest, completions), complete = false)
            }
        }
        walk(0, emptyList())
        return splits
    }

    /** 拼法和它代表的粵拼音節；incomplete 是拼法打到一半（不包括整個拼法）。 */
    private class Spellings {
        private val complete = HashMap<String, MutableSet<String>>()
        private val incomplete = HashMap<String, MutableSet<String>>()
        var maxLength = 0
            private set

        fun add(spelling: String, syllable: String) {
            complete.getOrPut(spelling) { LinkedHashSet() } += syllable
            for (end in 1 until spelling.length) incomplete.getOrPut(spelling.substring(0, end)) { LinkedHashSet() } += syllable
            maxLength = maxOf(maxLength, spelling.length)
        }

        fun complete(typed: String): Set<String>? = complete[typed]

        fun incomplete(typed: String): Set<String>? = incomplete[typed]
    }

    internal companion object {
        const val MAX_SPLITS = 8
        private const val UNREACHABLE = Int.MAX_VALUE / 2

        /** 聲母；較長的排先，ngaa 才會拆成 ng + aa。 */
        private val INITIALS = listOf("ng", "gw", "kw", "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h", "w", "z", "c", "s", "j")

        /** 港式拼法裏整個音節的寫法，不能由聲母和韻母分別推導。 */
        private val WHOLE_SYLLABLES = mapOf(
            "zi" to listOf("tsz"),
            "ci" to listOf("tsz"),
            "si" to listOf("sz", "sze"),
        )

        /** 懶音和港式拼法：粵拼音節 syllable 還可以怎樣拼，不包括 syllable 本身。 */
        fun looseSpellings(syllable: String): Set<String> {
            val (initial, final) = splitSyllable(syllable)
            val spellings = LinkedHashSet<String>()
            for (i in listOf(initial) + looseInitials(initial, final)) {
                for (f in listOf(final) + looseFinals(final)) spellings += i + f
            }
            spellings += WHOLE_SYLLABLES[syllable].orEmpty()
            spellings -= syllable
            // j 寫成 y 時，jyu 是 yu，不是 yyu。
            spellings.removeAll { "yy" in it }
            return spellings
        }

        private fun splitSyllable(syllable: String): Pair<String, String> {
            // 自成音節的 m 和 ng（唔、五），以及 hm、hng。
            for (nasal in listOf("ng", "m")) {
                if (syllable == nasal || syllable == "h$nasal") return syllable.removeSuffix(nasal) to nasal
            }
            val initial = INITIALS.firstOrNull { syllable.startsWith(it) }.orEmpty()
            return initial to syllable.substring(initial.length)
        }

        private fun looseInitials(initial: String, final: String): List<String> = when (initial) {
            // 懶音：n、l 不分；ng 可有可無；gw、kw 在 o 前面讀成 g、k。
            "n" -> listOf("l")
            "l" -> listOf("n")
            "ng" -> listOf("")
            "" -> if (final.first() in "aeiou") listOf("ng") else emptyList()
            "gw" -> if (final.startsWith("o")) listOf("g") else emptyList()
            "kw" -> if (final.startsWith("o")) listOf("k") else emptyList()
            // 港式拼法
            "j" -> listOf("y")
            "z" -> listOf("j", "ch", "ts")
            "c" -> listOf("ch", "ts")
            "s" -> listOf("sh")
            else -> emptyList()
        }

        private fun looseFinals(final: String): List<String> = when {
            // 港式拼法
            final == "aa" -> listOf("a")
            final.startsWith("aa") -> listOf(final.substring(1))
            final.startsWith("oe") -> listOf("eu" + final.substring(2))
            final.startsWith("eo") -> final.substring(2).let { listOf("u$it", "eu$it") }
            final.startsWith("yu") -> final.substring(2).let { listOf("u$it", "ue$it") }
            else -> when (final) {
                "ou" -> listOf("o")
                "i", "ei" -> listOf("ee")
                "u" -> listOf("oo")
                "un" -> listOf("oon")
                "ut" -> listOf("oot")
                "oi" -> listOf("oy")
                "au" -> listOf("ow")
                "e" -> listOf("eh")
                "m" -> listOf("mm")
                else -> emptyList()
            }
        }
    }
}
