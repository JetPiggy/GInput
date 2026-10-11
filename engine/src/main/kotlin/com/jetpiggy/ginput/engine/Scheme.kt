package com.jetpiggy.ginput.engine

/**
 * 輸入方案。databaseId 是字庫 codes 表用的代號，和字庫建置工具（tools/dictgen）一致；
 * 粵拼不查 codes 表，另有 jyutping 表。
 */
enum class Scheme(internal val databaseId: Int) {
    Cangjie(1),
    Quick(2),
    Jyutping(3),
}
