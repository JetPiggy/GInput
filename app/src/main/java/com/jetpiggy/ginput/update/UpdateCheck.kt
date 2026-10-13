package com.jetpiggy.ginput.update

import android.content.Context
import android.os.Build
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/** 網站的 latest.json 公佈的新版本。 */
data class AvailableUpdate(val versionName: String, val pageUrl: String, val notes: String?)

/** latest.json 的公告，例如服務暫停或要盡快更新；按「知道了」之後不再顯示。 */
data class Announcement(val id: String, val text: String, val url: String?)

/**
 * 更新提示（規格「發佈」）：不是從 Google Play 安裝的版本，才讀網站的 latest.json；
 * 有新版本就提示，按鈕交給瀏覽器下載 APK，不在 App 內下載。latest.json 也可以帶一則公告。
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

    /**
     * 讀 latest.json 的原文。會連線，要在背景執行；連線有問題是 null。
     * User-Agent 只寫「GInput」，不帶系統預設的手機型號和 Android 版本。
     */
    fun download(url: String): String? =
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                connection.setRequestProperty("User-Agent", "GInput")
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()

    /**
     * latest.json 的格式（notice 可以沒有；url 可以沒有）：
     * {"versionCode": 5, "versionName": "0.6.0", "pageUrl": "https://github.com/…/GInput.apk",
     *  "notes": {"zh-Hant": "…", "en": "…"},
     *  "notice": {"id": "2026-10-20", "zh-Hant": "…", "en": "…", "url": "https://…"}}
     * 格式有問題就當作沒有新版本。
     */
    internal fun parse(json: String, currentVersionCode: Int, chinese: Boolean): AvailableUpdate? {
        val latest = read(json) ?: return null
        if (latest.optInt("versionCode") <= currentVersionCode) return null
        val versionName = latest.optString("versionName").takeIf { it.isNotBlank() } ?: return null
        val pageUrl = latest.optString("pageUrl").takeIf { it.startsWith("https://") } ?: return null
        val notes = latest.optJSONObject("notes")?.let { inLanguage(it, chinese) }
        return AvailableUpdate(versionName, pageUrl, notes)
    }

    /** latest.json 公佈的 versionCode；格式有問題是 0。 */
    internal fun versionCode(json: String): Int = read(json)?.optInt("versionCode") ?: 0

    /** latest.json 的公告；沒有、沒有編號或沒有文字就是 null。連結一定要是 https，否則不顯示連結。 */
    internal fun announcement(json: String, chinese: Boolean): Announcement? {
        val notice = read(json)?.optJSONObject("notice") ?: return null
        val id = notice.optString("id").takeIf { it.isNotBlank() } ?: return null
        val text = inLanguage(notice, chinese) ?: return null
        return Announcement(id, text, notice.optString("url").takeIf { it.startsWith("https://") })
    }

    private fun read(json: String): JSONObject? = runCatching { JSONObject(json) }.getOrNull()

    /** 使用者語言的文字，沒有的話用另一種語言。 */
    private fun inLanguage(texts: JSONObject, chinese: Boolean): String? {
        val preferred = if (chinese) "zh-Hant" else "en"
        return texts.optString(preferred).ifBlank { texts.optString(if (chinese) "en" else "zh-Hant") }
            .takeIf { it.isNotBlank() }
    }
}
