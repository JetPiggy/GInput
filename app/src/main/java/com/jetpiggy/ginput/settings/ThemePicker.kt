package com.jetpiggy.ginput.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetpiggy.ginput.R
import com.jetpiggy.ginput.engine.Candidate
import com.jetpiggy.ginput.engine.Scheme
import com.jetpiggy.ginput.keyboard.ClipboardContent
import com.jetpiggy.ginput.keyboard.KeyboardColors
import com.jetpiggy.ginput.keyboard.KeyboardScreen
import com.jetpiggy.ginput.keyboard.NavigationBarSpace
import com.jetpiggy.ginput.keyboard.colors
import com.jetpiggy.ginput.keyboard.keyShadow
import com.jetpiggy.ginput.keyboard.keyboardBackground
import com.jetpiggy.ginput.typing.FieldKind
import com.jetpiggy.ginput.typing.KeyboardState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SampleShape = RoundedCornerShape(12.dp)
private val PreviewShape = RoundedCornerShape(16.dp)
private val MiniKeyCorner = 3.dp
private val ChipShape = RoundedCornerShape(MiniKeyCorner)
private val SwatchShape = RoundedCornerShape(6.dp)
private val SwatchKeyCorner = 1.5.dp
private const val THEME_FADE_MILLIS = 200

/** 主題頁頂部的預覽是真正鍵盤的六成大小；窄的手機再縮小，右邊最少留這麼闊寫主題名稱和淺色、深色切換。 */
private const val THEME_PREVIEW_SCALE = 0.6f
private val ThemeInfoMinWidth = 112.dp
private val ThemeHeaderGap = 16.dp

/** 主題頁的樣本：每行最少 3 個，螢幕闊就按最窄的闊度加到最多 6 個。 */
private const val MIN_THEMES_PER_ROW = 3
private const val MAX_THEMES_PER_ROW = 6
private val ThemeSampleMinWidth = 96.dp
private val ThemeGridGap = 12.dp

/** 主題頁左右的邊距；格子的邊距是它減去樣本之間一半的間距，因為每個樣本左右各有一半的間距。 */
private val ThemePageMargin = 16.dp
private val ThemeGridMargin = ThemePageMargin - ThemeGridGap / 2

/** 系列分頁標籤的文字左右各留的位置，和 Material 3 的 Tab 一樣。 */
private val TabTextPadding = 16.dp

/** 鍵盤預覽的候選列：打字打到一半，第一個是記憶字，看得到記憶字的顏色。 */
private val PreviewState = KeyboardState(
    composition = "nei",
    candidates = listOf("你", "妳", "尼", "膩", "餌", "伱").mapIndexed { index, text ->
        Candidate(text, Scheme.Jyutping, codeLength = 3, remembered = index == 0)
    },
)

/**
 * 鍵盤預覽：真正的鍵盤畫面，用現在的主題、鍵盤高度、按鍵標示和數字行，改了即時看到。
 * 只供觀看：上面蓋了一層接收觸控的空位，按鍵按不到；預覽跟着設定一起捲動時，在上面拖動仍然可以捲動。
 * TalkBack 只讀「鍵盤預覽」。
 */
