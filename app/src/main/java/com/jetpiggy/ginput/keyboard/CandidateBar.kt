package com.jetpiggy.ginput.keyboard

import android.view.View
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetpiggy.ginput.R
import com.jetpiggy.ginput.cloudtext.CloudTool
import com.jetpiggy.ginput.engine.SentencePart
import com.jetpiggy.ginput.typing.KeyboardState

/** 候選字超過這個數目，候選列最右邊才出 ▼ 鍵。 */
internal const val CANDIDATE_GRID_THRESHOLD = 6

/** 在候選列向下掃超過這個距離，就收起鍵盤。 */
private val HideSwipeDistance = 40.dp

/** 暫時取代候選列的選單：長按「，」「。」的標點選單，或長按 Emoji 的膚色選單。 */
internal data class BarMenu(val items: List<String>, val action: (String) -> KeyAction)

/** 暫時取代候選列的確認：長按記憶字、剪貼簿歷史或常用短語之後，或按了語音輸入但手機未啟用語音輸入法時出現。 */
internal sealed interface BarPrompt {
    /** 忘記記憶字。 */
    data class Forget(val text: String) : BarPrompt

    /** 剪貼簿歷史的一項：存成常用短語或刪除。 */
    data class ClipboardItem(val text: String) : BarPrompt

    /** 刪除常用短語。 */
    data class Phrase(val text: String) : BarPrompt

    /** 手機未啟用語音輸入法：提示到系統設定啟用。 */
    data object NoVoiceInput : BarPrompt
}

/**
 * 候選列。依次序顯示：長按後的確認、選單、打開候選字格時的組字和字根、改字時可以換上的字、組字的候選字和英文建議、
 * 相關符號、聯想（Emoji 關鍵字的 Emoji 固定在最右）；都沒有時顯示工具列。在候選列向下掃就收起鍵盤。
 * 長按整句候選再左右滑動揀字（改字）時，整句逐個字鋪在上面。
 * 手機橫向版面（digits）空閒時改為顯示數字 1–0，按了工具鍵（toolsOpen）才顯示工具列，排在選單之後、組字之前。
 */
