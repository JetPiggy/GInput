<p align="center"><img src="docs/play/graphics/icon-512.png" width="112" alt=""></p>

# GInput 中英混打輸入法

[English](#english)

GInput 是為香港人而設的 Android 輸入法：同一個鍵盤打中文和英文，不用切換。打英文照打，打中文就打速成、倉頡碼或粵拼，再在候選列選字。

**[在 Google Play 下載](https://play.google.com/store/apps/details?id=com.jetpiggy.ginput)**　·　[下載 APK](https://github.com/JetPiggy/GInput/releases/latest/download/GInput.apk)　·　[所有版本](https://github.com/JetPiggy/GInput/releases)

## 功能

- 中英混打：同一個鍵盤，不用切換
- 速成和倉頡：三代和五代的碼都接受
- 符號碼：速成和倉頡可以打標點，碼跟 Windows（倉頡 zxad、速成 zd →「。」）
- 連打：速成和倉頡可以連續打幾個字的碼，候選列出整句（hivddpof →「我好想你」），會記住你打過的搭配；預設關閉
- 粵拼：不用打聲調，可以打整個詞（neihou →「你好」）；也接受懶音和港式拼法（lei →「你」、cheung →「張」）
- 幾種輸入方案可以同時啟用
- 粵語口語字優先：嘅、咗、啲、冇排在前面，聯想加入口語詞
- 英文建議、智能全形標點、智能刪空格
- 英漢詞典：打完整個英文字，候選列也列出中文（duck →「鴨」）；預設開啟
- 手寫：在工具列按「手寫」，用手指寫字，停一停就出字；由 Google ML Kit 在手機上辨識
- 分邊空白鍵：空白鍵分成兩半，一半選中文、一半寫英文
- Emoji 面板、剪貼簿和常用短語、語音輸入
- 翻譯和改寫：譯成英文、書面語或粵語口語，或者改寫語氣、改錯字
- 26 套主題，分經典、漸變、香港、甜品、自然、風格六個系列，每套有淺色和深色，可以跟隨系統或固定一種
- 鍵盤高度：長按工具列的「設定」，在鍵盤上拖動調整（80% 至 125%），調整時可以照常打字

## 下載

GInput 在 Google Play 上架，免費，無廣告：**[在 Google Play 下載](https://play.google.com/store/apps/details?id=com.jetpiggy.ginput)**。從 Google Play 安裝會自動更新。

沒有 Google Play 的手機可以[下載 APK](https://github.com/JetPiggy/GInput/releases/latest/download/GInput.apk)：按下就直接下載 `GInput.apk`，安裝方法見下面。每個版本的更新內容見 [Releases](https://github.com/JetPiggy/GInput/releases)；網站：<https://ginput.jetpiggy.com/>

## 如何安裝

需要 Android 9 或以上。從 Google Play 安裝的話，按「安裝」就可以，跳到第 4 步。用 APK 安裝：

1. 用手機按上面的「下載 APK」。
2. 下載完成後打開 `GInput.apk`。第一次從瀏覽器或檔案管理員安裝 App，Android 會要求允許這個來源安裝 App：按「設定」，開啟允許這個來源的選項，返回再按「安裝」。
3. 如果 Google Play 保護機制彈出提示，按「更多詳情」再按「仍要安裝」。Samsung 手機開了自動封鎖程式（Auto Blocker）的話，要先在設定裏關閉它才可以安裝。
4. 打開 GInput，跟着設定精靈做：在手機的鍵盤設定開啟 GInput，再選它做目前的輸入法。Android 開啟任何輸入法都會提醒它可以收集你打的字；GInput 打的內容只留在手機上，見下面的「私隱」。

### 更新

從 Google Play 安裝的會自動更新。用 APK 安裝的，有新版本時 GInput 設定頁的最上面會顯示提示：按「在 Google Play 更新」，在 Google Play 按「更新」就可以，之後會自動更新；沒有 Google Play 的話按「下載 APK」，直接安裝蓋過舊版。兩種方法設定和記憶字都會保留；不要先解除安裝，否則記憶字和剪貼簿歷史會一併刪除。

### 核對 APK（可選）

GInput 的 APK 和 Google Play 版全部用同一把金鑰簽署，所以可以互相蓋過；簽署憑證的 SHA-256 指紋是：

```text
CF:DB:16:7E:A8:A9:0C:D2:5D:0F:E8:7D:D0:D3:07:7A:F3:E8:5F:D7:A6:0C:A8:6C:FC:FE:C6:18:5A:6F:58:07
```

Android 只容許同一把金鑰簽署的版本蓋過舊版；指紋不同的 APK 不是 JetPiggy 發佈的。

## 私隱

你打的內容只留在手機上。唯一的例外是你按「翻譯」或「改寫」的選項時：那段文字會經 GInput 的伺服器（`backend/ai`）交 Cloudflare 的 AI 處理，伺服器不會記錄。手寫的字也只在手機上辨識，但手寫用的 Google ML Kit 會把不含你寫的字的診斷資料送往 Google；第一次用手寫時會先說明，你同意後才開始。當機回報不包括輸入內容，可以關閉；使用統計預設關閉。詳情見[私隱政策](https://ginput.jetpiggy.com/privacy.html)。

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
- **手寫**：Google ML Kit 數位墨水辨識和連帶的 Google Play 服務程式庫不是開源，按 Google 的條款提供（ADR 0013）。從原始碼建置時 Gradle 會從 Google 下載；不想要的話，刪掉這個依賴和 `handwriting` 套件就可以建置沒有手寫的版本。
- 第三方的程式庫和資料保留各自的授權。

提交的貢獻同樣以 Apache License 2.0 授權。

## 聯絡

意見和問題：<ginput@jetpiggy.com>。保安漏洞請按 [SECURITY.md](SECURITY.md) 私下報告。

---

<a id="english"></a>

## GInput: Chinese and English in one keyboard

GInput is an Android keyboard made for Hong Kong. Type English as usual, or type Quick (Sucheng), Cangjie or Jyutping and pick the Chinese from the candidate bar, without switching keyboards.

**[Get it on Google Play](https://play.google.com/store/apps/details?id=com.jetpiggy.ginput)**　·　[Download the APK](https://github.com/JetPiggy/GInput/releases/latest/download/GInput.apk)　·　[All releases](https://github.com/JetPiggy/GInput/releases)

### Features

- Chinese and English on one keyboard, no switching
- Quick and Cangjie, accepting both 3rd and 5th generation codes
- Punctuation in Quick and Cangjie with the same codes as Windows (zxad in Cangjie or zd in Quick → 。)
- Continuous typing: type the codes of several characters in a row in Quick or Cangjie and pick the whole sentence (hivddpof → 我好想你); it learns the character pairs you type. Off by default
- Jyutping without tones, including whole words (neihou → 你好), lazy pronunciations and Hong Kong spellings (lei → 你, cheung → 張)
- Turn on more than one input scheme at the same time
- Colloquial Cantonese first: 嘅, 咗, 啲 and 冇 come first, and suggestions include colloquial words
- English suggestions, smart full-width punctuation and smart space removal
- English-Chinese dictionary: after a whole English word, the candidate bar also lists the Chinese (duck → 鴨). On by default
- Handwriting: tap Handwriting on the toolbar and write with your finger; each character goes in when you pause. Google ML Kit recognises it on your phone
- Split space bar: one half picks Chinese, the other writes English
- Emoji panel, clipboard and saved phrases, voice input
- Translate and Rewrite: translate into English, written Chinese or colloquial Cantonese, change the tone, or fix typos
- 26 themes in six series (Classic, Gradient, Hong Kong, Dessert, Nature and Style), each in light and dark, following your system or fixed
- Keyboard height: long-press Settings on the toolbar and drag on the keyboard (80% to 125%), typing as you go

### Download

GInput is on Google Play, free and with no ads: **[Get it on Google Play](https://play.google.com/store/apps/details?id=com.jetpiggy.ginput)**. Installing from Google Play keeps it up to date automatically.

On a phone without Google Play, [download the APK](https://github.com/JetPiggy/GInput/releases/latest/download/GInput.apk): the link downloads `GInput.apk` straight away; see below for how to install it. See [Releases](https://github.com/JetPiggy/GInput/releases) for what's new in each version. Website: <https://ginput.jetpiggy.com/>

### How to install

You need Android 9 or later. From Google Play, just tap Install and skip to step 4. To install the APK:

1. On your phone, tap "Download the APK" above.
2. When the download finishes, open `GInput.apk`. The first time you install an app from your browser or file manager, Android asks you to allow that source: tap Settings, turn on "Allow from this source", go back and tap Install.
3. If Google Play Protect shows a warning, tap "More details" and then "Install anyway". On a Samsung phone with Auto Blocker turned on, turn it off first in Settings.
4. Open GInput and follow the setup: turn on GInput in your phone's keyboard settings, then pick it as your current keyboard. Android warns that any keyboard can collect what you type; with GInput, what you type stays on your phone (see Privacy below).

#### Updating

Google Play keeps GInput up to date. If you installed the APK, the top of GInput's settings shows a notice when a new version is out: tap "Update on Google Play" and then Update in Google Play, and it updates automatically from then on. Without Google Play, tap "Download APK" and install it over the old version. Either way your settings and remembered characters are kept. Don't uninstall first, or your remembered characters and clipboard history are deleted too.

#### Checking the APK (optional)

Every GInput APK and the Google Play version are signed with the same key, so each can replace the other. The SHA-256 fingerprint of the signing certificate is:

```text
CF:DB:16:7E:A8:A9:0C:D2:5D:0F:E8:7D:D0:D3:07:7A:F3:E8:5F:D7:A6:0C:A8:6C:FC:FE:C6:18:5A:6F:58:07
```

Android only lets an APK signed with the same key replace the installed version; an APK with a different fingerprint wasn't released by JetPiggy.

### Privacy

What you type stays on your phone. The only exception is when you tap a Translate or Rewrite option: that text goes through GInput's server (`backend/ai`) to Cloudflare's AI, and the server doesn't keep it. Handwriting is also recognised on your phone, but Google ML Kit, which it uses, sends Google diagnostic data that doesn't include what you write; you're asked the first time you use handwriting, and it starts only after you agree. Crash reports never include what you type and can be turned off. Usage statistics are off by default. See the [privacy policy](https://ginput.jetpiggy.com/privacy.html).

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
- **Handwriting**: Google ML Kit Digital Ink Recognition and the Google Play services libraries it brings in are not open source and are provided under Google's terms (ADR 0013). Gradle downloads them from Google when you build from source; to build without handwriting, remove that dependency and the `handwriting` package.
- Third-party libraries and data keep their own licenses.

Contributions are accepted under the Apache License 2.0.

### Contact

Feedback and questions: <ginput@jetpiggy.com>. Please report security issues privately as described in [SECURITY.md](SECURITY.md).
