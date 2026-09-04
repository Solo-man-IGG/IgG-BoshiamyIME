# 嘸蝦米輸入法 Android App — 技術規劃

> 自訂嘸蝦米輸入法，支援自動更新字碼表、聯想詞、Gboard 風格鍵盤、T9 預測輸入

---

## 現有方案的痛點

| 輸入法 | 問題 |
|---|---|
| Rime (小狼毫) | 設定複雜、Android 版維護不積極、更新系統後常失效 |
| fcitx5 for Android | 嘸蝦米支援不完整、依賴 native library、版本相容性差 |
| GCIN | 只支援特定系統、更新後常 crash |
| 官方輸入法 | 不支援嘸蝦米 |

**共同問題：** 都依賴 native code (C/C++)，系統更新後 OAT/SELinux 改動容易導致載入失敗。

**本方案優勢：** 純 Kotlin/Java 實作，不依賴 native library，系統更新相容性高。

---

## 核心需求

1. **自訂字碼表** — 從 GitHub 自動下載、快取、更新
2. **聯想詞** — 輸入一個字後顯示相關詞組
3. **Gboard 風格鍵盤** — 現代化 UI，支援主題、動畫
4. **嘸蝦米模式** — 標準、簡速、倚天等輸入模式
5. **T9 預測輸入** — 大按鍵、低誤觸率，適合手大用戶
6. **注音輸入** — 嘸蝦米打不出來時的備用方案

---

## 系統架構

```
┌─────────────────────────────────────────────────────────┐
│                   Android IME App                        │
│                                                          │
│  ┌─────────────────────────────────────────────────────┐ │
│  │                  UI Layer                            │ │
│  │  ┌───────────────────────────────────────────────┐  │ │
│  │  │  Keyboard View (Custom View)                  │  │ │
│  │  │  • T9 佈局 (大按鍵、低誤觸)                   │  │ │
│  │  │  • QWERTY 佈局 (可切換)                       │  │ │
│  │  │  • 注音鍵盤佈局 (備用)                        │  │ │
│  │  │  • 候選字列 (Candidate Bar)                   │  │ │
│  │  │  • 主題引擎 (Material You / 自訂)            │  │ │
│  │  │  • 動畫 (按鍵反應、候選字滑動)               │  │ │
│  │  └───────────────────────────────────────────────┘  │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                          │
│  ┌─────────────────────────────────────────────────────┐ │
│  │               Input Engine Core                     │ │
│  │                                                     │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌────────────┐ │ │
│  │  │ Code Parser │  │ Lookup      │  │ Candidate  │ │ │
│  │  │ (解析輸入碼)│→ │ Engine      │→ │ Selector   │ │ │
│  │  │             │  │ (查表)      │  │ (候選字)   │ │ │
│  │  └─────────────┘  └─────────────┘  └────────────┘ │ │
│  │         ↑                    ↑            ↑         │ │
│  │  ┌─────────────┐    ┌─────────────┐  ┌──────────┐ │ │
│  │  │ Mode        │    │ T9 Predict  │  │ Zhuyin   │ │ │
│  │  │ Manager     │    │ Engine      │  │ Engine   │ │ │
│  │  │ (標準/簡速) │    │ (T9 預測)   │  │ (注音)   │ │ │
│  │  └─────────────┘    └─────────────┘  └──────────┘ │ │
│  │                          ↓              ↓          │ │
│  │                 ┌─────────────┐  ┌──────────────┐ │ │
│  │                 │ Association │  │ Learning     │ │ │
│  │                 │ Engine      │  │ Assistant    │ │ │
│  │                 │ (聯想詞)    │  │ (學習提示)  │ │ │
│  │                 └─────────────┘  └──────────────┘ │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                          │
│  ┌─────────────────────────────────────────────────────┐ │
│  │              Dictionary Manager                     │ │
│  │                                                     │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌────────────┐ │ │
│  │  │ GitHub      │  │ Local       │  │ Dictionary │ │ │
│  │  │ Sync        │→ │ Cache       │→ │ Loader     │ │ │
│  │  │ (自動更新)  │  │ (SQLite)    │  │ (記憶體)   │ │ │
│  │  └─────────────┘  └─────────────┘  └────────────┘ │ │
│  │                          ↓                        │ │
│  │                 ┌─────────────┐                   │ │
│  │                 │ T9 Index    │                   │ │
│  │                 │ Builder     │                   │ │
│  │                 │ (T9 索引)   │                   │ │
│  │                 └─────────────┘                   │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                          │
│  ┌─────────────────────────────────────────────────────┐ │
│  │              Settings & Prefs                       │ │
│  │  • 字碼表管理  • 按鍵佈局  • 主題  • 聯想詞開關   │ │
│  │  • T9/QWERTY/注音切換  • 按鍵音效  • 觸覺回饋     │ │
│  └─────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
         ↓                    ↓
    ┌─────────┐         ┌──────────┐
    │ 目標 App │         │ GitHub   │
    │ (Input   │         │ Repository│
    │ Connection)│       │ (字碼表) │
    └─────────┘         └──────────┘
```

