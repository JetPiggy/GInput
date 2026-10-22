package com.jetpiggy.ginput.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.jetpiggy.ginput.engine.SqlDictionary
import java.io.File

/**
 * App 內置的字庫（由 tools/dictgen 建置）：主字庫和英文詞庫是兩個檔案，因為英文詞庫以
 * CC BY-SA 4.0 授權（ADR 0004）。Android 不能直接開啟 assets 裏的 SQLite，所以第一次使用時
 * 複製到 noBackupFilesDir，再以唯讀方式開啟。
 */
object BundledDictionary {

    /** 要和 tools/dictgen/build_dict.py 的 DICTIONARY_VERSION 一致。 */
    private const val MAIN_FILE = "ginput-6.db"

    /** 要和 tools/dictgen/build_english.py 的 ENGLISH_VERSION 一致。 */
    private const val ENGLISH_FILE = "english-1.db"

    private const val ASSET_DIR = "dict"

    /** 會讀寫檔案，不要在主執行緒呼叫。 */
    fun open(context: Context): SqlDictionary {
        val dir = File(context.noBackupFilesDir, ASSET_DIR)
        dir.mkdirs()
        // 刪掉舊版本的字庫；現行版本上次未完成的複製會在 install 時覆蓋。
        dir.listFiles()
            ?.filter { file -> listOf(MAIN_FILE, ENGLISH_FILE).none { file.name.startsWith(it) } }
            ?.forEach { it.delete() }
        return SqlDictionary(
            sql = AndroidSqlRunner(openReadOnly(install(context, dir, MAIN_FILE))),
            english = AndroidSqlRunner(openReadOnly(install(context, dir, ENGLISH_FILE))),
        )
    }

    private fun openReadOnly(file: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(
            file.path,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        )

    private fun install(context: Context, dir: File, name: String): File {
        val target = File(dir, name)
        if (target.exists()) return target
        val partial = File(dir, "$name.partial")
        context.assets.open("$ASSET_DIR/$name").use { input ->
            partial.outputStream().use { output -> input.copyTo(output) }
        }
        check(partial.renameTo(target)) { "無法安裝字庫：$target" }
        return target
    }
}