@Composable
internal fun KeyboardPreview(keyboard: KeyboardSettings, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KeyboardPreviewFrame(keyboard, isSystemInDarkTheme(), scale = 1f, PreviewShape, Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.keyboard_preview_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 鍵盤預覽的鍵盤：用主題的深色或淺色那組顏色。scale 小於 1 時，按螢幕的闊度（真正鍵盤的闊度）畫，再按比例縮小，
 * 佔的位置也跟着縮小。
 */
@Composable
private fun KeyboardPreviewFrame(keyboard: KeyboardSettings, dark: Boolean, scale: Float, shape: Shape, modifier: Modifier) {
    val description = stringResource(R.string.keyboard_preview)
    val navigationBar = remember { NavigationBarSpace() }
    val keyboardWidth = LocalConfiguration.current.screenWidthDp.dp
    Box(
        modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Crossfade(
            targetState = keyboard.theme to dark,
            animationSpec = tween(THEME_FADE_MILLIS),
            label = "theme",
            modifier = if (scale < 1f) Modifier.scaled(keyboardWidth, scale) else Modifier,
        ) { (theme, themeDark) ->
            KeyboardScreen(
                state = PreviewState,
                settings = keyboard.copy(theme = theme, keyPreview = false, keySound = false),
                field = FieldKind.Text,
                emojiCategories = emptyList(),
                recentEmoji = emptyList(),
                clipboard = ClipboardContent(),
                onKey = {},
                navigationBar = navigationBar,
                dark = themeDark,
            )
        }
        // 觸控只到這一層（不會傳給下面的按鍵），沒有用掉的拖動交給外面捲動。
        Spacer(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        )
    }
}

/** 按 width 量度內容，再縮小 scale 倍顯示；佔的位置也跟着縮小。 */
private fun Modifier.scaled(width: Dp, scale: Float): Modifier = layout { measurable, _ ->
    val full = width.roundToPx()
    val placeable = measurable.measure(Constraints(minWidth = full, maxWidth = full))
    layout((placeable.width * scale).roundToInt(), (placeable.height * scale).roundToInt()) {
        placeable.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}

/** 「鍵盤外觀」頁的主題：目前的主題和小樣本，按一下打開主題頁。 */
@Composable
internal fun ThemeRow(theme: KeyboardTheme, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RowText(
            title = stringResource(R.string.keyboard_theme),
            summary = stringResource(R.string.theme_row_summary, themeName(theme)),
            modifier = Modifier.weight(1f),
        )
        ThemeSwatch(theme)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 主題頁的內容：主題按系列分段，每段一個標題，下面是小鍵盤樣本，按一下就套用。樣本用 dark 那組顏色，和預覽一樣。
 * header 不是 null 的話（矮的螢幕）放在最頂，跟着一起捲動。
 */
@Composable
internal fun ThemeGrid(
    state: LazyGridState,
    selected: KeyboardTheme,
    dark: Boolean,
    onSelect: (KeyboardTheme) -> Unit,
    header: (@Composable () -> Unit)?,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val columns = ((screenWidth - ThemePageMargin * 2 + ThemeGridGap) / (ThemeSampleMinWidth + ThemeGridGap)).toInt()
        .coerceIn(MIN_THEMES_PER_ROW, MAX_THEMES_PER_ROW)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        modifier = Modifier
            .fillMaxSize()
            .selectableGroup(),
        contentPadding = PaddingValues(start = ThemeGridMargin, end = ThemeGridMargin, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (header != null) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
        }
        for (series in ThemeSeries.entries) {
            item(key = series, span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = seriesName(series),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = ThemeGridGap / 2, end = ThemeGridGap / 2, top = 8.dp)
                        .semantics { heading() },
                )
            }
            items(KeyboardTheme.entries.filter { it.series == series }, key = { it }) { theme ->
                ThemeOption(theme, theme.colors(dark), selected = theme == selected) { onSelect(theme) }
            }
        }
    }
}

/** 主題頁格子裏每個系列標題的位置；header 在最頂的話全部後移一格。 */
internal fun seriesIndex(series: ThemeSeries, header: Boolean): Int =
    (if (header) 1 else 0) + ThemeSeries.entries.takeWhile { it != series }
        .sumOf { previous -> 1 + KeyboardTheme.entries.count { it.series == previous } }

/** 格子裏第 index 個項目屬於哪個系列；header 算作第一個系列。 */
private fun seriesAt(index: Int, header: Boolean): ThemeSeries =
    ThemeSeries.entries.lastOrNull { seriesIndex(it, header) <= index } ?: ThemeSeries.entries.first()

/**
 * 系列的分頁標籤：按一下捲到該段；捲動時跟着目前看到的系列，捲到最底就是最後一個系列。
 * 全部標籤平均分闊度放得下就這樣放，否則（例如英文）可以橫向捲動。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeriesTabs(state: LazyGridState, header: Boolean) {
    val scope = rememberCoroutineScope()
    // 按了標籤之後，捲到底也可能捲不到那一段的標題：手指再拖動之前，標籤停在按了的那個。
    var tapped by remember { mutableStateOf<ThemeSeries?>(null) }
    LaunchedEffect(state) {
        state.interactionSource.interactions.collect { if (it is DragInteraction.Start) tapped = null }
    }
    val visible by remember(state, header) {
        derivedStateOf {
            if (!state.canScrollForward && state.firstVisibleItemIndex > 0) return@derivedStateOf ThemeSeries.entries.last()
            // 頂部那一格大半已經捲走（例如只剩名稱），就看下一格。
            val layout = state.layoutInfo
            val first = layout.visibleItemsInfo
                .firstOrNull { it.offset.y + it.size.height / 2 >= layout.viewportStartOffset }?.index
                ?: state.firstVisibleItemIndex
            seriesAt(first, header)
        }
    }
    val current = tapped ?: visible
    val names = ThemeSeries.entries.map { seriesName(it) }
    val tabs = @Composable {
        ThemeSeries.entries.forEachIndexed { index, series ->
            Tab(
                selected = series == current,
                onClick = {
                    tapped = series
                    scope.launch { state.animateScrollToItem(if (series.ordinal == 0) 0 else seriesIndex(series, header)) }
                },
                text = { Text(names[index], maxLines = 1) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.titleSmall
    val padding = with(LocalDensity.current) { (TabTextPadding * 2).roundToPx() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widest = names.maxOf { measurer.measure(it, style).size.width }
        if ((widest + padding) * names.size <= constraints.maxWidth) {
            PrimaryTabRow(selectedTabIndex = current.ordinal, tabs = tabs)
        } else {
            PrimaryScrollableTabRow(selectedTabIndex = current.ordinal, edgePadding = ThemePageMargin, tabs = tabs)
        }
    }
}

/**
 * 主題頁頂部：縮小的鍵盤預覽，右邊是目前的主題和預覽用的淺色、深色切換。切換只換預覽和樣本，不改設定。
 * 右邊最少和預覽一樣高，切換放在底部。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeHeader(keyboard: KeyboardSettings, dark: Boolean, onDarkChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val scale = minOf(THEME_PREVIEW_SCALE, (screenWidth - ThemePageMargin * 2 - ThemeHeaderGap - ThemeInfoMinWidth) / screenWidth)
    val gap = with(LocalDensity.current) { ThemeHeaderGap.roundToPx() }
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            KeyboardPreviewFrame(keyboard, dark, scale, SampleShape, Modifier)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Column {
                    Text(
                        text = stringResource(R.string.theme_current),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(themeName(keyboard.theme), style = MaterialTheme.typography.titleLarge, maxLines = 2)
                    Text(
                        text = stringResource(R.string.theme_series_label, seriesName(keyboard.theme.series)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(false to R.string.theme_preview_light, true to R.string.theme_preview_dark).forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = dark == value,
                            onClick = { onDarkChange(value) },
                            shape = SegmentedButtonDefaults.itemShape(index, count = 2),
                            icon = {},
                        ) {
                            Text(stringResource(label), maxLines = 1)
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.theme_preview_mode_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) { measurables, constraints ->
        val preview = measurables[0].measure(Constraints())
        val infoWidth = (constraints.maxWidth - preview.width - gap).coerceAtLeast(0)
        val info = measurables[1].measure(Constraints(minWidth = infoWidth, maxWidth = infoWidth, minHeight = preview.height))
        layout(constraints.maxWidth, maxOf(preview.height, info.height)) {
            preview.placeRelative(0, 0)
            info.placeRelative(preview.width + gap, 0)
        }
    }
}

/** 一個主題的樣本：小鍵盤下面寫名稱；選中的有粗邊框和剔號。 */
@Composable
private fun ThemeOption(theme: KeyboardTheme, colors: KeyboardColors, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = ThemeGridGap / 2)
            .clip(SampleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            MiniKeyboard(
                colors = colors,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = SampleShape,
                    ),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(3.dp),
                )
            }
        }
        Text(
            text = themeName(theme),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Medium else null,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 小鍵盤樣本：候選列的記憶字和候選字，下面三行按鍵。Shift 用大寫鎖定的顏色，看得出主題的強調色。 */
@Composable
private fun MiniKeyboard(colors: KeyboardColors, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(SampleShape)
            .keyboardBackground(colors)
            .padding(6.dp)
            // 樣本只是圖案；TalkBack 讀主題名稱就夠。
            .clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "後",
                color = colors.remembered,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier
                    .clip(ChipShape)
                    .background(colors.rememberedChip)
                    .padding(horizontal = 3.dp),
            )
            Text("段", color = colors.label, fontSize = 12.sp, maxLines = 1)
            Text("般", color = colors.label, fontSize = 12.sp, maxLines = 1)
        }
        MiniRow {
            repeat(5) { MiniKey(colors.key, colors) }
        }
        MiniRow {
            MiniKey(colors.accent, colors, weight = 1.5f)
            repeat(3) { MiniKey(colors.key, colors) }
            MiniKey(colors.functionKey, colors, weight = 1.5f)
        }
        MiniRow {
            MiniKey(colors.functionKey, colors, weight = 1.5f)
            MiniKey(colors.key, colors, weight = 3f)
            MiniKey(colors.functionKey, colors, weight = 1.5f)
        }
    }
}

