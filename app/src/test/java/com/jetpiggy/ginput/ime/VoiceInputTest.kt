package com.jetpiggy.ginput.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceInputTest {

    @Test
    fun googleVoiceTypingComesFirst() {
        val google = voiceInputRank("com.google.android.tts", hasVoiceSubtype = true)!!
        val oldGoogle = voiceInputRank("com.google.android.googlequicksearchbox", hasVoiceSubtype = true)!!
        val other = voiceInputRank("com.samsung.android.svoiceime", hasVoiceSubtype = true)!!
        assertEquals(google, oldGoogle)
        assertTrue(google < other)
    }

    @Test
    fun voiceInputMethodsBeatKeyboardsWithAVoiceKey() {
        val voice = voiceInputRank("com.example.voice", hasVoiceSubtype = true)!!
        val keyboard = voiceInputRank("com.iflytek.inputmethod", hasVoiceSubtype = false)!!
        assertTrue(voice < keyboard)
    }

    @Test
    fun brandedVersionsOfChineseKeyboardsCount() {
        for (packageName in listOf(
            "com.iflytek.inputmethod.miui",
            "com.sohu.inputmethod.sogou.xiaomi",
            "com.baidu.input_mi",
            "com.google.android.inputmethod.latin",
        )) {
            assertTrue(packageName, voiceInputRank(packageName, hasVoiceSubtype = false) != null)
        }
    }

    @Test
    fun chineseKeyboardsBeatGboard() {
        val iflytek = voiceInputRank("com.iflytek.inputmethod.miui", hasVoiceSubtype = false)!!
        val gboard = voiceInputRank("com.google.android.inputmethod.latin", hasVoiceSubtype = false)!!
        assertTrue(iflytek < gboard)
    }

    @Test
    fun otherKeyboardsCannotBeUsed() {
        assertNull(voiceInputRank("com.example.keyboard", hasVoiceSubtype = false))
        assertNull(voiceInputRank("com.baidu.inputx", hasVoiceSubtype = false))
    }
}
