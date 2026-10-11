package com.jetpiggy.ginput.typing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PunctuationTest {

    @Test
    fun fullWidthUnlessTheLastCharacterIsAnEnglishLetterOrDigit() {
        assertTrue(Punctuation.fullWidthAfter(""))
        assertTrue(Punctuation.fullWidthAfter("你好"))
        assertTrue(Punctuation.fullWidthAfter("你好，"))
        assertFalse(Punctuation.fullWidthAfter("ok"))
        assertFalse(Punctuation.fullWidthAfter("ok, "))
        assertFalse(Punctuation.fullWidthAfter("2026"))
    }

    @Test
    fun closingParenthesisMatchesTheUnclosedOpeningOne() {
        val close = PunctuationKey.CloseParenthesis
        assertTrue(Punctuation.fullWidth(close, "我哋（Peter同May"))
        assertFalse(Punctuation.fullWidth(close, "call me (你知"))
        assertTrue(Punctuation.fullWidth(close, "（a (b) c"))
        // 沒有未配對的括號，就按前一個字。
        assertFalse(Punctuation.fullWidth(close, "（你好）ok"))
        assertTrue(Punctuation.fullWidth(close, "1) 你好"))
        // 其他鍵不看括號。
        assertFalse(Punctuation.fullWidth(PunctuationKey.Exclamation, "（Peter"))
    }

    @Test
    fun symbolKeysHaveAFullWidthAndAHalfWidthForm() {
        assertEquals(PunctuationKey.Exclamation, PunctuationKey.ofHalfWidth("!"))
        assertEquals(PunctuationKey.Tilde, PunctuationKey.ofHalfWidth("~"))
        assertEquals(null, PunctuationKey.ofHalfWidth("@"))
        assertEquals("（", Punctuation.of(PunctuationKey.OpenParenthesis, fullWidth = true))
        assertEquals(";", Punctuation.of(PunctuationKey.Semicolon, fullWidth = false))
    }

    @Test
    fun relatedSymbolsPutTheWrittenOneFirst() {
        assertEquals(listOf("！", "!", "！！", "‼", "❗"), Punctuation.relatedTo("！"))
        assertEquals(listOf("!", "！", "！！", "‼", "❗"), Punctuation.relatedTo("!"))
        assertEquals(listOf("。", ".", "……", "·"), Punctuation.relatedTo("。"))
        assertEquals(listOf("'", "‘", "’", "『", "』"), Punctuation.relatedTo("'"))
        // 只有規格表左欄的符號會列出。
        assertEquals(emptyList<String>(), Punctuation.relatedTo("“"))
        assertEquals(emptyList<String>(), Punctuation.relatedTo("、"))
        assertEquals(emptyList<String>(), Punctuation.relatedTo("@"))
    }

    @Test
    fun emailAndUrlFieldsOnlyOfferDotComAfterAPeriod() {
        assertEquals(listOf(".", ".com"), Punctuation.relatedTo(".", FieldKind.Email))
        assertEquals(listOf(".", ".com"), Punctuation.relatedTo(".", FieldKind.Url))
        assertEquals(emptyList<String>(), Punctuation.relatedTo("!", FieldKind.Url))
        assertEquals(emptyList<String>(), Punctuation.relatedTo(".", FieldKind.Password))
    }

    @Test
    fun chineseAndPunctuationRemoveTheAutoSpace() {
        assertTrue(Punctuation.removesAutoSpace("明"))
        assertTrue(Punctuation.removesAutoSpace("，"))
        assertTrue(Punctuation.removesAutoSpace("……"))
        assertTrue(Punctuation.removesAutoSpace(","))
        assertTrue(Punctuation.removesAutoSpace("?"))
        assertFalse(Punctuation.removesAutoSpace("("))
        assertFalse(Punctuation.removesAutoSpace("a"))
        assertFalse(Punctuation.removesAutoSpace("😀"))
        assertFalse(Punctuation.removesAutoSpace(""))
    }
}
