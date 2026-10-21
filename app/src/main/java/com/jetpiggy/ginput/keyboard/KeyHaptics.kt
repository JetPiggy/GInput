package com.jetpiggy.ginput.keyboard

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import com.jetpiggy.ginput.settings.KeyVibration

/** 甚麼時候震：每種在跟隨系統時用系統的哪種觸感，自訂強弱時比按下重或輕幾級。 */
internal enum class KeyHaptic(val systemFeedback: Int, val levelOffset: Int) {
    /** 按鍵、候選字和按鈕按下。 */
    Press(HapticFeedbackConstants.KEYBOARD_TAP, 0),

    /** 游標滑動、Delete 鍵向左掃和改字揀字每移一格：比按下輕一級。 */
    Step(HapticFeedbackConstants.CLOCK_TICK, -1),

    /** 長按整句候選、開始揀字：比按下重一級。 */
    LongPress(HapticFeedbackConstants.LONG_PRESS, 1),
}

/**
 * 按鍵震動（docs/spec/v1.md「按鍵震動」）。跟隨系統時用 View 的觸感回饋，開關和強弱照系統的「鍵盤震動」設定。
 * 選了輕、標準或強就直接用震動器，系統的「鍵盤震動」關了也照樣震；但手機把觸感回饋全部關了就不震。
 * vibrator 是 null（跟隨系統、關，或手機沒有震動器）就不用震動器。
 */
internal class KeyHaptics(private val vibration: KeyVibration, private val vibrator: Vibrator?) {

    /** 由輕到重的三級震動。 */
    private val effects: List<VibrationEffect> =
        if (vibrator == null) {
            emptyList()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(VibrationEffect.EFFECT_TICK, VibrationEffect.EFFECT_CLICK, VibrationEffect.EFFECT_HEAVY_CLICK)
                .map { VibrationEffect.createPredefined(it) }
        } else {
            // Android 9 沒有系統預設的觸感，用長短和強弱不同的一下震動代替。
            listOf(10L to 80, 20L to 160, 30L to 255).map { (ms, amplitude) -> VibrationEffect.createOneShot(ms, amplitude) }
        }

    fun perform(view: View, haptic: KeyHaptic) {
        val level = when (vibration) {
            KeyVibration.System -> {
                view.performHapticFeedback(haptic.systemFeedback)
                return
            }
            KeyVibration.Off -> return
            KeyVibration.Light -> 0
            KeyVibration.Standard -> 1
            KeyVibration.Strong -> 2
        }
        val vibrator = vibrator ?: return
        if (!touchFeedbackEnabled(view.context)) return
        vibrate(vibrator, effects[(level + haptic.levelOffset).coerceIn(effects.indices)])
    }

    companion object {
        /** 跟隨系統，不用震動器；預覽和未有設定時用。 */
        val System = KeyHaptics(KeyVibration.System, vibrator = null)

        fun create(context: Context, vibration: KeyVibration): KeyHaptics {
            val custom = vibration != KeyVibration.System && vibration != KeyVibration.Off
            return KeyHaptics(vibration, if (custom) vibrator(context)?.takeIf { it.hasVibrator() } else null)
        }

        private fun vibrator(context: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }

        /**
         * 系統的觸感回饋總開關（不是「鍵盤震動」）；讀不到當作開。Android 13 起震動標明是觸控回饋（vibrate），
         * 由系統按這個開關處理，不用自己讀。
         */
        private fun touchFeedbackEnabled(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ||
                @Suppress("DEPRECATION")
                Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0

        /** 標明是觸控回饋：系統按觸感回饋的強弱和開關處理，省電、勿擾等情況也照系統的規則。 */
        private fun vibrate(vibrator: Vibrator, effect: VibrationEffect) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, TouchAudioAttributes)
            }
        }

        private val TouchAudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