@Composable
internal fun CandidateBar(
    state: KeyboardState,
    menu: BarMenu?,
    prompt: BarPrompt?,
    gridOpen: Boolean,
    /** 手機橫向版面按了工具鍵：暫時顯示工具列，組字不變。 */
    toolsOpen: Boolean,
    /** 手機橫向版面：空閒時顯示數字，取代工具列；語音輸入在側欄。 */
    digits: Boolean,
    voiceInput: VoiceInputKey,
    /** 工具列的「設定」加一個小點：有未看過的新版本或公告。 */
    settingsBadge: Boolean,
    /** 工具列有沒有「翻譯」和「改寫」；密碼欄位沒有。 */
    cloudTools: Boolean,
    /** 打開候選字格時，組字後面顯示倉頡字根；只啟用粵拼時不顯示。 */
    radicals: Boolean,
    colors: KeyboardColors,
    onKey: (KeyAction) -> Unit,
    onCloseMenu: () -> Unit,
    onPrompt: (BarPrompt) -> Unit,
    onClosePrompt: () -> Unit,
    onToggleGrid: () -> Unit,
    onOpenClipboard: () -> Unit,
    onOpenCloudTool: (CloudTool) -> Unit,
) {
    val hidePx = with(LocalDensity.current) { HideSwipeDistance.toPx() }
    val currentOnKey by rememberUpdatedState(onKey)
    val view = LocalView.current
    val feedback by rememberUpdatedState(LocalKeyFeedback.current)
    // 改字：長按整句候選後揀中的是第幾個字（手指未放）；開了 TalkBack 的話，改為逐個字列出讓使用者按。
    var choosing by remember(state.composition) { mutableStateOf<Int?>(null) }
    var choosingByTap by remember(state) { mutableStateOf(false) }
    val fix = state.characterFix
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(LocalKeyboardDimensions.current.barHeight),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var travelled = 0f
                    detectVerticalDragGestures(
                        onDragStart = { travelled = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            travelled += dragAmount
                            change.consume()
                        },
                        onDragEnd = { if (travelled > hidePx) currentOnKey(KeyAction.HideKeyboard) },
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                prompt != null -> {
                    Text(
                        text = when (prompt) {
                            is BarPrompt.Forget -> stringResource(R.string.forget_prompt, prompt.text)
                            // 只顯示一行，讓使用者認得是哪一項。
                            is BarPrompt.ClipboardItem -> prompt.text.replace(Whitespace, " ").trim()
                            is BarPrompt.Phrase -> stringResource(R.string.phrase_delete_prompt)
                            BarPrompt.NoVoiceInput -> stringResource(R.string.voice_input_not_enabled)
                        },
                        color = colors.label,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp, end = 4.dp),
                    )
                    when (prompt) {
                        is BarPrompt.Forget ->
                            BarItem(stringResource(R.string.forget_button), colors, marked = true, english = true) {
                                onKey(KeyAction.ForgetCandidate(prompt.text))
                            }
                        is BarPrompt.ClipboardItem -> {
                            BarItem(stringResource(R.string.clipboard_save_phrase), colors, marked = true, english = true) {
                                onKey(KeyAction.SavePhrase(prompt.text))
                            }
                            BarItem(stringResource(R.string.clipboard_delete), colors, english = true) {
                                onKey(KeyAction.DeleteClipboardItem(prompt.text))
                            }
                        }
                        is BarPrompt.Phrase ->
                            BarItem(stringResource(R.string.clipboard_delete), colors, marked = true, english = true) {
                                onKey(KeyAction.DeletePhrase(prompt.text))
                            }
                        BarPrompt.NoVoiceInput ->
                            BarItem(stringResource(R.string.voice_input_settings), colors, marked = true, english = true) {
                                onKey(KeyAction.VoiceInputSettings)
                            }
                    }
                    CloseButton(colors, onClosePrompt)
                }
                menu != null -> {
                    ItemsRow(resetKey = menu.items) {
                        items(menu.items) { item ->
                            BarItem(item, colors) { onKey(menu.action(item)) }
                        }
                    }
                    CloseButton(colors, onCloseMenu)
                }
                toolsOpen -> Toolbar(colors, cloudTools, settingsBadge, onKey, onOpenClipboard, onOpenCloudTool)
                state.composition.isNotEmpty() && gridOpen -> {
                    val radicalText = if (radicals) "  " + state.composition.map(::cangjieRadical).joinToString("") else ""
                    Text(
                        text = state.composition + radicalText,
                        color = colors.secondaryLabel,
                        fontSize = 17.sp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    )
                    BarButton(R.drawable.ic_keyboard_arrow_up, stringResource(R.string.bar_hide_all), colors, onTap = onToggleGrid)
                }
                // 改字：揀中了整句候選的一個字，列出可以換上的字，原本的字加框。
                fix != null -> {
                    ItemsRow(resetKey = fix) {
                        items(fix.choices) { choice ->
                            BarItem(
                                text = choice.text,
                                colors = colors,
                                remembered = choice.remembered,
                                framed = choice.text == fix.part.text,
                            ) {
                                onKey(KeyAction.FixCharacter(choice.text))
                            }
                        }
                    }
                    if (fix.choices.size > CANDIDATE_GRID_THRESHOLD) {
                        BarButton(R.drawable.ic_keyboard_arrow_down, stringResource(R.string.bar_show_all), colors, onTap = onToggleGrid)
                    }
                    CloseButton(colors) { onKey(KeyAction.DismissFollowUps) }
                }
                state.composition.isNotEmpty() -> {
                    val forgetLabel = stringResource(R.string.forget_button)
                    val fixLabel = stringResource(R.string.fix_character)
                    ItemsRow(resetKey = state.composition) {
                        itemsIndexed(state.candidates) { index, candidate ->
                            val parts = candidate.parts
                            BarItem(
                                text = candidate.text,
                                colors = colors,
                                marked = state.markTopCandidate && index == 0,
                                remembered = candidate.remembered,
                                onLongPress = when {
                                    candidate.remembered -> ({ onPrompt(BarPrompt.Forget(candidate.text)) })
                                    // 整句候選：TalkBack 的長按改為逐個字列出；用手指的話由 onLongPressSlide 跟着手指揀字。
                                    parts.isNotEmpty() -> ({ choosingByTap = true })
                                    else -> null
                                },
                                onLongPressSlide = if (parts.isEmpty()) {
                                    null
                                } else {
                                    { down ->
                                        followFixSlide(
                                            down = down,
                                            count = parts.size,
                                            view = view,
                                            feedback = feedback,
                                            onMove = { choosing = it },
                                            onRelease = {
                                                choosing = null
                                                onKey(KeyAction.StartCharacterFix(it))
                                            },
                                            onCancel = { choosing = null },
                                        )
                                    }
                                },
                                longPressLabel = if (parts.isNotEmpty()) fixLabel else forgetLabel,
                            ) {
                                onKey(KeyAction.PickCandidate(candidate.text))
                            }
                        }
                        // 英漢詞典的譯詞排在候選字和英文建議之間，三組之間都有分隔。
                        if (state.translations.isNotEmpty()) {
                            if (state.candidates.isNotEmpty()) item { BarDivider(colors) }
                            items(state.translations) { text ->
                                BarItem(text, colors) { onKey(KeyAction.PickTranslation(text)) }
                            }
                        }
                        val chinese = state.candidates.isNotEmpty() || state.translations.isNotEmpty()
                        if (chinese && state.englishSuggestions.isNotEmpty()) {
                            item { BarDivider(colors) }
                        }
                        items(state.englishSuggestions) { word ->
                            BarItem(word, colors, english = true) { onKey(KeyAction.PickEnglish(word)) }
                        }
                    }
                    if (state.candidates.size > CANDIDATE_GRID_THRESHOLD) {
                        BarButton(R.drawable.ic_keyboard_arrow_down, stringResource(R.string.bar_show_all), colors, onTap = onToggleGrid)
                    }
                }
                state.relatedSymbols.isNotEmpty() -> {
                    // 換了符號，列表不變，也不重新捲動，方便再換。
                    ItemsRow(resetKey = state.relatedSymbols) {
                        items(state.relatedSymbols) { symbol ->
                            BarItem(symbol, colors, marked = symbol == state.writtenSymbol) {
                                onKey(KeyAction.PickRelatedSymbol(symbol))
                            }
                        }
                    }
                    CloseButton(colors) { onKey(KeyAction.DismissFollowUps) }
                }
                state.associations.isNotEmpty() || state.keywordEmoji.isNotEmpty() -> {
                    ItemsRow(resetKey = state.associations) {
                        items(state.associations) { text ->
                            BarItem(text, colors) { onKey(KeyAction.PickAssociation(text)) }
                        }
                    }
                    // Emoji 關鍵字的 Emoji 固定在最右（✕ 前面），聯想再多也不會被擠走。
                    if (state.keywordEmoji.isNotEmpty()) {
                        if (state.associations.isNotEmpty()) BarDivider(colors)
                        state.keywordEmoji.forEach { emoji ->
                            BarItem(emoji, colors) { onKey(KeyAction.PickKeywordEmoji(emoji)) }
                        }
                    }
                    CloseButton(colors) { onKey(KeyAction.DismissFollowUps) }
                }
                digits -> BarDigits(colors, onKey)
                else -> {
                    Toolbar(colors, cloudTools, settingsBadge, onKey, onOpenClipboard, onOpenCloudTool)
                    // 語音輸入在最右邊，和 ✕ ▼ 一樣只有圖示，不擠走其他按鈕的文字。
                    if (voiceInput != VoiceInputKey.Hidden) {
                        BarButton(R.drawable.ic_mic, stringResource(R.string.toolbar_voice_input), colors, iconSize = 22.dp) {
                            if (voiceInput == VoiceInputKey.Available) onKey(KeyAction.VoiceInput) else onPrompt(BarPrompt.NoVoiceInput)
                        }
                    }
                }
            }
        }
        // 揀字時鋪在候選列上面。下面的候選字要留着：長按的手指仍然由整句候選跟着。
        val parts = state.candidates.firstOrNull { it.sentence }?.parts.orEmpty()
        if (parts.isNotEmpty() && (choosing != null || choosingByTap)) {
            SentenceParts(
                parts = parts,
                composition = state.composition,
                chosen = choosing,
                colors = colors,
                onPick = if (choosingByTap) {
                    { index ->
                        choosingByTap = false
                        onKey(KeyAction.StartCharacterFix(index))
                    }
                } else {
                    null
                },
                onClose = { choosingByTap = false },
            )
        }
    }
}

