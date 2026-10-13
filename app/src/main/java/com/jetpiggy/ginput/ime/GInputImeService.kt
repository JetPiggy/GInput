package com.jetpiggy.ginput.ime

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.jetpiggy.ginput.BuildConfig
import com.jetpiggy.ginput.R
import com.jetpiggy.ginput.clipboard.ClipboardHistory
import com.jetpiggy.ginput.clipboard.ClipboardHistoryFile
import com.jetpiggy.ginput.clipboard.PhraseStore
import com.jetpiggy.ginput.clipboard.SavedPhrases
import com.jetpiggy.ginput.cloudtext.CloudTextClient
import com.jetpiggy.ginput.cloudtext.CloudTextState
import com.jetpiggy.ginput.cloudtext.CloudTextStatus
import com.jetpiggy.ginput.cloudtext.CloudTool
import com.jetpiggy.ginput.cloudtext.DailyAllowance
import com.jetpiggy.ginput.cloudtext.DailyAllowanceFile
import com.jetpiggy.ginput.cloudtext.FailReason
import com.jetpiggy.ginput.cloudtext.SourceRead
import com.jetpiggy.ginput.cloudtext.SourceText
import com.jetpiggy.ginput.cloudtext.TextTask
import com.jetpiggy.ginput.dictionary.BundledDictionary
import com.jetpiggy.ginput.dictionary.CandidateMemoryFile
import com.jetpiggy.ginput.dictionary.PairMemoryFile
import com.jetpiggy.ginput.emoji.EmojiCatalog
import com.jetpiggy.ginput.emoji.EmojiCategory
import com.jetpiggy.ginput.emoji.EmojiRecentsFile
import com.jetpiggy.ginput.engine.CandidateEngine
import com.jetpiggy.ginput.engine.CandidateMemory
import com.jetpiggy.ginput.engine.EmojiKeywords
import com.jetpiggy.ginput.engine.LearnedPair
import com.jetpiggy.ginput.engine.PairMemory
import com.jetpiggy.ginput.engine.Scheme
import com.jetpiggy.ginput.keyboard.ClipboardContent
import com.jetpiggy.ginput.keyboard.KeyAction
import com.jetpiggy.ginput.keyboard.KeyboardScreen
import com.jetpiggy.ginput.keyboard.NavigationBarSpace
import com.jetpiggy.ginput.keyboard.VoiceInputKey
import com.jetpiggy.ginput.settings.KeyboardSettings
import com.jetpiggy.ginput.settings.MainActivity
import com.jetpiggy.ginput.settings.SettingsStore
import com.jetpiggy.ginput.typing.FieldKind
import com.jetpiggy.ginput.typing.KeyboardState
import com.jetpiggy.ginput.typing.TypingSession
import com.jetpiggy.ginput.typing.WriteKind
import com.jetpiggy.ginput.update.UpdateNotices
import com.jetpiggy.ginput.update.WhatsNew
import com.jetpiggy.ginput.usagestats.UsageEvent
import com.jetpiggy.ginput.usagestats.UsageStats
import com.jetpiggy.ginput.ui.theme.GInputTheme
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class GInputImeService : LifecycleInputMethodService() {

    private val editor = InputConnectionEditor(this)
    private var engine: CandidateEngine? = null
    private var emojiKeywords: EmojiKeywords? = null
    private lateinit var settingsStore: SettingsStore
    private lateinit var phraseStore: PhraseStore
    private lateinit var usageStats: UsageStats

    /** 網站公佈的新版本和公告。 */
    private lateinit var updates: UpdateNotices

    /** 字庫載入完成前收到「清除全部記憶」，載入後再清。 */
    private var memoryClearPending = false

    private var session: TypingSession? = null

    /** 目前的文字框容許學習（不是無痕模式）：記錄記憶字和最近使用的 Emoji。 */
    private var learning = true

    /** 目前的文字框是密碼欄位，包括只收數字的密碼（例如 PIN）。 */
    private var passwordField = false
    private var keyboardState by mutableStateOf(KeyboardState())
    private var keyboardSettings by mutableStateOf(KeyboardSettings())
    private var field by mutableStateOf(FieldKind.Text)
    private var voiceInput by mutableStateOf(VoiceInputKey.Hidden)
    private var emojiCategories by mutableStateOf<List<EmojiCategory>>(emptyList())
    private var recentEmoji by mutableStateOf<List<String>>(emptyList())
    private var clipboardHistory by mutableStateOf(ClipboardHistory())
    private var clipboardHistoryEnabled by mutableStateOf(true)
    private var phrases by mutableStateOf(SavedPhrases())
    private var cloudText by mutableStateOf(CloudTextState())

    /** 更新了 GInput 之後，還未按「知道了」的新版簡介；見 WhatsNew。 */
    private var whatsNew by mutableStateOf(false)

    /** 導航列在鍵盤視窗裏佔的位置。 */
    private val navigationBar = NavigationBarSpace()

    /** 鍵盤視窗正在顯示；由隱藏變成顯示時，先向系統讀導航列的高度。 */
    private var windowVisible = false

    private var clipboardManager: ClipboardManager? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { recordClip(startup = false) }
    private var purgeJob: Job? = null

    /** 打開了的翻譯面板或改寫面板；沒有打開是 null。 */
    private var cloudTool: CloudTool? = null

    /** 送出了的原文，取代前用來核對文字框有沒有改動。 */
    private var cloudSource: SourceText? = null
    private var cloudJob: Job? = null
    private val cloudClient = CloudTextClient()

    /** 每日次數。檔案載入完成（allowanceLoad）之前不數，以免蓋掉檔案裏的次數。 */
    private var allowance = DailyAllowance()
    private lateinit var allowanceLoad: Job

    /** 最近收到的選取範圍有沒有文字：有的話原文是選取的部分，否則是整個文字框。 */
    private var hasSelection = false

    private val memoryWriter = LatestWriter<Map<String, Int>>("記憶字") {
        CandidateMemoryFile.save(applicationContext, it)
    }
    private val pairsWriter = LatestWriter<List<LearnedPair>>("搭配記憶") {
        PairMemoryFile.save(applicationContext, it)
    }
    private val recentsWriter = LatestWriter<List<String>>("最近使用的 Emoji") {
        EmojiRecentsFile.save(applicationContext, it)
    }
    private val historyWriter = LatestWriter<ClipboardHistory>("剪貼簿歷史") {
        ClipboardHistoryFile.save(applicationContext, it)
    }
    private val allowanceWriter = LatestWriter<DailyAllowance>("每日次數") {
        DailyAllowanceFile.save(applicationContext, it)
    }

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore.get(this)
        phraseStore = PhraseStore.get(this)
        keyboardSettings = settingsStore.settings.value.keyboardOnScreen
        whatsNew = checkWhatsNew()
        usageStats = UsageStats(applicationContext, settingsStore, lifecycleScope).also { it.load() }
        updates = UpdateNotices.get(applicationContext).also { it.load(lifecycleScope) }
        lifecycleScope.launch {
            var stats = settingsStore.settings.value.usageStats
            settingsStore.settings.collect {
                keyboardSettings = it.keyboardOnScreen
                clipboardHistoryEnabled = it.clipboardHistory
                cloudText = cloudText.copy(noticeAccepted = it.cloudNoticeAccepted)
                // 使用者關了使用統計，就刪掉未送出的次數。
                if (stats && !it.usageStats) usageStats.clear()
                stats = it.usageStats
                // 關了剪貼簿歷史，就刪除已記下的全部項目（連檔案）。
                if (!it.clipboardHistory) updateHistory(ClipboardHistory())
                // 開了連打，就在背景載入碼表，第一次打字才不會卡一下；已經載入的話甚麼都不做。
                val loaded = engine
                if (loaded != null && it.continuousTyping) {
                    launch(Dispatchers.IO) { loaded.loadContinuousTyping(it.typing.engine) }
                }
            }
        }
        lifecycleScope.launch {
            phraseStore.phrases.collect { phrases = it }
        }
        allowanceLoad = lifecycleScope.launch {
            allowance = withContext(Dispatchers.IO) { DailyAllowanceFile.load(applicationContext) }
        }
        lifecycleScope.launch {
            CandidateMemoryFile.clears.collect { clearMemory() }
        }
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val memory = CandidateMemory(CandidateMemoryFile.load(applicationContext), onChanged = memoryWriter::save)
                    val pairs = PairMemory(PairMemoryFile.load(applicationContext), onChanged = pairsWriter::save)
                    CandidateEngine(BundledDictionary.open(applicationContext), memory, pairs).also {
                        // 粵拼的拼法和連打的碼表在背景先載入，第一次打字時才不會卡一下。
                        val settings = settingsStore.settings.value
                        if (Scheme.Jyutping in settings.enabledSchemes) it.loadJyutping()
                        if (settings.continuousTyping) it.loadContinuousTyping(settings.typing.engine)
                    }
                }
            }.onSuccess { loaded ->
                engine = loaded
                if (memoryClearPending) clearMemory()
            }.onFailure {
                Log.e(TAG, "無法載入字庫", it)
            }
        }
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val categories = EmojiCatalog.load(applicationContext)
                    Triple(
                        categories,
                        EmojiRecentsFile.load(applicationContext),
                        EmojiCatalog.loadKeywords(applicationContext, categories),
                    )
                }
            }.onSuccess { (categories, recents, keywords) ->
                emojiCategories = categories
                recentEmoji = recents
                emojiKeywords = keywords
            }.onFailure {
                Log.e(TAG, "無法載入 Emoji", it)
            }
        }
        lifecycleScope.launch { startClipboardHistory() }
    }

    override fun onDestroy() {
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        installViewTreeOwners()
        return ComposeView(this).apply {
            setContent {
                val updateState by updates.state.collectAsState()
                val settingsBadge = remember(updateState) { updateState.unseen(BuildConfig.VERSION_CODE) }
                GInputTheme {
                    KeyboardScreen(
                        state = keyboardState,
                        settings = keyboardSettings,
                        field = field,
                        emojiCategories = emojiCategories,
                        recentEmoji = recentEmoji,
                        clipboard = ClipboardContent(
                            history = clipboardHistory.items.map { it.text },
                            historyEnabled = clipboardHistoryEnabled,
                            phrases = phrases.items,
                        ),
                        onKey = ::handleKey,
                        navigationBar = navigationBar,
                        voiceInput = voiceInput,
                        cloudText = cloudText,
                        onUsage = ::recordUsage,
                        whatsNew = whatsNew,
                        settingsBadge = settingsBadge,
                    )
                }
            }
            navigationBar.follow(this)
        }
    }

    override fun onWindowShown() {
        super.onWindowShown()
        // 鍵盤視窗由隱藏變成顯示：先向系統讀導航列現在的高度，視窗第一次量度就計在內。
        if (!windowVisible) navigationBar.beforeShow()
        windowVisible = true
    }

    override fun onWindowHidden() {
        windowVisible = false
        super.onWindowHidden()
    }

    // 橫向時也不用全螢幕的抽取文字框。
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        session?.finish()
        val info = editorInfo ?: EditorInfo()
        val previousField = field
        field = info.fieldKind()
        hasSelection = info.initialSelStart >= 0 && info.initialSelStart != info.initialSelEnd
        learning = info.allowsLearning()
        passwordField = info.isPassword()
        // 每次打開鍵盤都重新查看，使用者剛到系統設定啟用了語音輸入法也看得到。
        voiceInput = when {
            passwordField || info.disallowsMicrophone() -> VoiceInputKey.Hidden
            findVoiceInput() != null -> VoiceInputKey.Available
            else -> VoiceInputKey.NotEnabled
        }
        session = TypingSession(
            editor = editor,
            field = field,
            composingSupported = info.supportsComposing(),
            settings = { settingsStore.settings.value.typing },
            engine = { engine },
            emojiKeywords = { emojiKeywords },
            learning = learning,
            offerCompletions = (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) == 0,
            onStateChanged = { keyboardState = it },
            onWrite = { kind -> recordUsage(kind.usageEvent) },
        ).also { it.start() }
        // 文字框的種類沒變的話，鍵盤仍然停在翻譯面板或改寫面板：同一個文字框保留處理中的工作和結果，
        // 換了文字框就重新讀原文。種類變了，鍵盤會回到字母頁。
        cloudTool?.let { tool ->
            when {
                field != previousField -> closeCloudTool()
                restarting -> refreshCloudSource()
                else -> startCloudTool(tool)
            }
        }
        usageStats.sendIfDue()
        updates.checkIfDue(lifecycleScope)
        purgeClipboard()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        session?.finish()
        cloudJob?.cancel()
        usageStats.save()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        hasSelection = newSelStart != newSelEnd
        session?.selectionChanged(newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        refreshCloudSource()
    }

    private fun handleKey(action: KeyAction) {
        action.usageEvent?.let(::recordUsage)
        when (action) {
            KeyAction.ShowInputMethodPicker -> getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
            KeyAction.HideKeyboard -> requestHideSelf(0)
            KeyAction.Copy -> currentInputConnection?.performContextMenuAction(android.R.id.copy)
            KeyAction.Paste -> currentInputConnection?.performContextMenuAction(android.R.id.paste)
            KeyAction.SelectAll -> currentInputConnection?.performContextMenuAction(android.R.id.selectAll)
            KeyAction.OpenSettings ->
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            KeyAction.OpenClipboard -> purgeClipboard()
            KeyAction.ManagePhrases -> startActivity(MainActivity.phrasesIntent(this))
            KeyAction.VoiceInput -> switchToVoiceInput()
            KeyAction.VoiceInputSettings ->
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            is KeyAction.SavePhrase -> savePhrase(action.text)
            is KeyAction.DeleteClipboardItem -> updateHistory(clipboardHistory.remove(action.text))
            is KeyAction.DeletePhrase -> phraseStore.update { it.remove(action.text) }
            is KeyAction.OpenCloudTool -> openCloudTool(action.tool)
            KeyAction.AcceptCloudNotice -> settingsStore.update { it.copy(cloudNoticeAccepted = true) }
            is KeyAction.RunTextTask -> runTextTask(action.task)
            KeyAction.ApplyTextResult -> applyTextResult()
            KeyAction.CloseCloudTool -> closeCloudTool()
            KeyAction.DismissWhatsNew -> {
                whatsNew = false
                settingsStore.update { it.copy(whatsNewSeen = WhatsNew.VERSION) }
            }
            else -> session?.let { type(it, action) }
        }
    }

    private fun type(session: TypingSession, action: KeyAction) {
        when (action) {
            is KeyAction.Letter -> session.letter(action.letter)
            is KeyAction.Text -> session.symbol(action.text, offerRelated = true)
            is KeyAction.SmartPunctuation -> session.punctuation(action.key)
            KeyAction.Space -> session.space()
            KeyAction.Enter -> session.enter()
            KeyAction.Delete -> session.delete()
            KeyAction.Shift -> session.shift()
            is KeyAction.PickCandidate -> session.candidate(action.text)
            is KeyAction.PickEnglish -> session.englishSuggestion(action.word)
            is KeyAction.PickAssociation -> session.association(action.text)
            is KeyAction.PickRelatedSymbol -> session.relatedSymbol(action.text)
            KeyAction.DismissFollowUps -> session.dismissFollowUps()
            is KeyAction.ForgetCandidate -> session.forget(action.text)
            is KeyAction.CommitEmoji -> {
                session.symbol(action.emoji)
                if (learning) rememberEmoji(action.emoji)
            }
            is KeyAction.PickKeywordEmoji -> {
                session.symbol(action.emoji)
                if (learning) rememberEmoji(action.emoji)
            }
            is KeyAction.WriteClipboardItem -> session.symbol(action.text)
            is KeyAction.WritePhrase -> session.symbol(action.text)
            is KeyAction.MoveCursor -> session.moveCursor(action.steps, action.select)
            is KeyAction.SelectBackward -> session.selectBackward(action.words, action.wholeLine)
            is KeyAction.FinishBackwardSelection -> session.finishBackwardSelection(action.delete)
            KeyAction.ShowInputMethodPicker,
            KeyAction.HideKeyboard,
            KeyAction.Copy,
            KeyAction.Paste,
            KeyAction.SelectAll,
            KeyAction.OpenSettings,
            KeyAction.OpenClipboard,
            KeyAction.ManagePhrases,
            KeyAction.VoiceInput,
            KeyAction.VoiceInputSettings,
            is KeyAction.SavePhrase,
            is KeyAction.DeleteClipboardItem,
            is KeyAction.DeletePhrase,
            is KeyAction.OpenCloudTool,
            KeyAction.AcceptCloudNotice,
            is KeyAction.RunTextTask,
            KeyAction.ApplyTextResult,
            KeyAction.CloseCloudTool,
            KeyAction.DismissWhatsNew -> Unit
        }
    }

    /** 要不要顯示新版簡介。全新安裝的話當作已經看過，之後更新了才顯示新的簡介。 */
    private fun checkWhatsNew(): Boolean {
        if (settingsStore.settings.value.whatsNewSeen >= WhatsNew.VERSION) return false
        val updated = WhatsNew.updated(this)
        if (!updated) settingsStore.update { it.copy(whatsNewSeen = WhatsNew.VERSION) }
        return updated
    }

    /** 密碼欄位和要求不學習的欄位（無痕模式）不數。 */
    private fun recordUsage(event: UsageEvent) {
        if (learning && !passwordField) usageStats.record(event)
    }

    private val WriteKind.usageEvent: UsageEvent
        get() = when (this) {
            WriteKind.Chinese -> UsageEvent.Chinese
            WriteKind.Association -> UsageEvent.Association
            WriteKind.English -> UsageEvent.English
            WriteKind.EnglishSuggestion -> UsageEvent.EnglishSuggestion
        }

    /** 由按鍵動作得出的使用統計項目；寫入文字的由 TypingSession 的 onWrite 數，這裏不重複。 */
    private val KeyAction.usageEvent: UsageEvent?
        get() = when (this) {
            is KeyAction.CommitEmoji -> UsageEvent.Emoji
            is KeyAction.PickKeywordEmoji -> UsageEvent.KeywordEmoji
            is KeyAction.WriteClipboardItem -> UsageEvent.ClipboardItem
            is KeyAction.WritePhrase -> UsageEvent.Phrase
            is KeyAction.SavePhrase -> UsageEvent.SavePhrase
            KeyAction.OpenClipboard -> UsageEvent.ClipboardPanel
            is KeyAction.FinishBackwardSelection -> if (delete) UsageEvent.DeleteSwipe else null
            is KeyAction.ForgetCandidate -> UsageEvent.Forget
            KeyAction.Copy -> UsageEvent.Copy
            KeyAction.Paste -> UsageEvent.Paste
            KeyAction.SelectAll -> UsageEvent.SelectAll
            KeyAction.OpenSettings -> UsageEvent.OpenSettings
            KeyAction.ShowInputMethodPicker -> UsageEvent.KeyboardSwitch
            KeyAction.VoiceInput -> UsageEvent.VoiceInput
            KeyAction.HideKeyboard -> UsageEvent.HideKeyboard
            else -> null
        }

    private fun findVoiceInput() = getSystemService(InputMethodManager::class.java)?.enabledVoiceInput(packageName)

    /** 切換到語音輸入法，用完由它切換回來。剛好被停用了的話，再按就會提示到設定啟用。 */
    private fun switchToVoiceInput() {
        val (method, subtype) = findVoiceInput() ?: run {
            voiceInput = VoiceInputKey.NotEnabled
            return
        }
        runCatching { switchInputMethod(method.id, subtype) }
            .onFailure { Log.w(TAG, "無法切換到語音輸入法", it) }
    }

    /** 工具列的「翻譯」或「改寫」。已經打開同一個面板的話，只重新讀原文。 */
    private fun openCloudTool(tool: CloudTool) {
        if (tool == cloudTool) refreshCloudSource() else startCloudTool(tool)
    }

    /** 開始用翻譯面板或改寫面板：讀原文；之前未完成的處理不再理會。 */
    private fun startCloudTool(tool: CloudTool) {
        cloudJob?.cancel()
        cloudTool = tool
        cloudSource = null
        val read = SourceText.read(editor, hasSelection)
        cloudText = cloudText.copy(
            remaining = allowance.remaining(LocalDate.now()),
            source = read.preview,
            status = read.status,
        )
    }

    /**
     * 選取範圍變了：重新讀原文，例如原文太長時選取了其中一部分。處理中或已有結果時不變，取代前會再核對。
     * 原文沒有變的話，保留失敗的訊息。
     */
    private fun refreshCloudSource() {
        val status = cloudText.status
        if (cloudTool == null || status is CloudTextStatus.Working || status is CloudTextStatus.Done) return
        val read = SourceText.read(editor, hasSelection)
        val unchanged = read.preview == cloudText.source &&
            (read.status == status || status is CloudTextStatus.Failed && read is SourceRead.Found)
        if (!unchanged) cloudText = cloudText.copy(source = read.preview, status = read.status)
    }

    /** 翻譯面板或改寫面板的選項：把原文送出處理。只有成功才計入每日次數。 */
    private fun runTextTask(task: TextTask) {
        if (!cloudText.noticeAccepted || cloudText.status == CloudTextStatus.Working(task)) return
        // 以鍵盤正在顯示的面板為準。
        if (task.tool != cloudTool) startCloudTool(task.tool)
        if (allowance.remaining(LocalDate.now()) == 0) {
            cloudText = cloudText.copy(remaining = 0)
            return
        }
        // 按下時再讀一次原文：打開面板之後文字框可能改動過。
        val read = SourceText.read(editor, hasSelection)
        if (read !is SourceRead.Found) {
            cloudText = cloudText.copy(source = read.preview, status = read.status)
            return
        }
        val source = read.source
        cloudJob?.cancel()
        cloudSource = source
        cloudText = cloudText.copy(source = read.preview, status = CloudTextStatus.Working(task))
        cloudJob = lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) { cloudClient.run(task, source.trimmed) }
            if (outcome is CloudTextClient.Outcome.Done) {
                allowanceLoad.join()
                allowance = allowance.use(LocalDate.now())
                allowanceWriter.save(allowance)
                recordUsage(if (task.tool == CloudTool.Translation) UsageEvent.CloudTranslation else UsageEvent.Rewrite)
            }
            cloudText = cloudText.copy(
                remaining = allowance.remaining(LocalDate.now()),
                status = when (outcome) {
                    is CloudTextClient.Outcome.Done -> CloudTextStatus.Done(task, outcome.result)
                    CloudTextClient.Outcome.Busy -> CloudTextStatus.Failed(task, FailReason.Busy)
                    CloudTextClient.Outcome.Offline -> CloudTextStatus.Failed(task, FailReason.Offline)
                    CloudTextClient.Outcome.Failed -> CloudTextStatus.Failed(task, FailReason.Error)
                },
            )
        }
    }

    /** 用結果取代原文；文字框改動過就不取代，並提示。 */
    private fun applyTextResult() {
        val done = cloudText.status as? CloudTextStatus.Done ?: return
        val source = cloudSource ?: return
        if (SourceText.replace(editor, source, done.result, hasSelection)) {
            // 文字框的內容換了，自動大寫等狀態要重新看。
            session?.finish()
            session?.start()
        } else {
            Toast.makeText(this, R.string.cloud_changed, Toast.LENGTH_SHORT).show()
        }
    }

    /** 離開翻譯面板或改寫面板：未完成的處理不再理會，也不保留原文和結果。 */
    private fun closeCloudTool() {
        cloudJob?.cancel()
        cloudJob = null
        cloudTool = null
        cloudSource = null
        cloudText = cloudText.copy(source = "", status = CloudTextStatus.Ready)
    }

    /** 面板顯示的原文：去掉前後空白；讀不到原文就是空白。 */
    private val SourceRead.preview: String
        get() = (this as? SourceRead.Found)?.source?.trimmed.orEmpty()

    private val SourceRead.status: CloudTextStatus
        get() = when (this) {
            is SourceRead.Found -> CloudTextStatus.Ready
            SourceRead.Empty -> CloudTextStatus.NoText
            SourceRead.TooLong -> CloudTextStatus.TooLong
            SourceRead.Unreadable -> CloudTextStatus.Unreadable
        }

    private fun clearMemory() {
        val engine = engine
        memoryClearPending = engine == null
        engine?.clearMemory()
    }

    private fun rememberEmoji(emoji: String) {
        recentEmoji = (listOf(emoji) + recentEmoji.filter { it != emoji }).take(EmojiRecentsFile.MAX_RECENTS)
        recentsWriter.save(recentEmoji)
    }

    /** 已有的常用短語只移到最前；滿了就不加，並提示。 */
    private fun savePhrase(text: String) {
        val current = phraseStore.phrases.value
        val message = if (current.full && text !in current.items) {
            getString(R.string.phrases_full, SavedPhrases.MAX_PHRASES)
        } else {
            phraseStore.update { it.add(text) }
            getString(R.string.phrase_saved)
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /** 載入剪貼簿歷史，開始記錄系統剪貼簿，並補記 GInput 啟動前複製的文字。 */
    private suspend fun startClipboardHistory() {
        val saved = withContext(Dispatchers.IO) {
            if (settingsStore.settings.value.clipboardHistory) {
                ClipboardHistoryFile.load(applicationContext)
            } else {
                // 關了的話檔案應該已經刪掉；以防萬一再刪一次。
                ClipboardHistoryFile.delete(applicationContext)
                ClipboardHistory()
            }
        }
        // 載入期間關了剪貼簿歷史的話，就不用載入的項目。
        if (settingsStore.settings.value.clipboardHistory) clipboardHistory = saved
        clipboardManager = getSystemService(ClipboardManager::class.java)?.also {
            it.addPrimaryClipChangedListener(clipListener)
        }
        recordClip(startup = true)
        purgeClipboard()
    }

    /**
     * 系統剪貼簿有新複製的內容時記下；startup 是 GInput 啟動時補記 1 小時內複製、未見過的文字。
     * 鍵盤正在密碼欄位或無痕欄位的話，補記也不記，只當作見過：GInput 剛啟動時，那段文字可能是在這個欄位複製的。
     * 規則見 docs/spec/v1.md 的「剪貼簿和常用短語」。
     */
    private fun recordClip(startup: Boolean) {
        if (!settingsStore.settings.value.clipboardHistory) return
        // 例外的訊息可能帶有複製的文字，所以讀不到就算，不記錄。
        val clip = runCatching { clipboardManager?.primaryClip }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        val copiedAt = clip.description.timestamp.takeIf { it > 0 } ?: now
        if (startup && (copiedAt <= clipboardHistory.lastSeen || ClipboardHistory.expired(copiedAt, now))) return
        // 只收文字：圖片、檔案等只有 URI 的內容，text 是 null。
        val text = if (clip.itemCount > 0) clip.getItemAt(0).text?.toString() else null
        val record = !clip.description.isSensitive() && clipboardAllowedHere()
        updateHistory(
            if (text != null && record) clipboardHistory.add(text, copiedAt) else clipboardHistory.seen(copiedAt),
        )
    }

    /** 鍵盤正在密碼欄位（包括數字密碼）或要求不學習的欄位（例如無痕模式）時，複製的不記錄。 */
    private fun clipboardAllowedHere(): Boolean {
        val info = currentInputEditorInfo
        if (!currentInputStarted || info == null) return true
        return !info.isPassword() && info.allowsLearning()
    }

    private fun updateHistory(next: ClipboardHistory) {
        if (next != clipboardHistory) {
            clipboardHistory = next
            historyWriter.save(next)
        }
        schedulePurge()
    }

    /** 刪去過期的項目（連檔案）。 */
    private fun purgeClipboard() = updateHistory(clipboardHistory.fresh(System.currentTimeMillis()))

    /**
     * 到最早的一項過期時刪去它。delay 在手機睡眠時不計時間，可能遲了才刪，
     * 所以打開鍵盤和剪貼簿面板時也會刪一次。
     */
    private fun schedulePurge() {
        purgeJob?.cancel()
        val expiry = clipboardHistory.nextExpiry ?: return
        purgeJob = lifecycleScope.launch {
            delay((expiry - System.currentTimeMillis()).coerceAtLeast(MIN_PURGE_DELAY_MILLIS))
            purgeClipboard()
        }
    }

    /**
     * App 標記為敏感的內容，例如密碼管理器複製的密碼。鍵是 ClipDescription.EXTRA_IS_SENSITIVE（Android 13），
     * 之前的版本也有 App 用同一個鍵。
     */
    private fun ClipDescription.isSensitive(): Boolean = extras?.getBoolean(EXTRA_IS_SENSITIVE) == true

    /** 在背景寫檔；連續改動時只寫最新的一份。記錄錯誤時不會帶有檔案內容。 */
    private inner class LatestWriter<T : Any>(private val name: String, private val write: (T) -> Unit) {
        private val lock = Mutex()
        private val pending = AtomicReference<T?>(null)

        fun save(value: T) {
            pending.set(value)
            lifecycleScope.launch(Dispatchers.IO) {
                lock.withLock {
                    val latest = pending.getAndSet(null) ?: return@withLock
                    runCatching { write(latest) }.onFailure { Log.e(TAG, "無法儲存$name", it) }
                }
            }
        }
    }

    private companion object {
        const val TAG = "GInput"
        const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
        const val MIN_PURGE_DELAY_MILLIS = 1_000L
    }
}