---

## 模組一：字碼表管理 (Dictionary Manager)

### 字碼表格式設計

#### 主字碼表 (Primary Table)
```json
{
  "version": "1.0.0",
  "encoding": "boshiamy-standard",
  "entries": [
    {
      "code": "al",        // 嘸蝦米編碼
      "t9": "25",          // T9 對應序列
      "char": "中",        // 對應中文字
      "frequency": 10000,  // 使用頻率
      "phrases": ["中國", "中文", "中華"]  // 可選：關聯詞
    },
    {
      "code": "alk",
      "t9": "255",
      "char": "衝",
      "frequency": 500
    }
  ]
}
```

#### 聯想詞表 (Association Table)
```json
{
  "version": "1.0.0",
  "associations": {
    "中": ["國", "文", "心", "華", "東", "西"],
    "國": ["家", "際", "防", "旗", "語"],
    "電": ["腦", "話", "影", "視", "子"]
  }
}
```

### GitHub 同步機制

```
┌─────────────────────────────────────────┐
│           Dictionary Manager            │
│                                         │
│  1. 啟動時檢查 GitHub Release           │
│     GET /repos/{user}/{repo}/releases/latest
│                                         │
│  2. 比較 version (local vs remote)      │
│                                         │
│  3. 若有更新 → 下載 delta 或 full table │
│                                         │
│  4. 寫入 SQLite 快取                    │
│                                         │
│  5. 載入記憶體供查詢                     │
└─────────────────────────────────────────┘
```

#### 同步策略

| 頻率 | 動作 |
|---|---|
| App 啟動時 | 靜默檢查版本，有更新則提示 |
| 每 24 小時 | 自動檢查（背景） |
| 手動 | 設定頁面「立即更新」按鈕 |
| 首次安裝 | 引導下載預設字碼表 |

#### 快取格式

使用 SQLite 存放字碼表，避免每次啟動都載入 JSON：

```sql
-- 主字碼表
CREATE TABLE dictionary (
    code TEXT PRIMARY KEY,
    t9_code TEXT,           -- T9 對應序列
    char TEXT NOT NULL,
    frequency INTEGER DEFAULT 0
);

-- T9 索引表（加速 T9 查詢）
CREATE TABLE t9_index (
    t9_code TEXT,           -- T9 序列 (例: "626")
    boshiamy_code TEXT,     -- 嘸蝦米碼 (例: "oao")
    char TEXT,              -- 中文字 (例: "哈")
    frequency INTEGER,
    PRIMARY KEY (t9_code, boshiamy_code)
);

-- 聯想詞
CREATE TABLE associations (
    base_char TEXT PRIMARY KEY,
    related_chars TEXT  -- JSON array
);

-- 版本追蹤
CREATE TABLE meta (
    key TEXT PRIMARY KEY,
    value TEXT
);
```

### GitHub Repo 結構建議

```
boshiamy-tables/
├── README.md
├── releases/
│   ├── standard/
│   │   ├── dictionary.json    (~2MB, ~60,000 entries)
│   │   ├── t9_index.json      (~1.5MB, T9 反向索引)
│   │   └── associations.json  (~500KB)
│   ├── simplified/
│   │   ├── dictionary.json
│   │   ├── t9_index.json
│   │   └── associations.json
│   └── eten/
│       ├── dictionary.json
│       ├── t9_index.json
│       └── associations.json
└── versions.json              -- 各版本號
```

---

## 模組二：Input Engine Core

### 嘸蝦米輸入邏輯