/** 改字時，手指每左右移過這個距離，揀中的字就移一個。比游標滑動的一格大：一句只有幾個字，大一點較易揀得準。 */
private val FixStep = 24.dp

/**
 * 長按整句候選之後跟着手指揀字，直到放手：先揀中手指下面的字，之後每左右移過 FixStep 移一個字，到了頭尾就停，
 * 每移一個字輕輕震一下。只計左右距離；這隻手指的事件都會用掉，所以移到按鍵上也不會按到按鍵，候選列也不會捲動或收起鍵盤。
 */
private suspend fun AwaitPointerEventScope.followFixSlide(
    down: PointerInputChange,
    count: Int,
    view: View,
    feedback: KeyFeedback,
    onMove: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    // 整句候選左右各有 CandidatePadding 的空位，每個字的闊度大致相同。
    val padding = CandidatePadding.toPx()
    val width = ((size.width - padding * 2) / count).coerceAtLeast(1f)
    var index = ((down.position.x - padding) / width).toInt().coerceIn(0, count - 1)
    val stepPx = FixStep.toPx()
    onMove(index)
    feedback.longPress(view)
    var pending = 0f
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
        if (change == null) {
            onCancel()
            return
        }
        if (!change.pressed) {
            change.consume()
            onRelease(index)
            return
        }
        pending += change.positionChange().x
        change.consume()
        val steps = (pending / stepPx).toInt()
        if (steps != 0) {
            pending -= steps * stepPx
            val next = (index + steps).coerceIn(0, count - 1)
            if (next != index) {
                index = next
                onMove(index)
                feedback.step(view)
            }
        }
    }
}

