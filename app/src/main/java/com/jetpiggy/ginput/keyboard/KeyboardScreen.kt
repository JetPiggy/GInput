package com.jetpiggy.ginput.keyboard

import android.content.res.Configuration
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetpiggy.ginput.R
import com.jetpiggy.ginput.cloudtext.CloudTextState
import com.jetpiggy.ginput.cloudtext.CloudTool
import com.jetpiggy.ginput.emoji.EmojiCategory
import com.jetpiggy.ginput.settings.KeyLabels
import com.jetpiggy.ginput.settings.KeyboardSettings
import com.jetpiggy.ginput.typing.FieldKind
import com.jetpiggy.ginput.typing.KeyboardState
import com.jetpiggy.ginput.typing.Punctuation
import com.jetpiggy.ginput.typing.PunctuationKey
import com.jetpiggy.ginput.typing.ShiftState
import com.jetpiggy.ginput.usagestats.UsageEvent

private enum class Page { Letters, Symbols, MoreSymbols, Emoji, Clipboard, Translate, Rewrite }

/** 123 頁的兩組符號。以半形寫的 ! ? : ; ( ) ~ 按智能全形標點出全形或半形，見 PunctuationKey。 */
private val CommonSymbols = listOf(
    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
    listOf("@", "#", "\$", "%", "&", "-", "+", "(", ")"),
    listOf("*", "\"", "/", ":", "^", "!", "?"),
)

private val MoreSymbols = listOf(
    listOf("、", "「", "」", "『", "』", "《", "》", "……", "——", "·"),
    listOf("'", "\\", "_", "=", "[", "]", "{", "}", "<", ">"),
    listOf("~", ";", "|", "`", "°", "€", "£"),
)

@Composable
fun KeyboardScreen(
    state: KeyboardState,
    settings: KeyboardSettings,
    field: FieldKind,
    emojiCategories: List<EmojiCategory>,
    recentEmoji: List<String>,
    clipboard: ClipboardContent,
    onKey: (KeyAction) -> Unit,
    /** 導航列在鍵盤視窗裏佔的位置，鍵盤要讓出來。 */
    navigationBar: NavigationBarSpace,
    /** 工具列最右邊的語音輸入按鈕。 */
    voiceInput: VoiceInputKey = VoiceInputKey.Hidden,
    /** 翻譯面板和改寫面板的內容。 */
    cloudText: CloudTextState = CloudTextState(),
    /** 只有鍵盤自己知道的動作（打開候選字格、標點選單、游標滑動），給使用統計。 */
    onUsage: (UsageEvent) -> Unit = {},
    /** 更新了 GInput 之後，還未按「知道了」的新版簡介。 */
    whatsNew: Boolean = false,
    /** 有未在設定頁看過的新版本或公告：工具列的「設定」加一個小點。 */
    settingsBadge: Boolean = false,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val base = if (landscape) LandscapeDimensions else PortraitDimensions
    val context = LocalContext.current
    val feedback = remember(settings.keySound) {
        KeyFeedback(if (settings.keySound) context.getSystemService(AudioManager::class.java) else null)
    }
    val previews = remember(settings.keyPreview) { if (settings.keyPreview) KeyPreviewState() else null }
    CompositionLocalProvider(
        LocalKeyboardDimensions provides base.withHeight(settings.height.scale),
        LocalKeyFeedback provides feedback,
        LocalKeyPreview provides previews,
    ) {
        KeyboardContent(
            state, settings, field, emojiCategories, recentEmoji, clipboard, onKey, navigationBar, voiceInput, cloudText,
            onUsage, whatsNew, settingsBadge,
        )
    }
}

