package com.jetpiggy.ginput.typing

import com.jetpiggy.ginput.engine.Candidate
import com.jetpiggy.ginput.engine.CandidateEngine
import com.jetpiggy.ginput.engine.EmojiKeywords
import com.jetpiggy.ginput.engine.SentencePart

/**
 * 一個文字框裏的打字過程：組字、寫入、候選字、改字、聯想、Emoji 關鍵字、空白鍵模式、智能全形標點、相關符號和智能刪空格。
 * 對方的文字框每次取得焦點就開一個新的；所有方法都要在同一個執行緒呼叫。規則見 docs/spec/v1.md。
 */
class TypingSession(
    private val editor: Editor,
    private val field: FieldKind,
    /** 文字框支援組字；不支援（例如終端機）就把字母直接寫入，選中文時再刪掉。 */
    composingSupported: Boolean,
    private val settings: () -> TypingSettings,
    /** 字庫載入完成前是 null，那時只出英文。 */
    private val engine: () -> CandidateEngine?,
    /** 載入完成前是 null，那時不出 Emoji 關鍵字的 Emoji。 */
    private val emojiKeywords: () -> EmojiKeywords? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
    /** 記錄使用者選的字（記憶字和搭配記憶）；要求不學習的欄位（無痕模式）是 false。設定關了記憶字也不記錄。 */
    private val learning: Boolean = true,
    /** 提供英文建議的補全；欄位要求不出建議時是 false，只出照打的字母。 */
    private val offerCompletions: Boolean = true,
    private val onStateChanged: (KeyboardState) -> Unit = {},
    /** 每次寫入後通知寫入的類別（不含內容）。 */
    private val onWrite: (WriteKind) -> Unit = {},
) {
    private val composing = composingSupported && field.allowsChinese
    private val composition = StringBuilder()
    private var candidates: List<Candidate> = emptyList()
    private var completions: List<String> = emptyList()
    private var associations: List<String> = emptyList()
    private var keywordEmoji: List<String> = emptyList()
    private var relatedSymbols: List<String> = emptyList()

    /** 相關符號裏現在寫在文字框的那一個；取代之前先確認游標前仍然是它。 */
    private var writtenSymbol: String? = null
    private var shift = ShiftState.Off
    private var lastShiftTap = 0L

    /** 用空白鍵寫入英文之後、游標前的文字，以那個空格結尾；用來確認游標沒有移動過。 */
    private var autoSpace: String? = null

    /** 新的組字已經送出，但文字框還未回報組字的範圍。 */
    private var awaitingComposingEcho = false

    /** 連打的上文：組字開始時游標前的文字，分段寫入後是剛寫入的字詞。有些 App 稍後才套用寫入，所以不每次都讀文字框。 */
    private var contextBefore = ""

    /** 改字改好的字；Delete 刪到它用的字母、分段寫入了它或者組字清空，就不再保留。 */
    private var fixes: List<SentencePart> = emptyList()

    /** 剛改好字的整句：組字再變之前照樣顯示，其餘的字不會因為重新組句而變。 */
    private var heldSentence: List<SentencePart>? = null

    /** 改字時揀中的字和可以換上的字，候選列列出這些字。 */
    private var fixChoices: CharacterFixChoices? = null

    var state = KeyboardState()
        private set

    fun start() = finishAction()

    fun letter(letter: Char) {
        val typed = if (shift.uppercase) letter.uppercaseChar() else letter.lowercaseChar()
        if (shift != ShiftState.Locked) shift = ShiftState.Off
        clearFollowUps()
        if (!field.allowsChinese) {
            editor.commitText(typed.toString())
            autoSpace = null
        } else {
            if (composition.isEmpty()) {
                awaitingComposingEcho = true
                contextBefore = if (settings().engine.continuousTyping) editor.textBeforeCursor(CHARACTER_CONTEXT_LENGTH) else ""
            }
            composition.append(typed)
            heldSentence = null
            if (composing) editor.setComposingText(composition.toString()) else editor.commitText(typed.toString())
            refreshCandidates()
        }
        finishAction()
    }

    fun space() {
        clearFollowUps()
        when {
            composition.isEmpty() -> {
                editor.commitText(" ")
                autoSpace = null
            }
            settings().spaceMode == SpaceMode.SelectChinese && candidates.isNotEmpty() ->
                candidates.first().let {
                    writeChinese(it.text, picked = true, codeLength = it.codeLength, sentence = it.sentence)
                }
            else -> writeEnglish(typedEnglish(), withSpace = true)
        }
        finishAction()
    }

    /** 有組字時只把字母當英文寫入，不換行也不送出。 */
    fun enter() {
        clearFollowUps()
        if (composition.isNotEmpty()) {
            writeEnglish(typedEnglish(), withSpace = false)
        } else {
            editor.sendEnterKey()
            autoSpace = null
        }
        finishAction()
    }

    fun delete() {
        if (composition.isEmpty()) {
            clearFollowUps()
            autoSpace = null
            editor.sendDeleteKey()
        } else {
            composition.setLength(composition.length - 1)
            // 刪到改好的字用的字母，就取消那次改字。
            fixes = fixes.filter { it.end <= composition.length }
            heldSentence = null
            fixChoices = null
            when {
                !composing -> editor.sendDeleteKey()
                composition.isEmpty() -> {
                    editor.setComposingText("")
                    editor.finishComposingText()
                }
                else -> editor.setComposingText(composition.toString())
            }
            refreshCandidates()
        }
        finishAction()
    }

    /** 「，」「。」鍵和 123 頁的 ! ? : ; ( ) ~：按智能全形標點決定全形或半形，然後列出相關符號。 */
    fun punctuation(key: PunctuationKey) {
        // 有組字的話，字母會先當英文寫入，所以標點跟在英文後面，出半形。
        val fullWidth = !field.forcesHalfWidthPunctuation &&
            composition.isEmpty() &&
            Punctuation.fullWidth(key, editor.textBeforeCursor(PUNCTUATION_CONTEXT_LENGTH))
        symbol(Punctuation.of(key, fullWidth), offerRelated = true)
    }

    /**
     * 123 頁、長按選單和數字鍵盤上的字元：有組字就先當英文寫入。
     * offerRelated：打出的符號有相關符號的話，在候選列列出（電郵和網址欄位只有「.」有）；欄位要支援組字，才可以取代。
     */
    fun symbol(text: String, offerRelated: Boolean = false) {
        clearFollowUps()
        editor.beginBatchEdit()
        if (composition.isNotEmpty()) writeEnglish(typedEnglish(), withSpace = false)
        if (shouldRemoveAutoSpace(text)) deleteAutoSpace()
        editor.commitText(text)
        editor.endBatchEdit()
        autoSpace = null
        if (offerRelated && composing) {
            relatedSymbols = Punctuation.relatedTo(text, field)
            writtenSymbol = text.takeIf { relatedSymbols.isNotEmpty() }
        }
        finishAction()
    }

    /** 按候選列的相關符號：取代剛打的符號，列表留着可以再換。游標前已經不是那個符號，就在游標處寫入。 */
    fun relatedSymbol(text: String) {
        val written = writtenSymbol
        if (written == null || editor.textBeforeCursor(written.length) != written) {
            symbol(text)
            return
        }
        editor.beginBatchEdit()
        editor.deleteBeforeCursor(written.length)
        editor.commitText(text)
        editor.endBatchEdit()
        writtenSymbol = text
        finishAction()
    }

    fun candidate(text: String) {
        val candidate = candidates.firstOrNull { it.text == text }
        val codeLength = candidate?.codeLength ?: composition.length
        writeChinese(text, picked = true, codeLength = codeLength, sentence = candidate?.sentence == true)
        finishAction()
    }

    fun association(text: String) {
        writeChinese(text, picked = false)
        finishAction()
    }

    /** 忘記這個記憶字，候選字回到原來的次序。 */
    fun forget(text: String) {
        engine()?.forget(text)
        refreshCandidates()
        publish()
    }

    /** 改字：揀了整句候選的第 index 個字，候選列列出用同一段字母、可以換上的字。 */
    fun startCharacterFix(index: Int) {
        val part = sentenceParts()?.getOrNull(index) ?: return
        val engine = engine() ?: return
        clearFollowUps()
        fixChoices = CharacterFixChoices(part, engine.fixChoices(composition.toString(), part, settings().engine))
        publish()
    }

    /** 改字：把揀中的字換成 text。整句候選只換那個字，不寫入；之後再組句也一定經過它。 */
    fun fixCharacter(text: String) {
        val part = fixChoices?.part ?: return
        val sentence = sentenceParts() ?: return
        val fixed = part.copy(text = text)
        clearFollowUps()
        fixes = fixes.filterNot { it.start == part.start } + fixed
        heldSentence = sentence.map { if (it == part) fixed else it }
        refreshCandidates()
        publish()
    }

    private fun sentenceParts(): List<SentencePart>? = candidates.firstOrNull { it.sentence }?.parts?.takeIf { it.isNotEmpty() }

    /**
     * 游標滑動（在空白鍵或其他按鍵上左右滑動）：移動游標；select 就伸展選取（按住 Shift 時）。
     * 有組字的話，字母先留在文字框，當作英文。
     */
    fun moveCursor(steps: Int, select: Boolean = false) {
        if (steps == 0) return
        leaveComposition()
        editor.moveCursor(steps, select)
        finishAction()
    }

    /**
     * Delete 鍵向左掃：選取游標前 words 個字詞；wholeLine 就選取到行首。
     * 英文和數字以連續的一串算一個字詞，中文和標點每個字算一個；空白不另算，連同前面的字詞一起選取。
     * words 為 0 就取消選取。
     */
    fun selectBackward(words: Int, wholeLine: Boolean) {
        val anchor = backwardAnchor ?: run {
            leaveComposition()
            editor.cursorPosition()?.also { backwardAnchor = it } ?: return
        }
        // 選取了一部分之後，游標前的文字不再包括選取的部分，所以用開始時記下的。
        val before = backwardText ?: editor.textBeforeCursor(BACKWARD_CONTEXT_LENGTH).also { backwardText = it }
        val length = when {
            wholeLine -> before.length - (before.lastIndexOf('\n') + 1)
            else -> lengthOfWordsBefore(before, words)
        }.coerceAtMost(anchor)
        editor.setSelection(anchor - length, anchor)
        backwardLength = length
    }

    /** Delete 鍵放手：delete 為 true 就刪掉選取的字詞，否則還原游標。 */
    fun finishBackwardSelection(delete: Boolean) {
        val anchor = backwardAnchor ?: return
        if (delete && backwardLength > 0) editor.sendDeleteKey() else editor.setSelection(anchor, anchor)
        backwardAnchor = null
        backwardText = null
        backwardLength = 0
        finishAction()
    }

    private var backwardAnchor: Int? = null
    private var backwardText: String? = null
    private var backwardLength = 0

    private fun lengthOfWordsBefore(text: String, words: Int): Int {
        var index = text.length
        repeat(words) {
            while (index > 0 && text[index - 1].isWhitespace()) index--
            if (index == 0) return text.length
            if (text[index - 1].isAsciiLetterOrDigit()) {
                while (index > 0 && text[index - 1].isAsciiLetterOrDigit()) index--
            } else {
                index -= Character.charCount(text.codePointBefore(index))
            }
        }
        return text.length - index
    }

    private fun Char.isAsciiLetterOrDigit() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

    /** 組字留在文字框裏當作英文；同時收起聯想、放棄刪空格。 */
    private fun leaveComposition() {
        if (composition.isNotEmpty()) {
            if (composing) editor.finishComposingText()
            clearComposition()
        }
        clearFollowUps()
        autoSpace = null
    }

    /** 英文建議：寫入該字並加一個空格。 */
    fun englishSuggestion(word: String) {
        clearFollowUps()
        writeEnglish(word, withSpace = true, kind = WriteKind.EnglishSuggestion)
        finishAction()
    }

    /** 候選列的 ✕：收起聯想或相關符號。 */
    fun dismissFollowUps() {
        clearFollowUps()
        publish()
    }

    /** 按一下：下一個字母大寫；連按兩下：一直大寫。句首自動大寫時按一下就取消。 */
    fun shift() {
        val now = clock()
        shift = when (shift) {
            ShiftState.Off -> ShiftState.OneShot
            ShiftState.Auto -> ShiftState.Off
            ShiftState.OneShot -> if (now - lastShiftTap <= DOUBLE_TAP_MILLIS) ShiftState.Locked else ShiftState.Off
            ShiftState.Locked -> ShiftState.Off
        }
        lastShiftTap = now
        publish()
    }

    /** 文字框回報游標或組字範圍改變。使用者移動了游標，打過的字母就留在文字框，當作英文。 */
    fun selectionChanged(selectionStart: Int, selectionEnd: Int, compositionStart: Int, compositionEnd: Int) {
        // 選取了文字或游標移到別處，相關符號已經取代不到剛打的符號，所以收起。
        val written = writtenSymbol
        if (written != null && (selectionStart != selectionEnd || editor.textBeforeCursor(written.length) != written)) {
            clearRelatedSymbols()
            publish()
        }
        if (!composing || composition.isEmpty()) return
        if (compositionStart < 0) {
            // 可能是寫入之前的回報，遲了才到。
            if (awaitingComposingEcho) return
        } else {
            awaitingComposingEcho = false
            if (selectionStart == selectionEnd && selectionEnd == compositionEnd) return
        }
        editor.finishComposingText()
        clearComposition()
        autoSpace = null
        finishAction()
    }

    /** 鍵盤收起或文字框失去焦點：組字留在文字框裏。 */
    fun finish() {
        if (composing && composition.isNotEmpty()) editor.finishComposingText()
        clearComposition()
        clearFollowUps()
        autoSpace = null
    }

    /**
     * picked：使用者從候選字選的（要記錄成記憶字），不是聯想。
     * codeLength：候選字用了組字前面多少個字母；其餘字母留在組字，繼續選字（分段寫入），這時不出聯想。
     * sentence：整句候選，不記錄成記憶字。開了連打的話，寫入的字和上文的搭配都記下（搭配記憶）。
     */
    private fun writeChinese(text: String, picked: Boolean, codeLength: Int = composition.length, sentence: Boolean = false) {
        clearFollowUps()
        val engineSettings = settings().engine
        val recordPairs = learning && engineSettings.rememberCandidates && engineSettings.continuousTyping
        // 聯想沒有組字，上文就是游標前的文字。
        val before = when {
            !recordPairs -> ""
            composition.isEmpty() -> editor.textBeforeCursor(CHARACTER_CONTEXT_LENGTH)
            else -> contextBefore
        }
        val rest = composition.substring(codeLength.coerceIn(0, composition.length))
        val removeSpace = shouldRemoveAutoSpace(text)
        editor.beginBatchEdit()
        when {
            composition.isEmpty() -> Unit
            !composing -> repeat(composition.length) { editor.sendDeleteKey() }
            removeSpace -> {
                editor.setComposingText("")
                editor.finishComposingText()
            }
            // 不用刪空格的話，commitText 會直接取代組字。
        }
        if (removeSpace) deleteAutoSpace()
        editor.commitText(text)
        if (rest.isNotEmpty()) {
            if (composing) editor.setComposingText(rest) else editor.commitText(rest)
        }
        editor.endBatchEdit()
        // 分段寫入：留在組字裏的改好的字照樣保留。
        val keptFixes = fixes.filter { it.start >= codeLength }.map { it.copy(start = it.start - codeLength, end = it.end - codeLength) }
        clearComposition()
        autoSpace = null
        val engine = engine()
        if (picked && !sentence && learning && engineSettings.rememberCandidates) engine?.recordPick(text)
        if (recordPairs) engine?.rememberPairs(before, text)
        contextBefore = text
        if (rest.isEmpty()) {
            associations = engine?.associations(text, settings().engine).orEmpty()
            keywordEmoji = if (settings().emojiKeywords) keywordEmojiAfter(text) else emptyList()
        } else {
            composition.append(rest)
            fixes = keptFixes
            awaitingComposingEcho = true
            refreshCandidates()
        }
        onWrite(if (picked) WriteKind.Chinese else WriteKind.Association)
    }

    /** 把組字換成英文 word 寫入，可以在後面加一個空格。 */
    private fun writeEnglish(word: String, withSpace: Boolean, kind: WriteKind = WriteKind.English) {
        val typed = composition.toString()
        val before = textBeforeComposition()
        val text = if (withSpace) "$word " else word
        if (composing) {
            editor.commitText(text)
        } else if (word != typed) {
            // 直接寫入模式：字母已經在文字框裏，要換成 word。
            repeat(typed.length) { editor.sendDeleteKey() }
            editor.commitText(text)
        } else if (withSpace) {
            editor.commitText(" ")
        }
        clearComposition()
        autoSpace = if (withSpace && settings().smartSpaceRemoval) (before + text).takeLast(CONTEXT_LENGTH) else null
        onWrite(kind)
    }

    private fun shouldRemoveAutoSpace(text: String): Boolean {
        val expected = autoSpace ?: return false
        return settings().smartSpaceRemoval &&
            Punctuation.removesAutoSpace(text) &&
            textBeforeComposition().endsWith(expected)
    }

    private fun deleteAutoSpace() {
        if (composing) editor.deleteBeforeCursor(1) else editor.sendDeleteKey()
    }

    private fun textBeforeComposition(): String =
        editor.textBeforeCursor(CONTEXT_LENGTH + composition.length).dropLast(composition.length)

    /** 單獨一個小寫的 i 寫成 I。 */
    private fun typedEnglish(): String = composition.toString().let { if (it == "i") "I" else it }

    private fun refreshCandidates() {
        val typed = composition.toString()
        val engine = engine()
        val found = if (typed.isEmpty()) emptyList() else engine?.candidates(typed, settings().engine, contextBefore, fixes).orEmpty()
        // 剛改好字：整句候選只換了那個字，其餘的字照舊。
        val held = heldSentence
        candidates = if (held == null) {
            found
        } else {
            found.map { if (it.sentence) it.copy(text = held.joinToString("") { part -> part.text }, parts = held) else it }
                .distinctBy { it.text }
        }
        completions = if (typed.isEmpty() || !offerCompletions) emptyList() else engine?.englishSuggestions(typed).orEmpty()
    }

    /**
     * 游標前的文字以 Emoji 關鍵字結尾的話，相關的 Emoji。有些 App 稍後才套用寫入，讀到的文字未有剛寫入的字，
     * 那樣就只看剛寫入的字，免得出了前一個詞的 Emoji。
     */
    private fun keywordEmojiAfter(written: String): List<String> {
        val keywords = emojiKeywords() ?: return emptyList()
        val before = editor.textBeforeCursor(CONTEXT_LENGTH)
        return keywords.emojiAfter(if (before.endsWith(written)) before else written)
    }

    /** 收起寫入之後候選列接着出的東西：聯想、Emoji 關鍵字的 Emoji 和相關符號；也收起改字時可以換上的字。 */
    private fun clearFollowUps() {
        associations = emptyList()
        keywordEmoji = emptyList()
        fixChoices = null
        clearRelatedSymbols()
    }

    private fun clearRelatedSymbols() {
        relatedSymbols = emptyList()
        writtenSymbol = null
    }

    private fun clearComposition() {
        composition.clear()
        candidates = emptyList()
        completions = emptyList()
        fixes = emptyList()
        heldSentence = null
        fixChoices = null
        awaitingComposingEcho = false
    }

    private fun finishAction() {
        if (shift != ShiftState.OneShot && shift != ShiftState.Locked) {
            val auto = settings().autoCapitalize && composition.isEmpty() && editor.capsModeAtCursor()
            shift = if (auto) ShiftState.Auto else ShiftState.Off
        }
        publish()
    }

    private fun publish() {
        val typed = composition.toString()
        // 有組字的話，標點會跟在當英文寫入的字母後面，出半形。
        val smartPunctuation = !field.forcesHalfWidthPunctuation && typed.isEmpty()
        val before = if (smartPunctuation) editor.textBeforeCursor(PUNCTUATION_CONTEXT_LENGTH) else ""
        state = KeyboardState(
            composition = typed,
            candidates = candidates,
            englishSuggestions = if (typed.isEmpty()) emptyList() else (listOf(typedEnglish()) + completions).distinct(),
            associations = associations,
            keywordEmoji = keywordEmoji,
            markTopCandidate = settings().spaceMode == SpaceMode.SelectChinese && candidates.isNotEmpty(),
            fullWidthPunctuation = smartPunctuation && Punctuation.fullWidthAfter(before),
            fullWidthCloseParenthesis = smartPunctuation && Punctuation.fullWidth(PunctuationKey.CloseParenthesis, before),
            relatedSymbols = relatedSymbols,
            writtenSymbol = writtenSymbol,
            characterFix = fixChoices,
            shift = shift,
        )
        onStateChanged(state)
    }

    private companion object {
        const val CONTEXT_LENGTH = 32

        /** 上文只看游標前一個字；BMP 以外的字佔兩個 char。 */
        const val CHARACTER_CONTEXT_LENGTH = 2

        /** 智能全形標點最多看游標前多少字元；「)」要找前面未配對的括號，所以比 CONTEXT_LENGTH 長。 */
        const val PUNCTUATION_CONTEXT_LENGTH = 200
        const val DOUBLE_TAP_MILLIS = 300L

        /** 向左掃選取時最多看游標前多少字元；整行選取也以此為限。 */
        const val BACKWARD_CONTEXT_LENGTH = 1000
    }
}