/**
 * 改字揀字時鋪在候選列上面：只留下整句，字距拉闊，揀中的字（chosen）加底色，下面寫它用了的字母。
 * onPick 不是 null 的話（開了 TalkBack），每個字都寫出字母、都可以按，最右邊另有 ✕。
 */
@Composable
private fun SentenceParts(
    parts: List<SentencePart>,
    composition: String,
    chosen: Int?,
    colors: KeyboardColors,
    onPick: ((Int) -> Unit)?,
    onClose: () -> Unit,
) {
    val dimensions = LocalKeyboardDimensions.current
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            parts.forEachIndexed { index, part ->
                val letters = composition.substring(part.start, part.end)
                val selected = index == chosen
                // 滑動揀字時只有揀中的字寫出字母；其餘的照樣留位，字不會上下跳。
                val lettersColor = when {
                    selected -> colors.onAccentSoft
                    chosen == null -> colors.secondaryLabel
                    else -> Color.Transparent
                }
                // 中文字型的行高比字級高，上下疊起來會超出候選列；所以字母貼着底，字向上移開讓位。
                val content: @Composable BoxScope.() -> Unit = {
                    Text(
                        text = part.text,
                        color = if (selected) colors.onAccentSoft else colors.label,
                        style = tightText(dimensions.candidateSize),
                        modifier = Modifier.offset(y = -PartLettersRoom / 2),
                    )
                    Text(
                        text = letters,
                        color = lettersColor,
                        style = tightText(PartLettersSize),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 3.dp),
                    )
                }
                val modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 2.dp)
                if (onPick != null) {
                    PressableBox(
                        modifier = modifier,
                        color = Color.Transparent,
                        pressedColor = colors.pressedKey,
                        description = stringResource(R.string.fix_part, part.text, letters),
                        onTap = { onPick(index) },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                            content = content,
                        )
                    }
                } else {
                    Box(
                        modifier = modifier
                            .clip(KeyShape)
                            .background(if (selected) colors.accentSoft else Color.Transparent)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                        content = content,
                    )
                }
            }
        }
        if (onPick != null) CloseButton(colors, onClose)
    }
}

/** 改字揀字時，每個字下面寫它用了的字母的字級，和字向上移開讓出的高度。 */
private val PartLettersSize = 10.sp
private val PartLettersRoom = 12.dp

private val Whitespace = Regex("""\s+""")

/** badge：圖示右上角加一個小點；description 是 TalkBack 讀出的文字。 */
internal class ToolbarItem(
    @DrawableRes val icon: Int,
    val label: String,
    val badge: Boolean = false,
    val description: String = label,
    val onTap: () -> Unit,
)

