package com.jetpiggy.ginput.typing

import com.jetpiggy.ginput.engine.EngineSettings

/** 空白鍵模式：組字時按空白鍵做甚麼。 */
enum class SpaceMode {
    /** 把字母當英文寫入，再加一個空格（預設）。 */
    WriteEnglish,

    /** 有候選字就寫入首選字，沒有才當英文寫入。 */
    SelectChinese,
}

data class TypingSettings(
    val engine: EngineSettings = EngineSettings(),
    val spaceMode: SpaceMode = SpaceMode.WriteEnglish,
    /** 智能刪空格 */
    val smartSpaceRemoval: Boolean = true,
    /** 句首自動大寫（欄位也要接受自動大寫）；單獨的「i」寫成「I」不受這個設定影響。 */
    val autoCapitalize: Boolean = true,
    /** Emoji 關鍵字（預設關閉） */
    val emojiKeywords: Boolean = false,
)

/** 對方文字框的類型，決定鍵盤佈局、能否打中文，以及標點的全形或半形。 */
enum class FieldKind {
    Text,
    Email,
    Url,
    Password,
    Number,
    Phone,
    DateTime;

    val usesNumberPad: Boolean
        get() = this == Number || this == Phone || this == DateTime

    /** 密碼和數字欄位只出英文和數字，不組字、不出候選字。 */
    val allowsChinese: Boolean
        get() = this == Text || this == Email || this == Url

    /** 這些欄位的標點一律半形。 */
    val forcesHalfWidthPunctuation: Boolean
        get() = this != Text
}

enum class ShiftState {
    Off,

    /** 句首自動大寫。 */
    Auto,

    /** 使用者按了一下 Shift：下一個字母大寫。 */
    OneShot,

    /** 連按兩下 Shift：一直大寫。 */
    Locked;

    val uppercase: Boolean
        get() = this != Off
}

/** 按智能全形標點出全形或半形的鍵：字母頁的「，」「。」和 123 頁的 ! ? : ; ( ) ~。 */
enum class PunctuationKey(val fullWidth: String, val halfWidth: String) {
    Comma("，", ","),
    Period("。", "."),
    Exclamation("！", "!"),
    Question("？", "?"),
    Colon("：", ":"),
    Semicolon("；", ";"),
    OpenParenthesis("（", "("),
    CloseParenthesis("）", ")"),
    Tilde("～", "~"),
    ;

    companion object {
        /** 123 頁上半形寫法是 symbol 的鍵；不是這些鍵就是 null。 */
        fun ofHalfWidth(symbol: String): PunctuationKey? = entries.firstOrNull { it.halfWidth == symbol }
    }
}

/** 寫入了哪一類文字。只有類別，不含內容，給使用統計數次數。 */
enum class WriteKind {
    /** 按候選字，或用空白鍵選中文。 */
    Chinese,

    /** 聯想。 */
    Association,

    /** 把組字當英文寫入（空白鍵、Enter 或標點）。 */
    English,

    /** 英文建議。 */
    EnglishSuggestion,
}
