"""用 libcangjie、rime-cantonese 和 words.hk 的資料建置 GInput 的字庫（SQLite）。英文詞庫由 build_english.py 另外建置。

用法（在專案根目錄，先跑 fetch_sources.py 和 build_english.py）：
    python -I -X utf8 tools/dictgen/build_dict.py --sources build/dict-sources \
        --english app/src/main/assets/dict/english-1.db \
        --out app/src/main/assets/dict/ginput-6.db --licenses app/src/main/assets/licenses
資料來源、授權和欄位說明見同一資料夾的 README.md。
"""

import argparse
import csv
import hashlib
import itertools
import json
import math
import re
import sqlite3
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
LOCK_FILE = HERE / "sources.lock.json"
COLLOQUIAL_FILE = HERE / "colloquial_chars.txt"
SYMBOL_FILE = HERE / "symbol_codes.txt"
TRANSLATION_EXTRA_FILE = HERE / "english_chinese_extra.txt"
TRANSLATION_BLOCKED_FILE = HERE / "english_chinese_blocked.txt"

# 改動資料或表格結構時加一，並同步改 App 讀取的檔名（ginput-<版本>.db）。
DICTIONARY_VERSION = 6

# 和 engine 模組 Scheme 的資料庫代號一致。粵拼不在 codes 表，另有 jyutping 表。
SCHEME_CANGJIE = 1
SCHEME_QUICK = 2

# 用單字的讀音砌出詞的讀音時，每個詞最多砌幾個。
MAX_COMPOSED_READINGS = 8

# 符號碼（ADR 0010）：zx 後面的兩個字母各取 a 至 y，按 Big5 的次序由 A140 排下去，只收最前面三行。
SYMBOL_LETTERS = "abcdefghijklmnopqrstuvwxy"
SYMBOL_POSITIONS = 3 * len(SYMBOL_LETTERS)

# libcangjie 已知放錯的符號碼（符號、倉頡版本、碼），核對時略過：「﹏」的五代碼應是 zxbd。
LIBCANGJIE_SYMBOL_ERRORS = {("﹏", 5, "zxbb")}

# 英漢詞典（ADR 0012）：每個英文字最多幾個譯詞；相關度達到 STRONG_RELEVANCE 的譯詞排先。
MAX_TRANSLATIONS = 3
STRONG_RELEVANCE = 70

# 英文的詞尾和去掉之後要補回的字母，和引擎 CandidateEngine.INFLECTIONS 一致。
INFLECTIONS = (
    ("s", ("",)),
    ("es", ("",)),
    ("ies", ("y",)),
    ("ed", ("e", "")),
    ("ied", ("y",)),
    ("ing", ("e", "")),
    ("er", ("e", "")),
    ("ers", ("e", "")),
    ("est", ("e", "")),
    ("ly", ("",)),
)

SCHEMA = """
CREATE TABLE meta (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
CREATE TABLE chars (
    id INTEGER PRIMARY KEY,
    ch TEXT NOT NULL UNIQUE,
    frequency INTEGER NOT NULL,
    windows_rank INTEGER NOT NULL,
    colloquial INTEGER NOT NULL,
    symbol INTEGER NOT NULL
);
CREATE TABLE codes (
    scheme INTEGER NOT NULL,
    code TEXT NOT NULL,
    generation INTEGER NOT NULL,
    char_id INTEGER NOT NULL REFERENCES chars (id),
    PRIMARY KEY (scheme, code, generation, char_id)
) WITHOUT ROWID;
CREATE TABLE words (
    head TEXT NOT NULL,
    rest TEXT NOT NULL,
    frequency INTEGER NOT NULL,
    colloquial INTEGER NOT NULL,
    PRIMARY KEY (head, rest)
) WITHOUT ROWID;
CREATE TABLE jyutping (
    syllables TEXT NOT NULL,
    text TEXT NOT NULL,
    frequency INTEGER NOT NULL,
    colloquial INTEGER NOT NULL,
    PRIMARY KEY (syllables, text)
) WITHOUT ROWID;
CREATE TABLE english_chinese (
    key TEXT NOT NULL,
    rank INTEGER NOT NULL,
    text TEXT NOT NULL,
    PRIMARY KEY (key, rank)
) WITHOUT ROWID;
"""

