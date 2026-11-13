package com.jetpiggy.ginput.settings

import com.jetpiggy.ginput.engine.CangjieGeneration
import com.jetpiggy.ginput.engine.EngineSettings
import com.jetpiggy.ginput.engine.Scheme
import com.jetpiggy.ginput.engine.SharedCodeOrder
import com.jetpiggy.ginput.typing.SpaceMode
import com.jetpiggy.ginput.typing.TypingSettings
import kotlin.math.roundToInt

/**
 * 鍵盤高度：每一行按鍵的高度乘以這個百分比，由 MIN 到 MAX，每格 STEP。手機直向和打橫分開記住（平板也按螢幕方向），
 * 在鍵盤的調整高度模式拖動改變（keyboard/HeightAdjust.kt）。
 */
data class KeyboardHeight(val portrait: Int = STANDARD, val landscape: Int = STANDARD) {
    fun percent(landscape: Boolean): Int = if (landscape) this.landscape else portrait

    fun withPercent(landscape: Boolean, percent: Int): KeyboardHeight =
        if (landscape) copy(landscape = percent) else copy(portrait = percent)

    companion object {
        const val MIN = 80
        const val STANDARD = 100
        const val MAX = 125
        const val STEP = 5

        /** 最近的一格，不超出範圍。 */
        fun nearest(percent: Float): Int = ((percent / STEP).roundToInt() * STEP).coerceIn(MIN, MAX)
    }
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

/** 剪貼簿歷史每一項在複製後保留多久；millis 是 null 的話不限時間，直到被新複製的擠出 MAX_ITEMS 項以外或刪除。 */
enum class ClipboardRetention(val millis: Long?) {
    /** 1 小時（預設）。 */
    Hour(HOUR_MILLIS),
    Day(24 * HOUR_MILLIS),
    Week(7 * 24 * HOUR_MILLIS),
    Unlimited(null),
}

private const val HOUR_MILLIS = 60 * 60 * 1000L

/** 主題系列：設定的主題頁按系列分段排列主題。 */
enum class ThemeSeries { Classic, Gradient, HongKong, Dessert, Nature, Style }

/**
 * 主題：鍵盤的一套顏色。每套有淺色和深色兩組，用哪一組見 ThemeMode；顏色見 keyboard/KeyboardColors.kt。
 * 次序就是主題頁的次序，同一系列的排在一起。
 */
enum class KeyboardTheme(val series: ThemeSeries) {
    /** 預設：冷灰。 */
    Default(ThemeSeries.Classic),

    /** 奶茶：港式奶茶的暖啡和米白。 */
    MilkTea(ThemeSeries.Classic),

    /** 叮叮：電車車身的深綠配米白。 */
    Tram(ThemeSeries.Classic),

    /** 維港：深藍海面配對岸燈火的琥珀色；淺色是清晨的淡藍。 */
    Harbour(ThemeSeries.Classic),

    /** 櫻花：灰調的淡粉，較鮮的玫瑰色只留給 Shift。 */
    Sakura(ThemeSeries.Classic),

    /** 霓虹：舊式霓虹招牌的洋紅和青色；深色用純黑底，適合 OLED 螢幕。 */
    Neon(ThemeSeries.Classic),

    /** 日落：維港黃昏，由上而下橙、珊瑚粉到紫；深色是入夜的深紫到暗橙紅。 */
    Sunset(ThemeSeries.Gradient),

    /** 極光：青綠、深藍到藍紫的夜空；淺色是淡薄荷到淡紫。 */
    Aurora(ThemeSeries.Gradient),

    /** 海鹽：夏天汽水的淺藍到薄荷；深色是深海藍到深青。 */
    SeaSalt(ThemeSeries.Gradient),

    /** 鐳射：鐳射貼紙的粉紅、淡藍、薄荷，由左上斜到右下。 */
    Holo(ThemeSeries.Gradient),

    /** 紅白藍：紅白藍膠袋，白色按鍵、藍色功能鍵、紅色大寫鎖定。 */
    RedWhiteBlue(ThemeSeries.HongKong),

    /** 茶記：舊式茶餐廳的紙皮石牆，薄荷綠底、粉紅功能鍵、米白按鍵。 */
    ChaChaanTeng(ThemeSeries.HongKong),

    /** 廟街：夜市燈籠，近黑的底配深紅和金色；淺色是米色配朱紅。 */
    TempleStreet(ThemeSeries.HongKong),

    /** 大澳：海水的灰藍配棚屋鐵皮的鏽橙。 */
    TaiO(ThemeSeries.HongKong),

    /** 芋泥：奶白底配芋頭紫。 */
    Taro(ThemeSeries.Dessert),

    /** 抹茶：抹茶粉的黃綠配奶白按鍵。 */
    Matcha(ThemeSeries.Dessert),

