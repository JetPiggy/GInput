---
status: accepted
---

# 以 Apache License 2.0 開源，品牌圖像除外

ADR 0004 決定大約 1.0 時開源，授權在 GPL-3.0 加 Google Play 例外和 Apache-2.0 之間再選。我們選 Apache-2.0：

- 程式碼、建置工具、Worker 和文件以 Apache License 2.0 授權，根目錄有 `LICENSE` 和 `NOTICE`。
- 品牌圖像不在授權範圍內：GInput 和 JetPiggy 的名稱、App 圖示、`tools/icon/art/` 和 `docs/play/graphics/` 的圖片。別人發佈修改版，要改用自己的名稱和圖示（`NOTICE`）。
- 英文詞庫 `english-*.db` 維持 CC BY-SA 4.0（ADR 0004）。
- 公開 repo `JetPiggy/GInput` 由一個全新的 commit 開始，commit 用 JetPiggy 的名義。維護用的文件（例如發佈步驟和帳戶設定）、研究筆記，以及圖示的 AI 原圖和參考圖不放進公開 repo。

## Considered Options

- GPL-3.0 加 Google Play 例外：別人修改後再發佈，也要公開原始碼，對輸入法的私隱較有保障；但要另寫 Play 例外，日後想改授權或加入閉源的部分，又要貢獻者另簽協議。
- 連同 AI 原圖、參考圖和研究筆記一併公開：可以重現整個過程，但原圖和角色設定圖令別人更容易仿製飛豬，研究筆記也未逐條核實。

## Consequences

- 任何人都可以修改和發佈 GInput，包括閉源的版本，只要保留 `LICENSE` 和 `NOTICE`，並且不用我們的名稱和圖示。
- 程式庫和資料仍然只用寬鬆授權（ADR 0004）；GPL 和 LGPL 的東西會令整個 App 不能再以 Apache-2.0 發佈。
- 別人提交的貢獻同樣以 Apache License 2.0 授權（授權第 5 條），不用另簽協議。
- 公開 repo 不可以有個人資料、私人電郵、個人帳戶名、本機路徑或秘密。
