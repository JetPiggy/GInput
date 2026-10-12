package com.jetpiggy.ginput.update

import android.content.Context
import android.os.Build
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/** 網站的 latest.json 公佈的新版本。 */
data class AvailableUpdate(val versionName: String, val pageUrl: String, val notes: String?)

/**
 * 更新提示（規格「發佈」）：不是從 Google Play 安裝的版本，才讀網站的 latest.json；
 * 有新版本就提示，並打開下載頁，不在 App 內下載。
 */
object UpdateCheck {

    private const val PLAY_STORE = "com.android.vending"
    private const val TIMEOUT_MILLIS = 10_000

    fun isFromPlay(context: Context): Boolean {
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        }.getOrNull()
        return installer == PLAY_STORE
    }

    /** 會連線，要在背景執行。連線或格式有問題就當作沒有新版本。 */
    fun fetch(url: String, currentVersionCode: Int, chinese: Boolean): AvailableUpdate? =
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                parse(text, currentVersionCode, chinese)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()

    /**
     * latest.json 的格式：
     * {"versionCode": 5, "versionName": "0.6.0", "pageUrl": "https://ginput.jetpiggy.com/",
     *  "notes": {"zh-Hant": "…", "en": "…"}}
     */
    internal fun parse(json: String, currentVersionCode: Int, chinese: Boolean): AvailableUpdate? {
        val latest = JSONObject(json)
        if (latest.optInt("versionCode") <= currentVersionCode) return null
        val versionName = latest.optString("versionName").takeIf { it.isNotBlank() } ?: return null
        val pageUrl = latest.optString("pageUrl").takeIf { it.startsWith("https://") } ?: return null
        val notes = latest.optJSONObject("notes")?.let { all ->
            val preferred = if (chinese) "zh-Hant" else "en"
            all.optString(preferred).ifBlank { all.optString(if (chinese) "en" else "zh-Hant") }
        }?.takeIf { it.isNotBlank() }
        return AvailableUpdate(versionName, pageUrl, notes)
    }
}
