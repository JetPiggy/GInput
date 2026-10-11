"""用 Unicode 的 emoji-test.txt 建置 Emoji 面板的資料，再用 CLDR 的中文關鍵字建置 Emoji 關鍵字。

用法（在專案根目錄，先跑 fetch_sources.py 和 build_dict.py）：
    python -I -X utf8 tools/dictgen/build_emoji.py --sources build/dict-sources \
        --dict app/src/main/assets/dict/ginput-4.db --out app/src/main/assets/emoji/emoji.tsv \
        --keywords app/src/main/assets/emoji/keywords.tsv --licenses app/src/main/assets/licenses

emoji.tsv 每行一個 Emoji：分類<Tab>Emoji<Tab>膚色變體（以空格分隔，可以沒有）。
keywords.tsv 每行一個關鍵字：關鍵字<Tab>Emoji（以空格分隔，排前的先出）。兩個檔案裏 # 開頭的都是註解。
手機的字型未必支援最新的 Emoji，App 載入時會再篩走顯示不到的。
"""

import argparse
import hashlib
import json
import re
import sqlite3
import sys
import unicodedata
import xml.etree.ElementTree as ET
from pathlib import Path

HERE = Path(__file__).resolve().parent
LOCK_FILE = HERE / "sources.lock.json"
EXTRA_FILE = HERE / "emoji_keywords_extra.txt"
BLOCKED_FILE = HERE / "emoji_keywords_blocked.txt"
CLDR_BASE = "cldr/zh_Hant.xml"
CLDR_HK = "cldr/zh_Hant_HK.xml"
USED_SOURCES = ("unicode/emoji-test.txt", "unicode/license.txt", CLDR_BASE, CLDR_HK)

# emoji-test.txt 的 group → App 用的分類；Component（膚色、髮型部件）不收。
GROUPS = {
    "Smileys & Emotion": "smileys",
    "People & Body": "people",
    "Animals & Nature": "nature",
    "Food & Drink": "food",
    "Travel & Places": "travel",
    "Activities": "activities",
    "Objects": "objects",
    "Symbols": "symbols",
    "Flags": "flags",
}
SKIN_TONES = range(0x1F3FB, 0x1F400)
VARIATION_SELECTOR = 0xFE0F
ZWJ = 0x200D
GENDER_SIGNS = (0x2640, 0x2642)  # ♀ ♂
# 男、女版本的人物 → 中性版本：👨 👩 → 🧑、👦 👧 → 🧒、👴 👵 → 🧓。
NEUTRAL_PEOPLE = {0x1F468: 0x1F9D1, 0x1F469: 0x1F9D1, 0x1F466: 0x1F9D2, 0x1F467: 0x1F9D2, 0x1F474: 0x1F9D3, 0x1F475: 0x1F9D3}

# CLDR 用來表示「和上一層（zh_Hant）一樣」的記號。
INHERIT = "↑↑↑"
MAX_KEYWORD_LENGTH = 6
# App 最多顯示 3 個；多留幾個，手機字型顯示不到的時候用後面的補上。
MAX_EMOJI_PER_KEYWORD = 6


def verify_sources(sources: Path) -> dict:
    lock = json.loads(LOCK_FILE.read_text(encoding="utf-8"))
    for name in USED_SOURCES:
        if lock[name]["sha256"] != hashlib.sha256((sources / name).read_bytes()).hexdigest():
            sys.exit(f"{name} 和 sources.lock.json 不符，先用 fetch_sources.py 重新下載。")
    return lock


def key(codes) -> tuple:
    """比對時不理變體選擇符：☝️（有 FE0F）的膚色版本是 ☝🏻（沒有），CLDR 的 ❤ 也沒有。"""
    return tuple(code for code in codes if code != VARIATION_SELECTOR)


def neutral(codes: tuple) -> tuple:
    """比對鍵的中性版本，例如 🤷‍♂️ → 🤷、👩‍🎓 → 🧑‍🎓；本身是中性的原樣回傳。"""
    if len(codes) >= 3 and codes[-2] == ZWJ and codes[-1] in GENDER_SIGNS:
        codes = codes[:-2]
    return tuple(NEUTRAL_PEOPLE.get(code, code) for code in codes)