USED_SOURCES = (
    "libcangjie/table.txt",
    "wordshk/charcount.csv",
    "wordshk/existingwordcount.csv",
    "wordshk/englishindex.csv",
    "rime-cantonese/jyut6ping3.chars.dict.yaml",
    "rime-cantonese/jyut6ping3.words.dict.yaml",
    "rime-cantonese/LICENSE-CC-BY",
)
RIME_LICENSE_URL = "https://creativecommons.org/licenses/by/4.0/"

# 一個帶聲調的粵拼音節，例如 nei5。
SYLLABLE = re.compile(r"[a-z]+[1-6]")


def quick_code(cangjie: str) -> str:
    """速成碼是倉頡碼的首碼加尾碼；一至兩碼的不變。"""
    return cangjie if len(cangjie) <= 2 else cangjie[0] + cangjie[-1]


def read_table(path: Path):
    """逐行讀 libcangjie 的 table.txt，回傳（行號、15 欄）。"""
    with path.open(encoding="utf-8") as f:
        for number, line in enumerate(f, 1):
            line = line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            # 只按半形空格分欄：最後一行的字本身就是全形空格。
            fields = line.split(" ")
            if len(fields) != 15:
                raise ValueError(f"{path}:{number}: 應有 15 欄，實有 {len(fields)} 欄")
            yield number, fields


def table_codes(fields: list) -> list:
    """table.txt 一行裏的倉頡碼：（倉頡版本、碼）。"""
    return [
        (generation, code)
        for generation, codes in ((3, fields[11]), (5, fields[12]))
        if codes != "NA"
        for code in codes.split(",")
    ]


def read_cangjie(path: Path) -> dict:
    """讀 libcangjie 的 table.txt，只留 Big5 或香港增補字符集裏、有倉頡碼的中文字。"""
    chars = {}
    for number, fields in read_table(path):
        ch, _simplified, chinese, big5, hkscs = fields[:5]
        if chinese != "1" or (big5 != "1" and hkscs != "1"):
            continue
        entry = chars.setdefault(ch, {3: set(), 5: set(), "order": 0})
        for generation, code in table_codes(fields):
            if not (code.isascii() and code.isalpha() and code.islower()):
                raise ValueError(f"{path}:{number}: 不合法的碼 {code!r}")
            entry[generation].add(code)
        entry["order"] = max(entry["order"], int(fields[14]))
    return {ch: entry for ch, entry in chars.items() if entry[3] or entry[5]}


def symbol_code(position: int) -> str:
    """Big5 第 position 個符號（由 0 即 A140 起）的倉頡符號碼。"""
    row, column = divmod(position, len(SYMBOL_LETTERS))
    return "zx" + SYMBOL_LETTERS[row] + SYMBOL_LETTERS[column]


def big5_symbol(position: int) -> str:
    """Big5 A1 開頭的第 position 個符號，照 Windows 的對照（cp950）。第二個位元組由 40 到 7E，接着由 A1 起。"""
    second = 0x40 + position if position < 0x3F else 0xA1 + position - 0x3F
    return bytes([0xA1, second]).decode("cp950")


def read_symbols(path: Path, table: Path) -> list:
    """
    讀 symbol_codes.txt，回傳照 Big5 次序的（倉頡碼、符號）。清單要合乎 zx 規則，除了全形空格一個不漏；
    libcangjie 給這三行的符號碼也要和清單相同（已知的錯誤除外）。
    """
    symbols = []
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        fields = line.split("#", 1)[0].split()
        if not fields:
            continue
        if len(fields) != 2:
            raise ValueError(f"{path.name}:{number}: 應有碼和符號兩欄")
        symbols.append((fields[0], fields[1]))
    expected = [(symbol_code(position), big5_symbol(position)) for position in range(1, SYMBOL_POSITIONS)]
    for listed, rule in itertools.zip_longest(symbols, expected):
        if listed != rule:
            raise ValueError(f"{path.name} 不合乎 zx 規則：{listed} 應是 {rule}")
    by_code = {symbol_code(position): big5_symbol(position) for position in range(SYMBOL_POSITIONS)}
    for number, fields in read_table(table):
        if fields[2] == "1":
            continue
        for generation, code in table_codes(fields):
            if code not in by_code or (fields[0], generation, code) in LIBCANGJIE_SYMBOL_ERRORS:
                continue
            if fields[0] != by_code[code]:
                raise ValueError(f"{table}:{number}: libcangjie 的 {code} 是「{fields[0]}」，清單是「{by_code[code]}」")
    return symbols


