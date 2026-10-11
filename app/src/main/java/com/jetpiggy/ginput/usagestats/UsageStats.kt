package com.jetpiggy.ginput.usagestats

import android.content.Context
import android.os.Build
import android.util.Log
import com.jetpiggy.ginput.BuildConfig
import com.jetpiggy.ginput.settings.SettingsPreferences
import com.jetpiggy.ginput.settings.SettingsStore
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 使用統計（ADR 0001）：使用者同意後，數每日各功能的使用次數，翌日起經 backend/usage-stats 送出，
 * 每日一份。不記錄輸入內容，也沒有任何識別碼。次數存在 noBackupFilesDir，不會隨 Android 備份。
 * 除了在背景讀寫檔案和連線，所有方法都在主執行緒呼叫。
 */
class UsageStats(
    private val context: Context,
    private val store: SettingsStore,
    private val scope: CoroutineScope,
    private val today: () -> LocalDate = LocalDate::now,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var counts = UsageCounts()
    private var sending = false
    private var lastAttempt = 0L

    private val enabled: Boolean
        get() = store.settings.value.usageStats

    private val file: File
        get() = File(File(context.noBackupFilesDir, "usage"), "counts.tsv")

    /** 載入上次未送出的次數；使用統計關了就刪掉。 */
    fun load() {
        if (!enabled) {
            clear()
            return
        }
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { if (file.exists()) UsageCounts.parse(file.readText()) else UsageCounts() }
                    .onFailure { Log.w(TAG, "無法讀取使用統計：${it.javaClass.simpleName}") }
                    .getOrDefault(UsageCounts())
            }
            saved.addAll(counts)
            counts = saved
        }
    }

    fun record(event: UsageEvent) {
        if (enabled) counts.record(today(), event)
    }

    /** 鍵盤收起時寫入檔案。 */
    fun save() {
        if (!enabled) return
        val text = counts.format()
        scope.launch(Dispatchers.IO) { write(text) }
    }

    /** 使用者關了使用統計：刪掉全部未送出的次數。 */
    fun clear() {
        counts.clear()
        scope.launch(Dispatchers.IO) { file.delete() }
    }

    /** 有已完結、未送出的日子就送出；上次失敗的話，隔一段時間才再試。 */
    fun sendIfDue() {
        if (!enabled || sending || BuildConfig.USAGE_STATS_URL.isBlank()) return
        if (clock() - lastAttempt < RETRY_MILLIS) return
        val days = counts.finishedDays(today())
        if (days.isEmpty()) return
        sending = true
        lastAttempt = clock()
        val settings = SettingsPreferences.forUsageStats(store.settings.value)
        scope.launch {
            for ((day, dayCounts) in days) {
                val report = UsageReport.json(day, dayCounts, settings, BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT)
                val result = withContext(Dispatchers.IO) { post(report) }
                // 伺服器拒絕（4xx）的報告重送也不會成功，所以和成功一樣刪掉。
                if (result == Result.Failed) break
                counts.remove(day)
            }
            sending = false
            if (enabled) save() else clear()
        }
    }

    private fun write(text: String) {
        runCatching {
            val target = file
            val dir = checkNotNull(target.parentFile)
            dir.mkdirs()
            val partial = File(dir, "${target.name}.partial")
            partial.writeText(text)
            check(partial.renameTo(target)) { "無法儲存使用統計" }
        }.onFailure { Log.w(TAG, "無法儲存使用統計：${it.javaClass.simpleName}") }
    }

    private enum class Result { Delivered, Rejected, Failed }

    private fun post(body: String): Result {
        val connection = URL(BuildConfig.USAGE_STATS_URL).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray()) }
            when (connection.responseCode) {
                in 200..299 -> Result.Delivered
                in 400..499 -> Result.Rejected
                else -> Result.Failed
            }
        } catch (e: IOException) {
            Log.w(TAG, "使用統計未能送出：${e.javaClass.simpleName}")
            Result.Failed
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "GInput"
        const val TIMEOUT_MILLIS = 10_000
        const val RETRY_MILLIS = 60 * 60 * 1000L
    }
}
