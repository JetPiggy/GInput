package com.jetpiggy.ginput.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiKeywordsTest {

    private val keywords = EmojiKeywords(
        mapOf(
            "心" to listOf("❤️"),
            "開心" to listOf("😊", "😄", "🥰", "😆"),
            "唔開心" to listOf("😞"),
        ),
    )

    @Test
    fun longestKeywordWins() {
        assertEquals(listOf("😞"), keywords.emojiAfter("我唔開心"))
        assertEquals(listOf("😊", "😄", "🥰"), keywords.emojiAfter("好開心"))
        assertEquals(listOf("❤️"), keywords.emojiAfter("真心"))
    }

    @Test
    fun onlyTheEndOfTheTextCounts() {
        assertTrue(keywords.emojiAfter("開心果").isEmpty())
        assertTrue(keywords.emojiAfter("").isEmpty())
    }

    @Test
    fun limitsTheNumberOfEmoji() {
        assertEquals(listOf("😊"), keywords.emojiAfter("開心", limit = 1))
    }

    @Test
    fun parsingSkipsCommentsAndEmojiThePhoneCannotShow() {
        val parsed = EmojiKeywords.parse(
            sequenceOf("# 關鍵字<Tab>Emoji", "", "開心\t😊 🫨 😄", "震\t🫨"),
            displayable = { it != "🫨" },
        )
        assertEquals(listOf("😊", "😄"), parsed.emojiAfter("開心"))
        assertTrue(parsed.emojiAfter("震").isEmpty())
    }

    /** App 實際內置的 keywords.tsv（tools/dictgen/build_emoji.py 建置）。 */
    @Test
    fun bundledKeywordsFollowTheCuratedLists() {
        val path = checkNotNull(System.getProperty("ginput.emojiKeywords")) { "要經 Gradle 執行，才會帶入檔案路徑" }
        val bundled = File(path).useLines { EmojiKeywords.parse(it) }
        // 自行整理的清單取代 CLDR 的 Emoji 和次序。
        assertEquals("😊", bundled.emojiAfter("我好開心呀開心").first())
        assertEquals("😞", bundled.emojiAfter("唔開心").first())
        assertEquals("😂", bundled.emojiAfter("笑").first())
        // CLDR 的單字關鍵字不用；太常用的詞不作關鍵字。
        assertTrue(bundled.emojiAfter("上").isEmpty())
        assertTrue(bundled.emojiAfter("可以").isEmpty())
        // 排在中性版本後面的男、女版本不列。
        assertEquals(listOf("🧑‍🏫"), bundled.emojiAfter("老師"))
    }
}
