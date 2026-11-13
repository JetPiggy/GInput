package com.jetpiggy.ginput.settings

import com.jetpiggy.ginput.engine.CangjieGeneration
import com.jetpiggy.ginput.engine.Scheme
import com.jetpiggy.ginput.engine.SharedCodeOrder
import com.jetpiggy.ginput.typing.SpaceMode

/**
 * UserSettings 和 SharedPreferences 之間的轉換。值用固定的英文代號，所以改了 enum 的名字，
 * 已儲存或從備份還原的設定仍然有效；不認得的值一律用預設值。
 */
internal object SettingsPreferences {

    private const val SCHEME_ORDER = "scheme_order"
    private const val SCHEMES_ENABLED = "schemes_enabled"
    private const val CANGJIE_GENERATIONS = "cangjie_generations"
    private const val SHARED_CODE_ORDER = "shared_code_order"
    private const val REMEMBER_CANDIDATES = "remember_candidates"
    private const val LOOSE_SPELLING = "loose_spelling"
    private const val CONTINUOUS_TYPING = "continuous_typing"
    /** 1.0.1 之前未發佈的 "handwriting" 是「打開並同意」，意思不同，所以用新的鍵。 */
    private const val HANDWRITING = "handwriting_tool"
    private const val HANDWRITING_ACCEPTED = "handwriting_accepted"
    private const val COLLOQUIAL_PRIORITY = "colloquial_priority"
    private const val SPACE_MODE = "space_mode"
    private const val SPACE_SPLIT_ORDER = "space_split_order"
    private const val SMART_SPACE_REMOVAL = "smart_space_removal"
    private const val AUTO_CAPITALIZE = "auto_capitalize"
    private const val EMOJI_KEYWORDS = "emoji_keywords"
    private const val ENGLISH_CHINESE = "english_chinese"
    private const val CLIPBOARD_HISTORY = "clipboard_history"
    private const val CLIPBOARD_RETENTION = "clipboard_retention"
    private const val KEYBOARD_THEME = "keyboard_theme"
    private const val THEME_MODE = "theme_mode"
    /** 舊版的鍵盤高度（矮、標準、高，直向和打橫共用），只用來讀取。 */
    private const val KEYBOARD_HEIGHT = "keyboard_height"
    private const val KEYBOARD_HEIGHT_PORTRAIT = "keyboard_height_portrait"
    private const val KEYBOARD_HEIGHT_LANDSCAPE = "keyboard_height_landscape"
    private const val KEY_LABELS = "key_labels"
    private const val NUMBER_ROW = "number_row"
    private const val KEY_PREVIEW = "key_preview"
    private const val KEY_VIBRATION = "key_vibration"
    private const val KEY_SOUND = "key_sound"
    private const val CRASH_REPORTS = "crash_reports"
    private const val USAGE_STATS = "usage_stats"
    private const val CLOUD_NOTICE_ACCEPTED = "cloud_notice_accepted"
    private const val SETUP_DONE = "setup_done"
    private const val WHATS_NEW_SEEN = "whats_new_seen"

    private val SchemeCodes = mapOf(Scheme.Quick to "quick", Scheme.Cangjie to "cangjie", Scheme.Jyutping to "jyutping")
    private val GenerationCodes = mapOf(
        CangjieGeneration.entries.toSet() to "both",
        setOf(CangjieGeneration.Third) to "third",
        setOf(CangjieGeneration.Fifth) to "fifth",
    )
    private val OrderCodes = mapOf(SharedCodeOrder.Frequency to "frequency", SharedCodeOrder.Windows to "windows")
    private val SpaceModeCodes = mapOf(
        SpaceMode.WriteEnglish to "english",
        SpaceMode.SelectChinese to "chinese",
        SpaceMode.Split to "split",
    )
    private val SplitOrderCodes = mapOf(
        SplitSpaceOrder.ChineseLeft to "chinese_left",
        SplitSpaceOrder.EnglishLeft to "english_left",
    )
    private val RetentionCodes = mapOf(
        ClipboardRetention.Hour to "hour",
        ClipboardRetention.Day to "day",
        ClipboardRetention.Week to "week",
        ClipboardRetention.Unlimited to "unlimited",
    )
    private val ThemeCodes = mapOf(
        KeyboardTheme.Default to "default",
        KeyboardTheme.MilkTea to "milk_tea",
        KeyboardTheme.Tram to "tram",
        KeyboardTheme.Harbour to "harbour",
        KeyboardTheme.Sakura to "sakura",
        KeyboardTheme.Neon to "neon",
        KeyboardTheme.Sunset to "sunset",
        KeyboardTheme.Aurora to "aurora",
        KeyboardTheme.SeaSalt to "sea_salt",
        KeyboardTheme.Holo to "holo",
        KeyboardTheme.RedWhiteBlue to "red_white_blue",
        KeyboardTheme.ChaChaanTeng to "cha_chaan_teng",
        KeyboardTheme.TempleStreet to "temple_street",
        KeyboardTheme.TaiO to "tai_o",
        KeyboardTheme.Taro to "taro",
        KeyboardTheme.Matcha to "matcha",
        KeyboardTheme.MangoSago to "mango_sago",
        KeyboardTheme.BlackSesame to "black_sesame",
        KeyboardTheme.DeepSea to "deep_sea",
        KeyboardTheme.Maple to "maple",
        KeyboardTheme.FirstSnow to "first_snow",
        KeyboardTheme.Beach to "beach",
        KeyboardTheme.HighContrast to "high_contrast",
        KeyboardTheme.Ink to "ink",
        KeyboardTheme.Retro to "retro",
        KeyboardTheme.Morandi to "morandi",
    )
    private val ThemeModeCodes = mapOf(ThemeMode.System to "system", ThemeMode.Light to "light", ThemeMode.Dark to "dark")
    private val LegacyHeightCodes = mapOf("compact" to 85, "standard" to KeyboardHeight.STANDARD, "tall" to 115)
    private val LabelCodes = mapOf(
        KeyLabels.LettersLarge to "letters",
        KeyLabels.RadicalsLarge to "radicals",
        KeyLabels.LettersOnly to "letters_only",
    )
    private val VibrationCodes = mapOf(
        KeyVibration.System to "system",
        KeyVibration.Off to "off",
        KeyVibration.Light to "light",
        KeyVibration.Standard to "standard",
        KeyVibration.Strong to "strong",
    )

