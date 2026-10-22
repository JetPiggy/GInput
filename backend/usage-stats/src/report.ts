// 使用統計的報告格式和寫入規則（ADR 0001；規格見 docs/spec/v1.md 的「使用統計」）。
// App 那邊是 app/src/main/java/com/jetpiggy/ginput/usagestats/UsageReport.kt；加減項目要兩邊一起改。

/** 一部裝置一日的報告。沒有任何識別碼。 */
export interface DailyReport {
  day: string;
  app: string;
  sdk: number;
  settings: Record<string, string | boolean>;
  counts: Record<string, number>;
}

const SCHEMES = ["quick", "cangjie", "jyutping"];

/** 已啟用的輸入方案按輸入方案次序用逗號連起，例如 "cangjie,jyutping"：每種最多一次，至少一種。 */
function schemeLists(): string[] {
  const lists: string[] = [];
  const extend = (taken: string[]) => {
    if (taken.length > 0) lists.push(taken.join(","));
    for (const scheme of SCHEMES) if (!taken.includes(scheme)) extend([...taken, scheme]);
  };
  extend([]);
  return lists;
}

/** 接受的設定和值，和 App 的 SettingsPreferences.forUsageStats 一致。 */
export const SETTINGS: Readonly<Record<string, readonly (string | boolean)[]>> = {
  schemes_enabled: schemeLists(),
  cangjie_generations: ["both", "third", "fifth"],
  shared_code_order: ["frequency", "windows"],
  remember_candidates: [true, false],
  loose_spelling: [true, false],
  continuous_typing: [true, false],
  colloquial_priority: [true, false],
  space_mode: ["english", "chinese"],
  smart_space_removal: [true, false],
  auto_capitalize: [true, false],
  emoji_keywords: [true, false],
  english_chinese: [true, false],
  clipboard_history: [true, false],
  keyboard_height: ["compact", "standard", "tall"],
  key_labels: ["letters", "radicals", "letters_only"],
  number_row: [true, false],
  key_preview: [true, false],
  key_vibration: ["system", "off", "light", "standard", "strong"],
  key_sound: [true, false],
};

/** 接受的功能次數，和 App 的 UsageEvent 一致。 */
export const COUNTS: readonly string[] = [
  "chinese",
  "association",
  "english",
  "englishSuggestion",
  "translationCandidate",
  "emoji",
  "keywordEmoji",
  "candidateGrid",
  "punctuationMenu",
  "cursorSwipe",
  "deleteSwipe",
  "copy",
  "paste",
  "selectAll",
  "clipboardPanel",
  "clipboardItem",
  "phrase",
  "savePhrase",
  "openSettings",
  "translate",
  "rewrite",
  "voiceInput",
  "forget",
  "keyboardSwitch",
  "hideKeyboard",
];

const SCHEMA = 1;
const MAX_COUNT = 1_000_000;
// App 最多留 7 日未送出的報告；手機的日期可能比伺服器的 UTC 日期快一日。
const OLDEST_DAYS = 8;
const NEWEST_DAYS = 1;
const DAY_MILLIS = 24 * 60 * 60 * 1000;

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isRecentDay(day: string, now: Date): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(day)) return false;
  const time = Date.parse(`${day}T00:00:00Z`);
  if (Number.isNaN(time) || new Date(time).toISOString().slice(0, 10) !== day) return false;
  const today = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
  return time >= today - OLDEST_DAYS * DAY_MILLIS && time <= today + NEWEST_DAYS * DAY_MILLIS;
}

/**
 * 檢查報告；有任何不合格就整份不收（回傳 null）。不認得的設定和次數略過，
 * 方便日後新版 App 加入新項目。
 */
export function parseReport(body: unknown, now: Date): DailyReport | null {
  if (!isObject(body) || body.schema !== SCHEMA) return null;
  const { day, app, sdk, settings, counts } = body;
  if (typeof day !== "string" || !isRecentDay(day, now)) return null;
  if (typeof app !== "string" || !/^\d{1,3}\.\d{1,3}\.\d{1,3}$/.test(app)) return null;
  if (typeof sdk !== "number" || !Number.isInteger(sdk) || sdk < 28 || sdk > 99) return null;
  if (!isObject(settings) || !isObject(counts)) return null;

  const report: DailyReport = { day, app, sdk, settings: {}, counts: {} };
  for (const [key, value] of Object.entries(settings)) {
    const allowed = SETTINGS[key];
    if (allowed === undefined) continue;
    if ((typeof value !== "string" && typeof value !== "boolean") || !allowed.includes(value)) return null;
    report.settings[key] = value;
  }
  for (const [name, times] of Object.entries(counts)) {
    if (!COUNTS.includes(name)) continue;
    if (typeof times !== "number" || !Number.isInteger(times) || times < 0 || times > MAX_COUNT) return null;
    if (times > 0) report.counts[name] = times;
  }
  return report;
}

export interface Statement {
  sql: string;
  params: (string | number)[];
}

/** 把報告加進每日的合計；只存合計數字，不存報告本身。 */
export function upserts(report: DailyReport): Statement[] {
  const statements: Statement[] = [
    {
      sql:
        "INSERT INTO daily_devices (day, app, sdk, reports) VALUES (?1, ?2, ?3, 1) " +
        "ON CONFLICT (day, app, sdk) DO UPDATE SET reports = reports + 1",
      params: [report.day, report.app, report.sdk],
    },
  ];
  for (const [setting, value] of Object.entries(report.settings)) {
    statements.push({
      sql:
        "INSERT INTO daily_settings (day, setting, value, reports) VALUES (?1, ?2, ?3, 1) " +
        "ON CONFLICT (day, setting, value) DO UPDATE SET reports = reports + 1",
      params: [report.day, setting, String(value)],
    });
  }
  for (const [name, total] of Object.entries(report.counts)) {
    statements.push({
      sql:
        "INSERT INTO daily_counts (day, name, reports, total) VALUES (?1, ?2, 1, ?3) " +
        "ON CONFLICT (day, name) DO UPDATE SET reports = reports + 1, total = total + excluded.total",
      params: [report.day, name, total],
    });
  }
  return statements;
}
