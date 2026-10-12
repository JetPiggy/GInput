package com.jetpiggy.ginput.engine

data class Candidate(
    val text: String,
    val scheme: Scheme,
    /** 這個候選字用了組字前面多少個字母。粵拼可以只用前面一部分，選了之後其餘字母留在組字（分段寫入）。 */
    val codeLength: Int,
    /** 記憶字：因為使用者常選而排到最前。 */
    val remembered: Boolean = false,
    /** 整句候選：連打時由整串組字組成的一句。不記錄成記憶字。 */
    val sentence: Boolean = false,
)

/** 按設定把碼變成候選字，並在寫入中文後提供聯想。 */
class CandidateEngine(
    private val dictionary: Dictionary,
    private val memory: CandidateMemory = CandidateMemory(),
    private val pairs: PairMemory = PairMemory(),
) {
    private val speller by lazy { JyutpingSpeller(dictionary.jyutpingSyllables()) }
    private val sentenceModel by lazy { SentenceModel(dictionary) }
    private val codeTables = listOf(Scheme.Quick, Scheme.Cangjie).associateWith { scheme ->
        lazy { CodeTable(scheme, dictionary.codeTable(scheme), sentenceModel) }
    }

    /**
     * 各輸入方案的候選字按輸入方案次序合併；同一個字只出現一次，留在較前的位置。
     * 常用度次序下，用盡全部字母的記憶字再移到最前；Windows 次序完全不動。
     * 開了連打的話，再加上整句候選和開頭部分的字詞；before 是組字前面的文字（上文）。
     */
    fun candidates(code: String, settings: EngineSettings, before: String = ""): List<Candidate> {
        val normalized = code.lowercase()
        if (normalized.isEmpty() || normalized.any { it !in 'a'..'z' }) return emptyList()
        val seen = HashSet<String>()
        val merged = settings.schemes.flatMap { scheme ->
            val candidates = when (scheme) {
                Scheme.Jyutping -> jyutpingCandidates(normalized, settings)
                Scheme.Cangjie, Scheme.Quick -> codeCandidates(scheme, normalized, settings)
            }
            candidates.filter { seen.add(it.text) }
        }
        val useMemory = settings.rememberCandidates && settings.sharedCodeOrder == SharedCodeOrder.Frequency
        val ordered = if (useMemory) memory.promote(merged, normalized.length) else merged
        if (!settings.continuousTyping) return ordered
        return withContinuousTyping(code, normalized, ordered, settings, before)
    }

    /**
     * 連打（ADR 0009）：組字剛好是一個碼、像英文或者拆不到，就照舊。否則依次出
     * 1. 用盡全部字母的候選字（例如粵拼的「你好」）；
     * 2. 整句候選；
     * 3. 只用前面一部分字母的粵拼字詞；
     * 4. 開頭部分的詞，字多的先出，然後是開頭部分的字，碼長的先出、同一個碼照重碼次序。
     * 選了開頭部分的字詞就分段寫入，其餘字母再組句。
     */
    private fun withContinuousTyping(
        typed: String,
        code: String,
        ordered: List<Candidate>,
        settings: EngineSettings,
        before: String,
    ): List<Candidate> {
        val schemes = settings.schemes.filter { it in codeTables }
        if (schemes.isEmpty() || code.length < 2) return ordered
        // 組字剛好是速成或倉頡的一個碼：次序和位置不變。
        if (ordered.any { it.scheme in codeTables }) return ordered
        if (looksEnglish(typed)) return ordered
        val tables = schemes.map { codeTables.getValue(it).value }
        // 記憶字關了，搭配記憶也不加分。
        val learned = if (settings.rememberCandidates) pairs else NO_PAIRS
        val lattice = Lattice(code, tables, settings.cangjieGenerations, sentenceModel, learned)
        val previous = before.lastCharacter()?.takeIf { it.isSingleIdeograph() }
        val sentence = lattice.bestSentence(previous) ?: return ordered
        val scheme = schemes.first()
        val words = lattice.wordsFromStart()
            .sortedWith(compareByDescending<Lattice.Edge> { it.text.characters().size }.thenByDescending { it.score })
            .map { Candidate(it.text, scheme, codeLength = it.end) }
        val characters = lattice.firstCodeLengths().sortedDescending().flatMap { length ->
            val first = code.substring(0, length)
            tables.flatMap { table -> table.characters(first, settings.cangjieGenerations).map { it to table.scheme } }
                .distinctBy { it.first.character }
                .sortedWith(compareBy(characterOrder(settings)) { it.first })
                .map { (entry, scheme) -> Candidate(entry.character, scheme, codeLength = length) }
        }
        val (whole, partial) = ordered.partition { it.codeLength == code.length }
        return (whole + Candidate(sentence, scheme, code.length, sentence = true) + partial + words + characters)
            .distinctBy { it.text }
    }

    /**
     * 組字像英文：第二個字母起有大寫；或者是英文詞庫的字或任何字的開頭；或者去掉詞尾之後是英文詞庫的字
     * （詞庫缺很多變化形，例如 cats、happened）。
     */
    private fun looksEnglish(typed: String): Boolean {
        if (typed.drop(1).any { it.isUpperCase() }) return true
        val key = typed.lowercase()
        if (dictionary.englishWords(key, 1).isNotEmpty()) return true
        return englishStems(key).any { it.length >= 2 && dictionary.hasEnglishWord(it) }
    }

    private fun englishStems(key: String): Set<String> = buildSet {
        for ((suffix, endings) in INFLECTIONS) {
            if (!key.endsWith(suffix)) continue
            val stem = key.dropLast(suffix.length)
            endings.forEach { add(stem + it) }
            // 尾字母重複：stopped、running。
            if (stem.length >= 2 && stem.last() == stem[stem.length - 2]) add(stem.dropLast(1))
        }
    }

    /** 預先載入連打用的碼表和字詞的分數。要在背景執行緒呼叫，開了連打之後第一次打字才不會卡一下。 */
    fun loadContinuousTyping(settings: EngineSettings) {
        sentenceModel
        settings.schemes.forEach { codeTables[it]?.value }
    }

    private fun codeCandidates(scheme: Scheme, code: String, settings: EngineSettings): List<Candidate> =
        dictionary.characters(scheme, code)
            .filter { it.generation in settings.cangjieGenerations }
            .distinctBy { it.character }
            .sortedWith(characterOrder(settings))
            .map { Candidate(it.character, scheme, codeLength = code.length) }

    /** 預先載入粵拼的拼法。要在背景執行緒呼叫，第一次打粵拼時才不會卡一下。 */
    fun loadJyutping() {
        speller
    }

    /**
     * 粵拼：把組字拆成音節，然後依次出
     * 1. 用盡全部字母的字詞；
     * 2. 最後一個音節未打完的詞（例如 neih 的「你好」）；
     * 3. 只用前面幾個音節的字詞，音節多的先出；選了之後，其餘字母留在組字。
     * 同一組按常用度排，靠寬鬆拼法才配得上的，常用度減半。字頻來自粵語語料，口語字本來就排得前，所以不另外套用口語優先。
     */
    private fun jyutpingCandidates(code: String, settings: EngineSettings): List<Candidate> {
        val lookups = HashMap<String, List<JyutpingEntry>>()
        fun exact(syllables: String) = lookups.getOrPut("=$syllables") { dictionary.jyutping(syllables) }
        fun startingWith(syllables: String) = lookups.getOrPut(syllables) { dictionary.jyutpingStartingWith(syllables) }

        val matches = mutableListOf<JyutpingMatch>()
        for (split in speller.split(code, settings.looseSpelling)) {
            val typed = split.syllables
            val entries = typed.first().syllables.flatMap { first ->
                when {
                    typed.size == 1 -> exact(first)
                    // 第二個音節未打完：找出以第一個音節開頭的全部字詞，再逐個比對。
                    typed.size == 2 && !split.complete -> startingWith(first)
                    else -> exact(first) + typed[1].syllables.flatMap { second -> startingWith("$first $second") }
                }
            }
            for (entry in entries) {
                val syllables = entry.syllables.split(' ')
                if (syllables.size > typed.size || syllables.indices.any { syllables[it] !in typed[it].syllables }) continue
                val group = when {
                    syllables.size < typed.size -> JyutpingMatch.PREFIX
                    split.complete -> JyutpingMatch.WHOLE
                    else -> JyutpingMatch.UNFINISHED
                }
                // 照粵拼打的：完整的音節和打的一樣，未打完的音節以打的字母開頭。
                val strict = syllables.indices.all { index ->
                    val unfinished = !split.complete && index == typed.lastIndex
                    if (unfinished) syllables[index].startsWith(typed[index].typed) else syllables[index] == typed[index].typed
                }
                matches += JyutpingMatch(
                    entry = entry,
                    group = group,
                    syllableCount = syllables.size,
                    codeLength = typed.take(syllables.size).sumOf { it.typed.length },
                    score = if (strict) entry.frequency else entry.frequency / LOOSE_SPELLING_PENALTY,
                )
            }
        }
        val order = compareBy<JyutpingMatch> { it.group }
            .thenByDescending { it.syllableCount }
            .thenByDescending { it.score }
            .thenBy { it.entry.text }
        return matches.sortedWith(order)
            .distinctBy { it.entry.text }
            .map { Candidate(it.entry.text, Scheme.Jyutping, it.codeLength) }
    }

    private class JyutpingMatch(
        val entry: JyutpingEntry,
        val group: Int,
        val syllableCount: Int,
        val codeLength: Int,
        val score: Int,
    ) {
        companion object {
            const val WHOLE = 0
            const val UNFINISHED = 1
            const val PREFIX = 2
        }
    }

    /**
     * 英文建議：補全打到一半的英文字，不包括照打的那個字；大小寫跟隨使用者打的
     * （首字母大寫就首字母大寫，全大寫就全大寫）。
     */
    fun englishSuggestions(typed: String, limit: Int = DEFAULT_ENGLISH_LIMIT): List<String> {
        val key = typed.lowercase().filter { it in 'a'..'z' }
        if (key.isEmpty()) return emptyList()
        return dictionary.englishWords(key, limit + 1)
            .map { it.withCaseOf(typed) }
            .filter { it != typed }
            .distinct()
            .take(limit)
    }

    private fun String.withCaseOf(typed: String): String = when {
        typed.length > 1 && typed.all { it.isUpperCase() } -> uppercase()
        typed.first().isUpperCase() -> replaceFirstChar { it.uppercaseChar() }
        else -> this
    }

    /** 使用者選了這個候選字。 */
    fun recordPick(text: String) = memory.record(text)

    /** 忘記這個記憶字，讓它回到原來的位置。 */
    fun forget(text: String) = memory.forget(text)

    /** 開了連打時，記下寫入的中文裏前後相連的字（搭配記憶）；before 是寫入前游標前的文字。 */
    fun rememberPairs(before: String, text: String) = pairs.record(before, text)

    /** 忘記全部記憶字和搭配記憶。 */
    fun clearMemory() {
        memory.clear()
        pairs.clear()
    }

    /** 以最後寫入的那個字開頭的詞，回傳詞的其餘部分。口語優先關閉時，不出口語詞。 */
    fun associations(
        previous: String,
        settings: EngineSettings,
        limit: Int = DEFAULT_ASSOCIATION_LIMIT,
    ): List<String> {
        if (previous.isEmpty()) return emptyList()
        val head = String(Character.toChars(previous.codePointBefore(previous.length)))
        return dictionary.wordsAfter(head)
            .filter { settings.colloquialPriority || !it.colloquial }
            .sortedWith(compareByDescending<WordEntry> { it.frequency }.thenBy { it.rest })
            .map { it.rest }
            .distinct()
            .take(limit)
    }

    private fun characterOrder(settings: EngineSettings): Comparator<CharacterEntry> {
        val windows = compareBy<CharacterEntry> { if (it.windowsRank > 0) it.windowsRank else Int.MAX_VALUE }
        return when (settings.sharedCodeOrder) {
            // Windows 次序完全不動，口語優先只影響聯想。
            SharedCodeOrder.Windows -> windows.thenBy { it.character }
            SharedCodeOrder.Frequency ->
                compareBy<CharacterEntry> { !(settings.colloquialPriority && it.colloquial) }
                    .thenByDescending { it.frequency }
                    .then(windows)
                    .thenBy { it.character }
        }
    }

    companion object {
        const val DEFAULT_ASSOCIATION_LIMIT = 20
        const val DEFAULT_ENGLISH_LIMIT = 3

        /** 靠寬鬆拼法才配得上的粵拼字詞，常用度除以這個數。 */
        private const val LOOSE_SPELLING_PENALTY = 2

        private val NO_PAIRS = PairMemory()

        /** 英文的詞尾和去掉之後要補回的字母，例如 making → make。 */
        private val INFLECTIONS = listOf(
            "s" to listOf(""),
            "es" to listOf(""),
            "ies" to listOf("y"),
            "ed" to listOf("", "e"),
            "ied" to listOf("y"),
            "ing" to listOf("", "e"),
            "er" to listOf("", "e"),
            "ers" to listOf("", "e"),
            "est" to listOf("", "e"),
            "ly" to listOf(""),
        )
    }
}
