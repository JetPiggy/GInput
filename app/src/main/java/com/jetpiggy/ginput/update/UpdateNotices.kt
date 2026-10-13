package com.jetpiggy.ginput.update

import android.content.Context
import android.util.Log
import com.jetpiggy.ginput.BuildConfig
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 更新提示和公告的記錄：上次讀到的 latest.json，以及在設定頁看過、按了「知道了」的是哪個。
 */
data class UpdateState(
    /** 上次成功讀到的 latest.json 原文。 */
    val latest: String? = null,
    /** 上次成功讀到的時間（epoch 毫秒）。 */
    val lastCheck: Long = 0,
    /** 在設定頁看過的新版本（versionCode）和公告（編號）。 */
    val seenVersionCode: Int = 0,
    val seenNotice: String = "",
    /** 按了「知道了」的公告編號。 */
    val dismissedNotice: String = "",
) {
    fun update(currentVersionCode: Int, chinese: Boolean): AvailableUpdate? =
        latest?.let { UpdateCheck.parse(it, currentVersionCode, chinese) }

    fun notice(chinese: Boolean): Announcement? =
        latest?.let { UpdateCheck.announcement(it, chinese) }?.takeIf { it.id != dismissedNotice }

    /** 有未在設定頁看過的新版本或公告：鍵盤工具列的「設定」加一個小點。 */
    fun unseen(currentVersionCode: Int): Boolean {
        val newVersion = update(maxOf(currentVersionCode, seenVersionCode), chinese = true) != null
        val notice = notice(chinese = true)
        return newVersion || (notice != null && notice.id != seenNotice)
    }

    /** 在設定頁看過現在的新版本和公告。 */
    fun seen(): UpdateState {
        val latest = latest ?: return this
        return copy(
            seenVersionCode = maxOf(seenVersionCode, UpdateCheck.versionCode(latest)),
            seenNotice = UpdateCheck.announcement(latest, chinese = true)?.id ?: seenNotice,
        )
    }

    fun dismissed(id: String): UpdateState = copy(dismissedNotice = id, seenNotice = id)

    fun checked(latest: String, now: Long): UpdateState = copy(latest = latest, lastCheck = now)

    /** 鍵盤打開時，未讀過、距離上次成功讀到超過一日，或者手機的時間改早了，才再讀。 */
    fun due(now: Long): Boolean = lastCheck == 0L || now - lastCheck >= DAY_MILLIS || now < lastCheck

    fun format(): String = JSONObject()
        .put("latest", latest ?: JSONObject.NULL)
        .put("lastCheck", lastCheck)
        .put("seenVersionCode", seenVersionCode)
        .put("seenNotice", seenNotice)
        .put("dismissedNotice", dismissedNotice)
        .toString()

    companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        fun parse(text: String): UpdateState {
            val saved = JSONObject(text)
            return UpdateState(
                latest = if (saved.isNull("latest")) null else saved.optString("latest"),
                lastCheck = saved.optLong("lastCheck"),
                seenVersionCode = saved.optInt("seenVersionCode"),
                seenNotice = saved.optString("seenNotice"),
                dismissedNotice = saved.optString("dismissedNotice"),
            )
        }
    }
}

/**
 * 更新提示和公告（規格「發佈」）：不是從 Google Play 安裝的 GInput，打開設定 App 時，以及鍵盤打開時每日最多一次，
 * 讀網站的 latest.json。記錄存在 noBackupFilesDir，不會隨 Android 備份。設定 App 和輸入法在同一個行程，
 * 共用這個物件。除了在背景讀寫檔案和連線，所有方法都在主執行緒呼叫。
 */
class UpdateNotices private constructor(
    private val file: File,
    private val fromPlay: Boolean,
    private val url: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutableState = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = mutableState.asStateFlow()

    /** 在背景寫入檔案，一次只寫一個，後寫的蓋過先寫的。 */
    private val writes = CoroutineScope(Dispatchers.IO.limitedParallelism(1))

    private var loaded = false
    private var checking = false
    private var lastAttempt = 0L

    /** 載入上次的記錄，鍵盤一打開就知道要不要加小點。 */
    fun load(scope: CoroutineScope) {
        if (fromPlay) return
        scope.launch { ensureLoaded() }
    }

    /** 鍵盤打開時：距離上次讀到超過一日才讀；上次失敗的話，隔一小時才再試。 */
    fun checkIfDue(scope: CoroutineScope) = check(scope, onlyIfDue = true)

    /** 打開設定 App 時讀一次（見 SettingsScreen 的 UpdateCards）。 */
    fun checkNow(scope: CoroutineScope) = check(scope, onlyIfDue = false)

    private fun check(scope: CoroutineScope, onlyIfDue: Boolean) {
        if (fromPlay || checking) return
        val now = clock()
        if (onlyIfDue && now - lastAttempt < RETRY_MILLIS) return
        checking = true
        scope.launch {
            try {
                ensureLoaded()
                if (onlyIfDue && !state.value.due(now)) return@launch
                lastAttempt = now
                val latest = withContext(Dispatchers.IO) { UpdateCheck.download(url) } ?: return@launch
                change { it.checked(latest, clock()) }
            } finally {
                checking = false
            }
        }
    }

    fun markSeen() {
        if (loaded) change { it.seen() }
    }

    fun dismiss(id: String) {
        if (loaded) change { it.dismissed(id) }
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        val saved = withContext(Dispatchers.IO) {
            runCatching { if (file.exists()) UpdateState.parse(file.readText()) else null }
                .onFailure { Log.w(TAG, "無法讀取更新記錄：${it.javaClass.simpleName}") }
                .getOrNull()
        }
        if (loaded) return
        loaded = true
        if (saved != null) mutableState.value = saved
    }

    private fun change(transform: (UpdateState) -> UpdateState) {
        val next = transform(mutableState.value)
        if (next == mutableState.value) return
        mutableState.value = next
        val text = next.format()
        writes.launch { write(text) }
    }

    private fun write(text: String) {
        runCatching {
            val dir = checkNotNull(file.parentFile)
            dir.mkdirs()
            val partial = File(dir, "${file.name}.partial")
            partial.writeText(text)
            check(partial.renameTo(file)) { "無法儲存更新記錄" }
        }.onFailure { Log.w(TAG, "無法儲存更新記錄：${it.javaClass.simpleName}") }
    }

    companion object {
        private const val TAG = "UpdateNotices"
        private const val RETRY_MILLIS = 60 * 60 * 1000L

        @Volatile
        private var instance: UpdateNotices? = null

        fun get(context: Context): UpdateNotices =
            instance ?: synchronized(this) {
                instance ?: UpdateNotices(
                    file = File(File(context.applicationContext.noBackupFilesDir, "update"), "state.json"),
                    fromPlay = UpdateCheck.isFromPlay(context),
                    url = "${BuildConfig.SITE_URL}latest.json",
                ).also { instance = it }
            }
    }
}
