"""用 12dicts 的詞和 wordfreq 的詞頻建置英文建議用的詞庫（SQLite）。

用法（在專案根目錄，先跑 fetch_sources.py）：
    python -I -X utf8 tools/dictgen/build_english.py --sources build/dict-sources \
        --out app/src/main/assets/dict/english-1.db --licenses app/src/main/assets/licenses

詞頻來自 wordfreq（CC BY-SA 4.0），所以這個檔案也以 CC BY-SA 4.0 授權，和主字庫分開（ADR 0004）。
授權和出處寫在檔案的 meta 表裏，也寫進 App 的授權聲明。資料來源和規則見同一資料夾的 README.md。
"""

import argparse
import fnmatch
import gzip
import hashlib
import json
import re
import sqlite3
import sys
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
LOCK_FILE = HERE / "sources.lock.json"
ENGLISH_EXTRA_FILE = HERE / "english_extra.txt"
ENGLISH_BLOCKED_FILE = HERE / "english_blocked.txt"

# 改動資料或表格結構時加一，並同步改 App 讀取的檔名（english-<版本>.db）。
ENGLISH_VERSION = 1

USED_SOURCES = (
    "12dicts/12dicts-6.0.2.zip",
    "wordfreq/large_en.msgpack.gz",
    "wordfreq/NOTICE.md",
    "wordfreq/README.md",
)

# 12dicts 的常用程度等級（1 最常用、21 最少用）：2016 年新詞當作第 12 級，english_extra.txt 當作第 3 級。
NEOLOGISM_CLASS = 12
EXTRA_CLASS = 3

# wordfreq 的詞頻以負 centibel 表示（數值越小越常用，large_en 最大是 799）。
# wordfreq 沒有的詞排在最後，再按 12dicts 的常用程度等級排。
UNKNOWN_FREQUENCY = 900

# 同一個詞 12dicts 也收了小寫寫法時（例如 may 和 May），其他寫法稍為排後。
CASE_VARIANT_PENALTY = 30

ENGLISH_WORD = re.compile(r"[A-Za-z][A-Za-z']*")

SCHEMA = """
CREATE TABLE meta (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
CREATE TABLE english (
    key TEXT NOT NULL,
    word TEXT NOT NULL,
    frequency INTEGER NOT NULL,
    PRIMARY KEY (key, word)
) WITHOUT ROWID;
"""

LICENSE_URL = "https://creativecommons.org/licenses/by-sa/4.0/"

CHANGES = (
    "GInput kept wordfreq's English frequencies (large_en, in centibels) only for the words it suggests, "
    "and combined them with words from 12dicts 6.0.2 by Alan Beale (public domain; it depends on AGID "
    "by Kevin Atkinson) and GInput's own word lists."
)

ATTRIBUTION = (
    f"This file is licensed under CC BY-SA 4.0 ({LICENSE_URL}). "
    "It is adapted from the data of wordfreq v3.2 by Robyn Speer (https://github.com/rspeer/wordfreq), "
    "licensed under CC BY-SA 4.0. wordfreq includes the SUBTLEX word lists by Marc Brysbaert et al., "
    "which are freely available data. "
    f"Changes: {CHANGES} "
    "Full notices: licenses/wordfreq.txt and licenses/12dicts-agid.txt in the GInput app."
)


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


