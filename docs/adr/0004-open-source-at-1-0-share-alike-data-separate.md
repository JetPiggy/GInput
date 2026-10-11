---
status: accepted
---

# 1.0 前閉源；程式碼只用寬鬆授權，CC-BY-SA 資料要獨立成檔

GInput 開發期間閉源，repo 保持私人，到作品大致滿意（大約 1.0）才開源。開源時用一個全新的初始 commit 發佈，不帶之前的 commit 歷史和電郵地址。開源用哪個授權（GPL-3.0 加 Google Play 例外，或 Apache-2.0），開源前再決定。

程式碼仍然只用 Apache、MIT、BSD、ISC、CC0、CC-BY、Unicode 授權和公有領域的東西，GPL、LGPL 和禁止商用的一律不用。這樣閉源期間不會違反授權，開源時兩種授權都可以選。現成的開源中文輸入法（Trime、LIME、fcitx5-android）都是 GPL 或 LGPL，所以我們繼續用 Kotlin 和 Jetpack Compose 自己寫，速成、倉頡和英文的引擎用純 Kotlin 加 SQLite，不寫 C++。

CC-BY-SA 的資料可以用，但要獨立成檔，不可以和其他資料合併在同一個檔案：

- 那個資料檔以 CC BY-SA 4.0 授權，檔案內寫明授權、出處和我們改動了甚麼。
- App 的「開源授權」頁列明哪個檔案以 CC BY-SA 4.0 授權、它的出處，以及上游要求附上的聲明。
- 資料檔不加密、不混淆，任何人都可以從 APK 取出來用。

這樣 share-alike 只約束那個資料檔，App 的其他部分不受影響。這份 ADR 取代 ADR 0002。

## Considered Options

- 現在就開源：可以直接用 GPL 的程式碼，但未成熟的開發過程會公開。
- 開源時連 commit 歷史一起公開：看得到開發過程，但歷史裏有電郵地址和未整理的嘗試。
- 繼續完全不用 CC-BY-SA：英文建議只能按 12dicts 的 21 個常用程度等級排序，同級的詞只能按長短排，最常用的字未必排在前面。
- fork FlorisBoard 或 Jyutping，或者用 librime：理由見 ADR 0002，結論不變。

## Consequences

- 英文建議改按 wordfreq v3.2（Robyn Speer，CC BY-SA 4.0）的詞頻排序。英文詞庫獨立成 `english-<版本>.db`，以 CC BY-SA 4.0 授權；詞仍然來自 12dicts（公有領域，要附 AGID 的聲明）和 GInput 自己整理的清單。wordfreq 的作者要求轉換後的資料保留出處和授權，所以檔案的 meta 表和 App 的授權聲明都寫明。
- 其他資料來源不變：
  - 倉頡和速成：libcangjie（公有領域）
  - 字頻和聯想用的詞：words.hk 分析檔（公有領域）
  - Emoji：Unicode 的 emoji-test.txt（Unicode 授權，要附聲明）
- 日後做粵拼和漢語拼音時，CC-CEDICT、CC-Canto 這類 CC-BY-SA 資料也可以用，但同樣要獨立成檔。words.hk 完整詞典（禁止商用）和教育局字詞表（沒有開放授權）仍然不能用。
- 開源前要選定授權，並再檢查一次所有程式庫和資料跟那個授權是否相容。
- 2026-10-10 更新：公開的 repo 開在 GitHub organization JetPiggy，不用個人帳戶，commit 用 JetPiggy 的名義和電郵，避免公開個人帳戶名和電郵。
- 加入任何程式庫或資料之前，要先查它的授權。
- 2026-10-11 更新：清單加入 ISC。ISC 和 MIT 實質相同，GPL-3.0 和 Apache-2.0 都相容；第一個用到的是 Lucide 圖示（Emoji 面板和剪貼簿面板的返回鍵）。
- 2026-10-11 再更新：按新的鍵盤設計稿，鍵盤的圖示統一用 Material Symbols（Apache 2.0），返回鍵也改用它，Lucide 不再用。ISC 仍然留在清單。
- 2026-10-12 更新：授權選定 Apache-2.0，品牌圖像除外（ADR 0007）。
