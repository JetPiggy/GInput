package com.jetpiggy.ginput.typing

import com.jetpiggy.ginput.engine.CandidateEngine
import com.jetpiggy.ginput.engine.CandidateMemory
import com.jetpiggy.ginput.engine.CangjieGeneration
import com.jetpiggy.ginput.engine.CharacterEntry
import com.jetpiggy.ginput.engine.EmojiKeywords
import com.jetpiggy.ginput.engine.EngineSettings
import com.jetpiggy.ginput.engine.FakeDictionary
import com.jetpiggy.ginput.engine.JyutpingEntry
import com.jetpiggy.ginput.engine.Scheme
import com.jetpiggy.ginput.engine.WordEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingSessionTest {

    private fun entry(character: String, frequency: Int) =
        CharacterEntry(character, CangjieGeneration.Third, frequency, windowsRank = 0, colloquial = false)

    private val engine = CandidateEngine(
        FakeDictionary(
            characters = mapOf(
                (Scheme.Quick to "ab") to listOf(entry("明", 10), entry("晴", 5)),
                (Scheme.Quick to "ok") to listOf(entry("你", 10)),
                (Scheme.Quick to "i") to listOf(entry("戈", 1)),
            ),
            words = mapOf("明" to listOf(WordEntry("天", 100, false), WordEntry("白", 50, false))),
        ),
    )
    private var settings = TypingSettings()
    private var now = 0L

    private fun session(
        editor: Editor,
        field: FieldKind = FieldKind.Text,
        composing: Boolean = true,
        engine: CandidateEngine? = this.engine,
        learning: Boolean = true,
        offerCompletions: Boolean = true,
        emojiKeywords: EmojiKeywords? = null,
    ) = TypingSession(
        editor, field, composing, { settings }, { engine }, { emojiKeywords }, { now }, learning, offerCompletions,
    ).also { it.start() }

    private fun TypingSession.type(letters: String) = letters.forEach { letter(it) }

    private val TypingSession.candidateTexts get() = state.candidates.map { it.text }

    @Test
    fun lettersComposeAndShowCandidates() {
        val editor = FakeEditor()
        val session = session(editor)
        session.type("ab")
        assertEquals("ab", editor.composing)
        assertEquals(listOf("明", "晴"), session.candidateTexts)
        assertEquals(listOf("ab"), session.state.englishSuggestions)
        assertFalse(session.state.markTopCandidate)
    }

    @Test
    fun spaceWritesEnglishByDefault() {
        val editor = FakeEditor()
        val session = session(editor)
        session.type("ok")
        session.space()
        assertEquals("ok ", editor.text)
        assertEquals("", editor.composing)
        assertTrue(session.state.candidates.isEmpty())
    }

    @Test
    fun candidateWritesChineseAndShowsAssociations() {
        val editor = FakeEditor()
        val session = session(editor)
        session.type("ab")
        session.candidate("明")
        assertEquals("明", editor.text)
        assertEquals(listOf("天", "白"), session.state.associations)
    }

    @Test
    fun associationsChainAndCanBeDismissed() {
        val editor = FakeEditor()
        val session = session(editor)
        session.type("ab")
        session.candidate("明")
        session.association("天")
        assertEquals("明天", editor.text)
        assertTrue(session.state.associations.isEmpty())

        session.type("ab")
        session.candidate("明")
        session.dismissFollowUps()
        assertTrue(session.state.associations.isEmpty())
    }

    @Test
    fun typingHidesAssociations() {
        val editor = FakeEditor()
        val session = session(editor)
        session.type("ab")
        session.candidate("明")
        session.letter('a')
        assertTrue(session.state.associations.isEmpty())
        assertEquals("a", editor.composing)
    }

    private val keywords = EmojiKeywords(mapOf("明天" to listOf("🌅", "☀️"), "天" to listOf("🌌")))

    @Test
    fun keywordEmojiFollowTheLongestKeywordBeforeTheCursor() {
        settings = TypingSettings(emojiKeywords = true)
        val editor = FakeEditor()
        val session = session(editor, emojiKeywords = keywords)
        session.type("ab")
        session.candidate("明")
        assertTrue(session.state.keywordEmoji.isEmpty())
        // 「天」沒有聯想，Emoji 照出。
        session.association("天")
        assertEquals(listOf("🌅", "☀️"), session.state.keywordEmoji)
        assertTrue(session.state.associations.isEmpty())
        session.symbol("🌅")
        assertEquals("明天🌅", editor.text)
        assertTrue(session.state.keywordEmoji.isEmpty())
    }

    @Test
    fun keywordEmojiHideWithAssociations() {
        settings = TypingSettings(emojiKeywords = true)
        val editor = FakeEditor("明")
        val session = session(editor, emojiKeywords = EmojiKeywords(mapOf("明明" to listOf("💡"))))
        session.type("ab")
        session.candidate("明")
        assertEquals(listOf("💡"), session.state.keywordEmoji)
        assertEquals(listOf("天", "白"), session.state.associations)
        session.dismissFollowUps()
        assertTrue(session.state.keywordEmoji.isEmpty())

        session.type("ab")
        session.candidate("明")
        session.letter('a')
        assertTrue(session.state.keywordEmoji.isEmpty())
    }

    @Test
    fun keywordEmojiOnlyLookAtTheWrittenTextWhenTheAppIsBehind() {
        settings = TypingSettings(emojiKeywords = true)
        val editor = FakeEditor("開心")
        // 寫入後讀到的仍是舊文字，像稍後才套用寫入的 App。
        val behind = object : Editor by editor {
            override fun textBeforeCursor(length: Int) = "開心"
        }
        val session = session(behind, emojiKeywords = EmojiKeywords(mapOf("開心" to listOf("😊"), "天" to listOf("🌌"))))
        session.type("ab")
        session.candidate("明")
        assertTrue(session.state.keywordEmoji.isEmpty())
        session.association("天")
        assertEquals(listOf("🌌"), session.state.keywordEmoji)
        assertEquals("開心明天", editor.text)
    }

    @Test
    fun keywordEmojiAreOffByDefault() {
        val editor = FakeEditor()
        val session = session(editor, emojiKeywords = keywords)
        session.type("ab")
        session.candidate("明")
        session.association("天")
        assertTrue(session.state.keywordEmoji.isEmpty())
    }

    @Test
    fun selectChineseModeWritesTheTopCandidateWithSpace() {
        settings = TypingSettings(spaceMode = SpaceMode.SelectChinese)
        val editor = FakeEditor()
        val session = session(editor)
        session.type("ab")
        assertTrue(session.state.markTopCandidate)
        session.space()
        assertEquals("明", editor.text)
        session.type("xyz")
        session.space()
        assertEquals("明xyz ", editor.text)
    }

    @Test
    fun enterWritesComposingLettersWithoutSendingEnter() {
        val editor = FakeEditor()
        val session = session(editor)
        session.type("ok")
        session.enter()
        assertEquals("ok", editor.text)
        assertTrue(editor.keys.isEmpty())
        session.enter()
        assertEquals(listOf("ENTER"), editor.keys)
    }

    @Test
    fun deleteRemovesComposingLettersBeforeText() {
        val editor = FakeEditor("你")
        val session = session(editor)
        session.type("ab")
        session.delete()
        assertEquals("a", editor.composing)
        session.delete()
        assertEquals("", editor.composing)
        assertEquals("你", editor.text)
        assertTrue(editor.keys.isEmpty())
        session.delete()
        assertEquals(listOf("DEL"), editor.keys)
        assertEquals("", editor.text)
    }

    @Test
    fun punctuationFollowsThePreviousCharacter() {
        val editor = FakeEditor()
        val session = session(editor)
        assertTrue(session.state.fullWidthPunctuation)
        session.punctuation(PunctuationKey.Comma)
        session.type("ab")
        session.candidate("明")
        session.punctuation(PunctuationKey.Period)
        assertEquals("，明。", editor.text)
    }

    @Test
    fun punctuationAfterEnglishIsHalfWidthAndRemovesTheAutoSpace() {
        val editor = FakeEditor()
        val session = session(editor)
        session.type("ok")
        session.space()
        assertFalse(session.state.fullWidthPunctuation)
        session.punctuation(PunctuationKey.Comma)
        assertEquals("ok,", editor.text)
    }

    @Test
    fun punctuationWritesComposingLettersAsEnglishFirst() {
        val editor = FakeEditor()
        val session = session(editor)
        session.type("ok")
        assertFalse(session.state.fullWidthPunctuation)
        session.punctuation(PunctuationKey.Period)
        assertEquals("ok.", editor.text)
    }

    @Test
    fun symbolKeysFollowThePreviousCharacterToo() {
        val editor = FakeEditor("你好")
        val session = session(editor)
        assertEquals("！", session.state.punctuation(PunctuationKey.Exclamation))
        session.punctuation(PunctuationKey.Exclamation)
        session.type("ok")
        session.punctuation(PunctuationKey.Question)
        assertEquals("你好！ok?", editor.text)
    }

    @Test
    fun closingParenthesisMatchesTheOpeningOne() {
        val editor = FakeEditor("我哋（Peter")
        val session = session(editor)
        assertEquals("）", session.state.punctuation(PunctuationKey.CloseParenthesis))
        assertEquals("!", session.state.punctuation(PunctuationKey.Exclamation))
        session.punctuation(PunctuationKey.CloseParenthesis)
        assertEquals("我哋（Peter）", editor.text)
    }

    @Test
    fun relatedSymbolsReplaceTheWrittenSymbol() {
        val editor = FakeEditor("你好")
        val session = session(editor)
        session.punctuation(PunctuationKey.Exclamation)
        val related = listOf("！", "!", "！！", "‼", "❗")
        assertEquals(related, session.state.relatedSymbols)
        assertEquals("！", session.state.writtenSymbol)

        session.relatedSymbol("!")
        assertEquals("你好!", editor.text)
        // 列表不變，可以再換。
        assertEquals(related, session.state.relatedSymbols)
        assertEquals("!", session.state.writtenSymbol)
        session.relatedSymbol("！！")
        assertEquals("你好！！", editor.text)

        session.type("ab")
        assertTrue(session.state.relatedSymbols.isEmpty())
    }

    @Test
    fun relatedSymbolIsWrittenAtTheCursorIfTheCursorMoved() {
        val editor = FakeEditor("你好")
        val session = session(editor)
        session.punctuation(PunctuationKey.Period)
        editor.cursor = 1
        session.relatedSymbol("……")
        assertEquals("你……好。", editor.text)
        assertTrue(session.state.relatedSymbols.isEmpty())
    }

    @Test
    fun movingTheCursorSelectingTextOrTappingCloseHidesRelatedSymbols() {
        val editor = FakeEditor("你好")
        val session = session(editor)
        session.punctuation(PunctuationKey.Comma)
        // 寫入後文字框回報的新游標位置：仍然在剛打的符號後面。
        session.selectionChanged(3, 3, -1, -1)
        assertEquals(listOf("，", ",", "、"), session.state.relatedSymbols)
        editor.cursor = 1
        session.selectionChanged(1, 1, -1, -1)
        assertTrue(session.state.relatedSymbols.isEmpty())

        editor.cursor = editor.text.length
        session.punctuation(PunctuationKey.Comma)
        session.selectionChanged(0, 2, -1, -1)
        assertTrue(session.state.relatedSymbols.isEmpty())

        session.punctuation(PunctuationKey.Comma)
        session.dismissFollowUps()
        assertTrue(session.state.relatedSymbols.isEmpty())
    }

    @Test
    fun quotesOfferRelatedSymbolsButOtherSymbolsDoNot() {
        val editor = FakeEditor()
        val session = session(editor)
        session.symbol("\"", offerRelated = true)
        assertEquals(listOf("\"", "“", "”", "「", "」"), session.state.relatedSymbols)
        session.symbol("@", offerRelated = true)
        assertTrue(session.state.relatedSymbols.isEmpty())
    }

    @Test
    fun emailAndUrlFieldsOfferDotComAfterAPeriod() {
        for (field in listOf(FieldKind.Email, FieldKind.Url)) {
            val editor = FakeEditor()
            val session = session(editor, field = field)
            session.type("ok")
            session.punctuation(PunctuationKey.Period)
            assertEquals("$field", listOf(".", ".com"), session.state.relatedSymbols)
            session.relatedSymbol(".com")
            assertEquals("$field", "ok.com", editor.text)
            // 其他符號在這些欄位沒有相關符號。
            session.symbol("@", offerRelated = true)
            assertTrue("$field", session.state.relatedSymbols.isEmpty())
            session.punctuation(PunctuationKey.Comma)
            assertTrue("$field", session.state.relatedSymbols.isEmpty())
        }
    }

    @Test
    fun noRelatedSymbolsInPasswordFieldsOrFieldsThatDoNotCompose() {
        for ((field, composing) in listOf(FieldKind.Password to true, FieldKind.Text to false, FieldKind.Url to false)) {
            val editor = FakeEditor()
            val session = session(editor, field = field, composing = composing)
            session.punctuation(PunctuationKey.Period)
            assertTrue("$field", session.state.relatedSymbols.isEmpty())
        }
    }

    @Test
    fun autoSpaceIsRemovedBeforeChinese() {
        val editor = FakeEditor()
        val session = session(editor)
        session.type("ok")
        session.space()
        session.type("ab")
        session.candidate("明")
        assertEquals("ok明", editor.text)
    }

    @Test
    fun autoSpaceStaysWhenSmartSpaceRemovalIsOff() {
        settings = TypingSettings(smartSpaceRemoval = false)
        val editor = FakeEditor()
        val session = session(editor)
        session.type("ok")
        session.space()
        session.type("ab")
        session.candidate("明")
        assertEquals("ok 明", editor.text)
    }

    @Test
    fun extraSpacesAreKept() {
        val editor = FakeEditor()
        val session = session(editor)
        session.type("ok")
        session.space()
        session.space()
        session.type("ab")
        session.candidate("明")
        assertEquals("ok  明", editor.text)
    }

    @Test
    fun autoSpaceStaysWhenTheCursorMoved() {
        val editor = FakeEditor()
        val session = session(editor)
        session.type("ok")
        session.space()
        editor.cursor = 0
        session.type("ab")
        session.candidate("明")
        assertEquals("明ok ", editor.text)
    }

    @Test
    fun aLoneLowercaseIBecomesCapital() {
        val editor = FakeEditor()
        val session = session(editor)
        session.type("i")
        session.space()
        session.type("it")
        session.space()
        assertEquals("I it ", editor.text)
    }

    @Test
    fun sentenceStartIsCapitalisedAutomatically() {
        val editor = FakeEditor(capsMode = { it.isEmpty() || it.endsWith(". ") })
        val session = session(editor)
        assertEquals(ShiftState.Auto, session.state.shift)
        session.type("hi")
        assertEquals("Hi", editor.composing)
        assertEquals(ShiftState.Off, session.state.shift)
        session.punctuation(PunctuationKey.Period)
        session.space()
        assertEquals("Hi. ", editor.text)
        assertEquals(ShiftState.Auto, session.state.shift)
    }

    @Test
    fun shiftOnceThenLock() {
        val editor = FakeEditor()
        val session = session(editor)
        session.shift()
        assertEquals(ShiftState.OneShot, session.state.shift)
        session.type("ab")
        assertEquals("Ab", editor.composing)
        now = 1_000
        session.shift()
        now = 1_100
        session.shift()
        assertEquals(ShiftState.Locked, session.state.shift)
        session.type("cd")
        assertEquals("AbCD", editor.composing)
        session.shift()
        assertEquals(ShiftState.Off, session.state.shift)
    }

    @Test
    fun passwordFieldsTypeDirectlyWithoutChinese() {
        val editor = FakeEditor()
        val session = session(editor, FieldKind.Password)
        session.type("ab")
        assertEquals("ab", editor.text)
        assertEquals("", editor.composing)
        assertTrue(session.state.candidates.isEmpty())
        session.punctuation(PunctuationKey.Comma)
        assertEquals("ab,", editor.text)
    }

    @Test
    fun withoutComposingSupportTypedLettersAreReplacedByChinese() {
        val editor = FakeEditor()
        val session = session(editor, composing = false)
        session.type("ab")
        assertEquals("ab", editor.text)
        assertEquals(listOf("明", "晴"), session.candidateTexts)
        session.candidate("明")
        assertEquals("明", editor.text)
        assertEquals(listOf("DEL", "DEL"), editor.keys)
    }

    @Test
    fun withoutComposingSupportSpaceAddsOnlyASpace() {
        val editor = FakeEditor()
        val session = session(editor, composing = false)
        session.type("ok")
        session.space()
        assertEquals("ok ", editor.text)
    }

    @Test
    fun movingTheCursorKeepsTheLettersAsEnglish() {
        val editor = FakeEditor()
        val session = session(editor)
        session.type("ab")
        session.selectionChanged(0, 0, 0, 2)
        assertEquals("", session.state.composition)
        assertEquals("", editor.composing)
        assertEquals("ab", editor.text)
    }

    @Test
    fun lateUpdateWithoutCompositionIsIgnoredUntilTheEchoArrives() {
        val editor = FakeEditor()
        val session = session(editor)
        session.type("a")
        session.selectionChanged(1, 1, -1, -1)
        assertEquals("a", session.state.composition)
        session.selectionChanged(1, 1, 0, 1)
        session.selectionChanged(1, 1, -1, -1)
        assertEquals("", session.state.composition)
    }

    @Test
    fun emailFieldsUseHalfWidthPunctuation() {
        val editor = FakeEditor()
        val session = session(editor, FieldKind.Email)
        assertFalse(session.state.fullWidthPunctuation)
        session.punctuation(PunctuationKey.Period)
        assertEquals(".", editor.text)
    }

    @Test
    fun symbolsWriteComposingLettersFirst() {
        val editor = FakeEditor()
        val session = session(editor)
        session.type("ok")
        session.symbol("@")
        assertEquals("ok@", editor.text)
    }

    @Test
    fun englishSuggestionWritesTheWordAndASpace() {
        val editor = FakeEditor()
        val session = session(editor)
        session.type("ok")
        session.englishSuggestion("ok")
        assertEquals("ok ", editor.text)
    }

    @Test
    fun pickingACandidateTwiceRemembersIt() {
        val editor = FakeEditor()
        val session = session(editor)
        repeat(2) {
            session.type("ab")
            session.candidate("晴")
        }
        session.type("ab")
        assertEquals(listOf("晴", "明"), session.candidateTexts)
        assertTrue(session.state.candidates.first().remembered)
    }

    @Test
    fun fieldsThatAskNotToLearnRememberNothing() {
        val editor = FakeEditor()
        val session = session(editor, learning = false)
        repeat(2) {
            session.type("ab")
            session.candidate("晴")
        }
        session.type("ab")
        assertEquals(listOf("明", "晴"), session.candidateTexts)
    }

    @Test
    fun writesReportTheirKindWithoutContent() {
        val kinds = mutableListOf<WriteKind>()
        val session = TypingSession(
            FakeEditor(), FieldKind.Text, true, { settings }, { engine }, clock = { now },
            onWrite = { kinds += it },
        ).also { it.start() }
        session.type("ab")
        session.candidate("明")
        session.association("天")
        session.type("ok")
        session.space()
        session.type("ok")
        session.englishSuggestion("okay")
        assertEquals(
            listOf(WriteKind.Chinese, WriteKind.Association, WriteKind.English, WriteKind.EnglishSuggestion),
            kinds,
        )
    }

    @Test
    fun turningMemoryOffStopsRecording() {
        val saved = mutableListOf<Map<String, Int>>()
        val engine = CandidateEngine(
            FakeDictionary(characters = mapOf((Scheme.Quick to "ab") to listOf(entry("明", 1)))),
            CandidateMemory(onChanged = { saved += it }),
        )
        settings = TypingSettings(engine = EngineSettings(rememberCandidates = false))
        val session = session(FakeEditor(), engine = engine)
        session.type("ab")
        session.candidate("明")
        assertTrue(saved.isEmpty())
    }

    @Test
    fun onlyPickedCandidatesAreRecordedNotAssociations() {
        val saved = mutableListOf<Map<String, Int>>()
        val engine = CandidateEngine(
            FakeDictionary(
                characters = mapOf((Scheme.Quick to "ab") to listOf(entry("明", 1))),
                words = mapOf("明" to listOf(WordEntry("天", 1, false))),
            ),
            CandidateMemory(onChanged = { saved += it }),
        )
        val session = session(FakeEditor(), engine = engine)
        session.type("ab")
        session.candidate("明")
        session.association("天")
        assertEquals(listOf(mapOf("明" to 1)), saved)
    }

    @Test
    fun forgettingPutsTheCharacterBack() {
        val editor = FakeEditor()
        val session = session(editor)
        repeat(2) {
            session.type("ab")
            session.candidate("晴")
        }
        session.type("ab")
        session.forget("晴")
        assertEquals(listOf("明", "晴"), session.candidateTexts)
        assertFalse(session.state.candidates.any { it.remembered })
    }

    @Test
    fun englishSuggestionsFollowTheTypedLetters() {
        val engine = CandidateEngine(FakeDictionary(english = mapOf("ok" to 2, "okay" to 3)))
        val session = session(FakeEditor(), engine = engine)
        session.type("ok")
        assertEquals(listOf("ok", "okay"), session.state.englishSuggestions)
    }

    @Test
    fun fieldsThatRefuseSuggestionsOnlyGetTheTypedLetters() {
        val engine = CandidateEngine(FakeDictionary(english = mapOf("ok" to 2, "okay" to 3)))
        val session = session(FakeEditor(), engine = engine, offerCompletions = false)
        session.type("ok")
        assertEquals(listOf("ok"), session.state.englishSuggestions)
    }

    @Test
    fun movingTheCursorLeavesComposingLettersAsEnglish() {
        val editor = FakeEditor()
        val session = session(editor)
        session.type("ab")
        session.moveCursor(-1)
        assertEquals("", editor.composing)
        assertEquals("ab", editor.text)
        assertEquals(1, editor.cursor)
        assertEquals("", session.state.composition)
    }

    @Test
    fun swipingLeftOnDeleteSelectsWordByWordThenDeletes() {
        val editor = FakeEditor("call me 2026 你好")
        val session = session(editor)
        session.selectBackward(1, wholeLine = false)
        assertEquals("好", editor.selected)
        session.selectBackward(2, wholeLine = false)
        assertEquals("你好", editor.selected)
        // 英文和數字以連續一串算一個；空格連同前面的字詞一起選取。
        session.selectBackward(3, wholeLine = false)
        assertEquals("2026 你好", editor.selected)
        session.selectBackward(4, wholeLine = false)
        assertEquals("me 2026 你好", editor.selected)
        session.finishBackwardSelection(delete = true)
        assertEquals("call ", editor.text)
    }

    @Test
    fun swipingLeftSelectsACharacterOutsideTheBmpAsOne() {
        val editor = FakeEditor("lift 𨋢")
        val session = session(editor)
        session.selectBackward(1, wholeLine = false)
        assertEquals("𨋢", editor.selected)
        session.selectBackward(2, wholeLine = false)
        assertEquals("lift 𨋢", editor.selected)
    }

    @Test
    fun swipingToTheEdgeSelectsTheWholeLine() {
        val editor = FakeEditor("first line\nsecond line")
        val session = session(editor)
        session.selectBackward(0, wholeLine = true)
        assertEquals("second line", editor.selected)
        session.finishBackwardSelection(delete = true)
        assertEquals("first line\n", editor.text)
    }

    @Test
    fun swipingBackRestoresTheCursorWithoutDeleting() {
        val editor = FakeEditor("hello world")
        val session = session(editor)
        session.selectBackward(1, wholeLine = false)
        session.selectBackward(0, wholeLine = false)
        session.finishBackwardSelection(delete = false)
        assertEquals("hello world", editor.text)
        assertEquals("", editor.selected)
        assertEquals(11, editor.cursor)
    }

    private val jyutpingEngine = CandidateEngine(
        FakeDictionary(
            jyutping = listOf(
                JyutpingEntry("你", "nei", 100, colloquial = false),
                JyutpingEntry("你好", "nei hou", 50, colloquial = false),
                JyutpingEntry("嗎", "maa", 40, colloquial = false),
            ),
            words = mapOf("嗎" to listOf(WordEntry("啡", 1, false))),
        ),
    )

    private val jyutpingSettings = TypingSettings(engine = EngineSettings(schemes = listOf(Scheme.Jyutping)))

    @Test
    fun jyutpingCandidateLeavesTheRemainingLettersComposing() {
        settings = jyutpingSettings
        val editor = FakeEditor()
        val session = session(editor, engine = jyutpingEngine)
        session.type("neihoumaa")
        assertEquals(listOf("你好", "你"), session.candidateTexts)
        session.candidate("你好")
        assertEquals("你好maa", editor.text)
        assertEquals("maa", editor.composing)
        assertEquals("maa", session.state.composition)
        assertEquals(listOf("嗎"), session.candidateTexts)
        assertTrue(session.state.associations.isEmpty())
        session.candidate("嗎")
        assertEquals("你好嗎", editor.text)
        assertEquals("", editor.composing)
        assertEquals(listOf("啡"), session.state.associations)
    }

    @Test
    fun selectChineseModeWritesJyutpingPartByPart() {
        settings = jyutpingSettings.copy(spaceMode = SpaceMode.SelectChinese)
        val editor = FakeEditor()
        val session = session(editor, engine = jyutpingEngine)
        session.type("neimaa")
        session.space()
        assertEquals("你maa", editor.text)
        session.space()
        assertEquals("你嗎", editor.text)
    }

    @Test
    fun withoutComposingSupportTheRemainingLettersAreTypedAgain() {
        settings = jyutpingSettings
        val editor = FakeEditor()
        val session = session(editor, composing = false, engine = jyutpingEngine)
        session.type("neihoumaa")
        session.candidate("你好")
        assertEquals("你好maa", editor.text)
        session.candidate("嗎")
        assertEquals("你好嗎", editor.text)
    }

    @Test
    fun movingTheCursorAfterAPartialPickKeepsTheRestAsEnglish() {
        settings = jyutpingSettings
        val editor = FakeEditor()
        val session = session(editor, engine = jyutpingEngine)
        session.type("neimaa")
        session.candidate("你")
        session.selectionChanged(0, 0, 1, 4)
        assertEquals("", session.state.composition)
        assertEquals("你maa", editor.text)
    }

    @Test
    fun beforeTheDictionaryLoadsOnlyEnglishIsOffered() {
        val editor = FakeEditor()
        val session = session(editor, engine = null)
        session.type("ab")
        assertTrue(session.state.candidates.isEmpty())
        assertEquals(listOf("ab"), session.state.englishSuggestions)
    }
}