```
使用者按鍵序列        Engine 處理              輸出
─────────────       ──────────────          ──────
a l          →      查表 "al"        →      "中" (候選)
a l k        →      查表 "alk"       →      "衝" (候選)
a l + Space  →      選擇 "中"        →      確定 "中"
a l + 1      →      選第2候選字      →      確定 "衝"
```

### 查詢演算法

```kotlin
class LookupEngine(private val dictionary: Dictionary) {

    // 查詢完全匹配
    fun lookupExact(code: String): List<Candidate> {
        return dictionary.query(code)
            .sortedByDescending { it.frequency }
    }

    // 查詢前綴匹配（顯示所有可能）
    fun lookupPrefix(code: String): List<Candidate> {
        return dictionary.queryPrefix(code)
            .sortedByDescending { it.frequency }
            .take(MAX_CANDIDATES) // 最多顯示 9 個
    }

    // 聯想查詢
    fun lookupAssociations(char: String): List<String> {
        return dictionary.getAssociations(char)
    }

    companion object {
        const val MAX_CANDIDATES = 9
    }
}
```

### 模式管理器

```kotlin
enum class BoshiamyMode(
    val displayName: String,
    val tableFile: String
) {
    STANDARD("標準", "standard/dictionary.json"),
    SIMPLIFIED("簡速", "simplified/dictionary.json"),
    ETEN("倚天", "eten/dictionary.json")
}

class ModeManager {
    var currentMode: BoshiamyMode = BoshiamyMode.STANDARD

    fun switchMode(mode: BoshiamyMode) {
        currentMode = mode
        // 重新載入對應字碼表
    }
}
```

---

## 模組三：T9 預測輸入引擎（解決手大誤觸問題）

### 為什麼需要 T9

| 問題 | QWERTY 鍵盤 | T9 鍵盤 |
|---|---|---|
| 按鍵大小 | 小 (~5mm) | 大 (~15mm) |
| 按鍵數量 | 26+ | 12 |
| 手指移動距離 | 大 | 小 |
| 誤觸率 | 高 (尤其手大) | 極低 |
| 單手操作 | 困難 | 容易 |

**核心優勢：** 按鍵面積大 3 倍以上，誤觸率大幅降低。

### T9 與嘸蝦米的對應

#### T9 按鍵映射

```
[1]      [2ABC]    [3DEF]
[4GHI]   [5JKL]    [6MNO]
[7PQRS]  [8TUV]    [9WXYZ]
[*]      [0]       [#]
```

#### 輸入範例

| 字 | 嘸蝦米碼 | QWERTY 按法 | T9 按法 |
|---|---|---|---|
| 哈 | oao | o→a→o (3鍵) | 6→2→6 (3鍵) |
| 中 | al | a→l (2鍵) | 2→5 (2鍵) |
| 國 | alk | a→l→k (3鍵) | 2→5→5 (3鍵) |
| 電 | we | w→e (2鍵) | 9→3 (2鍵) |
| 腦 | k4 | k→4 (2鍵) | 5→4 (2鍵) |

### 核心演算法

```kotlin
class T9BoshiamyEngine(private val dictionary: Dictionary) {

    // T9 按鍵對應表
    private val t9Map = mapOf(
        '2' to "abc", '3' to "def", '4' to "ghi",
        '5' to "jkl", '6' to "mno", '7' to "pqrs",
        '8' to "tuv", '9' to "wxyz"
    )

    // 反向映射：字母 → T9 按鍵
    private val charToT9: Map<Char, Char> = buildMap {
        t9Map.forEach { (key, chars) ->
            chars.forEach { put(it, key) }
        }
    }

    // 將嘸蝦米碼轉換為 T9 序列
    fun codeToT9(code: String): String {
        return code.map { char ->
            charToT9[char] ?: char
        }.joinToString("")
    }

    // T9 預測查詢（核心）
    fun predict(t9Sequence: String): List<Candidate> {
        // 1. 找出所有符合的嘸蝦米碼
        val matches = dictionary.getAllCodes()
            .filter { codeToT9(it) == t9Sequence }

        // 2. 按使用頻率排序
        return matches
            .map { code ->
                Candidate(
                    code = code,
                    char = dictionary.lookup(code),
                    frequency = dictionary.getFrequency(code)
                )
            }
            .sortedByDescending { it.frequency }
            .take(MAX_CANDIDATES)
    }

    companion object {
        const val MAX_CANDIDATES = 9
    }
}
```

### 使用流程

