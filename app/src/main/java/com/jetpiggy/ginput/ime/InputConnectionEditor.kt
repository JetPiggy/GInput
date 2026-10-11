package com.jetpiggy.ginput.ime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.inputmethod.ExtractedTextRequest
import com.jetpiggy.ginput.cloudtext.SourceField
import com.jetpiggy.ginput.typing.Editor
import kotlin.math.abs

/**
 * 把 TypingSession 的操作，以及雲端翻譯和改寫讀取、取代原文的操作，轉給目前文字框的 InputConnection；
 * 沒有連線時甚麼都不做。
 */
class InputConnectionEditor(private val service: InputMethodService) : Editor, SourceField {

    private val connection get() = service.currentInputConnection

    override fun textBeforeCursor(length: Int): String =
        connection?.getTextBeforeCursor(length, 0)?.toString().orEmpty()

    override fun capsModeAtCursor(): Boolean {
        val inputType = service.currentInputEditorInfo?.inputType ?: return false
        return (connection?.getCursorCapsMode(inputType) ?: 0) != 0
    }

    override fun setComposingText(text: String) {
        connection?.setComposingText(text, 1)
    }

    override fun finishComposingText() {
        connection?.finishComposingText()
    }

    override fun commitText(text: String) {
        connection?.commitText(text, 1)
    }

    override fun deleteBeforeCursor(length: Int) {
        connection?.deleteSurroundingText(length, 0)
    }

    override fun sendDeleteKey() {
        service.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    override fun sendEnterKey() {
        service.sendKeyChar('\n')
    }

    override fun moveCursor(steps: Int) {
        val key = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(abs(steps)) { service.sendDownUpKeyEvents(key) }
    }

    override fun cursorPosition(): Int? {
        val request = ExtractedTextRequest().apply { hintMaxChars = 1000 }
        val extracted = connection?.getExtractedText(request, 0) ?: return null
        return if (extracted.selectionEnd < 0) null else extracted.startOffset + extracted.selectionEnd
    }

    override fun setSelection(start: Int, end: Int) {
        connection?.setSelection(start, end)
    }

    override fun selectedText(): String? = connection?.getSelectedText(0)?.toString()

    override fun textAfterCursor(length: Int): String =
        connection?.getTextAfterCursor(length, 0)?.toString().orEmpty()

    override fun replaceSelection(text: String) {
        connection?.commitText(text, 1)
    }

    override fun replaceAroundCursor(before: Int, after: Int, text: String) {
        val connection = connection ?: return
        connection.beginBatchEdit()
        connection.deleteSurroundingText(before, after)
        connection.commitText(text, 1)
        connection.endBatchEdit()
    }

    override fun beginBatchEdit() {
        connection?.beginBatchEdit()
    }

    override fun endBatchEdit() {
        connection?.endBatchEdit()
    }
}
