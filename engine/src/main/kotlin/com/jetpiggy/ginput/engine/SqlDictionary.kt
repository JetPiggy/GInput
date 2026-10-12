package com.jetpiggy.ginput.engine

/** 執行唯讀 SQL 查詢。Android 和 JVM 測試各有自己的實作。 */
interface SqlRunner {
    fun <T> query(sql: String, args: List<String>, read: (SqlRow) -> T): List<T>
}

interface SqlRow {
    fun string(column: Int): String
    fun int(column: Int): Int
}

/**
 * 讀 tools/dictgen 建置的 SQLite 字庫；表格說明見 tools/dictgen/README.md。
 * 英文詞庫以 CC BY-SA 4.0 授權，是另一個檔案（ADR 0004），所以用另一個 SqlRunner 讀。
 */
class SqlDictionary(private val sql: SqlRunner, private val english: SqlRunner) : Dictionary {

    override fun characters(scheme: Scheme, code: String): List<CharacterEntry> =
        sql.query(CHARACTERS_SQL, listOf(scheme.databaseId.toString(), code)) { row -> characterEntry(row, 0) }

    override fun codeTable(scheme: Scheme): List<CodedCharacter> =
        sql.query(CODE_TABLE_SQL, listOf(scheme.databaseId.toString())) { row ->
            CodedCharacter(code = row.string(0), entry = characterEntry(row, 1))
        }

    private fun characterEntry(row: SqlRow, first: Int) = CharacterEntry(
        character = row.string(first),
        generation = CangjieGeneration.fromDatabase(row.int(first + 1)),
        frequency = row.int(first + 2),
        windowsRank = row.int(first + 3),
        colloquial = row.int(first + 4) != 0,
    )

    override fun characterFrequencies(): Map<String, Int> =
        sql.query(FREQUENCIES_SQL, emptyList()) { row -> row.string(0) to row.int(1) }.toMap()

    override fun allWords(): List<WordFrequency> =
        sql.query(ALL_WORDS_SQL, emptyList()) { row -> WordFrequency(row.string(0) + row.string(1), row.int(2)) }

    override fun wordsAfter(head: String): List<WordEntry> =
        sql.query(WORDS_SQL, listOf(head)) { row ->
            WordEntry(rest = row.string(0), frequency = row.int(1), colloquial = row.int(2) != 0)
        }

    // 查找鍵只有 a–z，「{」緊接「z」之後，所以 prefix 到 prefix + "{" 正好涵蓋所有以 prefix 開頭的鍵。
    override fun englishWords(prefix: String, limit: Int): List<String> =
        english.query(ENGLISH_SQL, listOf(prefix, "$prefix{", limit.toString())) { row -> row.string(0) }

    override fun hasEnglishWord(key: String): Boolean =
        english.query(ENGLISH_KEY_SQL, listOf(key)) { true }.isNotEmpty()

    override fun jyutpingSyllables(): Set<String> =
        sql.query(SYLLABLES_SQL, emptyList()) { row -> row.string(0) }.toSet()

    override fun jyutping(syllables: String): List<JyutpingEntry> =
        sql.query(JYUTPING_SQL, listOf(syllables), ::jyutpingEntry)

    // 音節只有 a–z，用空格分隔。「syllables」到「syllables {」之間正好是 syllables 本身和以「syllables 」開頭的，
    // 不包括 syllables 後面直接接字母的（例如 nei 不會找到 neoi）。
    override fun jyutpingStartingWith(syllables: String): List<JyutpingEntry> =
        sql.query(JYUTPING_RANGE_SQL, listOf(syllables, "$syllables {"), ::jyutpingEntry)

    private fun jyutpingEntry(row: SqlRow) = JyutpingEntry(
        text = row.string(0),
        syllables = row.string(1),
        frequency = row.int(2),
        colloquial = row.int(3) != 0,
    )

    private companion object {
        const val CHARACTERS_SQL =
            "SELECT c.ch, k.generation, c.frequency, c.windows_rank, c.colloquial " +
                "FROM codes AS k JOIN chars AS c ON c.id = k.char_id " +
                "WHERE k.scheme = ? AND k.code = ?"
        const val CODE_TABLE_SQL =
            "SELECT k.code, c.ch, k.generation, c.frequency, c.windows_rank, c.colloquial " +
                "FROM codes AS k JOIN chars AS c ON c.id = k.char_id " +
                "WHERE k.scheme = ?"
        const val FREQUENCIES_SQL = "SELECT ch, frequency FROM chars"
        const val WORDS_SQL = "SELECT rest, frequency, colloquial FROM words WHERE head = ?"
        const val ALL_WORDS_SQL = "SELECT head, rest, frequency FROM words"
        const val SYLLABLES_SQL = "SELECT DISTINCT syllables FROM jyutping WHERE instr(syllables, ' ') = 0"
        const val JYUTPING_SQL = "SELECT text, syllables, frequency, colloquial FROM jyutping WHERE syllables = ?"
        const val JYUTPING_RANGE_SQL =
            "SELECT text, syllables, frequency, colloquial FROM jyutping WHERE syllables >= ? AND syllables < ?"
        const val ENGLISH_SQL =
            "SELECT word FROM english WHERE key >= ? AND key < ? " +
                "ORDER BY frequency, length(word), word LIMIT CAST(? AS INTEGER)"
        const val ENGLISH_KEY_SQL = "SELECT 1 FROM english WHERE key = ? LIMIT 1"
    }
}
