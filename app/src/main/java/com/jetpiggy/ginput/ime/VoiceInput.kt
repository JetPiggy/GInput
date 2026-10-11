package com.jetpiggy.ginput.ime

import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype

/** 語音輸入法的 subtype 模式。 */
private const val VOICE_MODE = "voice"

/** Google 語音輸入由 Google App 提供，支援粵語；手機有幾個語音輸入法時優先用它。 */
private const val GOOGLE_VOICE_TYPING = "com.google.android.googlequicksearchbox"

/**
 * 手機已啟用的語音輸入法（subtype 模式是 voice），連同那個 subtype；不計 GInput 自己，沒有就是 null。
 * Android 11 起要在 manifest 宣告 queries，才看得到其他輸入法。
 */
internal fun InputMethodManager.enabledVoiceInput(ownPackage: String): Pair<InputMethodInfo, InputMethodSubtype>? =
    enabledInputMethodList
        .filter { it.packageName != ownPackage }
        .sortedByDescending { it.packageName == GOOGLE_VOICE_TYPING }
        .firstNotNullOfOrNull { method ->
            getEnabledInputMethodSubtypeList(method, true).firstOrNull { it.mode == VOICE_MODE }?.let { method to it }
        }