def windows_ranks(chars: dict) -> dict:
    """libcangjie 的次序欄位數值越大越前，跟 Windows 倉頡和速成相同；轉成由 1 開始的名次。"""
    ordered = sorted((ch for ch, e in chars.items() if e["order"] > 0), key=lambda ch: (-chars[ch]["order"], ch))
    return {ch: rank for rank, ch in enumerate(ordered, 1)}


def read_counts(path: Path) -> dict:
    """讀 words.hk 分析檔的 CSV：第一行是來源，之後每行是「字詞,次數」。"""
    counts = {}
    with path.open(encoding="utf-8", newline="") as f:
        reader = csv.reader(f)
        if next(reader)[0] != "Source":
            raise ValueError(f"{path}: 第一行不是來源行")
        for number, row in enumerate(reader, 2):
            if len(row) != 2:
                raise ValueError(f"{path}:{number}: 應有 2 欄，實有 {len(row)} 欄")
            counts[row[0]] = counts.get(row[0], 0) + int(row[1])
    return counts


def read_rime(path: Path) -> list:
    """
    讀 rime 的 dict.yaml：「...」之後每行是「字詞<TAB>讀音」，單字表另有第三欄的權重（例如 5%）。
    回傳（字詞、不含聲調的音節、權重）。
    """
    rows = []
    body = False
    with path.open(encoding="utf-8") as f:
        for number, line in enumerate(f, 1):
            line = line.rstrip("\n")
            if line == "...":
                body = True
                continue
            if not body or not line or line.startswith("#"):
                continue
            fields = line.split("\t")
            if len(fields) not in (2, 3):
                raise ValueError(f"{path}:{number}: 應有 2 或 3 欄，實有 {len(fields)} 欄")
            syllables = fields[1].split(" ")
            if not all(SYLLABLE.fullmatch(s) for s in syllables):
                raise ValueError(f"{path}:{number}: 不合法的粵拼 {fields[1]!r}")
            rows.append((fields[0], [s[:-1] for s in syllables], fields[2] if len(fields) == 3 else ""))
    return rows


def reading_weight(weight: str) -> int:
    """rime 的權重：沒有寫的是主要讀音，當作 100；0% 是罕見讀音。"""
    return 100 if weight == "" else int(weight.rstrip("%"))


def char_readings(path: Path, chars: dict) -> dict:
    """字庫裏每個字的粵拼和權重。略去 0% 的罕見讀音，以及一個字讀成多個音節的讀音（例如「兡」）。"""
    readings = {}
    for ch, syllables, weight in read_rime(path):
        if ch not in chars or len(syllables) != 1 or reading_weight(weight) == 0:
            continue
        entry = readings.setdefault(ch, {})
        entry[syllables[0]] = max(entry.get(syllables[0], 0), reading_weight(weight))
    return readings


def word_readings(path: Path, words: dict, readings: dict) -> dict:
    """
    詞的粵拼。rime 詞表有的照詞表；沒有的，用每個字的主要讀音（權重不少於該字最高權重的一半）砌出來，
    最多 MAX_COMPOSED_READINGS 個。
    """
    listed = {}
    for word, syllables, _ in read_rime(path):
        if word in words and len(syllables) == len(word):
            listed.setdefault(word, set()).add(" ".join(syllables))
    result = {}
    for word in words:
        if word in listed:
            result[word] = listed[word]
        elif all(ch in readings for ch in word):
            options = []
            for ch in word:
                top = max(readings[ch].values())
                options.append(sorted(s for s, w in readings[ch].items() if w * 2 >= top))
            combined = itertools.islice(itertools.product(*options), MAX_COMPOSED_READINGS)
            result[word] = {" ".join(syllables) for syllables in combined}
    return result


def read_items(path: Path) -> list:
    """GInput 自行整理的清單：每行可以放多個項目，用空白分隔；# 之後是註解。"""
    items = []
    for line in path.read_text(encoding="utf-8").splitlines():
        items.extend(line.split("#", 1)[0].split())
    return items


def read_colloquial(path: Path, chars: dict) -> set:
    listed = set(read_items(path))
    missing = sorted(ch for ch in listed if ch not in chars)
    if missing:
        raise ValueError(f"{path.name} 有字不在字庫裏：{' '.join(missing)}")
    return listed


