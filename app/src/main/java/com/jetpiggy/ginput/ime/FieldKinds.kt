package com.jetpiggy.ginput.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.jetpiggy.ginput.typing.FieldKind

internal fun EditorInfo.fieldKind(): FieldKind {
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    return when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_NUMBER -> FieldKind.Number
        InputType.TYPE_CLASS_PHONE -> FieldKind.Phone
        InputType.TYPE_CLASS_DATETIME -> FieldKind.DateTime
        InputType.TYPE_CLASS_TEXT -> when (variation) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> FieldKind.Password
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> FieldKind.Email
            InputType.TYPE_TEXT_VARIATION_URI -> FieldKind.Url
            else -> FieldKind.Text
        }
        else -> FieldKind.Text
    }
}

/** InputType 為 TYPE_NULL 的欄位（例如終端機）不支援組字。 */
internal fun EditorInfo.supportsComposing(): Boolean = inputType != InputType.TYPE_NULL

/** 欄位要求不顯示咪高峰鍵：工具列不出語音輸入按鈕。 */
internal fun EditorInfo.disallowsMicrophone(): Boolean = disallowsMicrophone(privateImeOptions)

/** privateImeOptions 有 noMicrophoneKey（前面是輸入法的套件名稱），或舊式的 nm。 */
internal fun disallowsMicrophone(privateImeOptions: String?): Boolean =
    privateImeOptions.orEmpty().split(',').map(String::trim).any { it == "nm" || it.endsWith(".noMicrophoneKey") }

/** 欄位沒有要求不學習（例如無痕模式）：可以記錄記憶字、最近使用的 Emoji 和剪貼簿歷史。 */
internal fun EditorInfo.allowsLearning(): Boolean = (imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0
