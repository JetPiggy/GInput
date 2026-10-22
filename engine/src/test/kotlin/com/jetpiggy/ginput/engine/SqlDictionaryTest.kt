package com.jetpiggy.ginput.engine

import java.sql.Connection
import java.sql.DriverManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 用 App 實際內置的字庫，檢查建置出來的資料和 SQL 查詢。 */
class SqlDictionaryTest {

    private lateinit var connection: Connection
    private lateinit var englishConnection: Connection
    private lateinit var engine: CandidateEngine

    @Before
    fun openDictionary() {
        connection = connect("ginput.dictionary")
        englishConnection = connect("ginput.english")
        engine = CandidateEngine(SqlDictionary(JdbcSqlRunner(connection), JdbcSqlRunner(englishConnection)))
    }

    @After
    fun closeDictionary() {
        connection.close()
        englishConnection.close()
    }

    private fun connect(property: String): Connection {
        val path = checkNotNull(System.getProperty(property)) { "要經 Gradle 執行，才會帶入字庫路徑" }
        return DriverManager.getConnection("jdbc:sqlite:$path")
    }

    private fun texts(code: String, settings: EngineSettings) = engine.candidates(code, settings).map { it.text }

    @Test
    fun continuousTypingReadsWholeTablesAndFindsTheSentence() {
        val dictionary = SqlDictionary(JdbcSqlRunner(connection), JdbcSqlRunner(englishConnection))
        assertTrue(dictionary.codeTable(Scheme.Quick).any { it.code == "hi" && it.entry.character == "我" })
        assertTrue(dictionary.allWords().any { it.word == "香港" })
        assertTrue(dictionary.characterFrequencies().getValue("我") > 0)
        assertTrue(dictionary.hasEnglishWord("dont"))
        assertFalse(dictionary.hasEnglishWord("hivddpof"))
        val quick = EngineSettings(continuousTyping = true)
        val cangjie = quick.copy(schemes = listOf(Scheme.Cangjie))
        assertEquals("我好想你", engine.candidates("hivddpof", quick).first { it.sentence }.text)
        assertEquals("我好想你", engine.candidates("hqivndduponf", cangjie).first { it.sentence }.text)
        for (english in listOf("hello", "happened", "cats")) {
            assertTrue(english, engine.candidates(english, quick).none { it.sentence })
        }
    }

    @Test
    fun translationsComeFromTheEnglishChineseTable() {
        assertEquals("鴨", engine.translations("duck").first())
        assertEquals("鴨", engine.translations("ducks").first())
        assertEquals("的士", engine.translations("taxi").first())
        // english_chinese_extra.txt 改了次序；cherries 用 cherry 那一行。
        assertEquals("車厘子", engine.translations("cherries").first())
        // was 在 words.hk 是「華盛頓」，清單寫了「-」。
        assertEquals(emptyList<String>(), engine.translations("was"))
        // english_chinese_blocked.txt 的粗口不出。
        assertTrue(engine.translations("stupid").none { "鳩" in it })
        assertTrue(engine.translations("dog").size <= 3)
    }

    @Test
    fun fixingACharacterOfTheSentence() {
        // hivdbeof 組成「我好受你」（「好受」是詞）；把 be 的字改成「愛」。
        val quick = EngineSettings(continuousTyping = true)
        val part = engine.candidates("hivdbeof", quick).first { it.sentence }.parts[2]
        assertEquals(SentencePart(part.text, 4, 6), part)
        assertTrue(engine.fixChoices("hivdbeof", part, quick).map { it.text }.containsAll(listOf("愛", "受")))
        val fixed = listOf(SentencePart("愛", 4, 6))
        assertEquals("我好愛你", engine.candidates("hivdbeof", quick, fixes = fixed).first { it.sentence }.text)
    }

    @Test
    fun quickInWindowsOrderMatchesWindows() {
        val windows = EngineSettings(sharedCodeOrder = SharedCodeOrder.Windows)
        assertEquals(listOf("伙", "你", "係", "偽", "氮", "焦", "無"), texts("of", windows).take(7))
    }

    @Test
    fun symbolCodesFollowWindows() {
        val quick = EngineSettings()
        val cangjie = EngineSettings(schemes = listOf(Scheme.Cangjie))
        assertEquals(listOf("。", "﹏", "「"), texts("zd", quick))
        assertEquals(listOf("‥", "〔", "﹚"), texts("zm", quick))
        assertEquals(listOf("。", "﹏", "「"), texts("zd", quick.copy(sharedCodeOrder = SharedCodeOrder.Windows)))
        assertEquals(listOf("。"), texts("zxad", cangjie))
        assertEquals(listOf("‧"), texts("zxaf", cangjie))
        assertEquals(emptyList<String>(), texts("zxaa", cangjie))
        // 倉頡版本的設定不影響符號碼。
        for (generation in CangjieGeneration.entries) {
            assertEquals(listOf("﹏"), texts("zxbd", cangjie.copy(cangjieGenerations = setOf(generation))))
        }
        val dictionary = SqlDictionary(JdbcSqlRunner(connection), JdbcSqlRunner(englishConnection))
        val symbols = dictionary.symbols()
        assertEquals(74, symbols.size)
        assertEquals("，" to "′", symbols.first() to symbols.last())
        assertTrue(dictionary.codeTable(Scheme.Quick).none { it.entry.character in symbols })
        assertTrue(symbols.none { it in dictionary.characterFrequencies() })
    }