```
使用者操作                    Engine 處理
─────────────               ──────────────
按 [6]        →              候選："m","n","o"
按 [2]        →              候選："ma","mb","mc"...,"oa","ob","oc"...
按 [6]        →              候選："oao"(哈),"nap","mbn"...

使用者看到候選字列:
┌─────────────────────────────────────────┐
│  哈  |  南  |  墨  |  ...              │
└─────────────────────────────────────────┘
              ↑ 按數字鍵選擇
```

### T9 鍵盤佈局

```
┌─────────────────────────────────────────────┐
│  [中/EN]  [T9/QWERTY]  [全/半]            │
├─────────────────────────────────────────────┤
│                                             │
│        [1]      [2ABC]    [3DEF]           │
│                                             │
│        [4GHI]   [5JKL]    [6MNO]          │
│                                             │
│        [7PQRS]  [8TUV]    [9WXYZ]         │
│                                             │
│        [*]      [0]       [#]             │
│                                             │
├─────────────────────────────────────────────┤
│  輸入碼: 626                               │
│  候選: 哈 | 南 | 墨 | ...                  │
└─────────────────────────────────────────────┘
```

### 資料結構：T9 索引

為了加速 T9 查詢，建立反向索引：

```sql
-- T9 索引表（字碼表載入時自動產生）
CREATE TABLE t9_index (
    t9_code TEXT,           -- T9 序列 (例: "626")
    boshiamy_code TEXT,     -- 嘸蝦米碼 (例: "oao")
    char TEXT,              -- 中文字 (例: "哈")
    frequency INTEGER,      -- 使用頻率
    PRIMARY KEY (t9_code, boshiamy_code)
);

-- 查詢範例：SELECT * FROM t9_index WHERE t9_code = '626' ORDER BY frequency DESC
```

### 優化：前綴樹 (Trie) 加速

```kotlin
class T9Trie {
    private data class Node(
        val children: MutableMap<Char, Node> = mutableMapOf(),
        val candidates: MutableList<Candidate> = mutableListOf()
    )

    private val root = Node()

    // 建立 Trie（字碼表載入時）
    fun build(dictionary: Dictionary) {
        dictionary.getAllEntries().forEach { entry ->
            val t9Code = codeToT9(entry.code)
            var current = root
            t9Code.forEach { digit ->
                current = current.children.getOrPut(digit) { Node() }
            }
            current.candidates.add(entry)
        }
    }

    // 即時查詢（O(m) m = 按鍵數）
    fun search(t9Sequence: String): List<Candidate> {
        var current = root
        t9Sequence.forEach { digit ->
            current = current.children[digit] ?: return emptyList()
        }
        return current.candidates
            .sortedByDescending { it.frequency }
            .take(MAX_CANDIDATES)
    }
}
```

### T9 vs QWERTY 決策

| 場景 | 建議模式 |
|---|---|
| 日常快速輸入 | T9（大鍵、低誤觸） |
| 精確輸入特定字 | QWERTY（直接按） |
| 單手操作 | T9 |
| 雙手操作 | QWERTY |
| 行走中 | T9 |

**建議：** App 預設 T9 模式，可隨時切換 QWERTY。

---

## 模組四：注音輸入（備用方案）

### 為什麼需要注音

| 場景 | 問題 |
|---|---|
| 忘記拆碼 | 用了 20 年還是有些字想不起來怎么拆 |
| 生僻字 | 嘸蝦米碼表可能不完整 |
| 學習過渡期 | 新手還不熟嘸蝦米時可用注音 |
| 混合輸入 | 同一段文字中交替使用兩種輸入法 |

### 注音 + 嘸蝦米 混合模式

```
┌─────────────────────────────────────────────┐
│  [中/EN]  [注音/嘸蝦米]  [全/半]           │
├─────────────────────────────────────────────┤
│                                             │
│  ㄅ  ㄆ  ㄇ  ㄈ  ㄉ  ㄊ  ㄋ  ㄌ             │
│  ㄍ  ㄎ  ㄏ  ㄐ  ㄑ  ㄒ  ㄓ  ㄔ             │
│  ㄕ  ㄖ  ㄗ  ㄘ  ㄙ  ㄚ  ㄛ  ㄜ             │
│  ㄝ  ㄞ  ㄟ  ㄠ  ㄡ  ㄢ  ㄣ  ㄤ             │
│  ㄦ  ㄧ  ㄨ  ㄩ  ˊ  ˇ  ˋ  ˙              │
│                                             │
├─────────────────────────────────────────────┤
│  輸入: ㄓㄨㄥ                                │
│  候選: 中 | 終 | 鐘 | 鍾 | ...              │
└─────────────────────────────────────────────┘
```

