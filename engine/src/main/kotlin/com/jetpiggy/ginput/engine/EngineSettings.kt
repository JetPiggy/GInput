package com.jetpiggy.ginput.engine

/** 倉頡版本。速成碼也由使用者容許的版本推導。 */
enum class CangjieGeneration(internal val databaseNumber: Int) {
    Third(3),
    Fifth(5);

    internal companion object {
        fun fromDatabase(number: Int): CangjieGeneration =
            entries.firstOrNull { it.databaseNumber == number }
                ?: error("字庫裏有未知的倉頡版本：$number")
    }
}

/** 速成和倉頡重碼的候選字怎樣排。粵拼沒有 Windows 次序，一律按常用度。 */
enum class SharedCodeOrder {
    /** 常用度次序 */
    Frequency,

    /** Windows 次序 */
    Windows,
}

data class EngineSettings(
    /** 已啟用的輸入方案，按輸入方案次序排列。 */
    val schemes: List<Scheme> = listOf(Scheme.Quick),
    val cangjieGenerations: Set<CangjieGeneration> = CangjieGeneration.entries.toSet(),
    val sharedCodeOrder: SharedCodeOrder = SharedCodeOrder.Frequency,
    val colloquialPriority: Boolean = true,
    /** 記憶字排到最前；只在常用度次序下生效。 */
    val rememberCandidates: Boolean = true,
    /** 寬鬆拼法：粵拼也接受懶音和港式拼法。 */
    val looseSpelling: Boolean = true,
    /** 連打：速成和倉頡可以連續打幾個字的碼，出整句候選；預設關閉。 */
    val continuousTyping: Boolean = false,
)
