<p align="center"><img src="docs/play/graphics/icon-512.png" width="112" alt=""></p>

# GInput 中英混打輸入法

[English](#english)

GInput 是為香港人而設的 Android 輸入法：同一個鍵盤打中文和英文，不用切換。打英文照打，打中文就打速成、倉頡碼或粵拼，再在候選列選字。

## 功能

- 中英混打：同一個鍵盤，不用切換
- 速成和倉頡：三代和五代的碼都接受
- 粵拼：不用打聲調，可以打整個詞（neihou →「你好」）；也接受懶音和港式拼法（lei →「你」、cheung →「張」）
- 幾種輸入方案可以同時啟用
- 粵語口語字優先：嘅、咗、啲、冇排在前面，聯想加入口語詞
- 英文建議、智能全形標點、智能刪空格
- Emoji 面板、剪貼簿和常用短語、語音輸入
- 翻譯和改寫：譯成英文、書面語或粵語口語，或者改寫語氣、改錯字

## 下載

GInput 正在封閉測試，即將在 Google Play 推出。下載頁：<https://ginput.jetpiggy.com/>

## 私隱

你打的內容只留在手機上。唯一的例外是你按「翻譯」或「改寫」的選項時：那段文字會經 GInput 的伺服器（`backend/ai`）交 Cloudflare 的 AI 處理，伺服器不會記錄。當機回報不包括輸入內容，可以關閉；使用統計預設關閉。詳情見[私隱政策](https://ginput.jetpiggy.com/privacy.html)。

## 建置

需要 JDK 17 和 Android SDK（compileSdk 36）：

```bash
./gradlew :app:assembleDebug
./gradlew :engine:test :app:testDebugUnitTest :app:lintDebug
```

可選的設定（例如 Sentry 的 DSN）見 `local.properties.example`；沒有 DSN 就不啟動當機回報。`backend/` 的 Worker 用 Node.js：`npm install`、`npm test`。

## 結構

| 位置 | 內容 |
|---|---|
| `app/` | 輸入法和設定 App（Kotlin、Jetpack Compose） |
| `engine/` | 速成、倉頡、粵拼和英文的引擎（純 Kotlin 加 SQLite） |
| `backend/ai/` | 雲端翻譯和改寫的 Cloudflare Worker |
| `backend/usage-stats/` | 使用統計的 Cloudflare Worker |
| `tools/dictgen/` | 建置字庫 |
| `tools/icon/` | 產生圖示和 Play 商店圖片 |
| `tools/ai-eval/` | 比較雲端 AI 模型的翻譯和改寫 |
| `docs/spec/v1.md` | 規格 |
| `docs/adr/` | 技術決定 |
| `CONTEXT.md` | 用語 |

## 授權

GInput 以 [Apache License 2.0](LICENSE) 授權，以下除外（詳見 [NOTICE](NOTICE)）：

- **品牌圖像**：GInput 和 JetPiggy 的名稱、App 圖示和 JetPiggy 飛豬的圖像保留所有權利。發佈修改版時，請改用自己的名稱和圖示。
- **英文詞庫**：`app/src/main/assets/dict/english-1.db` 以 CC BY-SA 4.0 授權。
- 第三方的程式庫和資料保留各自的授權。

提交的貢獻同樣以 Apache License 2.0 授權。

## 聯絡

意見和問題：<ginput@jetpiggy.com>。保安漏洞請按 [SECURITY.md](SECURITY.md) 私下報告。

---

<a id="english"></a>

## GInput: Chinese and English in one keyboard

GInput is an Android keyboard made for Hong Kong. Type English as usual, or type Quick (Sucheng), Cangjie or Jyutping and pick the Chinese from the candidate bar, without switching keyboards.

### Features

- Chinese and English on one keyboard, no switching
- Quick and Cangjie, accepting both 3rd and 5th generation codes
- Jyutping without tones, including whole words (neihou → 你好), lazy pronunciations and Hong Kong spellings (lei → 你, cheung → 張)
- Turn on more than one input scheme at the same time
- Colloquial Cantonese first: 嘅, 咗, 啲 and 冇 come first, and suggestions include colloquial words
- English suggestions, smart full-width punctuation and smart space removal
- Emoji panel, clipboard and saved phrases, voice input
- Translate and Rewrite: translate into English, written Chinese or colloquial Cantonese, change the tone, or fix typos

### Download

GInput is in closed testing and coming soon to Google Play. Website: <https://ginput.jetpiggy.com/>

### Privacy

What you type stays on your phone. The only exception is when you tap a Translate or Rewrite option: that text goes through GInput's server (`backend/ai`) to Cloudflare's AI, and the server doesn't keep it. Crash reports never include what you type and can be turned off. Usage statistics are off by default. See the [privacy policy](https://ginput.jetpiggy.com/privacy.html).

### Building

You need JDK 17 and the Android SDK (compileSdk 36):

```bash
./gradlew :app:assembleDebug
./gradlew :engine:test :app:testDebugUnitTest :app:lintDebug
```

Optional settings, such as a Sentry DSN, are listed in `local.properties.example`; without a DSN, crash reporting stays off. The Workers in `backend/` use Node.js: `npm install`, `npm test`.

The documentation in this repository is written in Traditional Chinese (Hong Kong).

### License

GInput is licensed under the [Apache License 2.0](LICENSE), with these exceptions (see [NOTICE](NOTICE)):

- **Brand assets**: the GInput and JetPiggy names, the app icon and the JetPiggy flying pig artwork are all rights reserved. If you distribute a modified version, use your own name and icon.
- **English word list**: `app/src/main/assets/dict/english-1.db` is licensed under CC BY-SA 4.0.
- Third-party libraries and data keep their own licenses.

Contributions are accepted under the Apache License 2.0.

### Contact

Feedback and questions: <ginput@jetpiggy.com>. Please report security issues privately as described in [SECURITY.md](SECURITY.md).
