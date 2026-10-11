---
status: accepted
---

# 粵拼用 rime-cantonese 的資料和自己寫的 Kotlin 引擎，不做整句輸入

粵拼和速成、倉頡一樣，是中英混打的一個輸入方案：不用切換，整串組字拆得成粵拼音節就出候選字，英文建議照樣排在後面。讀音用 rime-cantonese（CC BY 4.0）的字表和詞表，字頻和詞頻沿用 words.hk 分析檔，全部放進字庫的 `jyutping` 表。引擎用純 Kotlin 加 SQLite：把組字拆成音節，查字和詞；選了只用前面幾個音節的字詞，就分段寫入。第一版不做整句輸入（自動組句）和簡拼。

## Considered Options

- 用 librime（BSD-3-Clause）：整句輸入、簡拼和模糊音都現成，但要打包 C++ 原生程式庫和 rime 的設定檔，違反 ADR 0004「不寫 C++」；它的語言模型（essay）的來源和授權也要另外查。
- fork Jyutping（CC0）：粵拼做得完整，但最低要求 Android 13，架構也不是為中英混打而設（ADR 0002）。
- 讀音改用 Unihan 的 kCantonese（Unicode 授權）：授權最簡單，但只有單字，沒有讀音的權重，多音字和詞的讀音要自己整理。
- 用 CC-Canto 或 words.hk 完整詞典：CC-Canto 是 CC BY-SA，要獨立成檔；words.hk 完整詞典禁止商用（ADR 0004）。
- 做整句輸入：要另外建語言模型，工作量大很多；逐段選詞已經可以打完一整句。

## Consequences

- 字庫升到第 4 版，多了 `jyutping` 表，檔案由 2.0 MB 增至 3.1 MB。rime-cantonese 沒有發佈版本，所以固定在某個 commit；App 的開源授權頁附上 CC BY 4.0 的聲明和我們的改動，字庫的 `meta` 表也寫明出處。
- rime-cantonese 的 phrase 表註明參考了 words.hk 和 CC-Canto 等詞典，所以不用 phrase 表；字表和詞表依它宣告的 CC BY 4.0 使用。
- 新增分段寫入：候選字可以只用組字前面一部分字母；`TypingSession` 寫入那部分之後，其餘字母留在組字。
- 寬鬆拼法（懶音和港式拼法）由引擎按規則推導，不需要另外的資料；規則見 `docs/spec/v1.md` 的「粵拼」。
- 日後做整句輸入、簡拼或漢語拼音，可以沿用同一套拆音節和查表的做法；要改用 librime，就要新的 ADR。