### 注音引擎實作

```kotlin
class ZhuyinEngine(private val zhuyinTable: ZhuyinTable) {

    // 注音序列 → 候選字
    fun lookup(zhuyinCode: String): List<Candidate> {
        return zhuyinTable.query(zhuyinCode)
            .sortedByDescending { it.frequency }
            .take(MAX_CANDIDATES)
    }

    // 注音 → 嘸蝦米碼查詢（學習用途）
    fun getBoshiamyCode(zhuyinCode: String, char: String): String? {
        return boshiamyTable.lookupByChar(char)?.code
    }

    companion object {
        const val MAX_CANDIDATES = 9
    }
}
```

### 智慧學習功能

當使用者用注音輸入某個字時，App 可以提示對應的嘸蝦米碼：

```
使用者用注音輸入 "衝"（ㄔㄨㄥ）
         ↓
App 提示：「衝」的嘸蝦米碼是 "alk"
         ↓
下次就可以用嘸蝦米直接輸入
```

```kotlin
class LearningAssistant(
    private val boshiamyTable: BoshiamyTable,
    private val userHistory: UserHistory
) {
    // 注音輸入後，提示嘸蝦米碼
    fun suggestBoshiamy(char: String): String? {
        val code = boshiamyTable.lookupByChar(char) ?: return null

        // 如果使用者常常用注音打這個字，就提示嘸蝦米碼
        val zhuyinCount = userHistory.getZhuyinCount(char)
        val boshiamyCount = userHistory.getBoshiamyCount(char)

        return if (zhuyinCount > 3 && boshiamyCount == 0) {
            code  // 提示：「下次可以試試嘸蝦米碼: $code」
        } else {
            null
        }
    }
}
```

### 模式切換流程

```
┌─────────────────────────────────────────────────┐
│                                                 │
│   預設模式: 嘸蝦米 (T9)                        │
│         ↓                                       │
│   遇到打不出的字？                              │
│         ↓                                       │
│   按 [注音/嘸蝦米] 切換                        │
│         ↓                                       │
│   用注音輸入該字                                │
│         ↓                                       │
│   App 自動記錄：這個字你常常用注音              │
│         ↓                                       │
│   下次輸入時，提示嘸蝦米碼                      │
│         ↓                                       │
│   學習完成後，自動切回嘸蝦米模式                │
│                                                 │
└─────────────────────────────────────────────────┘
```

### 記憶體占用估算

| 資料 | 大小 | 說明 |
|---|---|---|
| 注音碼表 | ~1MB | ~20,000 字 × 平均 5 候選 |
| 嘸蝦米碼表 | ~2MB | ~60,000 筆 |
| T9 索引 | ~1.5MB | 反向索引 |
| 聯想詞表 | ~0.5MB | ~5,000 字 |
| **總計** | **~5MB** | 可接受 |

---

## 模組五：Keyboard View (Gboard 風格)

### 鍵盤佈局

#### T9 嘸蝦米模式（預設）

```
┌─────────────────────────────────────────────┐
│  [中/EN]  [T9/QWERTY/注音]  [全/半]        │
├─────────────────────────────────────────────┤
│                                             │
│        [1]      [2ABC]    [3DEF]           │
│                                             │
│        [4GHI]   [5JKL]    [6MNO]          │
│                                             │
│        [7PQRS]  [8TUV]    [9WXYZ]         │
│                                             │
│        [*]      [0]       [#]             │
│                                             │
├─────────────────────────────────────────────┤
│  輸入碼: 626                               │
│  候選: 哈 | 南 | 墨 | ...                  │
└─────────────────────────────────────────────┘
```

#### 注音模式（備用）

