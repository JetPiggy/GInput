# 雲端翻譯和改寫 Worker

Cloudflare Worker 加 D1，處理 GInput 的雲端翻譯和改寫（ADR 0005、0006；規則見 `docs/spec/v1.md` 的「雲端翻譯和改寫」）。

- 網址：`https://ginput-ai.jetpiggy.com/v1/text`，只接受 `POST`。請求是 `{"task": "to-en", "text": "…"}`，成功時回覆 `{"result": "…"}`。
- 工作：翻譯是 `to-en`、`to-zh`、`to-yue`；改寫是 `written`、`colloquial`、`polite`、`casual`、`fix`。
- 模型：Workers AI 的 Gemma 4 26B（`@cf/google/gemma-4-26b-a4b-it`），關掉推理。經 AI binding 直接呼叫，不經 AI Gateway。
- 私隱：不記錄原文和結果，不開 Workers 日誌。D1 只存每日（UTC）的請求數和 neurons 合計（`daily_usage`），沒有原文、IP 或任何識別碼。
- 上限：
  - 每部手機每日 50 次由 App 數；Worker 不知道請求來自哪部手機。
  - 每個 IP 每分鐘 30 次（Rate Limiting binding，只在 Cloudflare 的記憶體計算）。
  - 全部請求每日合共的 neurons：`wrangler.jsonc` 的 `DAILY_NEURONS`，預設 10,000，即 Workers 免費方案的每日額度。轉付費方案後按預算調高。
- 回應：
  - 200：結果
  - 400：請求格式不對
  - 413：原文超過 1,000 字
  - 429：到了每分鐘上限
  - 503：到了每日上限、資料庫出錯，或 Workers AI 未能處理（包括免費方案用完當日的 neurons）
  - 502：結果不完整或空白

## 提示

提示在 `src/prompts.ts`，`tools/ai-eval` 用同一份比較模型。改提示或換模型時：

1. 用 `tools/ai-eval` 重跑 Gemma 4 26B 的全部測試句，確認沒有變差（見 `tools/ai-eval/README.md`）。
2. 跑這裏的 `npm test`。
3. 部署 Worker。

## 改動請求格式

App 和 Worker 要一起改：

- 工作：App 的 `cloudtext/TextTask.kt` 和這裏 `src/prompts.ts` 的 `TASKS`
- 原文上限：App 的 `SourceText.MAX_LENGTH` 和這裏 `src/text.ts` 的 `MAX_TEXT_LENGTH`

先部署 Worker，再發佈 App。

## 開發

```bash
npm install
npm test
npm run check
npm run db:local
npm run dev
```

`npm run dev` 時 Workers AI 一樣連到 Cloudflare，會用掉帳戶的 neurons，要先 `npx wrangler login`。

## 部署

部署由使用者決定（`AGENTS.md`）。`wrangler.jsonc` 是 `ginput-ai.jetpiggy.com` 的設定（D1 資料庫 `ginput-ai`）；部署到別的 Cloudflare 帳戶，要換上自己的 D1 `database_id`、網域和 Rate Limiting 的 `namespace_id`。

```bash
npx wrangler login
npm run db:remote
npm run deploy
```

`npm run db:remote` 只在第一次部署和加了新的 `migrations/` 檔案時需要。

部署後試一次：

```bash
curl -s https://ginput-ai.jetpiggy.com/v1/text -H "Content-Type: application/json" -d '{"task":"to-yue","text":"I have eaten."}'
```

Rate Limiting 的 `namespace_id`（1001）是帳戶內自訂的編號，不可以和其他 Worker 的重複。

## 查看用量

```bash
npx wrangler d1 execute ginput-ai --remote --command "SELECT day, requests, ROUND(neurons) AS neurons FROM daily_usage ORDER BY day DESC LIMIT 14"
```

Cloudflare 主控台的 Workers AI 頁也有每日用量。
