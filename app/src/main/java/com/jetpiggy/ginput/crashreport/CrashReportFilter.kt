package com.jetpiggy.ginput.crashreport

import io.sentry.SentryEvent

/**
 * 當機回報送出前的過濾（ADR 0001）：只留例外的類型、堆疊和裝置資料。
 * 例外訊息、日誌訊息和額外資料都可能帶有輸入內容，所以一律刪去，不靠 SDK 的預設值。
 */
internal object CrashReportFilter {

    fun scrub(event: SentryEvent): SentryEvent {
        event.exceptions?.forEach { exception ->
            exception.value = null
            exception.mechanism?.data = null
        }
        event.message = null
        event.breadcrumbs = null
        event.extras = null
        // Sentry 預設用安裝 ID 做使用者，那也是識別碼。
        event.user = null
        event.serverName = null
        event.request = null
        return event
    }
}
