---
status: superseded by ADR 0004
---

# 閉源，只用寬鬆授權，自己寫 Kotlin 引擎

GInput 閉源，所以不可以包含 GPL、LGPL、share-alike（CC-BY-SA）或禁止商用的程式碼和資料。我們只用 Apache、MIT、BSD、CC0、CC-BY、Unicode 授權和公有領域的東西，並在 App 內的「開源授權」頁列出。現成的開源中文輸入法（Trime、LIME、fcitx5-android）都是 GPL 或 LGPL，所以我們不 fork 它們。我們用 Kotlin 和 Jetpack Compose 自己寫，借用 Jyutping（CC0）和 FlorisBoard（Apache-2.0）的程式碼。速成、倉頡和英文的引擎用純 Kotlin 加 SQLite，不寫 C++。

## Considered Options

- fork FlorisBoard（Apache-2.0）：已有 Compose 鍵盤和速成、倉頡，但架構龐大，英文建議未正式推出；它的中文碼表來自帶 GPL 標頭的 fcitx5-table-extra，授權不清。
- fork Jyutping（CC0）：架構合適，但以粵拼為主，最低要求 Android 13。
- 用 librime（BSD-3-Clause）：適合日後粵拼和漢語拼音的整句輸入，但第一版只需要查表，不值得承擔 C++ 和原生程式庫的成本。而且 rime 的倉頡和速成碼表是 LGPL。

## Consequences

- 第一版的資料來源：
  - 倉頡和速成：libcangjie（公有領域）加 Unihan 和香港增補字符集
  - 字頻：words.hk 分析檔（公有領域）和 HKCanCor（CC-BY）
  - 英文：SCOWL、12dicts 和 Google Books Ngram（CC-BY）
  - Emoji 關鍵字：CLDR（Unicode 授權）
- 不能用 CC-CEDICT、CC-Canto、wordfreq、words.hk 完整詞典和教育局字詞表。日後做粵拼和漢語拼音時，要另找寬鬆授權的資料。
- 2026-10-09 更新：英文改用 12dicts（作者放入公有領域；它依賴的 AGID 要求附上授權聲明）和 GInput 自己整理的縮寫清單。Google Books Ngram 的英文 1-gram 檔案合共超過 12 GB，不採用；SCOWL 暫時沒有用到。Emoji 資料用 Unicode 的 emoji-test.txt（Unicode 授權，要附聲明）。
- 加入任何程式庫或資料之前，要先查它的授權。
