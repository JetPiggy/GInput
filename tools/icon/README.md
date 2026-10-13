# 圖示和 Play 商店圖片

正式圖示：JetPiggy 飛豬坐著，很得意地單蹄點一個藍色小鍵盤，品牌青綠（`#2EB8A6`）底。

`build_icon.py` 用 `art/` 的插圖產生：

| 輸出 | 用途 |
|---|---|
| `app/src/main/res/mipmap-*/ic_launcher_foreground.png` | 自適應圖示的前景，透明底，五種密度 |
| `app/src/main/res/mipmap-*/ic_launcher_monochrome.png` | Android 13 起的主題圖示（單色），五種密度。系統只用形狀，顏色跟主題，五官和鍵是鏤空的 |
| `docs/play/graphics/icon-512.png` | Play 商店圖示（512 × 512） |
| `docs/play/graphics/feature-graphic-zh-HK.png`、`-en.png` | Play 商店主題圖片（1024 × 500）：左邊是飛行插圖，右邊是墨黑（`#1C1C1C`）的字 |
| `docs/play/graphics/monochrome-preview.png` | 主題圖示的預覽，紅色虛線是 66dp 安全圈；不用上傳 |
| `app/src/main/res/drawable-nodpi/brand_flying.webp` | 設定頁品牌橫額的飛行插圖：去掉青綠背景（透明底），只取飛豬、屁雲和鍵盤；也用於「關於」的彩蛋 |
| `app/src/main/res/drawable-nodpi/brand_sitting.webp` | 設定頁品牌卡的坐姿插圖，透明底 |

背景色在 `app/src/main/res/values/ic_launcher_background.xml`。App 設定頁的主題色（`ui/theme/Theme.kt`）用同一種青綠和墨黑。

## 插圖

JetPiggy 是粗糙馬克筆風格的四腳粉紅豬：藍色泳鏡、細小的白翼、頸上打結的紅披風，靠放屁飛行，表情總是一副不在乎、很得意的樣子。

`art/` 的圖已經處理好，直接使用：

| 檔案 | 用途 |
|---|---|
| `typing.png` | App 圖示：坐著點鍵盤，透明底 |
| `typing-mono.png` | 主題圖示：黑豬白挖空的剪影，和 `typing.png` 同一位置 |
| `flying.png` | 主題圖片：放屁飛行邊飛邊點鍵盤，青綠底 |

建置時會自動處理兩件事，所以換圖時不用自己對位置和顏色：

- 圖示按圖案的最小外接圓縮放，剛好放進 66dp 安全圈，圓心放在中間。
- 飛行插圖的背景換成準確的品牌青綠，並裁走多餘的背景，所以背景只要是平均的青綠色就可以；飛豬超過畫面一半也沒有關係。

換圖時，用同一個檔名蓋過 `art/` 的檔案，再重新建置。

## 重新建置

在專案根目錄執行（需要 Google Chrome、Windows 內置的 Noto Sans HK 字型，以及 Python 的 Pillow 和 NumPy）：

```bash
python -I -X utf8 tools/icon/build_icon.py
```

Chrome 只用來畫主題圖片的字。只更新設定頁的兩張插圖的話，加 `--settings-art`，不用 Chrome：

```bash
python -I -X utf8 tools/icon/build_icon.py --settings-art
```

改了 `brand_flying.webp` 的闊高比，要一併改 `settings/Brand.kt` 的 `FLYING_RATIO`。

## 規則

- 圖案要放在 108dp 畫布中間的 66dp 安全圈內，任何啟動器形狀都不會裁到。
- Play 商店圖示用 108dp 中間的 72dp 可見範圍，令飛豬和手機桌面上一樣大。
- App 圖示要簡潔：不畫屁雲，也不寫字，只保留泳鏡、白翼、披風和鍵盤。
- 主題圖片不可以和 App 圖示太相似（Google 的指引），所以用放屁飛行的插圖，不用圖示的坐姿。插圖本身不寫字。
- 主題圖片的字是 Noto Sans HK（SIL Open Font License），只畫成圖片，沒有把字型放進 App。
- JetPiggy 飛豬是品牌圖像：圖示、`art/` 的圖片、設定頁的插圖和 Play 商店圖片保留所有權利，不在開源授權的範圍內（`NOTICE`）。
