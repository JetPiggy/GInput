package com.jetpiggy.ginput.keyboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetpiggy.ginput.R
import com.jetpiggy.ginput.handwriting.HandwritingModelStatus
import com.jetpiggy.ginput.handwriting.InkPoint
import com.jetpiggy.ginput.handwriting.InkStrokes
import com.jetpiggy.ginput.typing.KeyboardState
import com.jetpiggy.ginput.typing.PunctuationKey

/**
 * 鍵盤顯示手寫要用的狀態：有沒有同意說明、模型的狀態和下載了幾多百分比，以及目前筆跡的候選字，第一個是自動寫入的那個。
 * hidden 是鍵盤收起了幾多次：每次收起，手寫板的筆跡就丟掉。
 */
data class HandwritingState(
    /** 已經同意第一次使用時的說明（ADR 0015）；未同意，手寫板先顯示說明。 */
    val accepted: Boolean = false,
    val status: HandwritingModelStatus = HandwritingModelStatus.Unknown,
    val percent: Int = 0,
    val candidates: List<String> = emptyList(),
    val hidden: Int = 0,
)

/**
 * 手寫板上的筆跡，以及甚麼時候自動寫入第一個候選字：停筆超過 AUTO_COMMIT_MILLIS 再開始寫，新的一筆算下一個字。
 * 用 Compose 的狀態保存，畫面跟着筆跡更新。規則見 docs/spec/v1.md 的「手寫」。
 */
@Stable
internal class InkPad {
    /** 寫完了的筆畫。 */
    var strokes by mutableStateOf<List<List<InkPoint>>>(emptyList())
        private set

    /** 正在寫的一筆；手指未按下時是空的。 */
    var current by mutableStateOf<List<InkPoint>>(emptyList())
        private set

    private var lastUp = 0L

    val isEmpty: Boolean get() = strokes.isEmpty() && current.isEmpty()

    /** 在 time 開始寫新的一筆時，要不要先寫入第一個候選字（hasCandidates 是已經有辨識結果）。 */
    fun startsNewCharacter(time: Long, hasCandidates: Boolean): Boolean =
        strokes.isNotEmpty() && hasCandidates && time - lastUp > AUTO_COMMIT_MILLIS

    fun down(point: InkPoint) {
        current = listOf(point)
    }

    fun move(point: InkPoint) {
        if (current.isNotEmpty()) current = current + point
    }

    /** 放手：這一筆寫完了，傳回全部筆畫，交去辨識。 */
    fun up(time: Long): List<List<InkPoint>> {
        if (current.isNotEmpty()) strokes = strokes + listOf(current)
        current = emptyList()
        lastUp = time
        return strokes
    }

    fun clear() {
        strokes = emptyList()
        current = emptyList()
    }

    companion object {
        const val AUTO_COMMIT_MILLIS = 1_000L
    }
}

private val InkWidth = 4.dp

/**
 * 手寫板：取代按鍵區，高度相同（keyArea）。上面是寫字的地方，每寫完一筆就交 onInk 辨識，候選字在候選列；
 * 最底一行是返回鍵、「，」、空白、「。」、Delete 和 Enter。有筆跡時按這些鍵先寫入第一個候選字
 * （Delete 改為清走筆跡），見 docs/spec/v1.md 的「手寫」。模型未下載好時，寫字的地方顯示下載的狀態和百分比。
 * 第一次使用（未同意說明）時先顯示說明，按「開啟」後才初始化 ML Kit 和下載模型（ADR 0015）。
 */
