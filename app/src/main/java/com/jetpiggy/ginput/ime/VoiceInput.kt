package com.jetpiggy.ginput.ime

import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype

/** 語音輸入法的 subtype 模式。 */
private const val VOICE_MODE = "voice"

/**
 * Google 語音輸入（支援粵語）：舊手機由 Google App 提供，新手機由「Google 語音服務」（com.google.android.tts）提供。
 * 手機有幾個語音輸入法時優先用它。
 */
private val GOOGLE_VOICE_TYPING = setOf(
    "com.google.android.googlequicksearchbox",
    "com.google.android.tts",
)

/**
 * 鍵盤上有咪高峰鍵的常見輸入法（套件名稱的開頭；各手機品牌的版本只是在後面加字），按優先次序。
 * 這些輸入法只宣告鍵盤 subtype，沒有 voice 模式；沒有 Google 服務的手機（例如國行手機）通常只有它們能語音輸入。
 * Gboard 排最後：它的語音輸入要靠 Google 語音服務，有的話已經用上面的語音輸入法。
 */
private val KEYBOARDS_WITH_VOICE_KEY = listOf(
    "com.iflytek.inputmethod", // 訊飛輸入法
    "com.sohu.inputmethod.sogou", // 搜狗輸入法
    "com.baidu.input", // 百度輸入法
    "com.tencent.wetype", // 微信輸入法
    "com.huawei.ohos.inputmethod", // 小藝輸入法
    "com.samsung.android.honeyboard", // Samsung 鍵盤
    "com.touchtype.swiftkey", // Microsoft SwiftKey
    "com.google.android.inputmethod.latin", // Gboard
)

/**
 * 語音輸入選用一個輸入法的優先次序，數字越小越優先；null 是不能用來語音輸入。
 * 先揀有 voice subtype 的語音輸入法（Google 語音輸入最優先），沒有才揀鍵盤上有咪高峰鍵的輸入法。
 */
internal fun voiceInputRank(packageName: String, hasVoiceSubtype: Boolean): Int? = when {
    hasVoiceSubtype -> if (packageName in GOOGLE_VOICE_TYPING) 0 else 1
    else -> KEYBOARDS_WITH_VOICE_KEY
        .indexOfFirst { packageName == it || packageName.startsWith("$it.") || packageName.startsWith("${it}_") }
        .takeIf { it >= 0 }
        ?.let { 2 + it }
}

/**
 * 語音輸入要切換到的輸入法。[subtype] 是它的 voice subtype；null 表示它是鍵盤上有咪高峰鍵的輸入法，
 * 切換過去用它目前的 subtype，使用者再按它的咪高峰說話。
 */
internal class VoiceInputTarget(val method: InputMethodInfo, val subtype: InputMethodSubtype?)

/**
 * 手機已啟用、可以用來語音輸入的輸入法，按 [voiceInputRank] 揀最優先的一個；不計 GInput 自己，沒有就是 null。
 * Android 11 起要在 manifest 宣告 queries，才看得到其他輸入法。
 */
internal fun InputMethodManager.enabledVoiceInput(ownPackage: String): VoiceInputTarget? =
    enabledInputMethodList
        .filter { it.packageName != ownPackage }
        .mapNotNull { method ->
            val voice = getEnabledInputMethodSubtypeList(method, true).firstOrNull { it.mode == VOICE_MODE }
            voiceInputRank(method.packageName, voice != null)?.let { rank -> rank to VoiceInputTarget(method, voice) }
        }
        .minByOrNull { it.first }
        ?.second
