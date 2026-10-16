package com.jetpiggy.ginput.engine

import kotlin.math.ln
import kotlin.math.max

/**
 * 連打的計分（ADR 0009）：words.hk 的字頻和詞頻當作同一個 unigram。字的次數先扣除它在詞裏出現的次數（最少留一成），
 * 因為字頻也數了詞裏面的字，「係」這類常在詞裏出現的字作為單字會被高估。
 */
internal class SentenceModel(dictionary: Dictionary) {
    private val characterScores = HashMap<String, Double>()
    private val unknownCharacterScore: Double
    private val wordScores = HashMap<String, Double>()

    /** 詞的開頭部分（比整個詞短）和可以接在後面的字，用來沿着拆碼格找詞。 */
    private val nextInWords = HashMap<String, List<String>>()

    init {
        val frequencies = dictionary.characterFrequencies()
        val words = dictionary.allWords().filter { it.frequency > 0 && it.word.characters().size in 2..MAX_WORD_LENGTH }
        val inWords = HashMap<String, Long>()
        for (word in words) {
            for (character in word.word.characters()) inWords[character] = (inWords[character] ?: 0L) + word.frequency
        }
        val standalone = frequencies.mapValues { (character, frequency) ->
            max(frequency - (inWords[character] ?: 0L).toDouble(), frequency * MIN_STANDALONE_SHARE)
        }
        val total = standalone.values.sum() + words.sumOf { it.frequency.toDouble() }
        val denominator = total + SMOOTHING * frequencies.size
        for ((character, count) in standalone) characterScores[character] = ln((count + SMOOTHING) / denominator)
        unknownCharacterScore = ln(SMOOTHING / denominator)
        val next = HashMap<String, MutableSet<String>>()
        for (word in words) {
            wordScores[word.word] = ln(word.frequency / total)
            val characters = word.word.characters()
            for (length in 1 until characters.size) {
                next.getOrPut(characters.take(length).joinToString("")) { LinkedHashSet() } += characters[length]
            }
        }
        for ((prefix, characters) in next) nextInWords[prefix] = characters.toList()
    }

    fun characterScore(character: String): Double = characterScores[character] ?: unknownCharacterScore

    fun wordScore(word: String): Double? = wordScores[word]

    fun isWordPrefix(text: String): Boolean = text in nextInWords

    /** 在詞裏可以接在 prefix 後面的字。 */
    fun nextInWords(prefix: String): List<String> = nextInWords[prefix].orEmpty()

    companion object {
        const val MAX_WORD_LENGTH = 4
        private const val SMOOTHING = 0.5
        private const val MIN_STANDALONE_SHARE = 0.1
    }
}

/** 碼表裏的一個字和它在連打的分數。startsWord：是 words.hk 某個詞的第一個字。 */
internal class ScoredCharacter(val entry: CharacterEntry, val score: Double, val startsWord: Boolean)

/** 一個輸入方案的碼表，連打用，放在記憶體；同一個碼的字按分數由高至低排好。 */
internal class CodeTable(val scheme: Scheme, entries: List<CodedCharacter>, model: SentenceModel) {
    private val byCode = HashMap<String, List<ScoredCharacter>>()
    val maxCodeLength: Int

    init {
        for ((code, coded) in entries.groupBy { it.code }) {
            byCode[code] = coded.map {
                val character = it.entry.character
                ScoredCharacter(it.entry, model.characterScore(character), model.isWordPrefix(character))
            }.sortedByDescending { it.score }
        }
        maxCodeLength = byCode.keys.maxOfOrNull { it.length } ?: 0
    }

    /** 碼完全相同、倉頡版本容許的字，每個字一次，分數高的先。 */
    fun scored(code: String, generations: Set<CangjieGeneration>): List<ScoredCharacter> =
        byCode[code].orEmpty().filter { it.entry.generation in generations }.distinctBy { it.entry.character }

    fun characters(code: String, generations: Set<CangjieGeneration>): List<CharacterEntry> =
        scored(code, generations).map { it.entry }
}

/**
 * 整句候選裏的一個字，用了組字第 start 至第 end 個字母（不包括 end）。
 * 改字之後也用它記下改好的字：那段字母一定是這個字。
 */
