package com.jetpiggy.ginput.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetpiggy.ginput.BuildConfig
import com.jetpiggy.ginput.R
import com.jetpiggy.ginput.ui.theme.JetPiggyInk
import com.jetpiggy.ginput.ui.theme.JetPiggyTeal
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

private const val SOURCE_URL = "https://github.com/JetPiggy/GInput"

/** brand_flying 的闊和高之比（734 × 480），見 tools/icon/build_icon.py。 */
private const val FLYING_RATIO = 734f / 480f

/** 橫額上小標籤的字：品牌青綠調淺，在墨黑底上看得清楚。 */
private val PillText = Color(0xFF9FE6DC)

/** 連按飛豬多少下，牠就飛過畫面；兩下之間相隔太久就重新數。 */
private const val EASTER_EGG_TAPS = 5
private const val TAP_WINDOW_MILLIS = 1500L
private const val FLIGHT_MILLIS = 2400

/** 設定主頁最頂的品牌橫額：青綠底，左邊是 GInput 和版本，右邊是向前飛的 JetPiggy 飛豬。不寫標語。 */
@Composable
internal fun BrandBanner(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 168.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(JetPiggyTeal),
    ) {
        // 飛豬的高度跟橫額；字放大了、橫額加高的話，飛豬也跟着變大。右邊伸出橫額外，只見到一部分。
        Box(Modifier.matchParentSize()) {
            Image(
                painter = painterResource(R.drawable.brand_flying),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 20.dp)
                    .padding(vertical = 12.dp)
                    .fillMaxHeight()
                    .aspectRatio(FLYING_RATIO, matchHeightConstraintsFirst = true),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 22.dp, top = 20.dp, bottom = 20.dp, end = 16.dp)
                .semantics(mergeDescendants = true) { heading() },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                color = JetPiggyInk,
                fontSize = 38.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = stringResource(R.string.settings_banner),
                color = JetPiggyInk,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.brand_by_version, BuildConfig.VERSION_NAME),
                color = PillText,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(CircleShape)
                    .background(JetPiggyInk)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

/**
 * 「關於」的品牌卡：坐著點鍵盤的飛豬、GInput、出品和版本，下面是網站、原始碼和聯絡我們。
 * 連按飛豬 EASTER_EGG_TAPS 下是彩蛋（onEasterEgg）；這個不是按鈕，TalkBack 不會讀到。
 */
@Composable
internal fun AboutCard(onContact: () -> Unit, onEasterEgg: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PigAvatar(onEasterEgg)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = stringResource(R.string.brand_by),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.version_name, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AboutButton(stringResource(R.string.website)) { uriHandler.openUri(BuildConfig.SITE_URL) }
                AboutButton(stringResource(R.string.source_code)) { uriHandler.openUri(SOURCE_URL) }
                AboutButton(stringResource(R.string.contact), onContact)
            }
        }
    }
}

/** 三個按鈕平分闊度；英文等較長的字放不下就分兩行，三個一樣高。 */
@Composable
private fun RowScope.AboutButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
    ) {
        Text(text, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** 青綠圓形裏坐著點鍵盤的飛豬，和 App 圖示一樣。 */
@Composable
private fun PigAvatar(onEasterEgg: () -> Unit) {
    val currentOnEasterEgg by rememberUpdatedState(onEasterEgg)
    var taps by remember { mutableIntStateOf(0) }
    LaunchedEffect(taps) {
        if (taps > 0) {
            delay(TAP_WINDOW_MILLIS)
            taps = 0
        }
    }
    Box(
        modifier = Modifier
            .size(92.dp)
            .clip(CircleShape)
            .background(JetPiggyTeal)
            .pointerInput(Unit) {
                detectTapGestures {
                    taps += 1
                    if (taps >= EASTER_EGG_TAPS) {
                        taps = 0
                        currentOnEasterEgg()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.brand_sitting),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(60.dp)
                .padding(top = 2.dp),
        )
    }
}

/**
 * 彩蛋：飛豬由左至右，上下起伏飛過整個畫面，飛完就呼叫 onFinished。只是圖案，不攔觸控，TalkBack 也不讀。
 * 系統關了動畫的話，一下子就飛完。
 */
@Composable
internal fun FlyingPig(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val currentOnFinished by rememberUpdatedState(onFinished)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(FLIGHT_MILLIS, easing = LinearEasing))
        currentOnFinished()
    }
    BoxWithConstraints(modifier.clearAndSetSemantics {}) {
        val width = 180.dp
        val height = width / FLYING_RATIO
        Image(
            painter = painterResource(R.drawable.brand_flying),
            contentDescription = null,
            modifier = Modifier
                .size(width, height)
                .graphicsLayer {
                    val p = progress.value
                    val wave = 2 * PI * 2 * p
                    translationX = -width.toPx() + p * (maxWidth + width).toPx()
                    translationY = (maxHeight * 0.45f - height / 2).toPx() + sin(wave).toFloat() * 28.dp.toPx()
                    rotationZ = -cos(wave).toFloat() * 6f
                },
        )
    }
}
