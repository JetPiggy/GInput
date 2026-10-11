package com.jetpiggy.ginput.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldKindsTest {

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
