package com.jetpiggy.ginput.cloudtext

/** 工具列的「翻譯」和「改寫」，各自打開翻譯面板和改寫面板。 */
enum class CloudTool { Translation, Rewrite }

/**
 * 雲端翻譯和改寫的工作，即兩個面板裏的選項（規則見 docs/spec/v1.md 的「雲端翻譯和改寫」）。
 * code 和 backend/ai 的 TASKS 一致；加減項目要兩邊一起改。
 */
enum class TextTask(val code: String, val tool: CloudTool) {
    ToEnglish("to-en", CloudTool.Translation),
    ToWritten("to-zh", CloudTool.Translation),
    ToColloquial("to-yue", CloudTool.Translation),
    Written("written", CloudTool.Rewrite),
    Colloquial("colloquial", CloudTool.Rewrite),
    Polite("polite", CloudTool.Rewrite),
    Casual("casual", CloudTool.Rewrite),
    Fix("fix", CloudTool.Rewrite),
    ;

    companion object {
        /** 面板裏的選項，按顯示的次序。 */
        fun of(tool: CloudTool): List<TextTask> = entries.filter { it.tool == tool }
    }
}
