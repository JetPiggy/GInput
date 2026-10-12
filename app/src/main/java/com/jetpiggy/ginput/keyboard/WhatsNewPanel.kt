package com.jetpiggy.ginput.keyboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetpiggy.ginput.BuildConfig
import com.jetpiggy.ginput.R

/**
 * 新版簡介：更新了 GInput 之後，第一次在一般文字框打開鍵盤時取代按鍵區（高度相同，keyArea），
 * 簡單列出這個版本改了甚麼；按「知道了」就回到原來的按鍵，之後不再顯示。規則見 docs/spec/v1.md 的「發佈」。
 */
@Composable
internal fun WhatsNewPanel(keyArea: Dp, colors: KeyboardColors, onDismiss: () -> Unit) {
    val dimensions = LocalKeyboardDimensions.current
    // 放不下（例如橫向或矮鍵盤）就捲動。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(keyArea - dimensions.rowHeight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.whats_new_title, BuildConfig.VERSION_NAME),
            color = colors.label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        for (item in stringArrayResource(R.array.whats_new_items)) {
            Row(Modifier.padding(top = 4.dp)) {
                Text(text = "•", color = colors.label, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.width(14.dp))
                Text(text = item, color = colors.label, fontSize = 15.sp, lineHeight = 21.sp)
            }
        }
    }
    KeyRow(bottom = true) {
        Spacer(Modifier.weight(6.5f))
        ActionKey(stringResource(R.string.whats_new_ok), enabled = true, colors, onTap = onDismiss)
    }
}
