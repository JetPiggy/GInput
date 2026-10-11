package com.jetpiggy.ginput.cloudtext

/** 翻譯面板和改寫面板顯示的內容，由輸入法更新。 */
data class CloudTextState(
    /** 使用者已經同意第一次使用時的說明。 */
    val noticeAccepted: Boolean = false,
    /** 今日尚餘的每日次數。 */
    val remaining: Int = DailyAllowance.LIMIT,
    /** 去掉前後空白的原文，讓使用者知道會處理哪段文字。 */
    val source: String = "",
    val status: CloudTextStatus = CloudTextStatus.Ready,
)

sealed interface CloudTextStatus {
    /** 等使用者按選項。 */
    data object Ready : CloudTextStatus

    /** 讀原文時的問題，見 SourceRead。 */
    data object NoText : CloudTextStatus
    data object TooLong : CloudTextStatus
    data object Unreadable : CloudTextStatus

    data class Working(val task: TextTask) : CloudTextStatus
    data class Done(val task: TextTask, val result: String) : CloudTextStatus
    data class Failed(val task: TextTask, val reason: FailReason) : CloudTextStatus
}

enum class FailReason { Offline, Busy, Error }
