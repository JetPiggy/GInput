package com.jetpiggy.ginput

import android.app.Application
import com.jetpiggy.ginput.crashreport.CrashReports

/** 輸入法和設定 App 在同一個行程；行程一開始就啟動當機回報。 */
class GInputApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReports.start(this)
    }
}
