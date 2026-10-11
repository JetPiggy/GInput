# 字庫和 Emoji 資料建置工具

建置 App 內置的資料：

- 字庫 `app/src/main/assets/dict/ginput-<版本>.db`（SQLite）：碼表、粵拼、字頻和聯想用的詞
- 英文詞庫 `app/src/main/assets/dict/english-<版本>.db`（SQLite）：英文建議用的詞和詞頻。因為用了 wordfreq 的詞頻，這個檔案以 CC BY-SA 4.0 授權，所以和字庫分開（ADR 0004）
- Emoji 面板的資料 `app/src/main/assets/emoji/emoji.tsv`
- Emoji 關鍵字 `app/src/main/assets/emoji/keywords.tsv`：關鍵字和相關的 Emoji
- 授權聲明 `app/src/main/assets/licenses/`

建置出來的檔案都要 commit；只有資料或表格結構改變時才需要重新建置。

## 步驟

在專案根目錄執行：

```bash
python -I -X utf8 tools/dictgen/fetch_sources.py --dest build/dict-sources
python -I -X utf8 tools/dictgen/build_dict.py --sources build/dict-sources --out app/src/main/assets/dict/ginput-4.db --licenses app/src/main/assets/licenses
python -I -X utf8 tools/dictgen/build_english.py --sources build/dict-sources --out app/src/main/assets/dict/english-1.db --licenses app/src/main/assets/licenses
python -I -X utf8 tools/dictgen/build_emoji.py --sources build/dict-sources --dict app/src/main/assets/dict/ginput-4.db --out app/src/main/assets/emoji/emoji.tsv --keywords app/src/main/assets/emoji/keywords.tsv --licenses app/src/main/assets/licenses
```

- `fetch_sources.py` 下載資料，並用 `sources.lock.json` 核對 SHA-256。新加入的來源會自動記下雜湊；上游一更新，雜湊就會不符而停下，確認內容無誤後，加 `--update-lock` 重跑。
- 改動字庫的資料或表格結構時，把 `build_dict.py` 的 `DICTIONARY_VERSION`（英文詞庫是 `build_english.py` 的 `ENGLISH_VERSION`）加一，並同步改：
  - App 的 `BundledDictionary.MAIN_FILE`（英文詞庫是 `ENGLISH_FILE`）
  - `engine/build.gradle.kts` 測試用的字庫路徑
  - 上面指令裏的檔名，並刪掉舊版本的檔案
- `build_emoji.py` 用字庫確認關鍵字打得出，所以要在 `build_dict.py` 之後跑；字庫的字改變後要重跑。
- 建置後跑 `:engine:test`。`SqlDictionaryTest` 直接讀這兩個檔案。

## 資料來源

| 檔案 | 來源 | 授權 | 用途 |
|---|---|---|---|
| `libcangjie/table.txt` | Debian 的 libcangjie 1.4.0 | 公有領域 | 倉頡三代和五代的碼、Big5 和香港增補字符集標記、Windows 次序 |
| `wordshk/charcount.csv` | words.hk 分析檔 | 公有領域，歡迎註明出處 | 字頻 |
| `wordshk/existingwordcount.csv` | words.hk 分析檔 | 公有領域，歡迎註明出處 | 聯想和粵拼用的詞和詞頻 |
| `rime-cantonese/jyut6ping3.chars.dict.yaml` | rime-cantonese（CanCLID），固定在某個 commit | CC BY 4.0（要註明出處和改動） | 字的粵拼和讀音權重 |
| `rime-cantonese/jyut6ping3.words.dict.yaml` | 同上 | 同上 | 詞的粵拼 |
| `rime-cantonese/LICENSE-CC-BY` | 同上 | 同上 | 授權聲明 |
| `12dicts/12dicts-6.0.2.zip` | 12dicts（Alan Beale） | 作者放入公有領域並希望註明出處；依賴的 AGID 要附授權聲明 | 英文詞和常用程度 |
| `wordfreq/large_en.msgpack.gz` | wordfreq v3.2（Robyn Speer） | CC BY-SA 4.0；要以 Robyn Speer 之名註明出處，並註明 SUBTLEX 的作者 | 英文詞頻 |
| `wordfreq/NOTICE.md`、`wordfreq/README.md` | 同上 | 同上 | 授權聲明和引用來源 |
| `unicode/emoji-test.txt` | Unicode | Unicode 授權（要附聲明） | Emoji 面板 |
| `cldr/zh_Hant_HK.xml`、`cldr/zh_Hant.xml` | Unicode CLDR 48.2 的 annotations | Unicode 授權（要附聲明） | Emoji 關鍵字 |
| `colloquial_chars.txt` | GInput 自行整理 | — | 粵語口語字 |
| `english_extra.txt` | GInput 自行整理 | — | 12dicts 沒有的常用英文詞（縮寫、新詞、網上用語、品牌和地方） |
| `english_blocked.txt` | GInput 自行整理 | — | 不作英文建議的粗口、色情字眼和侮辱字眼 |
| `emoji_keywords_extra.txt` | GInput 自行整理 | — | 單字的 Emoji 關鍵字、CLDR 沒有的粵語和香港用語，以及取代 CLDR 古怪的配搭和次序 |
| `emoji_keywords_blocked.txt` | GInput 自行整理 | — | 不作 Emoji 關鍵字的 CLDR 關鍵字（太常用、配上 Emoji 沒有意思） |

