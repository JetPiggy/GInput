package com.jetpiggy.ginput.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 設定存在 SharedPreferences，會隨 Android 備份（ADR 0001）。設定 App 和輸入法在同一個行程，
 * 共用這個物件：任何一方改了設定，另一方的 settings 即時更新。只在主執行緒呼叫 update。
 */
class SettingsStore private constructor(private val preferences: SharedPreferences) {

    private val state = MutableStateFlow(SettingsPreferences.read(preferences.all))
    val settings: StateFlow<UserSettings> = state.asStateFlow()

    // SharedPreferences 只以弱引用保存 listener，所以要自己保留一份。
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { changed, _ ->
        state.value = SettingsPreferences.read(changed.all)
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun update(transform: (UserSettings) -> UserSettings) {
        val next = transform(state.value)
        if (next == state.value) return
        state.value = next
        val editor = preferences.edit()
        for ((key, value) in SettingsPreferences.write(next)) {
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
            }
        }
        editor.apply()
    }

    companion object {
        private const val FILE_NAME = "settings"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(
                    context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE),
                ).also { instance = it }
            }
    }
}
