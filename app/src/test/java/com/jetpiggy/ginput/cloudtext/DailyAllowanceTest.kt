package com.jetpiggy.ginput.cloudtext

import com.jetpiggy.ginput.cloudtext.DailyAllowance.Companion.LIMIT
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyAllowanceTest {

    private val today = LocalDate.of(2026, 10, 11)

    @Test
    fun fiftyUsesADay() {
        assertEquals(50, LIMIT)
        assertEquals(LIMIT, DailyAllowance().remaining(today))
        val used = (1..LIMIT).fold(DailyAllowance()) { allowance, _ -> allowance.use(today) }
        assertEquals(DailyAllowance(today, LIMIT), used)
        assertEquals(0, used.remaining(today))
        assertEquals(0, used.use(today).remaining(today))
    }

    @Test
    fun countStartsAgainTheNextDay() {
        val tomorrow = today.plusDays(1)
        val allowance = DailyAllowance(today, LIMIT)
        assertEquals(LIMIT, allowance.remaining(tomorrow))
        assertEquals(DailyAllowance(tomorrow, 1), allowance.use(tomorrow))
    }

    @Test
    fun survivesStorage() {
        val allowance = DailyAllowance(today, 12)
        assertEquals(allowance, DailyAllowance.parse(allowance.format()))
        assertEquals(DailyAllowance(), DailyAllowance.parse(DailyAllowance().format()))
    }

    @Test
    fun unreadableFileCountsAsUnused() {
        val broken = listOf("", "2026-10-11", "2026-10-11\tx", "2026-10-11\t-1", "11/10/2026\t3", "2026-10-11\t3\t4")
        for (text in broken) assertEquals(text, DailyAllowance(), DailyAllowance.parse(text))
    }
}
