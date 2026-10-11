package com.jetpiggy.ginput.crashreport

import io.sentry.Breadcrumb
import io.sentry.SentryEvent
import io.sentry.protocol.Mechanism
import io.sentry.protocol.Message
import io.sentry.protocol.SentryException
import io.sentry.protocol.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CrashReportFilterTest {

    @Test
    fun onlyTheTypeAndStackSurvive() {
        val exception = SentryException().apply {
            type = "IllegalArgumentException"
            value = "不是英文字母：我"
            mechanism = Mechanism().apply { data = mapOf("typed" to "我") }
        }
        val event = SentryEvent().apply {
            exceptions = listOf(exception)
            message = Message().apply { formatted = "打了：你好" }
            breadcrumbs = mutableListOf(Breadcrumb("組字 neihou"))
            setExtra("composition", "neihou")
            user = User().apply { id = "installation-id" }
            serverName = "phone"
        }

        val scrubbed = CrashReportFilter.scrub(event)

        val kept = scrubbed.exceptions!!.single()
        assertEquals("IllegalArgumentException", kept.type)
        assertNull(kept.value)
        assertNull(kept.mechanism!!.data)
        assertNull(scrubbed.message)
        assertNull(scrubbed.breadcrumbs)
        assertNull(scrubbed.extras)
        assertNull(scrubbed.user)
        assertNull(scrubbed.serverName)
    }
}