def unpack_msgpack(data: bytes):
    """只支援 wordfreq 的 cBpack 用到的 msgpack 型別：map、array、字串、整數、nil 和布林。"""
    pos = 0

    def read():
        nonlocal pos
        tag = data[pos]
        pos += 1
        if tag <= 0x7F:
            return tag
        if 0x80 <= tag <= 0x8F:
            return read_map(tag & 0x0F)
        if 0x90 <= tag <= 0x9F:
            return read_array(tag & 0x0F)
        if 0xA0 <= tag <= 0xBF:
            return read_str(tag & 0x1F)
        if tag >= 0xE0:
            return tag - 0x100
        simple = {0xC0: None, 0xC2: False, 0xC3: True}
        if tag in simple:
            return simple[tag]
        sized = {
            0xCC: lambda: read_uint(1),
            0xCD: lambda: read_uint(2),
            0xCE: lambda: read_uint(4),
            0xD9: lambda: read_str(read_uint(1)),
            0xDA: lambda: read_str(read_uint(2)),
            0xDB: lambda: read_str(read_uint(4)),
            0xDC: lambda: read_array(read_uint(2)),
            0xDD: lambda: read_array(read_uint(4)),
            0xDE: lambda: read_map(read_uint(2)),
            0xDF: lambda: read_map(read_uint(4)),
        }
        if tag not in sized:
            raise ValueError(f"不支援的 msgpack 型別 0x{tag:02x}（位置 {pos - 1}）")
        return sized[tag]()

    def read_uint(size: int) -> int:
        nonlocal pos
        value = int.from_bytes(data[pos:pos + size], "big")
        pos += size
        return value

    def read_str(size: int) -> str:
        nonlocal pos
        value = data[pos:pos + size].decode("utf-8")
        pos += size
        return value

    def read_array(size: int) -> list:
        return [read() for _ in range(size)]

    def read_map(size: int) -> dict:
        return {read(): read() for _ in range(size)}

    value = read()
    if pos != len(data):
        raise ValueError(f"msgpack 資料之後還有 {len(data) - pos} 個位元組")
    return value


def read_wordfreq(path: Path) -> dict:
    """wordfreq 的 cBpack：第一項是表頭，之後第 i 項是詞頻為 -i centibel 的詞（全部小寫）。"""
    packed = unpack_msgpack(gzip.decompress(path.read_bytes()))
    if packed[0] != {"format": "cB", "version": 1}:
        raise ValueError(f"{path}: 不是 cBpack 第 1 版")
    ranks = {}
    for rank, words in enumerate(packed[1:]):
        for word in words:
            ranks.setdefault(word, rank)
    return ranks


def clean_12dicts(token: str):
    """去掉 12dicts 的註記（* ! % 等）和專有名詞的括號；只留以字母開頭、由英文字母和撇號組成的詞。"""
    word = token.strip().rstrip("*!%+:$~=").strip()
    if word.startswith("(") and word.endswith(")"):
        word = word[1:-1]
    return word if ENGLISH_WORD.fullmatch(word) else None


def read_list(path: Path) -> list:
    """GInput 自己的詞表：# 之後是註解，每行可以放多個詞，用空白分隔。"""
    return [word for line in path.read_text(encoding="utf-8").splitlines() for word in line.split("#", 1)[0].split()]


def read_words(zip_path: Path) -> dict:
    """12dicts 的 2+2+3frq 分 21 個常用程度等級，詞形跟隨原形的等級；再加 2016 年新詞和 english_extra.txt。"""
    with zipfile.ZipFile(zip_path) as archive:
        frequency = archive.read("Lemmatized/2+2+3frq.txt").decode("ascii")
        neologisms = archive.read("Special/neol2016.txt").decode("ascii")
    words = {}

    def add(token: str, level: int) -> None:
        word = clean_12dicts(token)
        if word and words.get(word, level + 1) > level:
            words[word] = level

    level = None
    for line in frequency.splitlines():
        if line.startswith("-----"):
            level = int(line.strip("- "))
        elif line.strip():
            for token in line.split(","):
                add(token, level)
    for line in neologisms.splitlines():
        for token in line.split(","):
            add(token, NEOLOGISM_CLASS)
    for word in read_list(ENGLISH_EXTRA_FILE):
        if not ENGLISH_WORD.fullmatch(word):
            raise ValueError(f"{ENGLISH_EXTRA_FILE.name}: 不合法的詞 {word!r}")
        add(word, EXTRA_CLASS)
    return words


def blocked_pattern(path: Path) -> re.Pattern:
    """english_blocked.txt 的項目合併成一個正規表示式；* 代表任何字母。"""
    items = [item.lower() for item in read_list(path)]
    if not items:
        raise ValueError(f"{path.name} 是空的")
    return re.compile("|".join(fnmatch.translate(item) for item in items))