def porter_stem(word: str) -> str:
    """Porter（1980）的詞幹算法。words.hk 英粵對照表裏「!」開頭的查找鍵就是這個詞幹，例如 happy 是 happi。"""

    def consonant(w: str, i: int) -> bool:
        if w[i] in "aeiou":
            return False
        return w[i] != "y" or i == 0 or not consonant(w, i - 1)

    def measure(w: str) -> int:
        count, i = 0, 0
        while i < len(w) and consonant(w, i):
            i += 1
        while i < len(w):
            while i < len(w) and not consonant(w, i):
                i += 1
            if i >= len(w):
                break
            while i < len(w) and consonant(w, i):
                i += 1
            count += 1
        return count

    def has_vowel(w: str) -> bool:
        return any(not consonant(w, i) for i in range(len(w)))

    def double_consonant(w: str) -> bool:
        return len(w) >= 2 and w[-1] == w[-2] and consonant(w, len(w) - 1)

    def cvc(w: str) -> bool:
        return (
            len(w) >= 3
            and consonant(w, len(w) - 3)
            and not consonant(w, len(w) - 2)
            and consonant(w, len(w) - 1)
            and w[-1] not in "wxy"
        )

    def replace(w: str, rules: tuple, minimum: int) -> str:
        for suffix, replacement in rules:
            if w.endswith(suffix):
                stem = w[: len(w) - len(suffix)]
                return stem + replacement if measure(stem) > minimum else w
        return w

    w = word
    if len(w) <= 2:
        return w
    # 步驟 1a
    if w.endswith("sses") or w.endswith("ies"):
        w = w[:-2]
    elif not w.endswith("ss") and w.endswith("s"):
        w = w[:-1]
    # 步驟 1b
    removed = False
    if w.endswith("eed"):
        if measure(w[:-3]) > 0:
            w = w[:-1]
    elif w.endswith("ed") and has_vowel(w[:-2]):
        w, removed = w[:-2], True
    elif w.endswith("ing") and has_vowel(w[:-3]):
        w, removed = w[:-3], True
    if removed:
        if w.endswith(("at", "bl", "iz")):
            w += "e"
        elif double_consonant(w) and w[-1] not in "lsz":
            w = w[:-1]
        elif measure(w) == 1 and cvc(w):
            w += "e"
    # 步驟 1c
    if w.endswith("y") and has_vowel(w[:-1]):
        w = w[:-1] + "i"
    # 步驟 2 和 3
    w = replace(w, (
        ("ational", "ate"), ("tional", "tion"), ("enci", "ence"), ("anci", "ance"), ("izer", "ize"),
        ("abli", "able"), ("alli", "al"), ("entli", "ent"), ("eli", "e"), ("ousli", "ous"),
        ("ization", "ize"), ("ation", "ate"), ("ator", "ate"), ("alism", "al"), ("iveness", "ive"),
        ("fulness", "ful"), ("ousness", "ous"), ("aliti", "al"), ("iviti", "ive"), ("biliti", "ble"),
    ), 0)
    w = replace(w, (
        ("icate", "ic"), ("ative", ""), ("alize", "al"), ("iciti", "ic"), ("ical", "ic"), ("ful", ""), ("ness", ""),
    ), 0)
    # 步驟 4
    for suffix in (
        "al", "ance", "ence", "er", "ic", "able", "ible", "ant", "ement", "ment", "ent", "ion", "ou", "ism",
        "ate", "iti", "ous", "ive", "ize",
    ):
        if w.endswith(suffix):
            stem = w[: -len(suffix)]
            if measure(stem) > 1 and (suffix != "ion" or stem.endswith(("s", "t"))):
                w = stem
            break
    # 步驟 5
    if w.endswith("e"):
        stem = w[:-1]
        if measure(stem) > 1 or (measure(stem) == 1 and not cvc(stem)):
            w = stem
    if measure(w) > 1 and double_consonant(w) and w.endswith("l"):
        w = w[:-1]
    return w


def inflection_stems(key: str) -> list:
    """去掉英文詞尾之後可能的原形，例如 cherries 是 cherry；規則和引擎 CandidateEngine.englishStems 一樣。"""
    stems = []
    for suffix, endings in INFLECTIONS:
        if not key.endswith(suffix):
            continue
        stem = key[: -len(suffix)]
        stems.extend(stem + ending for ending in endings)
        # 尾字母重複：stopped、running。
        if len(stem) >= 2 and stem[-1] == stem[-2]:
            stems.append(stem[:-1])
    return list(dict.fromkeys(stems))