    fun read(values: Map<String, *>): UserSettings {
        val defaults = UserSettings()
        val keyboard = defaults.keyboard

        fun flag(key: String, default: Boolean) = values[key] as? Boolean ?: default

        fun number(key: String, default: Int) = values[key] as? Int ?: default

        fun <T> choice(key: String, codes: Map<T, String>, default: T): T =
            codes.entries.firstOrNull { it.value == values[key] }?.key ?: default

        fun schemes(key: String): List<Scheme> =
            (values[key] as? String).orEmpty().split(',')
                .mapNotNull { code -> SchemeCodes.entries.firstOrNull { it.value == code }?.key }
                .distinct()

        // 百分比存成文字，例如 "110"；未有的話用舊版的設定。
        val legacyHeight = LegacyHeightCodes[values[KEYBOARD_HEIGHT]] ?: KeyboardHeight.STANDARD

        fun height(key: String) =
            (values[key] as? String)?.toIntOrNull()?.let { KeyboardHeight.nearest(it.toFloat()) } ?: legacyHeight

        val order = schemes(SCHEME_ORDER).let { stored -> stored + defaults.schemeOrder.filter { it !in stored } }
        return UserSettings(
            schemeOrder = order,
            enabledSchemes = schemes(SCHEMES_ENABLED).toSet().ifEmpty { defaults.enabledSchemes },
            cangjieGenerations = choice(CANGJIE_GENERATIONS, GenerationCodes, defaults.cangjieGenerations),
            sharedCodeOrder = choice(SHARED_CODE_ORDER, OrderCodes, defaults.sharedCodeOrder),
            rememberCandidates = flag(REMEMBER_CANDIDATES, defaults.rememberCandidates),
            looseSpelling = flag(LOOSE_SPELLING, defaults.looseSpelling),
            continuousTyping = flag(CONTINUOUS_TYPING, defaults.continuousTyping),
            handwriting = flag(HANDWRITING, defaults.handwriting),
            handwritingAccepted = flag(HANDWRITING_ACCEPTED, defaults.handwritingAccepted),
            colloquialPriority = flag(COLLOQUIAL_PRIORITY, defaults.colloquialPriority),
            spaceMode = choice(SPACE_MODE, SpaceModeCodes, defaults.spaceMode),
            splitSpaceOrder = choice(SPACE_SPLIT_ORDER, SplitOrderCodes, defaults.splitSpaceOrder),
            smartSpaceRemoval = flag(SMART_SPACE_REMOVAL, defaults.smartSpaceRemoval),
            autoCapitalize = flag(AUTO_CAPITALIZE, defaults.autoCapitalize),
            emojiKeywords = flag(EMOJI_KEYWORDS, defaults.emojiKeywords),
            englishChinese = flag(ENGLISH_CHINESE, defaults.englishChinese),
            clipboardHistory = flag(CLIPBOARD_HISTORY, defaults.clipboardHistory),
            clipboardRetention = choice(CLIPBOARD_RETENTION, RetentionCodes, defaults.clipboardRetention),
            keyboard = KeyboardSettings(
                theme = choice(KEYBOARD_THEME, ThemeCodes, keyboard.theme),
                themeMode = choice(THEME_MODE, ThemeModeCodes, keyboard.themeMode),
                height = KeyboardHeight(portrait = height(KEYBOARD_HEIGHT_PORTRAIT), landscape = height(KEYBOARD_HEIGHT_LANDSCAPE)),
                labels = choice(KEY_LABELS, LabelCodes, keyboard.labels),
                numberRow = flag(NUMBER_ROW, keyboard.numberRow),
                keyPreview = flag(KEY_PREVIEW, keyboard.keyPreview),
                vibration = choice(KEY_VIBRATION, VibrationCodes, keyboard.vibration),
                keySound = flag(KEY_SOUND, keyboard.keySound),
            ),
            crashReports = flag(CRASH_REPORTS, defaults.crashReports),
            usageStats = flag(USAGE_STATS, defaults.usageStats),
            cloudNoticeAccepted = flag(CLOUD_NOTICE_ACCEPTED, defaults.cloudNoticeAccepted),
            setupDone = flag(SETUP_DONE, defaults.setupDone),
            whatsNewSeen = number(WHATS_NEW_SEEN, defaults.whatsNewSeen),
        )
    }