@Composable
private fun KeyboardContent(
    state: KeyboardState,
    settings: KeyboardSettings,
    field: FieldKind,
    emojiCategories: List<EmojiCategory>,
    recentEmoji: List<String>,
    clipboard: ClipboardContent,
    onKey: (KeyAction) -> Unit,
    navigationBar: NavigationBarSpace,
    voiceInput: VoiceInputKey,
    cloudText: CloudTextState,
    onUsage: (UsageEvent) -> Unit,
    whatsNew: Boolean,
    settingsBadge: Boolean,
) {
    val colors = keyboardColors(settings.theme)
    val previews = LocalKeyPreview.current
    // 換了文字框就回到字母頁。
    var page by remember(field) { mutableStateOf(Page.Letters) }
    var menu by remember(field) { mutableStateOf<BarMenu?>(null) }
    // 組字一變（包括選了字），候選字格和長按後的確認就收起。
    var gridOpen by remember(state.composition) { mutableStateOf(false) }
    var prompt by remember(state.composition) { mutableStateOf<BarPrompt?>(null) }
    // 按任何鍵都會關上選單和確認。
    val handle: (KeyAction) -> Unit = { action ->
        menu = null
        prompt = null
        // 改字換了字，組字不變，候選字格要另外收起。
        if (action is KeyAction.FixCharacter) gridOpen = false
        onKey(action)
    }
    val currentHandle by rememberUpdatedState(handle)
    val currentOnUsage by rememberUpdatedState(onUsage)
    val slide = remember {
        CursorSlide(
            onMove = { steps, select -> currentHandle(KeyAction.MoveCursor(steps, select)) },
            onStart = { currentOnUsage(UsageEvent.CursorSwipe) },
        )
    }
    // 按鍵區（不計候選列）的高度。候選字格、各個面板和 123 頁用同一高度，切換時鍵盤不會跳動。
    val dimensions = LocalKeyboardDimensions.current
    val keyArea = dimensions.rowHeight * 4 + if (settings.numberRow) dimensions.numberRowHeight else 0.dp
    var origin by remember { mutableStateOf(Offset.Zero) }
    var width by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .navigationBarSpace(navigationBar)
            .onGloballyPositioned {
                origin = it.positionInRoot()
                width = it.size.width.toFloat()
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = KeyboardPaddingX, vertical = KeyboardPaddingY),
        ) {
            if (field.usesNumberPad) {
                TypingKeys(slide) { NumberPad(field, colors, handle) }
            } else {
                CandidateBar(
                    state = state,
                    menu = menu,
                    prompt = prompt,
                    gridOpen = gridOpen,
                    voiceInput = voiceInput,
                    settingsBadge = settingsBadge,
                    cloudTools = field != FieldKind.Password,
                    radicals = settings.radicals,
                    colors = colors,
                    onKey = handle,
                    onCloseMenu = { menu = null },
                    onPrompt = { prompt = it },
                    onClosePrompt = { prompt = null },
                    onToggleGrid = {
                        if (!gridOpen) onUsage(UsageEvent.CandidateGrid)
                        gridOpen = !gridOpen
                    },
                    onOpenClipboard = {
                        handle(KeyAction.OpenClipboard)
                        page = Page.Clipboard
                    },
                    onOpenCloudTool = { tool ->
                        handle(KeyAction.OpenCloudTool(tool))
                        page = if (tool == CloudTool.Translation) Page.Translate else Page.Rewrite
                    },
                )
                when {
                    // 密碼欄位不顯示，等下次在一般文字框打開鍵盤。
                    whatsNew && field != FieldKind.Password ->
                        WhatsNewPanel(keyArea, colors, onDismiss = { handle(KeyAction.DismissWhatsNew) })
                    gridOpen && state.candidates.isNotEmpty() ->
                        CandidateGrid(state, keyArea, colors, handle, onAskForget = { prompt = BarPrompt.Forget(it) })
                    page == Page.Emoji -> EmojiPanel(
                        categories = emojiCategories,
                        recents = recentEmoji,
                        keyArea = keyArea,
                        colors = colors,
                        onKey = handle,
                        onShowVariants = { variants -> menu = BarMenu(variants) { KeyAction.CommitEmoji(it) } },
                        onBackToLetters = { page = Page.Letters },
                    )
                    page == Page.Clipboard -> ClipboardPanel(
                        content = clipboard,
                        keyArea = keyArea,
                        colors = colors,
                        onKey = { action ->
                            handle(action)
                            // 寫入一項或貼上之後回到字母頁。
                            if (action is KeyAction.WriteClipboardItem || action is KeyAction.WritePhrase || action == KeyAction.Paste) {
                                page = Page.Letters
                            }
                        },
                        onLongPress = { tab, text ->
                            prompt = if (tab == ClipboardTab.History) BarPrompt.ClipboardItem(text) else BarPrompt.Phrase(text)
                        },
                        // 長按後的確認只關於面板裏的項目，離開面板就收起。
                        onBackToLetters = {
                            prompt = null
                            page = Page.Letters
                        },
                    )
                    // 兩個面板共用同一個位置，在兩者之間切換時面板不會重新建立。
                    page == Page.Translate || page == Page.Rewrite -> CloudTextPanel(
                        tool = if (page == Page.Translate) CloudTool.Translation else CloudTool.Rewrite,
                        state = cloudText,
                        keyArea = keyArea,
                        colors = colors,
                        onKey = handle,
                        onApply = {
                            handle(KeyAction.ApplyTextResult)
                            page = Page.Letters
                        },
                        onBackToLetters = { page = Page.Letters },
                    )
                    else -> TypingKeys(slide) {
                        when (page) {
                            Page.Symbols -> FillRows(keyArea) {
                                SymbolRows(
                                    rows = CommonSymbols,
                                    state = state,
                                    switchLabel = stringResource(R.string.key_more),
                                    switchDescription = stringResource(R.string.key_more_symbols),
                                    colors = colors,
                                    onKey = handle,
                                    onSwitch = { page = Page.MoreSymbols },
                                )
                            }
                            Page.MoreSymbols -> FillRows(keyArea) {
                                SymbolRows(
                                    rows = MoreSymbols,
                                    state = state,
                                    switchLabel = stringResource(R.string.key_common),
                                    switchDescription = stringResource(R.string.key_common_symbols),
                                    colors = colors,
                                    onKey = handle,
                                    onSwitch = { page = Page.Symbols },
                                )
                            }
                            else -> LetterRows(state.shift, settings, colors, handle)
                        }
                        BottomRow(
                            field = field,
                            lettersShown = page == Page.Letters,
                            fullWidth = state.fullWidthPunctuation,
                            colors = colors,
                            onKey = handle,
                            onOpenEmoji = { page = Page.Emoji },
                            onSwitchPage = { page = if (page == Page.Letters) Page.Symbols else Page.Letters },
                            onOpenMenu = {
                                onUsage(UsageEvent.PunctuationMenu)
                                menu = BarMenu(Punctuation.more(state.fullWidthPunctuation)) { KeyAction.Text(it) }
                            },
                        )
                    }
                }
            }
        }
        if (previews != null) KeyPreviewBubble(previews, origin, width, colors)
    }
}

