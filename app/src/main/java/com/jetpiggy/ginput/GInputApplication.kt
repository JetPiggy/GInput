package com.jetpiggy.ginput

import android.app.Application
import androidx.work.Configuration
import com.jetpiggy.ginput.crashreport.CrashReports
import com.jetpiggy.ginput.handwriting.Handwriting
import com.jetpiggy.ginput.settings.SettingsStore

/**
 * 輸入法和設定 App 在同一個行程；行程一開始就啟動當機回報。
 * 手寫用的 ML Kit 連帶 WorkManager：manifest 移除了它自動初始化的部分，由這裏的設定在 ML Kit 第一次用時才初始化（ADR 0015）。
 */
class GInputApplication : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        CrashReports.start(this)
        if (!SettingsStore.get(this).settings.value.handwritingActive) Handwriting.removeLeftovers(this)
    }

    override fun getWorkManagerConfiguration(): Configuration = Configuration.Builder().build()
}