@Composable
internal fun HandwritingPanel(
    pad: InkPad,
    handwriting: HandwritingState,
    state: KeyboardState,
    keyArea: Dp,
    colors: KeyboardColors,
    slide: CursorSlide,
    onKey: (KeyAction) -> Unit,
    /** 筆跡改變了，交去辨識；清走筆跡時是 InkStrokes.Empty。 */
    onInk: (InkStrokes) -> Unit,
    onBackToLetters: () -> Unit,
) {
    val dimensions = LocalKeyboardDimensions.current
    val currentCandidates by rememberUpdatedState(handwriting.candidates)
    val currentOnKey by rememberUpdatedState(onKey)
    val currentOnInk by rememberUpdatedState(onInk)
    var size by remember { mutableStateOf(IntSize.Zero) }

    if (!handwriting.accepted) {
        // 和翻譯、改寫的說明一樣：行距較密，放不下（例如橫向）就捲動。
        Text(
            text = stringResource(R.string.handwriting_notice),
            color = colors.label,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            modifier = Modifier
                .fillMaxWidth()
                .height(keyArea - dimensions.rowHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        KeyRow(bottom = true) {
            BackToLettersKey(colors, onBackToLetters)
            Spacer(Modifier.weight(5f))
            ActionKey(stringResource(R.string.handwriting_notice_confirm), enabled = true, colors) {
                onKey(KeyAction.AcceptHandwriting)
            }
        }
        return
    }

    /** 寫入第一個候選字並清走筆跡；沒有候選字就只清走筆跡。 */
    fun commitTop() {
        val top = currentCandidates.firstOrNull()
        pad.clear()
        currentOnInk(InkStrokes.Empty)
        if (top != null) currentOnKey(KeyAction.PickHandwriting(top))
    }

    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(keyArea - dimensions.rowHeight)
                .padding(horizontal = KeyPaddingX, vertical = KeyPaddingY)
                .keyShadow(colors.keyShadow)
                .clip(KeyShape)
                .background(colors.key),
            contentAlignment = Alignment.Center,
        ) {
            when (handwriting.status) {
                HandwritingModelStatus.Ready -> {
                    val description = stringResource(R.string.handwriting_pad)
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { size = it }
                            .semantics { contentDescription = description }
                            // 收起鍵盤或換文字框會換一個新的 pad，要用新的 pad 重新接收筆畫，否則筆畫寫進舊的 pad，畫面上看不到。
                            .pointerInput(pad) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    down.consume()
                                    val start = down.uptimeMillis
                                    if (pad.startsNewCharacter(start, currentCandidates.isNotEmpty())) commitTop()
                                    pad.down(InkPoint(down.position.x, down.position.y, start))
                                    var end = start
                                    while (true) {
                                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                        change.consume()
                                        end = change.uptimeMillis
                                        if (!change.pressed) break
                                        pad.move(InkPoint(change.position.x, change.position.y, change.uptimeMillis))
                                    }
                                    val strokes = pad.up(end)
                                    currentOnInk(InkStrokes(strokes, size.width.toFloat(), size.height.toFloat()))
                                }
                            },
                    ) {
                        val stroke = Stroke(width = InkWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        (pad.strokes + listOf(pad.current)).forEach { points ->
                            if (points.size == 1) {
                                drawCircle(colors.label, radius = stroke.width / 2, center = Offset(points[0].x, points[0].y))
                            } else if (points.size > 1) {
                                val path = Path().apply {
                                    moveTo(points[0].x, points[0].y)
                                    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                                }
                                drawPath(path, colors.label, style = stroke)
                            }
                        }
                    }
                    if (pad.isEmpty) PadMessage(stringResource(R.string.handwriting_hint), colors)
                }
                HandwritingModelStatus.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PadMessage(stringResource(R.string.handwriting_download_failed), colors)
                    PressableBox(
                        modifier = Modifier.padding(top = 8.dp),
                        color = colors.functionKey,
                        pressedColor = colors.pressedKey,
                        description = stringResource(R.string.handwriting_retry),
                        onTap = { onKey(KeyAction.RetryHandwritingDownload) },
                    ) {
                        Text(
                            text = stringResource(R.string.handwriting_retry),
                            color = colors.label,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
                HandwritingModelStatus.Downloading ->
                    PadMessage(stringResource(R.string.handwriting_downloading, handwriting.percent), colors)
                else -> PadMessage(stringResource(R.string.handwriting_preparing), colors)
            }
        }
        // 有筆跡時，按鍵先寫入第一個候選字；空白鍵只寫入，不加空格；Delete 清走筆跡。
        val keys: (KeyAction) -> Unit = { action ->
            when {
                pad.isEmpty -> onKey(action)
                action == KeyAction.Delete -> {
                    pad.clear()
                    onInk(InkStrokes.Empty)
                }
                action == KeyAction.Space -> commitTop()
                action == KeyAction.Enter || action is KeyAction.SmartPunctuation -> {
                    commitTop()
                    onKey(action)
                }
                else -> onKey(action)
            }
        }
        TypingKeys(slide) {
            KeyRow(bottom = true) {
                BackToLettersKey(colors) {
                    if (!pad.isEmpty) commitTop()
                    onBackToLetters()
                }
                val comma = if (state.fullWidthPunctuation) "，" else ","
                val period = if (state.fullWidthPunctuation) "。" else "."
                Key(
                    colors,
                    weight = 1f,
                    description = stringResource(R.string.key_comma),
                    preview = comma,
                    onTap = { keys(KeyAction.SmartPunctuation(PunctuationKey.Comma)) },
                ) {
                    KeyLabel(comma, colors)
                }
                SpaceKey(colors, weight = 4f, onKey = keys)
                Key(
                    colors,
                    weight = 1f,
                    description = stringResource(R.string.key_period),
                    preview = period,
                    onTap = { keys(KeyAction.SmartPunctuation(PunctuationKey.Period)) },
                ) {
                    KeyLabel(period, colors)
                }
                DeleteKey(colors, weight = 1.5f, onKey = keys)
                Key(
                    colors,
                    weight = 1.5f,
                    description = stringResource(R.string.key_enter),
                    style = KeyStyle.Function,
                    sound = KeySound.Enter,
                    onTap = { keys(KeyAction.Enter) },
                ) {
                    KeyIcon(R.drawable.ic_keyboard_return, colors.label)
                }
            }
        }
    }
}

@Composable
private fun PadMessage(text: String, colors: KeyboardColors) {
    Text(
        text = text,
        color = colors.secondaryLabel,
        fontSize = 15.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}
