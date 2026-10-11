package com.jetpiggy.ginput.usagestats

import java.time.LocalDate
import java.util.EnumMap
import java.util.TreeMap

/**
 * 每日各功能的使用次數，日子用手機的本地日期。未送出的日子最多留 KEEP_DAYS 日。
 * 所有方法都要在同一個執行緒呼叫。
 */
class UsageCounts {

    private val days = TreeMap<LocalDate, EnumMap<UsageEvent, Int>>()

    fun record(day: LocalDate, event: UsageEvent, times: Int = 1) {
        val counts = days.getOrPut(day) { EnumMap(UsageEvent::class.java) }
        counts[event] = (counts[event] ?: 0) + times
    }

    /** 加入另一份記錄（例如檔案載入完成前已經記下的次數）。 */
    fun addAll(other: UsageCounts) {
        for ((day, counts) in other.days) {
            for ((event, times) in counts) record(day, event, times)
        }
    }

    /** today 之前、可以送出的日子，由舊到新；太舊的日子同時刪去。 */
    fun finishedDays(today: LocalDate): List<Pair<LocalDate, Map<UsageEvent, Int>>> {
        days.headMap(today.minusDays(KEEP_DAYS.toLong())).clear()
        return days.headMap(today).map { (day, counts) -> day to counts.toMap() }
    }

    fun remove(day: LocalDate) {
        days.remove(day)
    }

    fun clear() = days.clear()

    fun isEmpty(): Boolean = days.isEmpty()

    /** 每行是「日期<Tab>代號<Tab>次數」。 */
    fun format(): String = buildString {
        for ((day, counts) in days) {
            for ((event, times) in counts) append(day).append('\t').append(event.code).append('\t').append(times).append('\n')
        }
    }

    companion object {
        const val KEEP_DAYS = 7

        /** 讀 format 的結果；看不懂的行略過。 */
        fun parse(text: String): UsageCounts {
            val counts = UsageCounts()
            for (line in text.lineSequence()) {
                val parts = line.split('\t')
                if (parts.size != 3) continue
                val day = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: continue
                val event = UsageEvent.entries.firstOrNull { it.code == parts[1] } ?: continue
                val times = parts[2].toIntOrNull()?.takeIf { it > 0 } ?: continue
                counts.record(day, event, times)
            }
            return counts
        }
    }
}