/** 設定主頁「鍵盤外觀」和「鍵盤外觀」頁主題旁邊的小樣本：只有三行按鍵，看得出主題的顏色。只是圖案，TalkBack 不讀。 */
@Composable
internal fun ThemeSwatch(theme: KeyboardTheme) {
    val colors = theme.colors(isSystemInDarkTheme())
    Column(
        modifier = Modifier
            .width(48.dp)
            .clip(SwatchShape)
            .keyboardBackground(colors)
            .padding(4.dp)
            .clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        MiniRow(height = 6.dp, gap = 2.dp) {
            repeat(5) { MiniKey(colors.key, colors, corner = SwatchKeyCorner) }
        }
        MiniRow(height = 6.dp, gap = 2.dp) {
            MiniKey(colors.accent, colors, weight = 1.5f, corner = SwatchKeyCorner)
            repeat(3) { MiniKey(colors.key, colors, corner = SwatchKeyCorner) }
            MiniKey(colors.functionKey, colors, weight = 1.5f, corner = SwatchKeyCorner)
        }
        MiniRow(height = 6.dp, gap = 2.dp) {
            MiniKey(colors.functionKey, colors, weight = 1.5f, corner = SwatchKeyCorner)
            MiniKey(colors.key, colors, weight = 3f, corner = SwatchKeyCorner)
            MiniKey(colors.functionKey, colors, weight = 1.5f, corner = SwatchKeyCorner)
        }
    }
}

