---
status: accepted
---

# 雲端翻譯和改寫用 Workers AI 的 Gemma 4 26B，每日次數在手機上數

ADR 0005 容許雲端翻譯和改寫送出原文。實行時還要決定兩件事：用哪個 AI 服務，以及怎樣限制每部手機每日用多少次，而又不送出任何識別碼。

- AI 服務用 Cloudflare Workers AI 的 Gemma 4 26B（`@cf/google/gemma-4-26b-a4b-it`，關掉推理）。App 把工作和原文送往我們自己的 Worker（`backend/ai`），Worker 經 AI binding 呼叫模型：不用保管 API 金鑰，資料也只經 Cloudflare。2026-10-11 用 `tools/ai-eval` 的 165 句原創測試句比較了 Workers AI 免費方案可以用的 5 個模型：Gemma 4 26B 質素最好，陷阱句全部照譯，延遲和成本都低。
- 每部手機每日可以用 50 次，雲端翻譯和改寫合計，按手機的本地日期，只在手機上數。請求只有工作和原文，沒有識別碼。
- 伺服器另設兩道上限，防止有人繞過手機上的次數：每個 IP 每分鐘的請求數（Workers 的 Rate Limiting，只在記憶體計算），以及全部請求每日（UTC）合共用的 neurons。到了上限，App 顯示「暫時未能使用」。

## Considered Options

- Vertex AI 的 Gemini：要另開 Google Cloud 帳戶，價錢是 Gemma 的 2 至 9 倍，資料也要多交一間公司處理。使用者決定不比較，直接用 Gemma 4 26B。
- Workers AI 的其他模型：Qwen3.8 27B 的粵語夾普通話用語，成本是 Gemma 的 6.6 倍；gpt-oss-120b 會答陷阱句，結果夾特殊字元；GLM 4.7 Flash 改書面語常常原文照搬；Qwen3-30B 有簡體字。
- 在伺服器按裝置數次數：要送出裝置識別碼，違反 ADR 0005「只送出要處理的那段文字」。
- 每次請求用 Play Integrity 驗證：從 GInput-updates 安裝的 APK 用不到，每次又多一個連線；暫時不值得。

## Consequences

- 清除 App 資料或改手機日期，就可以多用；暫時接受，成本由伺服器的每日上限封頂。
- 有人直接呼叫 Worker 的話，可能用光當日的上限，其他人當日就用不到。發現濫用時，再加裝置驗證。
- Workers 免費方案每日有 10,000 neurons，以每次約 2 neurons 計，約夠 5,000 次；用完 Workers AI 就停止。使用者多了就要轉付費方案（每月 US$5，超出每日免費額度後每 1,000 neurons US$0.011），並調高 Worker 的每日上限。
- 提示放在 `backend/ai/src/prompts.ts`，`tools/ai-eval` 用同一份提示比較模型。改提示或換模型之前，先用 `tools/ai-eval` 重新比較。
- 私隱政策和 Play 的資料安全表格要寫明：原文經 Cloudflare 處理，由 Cloudflare 執行 Google 的 Gemma 模型，不會交給 Google；GInput 的伺服器不記錄原文，Cloudflare 不用它訓練模型。