```
┌─────────────────────────────────────────────┐
│  [中/EN]  [T9/QWERTY/注音]  [全/半]        │
├─────────────────────────────────────────────┤
│                                             │
│  ㄅ  ㄆ  ㄇ  ㄈ  ㄉ  ㄊ  ㄋ  ㄌ             │
│  ㄍ  ㄎ  ㄏ  ㄐ  ㄑ  ㄒ  ㄓ  ㄔ             │
│  ㄕ  ㄖ  ㄗ  ㄘ  ㄙ  ㄚ  ㄛ  ㄜ             │
│  ㄝ  ㄞ  ㄟ  ㄠ  ㄡ  ㄢ  ㄣ  ㄤ             │
│  ㄦ  ㄧ  ㄨ  ㄩ  ˊ  ˇ  ˋ  ˙              │
│                                             │
├─────────────────────────────────────────────┤
│  輸入: ㄓㄨㄥ                                │
│  候選: 中 | 終 | 鐘 | 鍾 | ...              │
│  💡 提示：「中」的嘸蝦米碼是 "al"          │
└─────────────────────────────────────────────┘
```

#### QWERTY 模式

```
┌─────────────────────────────────────────────┐
│  [中/EN]  [T9/QWERTY/注音]  [全/半]        │
├─────────────────────────────────────────────┤
│                                             │
│   Q   W   E   R   T   Y   U   I   O   P    │
│                                             │
│    A   S   D   F   G   H   J   K   L        │
│                                             │
│  [⇧]   Z   X   C   V   B   N   M   [⌫]   │
│                                             │
│  [123] [🌐] [     空白鍵     ] [⏎] [📝]    │
│                                             │
├─────────────────────────────────────────────┤
│  候選字列: 中 | 衷 | 鐘 | 衝 | ...          │
└─────────────────────────────────────────────┘
```

### 功能鍵說明

| 按鍵 | 功能 |
|---|---|
| [中/EN] | 切換中英文 |
| [T9/QWERTY/注音] | 切換輸入模式 |
| [全形/半形] | 切換全半形 |
| [⇧] | Shift (大寫/符號) |
| [⌫] | 退字 |
| [123] | 數字/符號鍵盤 |
| [🌐] | 切換輸入法 |
| [⏎] | 送出/換行 |
| [📝] | 模式選單（嘸蝦米/注音/設定） |

### UI 實作技術

```kotlin
class BoshiamyKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // 按鍵繪製
    override fun onDraw(canvas: Canvas) {
        // Material You 動態主題色
        // 按鍵陰影、圓角
        // 按下動畫 (scale + ripple)
    }

    // 觸控處理
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 偵測按鍵位置
        // 觸發 haptic feedback
        // 呼叫 InputEngine
    }

    // 按鍵佈局資料
    private val keyLayout = listOf(
        listOf("q","w","e","r","t","y","u","i","o","p"),
        listOf("a","s","d","f","g","h","j","k","l"),
        listOf("⇧","z","x","c","v","b","n","m","⌫"),
        listOf("123","🌐","space","⏎","📝")
    )
}
```

---

## 模組六：Android IME 整合

### InputMethodService 實作

```kotlin
class BoshiamyInputMethodService : InputMethodService() {

    private lateinit var keyboardView: BoshiamyKeyboardView
    private lateinit var engine: LookupEngine
    private lateinit var dictionaryManager: DictionaryManager

    override fun onCreateInputView(): View {
        // 載入自訂鍵盤 view
        keyboardView = BoshiamyKeyboardView(this)
        keyboardView.onKeyPress = { key -> handleKeyPress(key) }
        return keyboardView
    }

    private fun handleKeyPress(key: String) {
        val inputConnection = currentInputConnection ?: return

        when (key) {
            "⌫" -> inputConnection.deleteSurroundingText(1, 0)
            "⏎" -> inputConnection.performEditorAction(EditorInfo.IME_ACTION_SEND)
            "space" -> commitCandidate()
            else -> {
                // 累積輸入碼 → 查詢 → 顯示候選字
                inputBuffer.append(key)
                val candidates = engine.lookupPrefix(inputBuffer.toString())
                keyboardView.showCandidates(candidates)
            }
        }
    }

    private fun commitCandidate() {
        val candidate = keyboardView.selectedCandidate ?: return
        val inputConnection = currentInputConnection ?: return

        inputConnection.commitText(candidate.char, 1)
        inputBuffer.clear()

        // 顯示聯想詞
        val associations = engine.lookupAssociations(candidate.char)
        keyboardView.showCandidates(associations.map { Candidate(it, 0) })
    }
}
```

### AndroidManifest.xml

