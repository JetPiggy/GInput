package com.jetpiggy.ginput.typing

/** 對方 App 的文字框。Android 的實作包住 InputConnection；測試用 FakeEditor。 */
interface Editor {
    /** 游標前最多 length 個 UTF-16 字元，包括組字；讀不到就回傳空字串。 */
    fun textBeforeCursor(length: Int): String

    /** 按欄位的設定，游標處的字母要不要自動大寫。 */
    fun capsModeAtCursor(): Boolean

    /** 取代目前的組字；沒有組字就在游標處開始一段。 */
    fun setComposingText(text: String)

    /** 把組字留在文字框裏，成為普通文字。 */
    fun finishComposingText()

    /** 寫入文字；有組字就取代組字。 */
    fun commitText(text: String)

    /** 刪除游標前 length 個 UTF-16 字元。 */
    fun deleteBeforeCursor(length: Int)

    /** 像實體鍵盤一樣按一下 Delete。 */
    fun sendDeleteKey()

    /** 像實體鍵盤一樣按一下 Enter：欄位有動作（例如送出）就執行，否則換行。 */
    fun sendEnterKey()

    /** 像實體鍵盤的方向鍵一樣移動游標；steps 是負數向左，正數向右。 */
    fun moveCursor(steps: Int)

    /** 游標在整段文字裏的位置；讀不到就回傳 null。 */
    fun cursorPosition(): Int?

    /** 選取 [start, end)，兩者都是整段文字裏的位置；start 等於 end 就只是移動游標。 */
    fun setSelection(start: Int, end: Int)

    fun beginBatchEdit()
    fun endBatchEdit()
}
