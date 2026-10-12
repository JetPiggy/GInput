package com.jetpiggy.ginput.usagestats

/**
 * 使用統計數的功能（規則見 docs/spec/v1.md 的「使用統計」）。只數次數，不記內容。
 * code 和 backend/usage-stats 接受的名稱一致；加減項目要兩邊一起改。
 */
enum class UsageEvent(val code: String) {
    /** 按候選字，或用空白鍵選中文。 */
    Chinese("chinese"),
    Association("association"),

    /** 把組字當英文寫入。 */
    English("english"),
    EnglishSuggestion("englishSuggestion"),
    Emoji("emoji"),

    /** Emoji 關鍵字的 Emoji。 */
    KeywordEmoji("keywordEmoji"),
    CandidateGrid("candidateGrid"),
    PunctuationMenu("punctuationMenu"),

    /** 開始一次游標滑動，在空白鍵或其他按鍵上都算。 */
    CursorSwipe("cursorSwipe"),
    DeleteSwipe("deleteSwipe"),
    Copy("copy"),
    Paste("paste"),
    SelectAll("selectAll"),

    /** 打開剪貼簿面板。 */
    ClipboardPanel("clipboardPanel"),

    /** 寫入剪貼簿歷史的一項。 */
    ClipboardItem("clipboardItem"),

    /** 寫入常用短語。 */
    Phrase("phrase"),

    /** 把剪貼簿歷史的一項存成常用短語。 */
    SavePhrase("savePhrase"),
    OpenSettings("openSettings"),

    /** 雲端翻譯成功一次。 */
    CloudTranslation("translate"),

    /** 改寫成功一次。 */
    Rewrite("rewrite"),

    /** 工具列的語音輸入：切換到語音輸入法。 */
    VoiceInput("voiceInput"),
    Forget("forget"),
    KeyboardSwitch("keyboardSwitch"),
    HideKeyboard("hideKeyboard"),
}
