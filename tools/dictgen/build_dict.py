"""用 libcangjie、rime-cantonese 和 words.hk 的資料建置 GInput 的字庫（SQLite）。英文詞庫由 build_english.py 另外建置。

用法（在專案根目錄，先跑 fetch_sources.py）：
    python -I -X utf8 tools/dictgen/build_dict.py --sources build/dict-sources \
        --out app/src/main/assets/dict/ginput-4.db --licenses app/src/main/assets/licenses
資料來源、授權和欄位說明見同一資料夾的 README.md。
"""

import argparse
import csv
import hashlib
import itertools
import json
import re
import sqlite3
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
LOCK_FILE = HERE / "sources.lock.json"
COLLOQUIAL_FILE = HERE / "colloquial_chars.txt"

# 改動資料或表格結構時加一，並同步改 App 讀取的檔名（ginput-<版本>.db）。
DICTIONARY_VERSION = 4

# 和 engine 模組 Scheme 的資料庫代號一致。粵拼不在 codes 表，另有 jyutping 表。
SCHEME_CANGJIE = 1
SCHEME_QUICK = 2

# 用單字的讀音砌出詞的讀音時，每個詞最多砌幾個。
MAX_COMPOSED_READINGS = 8

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
    colloquial INTEGER NOT NULL
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
"""

USED_SOURCES = (
    "libcangjie/table.txt",
    "wordshk/charcount.csv",
    "wordshk/existingwordcount.csv",
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


def read_cangjie(path: Path) -> dict:
    """讀 libcangjie 的 table.txt，只留 Big5 或香港增補字符集裏、有倉頡碼的中文字。"""
    chars = {}
    with path.open(encoding="utf-8") as f:
        for number, line in enumerate(f, 1):
            line = line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            # 只按半形空格分欄：最後一行的字本身就是全形空格。
            fields = line.split(" ")
            if len(fields) != 15:
                raise ValueError(f"{path}:{number}: 應有 15 欄，實有 {len(fields)} 欄")
            ch, _simplified, chinese, big5, hkscs = fields[:5]
            if chinese != "1" or (big5 != "1" and hkscs != "1"):
                continue
            entry = chars.setdefault(ch, {3: set(), 5: set(), "order": 0})
            for generation, codes in ((3, fields[11]), (5, fields[12])):
                if codes == "NA":
                    continue
                for code in codes.split(","):
                    if not (code.isascii() and code.isalpha() and code.islower()):
                        raise ValueError(f"{path}:{number}: 不合法的碼 {code!r}")
                    entry[generation].add(code)
            entry["order"] = max(entry["order"], int(fields[14]))
    return {ch: entry for ch, entry in chars.items() if entry[3] or entry[5]}


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


def read_colloquial(path: Path, chars: dict) -> set:
    listed = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        listed.update(line.split("#", 1)[0].split())
    missing = sorted(ch for ch in listed if ch not in chars)
    if missing:
        raise ValueError(f"{path.name} 有字不在字庫裏：{' '.join(missing)}")
    return listed


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


def build(sources: Path, out: Path, licenses: Path) -> None:
    hashes = verify_sources(sources)
    chars = read_cangjie(sources / "libcangjie/table.txt")
    frequency = read_counts(sources / "wordshk/charcount.csv")
    colloquial = read_colloquial(COLLOQUIAL_FILE, chars)
    ranks = windows_ranks(chars)
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
        "INSERT INTO chars VALUES (?, ?, ?, ?, ?)",
        ((ids[ch], ch, frequency.get(ch, 0), ranks.get(ch, 0), int(ch in colloquial)) for ch in sorted(chars)),
    )

    code_rows = set()
    for ch, entry in chars.items():
        for generation in (3, 5):
            for code in entry[generation]:
                code_rows.add((SCHEME_CANGJIE, code, generation, ids[ch]))
                code_rows.add((SCHEME_QUICK, quick_code(code), generation, ids[ch]))
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
    print(f"碼：{len(code_rows)}")
    print(f"詞：{len(words)}")
    print(f"粵拼：{len(readings)} 字、{len(spoken)} 詞，共 {len(jyutping_rows)} 項")
    print(f"{out}：{out.stat().st_size / 1024 / 1024:.1f} MB")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--sources", type=Path, required=True, help="fetch_sources.py 的 --dest 資料夾")
    parser.add_argument("--out", type=Path, required=True, help="輸出的 SQLite 檔案")
    parser.add_argument("--licenses", type=Path, required=True, help="輸出授權聲明的資料夾（App 的 assets）")
    args = parser.parse_args()
    build(args.sources, args.out, args.licenses)


if __name__ == "__main__":
    main()
