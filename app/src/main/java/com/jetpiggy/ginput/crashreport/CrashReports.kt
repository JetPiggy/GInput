package com.jetpiggy.ginput.crashreport

import android.content.Context
import com.jetpiggy.ginput.BuildConfig
import com.jetpiggy.ginput.settings.SettingsStore
import io.sentry.android.core.SentryAndroid

/**
 * 當機回報（ADR 0001）：用 Sentry（歐盟區），只送當機和 ANR 的類型和堆疊。
 * local.properties 沒有 SENTRY_DSN 就完全不啟動。設定關了當機回報，事件在送出前就丟掉，甚麼都不送。
 */
object CrashReports {

    fun start(context: Context) {
        val dsn = BuildConfig.SENTRY_DSN
        if (dsn.isBlank()) return
        val settings = SettingsStore.get(context).settings
        SentryAndroid.init(context) { options ->
            options.dsn = dsn
            options.environment = BuildConfig.BUILD_TYPE
            options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            options.isSendDefaultPii = false
            options.isAttachServerName = false
            // 只要當機和 ANR：不收麵包屑、不追蹤效能、不記錄使用時段，也不送 SDK 自己的統計。
            options.maxBreadcrumbs = 0
            options.isEnableActivityLifecycleBreadcrumbs = false
            options.isEnableAppLifecycleBreadcrumbs = false
            options.isEnableSystemEventBreadcrumbs = false
            options.isEnableAppComponentBreadcrumbs = false
            options.isEnableNetworkEventBreadcrumbs = false
            options.isEnableUserInteractionBreadcrumbs = false
            options.isEnableUserInteractionTracing = false
            options.isEnableAutoActivityLifecycleTracing = false
            options.isEnableFramesTracking = false
            options.isEnableAppStartProfiling = false
            options.tracesSampleRate = null
            options.isEnableAutoSessionTracking = false
            options.isSendClientReports = false
            // 截圖、畫面結構、ANR 的原始執行緒傾印和原生當機的記憶體內容都可能看到輸入內容。
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
            options.isAttachAnrThreadDump = false
            options.isTombstoneEnabled = false
            options.isAttachRawTombstone = false
            options.isEnableNdk = false
            options.isCollectAdditionalContext = false
            options.isCollectExternalStorageContext = false
            options.isEnableRootCheck = false
            options.setBeforeBreadcrumb { _, _ -> null }
            options.setBeforeSend { event, _ ->
                if (settings.value.crashReports) CrashReportFilter.scrub(event) else null
            }
        }
    }
}
