"""下載建置字庫用的資料來源，並用 sources.lock.json 核對 SHA-256。

用法（在專案根目錄）：
    python -I tools/dictgen/fetch_sources.py --dest build/dict-sources
新加入的來源會自動記下雜湊。上游更新後，確認內容無誤，再加 --update-lock 重跑，把新的雜湊寫進 sources.lock.json。
"""

import argparse
import hashlib
import json
import sys
import urllib.request
from pathlib import Path

LIBCANGJIE = "https://sources.debian.org/data/main/libc/libcangjie/1.4.0.really.1.4.0-5/data"
WORDSHK = "https://words.hk/faiman/analysis"
WORDFREQ = "https://raw.githubusercontent.com/rspeer/wordfreq/v3.2"
CLDR = "https://raw.githubusercontent.com/unicode-org/cldr/release-48-2/common/annotations"
# rime-cantonese 沒有發佈版本，所以固定在某個 commit。
RIME_CANTONESE = "https://raw.githubusercontent.com/rime/rime-cantonese/259f0e48bba840c3a2e0d117539e96937f3d89bc"

SOURCES = {
    "libcangjie/table.txt": f"{LIBCANGJIE}/table.txt",
    "libcangjie/README.table.md": f"{LIBCANGJIE}/README.table.md",
    "wordshk/charcount.csv": f"{WORDSHK}/charcount.csv",
    "wordshk/existingwordcount.csv": f"{WORDSHK}/existingwordcount.csv",
    "12dicts/12dicts-6.0.2.zip": "http://downloads.sourceforge.net/wordlist/12dicts-6.0.2.zip",
    "unicode/emoji-test.txt": "https://www.unicode.org/Public/emoji/latest/emoji-test.txt",
    "unicode/license.txt": "https://www.unicode.org/license.txt",
    "cldr/zh_Hant.xml": f"{CLDR}/zh_Hant.xml",
    "cldr/zh_Hant_HK.xml": f"{CLDR}/zh_Hant_HK.xml",
    "wordfreq/large_en.msgpack.gz": f"{WORDFREQ}/wordfreq/data/large_en.msgpack.gz",
    "wordfreq/NOTICE.md": f"{WORDFREQ}/NOTICE.md",
    "wordfreq/README.md": f"{WORDFREQ}/README.md",
    "rime-cantonese/jyut6ping3.chars.dict.yaml": f"{RIME_CANTONESE}/jyut6ping3.chars.dict.yaml",
    "rime-cantonese/jyut6ping3.words.dict.yaml": f"{RIME_CANTONESE}/jyut6ping3.words.dict.yaml",
    "rime-cantonese/LICENSE-CC-BY": f"{RIME_CANTONESE}/LICENSE-CC-BY",
}

LOCK_FILE = Path(__file__).resolve().with_name("sources.lock.json")


def download(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": "GInput-dictgen"})
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.read()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dest", type=Path, required=True, help="存放下載檔案的資料夾")
    parser.add_argument("--update-lock", action="store_true", help="把下載到的雜湊寫進 sources.lock.json")
    args = parser.parse_args()

    lock = json.loads(LOCK_FILE.read_text(encoding="utf-8")) if LOCK_FILE.exists() else {}
    new_lock = {}
    mismatches = []
    for name, url in SOURCES.items():
        data = download(url)
        target = args.dest / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        digest = hashlib.sha256(data).hexdigest()
        new_lock[name] = {"url": url, "sha256": digest}
        if lock.get(name, {}).get("sha256") not in (None, digest):
            mismatches.append(name)
        print(f"{name}  {digest}")

    if mismatches and not args.update_lock:
        sys.exit(
            "以下來源和 sources.lock.json 不符，上游可能已更新："
            + "、".join(mismatches)
            + "。確認內容無誤後，加 --update-lock 重跑。"
        )
    # 新加入 SOURCES 的來源，第一次下載時自動記下雜湊。
    if args.update_lock or new_lock.keys() != lock.keys():
        LOCK_FILE.write_text(json.dumps(new_lock, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"已寫入 {LOCK_FILE.name}")


if __name__ == "__main__":
    main()