def read_english_index(path: Path, chars: dict) -> dict:
    """
    讀 words.hk 的英粵對照表：第一行是來源，之後每行是英文查找鍵（「!」開頭的是 Porter 詞幹）和多格
    [「寫法:粵拼,另一個寫法:粵拼」, 相關度]。每格取第一個每個字都在字庫裏的寫法；同一個寫法取最高的相關度。
    """
    index = {}
    with path.open(encoding="utf-8", newline="") as f:
        reader = csv.reader(f)
        if next(reader)[0] != "Source":
            raise ValueError(f"{path}: 第一行不是來源行")
        for number, row in enumerate(reader, 2):
            found = index.setdefault(row[0], {})
            for cell in row[1:]:
                forms, relevance = json.loads(cell)
                for form in (part.split(":", 1)[0] for part in forms.split(",")):
                    if form and all(ch in chars for ch in form):
                        found[form] = max(found.get(form, 0), int(relevance))
                        break
    return index


def read_english_keys(path: Path) -> list:
    """英文詞庫的全部查找鍵。只用查找鍵，不用英文詞庫的詞頻（ADR 0004）。"""
    db = sqlite3.connect(path)
    try:
        return [key for (key,) in db.execute("SELECT DISTINCT key FROM english ORDER BY key")]
    finally:
        db.close()


def read_translation_extra(path: Path, chars: dict, keys: set, blocked: list) -> dict:
    """
    english_chinese_extra.txt：每行「英文字<Tab>譯詞」，譯詞用空格分隔，排前的先出，取代 words.hk 的譯詞；
    譯詞寫「-」就不出譯詞（例如 was 在 words.hk 是「華盛頓」）。
    """
    extra = {}
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        text = line.split("#", 1)[0].rstrip()
        if not text:
            continue
        where = f"{path.name}:{number}"
        english, tab, rest = text.partition("\t")
        translations = rest.split()
        if translations == ["-"]:
            translations = []
        elif not tab or not translations:
            raise ValueError(f"{where}：應是「英文字<Tab>譯詞」")
        if english not in keys:
            raise ValueError(f"{where}：{english} 不在英文詞庫裏")
        if english in extra:
            raise ValueError(f"{where}：{english} 重複")
        if len(translations) > MAX_TRANSLATIONS or len(set(translations)) != len(translations):
            raise ValueError(f"{where}：最多 {MAX_TRANSLATIONS} 個譯詞，不可以重複")
        for translation in translations:
            if not all(ch in chars for ch in translation):
                raise ValueError(f"{where}：「{translation}」有字不在字庫裏")
            if any(word in translation for word in blocked):
                raise ValueError(f"{where}：「{translation}」在 english_chinese_blocked.txt 裏")
        extra[english] = translations
    return extra