data class SentencePart(val text: String, val start: Int, val end: Int) {
    internal fun matches(text: String, start: Int, end: Int) = this.text == text && this.start == start && this.end == end
}

/**
 * 拆碼格：組字第 start 至第 end 個字母剛好是一個字的碼，或者幾個字的碼剛好連成 words.hk 的一個詞，就有一條邊。
 * 每條邊的分數是字詞的分數，加上詞裏面記下的搭配；邊和邊之間的搭配在組句時才加。
 * fixes 是改好的字：碰到那段字母的邊，要在同一段字母剛好是那個字才保留，所以組句一定經過它。
 */
internal class Lattice(
    private val code: String,
    private val tables: List<CodeTable>,
    private val generations: Set<CangjieGeneration>,
    private val model: SentenceModel,
    private val pairs: PairMemory,
    private val fixes: List<SentencePart> = emptyList(),
) {
    /** ends：每個字用的字母在哪裏完結；字的邊只有 end 一個。 */
    class Edge(val start: Int, val end: Int, val text: String, val score: Double, val word: Boolean, val ends: List<Int> = listOf(end)) {
        val first: String = text.substring(0, text.offsetByCodePoints(0, 1))
        val last: String = text.lastCharacter()!!

        /** 每個字和它用的字母。 */
        fun parts(): List<SentencePart> = text.characters().mapIndexed { index, character ->
            SentencePart(character, if (index == 0) start else ends[index - 1], ends[index])
        }
    }

    private val maxCodeLength = tables.maxOf { it.maxCodeLength }
    private val spans = HashMap<String, List<ScoredCharacter>>()
    private val spansByCharacter = HashMap<String, Map<String, ScoredCharacter>>()
    private val endingAt = Array(code.length + 1) { ArrayList<Edge>() }
    private val startingAt = Array(code.length + 1) { ArrayList<Edge>() }

    /** reachable[i]：由第 i 個字母可以沿着邊走到組字的尾。 */
    private val reachable = BooleanArray(code.length + 1)

    init {
        for (start in code.indices) {
            for (end in start + 1..minOf(start + maxCodeLength, code.length)) {
                // 每個碼只取分數最高的幾個字組句，但記下過搭配的字一定取，學過的才用得着；改好的字也一定取。
                spanCharacters(start, end).forEachIndexed { index, scored ->
                    val character = scored.entry.character
                    if (index < CHARACTERS_PER_CODE || pairs.hasPairsBefore(character) || fixes.any { it.matches(character, start, end) }) {
                        add(Edge(start, end, character, scored.score, word = false))
                    }
                }
            }
            addWordsFrom(start)
        }
        reachable[code.length] = true
        for (start in code.length - 1 downTo 0) reachable[start] = startingAt[start].any { reachable[it.end] }
    }

    /** 碼剛好是 code[start, end) 的字，分數高的先；啟用了幾個輸入方案的話合併，每個字一次。 */
    private fun spanCharacters(start: Int, end: Int): List<ScoredCharacter> {
        val span = code.substring(start, end)
        return spans.getOrPut(span) {
            if (tables.size == 1) {
                tables.single().scored(span, generations)
            } else {
                tables.flatMap { it.scored(span, generations) }.distinctBy { it.entry.character }.sortedByDescending { it.score }
            }
        }
    }

    private fun spanByCharacter(start: Int, end: Int): Map<String, ScoredCharacter> =
        spansByCharacter.getOrPut(code.substring(start, end)) { spanCharacters(start, end).associateBy { it.entry.character } }

    /**
     * 由 start 開始沿着字的邊向前走：第一個字要是某個詞的開頭，之後每一步只試在詞裏接得上的字；
     * 走到完整的詞就加一條詞的邊。
     */
    private fun addWordsFrom(start: Int) {
        val stack = ArrayDeque<WordWalk>()
        for (end in start + 1..minOf(start + maxCodeLength, code.length)) {
            for (scored in spanCharacters(start, end)) {
                if (scored.startsWord) stack.addLast(WordWalk(scored.entry.character, listOf(end)))
            }
        }
        while (stack.isNotEmpty()) {
            val walk = stack.removeLast()
            val position = walk.ends.last()
            val next = model.nextInWords(walk.text)
            for (end in position + 1..minOf(position + maxCodeLength, code.length)) {
                val span = spanByCharacter(position, end)
                for (character in next) {
                    if (character !in span) continue
                    val text = walk.text + character
                    val ends = walk.ends + end
                    model.wordScore(text)?.let { add(Edge(start, end, text, it + pairBonus(text), word = true, ends)) }
                    if (ends.size < SentenceModel.MAX_WORD_LENGTH && model.isWordPrefix(text)) {
                        stack.addLast(WordWalk(text, ends))
                    }
                }
            }
        }
    }

    /** 沿着拆碼格找詞時已經接上的字，和每個字用的字母在哪裏完結。 */
    private class WordWalk(val text: String, val ends: List<Int>)

    private fun add(edge: Edge) {
        if (!fixes.all { edge.respects(it) }) return
        endingAt[edge.end] += edge
        startingAt[edge.start] += edge
    }

    /** 不碰到改好的字那段字母，或者在同一段字母剛好是那個字。 */
    private fun Edge.respects(fix: SentencePart): Boolean = end <= fix.start || start >= fix.end || fix in parts()

    private fun pairBonus(text: String): Double =
        text.characters().zipWithNext().sumOf { (a, b) -> learnedBonus(a, b) }

    private fun learnedBonus(previous: String?, next: String): Double {
        if (previous == null) return 0.0
        val count = pairs.count(previous, next)
        return if (count == 0) 0.0 else LEARNED_WEIGHT * ln(1.0 + count)
    }

    /** 整串組字拆得到嗎。 */
    val complete: Boolean get() = reachable[0]

    /** 由組字開頭開始、而且之後的字母仍然拆得到的詞的邊。 */
    fun wordsFromStart(): List<Edge> = startingAt[0].filter { it.word && reachable[it.end] }

    /** 組字開頭的碼可以有多長（之後的字母仍然拆得到）。 */
    fun firstCodeLengths(): List<Int> =
        (1..minOf(maxCodeLength, code.length)).filter { reachable[it] && spanCharacters(0, it).isNotEmpty() }

    /**
     * 分數最高的整句，逐個字列出：k-best Viterbi，每個位置留 BEAM 個不同的句子。previous 是組字前面的那個字（上文），
     * 它和第一個字的搭配也加分。
     */
    fun bestSentence(previous: String?): List<SentencePart>? {
        if (!complete) return null
        val paths = arrayOfNulls<List<Path>>(code.length + 1)
        paths[0] = listOf(Path(0.0, "", previous, before = null, edge = null))
        for (end in 1..code.length) {
            val byText = HashMap<String, Path>()
            for (edge in endingAt[end]) {
                if (!reachable[edge.end]) continue
                for (path in paths[edge.start] ?: continue) {
                    val score = path.score + edge.score + learnedBonus(path.last, edge.first)
                    val text = path.text + edge.text
                    val old = byText[text]
                    if (old == null || old.score < score) byText[text] = Path(score, text, edge.last, path, edge)
                }
            }
            paths[end] = byText.values.sortedWith(compareByDescending<Path> { it.score }.thenBy { it.text }).take(BEAM)
        }
        val best = paths[code.length]?.firstOrNull() ?: return null
        return generateSequence(best) { it.before }.mapNotNull { it.edge }.toList().asReversed().flatMap { it.parts() }
    }

    /** 組句時的一句：before 和 edge 是它由哪一句加上哪一條邊而來，最後才逐個字列出。 */
    private class Path(val score: Double, val text: String, val last: String?, val before: Path?, val edge: Edge?)

    companion object {
        private const val BEAM = 10

        /** 組句時每個碼最多用多少個字（記下過搭配的字不計）。 */
        private const val CHARACTERS_PER_CODE = 10

        /** 記下的搭配：每對加 LEARNED_WEIGHT × ln(1 + 次數)。夠大，打過一次的句子再打時大多排第一。 */
        private const val LEARNED_WEIGHT = 4.0
    }
}
