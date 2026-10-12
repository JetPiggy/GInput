package com.jetpiggy.ginput.typing

/** 模擬 Android 文字框的組字和選取行為；按鍵事件記在 keys。 */
class FakeEditor(
    initialText: String = "",
    private val capsMode: (String) -> Boolean = { false },
) : Editor {
    private val buffer = StringBuilder(initialText)
    private var composingStart = -1
    private var composingEnd = -1

    /** 選取的終點（沒有選取時就是游標）。 */
    var cursor = initialText.length
        set(value) {
            field = value
            selectionStart = null
        }

    /** 有選取時是選取的起點（不動的一端，可以在 cursor 之後），否則是 null。 */
    private var selectionStart: Int? = null

    val keys = mutableListOf<String>()

    val text: String get() = buffer.toString()
    val composing: String get() = if (composingStart < 0) "" else buffer.substring(composingStart, composingEnd)
    val selected: String get() = selectionStart?.let { buffer.substring(minOf(it, cursor), maxOf(it, cursor)) }.orEmpty()

    /** 和 Android 一樣：有選取時，回傳選取範圍之前的文字。 */
    override fun textBeforeCursor(length: Int): String {
        val end = selectionStart?.let { minOf(it, cursor) } ?: cursor
        return buffer.substring(maxOf(0, end - length), end)
    }

    override fun capsModeAtCursor(): Boolean = capsMode(buffer.substring(0, cursor))

    override fun setComposingText(text: String) {
        val start = replace(text)
        if (text.isEmpty()) {
            finishComposingText()
        } else {
            composingStart = start
            composingEnd = cursor
        }
    }

    override fun finishComposingText() {
        composingStart = -1
        composingEnd = -1
    }

    override fun commitText(text: String) {
        replace(text)
        finishComposingText()
    }

    override fun deleteBeforeCursor(length: Int) {
        val start = maxOf(0, cursor - length)
        buffer.delete(start, cursor)
        cursor = start
    }

    override fun sendDeleteKey() {
        keys += "DEL"
        val anchor = selectionStart
        when {
            anchor != null -> {
                val start = minOf(anchor, cursor)
                buffer.delete(start, maxOf(anchor, cursor))
                cursor = start
            }
            cursor > 0 -> {
                buffer.deleteCharAt(cursor - 1)
                cursor--
            }
        }
    }

    override fun sendEnterKey() {
        keys += "ENTER"
    }

    override fun moveCursor(steps: Int, select: Boolean) {
        val arrow = if (steps < 0) "LEFT×${-steps}" else "RIGHT×$steps"
        keys += if (select) "SHIFT+$arrow" else arrow
        val anchor = selectionStart ?: cursor
        cursor = (cursor + steps).coerceIn(0, buffer.length)
        if (select && anchor != cursor) selectionStart = anchor
    }

    override fun cursorPosition(): Int = cursor

    override fun setSelection(start: Int, end: Int) {
        cursor = end
        selectionStart = if (start == end) null else start
    }

    override fun beginBatchEdit() = Unit

    override fun endBatchEdit() = Unit

    /** 取代組字（沒有組字就插入游標處），回傳新文字的起點。 */
    private fun replace(text: String): Int {
        val start = if (composingStart >= 0) composingStart else cursor
        val end = if (composingStart >= 0) composingEnd else cursor
        buffer.replace(start, end, text)
        cursor = start + text.length
        return start
    }
}
