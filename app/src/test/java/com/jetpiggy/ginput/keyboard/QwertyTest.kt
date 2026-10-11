package com.jetpiggy.ginput.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class QwertyTest {

    @Test
    fun radicalsFollowCangjieLetters() {
        assertEquals('日', cangjieRadical('a'))
        assertEquals('手', cangjieRadical('Q'))
        assertEquals('難', cangjieRadical('x'))
        assertEquals('重', cangjieRadical('z'))
    }

    @Test
    fun rowsCoverEveryLetterOnce() {
        assertEquals(('a'..'z').toList(), QWERTY_ROWS.joinToString("").toList().sorted())
    }
}
