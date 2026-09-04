# 璦閣輸入法（BoshiamyIME）

純 Kotlin 開發的 Android 嘸蝦米（Boshiamy）輸入法，含 **標準嘸蝦米、注音、T9 預測、QWERTY 英文** 四種鍵盤模式，外加符號與表情符號面板。**無償開放原始碼**，給有緣人自由使用、研究與改進。

> 舊的技術規劃文件已移置 `docs/ORIGINAL-TECH-PLAN.md`。

---

## 功能特色

| 項目 | 說明 |
|---|---|
| 🎹 鍵盤模式 | 標準嘸蝦米 / 注音（依字頻排序）/ T9 預測 / QWERTY |
| 🔣 額外面板 | 符號面板、表情符號（Emoji）面板 |
| 🔄 中英切換 | 一鍵切換中文／英文輸入（`中` / `EN`） |
| ↔️ 全半形 | 支援全形／半形符號切換 |
| 🧠 聯想詞 | 內建 1 萬+ 詞組聯想，**並可於輸入時自動學習**新詞（依字頻與個人習慣） |
| 📚 三種嘸蝦米字碼表 | 標準／簡速／倚天（目前隨附為「標準」表，另二表待補） |
| 🔤 大小寫 | 英文輸入時保留大小寫（含 Shift 一次性切換） |

---

## 鍵盤模式

1. **標準嘸蝦米（QWERTY）中文**
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
   - 直接輸出字母與大小寫

---

## 開發環境與建置

| 項目 | 版本 |
|---|---|
| Min SDK | 24（Android 7.0） |
| Target / Compile SDK | 35 |
| Kotlin | 2.1.0 |
| AGP | 8.7.3 |
| Gradle | 9.3.0 |
| JDK | 17 |
| 依賴 | AndroidX Core / AppCompat / Material / ConstraintLayout / coroutines |

**指令列建置**（Debug APK）：

```bash
export JAVA_HOME=/path/to/android-studio/jbr
export PATH=$JAVA_HOME/bin:$PATH
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew :app:assembleDebug
```

產出物位於 `app/build/outputs/apk/debug/app-debug.apk`。

---

## 安裝與啟用

1. 建置後（或下載 release APK），安裝 `app-debug.apk`
2. 開啟系統 **設定 → 語言與輸入 → 鍵盤**，啟用「璦閣輸入法」
3. 於任何輸入框切換至「璦閣輸入法」即可使用

---

## 專案結構

```
app/src/main/
├── assets/tables/
│   ├── standard/dictionary.json   # 標準嘸蝦米字碼表 + 聯想詞
│   └── zhuyin/   dictionary.json  # 注音字典（含字頻）
├── java/tw/igg/boshiamyime/
│   ├── service/ BoshiamyInputMethodService.kt   # 輸入法服務核心
│   ├── engine/  InputEngineManager / Lookup / Zhuyin / T9
│   ├── data/    DictionaryManager / DictionaryDownloader
│   ├── ui/      KeyboardView / CandidateBarView
│   └── model/   Candidate / DictionaryEntry / Enums
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

本專案程式碼採 **GPL-3.0** 授權發布（見 `LICENSE`）。內含之字碼表／字典資料另作歸屬聲明，詳見上方「資料來源與授權歸屬」與各資料檔的版權註記。

- 修改後再散布時，須以相同授權開源，並保留原始出處標示。
- 欲商用或另作授權，請先與作者聯繫。

---

## 現況與待辦

- [x] 標準嘸蝦米字碼表（內建）
- [x] 注音字典（字頻排序、簡化字降權）
- [x] T9 五列大按鍵佈局
- [x] 聯想詞（靜態 + 動態學習）
- [x] 中英切換、全半形切換
- [ ] 簡速／倚天字碼表隨附
- [ ] 移除部分輸入框的半形／全形按鈕（依使用者需求調整）
- [ ] 正式 release 簽署與上架準備

---

## 貢獻與聯絡

- GitHub：<https://github.com/Solo-man-IGG/>
- 官方網站：<https://www.igg.tw/>

歡迎回報問題、提出建議或發送 PR。
