package com.jetpiggy.ginput.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JyutpingSpellerTest {

    private val speller = JyutpingSpeller(
        setOf("nei", "lei", "hou", "maa", "sing", "si", "sin", "ng", "jau", "zau", "hoeng", "gong", "m", "goi"),
    )

    private fun typed(input: String, loose: Boolean = true) =
        speller.split(input, loose).map { split -> split.syllables.map { it.typed } to split.complete }

    @Test
    fun splitsIntoSyllables() {
        assertEquals(listOf(listOf("nei", "hou", "maa") to true), typed("neihoumaa"))
        assertEquals(listOf(listOf("m", "goi") to true), typed("mgoi"))
    }

    @Test
    fun keepsOnlyTheSplitsWithTheFewestSyllables() {
        // sing 也可以拆成 si + ng，但音節較多。
        assertEquals(listOf(listOf("sing") to true), typed("sing"))
    }

    @Test
    fun lastSyllableMayBeUnfinished() {
        val split = speller.split("neih", looseSpelling = false).single()
        assertFalse(split.complete)
        assertEquals(listOf("nei", "h"), split.syllables.map { it.typed })
        assertEquals(setOf("hou", "hoeng"), split.syllables.last().syllables)
    }

    @Test
    fun aLoneUnfinishedSyllableOrNonJyutpingDoesNotSplit() {
        assertEquals(emptyList<Any>(), typed("n"))
        assertEquals(emptyList<Any>(), typed("hello"))
    }

    @Test
    fun looseSpellingOnlyWhenEnabled() {
        assertEquals(setOf("lei", "nei"), speller.split("lei", looseSpelling = true).single().syllables.single().syllables)
        assertEquals(setOf("lei"), speller.split("lei", looseSpelling = false).single().syllables.single().syllables)
        assertEquals(setOf("jau"), speller.split("yau", looseSpelling = true).single().syllables.single().syllables)
        assertTrue(speller.split("yau", looseSpelling = false).isEmpty())
    }

    @Test
    fun lazyPronunciations() {
        assertTrue("lei" in JyutpingSpeller.looseSpellings("nei"))
        assertTrue("nei" in JyutpingSpeller.looseSpellings("lei"))
        assertTrue("o" in JyutpingSpeller.looseSpellings("ngo"))
        assertTrue("ngoi" in JyutpingSpeller.looseSpellings("oi"))
        assertTrue("gong" in JyutpingSpeller.looseSpellings("gwong"))
        assertTrue("kok" in JyutpingSpeller.looseSpellings("kwok"))
        // gw、kw 只在 o 前面讀成 g、k。
        assertFalse("gai" in JyutpingSpeller.looseSpellings("gwai"))
    }

    @Test
    fun hongKongSpellings() {
        assertTrue("yau" in JyutpingSpeller.looseSpellings("jau"))
        assertTrue(JyutpingSpeller.looseSpellings("zoeng").containsAll(listOf("cheung", "jeung", "tseung")))
        assertTrue(JyutpingSpeller.looseSpellings("coi").containsAll(listOf("choi", "tsoi", "choy")))
        assertTrue("shui" in JyutpingSpeller.looseSpellings("seoi"))
        assertTrue("chun" in JyutpingSpeller.looseSpellings("ceon"))
        assertTrue("san" in JyutpingSpeller.looseSpellings("saan"))
        assertTrue("ga" in JyutpingSpeller.looseSpellings("gaa"))
        assertTrue("ho" in JyutpingSpeller.looseSpellings("hou"))
        assertTrue("mm" in JyutpingSpeller.looseSpellings("m"))
        assertTrue(JyutpingSpeller.looseSpellings("si").containsAll(listOf("see", "sz", "sze", "shi")))
        assertTrue("tsz" in JyutpingSpeller.looseSpellings("zi"))
        assertTrue("suen" in JyutpingSpeller.looseSpellings("syun"))
        assertTrue("yeh" in JyutpingSpeller.looseSpellings("je"))
    }

    @Test
    fun englishSpellings() {
        assertTrue("arm" in JyutpingSpeller.looseSpellings("ngaam"))
        assertTrue("char" in JyutpingSpeller.looseSpellings("caa"))
        assertTrue("jor" in JyutpingSpeller.looseSpellings("zo"))
        assertTrue("lor" in JyutpingSpeller.looseSpellings("lo"))
        assertTrue("mer" in JyutpingSpeller.looseSpellings("me"))
        assertTrue("her" in JyutpingSpeller.looseSpellings("hoe"))
        assertTrue(JyutpingSpeller.looseSpellings("nei").containsAll(listOf("nay", "lay")))
        assertTrue("gum" in JyutpingSpeller.looseSpellings("gam"))
        assertTrue("yum" in JyutpingSpeller.looseSpellings("jam"))
        assertTrue("sup" in JyutpingSpeller.looseSpellings("sap"))
        assertTrue("bao" in JyutpingSpeller.looseSpellings("baau"))
    }

    @Test
    fun englishSpellingsCostMoreThanHongKongSpellings() {
        val costs = JyutpingSpeller.looseSpellingCosts("ngaam")
        assertEquals(JyutpingSpeller.HONG_KONG, costs["am"])
        // 聲母和韻母的寫法一起用時，取代價較高的一個。
        assertEquals(JyutpingSpeller.ENGLISH, costs["arm"])
        assertEquals(JyutpingSpeller.ENGLISH, costs["ngarm"])
        assertEquals(JyutpingSpeller.HONG_KONG, JyutpingSpeller.looseSpellingCosts("zo")["jo"])
        assertEquals(JyutpingSpeller.ENGLISH, JyutpingSpeller.looseSpellingCosts("zo")["jor"])
    }

    @Test
    fun splitKeepsTheCheapestCostOfEachSyllable() {
        val speller = JyutpingSpeller(setOf("ngaam", "zo", "zou"))
        assertEquals(mapOf("ngaam" to JyutpingSpeller.ENGLISH), speller.split("arm", looseSpelling = true).single().syllables.single().costs)
        assertTrue(speller.split("arm", looseSpelling = false).isEmpty())
        assertEquals(
            mapOf("zo" to JyutpingSpeller.HONG_KONG, "zou" to JyutpingSpeller.HONG_KONG),
            speller.split("jo", looseSpelling = true).single().syllables.single().costs,
        )
    }

    @Test
    fun englishSpellingCanBeTurnedOffSeparately() {
        val speller = JyutpingSpeller(setOf("ngaam", "zo", "zou"))
        assertTrue(speller.split("arm", looseSpelling = true, englishSpelling = false).isEmpty())
        assertTrue(speller.split("jor", looseSpelling = true, englishSpelling = false).isEmpty())
        // 懶音和港式拼法照用，包括打到一半的音節。
        assertEquals(setOf("zo", "zou"), speller.split("jo", looseSpelling = true, englishSpelling = false).single().syllables.single().syllables)
        assertEquals(setOf("ngaam"), speller.split("am", looseSpelling = true, englishSpelling = false).single().syllables.single().syllables)
        // 打到一半的 ar 只可能是英式拼法（arm）。
        assertTrue(speller.split("zoar", looseSpelling = true, englishSpelling = false).isEmpty())
        assertEquals(1, speller.split("zoar", looseSpelling = true).size)
    }

    @Test
    fun jBecomesYWithoutDoublingBeforeYu() {
        val spellings = JyutpingSpeller.looseSpellings("jyut")
        assertTrue(spellings.containsAll(listOf("yut", "yuet")))
        assertTrue(spellings.none { "yy" in it })
        assertFalse("jyut" in spellings)
    }
}
