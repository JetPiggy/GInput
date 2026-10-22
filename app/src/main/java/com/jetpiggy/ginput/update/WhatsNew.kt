package com.jetpiggy.ginput.update

import android.content.Context

/**
 * 新版簡介（規格「發佈」）：更新了 GInput 之後，第一次在一般文字框打開鍵盤時顯示一次，簡單列出改了甚麼。
 * 內容在 strings.xml 的 whats_new_*，由 keyboard/WhatsNewPanel.kt 顯示。
 */
object WhatsNew {

    /**
     * 簡介介紹的是哪個版本（寫簡介時的 versionCode）。新版本有使用者看得到的改動，就改寫簡介，
     * 並把這個數字改成新版本的 versionCode；沒有的話不用改，看過的人不會再看到舊的簡介。
     */
    const val VERSION = 14

    /** GInput 是更新上來的：全新安裝時，第一次安裝和最後更新的時間相同。 */
    fun updated(context: Context): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.lastUpdateTime > info.firstInstallTime
    }.getOrDefault(false)
}
