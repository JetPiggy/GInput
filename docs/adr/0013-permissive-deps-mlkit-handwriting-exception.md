---
status: accepted
---

# 程式庫和資料只用寬鬆授權；手寫用的 ML Kit 是唯一的閉源例外

這份 ADR 取代 ADR 0004。ADR 0004 的開源步驟已經完成（ADR 0007），以下是現行的授權規則，加上一個例外。

程式庫和資料只用 Apache、MIT、BSD、ISC、CC0、CC-BY、Unicode 授權和公有領域的東西，GPL、LGPL 和禁止商用的一律不用。速成、倉頡、粵拼和英文的引擎繼續用純 Kotlin 加 SQLite 自己寫，不寫 C++。

CC-BY-SA 的資料可以用，但要獨立成檔，不可以和其他資料合併在同一個檔案：

- 那個資料檔以 CC BY-SA 4.0 授權，檔案內寫明授權、出處和我們改動了甚麼。
- App 的「開源授權」頁列明哪個檔案以 CC BY-SA 4.0 授權、它的出處，以及上游要求附上的聲明。
- 資料檔不加密、不混淆，任何人都可以從 APK 取出來用。

例外：手寫用 Google ML Kit 的數位墨水辨識（`com.google.mlkit:digital-ink-recognition`）。它按 ML Kit 服務條款提供，不是開源，條款禁止反向工程和抽取模型；它連帶打包 Google Play 服務的基本程式庫（`play-services-base`、`play-services-basement`、`play-services-tasks`，Android 軟件開發套件授權）。條件：

- 手寫預設關閉。使用者在設定打開手寫之前，App 不初始化 ML Kit，也不下載模型（ADR 0014）。
- 只用在手寫，不用 ML Kit 做其他功能。再加任何閉源元件，要另寫 ADR。
- 「開源授權」頁照樣列出 ML Kit 和 Google Play 服務程式庫，寫明不是開源和適用的條款。

## Considered Options

- PaddleOCR 的 PP-OCRv5 流動版辨識模型（Apache 2.0）：完全符合授權規則，也完全離線；但它看的是圖片，不看筆順，手寫中文的準確度未經證實，又要打包 ONNX Runtime 一類的原生程式庫和十幾 MB 的模型。
- Zinnia 加 Tegaki 的繁體中文模型：引擎是寬鬆授權，但模型是 LGPL 2.1。
- Make Me a Hanzi、Hanzi Writer、HanziLookup：資料是 Arphic 授權、LGPL 或 GPL。
- 用 CASIA 這類手寫資料集自己訓練模型：資料集只准研究用途。
- 雲端手寫辨識：寫的字就是輸入內容，送出去違反 ADR 0014。
- 用 ML Kit 數位墨水辨識（採用）：按筆劃辨識，有香港中文的模型（`zh-Hani-HK`），模型下載後在手機上辨識，質素最好。

## Consequences

- GInput 仍然以 Apache License 2.0 發佈（ADR 0007），但 APK 含有閉源的 ML Kit 和 Google Play 服務程式庫。從原始碼建置的人，Gradle 會從 Google 的 Maven 下載它們；不想要的話，刪掉這個依賴和 `handwriting` 套件就可以建置沒有手寫的版本。
- 不能上 F-Droid（規格本來就不做）。
- ML Kit 連帶加入 WorkManager、Room、Firebase Components 和 Data Transport、OkHttp、Okio 等 Apache 2.0 的程式庫，「開源授權」頁要列出。
- 現成的開源中文輸入法（Trime、LIME、fcitx5-android）都是 GPL 或 LGPL，所以仍然不 fork 它們；fork FlorisBoard、Jyutping 或用 librime 的理由見 ADR 0002，結論不變。
- 其他資料來源和 ADR 0004 相同：倉頡和速成用 libcangjie（公有領域）；字頻和聯想用 words.hk 分析檔（公有領域）；Emoji 用 Unicode 的 emoji-test.txt（Unicode 授權）；英文詞庫 `english-<版本>.db` 用 wordfreq 的詞頻，以 CC BY-SA 4.0 授權。
- CC-CEDICT、CC-Canto 這類 CC-BY-SA 資料日後也可以用，但同樣要獨立成檔。words.hk 完整詞典（禁止商用）和教育局字詞表（沒有開放授權）仍然不能用。
- `AGENTS.md` 的硬規則加上這個例外。
