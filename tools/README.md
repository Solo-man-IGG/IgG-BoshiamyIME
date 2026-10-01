# tools/

碼表與字頻資料的產生腳本。`app/src/main/assets/tables/standard/dictionary.json`
不要手改，一律用這支腳本重產。

## build-dictionary.py

把萬國蝦米（`uniliu.cin`）轉成 App 用的 `dictionary.json`，並在轉換過程中塞入字頻。

```bash
# 一般用法（用 tools/.cache/uniliu.cin 產生字典）
python3 tools/build-dictionary.py

# 第一次使用或想更新來源
python3 tools/build-dictionary.py --refresh-cin        # 下載 uniliu.cin
python3 tools/build-dictionary.py --refresh-charcount  # 重建教育部字頻表（需 poppler-utils）
python3 tools/build-dictionary.py --refresh-simplified # 重建簡體字集
```

輸出：

| 檔案 | 內容 |
|---|---|
| `app/src/main/assets/tables/standard/dictionary.json` | 97,030 筆，含 `code` / `t9` / `char` / `frequency` |
| `tools/data/moe-char-count.tsv` | 教育部字頻表表一，5,702 字（字 + 原始出現頻次） |
| `tools/data/simplified-only.txt` | 只存在於簡體、沒有對應正體用法的字，3,809 字 |

`tools/.cache/` 已在 `.gitignore` 內，`tools/data/` 則要進版控，讓沒有網路也能重產。

## 資料來源

| 用途 | 來源 | 授權 |
|---|---|---|
| 碼表 | [cin-tables](https://github.com/chinese-opendesktop/cin-tables) `uniliu.cin` | CC0-1.0 |
| 字頻 | [教育部語文司 常用字字頻表 表一](https://language.moe.gov.tw/001/Upload/files/SITE_CONTENT/M0001/BIAU1.zip) | 政府資料開放 |
| 簡體降權 | [OpenCC](https://github.com/BYVoid/OpenCC) `STCharacters.txt` | Apache-2.0 |

## 字頻怎麼算

`uniliu.cin` 本身沒有字頻，而且**檔案內的字序不是常見度**（`a` 是「對、对」、
`yi` 是「款、丫」），所以字序不能拿來當排序依據。改用教育部字頻總表：

```
frequency = 1 + round(998 * log(原始頻次) / log(最高頻次))     # 在字頻表內，1..999
frequency = 0                                                  # 罕見字／異體字，不在字頻表
frequency = -1_000_000 + |frequency|                           # 簡體專用字
```

為什麼壓到 999 上限：`DictionaryManager.getEffectiveFrequency()` 是
`static + userFreq * 1000`。若靜態字頻照用原始次數（最高 32,739），
使用者要打 33 次才追得過「的」，等於學習功能失效。壓到 999 之後，
**打過一次就一定排前面**，這是回報「學不會」的直接修正。

## 簡體字降權的判定

OpenCC 有兩張表，單看任何一張都會誤判：

- `TSCharacters.txt`（繁→簡）：只看右欄會把「丫」算成簡體，但「丫」本身是合法正體。
- 只取「右欄有、左欄無」會把「谷／只／后／干／台／里／面／板」全誤判——
  這些字自己就是正體，只是恰好也是別個正體字的簡化字（穀→谷、后→後）。

正確規則（`refresh_simplified()`）：

> X 必須是 `STCharacters.txt`（簡→繁）的**鍵**，且 `s2t(X)` 的候選**不含 X 本身**。

## PDF 解析的兩個坑

`--refresh-charcount` 走 `BIAU1.zip` 裡的 PDF（PDF 有完整 5,731 字，
同包附的 `BIAU1.TXT` 被截斷在第 3,275 名），過程中踩到兩個問題：

1. **CJK 相容區**：PDF 內部分字落在 U+F900–U+FAFF（例如「不」是 U+F967
   而不是 U+4E0D），共 276 個。必須 `unicodedata.normalize('NFKC', ch)`，
   否則常見字會整批對不到字頻。
2. **私用區缺字**：29 個字是字型缺字造出的 PUA 字形，要捨棄。
   捨棄要放在「字頻序號連續性驗證之後」，否則序號會出現缺口而誤判解析失敗。

`║` 與 `│` 都是表格分隔線，兩者都要當分隔符；用 `len(cells) == 7` 判斷資料列。

## 解析規則與 App 一致

碼表過濾與 `DictionaryDownloader.parseCinFile()` 保持一致，避免內建字典和
下載的字典行為不一致：

- code 必須符合 `^[a-z,.'\[\]]+$`（轉小寫後比對）
- 依 `code|char` 去重
- `t9` 為 code 逐字過電話鍵盤（`DictionaryDownloader` 內的 `t9Map`）
- 依 code 排序；Python 排序是穩定的，同碼的字保留 `.cin` 原序作為最終排序依據
