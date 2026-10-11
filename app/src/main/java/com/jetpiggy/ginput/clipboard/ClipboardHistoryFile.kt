package com.jetpiggy.ginput.clipboard

import android.content.Context
import java.io.File

/**
 * 剪貼簿歷史的檔案，放在 noBackupFilesDir，不會隨 Android 備份離開手機（ADR 0001）。
 * 內容是 ClipboardHistory.toJson()。讀寫都會碰檔案，不要在主執行緒呼叫。
 */
object ClipboardHistoryFile {

    private fun file(context: Context) = File(File(context.noBackupFilesDir, "clipboard"), "history.json")

    /** 讀不到或格式不對就當作沒有。例外的訊息可能帶有複製的文字，所以不記錄。 */
    fun load(context: Context): ClipboardHistory {
        val file = file(context)
        if (!file.exists()) return ClipboardHistory()
        return runCatching { ClipboardHistory.fromJson(file.readText()) }.getOrDefault(ClipboardHistory())
    }

    /** 沒有項目、也未見過任何複製（例如關了剪貼簿歷史）就刪掉檔案。 */
    fun save(context: Context, history: ClipboardHistory) {
        if (history == ClipboardHistory()) {
            delete(context)
            return
        }
        val file = file(context)
        val dir = checkNotNull(file.parentFile)
        dir.mkdirs()
        val partial = File(dir, "${file.name}.partial")
        partial.writeText(history.toJson())
        check(partial.renameTo(file)) { "無法儲存剪貼簿歷史" }
    }

    fun delete(context: Context) {
        file(context).delete()
    }
}
