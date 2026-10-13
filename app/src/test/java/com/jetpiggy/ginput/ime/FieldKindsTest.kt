package com.jetpiggy.ginput.ime

import android.text.InputType
import com.jetpiggy.ginput.typing.FieldKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldKindsTest {

    @Test
    fun textPasswordsUseThePasswordKeyboard() {
        for (variation in listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )) {
            val inputType = InputType.TYPE_CLASS_TEXT or variation
            assertTrue(isPassword(inputType))
            assertEquals(FieldKind.Password, fieldKind(inputType))
        }
    }

    @Test
    fun numberPasswordsAreAlsoPasswordsButKeepTheNumberPad() {
        val pin = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        assertTrue(isPassword(pin))
        assertEquals(FieldKind.Number, fieldKind(pin))
    }

    @Test
    fun otherFieldsAreNotPasswords() {
        // 網址欄位的 variation 和數字密碼的數值一樣，要連類別一起看。
        val url = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        assertFalse(isPassword(url))
        assertEquals(FieldKind.Url, fieldKind(url))
        assertFalse(isPassword(InputType.TYPE_CLASS_TEXT))
        assertFalse(isPassword(InputType.TYPE_CLASS_NUMBER))
        assertFalse(isPassword(InputType.TYPE_CLASS_PHONE))
        assertFalse(isPassword(InputType.TYPE_NULL))
        assertEquals(FieldKind.Text, fieldKind(InputType.TYPE_NULL))
    }

    @Test
    fun fieldsCanAskForNoMicrophoneKey() {
        assertTrue(disallowsMicrophone("com.google.android.inputmethod.latin.noMicrophoneKey"))
        assertTrue(disallowsMicrophone("com.example.foo, com.android.inputmethod.latin.noMicrophoneKey"))
        assertTrue(disallowsMicrophone("nm"))
        assertFalse(disallowsMicrophone(null))
        assertFalse(disallowsMicrophone(""))
        // 只認完整的選項，不認包含 nm 的其他字。
        assertFalse(disallowsMicrophone("com.example.nmode"))
    }
}
