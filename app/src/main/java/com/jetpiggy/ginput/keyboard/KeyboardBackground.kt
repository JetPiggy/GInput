package com.jetpiggy.ginput.keyboard

import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize

/** 整個鍵盤（連導航列讓出的位置）的版面位置和大小：鋪在鍵盤一部分上面的底色用它對齊漸變。 */
internal class KeyboardArea {
    var coordinates: LayoutCoordinates? = null
        private set
    var size by mutableStateOf(Size.Zero)
        private set

    fun update(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        size = coordinates.size.toSize()
    }
}

internal val LocalKeyboardArea = staticCompositionLocalOf<KeyboardArea?> { null }

/** 記下整個鍵盤的範圍，放在鍵盤最外層、讓出導航列的位置之前。 */
internal fun Modifier.keyboardArea(area: KeyboardArea): Modifier = onGloballyPositioned { area.update(it) }

/**
 * 主題的底色：純色，或者按整個鍵盤的範圍畫的漸變，所以鋪在候選列或按鍵區上面的一層（例如改字揀字時的整句）
 * 和下面的底色接得上。不在鍵盤裏（例如設定頁的小樣本）就按自己的大小畫。
 */
internal fun Modifier.keyboardBackground(colors: KeyboardColors): Modifier {
    val gradient = colors.gradient ?: return background(colors.background)
    return composed {
        val area = LocalKeyboardArea.current
        // 這一塊在鍵盤裏的位置，用鍵盤自己的座標，不受設定頁預覽的縮放影響。
        var offset by remember { mutableStateOf(Offset.Zero) }
        onGloballyPositioned { coordinates ->
            val keyboard = area?.coordinates
            offset = if (keyboard != null && keyboard.isAttached) keyboard.localPositionOf(coordinates, Offset.Zero) else Offset.Zero
        }.drawBehind {
            val whole = area?.size?.takeUnless { it.isEmpty() } ?: size
            val (start, end) = gradient.line(whole)
            drawRect(Brush.linearGradient(gradient.colors, start - offset, end - offset))
        }
    }
}