/**
 * 打字用的按鍵區：字母頁、123 頁、最底一行和數字鍵盤。這裏的按鍵可以游標滑動，
 * 一行裏不是按鍵的地方（a 行兩端的空位、鍵盤左右的邊距）都算同一行最近的鍵。
 */
@Composable
private fun TypingKeys(slide: CursorSlide, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalCursorSlide provides slide,
        LocalNearestKeyInRow provides true,
        content = content,
    )
}

/** 123 頁只有三行符號，加上最底一行；開了數字行時三行符號加高，令按鍵區的高度和字母頁一樣。 */
@Composable
private fun FillRows(keyArea: Dp, content: @Composable () -> Unit) {
    val dimensions = LocalKeyboardDimensions.current
    CompositionLocalProvider(
        LocalKeyboardDimensions provides dimensions.copy(rowHeight = (keyArea - dimensions.rowHeight) / 3f),
        content = content,
    )
}

@Composable
private fun LetterRows(
    shift: ShiftState,
    settings: KeyboardSettings,
    colors: KeyboardColors,
    onKey: (KeyAction) -> Unit,
) {
    if (settings.numberRow) NumberRow(colors, onKey)
    QWERTY_ROWS.forEachIndexed { index, letters ->
        KeyRow {
            when (index) {
                1 -> Spacer(Modifier.weight(0.5f))
                2 -> ShiftKey(shift, colors, onKey)
            }
            for (letter in letters) {
                // 開了數字行就不用長按字母打數字。
                val digit = if (settings.numberRow) null else topRowDigit(letter)
                LetterKey(
                    letter = letter,
                    uppercase = shift.uppercase,
                    labels = if (settings.radicals) settings.labels else KeyLabels.LettersOnly,
                    colors = colors,
                    onTap = { onKey(KeyAction.Letter(letter)) },
                    digit = digit,
                    digitHints = !settings.numberRow,
                    onLongPress = digit?.let { d -> { onKey(KeyAction.Text(d.toString())) } },
                )
            }
            when (index) {
                1 -> Spacer(Modifier.weight(0.5f))
                2 -> DeleteKey(colors, weight = 1.5f, onKey = onKey)
            }
        }
    }
}

