# 使用統計 Worker

Cloudflare Worker 加 D1，收 GInput 的使用統計（ADR 0001；規則見 `docs/spec/v1.md` 的「使用統計」）。

- 網址：`https://ginput-stats.jetpiggy.com/v1/daily`，只接受 `POST`。
- App 每部裝置每日最多送一份報告，內容只有日期、App 和 Android 版本、主要設定和各功能的次數，沒有任何識別碼。
- Worker 只把報告加進每日的合計數字（`daily_devices`、`daily_settings`、`daily_counts`），不保存報告本身，不記錄 IP，也不開 Workers 日誌。
- 報告有任何一項不合格就整份不收（400）。不認得的設定和次數略過，方便日後新版 App 加入新項目。

## 改動報告格式

App 和 Worker 要一起改：

- 次數：App 的 `usagestats/UsageEvent.kt` 和這裏 `src/report.ts` 的 `COUNTS`
- 設定：App 的 `SettingsPreferences.forUsageStats` 和這裏 `src/report.ts` 的 `SETTINGS`
- 不相容的改動：兩邊的 `schema` 一起加一，Worker 要同時接受新舊版本，直至舊版 App 不再使用

## 開發

```bash
npm install
npm test
npm run check
npm run db:local
npm run dev
```

## 部署

部署由使用者決定（`AGENTS.md`）。`wrangler.jsonc` 是 `ginput-stats.jetpiggy.com` 的設定（D1 資料庫 `ginput-usage-stats`）；部署到別的 Cloudflare 帳戶，要換上自己的 D1 `database_id` 和網域。

```bash
npx wrangler login
npm run db:remote
npm run deploy
```

`npm run db:remote` 只在第一次部署和加了新的 `migrations/` 檔案時需要。

## 查看數據

```bash
npx wrangler d1 execute ginput-usage-stats --remote --command "SELECT day, SUM(reports) AS devices FROM daily_devices GROUP BY day ORDER BY day DESC LIMIT 14"
npx wrangler d1 execute ginput-usage-stats --remote --command "SELECT name, SUM(reports) AS devices, SUM(total) AS total FROM daily_counts WHERE day >= date('now', '-7 days') GROUP BY name ORDER BY total DESC"
npx wrangler d1 execute ginput-usage-stats --remote --command "SELECT setting, value, SUM(reports) AS devices FROM daily_settings WHERE day >= date('now', '-7 days') GROUP BY setting, value ORDER BY setting, devices DESC"
```
