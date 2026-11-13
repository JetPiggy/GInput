package com.jetpiggy.ginput.handwriting

import android.app.job.JobScheduler
import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.MlKit
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** 手寫模型的狀態。 */
enum class HandwritingModelStatus {
    /** 未查過；未同意手寫的說明或關掉了手寫時一直是這樣，因為不初始化 ML Kit。 */
    Unknown,
    Missing,
    Downloading,
    Ready,

    /** 下載失敗，例如沒有網絡；可以再試。 */
    Failed,
}

/** 手寫板的一點：座標是 px，time 是毫秒。 */
data class InkPoint(val x: Float, val y: Float, val time: Long)

/** 手寫板上的筆跡：每一筆是一串點；width 和 height 是寫字地方的大小（px）。 */
data class InkStrokes(val strokes: List<List<InkPoint>>, val width: Float, val height: Float) {
    val isEmpty: Boolean get() = strokes.isEmpty()

    companion object {
        val Empty = InkStrokes(emptyList(), 0f, 0f)
    }
}

/**
 * 手寫（ADR 0013、0015）：包住 Google ML Kit 的數位墨水辨識，整個 App 共用一個。
 * ML Kit 會把診斷資料送往 Google，所以使用者未同意手寫的說明（或關掉了手寫）時，這裏的方法都不可以呼叫，連 ML Kit 也不初始化；
 * 第一次呼叫時才初始化（manifest 移除了 ML Kit 自動初始化的 provider）。筆跡和辨識結果只在手機上處理。
 */
