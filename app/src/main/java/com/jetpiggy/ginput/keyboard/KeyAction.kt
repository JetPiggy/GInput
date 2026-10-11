package com.jetpiggy.ginput.keyboard

import com.jetpiggy.ginput.cloudtext.CloudTool
import com.jetpiggy.ginput.cloudtext.TextTask
import com.jetpiggy.ginput.typing.PunctuationKey

/** 鍵盤交給輸入法處理的動作。 */
sealed interface KeyAction {
    /** 字母鍵，letter 一律是小寫；大小寫由 Shift 狀態決定。 */
    data class Letter(val letter: Char) : KeyAction

    /** 照樣寫入的字元：數字、123 頁的符號、標點選單、數字鍵盤。有相關符號的話，候選列會列出。 */
    data class Text(val text: String) : KeyAction

    /** 「，」「。」鍵和 123 頁的 ! ? : ; ( ) ~，按智能全形標點決定全形或半形。 */
    data class SmartPunctuation(val key: PunctuationKey) : KeyAction
    data object Space : KeyAction
    data object Enter : KeyAction
    data object Delete : KeyAction
    data object Shift : KeyAction
    data class PickCandidate(val text: String) : KeyAction
    data class PickEnglish(val word: String) : KeyAction
    data class PickAssociation(val text: String) : KeyAction

    /** 候選列的相關符號：取代剛打的符號。 */
    data class PickRelatedSymbol(val text: String) : KeyAction

    /** 候選列的 ✕：收起聯想或相關符號。 */
    data object DismissFollowUps : KeyAction

    /** 忘記記憶字，讓它回到原來的位置。 */
    data class ForgetCandidate(val text: String) : KeyAction

    /** Emoji 面板或膚色選單裏的 Emoji；會記進「最近使用」。 */
    data class CommitEmoji(val emoji: String) : KeyAction

    /** Emoji 關鍵字找到、排在聯想前面的 Emoji；也記進「最近使用」。 */
    data class PickKeywordEmoji(val emoji: String) : KeyAction

    /** 剪貼簿面板裏剪貼簿歷史的一項。 */
    data class WriteClipboardItem(val text: String) : KeyAction

    /** 剪貼簿面板裏的常用短語。 */
    data class WritePhrase(val text: String) : KeyAction

    /** 把剪貼簿歷史的一項存成常用短語。 */
    data class SavePhrase(val text: String) : KeyAction

    /** 從剪貼簿歷史刪除一項；不影響系統剪貼簿。 */
    data class DeleteClipboardItem(val text: String) : KeyAction
    data class DeletePhrase(val text: String) : KeyAction

    /** 工具列的「剪貼簿」：打開剪貼簿面板。 */
    data object OpenClipboard : KeyAction

    /** 打開設定 App 的常用短語頁。 */
    data object ManagePhrases : KeyAction

    /** 在空白鍵上左右掃；steps 是負數向左，正數向右。 */
    data class MoveCursor(val steps: Int) : KeyAction

    /** Delete 鍵向左掃：選取游標前 words 個字詞；wholeLine 就選取到行首。 */
    data class SelectBackward(val words: Int, val wholeLine: Boolean) : KeyAction

    /** Delete 鍵放手：delete 為 true 就刪掉選取的字詞，否則還原游標。 */
    data class FinishBackwardSelection(val delete: Boolean) : KeyAction
    data object HideKeyboard : KeyAction

    /** 剪貼簿面板最頂一行的複製、貼上和全選。 */
    data object Copy : KeyAction
    data object Paste : KeyAction
    data object SelectAll : KeyAction
    data object OpenSettings : KeyAction
    data object ShowInputMethodPicker : KeyAction

    /** 工具列的語音輸入：切換到手機已啟用的語音輸入法。 */
    data object VoiceInput : KeyAction

    /** 手機未啟用語音輸入法時，打開系統的輸入法設定。 */
    data object VoiceInputSettings : KeyAction

    /** 工具列的「翻譯」或「改寫」：打開翻譯面板或改寫面板，並讀取原文。 */
    data class OpenCloudTool(val tool: CloudTool) : KeyAction

    /** 同意第一次使用時的說明。 */
    data object AcceptCloudNotice : KeyAction

    /** 翻譯面板或改寫面板的選項：把原文送出處理。 */
    data class RunTextTask(val task: TextTask) : KeyAction

    /** 用結果取代原文。 */
    data object ApplyTextResult : KeyAction

    /** 離開翻譯面板或改寫面板；未完成的處理不再理會。 */
    data object CloseCloudTool : KeyAction
}
