package com.jetpiggy.ginput.engine

import com.jetpiggy.ginput.engine.CangjieGeneration.Fifth
import com.jetpiggy.ginput.engine.CangjieGeneration.Third
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousTypingTest {

    private fun entry(
        character: String,
        frequency: Int,
        windowsRank: Int = 0,
        generation: CangjieGeneration = Third,
    ) = CharacterEntry(character, generation, frequency, windowsRank, colloquial = false)

    private val quickCodes = mapOf(
        (Scheme.Quick to "hi") to listOf(entry("我", 1000, windowsRank = 2), entry("手", 100, windowsRank = 1)),
        (Scheme.Quick to "vd") to listOf(entry("好", 500), entry("妹", 600)),
        (Scheme.Quick to "of") to listOf(entry("你", 800)),
        (Scheme.Quick to "h") to listOf(entry("竹", 10)),
        (Scheme.Quick to "i") to listOf(entry("戈", 10)),
        (Scheme.Quick to "v") to listOf(entry("女", 50)),
        (Scheme.Quick to "d") to listOf(entry("木", 50)),
    )

    private fun engine(
        codes: Map<Pair<Scheme, String>, List<CharacterEntry>> = quickCodes,
        words: Map<String, List<WordEntry>> = emptyMap(),
        english: Map<String, Int> = emptyMap(),
        jyutping: List<JyutpingEntry> = emptyList(),
        pairs: PairMemory = PairMemory(),
    ) = CandidateEngine(FakeDictionary(codes, words, english, jyutping), pairs = pairs)

    private val on = EngineSettings(continuousTyping = true)

    private fun CandidateEngine.texts(code: String, settings: EngineSettings = on, before: String = "") =
        candidates(code, settings, before).map { it.text }

    private fun CandidateEngine.sentence(code: String, settings: EngineSettings = on, before: String = "") =
        candidates(code, settings, before).singleOrNull { it.sentence }?.text

    @Test
    fun offByDefault() {
        assertEquals(emptyList<String>(), engine().texts("hivd", EngineSettings()))
    }

    @Test
    fun sentenceComesFirstThenTheCharactersOfTheFirstCode() {
        val candidates = engine().candidates("hivd", on)
        assertEquals(listOf("我妹", "我", "手", "竹"), candidates.map { it.text })
        assertEquals(listOf(true, false, false, false), candidates.map { it.sentence })
        // 開頭部分的字只用了前面的字母，選了就分段寫入。
        assertEquals(listOf(4, 2, 2, 1), candidates.map { it.codeLength })
    }

    @Test
    fun charactersOfTheFirstCodeFollowTheChosenOrder() {
        val windows = on.copy(sharedCodeOrder = SharedCodeOrder.Windows)
        assertEquals(listOf("我妹", "手", "我", "竹"), engine().texts("hivd", windows))
    }

    @Test
    fun wordsBeatSeparateCharactersAndAreOfferedBeforeThem() {
        val engine = engine(words = mapOf("我" to listOf(WordEntry("好", 300, false))))
        val candidates = engine.candidates("hivdof", on)
        assertEquals("我好你", candidates.first { it.sentence }.text)
        assertEquals(listOf("我好你", "我好", "我", "手", "竹"), candidates.map { it.text })
        assertEquals(4, candidates[1].codeLength)
    }

    @Test
    fun aSingleCodeKeepsItsUsualCandidates() {
        assertEquals(listOf("我", "手"), engine().texts("hi"))
        assertEquals(listOf("妹", "好"), engine().texts("vd"))
    }

    @Test
    fun firstCodesThatLeaveLettersNoCodeCanUseAreLeftOut() {
        val codes = mapOf(
            (Scheme.Quick to "ab") to listOf(entry("明", 100)),
            (Scheme.Quick to "a") to listOf(entry("日", 100)),
            (Scheme.Quick to "bz") to listOf(entry("凹", 10)),
        )
        assertEquals(listOf("日凹", "日"), engine(codes).texts("abz"))
        assertEquals(emptyList<String>(), engine(codes).texts("abx"))
    }

    @Test
    fun englishWordsTheirBeginningsAndInflectionsGetNoSentence() {
        val codes = quickCodes + mapOf(
            (Scheme.Quick to "c") to listOf(entry("金", 10)),
            (Scheme.Quick to "a") to listOf(entry("日", 10)),
            (Scheme.Quick to "t") to listOf(entry("廿", 10)),
            (Scheme.Quick to "s") to listOf(entry("尸", 10)),
        )
        val engine = engine(codes, english = mapOf("hive" to 1, "cat" to 2))
        assertEquals(emptyList<String>(), engine.texts("hiv"))
        assertEquals(null, engine.sentence("cat"))
        assertEquals(null, engine.sentence("cats"))
        assertEquals("金日尸", engine.sentence("cas"))
        assertEquals("我妹", engine.sentence("hivd"))
    }

    @Test
    fun capitalsAfterTheFirstLetterMeanEnglish() {
        assertEquals(null, engine().sentence("hiVd"))
        assertEquals("我妹", engine().sentence("Hivd"))
    }

    @Test
    fun wholeJyutpingMatchesStayBeforeTheSentence() {
        val codes = mapOf(
            (Scheme.Quick to "n") to listOf(entry("弓", 10)),
            (Scheme.Quick to "e") to listOf(entry("水", 10)),
            (Scheme.Quick to "i") to listOf(entry("戈", 10)),
            (Scheme.Quick to "h") to listOf(entry("竹", 10)),
            (Scheme.Quick to "o") to listOf(entry("人", 10)),
            (Scheme.Quick to "u") to listOf(entry("山", 10)),
        )
        val engine = engine(codes, jyutping = listOf(JyutpingEntry("你好", "nei hou", 100, false)))
        val settings = on.copy(schemes = listOf(Scheme.Quick, Scheme.Jyutping))
        val candidates = engine.candidates("neihou", settings)
        assertEquals(listOf("你好", "弓水戈竹人山", "弓"), candidates.map { it.text })
        assertEquals(Scheme.Jyutping, candidates.first().scheme)
        assertTrue(candidates[1].sentence)
    }

    @Test
    fun quickAndCangjieShareOneSentence() {
        val codes = quickCodes + mapOf((Scheme.Cangjie to "vnd") to listOf(entry("好", 500)))
        val both = on.copy(schemes = listOf(Scheme.Quick, Scheme.Cangjie))
        assertEquals("我好", engine(codes).sentence("hivnd", both))
        assertEquals(null, engine(codes).sentence("hivnd"))
    }

    @Test
    fun generationSettingAppliesToSentences() {
        val codes = quickCodes + mapOf(
            (Scheme.Quick to "vd") to listOf(entry("好", 500), entry("妹", 600, generation = Fifth)),
        )
        assertEquals("我妹", engine(codes).sentence("hivd"))
        assertEquals("我好", engine(codes).sentence("hivd", on.copy(cangjieGenerations = setOf(Third))))
    }

    @Test
    fun learnedPairsChangeTheSentence() {
        val pairs = PairMemory()
        val engine = engine(pairs = pairs)
        assertEquals("我妹", engine.sentence("hivd"))
        engine.rememberPairs(before = "", text = "我好")
        assertEquals("我好", engine.sentence("hivd"))
        assertEquals("我妹", engine.sentence("hivd", on.copy(rememberCandidates = false)))
    }

    @Test
    fun theCharacterBeforeTheCompositionIsContext() {
        val engine = engine()
        engine.rememberPairs(before = "", text = "佢好")
        assertEquals("妹你", engine.sentence("vdof"))
        assertEquals("好你", engine.sentence("vdof", before = "佢"))
        assertEquals("妹你", engine.sentence("vdof", before = "佢 "))
    }

    @Test
    fun learnedCharactersAreUsedEvenIfRare() {
        // 「呀」排第 11，平時不用來組句；記下過搭配之後就用。
        val common = "丁七丈三上下丌不与丐".map { entry(it.toString(), 1000) }
        val codes = quickCodes + mapOf((Scheme.Quick to "rm") to common + entry("呀", 300))
        val engine = engine(codes)
        assertEquals("我丁", engine.sentence("hirm"))
        engine.rememberPairs(before = "", text = "我呀")
        assertEquals("我呀", engine.sentence("hirm"))
    }

    @Test
    fun clearingMemoryForgetsPairsToo() {
        val engine = engine()
        engine.rememberPairs(before = "", text = "我好")
        engine.clearMemory()
        assertEquals("我妹", engine.sentence("hivd"))
    }
}
