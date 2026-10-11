package com.jetpiggy.ginput.engine

import java.sql.Connection

/** 在 JVM 測試裏用 sqlite-jdbc 讀字庫。 */
class JdbcSqlRunner(private val connection: Connection) : SqlRunner {
    override fun <T> query(sql: String, args: List<String>, read: (SqlRow) -> T): List<T> =
        connection.prepareStatement(sql).use { statement ->
            args.forEachIndexed { index, value -> statement.setString(index + 1, value) }
            statement.executeQuery().use { result ->
                val row = object : SqlRow {
                    override fun string(column: Int): String = result.getString(column + 1)
                    override fun int(column: Int): Int = result.getInt(column + 1)
                }
                buildList { while (result.next()) add(read(row)) }
            }
        }
}