def english_key(word: str) -> str:
    """查英文建議用的鍵：小寫、去掉撇號，打 dont 也找得到 don't。"""
    return word.lower().replace("'", "")


def write_notices(sources: Path, licenses: Path) -> None:
    """12dicts 依賴的 AGID 要求附上聲明；wordfreq 要求以 Robyn Speer 之名註明出處，並註明 SUBTLEX 的作者。"""
    licenses.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(sources / "12dicts/12dicts-6.0.2.zip") as archive:
        agid = archive.read("agid.txt").decode("latin-1")
    (licenses / "12dicts-agid.txt").write_text(
        "English word list: 12dicts 6.0.2 by Alan Beale (http://wordlist.aspell.net/12dicts/),\n"
        "released to the public domain by its author, who requests acknowledgment.\n"
        "It depends on AGID by Kevin Atkinson, whose notice follows.\n\n" + agid,
        encoding="utf-8",
    )
    readme = (sources / "wordfreq/README.md").read_text(encoding="utf-8")
    citations = readme[readme.index("## Citations to work that wordfreq is built on"):]
    (licenses / "wordfreq.txt").write_text(
        "English word frequencies: wordfreq v3.2 by Robyn Speer (https://github.com/rspeer/wordfreq).\n\n"
        "GInput's English word list (dict/english-<version>.db) is adapted from wordfreq's data and is\n"
        f"licensed under CC BY-SA 4.0 ({LICENSE_URL}).\n"
        f"Changes: {CHANGES}\n\n"
        "wordfreq's notice and the citations from its README follow.\n\n"
        + (sources / "wordfreq/NOTICE.md").read_text(encoding="utf-8").rstrip()
        + "\n\n"
        + citations,
        encoding="utf-8",
    )


def build(sources: Path, out: Path, licenses: Path) -> None:
    hashes = verify_sources(sources)
    classes = read_words(sources / "12dicts/12dicts-6.0.2.zip")
    ranks = read_wordfreq(sources / "wordfreq/large_en.msgpack.gz")
    blocked = blocked_pattern(ENGLISH_BLOCKED_FILE)

    has_lowercase = {word for word in classes if word == word.lower()}
    words = {}
    for word, level in classes.items():
        if blocked.match(word.lower()):
            continue
        rank = ranks.get(word.lower())
        frequency = rank if rank is not None else UNKNOWN_FREQUENCY + level
        if word != word.lower() and word.lower() in has_lowercase:
            frequency += CASE_VARIANT_PENALTY
        words[word] = frequency

    out.parent.mkdir(parents=True, exist_ok=True)
    if out.exists():
        out.unlink()
    db = sqlite3.connect(out)
    db.executescript(SCHEMA)
    db.executemany(
        "INSERT INTO english VALUES (?, ?, ?)",
        sorted((english_key(word), word, frequency) for word, frequency in words.items()),
    )
    meta = {
        "version": str(ENGLISH_VERSION),
        "license": "CC-BY-SA-4.0",
        "attribution": ATTRIBUTION,
        "sources": json.dumps(hashes, sort_keys=True),
    }
    db.executemany("INSERT INTO meta VALUES (?, ?)", sorted(meta.items()))
    db.execute(f"PRAGMA user_version = {ENGLISH_VERSION}")
    db.commit()
    db.execute("VACUUM")
    db.close()

    write_notices(sources, licenses)

    unknown = sum(frequency >= UNKNOWN_FREQUENCY for frequency in words.values())
    print(f"英文詞：{len(words)}（wordfreq 沒有的 {unknown}、封鎖 {len(classes) - len(words)}）")
    print(f"{out}：{out.stat().st_size / 1024:.0f} KB")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--sources", type=Path, required=True, help="fetch_sources.py 的 --dest 資料夾")
    parser.add_argument("--out", type=Path, required=True, help="輸出的 SQLite 檔案")
    parser.add_argument("--licenses", type=Path, required=True, help="輸出授權聲明的資料夾（App 的 assets）")
    args = parser.parse_args()
    build(args.sources, args.out, args.licenses)


if __name__ == "__main__":
    main()