    /**
     * 使用統計報告裏的設定：和儲存時同樣的鍵和代號，但不包括私隱開關和同意、手寫、首次設定精靈、新版簡介、未啟用的輸入方案和主題（連淺色、深色）。
     * backend/usage-stats 只接受這些鍵；加減項目要兩邊一起改。
     */
    fun forUsageStats(settings: UserSettings): Map<String, Any> =
        write(settings).filterKeys { it in UsageStatsKeys }

    private val UsageStatsKeys = setOf(
        SCHEMES_ENABLED,
        CANGJIE_GENERATIONS,
        SHARED_CODE_ORDER,
        REMEMBER_CANDIDATES,
        LOOSE_SPELLING,
        CONTINUOUS_TYPING,
        COLLOQUIAL_PRIORITY,
        SPACE_MODE,
        SPACE_SPLIT_ORDER,
        SMART_SPACE_REMOVAL,
        AUTO_CAPITALIZE,
        EMOJI_KEYWORDS,
        ENGLISH_CHINESE,
        CLIPBOARD_HISTORY,
        KEYBOARD_HEIGHT_PORTRAIT,
        KEYBOARD_HEIGHT_LANDSCAPE,
        KEY_LABELS,
        NUMBER_ROW,
        KEY_PREVIEW,
        KEY_VIBRATION,
        KEY_SOUND,
    )

    /** 全部設定，值是 String、Boolean 或 Int。 */
    fun write(settings: UserSettings): Map<String, Any> = mapOf(
        SCHEME_ORDER to settings.schemeOrder.joinToString(",") { SchemeCodes.getValue(it) },
        SCHEMES_ENABLED to settings.schemeOrder.filter { it in settings.enabledSchemes }
            .joinToString(",") { SchemeCodes.getValue(it) },
        CANGJIE_GENERATIONS to GenerationCodes.getValue(settings.cangjieGenerations),
        SHARED_CODE_ORDER to OrderCodes.getValue(settings.sharedCodeOrder),
        REMEMBER_CANDIDATES to settings.rememberCandidates,
        LOOSE_SPELLING to settings.looseSpelling,
        CONTINUOUS_TYPING to settings.continuousTyping,
        HANDWRITING to settings.handwriting,
        HANDWRITING_ACCEPTED to settings.handwritingAccepted,
        COLLOQUIAL_PRIORITY to settings.colloquialPriority,
        SPACE_MODE to SpaceModeCodes.getValue(settings.spaceMode),
        SPACE_SPLIT_ORDER to SplitOrderCodes.getValue(settings.splitSpaceOrder),
        SMART_SPACE_REMOVAL to settings.smartSpaceRemoval,
        AUTO_CAPITALIZE to settings.autoCapitalize,
        EMOJI_KEYWORDS to settings.emojiKeywords,
        ENGLISH_CHINESE to settings.englishChinese,
        CLIPBOARD_HISTORY to settings.clipboardHistory,
        CLIPBOARD_RETENTION to RetentionCodes.getValue(settings.clipboardRetention),
        KEYBOARD_THEME to ThemeCodes.getValue(settings.keyboard.theme),
        THEME_MODE to ThemeModeCodes.getValue(settings.keyboard.themeMode),
        KEYBOARD_HEIGHT_PORTRAIT to settings.keyboard.height.portrait.toString(),
        KEYBOARD_HEIGHT_LANDSCAPE to settings.keyboard.height.landscape.toString(),
        KEY_LABELS to LabelCodes.getValue(settings.keyboard.labels),
        NUMBER_ROW to settings.keyboard.numberRow,
        KEY_PREVIEW to settings.keyboard.keyPreview,
        KEY_VIBRATION to VibrationCodes.getValue(settings.keyboard.vibration),
        KEY_SOUND to settings.keyboard.keySound,
        CRASH_REPORTS to settings.crashReports,
        USAGE_STATS to settings.usageStats,
        CLOUD_NOTICE_ACCEPTED to settings.cloudNoticeAccepted,
        SETUP_DONE to settings.setupDone,
        WHATS_NEW_SEEN to settings.whatsNewSeen,
    )
}
