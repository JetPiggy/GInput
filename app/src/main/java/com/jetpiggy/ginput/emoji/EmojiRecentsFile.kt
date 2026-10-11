package com.jetpiggy.ginput.emoji

import android.content.Context
import java.io.File

/**
 * 「最近使用」的 Emoji，放在 noBackupFilesDir，不會隨 Android 備份離開手機。
 * 每行一個，最近用的在前。讀寫都會碰檔案，不要在主執行緒呼叫。
 */
object EmojiRecentsFile {

    const val MAX_RECENTS = 32

    private fun file(context: Context) = File(File(context.noBackupFilesDir, "emoji"), "recent.txt")

    fun load(context: Context): List<String> {
        val file = file(context)
        return if (file.exists()) file.readLines().filter { it.isNotEmpty() }.take(MAX_RECENTS) else emptyList()
    }

    fun save(context: Context, recents: List<String>) {
        val file = file(context)
        val dir = checkNotNull(file.parentFile)
        dir.mkdirs()
        val partial = File(dir, "${file.name}.partial")
        partial.writeText(recents.joinToString("") { "$it\n" })
        check(partial.renameTo(file)) { "無法儲存最近使用的 Emoji" }
    }
}
