#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
由萬國蝦米碼表產生 app/src/main/assets/tables/standard/dictionary.json

字頻來源：教育部語文司「常用字字頻表」表一（5,731 字，原始出現頻次）
簡體字降權：OpenCC TSCharacters.txt（只存在於簡體的字）
碼表來源：chinese-opendesktop/cin-tables（CC0-1.0）uniliu.cin

用法：
    python3 tools/build-dictionary.py                     # 用 .cache 內的 uniliu.cin 產生字典
    python3 tools/build-dictionary.py --cin PATH          # 指定 .cin
    python3 tools/build-dictionary.py --refresh-cin       # 重新下載 uniliu.cin
    python3 tools/build-dictionary.py --refresh-charcount # 從教育部 PDF 重建字頻表
    python3 tools/build-dictionary.py --refresh-simplified# 從 OpenCC 重建簡體字集
    python3 tools/build-dictionary.py --out PATH
"""

import argparse
import json
import math
import os
import re
import subprocess
import sys
import tempfile
import unicodedata
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOOLS = os.path.join(ROOT, "tools")
DATA = os.path.join(TOOLS, "data")
CACHE = os.path.join(TOOLS, ".cache")

CIN_URL = "https://raw.githubusercontent.com/chinese-opendesktop/cin-tables/master/uniliu.cin"
CIN_NAME = "uniliu.cin"
CHARCOL_URL = "https://language.moe.gov.tw/001/Upload/files/SITE_CONTENT/M0001/BIAU1.zip"
OPENCC_URL = "https://raw.githubusercontent.com/BYVoid/OpenCC/master/data/dictionary/TSCharacters.txt"
OPENCC_ST_URL = "https://raw.githubusercontent.com/BYVoid/OpenCC/master/data/dictionary/STCharacters.txt"

CHARCOL_TSV = os.path.join(DATA, "moe-char-count.tsv")
SIMPLIFIED_TXT = os.path.join(DATA, "simplified-only.txt")
DEFAULT_OUT = os.path.join(ROOT, "app", "src", "main", "assets", "tables", "standard", "dictionary.json")

# 與 DictionaryDownloader.parseCinFile 保持一致
CODE_PATTERN = re.compile(r"^[a-z,.'\[\]]+$")

# uniliu.cin 收了 165 個假名但獨缺「の」，此處依該檔自身的假名慣例補上。
#
# 為何不用 unicon 的 hiragana.cin（原廠碼 a/ka/no）併入？因為碼位直接衝突：
# 萬國蝦米的單字母是常用漢字（a=對、i=後、u=以、ka=黃、ta=頁），
# 假名插進去會被常用字擠到第 10 碼以後，等於打不出來。
# 萬國蝦米的假名一律帶 j 前綴與逗號（ja, / jka, / jn,）本就是為了避開這類撞碼。
KANA_PATCH = [("jno,", "の")]

# 同一 repo 的 boshiamy.cin（劉重次原廠嘸蝦米）獨有的字，主要是 № ⅰ ① 々 ゞ
# ヂ ヴ 等萬國蝦米未收錄的符號與符號用字（日文也會用到 々 〆 ヂ ヅ ヴ）。
# 兩表的漢字覆蓋幾乎重疊（原廠僅少 7,834 個漢字卻多 42 字），故直接併入，
# 讓使用者不必在兩份碼表之間切換。
SUPPLEMENT_CIN = ("boshiamy.cin",)
T9_MAP = {
    **dict.fromkeys("abc", "2"),
    **dict.fromkeys("def", "3"),
    **dict.fromkeys("ghi", "4"),
    **dict.fromkeys("jkl", "5"),
    **dict.fromkeys("mno", "6"),
    **dict.fromkeys("pqrs", "7"),
    **dict.fromkeys("tuv", "8"),
    **dict.fromkeys("wxyz", "9"),
}

# 靜態字頻壓縮到 1..999，讓 DictionaryManager 的 userFreq * 1000 一定壓得過靜態值
# （使用者打過一次就該排前面，這是回報「學不會」的直覺修正）
STATIC_MAX = 999
SIMPLIFIED_PENALTY = 1_000_000


def is_pua(ch):
    return "\ue000" <= ch <= "\uf8ff"


def to_t9(code):
    return "".join(T9_MAP.get(c, c) for c in code)


def download(url, dest, label):
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    print(f"  下載 {label} …", flush=True)
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "BoshiamyIME-build-dictionary"})
        with urllib.request.urlopen(req, timeout=120) as r, open(dest, "wb") as f:
            f.write(r.read())
    except Exception as exc:
        # 教育部網站的憑證缺少 Subject Key Identifier，urllib 會拒絕，改走 curl
        print(f"  urllib 失敗（{exc}），改用 curl")
        subprocess.check_call(["curl", "-fsSL", "--max-time", "180", "-o", dest, url])
    print(f"  → {dest} ({os.path.getsize(dest):,} bytes)")


def parse_cin(path):
    """讀 .cin 的 %chardef 區段，規則與 DictionaryDownloader.parseCinFile 相同。"""
    entries = []
    seen = set()
    in_chardef = False
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            trimmed = line.strip()
            if trimmed == "%chardef begin":
                in_chardef = True
                continue
            if trimmed == "%chardef end":
                break
            if not in_chardef or not trimmed or trimmed.startswith("#"):
                continue
            parts = re.split(r"\s+", trimmed, maxsplit=1)
            if len(parts) != 2:
                continue
            code = parts[0].lower()
            char = parts[1]
            if not code or not CODE_PATTERN.match(code):
                continue
            key = f"{code}|{char}"
            if key in seen:
                continue
            seen.add(key)
            entries.append((code, char))
    # 補丁：uniliu.cin 收了 165 個假名但獨缺「の」，這裡依該檔自身慣例補回來。
    # jno, 可能已被私用區字形（康熙部首，必然顯示方塊）佔用，此時仍要加入，
    # 靠 GlyphSupport 把方塊字沉底讓「の」浮上來。
    for code, char in KANA_PATCH:
        if not any(c == char for _co, c in entries):
            entries.append((code, char))
    # Python 的排序是穩定的，同碼的字維持 .cin 內的原始順序作為最終排序依據
    entries.sort(key=lambda e: e[0])
    return entries


def merge_supplements(entries):
    """把 boshiamy.cin 獨有的字併進來（萬國蝦米未收錄的符號、々 〆 ヂ ヴ 等）。"""
    existing = {char for _code, char in entries}
    added = []
    for name in SUPPLEMENT_CIN:
        path = os.path.join(CACHE, name)
        if not os.path.exists(path):
            raise SystemExit(f"找不到 {path}，請先下載 {name} 到 tools/.cache/")
        for code, char in parse_cin(path):
            if char in existing:
                continue
            existing.add(char)
            added.append((code, char))
    entries.extend(added)
    entries.sort(key=lambda e: e[0])
    return added
    entries.sort(key=lambda e: e[0])
    return entries


def refresh_cin():
    dest = os.path.join(CACHE, CIN_NAME)
    download(CIN_URL, dest, CIN_NAME)
    return dest


def refresh_charcount():
    """從教育部 BIAU1.zip 內的 PDF 抽出表一字頻（PDF 有完整 5,731 字，TXT 版本被截斷）。"""
    for tool in ("pdftotext", "iconv"):
        if subprocess.call(["which", tool], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL) != 0:
            sys.exit(f"需要 {tool}（poppler-utils / libc-utils）才能重建字頻表")

    with tempfile.TemporaryDirectory() as tmp:
        zpath = os.path.join(tmp, "BIAU1.zip")
        download(CHARCOL_URL, zpath, "BIAU1.zip")
        subprocess.check_call(["unzip", "-o", "-q", zpath, "-d", tmp])
        pdf = os.path.join(tmp, "BIAU1", "BIAU1.pdf")
        txt = os.path.join(tmp, "BIAU1.txt")
        subprocess.check_call(["pdftotext", "-layout", pdf, txt])

        rows = {}
        with open(txt, encoding="utf-8", errors="ignore") as f:
            for line in f:
                if "║" not in line or "│" not in line:
                    continue
                cells = [c.strip() for c in re.split(r"[║│]", line)]
                cells = [c for c in cells if c]
                if len(cells) != 7:
                    continue
                rank, char, _radical, strokes, count = cells[0], cells[1], cells[2], cells[3], cells[4]
                if not rank.isdigit() or len(char) != 1 or not strokes.isdigit():
                    continue
                try:
                    value = int(count.replace(",", ""))
                except ValueError:
                    continue
                # PDF 有些字落在 CJK 相容區（不 = U+F967），需正規化回統一碼
                char = unicodedata.normalize("NFKC", char)
                rows[int(rank)] = (char, value)

    if sorted(rows) != list(range(1, len(rows) + 1)):
        sys.exit("字頻表字頻序號不連續，PDF 解析可能有誤，已中止")

    # 私用區字形（PDF 字型缺字）在輸出時捨棄，但序號驗證要在捨棄之前做
    entries = [(char, value) for _rank, (char, value) in sorted(rows.items()) if not is_pua(char)]

    os.makedirs(DATA, exist_ok=True)
    with open(CHARCOL_TSV, "w", encoding="utf-8") as out:
        out.write("# 教育部語文司 常用字字頻表 表一（原始出現頻次）\n")
        out.write(f"# 來源：{CHARCOL_URL}（BIAU1.pdf，取用 2026-10-01）\n")
        out.write(f"# 原始 {len(rows)} 字，捨棄 {len(rows) - len(entries)} 個私用區字形，實際輸出 {len(entries)} 字\n")
        out.write("# 格式：字<TAB>出現頻次\n")
        for char, value in entries:
            out.write(f"{char}\t{value}\n")
    print(f"  → {CHARCOL_TSV}（{len(entries):,} 字）")


def refresh_simplified():
    """
    判定「只存在於簡體」的字。

    OpenCC TSCharacters 是繁→簡、STCharacters 是簡→繁，兩者單獨用都會誤判：
      - 只看 TSCharacters 的右欄，會把 丫（亞的簡化字）當成簡體，但 丫 本身是合法正體。
      - 只看「TSCharacters 右欄有、左欄無」，會把 谷／只／后／干／台／里／面／板
        這些「自己也是正體、只是恰好也是別字的簡化字」全部誤判。

    正確規則：X 必須是 STCharacters 的鍵（OpenCC 認定它是簡化字），
    且 s2t(X) 的候選裡不含 X 本身（代表 X 沒有對應的合法正體用法）。
    """
    ts_path = os.path.join(CACHE, "TSCharacters.txt")
    st_path = os.path.join(CACHE, "STCharacters.txt")
    download(OPENCC_URL, ts_path, "TSCharacters.txt")
    download(OPENCC_ST_URL, st_path, "STCharacters.txt")

    simplified_to_traditional = {}
    with open(st_path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("#") or not line.strip():
                continue
            key, values = line.rstrip("\n").split("\t")
            simplified_to_traditional[key] = values.split()

    only_simplified = sorted(
        char
        for char, candidates in simplified_to_traditional.items()
        if char not in candidates
    )
    os.makedirs(DATA, exist_ok=True)
    with open(SIMPLIFIED_TXT, "w", encoding="utf-8") as out:
        out.write("# 只存在於簡體、沒有對應正體用法的字（候選排序時降權）\n")
        out.write("# 判定：X 是 OpenCC STCharacters（簡→繁）的鍵，且 s2t(X) 不含 X 本身\n")
        out.write(f"# 來源：{OPENCC_ST_URL}（取用 2026-10-01）\n")
        out.write(f"# 共 {len(only_simplified)} 字\n")
        for char in only_simplified:
            out.write(f"{char}\n")
    print(f"  → {SIMPLIFIED_TXT}（{len(only_simplified):,} 字）")


def load_charcount():
    counts = {}
    with open(CHARCOL_TSV, encoding="utf-8") as f:
        for line in f:
            if line.startswith("#") or not line.strip():
                continue
            char, value = line.rstrip("\n").split("\t")
            counts[char] = int(value)
    if not counts:
        sys.exit(f"{CHARCOL_TSV} 是空的，請先執行 --refresh-charcount")
    return counts


def load_simplified():
    chars = set()
    with open(SIMPLIFIED_TXT, encoding="utf-8") as f:
        for line in f:
            if line.startswith("#") or not line.strip():
                continue
            chars.add(line.strip())
    if not chars:
        sys.exit(f"{SIMPLIFIED_TXT} 是空的，請先執行 --refresh-simplified")
    return chars


def static_frequency(char, counts):
    """把教育部原始頻次對數壓縮到 1..999；不在字頻表的罕見/異體字給 0。"""
    count = counts.get(char)
    if not count:
        return 0
    top = max(counts.values())
    return 1 + round((STATIC_MAX - 1) * math.log(count) / math.log(top))


def main():
    parser = argparse.ArgumentParser(description="產生標準（萬國蝦米）字典")
    parser.add_argument("--cin", help="uniliu.cin 路徑（預設用 tools/.cache/）")
    parser.add_argument("--out", default=DEFAULT_OUT, help="輸出 dictionary.json")
    parser.add_argument("--refresh-cin", action="store_true")
    parser.add_argument("--refresh-charcount", action="store_true")
    parser.add_argument("--refresh-simplified", action="store_true")
    args = parser.parse_args()

    did_something = False
    if args.refresh_cin:
        print("重新下載碼表：")
        refresh_cin()
        did_something = True
    if args.refresh_charcount:
        print("重建教育部字頻表：")
        refresh_charcount()
        did_something = True
    if args.refresh_simplified:
        print("重建簡體字集：")
        refresh_simplified()
        did_something = True
    if did_something:
        return

    cin = args.cin or os.path.join(CACHE, CIN_NAME)
    if not os.path.exists(cin):
        sys.exit(f"找不到 {cin}，請先執行 --refresh-cin 或用 --cin 指定")

    counts = load_charcount()
    simplified = load_simplified()

    pairs = parse_cin(cin)
    print(f"讀入 {os.path.basename(cin)}：{len(pairs):,} 筆")

    supplemented = merge_supplements(pairs)
    print(
        f"併入 {'+'.join(SUPPLEMENT_CIN)} 獨有字：{len(supplemented):,} 筆"
        f"（{''.join(sorted(c for _co, c in supplemented))}）"
    )
    print(f"字頻表 {len(counts):,} 字、簡體字集 {len(simplified):,} 字")

    out_entries = []
    stats = {"with_freq": 0, "simplified": 0, "zero": 0}
    for code, char in pairs:
        freq = static_frequency(char, counts)
        if char in simplified:
            # 簡體字保留其正體的頻次量體，但整體壓到負數區，確保一定排在正體後面
            freq = -(SIMPLIFIED_PENALTY) + abs(freq)
            stats["simplified"] += 1
        elif freq > 0:
            stats["with_freq"] += 1
        else:
            stats["zero"] += 1
        out_entries.append({"code": code, "t9": to_t9(code), "char": char, "frequency": freq})

    root = {
        "version": "bundled",
        "encoding": "boshiamy-standard",
        "source": "https://github.com/chinese-opendesktop/cin-tables",
        "source_file": CIN_NAME,
        "license": "CC0-1.0",
        "frequency_source": "教育部語文司 常用字字頻表 表一（5,731 字，對數壓縮至 1-999）",
        "entries": out_entries,
    }

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(root, f, ensure_ascii=False, separators=(",", ":"))

    print(f"→ {args.out}（{len(out_entries):,} 筆，{os.path.getsize(args.out):,} bytes）")
    print(
        f"  有字頻 {stats['with_freq']:,}、簡體降權 {stats['simplified']:,}、"
        f"無字頻（罕見/異體）{stats['zero']:,}"
    )


if __name__ == "__main__":
    main()
