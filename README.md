# 璦閣輸入法（BoshiamyIME）

> 純 Kotlin、完全離線的 Android 嘸蝦米輸入法。
> 你的輸入是你的，**字根不上傳、足跡不留下**——GPL-3.0 開放原始碼，給有緣人自由使用、研究與改進。

## 截圖

> 待補：三張鍵盤模式（標準嘸蝦米／注音／T9）＋ 設定頁。
> 歡迎發 PR 補上實機截圖（放到 `docs/screenshots/`）。

---

## 功能特色

| 項目 | 說明 |
|---|---|
| 🎹 鍵盤模式 | 標準嘸蝦米 / 注音（依字頻排序）/ T9 預測 / QWERTY 英文 |
| 🔣 額外面板 | 符號面板、表情符號（Emoji）面板 |
| 🧠 聯想詞 | 內建 1 萬+ 鍵、11 萬+ 詞條聯想，**並於輸入時自動學習**新詞 |
| 🕶️ 主題 | 跟隨系統深色／自訂色彩（背景、文字、按鍵、框線，`#RRGGBB` 色碼） |
| 🎨 鍵盤大小 | 0.7×–1.4× 縮放，大螢幕小螢幕都好按 |
| 🔂 輸出控制 | 數字／符號可切換全形輸出；空白鍵即時顯示注音提示 |
| 📦 資料管理 | 清除自學記錄、設定**匯出／匯入**（JSON） |
| 🔌 回饋 | 按鍵震動／音效可獨立開關（抖音愛好者有福了） |

---

## 隱私（輸入法該有的基本尊重）

**輸入法看得到你打的一切，這是權力也是責任。**

- 🚫 **完全離線運作**：拆字、組字、聯想、學習全部在本機，鍵盤輸入**絕不上傳**
- 🚫 **無廣告、無追蹤、無資料收集 SDK**（不掛 Firebase / 分析套件）
- 🌐 唯一的網路權限是「手動更新碼表」——按了「更新碼表」才會連 GitHub 下載，除此之外不會主動連網
- 🔏 開源即信任：**開源軟體沒有廚房門**，任何人都可以走進來看 code 到底做了什麼

---

## 鍵盤模式

1. **標準嘸蝦米（QWERTY 中文）**
   - 輸入嘸蝦米碼時於游標處即時組字
   - `Enter` 將目前組合提交為**英文**（保留大小寫與混合字）
   - `Space` 若為合法字碼 → 出字；否則顯示錯誤提示
   - 嘸蝦米碼維持小寫（含 `, . ' [ ]`）

2. **注音**
   - 依實際字頻排序候選，常用字優先
   - 簡化字自動降權，避免「正政證…」被簡體字「证」插隊

3. **T9 預測**
   - 五列大按鍵佈局（`1 2 3 / 4 5 6 / 7 8 9 / , 0 .`）
   - 子標籤放大，減少誤觸（手大友善）
   - `Space` = 送出暫存後輸出空白；`Enter` = 換行／送出數字

4. **QWERTY 英文**
   - 直接輸出字母，保留大小寫（含 Shift 一次性切換）

> 模式切換：空白鍵列左側「蝦／注／T9」一鍵循環；`#+=`、`😊` 開符號與表情面板。

---

## 開發環境與建置

| 項目 | 版本 |
|---|---|
| Min SDK | 24（Android 7.0） |
| Target / Compile SDK | 35 |
| Kotlin | 2.1.0 |
| AGP | 8.7.3 |
| Gradle | 9.7.1 |
| JDK | 17（**不要用 JDK 25，AGP lint 會崩**） |
| 依賴 | AndroidX Core / AppCompat / Material / ConstraintLayout / coroutines |

**Debug APK：**

```bash
export JAVA_HOME=/path/to/jdk-17
export PATH=$JAVA_HOME/bin:$PATH
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew :app:assembleDebug
```

產出：`app/build/outputs/apk/debug/app-debug.apk`

**Release 簽署版：**

