# AI 模型比較工具

用同一批測試句，比較雲端 AI 模型做翻譯和改寫（ADR 0005）的質素、速度和成本。只在開發時用，不會打包進 App。2026-10-11 的比較結論記在 ADR 0006。

## 檔案

- `cases.json`：測試句，全部原創。每句有 `id`、`task`（工作）和 `text`，另外可以有：
  - `expect`／`forbid`：結果必須有／不可以有的字詞，寫成正規表示式，不分大小寫
  - `same`：原句沒有錯，結果應該和原句一樣
  - `trap`：陷阱句，即問題或指示。模型應該照譯，不可以回答或照做
  - `maxLength`、`note`
- `common.mjs`：候選模型和結果檔案的位置。提示和溫度來自 `backend/ai/src/prompts.ts`，和 Worker 用的是同一份。
- `run.mjs`：執行比較，結果寫入 `build/ai-eval/results/<模型>.jsonl`。已有結果的句子不會重做。
- `report.mjs`：自動檢查結果，在終端機印出摘要，並把逐句對照寫入 `build/ai-eval/report.md`。

## 工作

| `task` | 內容 |
|---|---|
| `to-en` | 譯成英文；原文可以是粵語、書面語或中英夾雜 |
| `to-yue` | 英文譯成粵語口語 |
| `to-zh` | 英文譯成書面語 |
| `written` | 改寫成書面語 |
| `colloquial` | 改寫成粵語口語 |
| `polite` | 改得客氣 |
| `casual` | 改得輕鬆 |
| `fix` | 改錯字 |

## 執行

Workers AI 經 wrangler 的 `getPlatformProxy` 在本機呼叫，不會部署 Worker，但要先登入 Cloudflare：`npx wrangler login`，由使用者自己登入。

```bash
cd tools/ai-eval
npm install
node run.mjs
node report.mjs
```

- `node run.mjs` 預設跑全部免費方案的模型和全部測試句。用 `--models gemma-4-26b,qwen3.8-27b` 指定模型；用 `--cases yue-,fix-01` 指定測試句，可以寫 id 或以「-」結尾的前綴。
- Workers AI 每日（UTC）有 10,000 neurons 免費額度。`run.mjs` 按回應的 neurons 累計用量，記在 `build/ai-eval/neurons.json`；預設用到 9,000 就停，用 `--budget` 可以改。翌日再跑，會接着做未完成的句子。
- 改了提示或模型設定之後要重做，就加 `--force`，或刪掉 `build/ai-eval/results/` 裏那個模型的檔案。改了 `backend/ai/src/prompts.ts` 的話，先用 Gemma 4 26B 重跑全部測試句，確認沒有變差，才部署 Worker。
- DeepSeek V4 Flash 和 GLM 5.3 Flash 要 Workers 付費方案，要用 `--models` 明確指定才會跑。

## 私隱

- 測試句全部是原創的例句，不可以放入真實的輸入內容。
- 不經 AI Gateway，所以 Cloudflare 不會記錄提示。

## 自動檢查的限制

自動檢查只找出明顯的問題：
- 簡體字和特殊字元
- 語體不對：書面語夾粵語字，或者口語不夠口語
- 改了語言，或者陷阱句答了問題
- 改錯字改動太多
- 欠缺或多了指定的字詞

譯得準不準、自不自然，要人看逐句對照。延遲是在本機經 wrangler 的遠端連線量度的，比 Worker 直接呼叫 Workers AI 慢。
