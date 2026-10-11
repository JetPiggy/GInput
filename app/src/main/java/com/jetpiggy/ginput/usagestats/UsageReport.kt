package com.jetpiggy.ginput.usagestats

import java.time.LocalDate

/**
 * 一日的使用統計報告（JSON），送往 backend/usage-stats。沒有任何識別碼：
 * 只有日期、App 和 Android 版本、主要設定和各功能的次數。
 */
internal object UsageReport {

    const val SCHEMA = 1

    fun json(
        day: LocalDate,
        counts: Map<UsageEvent, Int>,
        settings: Map<String, Any>,
        appVersion: String,
        sdk: Int,
    ): String = buildString {
        append("{\"schema\":").append(SCHEMA)
        append(",\"day\":").append(quote(day.toString()))
        append(",\"app\":").append(quote(appVersion))
        append(",\"sdk\":").append(sdk)
        append(",\"settings\":{")
        settings.entries.joinTo(this, ",") { (key, value) -> quote(key) + ":" + literal(value) }
        append("},\"counts\":{")
        counts.filterValues { it > 0 }.entries.joinTo(this, ",") { (event, times) -> quote(event.code) + ":" + times }
        append("}}")
    }

    private fun literal(value: Any): String = when (value) {
        is Boolean, is Int -> value.toString()
        else -> quote(value.toString())
    }

    private fun quote(text: String): String = buildString {
        append('"')
        for (ch in text) {
            when {
                ch == '"' || ch == '\\' -> append('\\').append(ch)
                ch < ' ' -> append("\\u%04x".format(ch.code))
                else -> append(ch)
            }
        }
        append('"')
    }
}