class Handwriting private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _status = MutableStateFlow(HandwritingModelStatus.Unknown)
    private val _percent = MutableStateFlow(0)
    private var initialized = false
    private var recognizer: DigitalInkRecognizer? = null
    private var downloadJob: Job? = null

    /** 模型的狀態；設定頁和手寫板顯示。 */
    val status: StateFlow<HandwritingModelStatus> = _status.asStateFlow()

    /** 下載了幾多百分比（0 至 99）；status 是 Downloading 時才有意思。 */
    val percent: StateFlow<Int> = _percent.asStateFlow()

    private val model: DigitalInkRecognitionModel by lazy {
        DigitalInkRecognitionModel.builder(DigitalInkRecognitionModelIdentifier.ZH_HANI_HK).build()
    }

    private val models: RemoteModelManager
        get() {
            if (!initialized) {
                MlKit.initialize(appContext)
                initialized = true
            }
            return RemoteModelManager.getInstance()
        }

    /**
     * 未下載模型就開始下載；正在下載或已經下載了就甚麼都不做。手寫打開時和打開手寫板時呼叫。
     * warmUp：模型下載好之後，先在背景載入辨識器，寫第一個字時不用等（打開手寫板時用）。
     */
    fun prepare(warmUp: Boolean = false) {
        if (_status.value == HandwritingModelStatus.Ready) {
            if (warmUp) warmUp()
            return
        }
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            val downloaded = runCatching { models.isModelDownloaded(model).await() }.getOrDefault(false)
            if (!downloaded) {
                _percent.value = 0
                _status.value = HandwritingModelStatus.Downloading
                val watcher = launch { watchDownload() }
                val result = runCatching { models.download(model, DownloadConditions.Builder().build()).await() }
                watcher.cancel()
                result.exceptionOrNull()?.let { Log.w(TAG, "無法下載手寫模型", it) }
                if (result.isFailure) {
                    _status.value = HandwritingModelStatus.Failed
                    return@launch
                }
            }
            _status.value = HandwritingModelStatus.Ready
            if (warmUp) warmUp()
        }
    }

    /**
     * ML Kit 不提供下載進度，所以看它的資料夾有多大，和下載完的大小比較。下載時先寫壓縮檔，再解壓，
     * 資料夾一度比下載完更大，所以最多顯示 99%，而且只升不跌。
     */
    private suspend fun watchDownload() {
        val folder = File(appContext.filesDir, MODEL_FOLDER)
        while (true) {
            val bytes = withContext(Dispatchers.IO) { folder.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
            val percent = (bytes * 100 / MODEL_BYTES).toInt().coerceAtMost(99)
            if (percent > _percent.value) _percent.value = percent
            delay(PROGRESS_INTERVAL_MILLIS)
        }
    }

    /** 第一次辨識要先載入模型，需時一兩秒；用一筆假的筆跡先載入，結果不用。 */
    private fun warmUp() {
        if (recognizer != null) return
        val dummy = Ink.builder()
            .addStroke(Ink.Stroke.builder().addPoint(Ink.Point.create(0f, 0f, 0L)).addPoint(Ink.Point.create(10f, 0f, 10L)).build())
            .build()
        scope.launch { runCatching { client().recognize(dummy).await() } }
    }

    private fun client(): DigitalInkRecognizer = recognizer ?: DigitalInkRecognition.getClient(
        DigitalInkRecognizerOptions.builder(model).setMaxResultCount(MAX_CANDIDATES).build(),
    ).also { recognizer = it }

    /**
     * 關掉手寫：停止下載、關上辨識器並刪除模型，之後不再使用 ML Kit。
     * ML Kit 刪除模型時留下共用的檔案和紀錄；下次行程啟動時由 [removeLeftovers] 清走。
     */
    fun turnOff() {
        downloadJob?.cancel()
        downloadJob = null
        recognizer?.close()
        recognizer = null
        if (initialized) {
            // GInput 自己不用 WorkManager，排了的工作都是 ML Kit 下載模型的。
            WorkManager.getInstance(appContext).cancelAllWork()
            scope.launch {
                runCatching { models.deleteDownloadedModel(model).await() }
                    .onFailure { Log.w(TAG, "無法刪除手寫模型", it) }
            }
        }
        _status.value = HandwritingModelStatus.Unknown
    }

    /**
     * 辨識筆跡，傳回候選字，最好的排最前。before 是游標前的文字，只在手機上用來提高準確度。
     * 模型未下載好或者辨識失敗就傳回空白。
     */
    suspend fun recognize(ink: InkStrokes, before: String): List<String> {
        if (ink.isEmpty || _status.value != HandwritingModelStatus.Ready) return emptyList()
        val client = client()
        val builder = Ink.builder()
        for (stroke in ink.strokes) {
            val strokeBuilder = Ink.Stroke.builder()
            stroke.forEach { strokeBuilder.addPoint(Ink.Point.create(it.x, it.y, it.time)) }
            builder.addStroke(strokeBuilder.build())
        }
        val context = RecognitionContext.builder()
            .setPreContext(before.takeLast(PRE_CONTEXT_LENGTH))
            .setWritingArea(WritingArea(ink.width, ink.height))
            .build()
        return runCatching { client.recognize(builder.build(), context).await() }
            .map { result -> result.candidates.map { it.text }.filter(String::isNotBlank).distinct() }
            .onFailure { Log.w(TAG, "無法辨識手寫", it) }
            .getOrDefault(emptyList())
    }

    companion object {
        private const val TAG = "GInput"

        /** ML Kit 建議的上文長度。 */
        const val PRE_CONTEXT_LENGTH = 20
        private const val MAX_CANDIDATES = 10

        /** ML Kit 放手寫模型的資料夾（在 filesDir）。 */
        private const val MODEL_FOLDER = "mlkit_digital_ink_recognition"

        /** 香港中文模型下載完後資料夾的大小（2026-11-12 在手機量度），用來計算下載進度。 */
        private const val MODEL_BYTES = 26_042_266L
        private const val PROGRESS_INTERVAL_MILLIS = 300L

        @Volatile
        private var instance: Handwriting? = null

        fun get(context: Context): Handwriting =
            instance ?: synchronized(this) { instance ?: Handwriting(context).also { instance = it } }

        /** ML Kit 和它連帶的程式庫在 App 資料裏留下的檔案（相對 dataDir）。 */
        private val LEFTOVER_FILES = listOf(
            "files/$MODEL_FOLDER",
            "files/mdd_pds_config",
            "files/gms_icing_mdd_garbage_filemlkit_digital_ink_recognition",
            // 未送出的診斷資料。
            "databases/com.google.android.datatransport.events",
            "databases/com.google.android.datatransport.events-journal",
            "no_backup/androidx.work.workdb",
            "no_backup/androidx.work.workdb-shm",
            "no_backup/androidx.work.workdb-wal",
        )

        /** ML Kit 的 SharedPreferences：每次安裝不同的識別碼（com.google.mlkit.internal）和模型下載的紀錄。 */
        private fun isLeftoverPreferences(name: String): Boolean =
            name == "com.google.mlkit.internal.xml" || name.startsWith("gms_icing_mdd_")

        /**
         * 不可以用 ML Kit 時（手寫關閉或未同意說明），在行程啟動時呼叫（未初始化 ML Kit 之前）：刪走 ML Kit 留下的模型、紀錄、識別碼和
         * 未送出的診斷資料，並取消它排了的背景工作。沒有留下東西就甚麼都不做。
         */
        fun removeLeftovers(context: Context) {
            check(instance?.initialized != true) { "ML Kit 已經初始化" }
            val dataDir = context.applicationContext.dataDir
            var removed = false
            for (path in LEFTOVER_FILES) {
                val file = File(dataDir, path)
                if (file.exists()) removed = file.deleteRecursively() || removed
            }
            File(dataDir, "shared_prefs").listFiles()?.filter { isLeftoverPreferences(it.name) }?.forEach {
                context.deleteSharedPreferences(it.name.removeSuffix(".xml"))
                removed = true
            }
            if (removed) {
                // ML Kit 下載模型（WorkManager）和送出診斷資料（datatransport）的工作；GInput 自己不用 JobScheduler。
                context.getSystemService(JobScheduler::class.java)?.cancelAll()
                Log.i(TAG, "已清走手寫留下的檔案")
            }
        }
    }
}

/** 等 Google Play 服務的 Task 完成；不另外加 kotlinx-coroutines-play-services。 */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
