package com.jetpiggy.ginput.dictionary

import android.content.Context
import com.jetpiggy.ginput.engine.LearnedPair
import java.io.File

/**
 * 搭配記憶的檔案，放在 noBackupFilesDir，不會隨 Android 備份離開手機（ADR 0001）。
 * 每行是「前一個字<Tab>後一個字<Tab>次數」，由舊到新。讀寫都會碰檔案，不要在主執行緒呼叫。
 * 「清除全部記憶」和記憶字共用 CandidateMemoryFile.clears。
 */
object PairMemoryFile {

    private fun file(context: Context) = File(File(context.noBackupFilesDir, "memory"), "pairs.tsv")

    fun load(context: Context): List<LearnedPair> {
        val file = file(context)
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            val parts = line.split('\t')
            val count = parts.getOrNull(2)?.toIntOrNull()
            if (parts.size == 3 && parts[0].isNotEmpty() && parts[1].isNotEmpty() && count != null && count > 0) {
                LearnedPair(parts[0], parts[1], count)
            } else {
                null
            }
        }
    }

    fun save(context: Context, pairs: List<LearnedPair>) {
        val file = file(context)
        val dir = checkNotNull(file.parentFile)
        dir.mkdirs()
        val partial = File(dir, "${file.name}.partial")
        partial.writeText(pairs.joinToString("") { "${it.previous}\t${it.next}\t${it.count}\n" })
        check(partial.renameTo(file)) { "無法儲存搭配記憶" }
    }

    fun delete(context: Context) {
        file(context).delete()
    }
}
