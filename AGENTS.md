# GInput

GInput 是 Android 輸入法，讓香港使用者中英混打，不用切換。原始碼以 Apache License 2.0 公開，品牌圖像除外（ADR 0007、`NOTICE`）。

## 先讀甚麼

- 命名、寫文件、和使用者討論功能時：用 `CONTEXT.md` 的用語，程式碼用每個用語括號內的英文名。遇到新概念，先在 `CONTEXT.md` 定名。
- 改動任何行為之前：在 `docs/spec/v1.md` 查這個功能屬於哪個版本、規則是甚麼。
- 做技術選擇之前：查 `docs/adr/`。要推翻 ADR 的決定，先問使用者；使用者同意後，新增一份 ADR 取代舊的，並把舊的標為 superseded。
- 改動字庫的資料或建置方法之前：讀 `tools/dictgen/README.md`。
- 改動使用統計之前：讀 `backend/usage-stats/README.md`。改動雲端翻譯和改寫的 Worker 或提示之前：讀 `backend/ai/README.md`。
- 寫文件和 App 介面文字：用繁體中文（香港用語）。

## 硬規則

- 產生或安裝 APK、發佈和 push 都要等使用者明確要求才做；編譯和跑單元測試可以直接做。
- 這個 repo 是公開的：不可以加入個人姓名、私人電郵、個人帳戶名、本機路徑或秘密（金鑰、密碼、DSN、token）。聯絡一律用 jetpiggy.com 的地址。
- 加入會打包進 App 的程式庫、字庫或任何資料之前，先確認它是寬鬆授權：Apache、MIT、BSD、ISC、CC0、CC-BY、Unicode 授權或公有領域（ADR 0004、0007）。GPL、LGPL 和禁止商用的一律不用。CC-BY-SA 只限資料，而且要獨立成檔，檔案本身以 CC BY-SA 4.0 授權、寫明出處，不可加密。加入之後，更新 App 內的「開源授權」頁（`app/src/main/java/com/jetpiggy/ginput/settings/LicensesScreen.kt` 的 `Licenses`）。
- 輸入內容只留在手機上：日誌、當機報告和使用統計都不可以帶有輸入內容（ADR 0005）。唯一的例外是使用者主動按「翻譯」或「改寫」時送出的那段文字。會傳送資料的功能有改動時，提醒使用者私隱政策和 Play 的資料安全表格要一起改。
- 使用者有未 commit 的工作時，只 `git add` 自己改過的檔案。
