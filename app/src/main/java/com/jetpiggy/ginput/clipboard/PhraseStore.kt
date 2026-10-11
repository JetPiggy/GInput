package com.jetpiggy.ginput.clipboard

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 常用短語存在 SharedPreferences（檔案 phrases），隨 Android 備份（ADR 0001）。設定 App 和輸入法在同一個行程，
 * 共用這個物件：任何一方改了，另一方即時更新。只在主執行緒呼叫 update。
 */
class PhraseStore private constructor(private val preferences: SharedPreferences) {

    private val state = MutableStateFlow(read(preferences))
    val phrases: StateFlow<SavedPhrases> = state.asStateFlow()

    // SharedPreferences 只以弱引用保存 listener，所以要自己保留一份。
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { changed, _ ->
        state.value = read(changed)
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun update(transform: (SavedPhrases) -> SavedPhrases) {
        val next = transform(state.value)
        if (next == state.value) return
        state.value = next
        preferences.edit().putString(KEY, next.toJson()).apply()
    }

    companion object {
        private const val FILE_NAME = "phrases"
        private const val KEY = "phrases"

        @Volatile
        private var instance: PhraseStore? = null

        private fun read(preferences: SharedPreferences) = SavedPhrases.fromJson(preferences.getString(KEY, null))

        fun get(context: Context): PhraseStore =
            instance ?: synchronized(this) {
                instance ?: PhraseStore(
                    context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE),
                ).also { instance = it }
            }
    }
}