```bash
./gradlew :app:assembleRelease
# 產生正式簽名（只需做一次，keystore 請高規格備份）
keytool -genkeypair -keystore boshiamy-release.keystore -alias boshiamy \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=IgG Digital Technology, O=IgG, C=TW"
# 簽署
apksigner sign --ks boshiamy-release.keystore --ks-key-alias boshiamy \
        --out app-release-signed.apk app/build/outputs/apk/release/app-release.apk
```

已簽署的正式包目前在 `dist/boshiamy-1.0.0-release.apk`（v1.0.0 / versionCode 1）。

---

## 安裝與啟用

1. 下載 release APK（或自行建置 debug 版）安裝
2. 開啟系統 **設定 → 語言與輸入 → 鍵盤**，啟用「璦閣輸入法」
3. 於任何輸入框切換至「璦閣輸入法」即可使用

> ⚠️ 若之前裝的是 debug 版，升級到正式簽署版前請**先卸載**（簽章不同無法覆蓋，卸載會清掉自學資料）。

---

## 專案結構

```
app/src/main/
├── assets/tables/
│   ├── standard/dictionary.json     # 標準嘸蝦米字碼表
│   ├── standard/associations.json   # 聯想詞（1 萬+ 鍵）
│   └── zhuyin/dictionary.json       # 注音字典（含字頻）
├── java/tw/igg/boshiamyime/
│   ├── service/ BoshiamyInputMethodService.kt   # 輸入法服務核心
│   ├── engine/  InputEngineManager / Lookup / Zhuyin / T9
│   ├── data/    DictionaryManager / DictionaryDownloader
│   ├── theme/   ThemePalette                    # 主題解析（深色／自訂）
│   ├── ui/      KeyboardView / CandidateBarView / KeyboardPreviewView
│   ├── model/   Candidate / DictionaryEntry / Enums
│   └── SettingsActivity.kt          # 設定頁（主題／鍵盤／資料管理）
└── res/         layouts / drawables / xml(method)
```

---

## 資料來源與授權歸屬（重要）

本專案使用以下**開放資料**，兩者皆保留原始授權義務，使用或再散布時請一併遵守：

| 資料 | 來源 | 授權 | 用途 |
|---|---|---|---|
| 常用詞與字頻統計 | [samejack/sc-dictionary](https://github.com/samejack/sc-dictionary)（約 110 萬詞，CC BY 3.0） | **CC BY 3.0** | 產生聯想詞、計算注音字頻 |
| 簡化字對照 | [OpenCC STCharacters.txt](https://github.com/BYVoid/OpenCC) | **Apache-2.0** | 注音候選排序中將簡化字降權 |

- **聯想詞**（`associations.json`，1 萬+ 鍵、11 萬+ 詞條）由上述語料之 bigram 統計，加上人工整理條目產生。
- **注音字頻**（`zhuyin/dictionary.json`，8.3 萬+ 條）依真實字頻排序候選。
- 生成與統計方法說明可於 `docs/ORIGINAL-TECH-PLAN.md` 中找到。

---

## 授權

本專案程式碼採 **GPL-3.0** 授權發布（見 `LICENSE`）。內含之字碼表／字典資料另作歸屬聲明，詳見上方「資料來源與授權歸屬」與 `NOTICE`。

- 修改後再散布時，須以相同授權開源，並保留原始出處標示。
- 欲商用或另作授權，請先與作者聯繫。

---

## 現況與待辦

- [x] 標準嘸蝦米字碼表（內建）
- [x] 注音字典（字頻排序、簡化字降權）
- [x] T9 五列大按鍵佈局
- [x] 聯想詞（靜態 + 動態學習）
- [x] 正式 release 簽署（`dist/boshiamy-1.0.0-release.apk`）
- [x] 設定頁（主題／鍵盤大小／全形／震動音效／資料管理）
- [ ] 上架 Google Play ／其他商店
- [ ] 補充實機截圖進 README

> 關於「簡速／倚天」字碼表：早期規劃曾包含，產品決策後已從輸入模式中移除；如社群有需求可再評估。

---

## 貢獻與聯絡

- GitHub：<https://github.com/Solo-man-IGG/>
- 官方網站：<https://www.igg.tw/>

歡迎回報問題、提出建議或發送 PR。中文輸入法不只是輸入工具，它承載了我們每天最常說的話——讓它更好，人人有責。