def translation_scores(frequency: dict, words: dict):
    """
    譯詞的常用度：詞用 words.hk 的詞頻；單字用扣除了它在詞裏出現次數的字頻（最少留一成，同 ADR 0009），
    再除以 10，讓只是詞的一部分的單字（例如 meeting 的「會」）排在常用的詞後面。
    """
    in_words = {}
    for word, count in words.items():
        for ch in word:
            in_words[ch] = in_words.get(ch, 0) + count

    def score(text: str) -> float:
        if len(text) > 1:
            return words.get(text, 0)
        count = frequency.get(text, 0)
        return max(count - in_words.get(text, 0), count // 10) / 10

    return score


def rank_translations(key: str, index: dict, score) -> list:
    """
    英文字本身和它的 Porter 詞幹在英粵對照表找到的譯詞：相關度達到 STRONG_RELEVANCE 的排先，
    然後按常用度和相關度。
    """
    found = {}
    for term in (key, "!" + porter_stem(key)):
        for form, relevance in index.get(term, {}).items():
            found[form] = max(found.get(form, 0), relevance)
    return sorted(
        found,
        key=lambda form: (found[form] < STRONG_RELEVANCE, -(math.log1p(score(form)) + found[form] / 25), form),
    )


def english_chinese_rows(keys: list, index: dict, score, extra: dict, blocked: list) -> list:
    """
    每個英文字最多 MAX_TRANSLATIONS 個譯詞（查找鍵、次序、譯詞）。english_chinese_extra.txt 有的字照清單；
    清單的字的變化形（去掉詞尾後是清單的字，而且 Porter 詞幹相同，例如 cherry 的 cherries，但 car 不包括 cares）
    也照清單。含有 english_chinese_blocked.txt 項目的譯詞不收。
    """
    rows = []
    for key in keys:
        stem = porter_stem(key)
        listed = next(
            (extra[word] for word in [key, *inflection_stems(key)] if word in extra and porter_stem(word) == stem),
            None,
        )
        if listed is None:
            listed = [t for t in rank_translations(key, index, score) if not any(word in t for word in blocked)]
        rows.extend((key, rank, text) for rank, text in enumerate(listed[:MAX_TRANSLATIONS]))
    return rows


def verify_sources(sources: Path) -> dict:
    if not LOCK_FILE.exists():
        sys.exit("找不到 sources.lock.json，先跑 fetch_sources.py。")
    lock = json.loads(LOCK_FILE.read_text(encoding="utf-8"))
    hashes = {}
    for name in USED_SOURCES:
        digest = hashlib.sha256((sources / name).read_bytes()).hexdigest()
        if lock[name]["sha256"] != digest:
            sys.exit(f"{name} 和 sources.lock.json 不符，先用 fetch_sources.py 重新下載。")
        hashes[name] = digest
    return hashes


def jyutping_attribution() -> str:
    """rime-cantonese 以 CC BY 4.0 授權：要註明出處、授權和改動。"""
    url = json.loads(LOCK_FILE.read_text(encoding="utf-8"))["rime-cantonese/jyut6ping3.chars.dict.yaml"]["url"]
    return (
        "Jyutping readings: rime-cantonese by CanCLID (粵語計算語言學基礎建設組) and its contributors\n"
        f"(https://github.com/rime/rime-cantonese, files from {url.rsplit('/', 1)[0]}),\n"
        f"licensed under CC BY 4.0 ({RIME_LICENSE_URL}).\n"
        "Changes: GInput took the readings in jyut6ping3.chars.dict.yaml and jyut6ping3.words.dict.yaml, "
        "removed the tones and the rare (0%) readings, kept only the characters and words in its own dictionary, "
        "composed readings for words.hk words missing from rime-cantonese from their characters' main readings, "
        "and stored them with words.hk frequencies in the jyutping table of dict/ginput-<version>.db."
    )


def write_notices(sources: Path, licenses: Path) -> None:
    licenses.mkdir(parents=True, exist_ok=True)
    (licenses / "rime-cantonese.txt").write_text(
        jyutping_attribution()
        + "\n\nThe license of rime-cantonese follows.\n\n"
        + (sources / "rime-cantonese/LICENSE-CC-BY").read_text(encoding="utf-8"),
        encoding="utf-8",
    )


def build(sources: Path, english: Path, out: Path, licenses: Path) -> None:
    hashes = verify_sources(sources)
    chars = read_cangjie(sources / "libcangjie/table.txt")
    frequency = read_counts(sources / "wordshk/charcount.csv")
    colloquial = read_colloquial(COLLOQUIAL_FILE, chars)
    ranks = windows_ranks(chars)
    symbols = read_symbols(SYMBOL_FILE, sources / "libcangjie/table.txt")
    words = {
        word: count
        for word, count in read_counts(sources / "wordshk/existingwordcount.csv").items()
        if len(word) >= 2 and all(ch in chars for ch in word)
    }

    out.parent.mkdir(parents=True, exist_ok=True)
    if out.exists():
        out.unlink()
    db = sqlite3.connect(out)
    db.executescript(SCHEMA)

    ids = {ch: index for index, ch in enumerate(sorted(chars), 1)}
    db.executemany(
        "INSERT INTO chars VALUES (?, ?, ?, ?, ?, 0)",
        ((ids[ch], ch, frequency.get(ch, 0), ranks.get(ch, 0), int(ch in colloquial)) for ch in sorted(chars)),
    )
    # 符號接在中文字後面：沒有字頻，Windows 名次照 Big5 次序排在全部中文字之後。
    symbol_ids = {ch: len(ids) + index for index, (_, ch) in enumerate(symbols, 1)}
    db.executemany(
        "INSERT INTO chars VALUES (?, ?, 0, ?, 0, 1)",
        ((symbol_ids[ch], ch, len(ranks) + index) for index, (_, ch) in enumerate(symbols, 1)),
    )

    code_rows = set()
    for ch, entry in chars.items():
        for generation in (3, 5):
            for code in entry[generation]:
                code_rows.add((SCHEME_CANGJIE, code, generation, ids[ch]))
                code_rows.add((SCHEME_QUICK, quick_code(code), generation, ids[ch]))
    # 倉頡版本的設定不影響符號碼：三代和五代都有。
    for code, ch in symbols:
        for generation in (3, 5):
            code_rows.add((SCHEME_CANGJIE, code, generation, symbol_ids[ch]))
            code_rows.add((SCHEME_QUICK, quick_code(code), generation, symbol_ids[ch]))
    db.executemany("INSERT INTO codes VALUES (?, ?, ?, ?)", sorted(code_rows))

    # 粵拼：一個字有幾個讀音的話，次要讀音的字頻按 rime 的權重打折。
    readings = char_readings(sources / "rime-cantonese/jyut6ping3.chars.dict.yaml", chars)
    jyutping_rows = {}
    for ch, entry in readings.items():
        top = max(entry.values())
        for syllable, weight in entry.items():
            jyutping_rows[(syllable, ch)] = (frequency.get(ch, 0) * weight // top, int(ch in colloquial))
    spoken = word_readings(sources / "rime-cantonese/jyut6ping3.words.dict.yaml", words, readings)
    for word, syllables in spoken.items():
        for reading in syllables:
            jyutping_rows[(reading, word)] = (words[word], int(any(ch in colloquial for ch in word)))
    db.executemany(
        "INSERT INTO jyutping VALUES (?, ?, ?, ?)",
        sorted((syllables, text, *value) for (syllables, text), value in jyutping_rows.items()),
    )

    db.executemany(
        "INSERT INTO words VALUES (?, ?, ?, ?)",
        sorted(
            (word[0], word[1:], count, int(any(ch in colloquial for ch in word)))
            for word, count in words.items()
        ),
    )

    english_keys = read_english_keys(english)
    blocked = read_items(TRANSLATION_BLOCKED_FILE)
    extra = read_translation_extra(TRANSLATION_EXTRA_FILE, chars, set(english_keys), blocked)
    index = read_english_index(sources / "wordshk/englishindex.csv", chars)
    translation_rows = english_chinese_rows(english_keys, index, translation_scores(frequency, words), extra, blocked)
    db.executemany("INSERT INTO english_chinese VALUES (?, ?, ?)", translation_rows)

    meta = {
        "version": str(DICTIONARY_VERSION),
        "sources": json.dumps(hashes, sort_keys=True),
        "jyutping_attribution": jyutping_attribution(),
    }
    db.executemany("INSERT INTO meta VALUES (?, ?)", sorted(meta.items()))
    db.execute(f"PRAGMA user_version = {DICTIONARY_VERSION}")
    db.commit()
    db.execute("VACUUM")
    db.close()
    write_notices(sources, licenses)

    print(f"字：{len(chars)}（Windows 次序 {len(ranks)}、口語 {len(colloquial)}）")
    print(f"符號：{len(symbols)}")
    print(f"碼：{len(code_rows)}")
    print(f"詞：{len(words)}")
    print(f"粵拼：{len(readings)} 字、{len(spoken)} 詞，共 {len(jyutping_rows)} 項")
    print(f"英漢詞典：{len({key for key, _, _ in translation_rows})} 個英文字、{len(translation_rows)} 個譯詞（自行整理 {len(extra)} 個字）")
    print(f"{out}：{out.stat().st_size / 1024 / 1024:.1f} MB")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--sources", type=Path, required=True, help="fetch_sources.py 的 --dest 資料夾")
    parser.add_argument("--english", type=Path, required=True, help="build_english.py 建置的英文詞庫，用它的查找鍵")
    parser.add_argument("--out", type=Path, required=True, help="輸出的 SQLite 檔案")
    parser.add_argument("--licenses", type=Path, required=True, help="輸出授權聲明的資料夾（App 的 assets）")
    args = parser.parse_args()
    build(args.sources, args.english, args.out, args.licenses)


if __name__ == "__main__":
    main()
