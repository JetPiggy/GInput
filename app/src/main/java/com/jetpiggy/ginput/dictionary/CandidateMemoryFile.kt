package com.jetpiggy.ginput.dictionary

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * 記憶字的檔案，放在 noBackupFilesDir，不會隨 Android 備份離開手機（ADR 0001）。
 * 每行是「字<Tab>次數」。讀寫都會碰檔案，不要在主執行緒呼叫。
 */
object CandidateMemoryFile {

    /**
     * 設定頁按「清除全部記憶」時送出。執行中的輸入法收到後清除手上的記憶，再把空的記憶寫回檔案；
     * 輸入法沒有執行的話，就靠 delete 刪掉的檔案。
     */
    val clears = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private fun file(context: Context) = File(File(context.noBackupFilesDir, "memory"), "candidates.tsv")

    fun load(context: Context): Map<String, Int> {
        val file = file(context)
        if (!file.exists()) return emptyMap()
        return file.readLines().mapNotNull { line ->
            val parts = line.split('\t')
            val count = parts.getOrNull(1)?.toIntOrNull()
            if (parts.size == 2 && parts[0].isNotEmpty() && count != null && count > 0) parts[0] to count else null
        }.toMap()
    }

    fun save(context: Context, counts: Map<String, Int>) {
        val file = file(context)
        val dir = checkNotNull(file.parentFile)
        dir.mkdirs()
        val partial = File(dir, "${file.name}.partial")
        partial.writeText(counts.entries.sortedBy { it.key }.joinToString("") { "${it.key}\t${it.value}\n" })
        check(partial.renameTo(file)) { "無法儲存記憶字" }
    }

    fun delete(context: Context) {
        file(context).delete()
    }
}