    /** 楊枝甘露：芒果橙黃、椰奶白，柚子粉紅只用在 Shift。 */
    MangoSago(ThemeSeries.Dessert),

    /** 黑芝麻：炭灰帶少少暖色，近乎單色。 */
    BlackSesame(ThemeSeries.Dessert),

    /** 深海：偏綠的深青藍，強調色是冷的青藍。 */
    DeepSea(ThemeSeries.Nature),

    /** 楓葉：枯葉的啡橙配楓紅。 */
    Maple(ThemeSeries.Nature),

    /** 初雪：雪白和冰藍；深色是雪夜的藍灰。 */
    FirstSnow(ThemeSeries.Nature),

    /** 沙灘：細沙的淺金米色配海水的土耳其藍。 */
    Beach(ThemeSeries.Nature),

    /** 高對比：給視力較弱的人，純黑、純白加鮮黃，文字至少 7:1。 */
    HighContrast(ThemeSeries.Style),

    /** 水墨：宣紙米白、墨黑和印章的朱紅；深色是墨底白字。 */
    Ink(ThemeSeries.Style),

    /** 復古：深色是舊終端機的黑底磷光綠字；淺色是米黃色的舊電腦外殼。 */
    Retro(ThemeSeries.Style),

    /** 莫蘭迪：低飽和的暖灰、鼠尾草綠、霧藍和乾燥玫瑰。 */
    Morandi(ThemeSeries.Style),
}

/** 主題用淺色還是深色那組顏色。 */
enum class ThemeMode {
    /** 跟隨系統的深色模式（預設）。 */
    System,

    Light,
    Dark,
    ;

    fun dark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }
}

data class KeyboardSettings(
    val theme: KeyboardTheme = KeyboardTheme.Default,
    val themeMode: ThemeMode = ThemeMode.System,
    val height: KeyboardHeight = KeyboardHeight(),
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
    /**
     * 分邊空白鍵的次序；不是分邊就是 null。由空白鍵模式和分邊次序決定，不另外儲存；
     * 見 UserSettings.keyboardOnScreen。
     */
    val splitSpace: SplitSpaceOrder? = null,
)

/** 分邊次序：分邊時空白鍵哪一半選中文。 */
enum class SplitSpaceOrder {
    /** 左中右英（預設） */
    ChineseLeft,

    /** 左英右中 */
    EnglishLeft,
}

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
    /** 工具列有「手寫」（ADR 0013、0015）；預設開啟。 */
    val handwriting: Boolean = true,
    /** 已經同意手寫第一次使用時的說明（ML Kit 會傳送診斷資料，ADR 0015）；關掉手寫就要再同意。 */
    val handwritingAccepted: Boolean = false,
    val colloquialPriority: Boolean = true,
    val spaceMode: SpaceMode = SpaceMode.WriteEnglish,
    /** 分邊次序；只在空白鍵模式是分邊時用到，轉去其他模式也記住。 */
    val splitSpaceOrder: SplitSpaceOrder = SplitSpaceOrder.ChineseLeft,
    val smartSpaceRemoval: Boolean = true,
    /** 句首自動大寫。 */
    val autoCapitalize: Boolean = true,
    /** Emoji 關鍵字；預設關閉。 */
    val emojiKeywords: Boolean = false,
    /** 英漢詞典（ADR 0012）；預設開啟。 */
    val englishChinese: Boolean = true,
    /** 剪貼簿歷史；關了就不再記錄，並刪除已記下的。 */
    val clipboardHistory: Boolean = true,
    val clipboardRetention: ClipboardRetention = ClipboardRetention.Hour,
    val keyboard: KeyboardSettings = KeyboardSettings(),
    /** 當機回報（ADR 0001）。 */
    val crashReports: Boolean = true,
    /** 使用統計（ADR 0001）。 */
    val usageStats: Boolean = false,
    /** 已經同意雲端翻譯和改寫第一次使用時的說明（ADR 0015）。 */
    val cloudNoticeAccepted: Boolean = false,
    /** 已經完成首次設定精靈。 */
    val setupDone: Boolean = false,
    /** 已經看過（按了「知道了」）的新版簡介，是 WhatsNew.VERSION 的值；全新安裝時直接當作看過。 */
    val whatsNewSeen: Int = 0,
) {
    /** 可以用 ML Kit：工具列有「手寫」，而且已經同意說明（ADR 0015）。 */
    val handwritingActive: Boolean get() = handwriting && handwritingAccepted

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
        englishChinese = englishChinese,
    )

    /** 鍵盤畫面用的外觀設定：沒有啟用速成或倉頡時，字根沒有用處，不顯示；分邊時空白鍵分成兩半。 */
    val keyboardOnScreen: KeyboardSettings = keyboard.copy(
        radicals = enabledSchemes.any { it != Scheme.Jyutping },
        splitSpace = splitSpaceOrder.takeIf { spaceMode == SpaceMode.Split },
    )

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