/** 數字行：字母上面常駐的一行數字鍵，設定裏開啟；比其他行矮。 */
@Composable
private fun NumberRow(colors: KeyboardColors, onKey: (KeyAction) -> Unit) {
    val dimensions = LocalKeyboardDimensions.current
    KeyRow(height = dimensions.numberRowHeight) {
        for (digit in "1234567890") {
            val text = digit.toString()
            Key(colors, weight = 1f, description = text, preview = text, onTap = { onKey(KeyAction.Text(text)) }) {
                KeyLabel(text, colors, fontSize = dimensions.numberRowSize)
            }
        }
    }
}

/** Shift：未開是空心圖示；單次大寫是淺藍底、實心圖示；大寫鎖定是藍底、鎖定圖示。 */
@Composable
private fun RowScope.ShiftKey(
    shift: ShiftState,
    colors: KeyboardColors,
    onKey: (KeyAction) -> Unit,
) {
    Key(
        colors,
        weight = 1.5f,
        description = stringResource(R.string.key_shift),
        style = when (shift) {
            ShiftState.Off -> KeyStyle.Function
            ShiftState.Locked -> KeyStyle.Accent
            ShiftState.OneShot, ShiftState.Auto -> KeyStyle.AccentSoft
        },
        stateDescription = when (shift) {
            ShiftState.Off -> null
            ShiftState.Locked -> stringResource(R.string.state_caps_lock)
            ShiftState.OneShot, ShiftState.Auto -> stringResource(R.string.state_uppercase)
        },
        onTap = { onKey(KeyAction.Shift) },
        shift = true,
    ) {
        when (shift) {
            ShiftState.Off -> KeyIcon(R.drawable.ic_shift, colors.label)
            ShiftState.Locked -> KeyIcon(R.drawable.ic_shift_lock, colors.onAccent)
            ShiftState.OneShot, ShiftState.Auto -> KeyIcon(R.drawable.ic_shift_filled, colors.onAccentSoft)
        }
    }
}