private val ToolbarPadding = 4.dp
private val ToolbarIconSize = 18.dp
private val ToolbarGap = 4.dp
private val ToolbarTextSize = 13.sp
private val BadgeSize = 7.dp

/**
 * 候選列空閒時的工具列：剪貼簿、翻譯、改寫和設定（密碼欄位沒有翻譯和改寫）。圖示和文字並排，
 * 按各自需要的闊度分配，「剪貼簿」這類較長的文字分得較闊。全部文字放不下（例如英文較長），
 * 就全部只顯示圖示、平分闊度；TalkBack 仍然讀出文字。
 */
@Composable
private fun RowScope.Toolbar(
    colors: KeyboardColors,
    cloudTools: Boolean,
    settingsBadge: Boolean,
    onKey: (KeyAction) -> Unit,
    onOpenClipboard: () -> Unit,
    onOpenCloudTool: (CloudTool) -> Unit,
) {
    val settingsLabel = stringResource(R.string.toolbar_settings)
    val items = listOfNotNull(
        ToolbarItem(R.drawable.ic_assignment, stringResource(R.string.toolbar_clipboard), onTap = onOpenClipboard),
        if (cloudTools) {
            ToolbarItem(R.drawable.ic_translate, stringResource(R.string.toolbar_translate)) { onOpenCloudTool(CloudTool.Translation) }
        } else {
            null
        },
        if (cloudTools) {
            ToolbarItem(R.drawable.ic_edit_note, stringResource(R.string.toolbar_rewrite)) { onOpenCloudTool(CloudTool.Rewrite) }
        } else {
            null
        },
        ToolbarItem(
            icon = R.drawable.ic_settings,
            label = settingsLabel,
            badge = settingsBadge,
            description = if (settingsBadge) stringResource(R.string.toolbar_settings_badge) else settingsLabel,
        ) { onKey(KeyAction.OpenSettings) },
    )
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
    ) {
        // 每個按鈕連文字需要的闊度。
        val needed = items.map { item ->
            val label = measurer.measure(item.label, TextStyle(fontSize = ToolbarTextSize)).size.width
            with(density) { label.toDp() } + ToolbarPadding * 2 + ToolbarIconSize + ToolbarGap
        }
        val showLabels = needed.fold(0.dp) { total, width -> total + width } <= maxWidth
        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { index, item ->
                ToolbarButton(item, showLabels, colors, weight = if (showLabels) needed[index].value else 1f)
            }
        }
    }
}

/** 手機橫向版面候選列空閒時的數字 1–0，取代工具列；樣子和字母鍵一樣。 */
@Composable
private fun RowScope.BarDigits(colors: KeyboardColors, onKey: (KeyAction) -> Unit) {
    val dimensions = LocalKeyboardDimensions.current
    Box(Modifier.weight(1f)) {
        KeyRow(height = dimensions.barHeight) {
            for (digit in "1234567890") {
                val text = digit.toString()
                Key(colors, weight = 1f, description = text, preview = text, onTap = { onKey(KeyAction.Text(text)) }) {
                    KeyLabel(text, colors, fontSize = dimensions.letterSize)
                }
            }
        }
    }
}

/** 圖示右上角的小點：有未看過的新版本或公告。工具列的「設定」和手機橫向版面的工具鍵用。 */
@Composable
internal fun BoxScope.IconBadge(colors: KeyboardColors) {
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .offset(x = 2.dp, y = (-2).dp)
            .size(BadgeSize)
            .background(colors.accent, CircleShape),
    )
}

