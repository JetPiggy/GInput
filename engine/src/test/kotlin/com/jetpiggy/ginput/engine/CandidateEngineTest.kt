package com.jetpiggy.ginput.engine

import com.jetpiggy.ginput.engine.CangjieGeneration.Fifth
import com.jetpiggy.ginput.engine.CangjieGeneration.Third
import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateEngineTest {

    private fun entry(
        character: String,
        frequency: Int = 0,
        windowsRank: Int = 0,
        colloquial: Boolean = false,
        generation: CangjieGeneration = Third,
    ) = CharacterEntry(character, generation, frequency, windowsRank, colloquial)

    private fun engine(
        vararg codes: Pair<Pair<Scheme, String>, List<CharacterEntry>>,
        words: Map<String, List<WordEntry>> = emptyMap(),
    ) = CandidateEngine(FakeDictionary(mapOf(*codes), words))

    private fun CandidateEngine.texts(code: String, settings: EngineSettings = EngineSettings()) =
        candidates(code, settings).map { it.text }

    private val sharedCode = engine(
        (Scheme.Quick to "ru") to listOf(
            entry("吧", frequency = 900, windowsRank = 3),
            entry("嘅", frequency = 100, colloquial = true),
            entry("兄", frequency = 500, windowsRank = 2),
        ),
    )

    @Test
    fun frequencyOrderPutsColloquialCharactersFirst() {
        assertEquals(listOf("嘅", "吧", "兄"), sharedCode.texts("ru"))
    }

    @Test
    fun withoutColloquialPriorityFrequencyDecides() {
        assertEquals(listOf("吧", "兄", "嘅"), sharedCode.texts("ru", EngineSettings(colloquialPriority = false)))
    }

    @Test
    fun windowsOrderFollowsRanksAndPutsUnrankedLast() {
        val settings = EngineSettings(sharedCodeOrder = SharedCodeOrder.Windows)
        assertEquals(listOf("兄", "吧", "嘅"), sharedCode.texts("ru", settings))
    }

    @Test
    fun equalFrequencyFallsBackToWindowsRank() {
        val engine = engine((Scheme.Quick to "of") to listOf(entry("係", windowsRank = 3), entry("伙", windowsRank = 1)))
        assertEquals(listOf("伙", "係"), engine.texts("of"))
    }

    @Test
    fun generationSettingFiltersCodes() {
        val engine = engine((Scheme.Cangjie to "xkb") to listOf(entry("冇", generation = Fifth)))
        val cangjie = EngineSettings(schemes = listOf(Scheme.Cangjie))
        assertEquals(listOf("冇"), engine.texts("xkb", cangjie))
        assertEquals(emptyList<String>(), engine.texts("xkb", cangjie.copy(cangjieGenerations = setOf(Third))))
    }

    @Test
    fun characterWithTheSameCodeInBothGenerationsAppearsOnce() {
        val engine = engine((Scheme.Cangjie to "ab") to listOf(entry("明"), entry("明", generation = Fifth)))
        assertEquals(listOf("明"), engine.texts("ab", EngineSettings(schemes = listOf(Scheme.Cangjie))))
    }

    @Test
    fun schemesMergeInSchemeOrderWithoutDuplicates() {
        val engine = engine(
            (Scheme.Quick to "ab") to listOf(entry("明", frequency = 10), entry("晴", frequency = 5)),
            (Scheme.Cangjie to "ab") to listOf(entry("明", frequency = 10), entry("昍", frequency = 1)),
        )
        val quickFirst = engine.candidates("ab", EngineSettings(schemes = listOf(Scheme.Quick, Scheme.Cangjie)))
        assertEquals(listOf("明", "晴", "昍"), quickFirst.map { it.text })
        assertEquals(listOf(Scheme.Quick, Scheme.Quick, Scheme.Cangjie), quickFirst.map { it.scheme })
        val cangjieFirst = EngineSettings(schemes = listOf(Scheme.Cangjie, Scheme.Quick))
        assertEquals(listOf("明", "昍", "晴"), engine.texts("ab", cangjieFirst))
    }

    @Test
    fun codesIgnoreCaseAndMustBeLetters() {
        val engine = engine((Scheme.Quick to "ab") to listOf(entry("明")))
        assertEquals(listOf("明"), engine.texts("AB"))
        assertEquals(emptyList<String>(), engine.texts(""))
        assertEquals(emptyList<String>(), engine.texts("a1"))
    }

    @Test
    fun associationsAreOrderedByFrequency() {
        val engine = engine(
            words = mapOf(
                "唔" to listOf(WordEntry("該", 164, true), WordEntry("係", 5895, true), WordEntry("好", 2854, true)),
            ),
        )
        assertEquals(listOf("係", "好", "該"), engine.associations("我唔", EngineSettings()))
    }

    @Test
    fun associationsHideColloquialWordsWithoutColloquialPriority() {
        val engine = engine(words = mapOf("我" to listOf(WordEntry("哋", 900, true), WordEntry("們", 100, false))))
        assertEquals(listOf("哋", "們"), engine.associations("我", EngineSettings()))
        assertEquals(listOf("們"), engine.associations("我", EngineSettings(colloquialPriority = false)))
    }

    @Test
    fun associationsFollowTheLastCharacterEvenOutsideTheBmp() {
        val engine = engine(words = mapOf("𨋢" to listOf(WordEntry("門", 3, false))))
        assertEquals(listOf("門"), engine.associations("搭𨋢", EngineSettings()))
        assertEquals(emptyList<String>(), engine.associations("", EngineSettings()))
    }

    @Test
    fun englishSuggestionsPreferCommonThenShortWords() {
        val engine = CandidateEngine(
            FakeDictionary(english = mapOf("the" to 1, "that" to 2, "them" to 4, "there" to 4, "theory" to 9)),
        )
        assertEquals(listOf("the", "that", "them"), engine.englishSuggestions("th"))
    }

    @Test
    fun englishSuggestionsSkipTheTypedWordAndFollowItsCase() {
        val engine = CandidateEngine(FakeDictionary(english = mapOf("hello" to 3, "help" to 2, "don't" to 3)))
        assertEquals(listOf("help", "hello"), engine.englishSuggestions("hel"))
        assertEquals(listOf("Help", "Hello"), engine.englishSuggestions("Hel"))
        assertEquals(listOf("HELP", "HELLO"), engine.englishSuggestions("HEL"))
        assertEquals(emptyList<String>(), engine.englishSuggestions("help"))
        assertEquals(listOf("don't"), engine.englishSuggestions("dont"))
    }

    private val translating = CandidateEngine(
        FakeDictionary(
            english = mapOf("duck" to 3, "hope" to 3, "hop" to 5, "peter" to 9, "pet" to 5, "ox" to 3),
            translations = mapOf(
                "duck" to listOf("鴨", "鴨仔"),
                "hope" to listOf("希望"),
                "hop" to listOf("跳"),
                "pet" to listOf("寵物"),
                "ox" to listOf("牛"),
            ),
        ),
    )

    @Test
    fun translationsNeedAWholeWordOfAtLeastThreeLetters() {
        assertEquals(listOf("鴨", "鴨仔"), translating.translations("duck"))
        assertEquals(listOf("鴨", "鴨仔"), translating.translations("Duck"))
        assertEquals(emptyList<String>(), translating.translations("duc"))
        assertEquals(emptyList<String>(), translating.translations("ox"))
    }

    @Test
    fun translationsFollowInflectedWords() {
        assertEquals(listOf("鴨", "鴨仔"), translating.translations("ducks"))
        assertEquals(listOf("希望"), translating.translations("hoped"))
        assertEquals(listOf("跳"), translating.translations("hopped"))
        // peter 本身是英文詞庫的字：沒有譯詞也不去查 pet。
        assertEquals(emptyList<String>(), translating.translations("peter"))
    }

    private val jyutping = CandidateEngine(
        FakeDictionary(
            characters = mapOf((Scheme.Cangjie to "nei") to listOf(entry("屍"))),
            jyutping = listOf(
                JyutpingEntry("你", "nei", 100, colloquial = false),
                JyutpingEntry("尼", "nei", 10, colloquial = false),
                JyutpingEntry("理", "lei", 60, colloquial = false),
                JyutpingEntry("嚟", "lei", 20, colloquial = true),
                JyutpingEntry("好", "hou", 80, colloquial = false),
                JyutpingEntry("你好", "nei hou", 5, colloquial = false),
                JyutpingEntry("離開", "lei hoi", 8, colloquial = false),
                JyutpingEntry("開", "hoi", 30, colloquial = false),
                JyutpingEntry("嗎", "maa", 40, colloquial = false),
            ),
        ),
    )

    private val jyutpingOnly = EngineSettings(schemes = listOf(Scheme.Jyutping))

    private fun CandidateEngine.withLengths(code: String, settings: EngineSettings = jyutpingOnly) =
        candidates(code, settings).map { it.text to it.codeLength }

    @Test
    fun jyutpingPutsWordsUsingEveryLetterFirstThenShorterOnes() {
        assertEquals(listOf("你好" to 6, "你" to 3, "尼" to 3), jyutping.withLengths("neihou", jyutpingOnly.copy(looseSpelling = false)))
        assertEquals(listOf("你好" to 6, "你" to 3, "尼" to 3), jyutping.withLengths("neihoumaa", jyutpingOnly.copy(looseSpelling = false)))
    }

    @Test
    fun jyutpingCompletesTheLastSyllable() {
        val strict = jyutpingOnly.copy(looseSpelling = false)
        assertEquals(listOf("你好" to 4, "你" to 3, "尼" to 3), jyutping.withLengths("neih", strict))
    }

    @Test
    fun looseSpellingMatchesHalveTheFrequency() {
        // lei 也配到 nei：「你」100 減半是 50，排在「理」60 後面、「嚟」20 前面。
        assertEquals(listOf("理", "你", "嚟", "尼"), jyutping.texts("lei", jyutpingOnly))
        assertEquals(listOf("理", "嚟"), jyutping.texts("lei", jyutpingOnly.copy(looseSpelling = false)))
        // 「離開」（lei hoi）8 減半是 4，排在照粵拼打的「你好」5 後面。
        assertEquals(listOf("你好", "離開"), jyutping.texts("neih", jyutpingOnly).take(2))
    }

    @Test
    fun jyutpingIgnoresColloquialPriority() {
        val strict = jyutpingOnly.copy(looseSpelling = false)
        assertEquals(listOf("理", "嚟"), jyutping.texts("lei", strict))
        assertEquals(listOf("理", "嚟"), jyutping.texts("lei", strict.copy(colloquialPriority = false)))
    }

    @Test
    fun jyutpingMergesWithOtherSchemesInSchemeOrder() {
        val both = EngineSettings(schemes = listOf(Scheme.Cangjie, Scheme.Jyutping), looseSpelling = false)
        assertEquals(listOf("屍" to 3, "你" to 3, "尼" to 3), jyutping.withLengths("nei", both))
        val jyutpingFirst = both.copy(schemes = listOf(Scheme.Jyutping, Scheme.Cangjie))
        assertEquals(listOf("你", "尼", "屍"), jyutping.texts("nei", jyutpingFirst))
    }

    @Test
    fun lettersThatAreNotJyutpingGiveNoJyutpingCandidates() {
        assertEquals(emptyList<String>(), jyutping.texts("hello", jyutpingOnly))
        assertEquals(emptyList<String>(), jyutping.texts("n", jyutpingOnly))
    }

    @Test
    fun associationsRespectTheLimit() {
        val engine = engine(words = mapOf("香" to (1..30).map { WordEntry("詞$it", it, false) }))
        val associations = engine.associations("香", EngineSettings(), limit = 5)
        assertEquals(listOf("詞30", "詞29", "詞28", "詞27", "詞26"), associations)
    }

    private fun symbol(character: String, windowsRank: Int) =
        CharacterEntry(character, Third, frequency = 0, windowsRank = windowsRank, colloquial = false, symbol = true)

    private val symbolCodes = mapOf(
        (Scheme.Quick to "zd") to listOf(symbol("「", 3), symbol("。", 1), symbol("﹏", 2)),
        (Scheme.Quick to "zi") to listOf(symbol("？", 1)),
    )

    @Test
    fun symbolsFollowBig5OrderInBothOrders() {
        val engine = CandidateEngine(FakeDictionary(symbolCodes))
        assertEquals(listOf("。", "﹏", "「"), engine.texts("zd"))
        assertEquals(listOf("。", "﹏", "「"), engine.texts("zd", EngineSettings(sharedCodeOrder = SharedCodeOrder.Windows)))
        assertEquals(listOf(true, true, true), engine.candidates("zd", EngineSettings()).map { it.symbol })
    }

    @Test
    fun symbolsComeAfterEveryChineseCandidate() {
        val engine = CandidateEngine(FakeDictionary(symbolCodes, jyutping = listOf(JyutpingEntry("自", "zi", 100, false))))
        val quickFirst = EngineSettings(schemes = listOf(Scheme.Quick, Scheme.Jyutping))
        assertEquals(listOf("自", "？"), engine.texts("zi", quickFirst))
        assertEquals(listOf("自", "？"), engine.texts("zi", quickFirst.copy(continuousTyping = true)))
    }

    @Test
    fun rememberedSymbolsStayBehind() {
        val engine = CandidateEngine(
            FakeDictionary(symbolCodes, jyutping = listOf(JyutpingEntry("自", "zi", 100, false))),
            CandidateMemory(mapOf("？" to 5)),
        )
        val candidates = engine.candidates("zi", EngineSettings(schemes = listOf(Scheme.Quick, Scheme.Jyutping)))
        assertEquals(listOf("自" to false, "？" to false), candidates.map { it.text to it.remembered })
    }

    @Test
    fun symbolsTheFontCannotShowAreLeftOut() {
        val dictionary = FakeDictionary(symbolCodes)
        val engine = CandidateEngine(dictionary, canDisplay = { it != "﹏" })
        assertEquals(listOf("。", "「"), engine.texts("zd"))
    }
}
