package com.jetpiggy.ginput.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** GInput 是否已在系統啟用，以及是否目前的輸入法。 */
internal data class ImeStatus(val enabled: Boolean, val isDefault: Boolean) {
    companion object {
        fun read(context: Context): ImeStatus {
            val imm = context.getSystemService(InputMethodManager::class.java)
            val enabled = imm?.enabledInputMethodList.orEmpty()
                .any { it.packageName == context.packageName }
            val defaultIme = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD,
            )
            return ImeStatus(enabled, defaultIme?.startsWith("${context.packageName}/") == true)
        }
    }
}

/** 回到畫面或關上輸入法選單時，重新讀取 ImeStatus。 */
@Composable
internal fun rememberImeStatus(): ImeStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf(ImeStatus.read(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { status = ImeStatus.read(context) }
    // 輸入法選單是對話框，關上後視窗重新取得焦點，要再讀一次狀態。
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (windowFocused) status = ImeStatus.read(context)
    }
    return status
}

internal fun openKeyboardSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
}

internal fun showKeyboardPicker(context: Context) {
    context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
}