/** 工具列和剪貼簿面板最頂一行的按鈕：圖示，加上放得下時的文字。 */
@Composable
internal fun RowScope.ToolbarButton(item: ToolbarItem, showLabel: Boolean, colors: KeyboardColors, weight: Float) {
    PressableBox(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .padding(ToolbarPadding),
        color = Color.Transparent,
        pressedColor = colors.pressedKey,
        description = item.description,
        onTap = item.onTap,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ToolbarGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                KeyIcon(item.icon, colors.secondaryLabel, size = ToolbarIconSize)
                if (item.badge) IconBadge(colors)
            }
            if (showLabel) {
                Text(
                    text = item.label,
                    color = colors.secondaryLabel,
                    fontSize = ToolbarTextSize,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** 列表還可以向右捲動時，右邊這個闊度淡出。 */
private val FadeWidth = 24.dp

/** 內容一換就由頭顯示，不保留上一次捲動的位置。還可以向右捲動時右邊淡出，表示後面還有。 */
@Composable
private fun RowScope.ItemsRow(resetKey: Any, content: LazyListScope.() -> Unit) {
    key(resetKey) {
        val listState = rememberLazyListState()
        LazyRow(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    if (listState.canScrollForward) {
                        val fade = FadeWidth.toPx()
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color.Black, Color.Transparent),
                                startX = size.width - fade,
                                endX = size.width,
                            ),
                            topLeft = Offset(size.width - fade, 0f),
                            size = Size(fade, size.height),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** 候選字的底色：記憶字用淺綠底，「空白鍵選中文」模式的首選字用突出一級的底；都不是就用 plain。 */
internal fun KeyboardColors.candidateBackground(remembered: Boolean, marked: Boolean, plain: Color): Color = when {
    remembered && marked -> rememberedChipMarked
    remembered -> rememberedChip
    marked -> functionKey
    else -> plain
}

/** 候選字左右的空位。 */
private val CandidatePadding = 12.dp

/**
 * 記憶字用淺綠底綠字；記憶字可以長按（選擇忘記它），整句候選也可以長按（改字）。framed：改字時原本的字，加框標示。
 * TalkBack 讀出字的本身，記憶字另外讀出「記憶字」，原本的字讀出「原本的字」。
 */
@Composable
private fun BarItem(
    text: String,
    colors: KeyboardColors,
    marked: Boolean = false,
    english: Boolean = false,
    remembered: Boolean = false,
    framed: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    onLongPressSlide: (suspend AwaitPointerEventScope.(down: PointerInputChange) -> Unit)? = null,
    longPressLabel: String? = null,
    onTap: () -> Unit,
) {
    val dimensions = LocalKeyboardDimensions.current
    val states = listOfNotNull(
        if (remembered) stringResource(R.string.candidate_remembered) else null,
        if (framed) stringResource(R.string.fix_current) else null,
    )
    PressableBox(
        modifier = Modifier
            .fillMaxHeight()
            // 記憶字的底色左右留空，連着幾個記憶字也分得開。
            .padding(horizontal = if (remembered || framed) 2.dp else 0.dp, vertical = 4.dp)
            .then(if (framed) Modifier.border(1.dp, colors.secondaryLabel, KeyShape) else Modifier),
        color = colors.candidateBackground(remembered, marked, plain = Color.Transparent),
        pressedColor = colors.pressedKey,
        description = text,
        onTap = onTap,
        onLongPress = onLongPress,
        onLongPressSlide = onLongPressSlide,
        longPressLabel = if (onLongPress != null) longPressLabel else null,
        stateDescription = states.joinToString(", ").ifEmpty { null },
    ) {
        Text(
            text = text,
            color = if (remembered) colors.remembered else colors.label,
            fontSize = if (english) dimensions.englishSize else dimensions.candidateSize,
            modifier = Modifier.padding(horizontal = CandidatePadding),
        )
    }
}

@Composable
private fun BarDivider(colors: KeyboardColors) {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(20.dp)
            .background(colors.divider),
    )
}

/** 候選列最右邊的按鈕（44 × 36dp）：✕ 收起、▼ 打開候選字格、▲ 收起候選字格。 */
@Composable
private fun BarButton(
    @DrawableRes icon: Int,
    description: String,
    colors: KeyboardColors,
    iconSize: Dp = 24.dp,
    onTap: () -> Unit,
) {
    PressableBox(
        modifier = Modifier
            .width(44.dp)
            .fillMaxHeight()
            .padding(vertical = 4.dp),
        color = Color.Transparent,
        pressedColor = colors.pressedKey,
        description = description,
        onTap = onTap,
    ) {
        KeyIcon(icon, colors.secondaryLabel, size = iconSize)
    }
}

/** 收起聯想、選單或確認的 ✕。 */
@Composable
private fun CloseButton(colors: KeyboardColors, onTap: () -> Unit) {
    BarButton(R.drawable.ic_close, stringResource(R.string.bar_close), colors, iconSize = 22.dp, onTap = onTap)
}