`build_dict.py`、`build_english.py` 和 `build_emoji.py` 會把 rime-cantonese、12dicts／AGID、wordfreq 和 Unicode 的授權聲明寫進 `app/src/main/assets/licenses/`。App 的「開源授權」頁要列出 words.hk、libcangjie、rime-cantonese、12dicts（連同 AGID 聲明）、wordfreq、Unicode Emoji 和 Unicode CLDR，並寫明英文詞庫以 CC BY-SA 4.0 授權。

rime-cantonese 的 phrase 表註明參考了 words.hk 和 CC-Canto 等詞典，所以只用字表和詞表（ADR 0008）。字庫的 `meta` 表寫明粵拼的出處和改動（`jyutping_attribution`）。

wordfreq 的作者不希望資料被轉換成沒有出處和授權的格式，所以英文詞庫的 `meta` 表寫明授權（`license`）、出處和改動（`attribution`）。不要把英文詞庫併入字庫，也不要加密或混淆它（ADR 0004）。

## 規則

- **字的範圍**：Big5 或香港增補字符集裏、有倉頡碼的中文字。
- **速成碼**：倉頡碼的首碼加尾碼，一至兩碼的不變。三代和五代的碼各自推導。
- **Windows 次序**：libcangjie 次序欄位的數值越大越前，跟 Windows 倉頡和速成的次序相同。建置時轉成由 1 開始的名次；不在 Big5 的字沒有名次。
- **詞**：words.hk 詞頻表裏兩個字或以上、而且每個字都在字庫裏的詞。含粵語口語字的詞標為口語詞。
- **字的粵拼**：rime-cantonese 字表裏、字庫有的字，去掉聲調；略去 0% 的罕見讀音和一個字讀成幾個音節的讀音（例如「兡」）。權重沒有寫的是主要讀音，當作 100；一個字有幾個讀音的話，每個讀音的字頻是字頻乘以「該讀音的權重 ÷ 該字最高的權重」。去掉聲調後相同的讀音取較高的。
- **詞的粵拼**：上面的詞，rime-cantonese 詞表有的照詞表（可以有幾個讀音）；沒有的，用每個字的主要讀音（權重不少於該字最高權重的一半）砌出來，每個詞最多 8 個。詞頻照 words.hk。
- **英文詞**：12dicts 的 2+2+3frq 和 2016 年新詞，加上 `english_extra.txt`；刪去 `english_blocked.txt` 的詞（不分大小寫）。只收以字母開頭、由英文字母和撇號組成的詞。查找鍵是小寫、去掉撇號的詞。
- **英文詞頻**：wordfreq 的 large_en，以負 centibel 表示（0 最常用，數值越大越罕見，最大 799），按詞的小寫查。12dicts 同時收了小寫寫法的詞（例如 may 和 May），其他寫法加 30。wordfreq 沒有的詞排在最後（900 加 12dicts 的常用程度等級：2+2+3frq 分 21 級，1 最常用；2016 年新詞當作第 12 級，`english_extra.txt` 當作第 3 級）。
- **Emoji**：只收 fully-qualified 的 Emoji，不收 Component 分類。膚色變體歸到原形之下；找不到原形的（例如兩種膚色的組合）略去。
- **Emoji 關鍵字**（docs/spec/v1.md 的「Emoji 關鍵字」）：
  - CLDR 用 zh_Hant_HK 的關鍵字和名稱（`type="tts"`），標示 `↑↑↑` 或沒有的用 zh_Hant 的。比對 Emoji 時不理變體選擇符 FE0F。
  - 只收全是中文字、兩至六個字、每個字都在字庫裏、不在 `emoji_keywords_blocked.txt` 的關鍵字。
  - 同一個關鍵字：名稱就是那個關鍵字的 Emoji 排先，其餘按 Emoji 面板的次序；排在中性版本後面的男、女版本（例如 🧑‍🎓 之後的 👨‍🎓）不收。
  - `emoji_keywords_extra.txt` 有的關鍵字，Emoji 和次序照清單，取代 CLDR 的；單字的關鍵字只來自這個清單。
  - 每個關鍵字最多 6 個 Emoji（App 顯示 3 個，其餘在手機字型顯示不到時補上）。
  - 清單有錯（關鍵字打不出、重複、同時在兩個清單、Emoji 不在 Emoji 面板）時，建置會停下。

## 表格

字庫：

| 表格 | 內容 |
|---|---|
| `chars` | 字、字頻、Windows 名次（0 表示沒有）、是否粵語口語字 |
| `codes` | 輸入方案（1 倉頡、2 速成）、碼、倉頡版本（3 或 5）、字 |
| `words` | 開頭的字、其餘部分、詞頻、是否口語詞 |
| `jyutping` | 不含聲調、用空格分隔的音節（例如 `nei hou`）、字或詞、字頻或詞頻、是否口語字詞 |
| `meta` | 字庫版本、各來源的 SHA-256、粵拼的出處和改動 |

英文詞庫：

| 表格 | 內容 |
|---|---|
| `english` | 查找鍵、詞（保留大小寫和撇號）、詞頻（數值越小越常用） |
| `meta` | 詞庫版本、授權、出處和改動、各來源的 SHA-256 |