    @Test
    fun symbolsComeAfterJyutping() {
        val both = EngineSettings(schemes = listOf(Scheme.Quick, Scheme.Jyutping))
        val zi = texts("zi", both)
        assertEquals("自", zi.first())
        assertEquals(listOf("？", "｛", "』"), zi.takeLast(3))
    }

    @Test
    fun cangjieAcceptsEveryCodeOfACharacter() {
        val cangjie = EngineSettings(schemes = listOf(Scheme.Cangjie))
        assertEquals("嘅", texts("raiu", cangjie).single())
        assertEquals("嘅", texts("rhpu", cangjie).single())
        assertEquals("𨋢", texts("jjyt", cangjie).single())
    }

    @Test
    fun quickAndCangjieShareShortCodes() {
        assertTrue("明" in texts("ab", EngineSettings(schemes = listOf(Scheme.Quick))))
        assertTrue("明" in texts("ab", EngineSettings(schemes = listOf(Scheme.Cangjie))))
    }

    @Test
    fun generationSettingFiltersCangjieCodes() {
        val fifthOnly = EngineSettings(
            schemes = listOf(Scheme.Cangjie),
            cangjieGenerations = setOf(CangjieGeneration.Fifth),
        )
        assertTrue("冇" in texts("xkb", fifthOnly))
        assertFalse("冇" in texts("xkb", fifthOnly.copy(cangjieGenerations = setOf(CangjieGeneration.Third))))
    }

    @Test
    fun colloquialCharacterLeadsItsSharedCode() {
        assertEquals("嘅", texts("ru", EngineSettings()).first())
    }

    @Test
    fun englishSuggestionsFollowWordfreq() {
        assertEquals(listOf("the", "that", "this"), engine.englishSuggestions("th"))
        assertEquals(listOf("what", "when", "who"), engine.englishSuggestions("wh"))
    }

    @Test
    fun englishSuggestionsIncludeTheExtraList() {
        assertEquals(listOf("don't"), engine.englishSuggestions("dont"))
        assertEquals("gonna", engine.englishSuggestions("gon").first())
        assertEquals(listOf("WhatsApp"), engine.englishSuggestions("whatsa"))
    }

    @Test
    fun englishSuggestionsSkipBlockedWords() {
        val afterFu = engine.englishSuggestions("fu", limit = 100)
        assertTrue("full" in afterFu)
        assertTrue(afterFu.none { it.startsWith("fuck") })
        assertFalse("ass" in engine.englishSuggestions("as", limit = 100))
    }

    @Test
    fun jyutpingFindsCharactersAndWords() {
        val jyutping = EngineSettings(schemes = listOf(Scheme.Jyutping), looseSpelling = false)
        assertEquals("你", texts("nei", jyutping).first())
        assertEquals("香港", texts("hoenggong", jyutping).first())
        assertEquals("我哋", texts("ngodei", jyutping).first())
        assertEquals("唔該", texts("mgoi", jyutping).first())
        val partial = engine.candidates("neihoumaa", jyutping).first()
        assertEquals("你好" to 6, partial.text to partial.codeLength)
    }

    @Test
    fun jyutpingAcceptsLooseSpelling() {
        val jyutping = EngineSettings(schemes = listOf(Scheme.Jyutping))
        assertEquals("你", texts("lei", jyutping).first())
        assertEquals("我", texts("o", jyutping).first())
        assertEquals("香港", texts("heunggong", jyutping).first())
        assertEquals("有", texts("yau", jyutping).first())
        assertEquals("廣東", texts("gongdung", jyutping).first())
        assertEquals("你好", texts("leiho", jyutping).first())
    }

    @Test
    fun jyutpingSyllablesComeFromTheDictionary() {
        val syllables = SqlDictionary(JdbcSqlRunner(connection), JdbcSqlRunner(englishConnection)).jyutpingSyllables()
        assertTrue(listOf("nei", "hou", "m", "ng", "jyut").all { it in syllables })
        assertTrue(syllables.all { syllable -> syllable.all { it in 'a'..'z' } })
    }

    @Test
    fun associationsComeFromWordsHk() {
        val afterNg = engine.associations("唔", EngineSettings(), limit = 100)
        assertTrue(listOf("係", "好", "該").all { it in afterNg })
        assertTrue(engine.associations("唔", EngineSettings(colloquialPriority = false)).isEmpty())
        assertEquals("港", engine.associations("香", EngineSettings()).first())
    }
}