/** 功能鍵上的文字，例如「123」「ABC」「全形」。 */
@Composable
private fun FunctionLabel(text: String, colors: KeyboardColors) {
    KeyLabel(text, colors, fontSize = 15.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun SymbolRows(
    rows: List<List<String>>,
    state: KeyboardState,
    switchLabel: String,
    switchDescription: String,
    colors: KeyboardColors,
    onKey: (KeyAction) -> Unit,
    onSwitch: () -> Unit,
) {
    rows.forEachIndexed { index, symbols ->
        val last = index == rows.lastIndex
        KeyRow {
            if (last) {
                Key(colors, weight = 1.5f, description = switchDescription, style = KeyStyle.Function, onTap = onSwitch) {
                    FunctionLabel(switchLabel, colors)
                }
            }
            val margin = (10f - symbols.size - if (last) 3f else 0f) / 2f
            if (margin > 0f) Spacer(Modifier.weight(margin))
            for (symbol in symbols) {
                // 智能全形標點的鍵：標示和打出的都跟前一個字。
                val smart = PunctuationKey.ofHalfWidth(symbol)
                val shown = smart?.let(state::punctuation) ?: symbol
                val action = smart?.let { KeyAction.SmartPunctuation(it) } ?: KeyAction.Text(symbol)
                Key(colors, weight = 1f, description = shown, preview = shown, onTap = { onKey(action) }) {
                    KeyLabel(shown, colors, fontSize = if (shown.length > 1) 14.sp else 20.sp)
                }
            }
            if (margin > 0f) Spacer(Modifier.weight(margin))
            if (last) DeleteKey(colors, weight = 1.5f, onKey = onKey)
        }
    }
}

@Composable
private fun BottomRow(
    field: FieldKind,
    lettersShown: Boolean,
    fullWidth: Boolean,
    colors: KeyboardColors,
    onKey: (KeyAction) -> Unit,
    onOpenEmoji: () -> Unit,
    onSwitchPage: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    KeyRow(bottom = true) {
        Key(
            colors,
            weight = 1.5f,
            description = stringResource(if (lettersShown) R.string.key_symbols else R.string.key_letters),
            style = KeyStyle.Function,
            onTap = onSwitchPage,
        ) {
            FunctionLabel(if (lettersShown) "123" else "ABC", colors)
        }
        Key(
            colors,
            weight = 1f,
            description = stringResource(R.string.key_emoji),
            style = KeyStyle.Function,
            onTap = onOpenEmoji,
            onLongPress = { onKey(KeyAction.ShowInputMethodPicker) },
            longPressLabel = stringResource(R.string.key_switch_keyboard),
        ) {
            KeyIcon(R.drawable.ic_mood, colors.label)
        }
        // 電郵欄位：@ 在空白鍵左邊，只有「.」，.com 在打「.」之後的相關符號。網址欄位用一般的「，」「。」鍵（出半形）。
        if (field == FieldKind.Email) {
            Key(colors, weight = 1f, description = "@", preview = "@", onTap = { onKey(KeyAction.Text("@")) }) {
                KeyLabel("@", colors)
            }
            SpaceKey(colors, weight = 4f, onKey = onKey)
            Key(colors, weight = 1f, description = ".", preview = ".", onTap = { onKey(KeyAction.Text(".")) }) {
                KeyLabel(".", colors)
            }
        } else {
            SpaceKey(colors, weight = 4f, onKey = onKey)
            val comma = if (fullWidth) "，" else ","
            val period = if (fullWidth) "。" else "."
            val more = stringResource(R.string.key_more_punctuation)
            Key(
                colors,
                weight = 1f,
                description = stringResource(R.string.key_comma),
                preview = comma,
                onTap = { onKey(KeyAction.SmartPunctuation(PunctuationKey.Comma)) },
                onLongPress = onOpenMenu,
                longPressLabel = more,
            ) {
                KeyLabel(comma, colors)
            }
            Key(
                colors,
                weight = 1f,
                description = stringResource(R.string.key_period),
                preview = period,
                onTap = { onKey(KeyAction.SmartPunctuation(PunctuationKey.Period)) },
                onLongPress = onOpenMenu,
                longPressLabel = more,
            ) {
                KeyLabel(period, colors)
            }
        }
        Key(
            colors,
            weight = 1.5f,
            description = stringResource(R.string.key_enter),
            style = KeyStyle.Function,
            sound = KeySound.Enter,
            onTap = { onKey(KeyAction.Enter) },
        ) {
            KeyIcon(R.drawable.ic_keyboard_return, colors.label)
        }
    }
}
