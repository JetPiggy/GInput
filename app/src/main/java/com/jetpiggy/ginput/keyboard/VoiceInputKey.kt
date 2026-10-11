package com.jetpiggy.ginput.keyboard

/** 工具列最右邊的語音輸入按鈕。 */
enum class VoiceInputKey {
    /** 密碼欄位和要求不顯示咪高峰鍵的欄位：沒有按鈕。 */
    Hidden,

    /** 按下就切換到手機已啟用的語音輸入法。 */
    Available,

    /** 手機未啟用語音輸入法：按下時候選列提示到系統設定啟用。 */
    NotEnabled,
}