```xml
<service
    android:name=".BoshiamyInputMethodService"
    android:label="嘸蝦米輸入法"
    android:permission="android.permission.BIND_INPUT_METHOD">
    <intent-filter>
        <action android:name="android.view.InputMethod" />
    </intent-filter>
    <meta-data
        android:name="android.view.im"
        android:resource="@xml/method" />
</service>
```

---

## 設計原則

### 為什麼不依賴 Native Code

| 比較 | Native (C/C++) | Kotlin/Java |
|---|---|---|
| 系統更新相容性 | ❌ 常因 SELinux/OAT 改動失效 | ✅ 完全相容 |
| 開發速度 | 慢 | 快 |
| 效能 | 快 | 夠用（查表不需要 native） |
| 除錯 | 困難 | 容易 |
| APK 大小 | 需打包 .so | 純 Java bytecode |

### 查詢效能優化

```
1. Trie Tree (前綴樹) — 快速前綴查詢
2. LRU Cache — 熱門字碼快取
3. Frequency Sort — 高頻字排前面
4. Background Loading — 字碼表背景載入，不阻塞 UI
```

### 記憶體管理

```
目標：字碼表 ~60,000 筆，記憶體占用 < 20MB

優化策略：
• SQLite 作為主要儲存，不全載入記憶體
• 只載入高頻字 (frequency > 100) 到記憶體
• 低頻字走 SQLite 查詢 (延遲 ~1ms，可接受)
• 聯想詞表約 5,000 筆，全載入 (~2MB)
```

---

## 開發路線圖

### Phase 1：基礎功能 (MVP)
- [x] InputMethodService 基本框架
- [x] T9 鍵盤佈局（大按鍵、低誤觸）
- [x] QWERTY 鍵盤佈局（可切換）
- [x] 嘸蝦米標準模式查表
- [x] T9 預測查詢引擎
- [x] 候選字列 UI
- [x] 本地 JSON 字碼表載入

### Phase 2：字碼表管理
- [ ] GitHub API 整合
- [ ] 自動更新機制
- [ ] SQLite 快取
- [ ] 多字碼表支援（標準/簡速/倚天）

### Phase 3：進階功能
- [ ] 注音輸入引擎
- [ ] 聯想詞引擎
- [ ] 使用頻率學習
- [ ] 注音→嘸蝦米碼提示功能
- [ ] 自訂按鍵佈局
- [ ] 主題引擎 (Material You)

### Phase 4：優化
- [ ] 效能優化 (Trie Tree for T9)
- [ ] T9 索引自動產生
- [ ] 省電模式
- [ ] 無障礙支援
- [ ] 多語言切換優化

---

## 技術選型

| 項目 | 選擇 | 原因 |
|---|---|---|
| 開發語言 | Kotlin | Android 官方推薦 |
| 最低 API | 24 (Android 7) | 覆蓋 95%+ 裝置 |
| UI 框架 | Custom View (Canvas) | 鍵盤需要高效繪製 |
| 資料儲存 | SQLite + JSON | 結構化查詢 + 方便更新 |
| 網路 | Retrofit + OkHttp | GitHub API 呼叫 |
| 主題 | Material You (M3) | 跟隨系統動態色彩 |
| DI | Hilt | Android 官方推薦 |
| 測試 | JUnit + Espresso | 單元測試 + UI 測試 |

---

## 與現有方案的差異

| 特性 | Rime | fcitx5 | 本方案 |
|---|---|---|---|
| Native 依賴 | ✅ 有 | ✅ 有 | ❌ 無 |
| 系統更新後穩定性 | ❌ 差 | ❌ 差 | ✅ 好 |
| 字碼表更新 | 手動 | 手動 | 自動 (GitHub) |
| UI 現代化 | ❌ 古板 | ⚠️ 普通 | ✅ Gboard 風格 |
| 聯想詞 | ⚠️ 有但弱 | ⚠️ 有但弱 | ✅ 完整 |
| T9 輸入 | ❌ 無 | ❌ 無 | ✅ 有 |
| 大按鍵模式 | ❌ 無 | ❌ 無 | ✅ 有 |
| 注音備用 | ✅ 有 | ✅ 有 | ✅ 有 |
| 學習功能 | ❌ 無 | ❌ 無 | ✅ 注音→嘸蝦米提示 |
| 開源 | ✅ | ✅ | ✅ |

---

## 開源授權與碼表著作權

### 法律分析

