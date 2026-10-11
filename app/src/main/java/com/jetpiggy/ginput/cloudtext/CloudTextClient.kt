package com.jetpiggy.ginput.cloudtext

import android.util.Log
import com.jetpiggy.ginput.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * 把原文送往 backend/ai（ADR 0005、0006）。請求只有工作和原文，沒有任何識別碼，User-Agent 也只寫「GInput」。
 * 日誌不可以帶有原文或結果。會連線，要在背景執行。
 */
class CloudTextClient(private val url: String = BuildConfig.CLOUD_TEXT_URL) {

    sealed interface Outcome {
        data class Done(val result: String) : Outcome

        /** 到了伺服器的上限，或者 Workers AI 暫時未能處理。 */
        data object Busy : Outcome

        /** 連不上伺服器。 */
        data object Offline : Outcome

        /** 伺服器不接受，或者回覆看不懂。 */
        data object Failed : Outcome
    }

    fun run(task: TextTask, text: String): Outcome {
        if (url.isBlank()) return Outcome.Failed
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            Log.w(TAG, "雲端翻譯和改寫未能連線：${e.javaClass.simpleName}")
            return Outcome.Offline
        }
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("User-Agent", "GInput")
            connection.outputStream.use { it.write(CloudTextProtocol.request(task, text).toByteArray()) }
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    CloudTextProtocol.result(body)?.let { Outcome.Done(it) } ?: Outcome.Failed
                }
                TOO_MANY_REQUESTS, HttpURLConnection.HTTP_UNAVAILABLE -> Outcome.Busy
                else -> Outcome.Failed
            }
        } catch (e: IOException) {
            Log.w(TAG, "雲端翻譯和改寫未能連線：${e.javaClass.simpleName}")
            Outcome.Offline
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "GInput"
        const val TOO_MANY_REQUESTS = 429
        const val CONNECT_TIMEOUT_MILLIS = 10_000

        /** 1,000 字的原文要寫一段時間才完成。 */
        const val READ_TIMEOUT_MILLIS = 30_000
    }
}

/** 請求和回覆的 JSON；伺服器那邊是 backend/ai 的 src/text.ts。 */
internal object CloudTextProtocol {

    /** {"task": "to-en", "text": "…"}：只有工作和原文。 */
    fun request(task: TextTask, text: String): String =
        JSONObject().put("task", task.code).put("text", text).toString()

    /** {"result": "…"}；格式不對或結果空白就回傳 null。 */
    fun result(body: String): String? =
        runCatching { JSONObject(body).opt("result") as? String }.getOrNull()?.takeIf { it.isNotBlank() }
}