@Composable
private fun MiniRow(height: Dp = 12.dp, gap: Dp = 3.dp, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/** 一粒小按鍵，連底部 1dp 的實色陰影，和真正的按鍵一樣。 */
@Composable
private fun RowScope.MiniKey(color: Color, colors: KeyboardColors, weight: Float = 1f, corner: Dp = MiniKeyCorner) {
    Spacer(
        Modifier
            .weight(weight)
            .fillMaxHeight()
            .keyShadow(colors.keyShadow, corner)
            .background(color, RoundedCornerShape(corner)),
    )
}

@Composable
internal fun themeName(theme: KeyboardTheme): String = stringResource(
    when (theme) {
        KeyboardTheme.Default -> R.string.theme_default
        KeyboardTheme.MilkTea -> R.string.theme_milk_tea
        KeyboardTheme.Tram -> R.string.theme_tram
        KeyboardTheme.Harbour -> R.string.theme_harbour
        KeyboardTheme.Sakura -> R.string.theme_sakura
        KeyboardTheme.Neon -> R.string.theme_neon
        KeyboardTheme.Sunset -> R.string.theme_sunset
        KeyboardTheme.Aurora -> R.string.theme_aurora
        KeyboardTheme.SeaSalt -> R.string.theme_sea_salt
        KeyboardTheme.Holo -> R.string.theme_holo
        KeyboardTheme.RedWhiteBlue -> R.string.theme_red_white_blue
        KeyboardTheme.ChaChaanTeng -> R.string.theme_cha_chaan_teng
        KeyboardTheme.TempleStreet -> R.string.theme_temple_street
        KeyboardTheme.TaiO -> R.string.theme_tai_o
        KeyboardTheme.Taro -> R.string.theme_taro
        KeyboardTheme.Matcha -> R.string.theme_matcha
        KeyboardTheme.MangoSago -> R.string.theme_mango_sago
        KeyboardTheme.BlackSesame -> R.string.theme_black_sesame
        KeyboardTheme.DeepSea -> R.string.theme_deep_sea
        KeyboardTheme.Maple -> R.string.theme_maple
        KeyboardTheme.FirstSnow -> R.string.theme_first_snow
        KeyboardTheme.Beach -> R.string.theme_beach
        KeyboardTheme.HighContrast -> R.string.theme_high_contrast
        KeyboardTheme.Ink -> R.string.theme_ink
        KeyboardTheme.Retro -> R.string.theme_retro
        KeyboardTheme.Morandi -> R.string.theme_morandi
    },
)

@Composable
private fun seriesName(series: ThemeSeries): String = stringResource(
    when (series) {
        ThemeSeries.Classic -> R.string.theme_series_classic
        ThemeSeries.Gradient -> R.string.theme_series_gradient
        ThemeSeries.HongKong -> R.string.theme_series_hong_kong
        ThemeSeries.Dessert -> R.string.theme_series_dessert
        ThemeSeries.Nature -> R.string.theme_series_nature
        ThemeSeries.Style -> R.string.theme_series_style
    },
)