| 項目 | 說明 | 風險等級 |
|---|---|---|
| 嘸蝦米輸入法軟體 |  proprietary，不可複製 | ❌ 高 |
| 字碼表（官方版） | 可能受著作權保護 | ⚠️ 中 |
| 字碼表（自製/社區版） | 事實性資料，較無問題 | ✅ 低 |
| 注音碼表 | 公共領域 | ✅ 無 |
| T9 對應表 | 自行產生 | ✅ 無 |

### 嘸蝦米碼表的合法性

**關鍵問題：** 「字 → 嘸蝦米碼」的對應表是否受著作權保護？

**分析：**

1. **事實性資料原則**
   - 字碼對應是「事實」（類似字典、電話簿）
   - 在多數法域，純事實資料不受著作權保護
   - 但「資料的選擇與編排」可能受保護

2. **現有先例**
   - gcin（開放原始碼輸入法）包含嘸蝦米支援
   - fcitx 也有嘸蝦米碼表
   - 這些都是社區維護的，非官方授權

3. **安全做法**
   - ✅ 使用社區維護的碼表（如 gcin/fcitx 的碼表）
   - ✅ 自己建立碼表（參考公開資料）
   - ❌ 不要直接複製官方嘸蝦米軟體中的碼表
   - ❌ 不要宣稱是「官方」或「授權」版本

### 建議的授權策略

```
專案結構：
├── LICENSE                    # MIT 或 GPL v3
├── src/                       # App 原始碼
├── tables/                    # 字碼表
│   ├── README.md              # 說明碼表來源
│   ├── boshiamy/              # 嘸蝦米碼表
│   │   ├── SOURCE.md          # 碼表來源說明
│   │   └── dictionary.json
│   ├── zhuyin/                # 注音碼表（公共領域）
│   │   └── dictionary.json
│   └── associations/          # 聯想詞表
└── docs/
```

### 碼表來源文件範例

```markdown
# 碼表來源說明

## 嘸蝦米碼表
- 來源：gcin 開放原始碼輸入法
- 授權：GPL v3
- 修改：僅調整 JSON 格式，內容未變更
- 日期：2026-08-27

## 注音碼表
- 來源：教育部注音符號表
- 授權：公共領域
- 說明：標準注音對應表

## 聯想詞表
- 來源：自建
- 授權：CC BY-SA 4.0
- 說明：基於常用詞頻統計建立
```

### 開源授權選擇

| 授權 | 適合情境 | 說明 |
|---|---|---|
| **MIT** | 最寬鬆 | 任何人可用，只需保留授權聲明 |
| **GPL v3** | 要求衍生作品也開源 | 適合希望保持開源的專案 |
| **Apache 2.0** | 企業友好 | 明確的專利授權 |
| **CC BY-SA 4.0** | 碼表資料 | 適用於碼表等資料集 |

**建議：**
- App 原始碼：**GPL v3**（與 gcin/fcitx 一致）
- 碼表資料：**CC BY-SA 4.0**（允許分享，但需註明來源）

### 避免的行為

| ❌ 不要做 | 為什麼 |
|---|---|
| 宣稱是「官方嘸蝦米」 | 商標侵權 |
| 複製官方軟體中的碼表 | 可能著作權侵權 |
| 使用「嘸蝦米」作為 App 名稱 | 商標問題 |
| 在 App 商店付費販售 | 可能違反授權 |

### 安全的 App 命名建議

| ✅ 安全 | ❌ 危險 |
|---|---|
| 「Boshiamy IME」 | 「嘸蝦米輸入法」 |
| 「Boshiamy-compatible IME」 | 「官方嘸蝦米」 |
| 「BSM Keyboard」 | 「嘸蝦米 T9」 |
| 「Free Boshiamy」 | 「嘸蝦米免費版」 |

---

## 參考資源

- [Android InputMethodService](https://developer.android.com/reference/android/inputmethodservice.InputMethodService)
- [Custom Keyboard Guide](https://developer.android.com/guide/topics/ui/layout/keyboard)
- [GitHub REST API - Releases](https://docs.github.com/en/rest/releases/releases)
- [Material Design 3 - Keyboard](https://m3.material.io/components/keyboard)
- [嘸蝦米官方](https://boshiamy.com/)
- [T9 Predictive Text Algorithm](https://en.wikipedia.org/wiki/T9_(predictive_text))
- [Trie Data Structure](https://en.wikipedia.org/wiki/Trie)

---

> ✅ ocw完成 2026-08-27