def read_emoji_test(sources: Path) -> tuple[str, list, dict]:
    """回傳版本、原形 Emoji（分類, Emoji, 比對鍵）和膚色變體（原形的比對鍵 → 變體）。"""
    version = "?"
    group = None
    bases = []
    variants = {}
    for line in (sources / "unicode/emoji-test.txt").read_text(encoding="utf-8").splitlines():
        if line.startswith("# Version:"):
            version = line.split(":", 1)[1].strip()
        elif line.startswith("# group:"):
            group = GROUPS.get(line.split(":", 1)[1].strip())
        elif line and not line.startswith("#") and group:
            codes_text, rest = line.split(";", 1)
            if rest.split("#", 1)[0].strip() != "fully-qualified":
                continue
            codes = [int(code, 16) for code in codes_text.split()]
            emoji = "".join(map(chr, codes))
            plain = [code for code in codes if code not in SKIN_TONES]
            if len(plain) == len(codes):
                bases.append((group, emoji, key(codes)))
            else:
                variants.setdefault(key(plain), []).append(emoji)
    return version, bases, variants


def write_panel(out: Path, version: str, bases: list, variants: dict) -> None:
    known = {base_key for _, _, base_key in bases}
    orphans = sum(len(v) for k, v in variants.items() if k not in known)
    out.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        f"# Unicode emoji-test.txt, Version {version}. Unicode License v3; see licenses/unicode.txt.",
        "# 分類<Tab>Emoji<Tab>膚色變體",
    ]
    lines += [f"{group}\t{emoji}\t{' '.join(variants.get(base_key, []))}" for group, emoji, base_key in bases]
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Emoji 版本 {version}：{len(bases)} 個，膚色變體 {sum(len(v) for v in variants.values()) - orphans} 個")
    print(f"沒有對應原形、略去的變體：{orphans} 個")
    print(f"{out}：{out.stat().st_size / 1024:.0f} KB")


def read_annotations(path: Path) -> tuple[dict, dict]:
    """CLDR annotations：Emoji → 關鍵字清單、Emoji → 名稱。值可以是 INHERIT。"""
    keywords, names = {}, {}
    for node in ET.parse(path).getroot().iter("annotation"):
        text = (node.text or "").strip()
        if node.get("type") == "tts":
            names[node.get("cp")] = text
        else:
            keywords[node.get("cp")] = text
    return keywords, names


def cldr_annotations(sources: Path) -> list:
    """香港的關鍵字和名稱；標示 INHERIT 或沒有的，用 zh_Hant 的。回傳（Emoji, 名稱, 關鍵字清單）。"""
    base_keywords, base_names = read_annotations(sources / CLDR_BASE)
    hk_keywords, hk_names = read_annotations(sources / CLDR_HK)
    result = []
    for cp in sorted(set(base_keywords) | set(hk_keywords)):
        keywords = hk_keywords.get(cp, INHERIT)
        if keywords == INHERIT:
            keywords = base_keywords.get(cp, "")
        name = hk_names.get(cp, INHERIT)
        if name == INHERIT:
            name = base_names.get(cp, "")
        result.append((cp, name, [word.strip() for word in keywords.split("|") if word.strip()]))
    return result


def is_chinese(text: str) -> bool:
    ideographs = ("CJK UNIFIED IDEOGRAPH", "CJK COMPATIBILITY IDEOGRAPH")
    return bool(text) and all(unicodedata.name(ch, "").startswith(ideographs) for ch in text)


def read_list(path: Path) -> list[str]:
    """# 開頭的是註解；空行略過。"""
    lines = path.read_text(encoding="utf-8").splitlines()
    return [line.strip() for line in lines if line.strip() and not line.startswith("#")]


def cldr_version(lock: dict) -> str:
    match = re.search(r"release-(\d+)(?:-(\d+))?", lock[CLDR_HK]["url"])
    return ".".join(part for part in match.groups() if part) if match else "?"


