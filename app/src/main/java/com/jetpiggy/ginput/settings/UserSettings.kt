package com.jetpiggy.ginput.settings

import com.jetpiggy.ginput.engine.CangjieGeneration
import com.jetpiggy.ginput.engine.EngineSettings
import com.jetpiggy.ginput.engine.Scheme
import com.jetpiggy.ginput.engine.SharedCodeOrder
import com.jetpiggy.ginput.typing.SpaceMode
import com.jetpiggy.ginput.typing.TypingSettings

/** 鍵盤高度：每一行按鍵的高度乘以 scale。 */
enum class KeyboardHeight(val scale: Float) {
    Compact(0.85f),
    Standard(1f),
    Tall(1.15f),
}

/** 按鍵標示：字母鍵上英文字母和倉頡字根怎樣顯示。 */
enum class KeyLabels {
    /** 英文字母大、倉頡字根小（預設）。 */
    LettersLarge,

    /** 倉頡字根大、英文字母小。 */
    RadicalsLarge,

    /** 只顯示英文字母。 */
    LettersOnly,
}

/** 按鍵震動：跟隨系統的鍵盤觸感，或由 GInput 自己震（見 keyboard/KeyHaptics.kt）。 */
enum class KeyVibration {
    /** 跟隨系統的「鍵盤震動」設定（預設）。 */
    System,

    /** 全部不震。 */
    Off,

    Light,
    Standard,
    Strong,
}

/** 主題：鍵盤的一套顏色。每套有淺色和深色兩組，跟隨系統的深色模式；顏色見 keyboard/KeyboardColors.kt。 */
enum class KeyboardTheme {
    /** 預設：冷灰。 */
    Default,

    /** 奶茶：港式奶茶的暖啡和米白。 */
    MilkTea,

    /** 叮叮：電車車身的深綠配米白。 */
    Tram,

    /** 維港：深藍海面配對岸燈火的琥珀色；淺色是清晨的淡藍。 */
    Harbour,

    /** 櫻花：灰調的淡粉，較鮮的玫瑰色只留給 Shift。 */
    Sakura,

    /** 霓虹：舊式霓虹招牌的洋紅和青色；深色用純黑底，適合 OLED 螢幕。 */
    Neon,
}

data class KeyboardSettings(
    val theme: KeyboardTheme = KeyboardTheme.Default,
    val height: KeyboardHeight = KeyboardHeight.Standard,
    val labels: KeyLabels = KeyLabels.LettersLarge,
    /** 字母上面常駐的數字行；預設開啟。 */
    val numberRow: Boolean = true,
    /** 按鍵放大預覽 */
    val keyPreview: Boolean = true,
    val vibration: KeyVibration = KeyVibration.System,
    val keySound: Boolean = false,
    /**
     * 按鍵和候選字格顯示倉頡字根。由啟用的輸入方案決定（只啟用粵拼時不顯示），不另外儲存；
     * 見 UserSettings.keyboardOnScreen。
     */
    val radicals: Boolean = true,
)

/** 設定 App 的全部設定。規則和預設值見 docs/spec/v1.md。 */
data class UserSettings(
    /** 全部輸入方案的次序，包括未啟用的；合併候選字時按這個次序。 */
    val schemeOrder: List<Scheme> = DEFAULT_SCHEME_ORDER,
    /** 已啟用的輸入方案，至少一個。 */
    val enabledSchemes: Set<Scheme> = setOf(Scheme.Quick),
    val cangjieGenerations: Set<CangjieGeneration> = CangjieGeneration.entries.toSet(),
    val sharedCodeOrder: SharedCodeOrder = SharedCodeOrder.Frequency,
    val rememberCandidates: Boolean = true,
    /** 粵拼的寬鬆拼法：也接受懶音和港式拼法。 */
    val looseSpelling: Boolean = true,
    /** 連打：速成和倉頡可以連續打幾個字的碼，出整句候選；預設關閉。 */
    val continuousTyping: Boolean = false,
    val colloquialPriority: Boolean = true,
    val spaceMode: SpaceMode = SpaceMode.WriteEnglish,
    val smartSpaceRemoval: Boolean = true,
    /** 句首自動大寫。 */
    val autoCapitalize: Boolean = true,
    /** Emoji 關鍵字；預設關閉。 */
    val emojiKeywords: Boolean = false,
    /** 剪貼簿歷史；關了就不再記錄，並刪除已記下的。 */
    val clipboardHistory: Boolean = true,
    val keyboard: KeyboardSettings = KeyboardSettings(),
    /** 當機回報（ADR 0001）。 */
    val crashReports: Boolean = true,
    /** 使用統計（ADR 0001）。 */
    val usageStats: Boolean = false,
    /** 已經同意雲端翻譯和改寫第一次使用時的說明（ADR 0005）。 */
    val cloudNoticeAccepted: Boolean = false,
    /** 已經完成首次設定精靈。 */
    val setupDone: Boolean = false,
    /** 已經看過（按了「知道了」）的新版簡介，是 WhatsNew.VERSION 的值；全新安裝時直接當作看過。 */
    val whatsNewSeen: Int = 0,
) {
    /** 打字用的設定，交給 TypingSession。 */
    val typing: TypingSettings = TypingSettings(
        engine = EngineSettings(
            schemes = schemeOrder.filter { it in enabledSchemes },
            cangjieGenerations = cangjieGenerations,
            sharedCodeOrder = sharedCodeOrder,
            colloquialPriority = colloquialPriority,
            rememberCandidates = rememberCandidates,
            looseSpelling = looseSpelling,
            continuousTyping = continuousTyping,
        ),
        spaceMode = spaceMode,
        smartSpaceRemoval = smartSpaceRemoval,
        autoCapitalize = autoCapitalize,
        emojiKeywords = emojiKeywords,
    )

    /** 鍵盤畫面用的外觀設定：沒有啟用速成或倉頡時，字根沒有用處，不顯示。 */
    val keyboardOnScreen: KeyboardSettings = keyboard.copy(radicals = enabledSchemes.any { it != Scheme.Jyutping })

    /** 只啟用 scheme，並把它排到最前；首次設定精靈用。 */
    fun withOnlyScheme(scheme: Scheme): UserSettings =
        copy(schemeOrder = listOf(scheme) + schemeOrder.filter { it != scheme }, enabledSchemes = setOf(scheme))

    /** 啟用或停用 scheme；不可以停用最後一個。 */
    fun withSchemeEnabled(scheme: Scheme, enabled: Boolean): UserSettings {
        val next = if (enabled) enabledSchemes + scheme else enabledSchemes - scheme
        return if (next.isEmpty()) this else copy(enabledSchemes = next)
    }

    /** 把 scheme 在輸入方案次序裏移前（offset 為負）或移後。 */
    fun withSchemeMoved(scheme: Scheme, offset: Int): UserSettings {
        val from = schemeOrder.indexOf(scheme)
        val to = (from + offset).coerceIn(schemeOrder.indices)
        if (from < 0 || from == to) return this
        return copy(schemeOrder = schemeOrder.toMutableList().apply { add(to, removeAt(from)) })
    }

    companion object {
        val DEFAULT_SCHEME_ORDER = listOf(Scheme.Quick, Scheme.Cangjie, Scheme.Jyutping)
    }
}
