# 上架 F-Droid 申請文件（BoshiamyIME）

## 背景
- 目標：讓「有緣人」可經 **F-Droid** 免費安裝，符合專案開源精神（GPL-3.0、完全離線）。
- 主要提供無償使用；贊助走 F-Droid 允許的 **Donate** 連結（官方店不放付費內容）。

## 前置準備（已完成，本 repo）
- [x] Gradle wrapper 降至 8.13（F-Droid build server 相容）
- [x] 移除 `kotlin { jvmToolchain(17) }`（避免申請時自動下載 JDK；改用 server 上 JDK 17）
- [x] 產出 metadata：`fdroid/tw.igg.boshiamyime.yml`
- [ ] 可重現建置（reproducible build）——官方庫近期門檻，通過初步審查後再補

## 路線 A：官方 F-Droid 主庫（推薦最終目標）
1. 註冊/登入 GitLab（https://gitlab.com）
2. Fork `https://gitlab.com/fdroid/fdroiddata`
3. 新增檔案 `metadata/tw.igg.boshiamyime.yml`（內容＝本 repo 的 `fdroid/tw.igg.boshiamyime.yml`）
4. 開 Merge Request，等待維護者審查（可能要求補可重現建置、修正 metadata）
5. 通過後 F-Droid 伺服器會對每個新 tag 自動 build 並發佈（`AutoUpdateMode: Version` 已設）

> ✅ **（2026-09-18）已送出 MR #49249**：https://gitlab.com/fdroid/fdroiddata/-/merge_requests/49249（狀態：opened，等待維護者審查）。fork：`Solo-man-IGG/fdroiddata` 分支 `add-boshiamyime`。
>
> ✅ **（2026-09-18）審查迭代記錄**：
> - 第 1 輪：linsui 要求 App Inclusion 模板、commit 完整 hash、修 pipeline。
> - ⚠️ 我們以平行 commit 蓋掉維護者重寫的 metadata（`766d1ea6`）→ linsui 回「You reverted all my changes」。
> - **修正**：fork 分支重整到 `766d1ea6`（保留 RepoType/Builds/Categories Keyboard & IME/License GPL-3.0-only/AutoName）之上，另疊修復 commit `1e63ecc1`（commit 改完整 hash `8c1c5f0e…`、移除 Binaries/scandelete——`Build:` 單數是無效欄位，schema/lint/build 全擋）。
> - Summary/Description 依指示遷到 app repo `fastlane/metadata/android/`（zh-TW＋en-US＋截圖＋changelog），fdroiddata 只留 build metadata。
> - **待維護者重觸發 CI 驗證**（fork 無 SaaS runner，無法自跑）。
> - ✅ 第 3 輪：linsui 疊 `subdir: app`（`afceaf32`）→ parent pipeline `2861611009` 全綠 9/9，MR 等最終 merge。
> - ✅ 第 4 輪完成（2026-09-19）：commit pin→`3f9de31`；兩次 clean build 未簽名 APK 逐位元相同、v2/v3-only 簽名亦逐位元相同；`dist/boshiamy-1.0.19-release.apk`（可重現版，md5 `3ad6061c…`）已替換 GitHub Release 資產；fork MR metadata `c83ea93`；已回覆 linsui 請求重觸發 CI（note `3867888574`）。

## 路線 B：自架第三方 repo（立即可用，與 A 並行）
本機已具備 Android SDK（build-tools 35.0.0）。流程：

```bash
sudo apt install fdroidserver        # 安裝 fdroidserver（若無）
cd /tmp && fdroid init                                  # 建立 repo 骨架
# 把 fdroid/tw.igg.boshiamyime.yml 放進 repo/metadata/ 並填 src 指向 GitHub
fdroid build --server tw.igg.boshiamyime:20             # 從原始碼 build
fdroid update --rename-apks                             # 產生 index 與 apk
# 將 repo/ 內容推到 GitHub Pages（https://<user>.github.io/fdroid/）
```

使用者於 F-Droid 客戶端「新增套件庫」貼上 `https://<user>.github.io/fdroid/` 即可安裝。

卷前申請期間，我們可先以 路線 B 讓老婆/親友馬上用，等官方庫通過後再切換。

## 每版更新做法
- 官方庫：`AutoUpdateMode: Version` 會自動偵測新 tag 並 build，**無需手動**。
- 自架：重跑 `fdroid build --server tw.igg.boshiamyime:<版號>` ＋ `fdroid update` 後 push。

## 注意
- F-Droid 用自己的簽章 key build，與 Play 版（我們的 keystore）**簽章不同**，裝置上不可同時覆蓋安裝（卸載其一）。
- 官方庫要求 code 可複現，若被要求補強，我會加固定時間戳／統一序列化設定。