package com.jetpiggy.ginput.cloudtext

import android.content.Context
import java.io.File
import java.time.LocalDate

/**
 * 每日次數（ADR 0006）：每部手機每日可以用雲端翻譯和改寫合共 LIMIT 次，按手機的本地日期，只在手機上數。
 * used 是 day 那日成功了幾次；失敗的不計。
 */
data class DailyAllowance(val day: LocalDate? = null, val used: Int = 0) {

    fun remaining(today: LocalDate): Int = if (day == today) (LIMIT - used).coerceAtLeast(0) else LIMIT

    /** 成功一次。換了日子就重新數。 */
    fun use(today: LocalDate): DailyAllowance = if (day == today) copy(used = used + 1) else DailyAllowance(today, 1)

    /** 儲存格式：日期和次數，中間用 Tab 分隔。 */
    fun format(): String = if (day == null) "" else "$day\t$used"

    companion object {
        const val LIMIT = 50

        /** 讀不懂就當作未用過。 */
        fun parse(text: String): DailyAllowance {
            val parts = text.trim().split('\t')
            if (parts.size != 2) return DailyAllowance()
            val day = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return DailyAllowance()
            val used = parts[1].toIntOrNull()?.takeIf { it >= 0 } ?: return DailyAllowance()
            return DailyAllowance(day, used)
        }
    }
}

/** 每日次數的檔案，放在 noBackupFilesDir，不隨 Android 備份。讀寫都會碰檔案，不要在主執行緒呼叫。 */
object DailyAllowanceFile {

    private fun file(context: Context) = File(File(context.noBackupFilesDir, "cloudtext"), "allowance.tsv")

    fun load(context: Context): DailyAllowance {
        val file = file(context)
        if (!file.exists()) return DailyAllowance()
        return runCatching { DailyAllowance.parse(file.readText()) }.getOrDefault(DailyAllowance())
    }

    fun save(context: Context, allowance: DailyAllowance) {
        val file = file(context)
        val dir = checkNotNull(file.parentFile)
        dir.mkdirs()
        val partial = File(dir, "${file.name}.partial")
        partial.writeText(allowance.format())
        check(partial.renameTo(file)) { "無法儲存每日次數" }
    }
}