def build_keywords(sources: Path, dictionary: Path, out: Path, bases: list, version: str) -> None:
    """Emoji 關鍵字的規則見 docs/spec/v1.md 的「Emoji 關鍵字」。"""
    order = {}  # 比對鍵 → (Emoji 面板的次序, Emoji)
    for index, (_, emoji, base_key) in enumerate(bases):
        order[base_key] = (index, emoji)
    with sqlite3.connect(dictionary) as db:
        typeable = {row[0] for row in db.execute("SELECT ch FROM chars")}

    def usable(word: str) -> bool:
        return is_chinese(word) and len(word) <= MAX_KEYWORD_LENGTH and all(ch in typeable for ch in word)

    def emoji_of(text: str, where: str) -> str:
        found = order.get(key(ord(ch) for ch in text))
        if found is None:
            sys.exit(f"{where}：「{text}」不是 Emoji 面板裏的 Emoji。")
        return found[1]

    # CLDR：單字的關鍵字不用；名稱就是關鍵字的 Emoji 排先，其餘按 Emoji 面板的次序。
    blocked = set(read_list(BLOCKED_FILE))
    cldr = {}  # 關鍵字 → [(不是名稱, Emoji 面板的次序, Emoji)]
    seen = set()
    for cp, name, words in cldr_annotations(sources):
        found = order.get(key(ord(ch) for ch in cp))
        if found is None:  # 不是 Emoji（CLDR 也有符號的名稱），或者是 Component
            continue
        for word in set(words) | {name}:
            seen.add(word)
            if len(word) >= 2 and usable(word) and word not in blocked:
                cldr.setdefault(word, []).append((word != name, found[0], found[1]))
    if blocked - seen:
        print(f"emoji_keywords_blocked.txt 裏 CLDR 沒有的詞：{'、'.join(sorted(blocked - seen))}")

    def ranked(found: list) -> list:
        """男、女版本排在中性版本後面的話不列，例如「學生」只列 🧑‍🎓，不列 👨‍🎓 和 👩‍🎓。"""
        listed, kept = set(), []
        for _, _, emoji in sorted(found):
            codes = key(ord(ch) for ch in emoji)
            if codes not in listed and neutral(codes) not in listed:
                kept.append(emoji)
            listed.add(codes)
        return kept

    # 自行整理的清單：取代 CLDR 找到的 Emoji。
    extra = {}
    for line in read_list(EXTRA_FILE):
        word, _, emoji_text = line.partition("\t")
        where = f"emoji_keywords_extra.txt 的「{word}」"
        if not usable(word):
            sys.exit(f"{where}：關鍵字要全是字庫裏的中文字，最多 {MAX_KEYWORD_LENGTH} 個字。")
        if word in blocked:
            sys.exit(f"{where}：同時在 emoji_keywords_blocked.txt 裏。")
        if word in extra:
            sys.exit(f"{where}：重複了。")
        extra[word] = [emoji_of(text, where) for text in emoji_text.split()]
        if not extra[word] or len(set(extra[word])) != len(extra[word]):
            sys.exit(f"{where}：沒有 Emoji，或者 Emoji 重複了。")

    table = {word: ranked(found) for word, found in cldr.items()} | extra
    table = {word: emoji[:MAX_EMOJI_PER_KEYWORD] for word, emoji in table.items()}

    out.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        f"# Emoji 關鍵字：Unicode CLDR {version} 的 zh_Hant_HK 和 zh_Hant annotations"
        " (Unicode License v3; see licenses/unicode.txt)，加上 GInput 自行整理的關鍵字。",
        "# 關鍵字<Tab>Emoji（以空格分隔，排前的先出）",
    ]
    lines += [f"{word}\t{' '.join(table[word])}" for word in sorted(table)]
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    singles = sum(1 for word in table if len(word) == 1)
    print(f"Emoji 關鍵字：{len(table)} 個（自行整理 {len(extra)}，其中單字 {singles}；其餘來自 CLDR）")
    print(f"{out}：{out.stat().st_size / 1024:.0f} KB")


def write_license(licenses: Path, sources: Path, emoji_version: str, cldr: str) -> None:
    licenses.mkdir(parents=True, exist_ok=True)
    (licenses / "unicode.txt").write_text(
        f"Emoji data: Unicode emoji-test.txt, Version {emoji_version} (https://www.unicode.org/Public/emoji/).\n"
        f"Emoji keywords: Unicode CLDR {cldr} annotations for zh_Hant and zh_Hant_HK (https://cldr.unicode.org/).\n\n"
        + (sources / "unicode/license.txt").read_text(encoding="utf-8"),
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--sources", type=Path, required=True, help="fetch_sources.py 的 --dest 資料夾")
    parser.add_argument("--dict", type=Path, required=True, help="build_dict.py 建置的字庫，用來確認關鍵字打得出")
    parser.add_argument("--out", type=Path, required=True, help="Emoji 面板的 TSV 檔案")
    parser.add_argument("--keywords", type=Path, required=True, help="Emoji 關鍵字的 TSV 檔案")
    parser.add_argument("--licenses", type=Path, required=True, help="輸出授權聲明的資料夾（App 的 assets）")
    args = parser.parse_args()
    lock = verify_sources(args.sources)
    version, bases, variants = read_emoji_test(args.sources)
    write_panel(args.out, version, bases, variants)
    build_keywords(args.sources, args.dict, args.keywords, bases, cldr_version(lock))
    write_license(args.licenses, args.sources, version, cldr_version(lock))


if __name__ == "__main__":
    main()
