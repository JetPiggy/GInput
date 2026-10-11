package com.jetpiggy.ginput.dictionary

import android.database.sqlite.SQLiteDatabase
import com.jetpiggy.ginput.engine.SqlRow
import com.jetpiggy.ginput.engine.SqlRunner

class AndroidSqlRunner(private val database: SQLiteDatabase) : SqlRunner {
    override fun <T> query(sql: String, args: List<String>, read: (SqlRow) -> T): List<T> =
        database.rawQuery(sql, args.toTypedArray()).use { cursor ->
            val row = object : SqlRow {
                override fun string(column: Int): String = cursor.getString(column)
                override fun int(column: Int): Int = cursor.getInt(column)
            }
            buildList { while (cursor.moveToNext()) add(read(row)) }
        }
}
