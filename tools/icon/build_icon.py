"""建置 GInput 的圖示和 Play 商店圖片。

圖示是 JetPiggy 飛豬坐著單蹄點鍵盤（art/typing.png），主題圖示用它的剪影（art/typing-mono.png），
主題圖片用飛行插圖（art/flying.png）。

用法（在專案根目錄）：
    python -I -X utf8 tools/icon/build_icon.py

會寫出：
    app/src/main/res/mipmap-*/ic_launcher_foreground.png   自適應圖示的前景（五種密度）
    app/src/main/res/mipmap-*/ic_launcher_monochrome.png   Android 13 起的主題圖示（單色，五種密度）
    docs/play/graphics/icon-512.png                         Play 商店圖示
    docs/play/graphics/monochrome-preview.png               主題圖示的預覽
    docs/play/graphics/feature-graphic-zh-HK.png、-en.png    Play 商店主題圖片（1024 × 500）
    app/src/main/res/drawable-nodpi/brand_flying.webp       設定頁頂部橫額的飛行插圖（透明底）
    app/src/main/res/drawable-nodpi/brand_sitting.webp      設定頁「關於」的坐姿插圖（透明底）
只更新設定頁的插圖（不用 Chrome）：
    python -I -X utf8 tools/icon/build_icon.py --settings-art
詳情見同一資料夾的 README.md。
"""

import math
import random
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
CHROME = Path("C:/Program Files/Google/Chrome/Application/chrome.exe")
ART = Path(__file__).resolve().parent / "art"
TYPING = ART / "typing.png"
TYPING_MONO = ART / "typing-mono.png"
BANNER = ART / "flying.png"

TEAL = "#2EB8A6"
INK = "#1C1C1C"
BLUE_DARK = "#3F75A8"

# 圖示先畫在 108dp 的大畫布上，每 dp 16 像素，再縮成各種密度。
DP = 16
CANVAS = 108 * DP
VISIBLE = 72  # 啟動器遮罩內看得到的範圍（dp）
SAFE = 66  # 安全圈的直徑（dp）：任何啟動器形狀都不會裁到

DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

# 設定頁的插圖只出一種大小（drawable-nodpi），由 App 按需要縮放。
# 橫額的飛豬約 150dp 高，「關於」的飛豬約 86dp 闊；這個大小在 xxhdpi 和 xxxhdpi 的手機上都清楚，檔案也細。
FLYING_HEIGHT = 480
SITTING_WIDTH = 320
WEBP_QUALITY = 88


# ---- 圖示 ----

def circle_two(a: tuple[float, float], b: tuple[float, float]) -> tuple[float, float, float]:
    return (a[0] + b[0]) / 2, (a[1] + b[1]) / 2, math.dist(a, b) / 2


def circle_three(a: tuple[float, float], b: tuple[float, float], c: tuple[float, float]) -> tuple[float, float, float]:
    (ax, ay), (bx, by), (cx, cy) = a, b, c
    d = 2 * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by))
    if abs(d) < 1e-9:  # 三點在同一直線上
        return max(circle_two(a, b), circle_two(a, c), circle_two(b, c), key=lambda circle: circle[2])
    a2, b2, c2 = ax * ax + ay * ay, bx * bx + by * by, cx * cx + cy * cy
    ux = (a2 * (by - cy) + b2 * (cy - ay) + c2 * (ay - by)) / d
    uy = (a2 * (cx - bx) + b2 * (ax - cx) + c2 * (bx - ax)) / d
    return ux, uy, math.dist((ux, uy), a)


def enclosing_circle(alpha: np.ndarray) -> tuple[float, float, float]:
    """圖案（alpha 過半的點）的最小外接圓，回傳圓心和半徑，單位是像素。

    外接圓只取決於凸包，而凸包的頂點一定是某一行最左或最右的點，所以只用這些像素的四角，以 Welzl 演算法計算。
    """
    solid = alpha >= 128
    rows = np.nonzero(solid.any(axis=1))[0]
    left = solid[rows].argmax(axis=1)
    right = solid.shape[1] - solid[rows, ::-1].argmax(axis=1)
    xs = np.concatenate([left, right, left, right]).astype(float)
    ys = np.concatenate([rows, rows, rows + 1, rows + 1]).astype(float)
    points = list(zip(xs, ys))
    random.Random(0).shuffle(points)

    def inside(circle: tuple[float, float, float], p: tuple[float, float]) -> bool:
        return math.dist(circle[:2], p) <= circle[2] + 1e-6

    circle = (*points[0], 0.0)
    for i, p in enumerate(points):
        if inside(circle, p):
            continue
        circle = (*p, 0.0)
        for j in range(i):
            q = points[j]
            if inside(circle, q):
                continue
            circle = circle_two(p, q)
            for k in range(j):
                if not inside(circle, points[k]):
                    circle = circle_three(p, q, points[k])
    return circle


def fit_safe_zone(art: Image.Image) -> Image.Image:
    """縮放圖案，令它的最小外接圓剛好是 66dp 安全圈，圓心放在畫布中間。回傳 108dp 的透明畫布。"""
    cx, cy, radius = enclosing_circle(np.asarray(art.getchannel("A")))
    scale = SAFE * DP / (2 * radius)
    size = (round(art.width * scale), round(art.height * scale))
    canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    canvas.paste(art.resize(size, Image.LANCZOS), (round(CANVAS / 2 - cx * scale), round(CANVAS / 2 - cy * scale)))
    return canvas


def stencil() -> Image.Image:
    """剪影是白底黑色的圖；主題圖示只用 alpha 再由系統上色，所以黑色變成不透明，白色變成透明。"""
    gray = Image.open(TYPING_MONO).convert("L")
    art = Image.new("RGBA", gray.size, (0, 0, 0, 0))
    art.putalpha(gray.point(lambda v: 255 - v))
    return art


def save_densities(icon: Image.Image, res: Path, name: str) -> None:
    for density, size in DENSITIES.items():
        folder = res / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        icon.resize((size, size), Image.LANCZOS).save(folder / f"{name}.png", optimize=True)


def visible(icon: Image.Image) -> Image.Image:
    """自適應圖示只顯示 108dp 中間的 72dp；Play 商店圖示用同一範圍，圖案才和桌面上一樣大。"""
    margin = (108 - VISIBLE) // 2 * DP
    return icon.crop((margin, margin, CANVAS - margin, CANVAS - margin))


def monochrome_preview(monochrome: Image.Image) -> Image.Image:
    """淺色主題下的樣子：淺藍底、深藍圖案。紅色虛線是 66dp 安全圈。"""
    preview = Image.new("RGBA", (CANVAS, CANVAS), "#D9E2FF")
    preview.paste(Image.new("RGBA", (CANVAS, CANVAS), "#1A2B5C"), (0, 0), monochrome.getchannel("A"))
    draw = ImageDraw.Draw(preview)
    r = SAFE * DP / 2
    box = (CANVAS / 2 - r, CANVAS / 2 - r, CANVAS / 2 + r, CANVAS / 2 + r)
    dash, gap = 1.5 / (SAFE / 2), 1 / (SAFE / 2)  # 弧度：虛線 1.5dp，空位 1dp
    angle = 0.0
    while angle < 2 * math.pi:
        end = min(angle + dash, 2 * math.pi)
        draw.arc(box, math.degrees(angle), math.degrees(end), fill="#E53935", width=round(0.4 * DP))
        angle += dash + gap
    return preview.resize((432, 432), Image.LANCZOS)


# ---- Play 商店主題圖片 ----

def render(page: Path, png: Path, width: int, height: int, profile: Path) -> None:
    subprocess.run(
        [
            str(CHROME),
            "--headless=new",
            "--disable-gpu",
            "--hide-scrollbars",
            "--force-device-scale-factor=1",
            "--allow-file-access-from-files",
            "--default-background-color=00000000",
            f"--user-data-dir={profile}",
            f"--window-size={width},{height}",
            f"--screenshot={png}",
            page.as_uri(),
        ],
        check=True,
        capture_output=True,
    )
    if not png.exists():
        raise RuntimeError(f"Chrome 沒有輸出 {png}")


FEATURE_W, FEATURE_H = 1024, 500
# 飛行插圖縮放後放在左邊、上下置中；字放在右邊。
BANNER_SCALE = 0.52
BANNER_LEFT, TEXT_GAP, TEXT_RIGHT = 40, 40, 40

FEATURE_TEXT = {
    "zh-HK": {
        "lang": "zh-Hant-HK",
        "title": "GInput",
        "subtitle": "中英混打輸入法",
        "subtitle_size": 50,
        "tagline": "同一個鍵盤打中英文，不用切換",
    },
    "en": {
        "lang": "en",
        "title": "GInput",
        "subtitle": "Chinese &amp; English,<br>one keyboard",
        "subtitle_size": 38,
        "tagline": "Made for Hong Kong.",
    },
}


def banner_art() -> tuple[Image.Image, int]:
    """把飛行插圖放在品牌青綠底上，回傳 1024 × 500 的圖，以及字的左邊位置。

    插圖的背景接近平均的青綠色，但不是準確的品牌青綠，而且有壓縮雜點；直接貼上去，四周會見到一個方框。
    所以和背景色相近的點一律換成品牌青綠，飛豬的邊緣按和背景色的距離漸變，飛豬本身不變。
    """
    rgb = np.asarray(Image.open(BANNER).convert("RGB"), dtype=np.float32)
    edges = np.concatenate([rgb[:8].reshape(-1, 3), rgb[-8:].reshape(-1, 3)])
    background = np.median(edges, axis=0)
    distance = np.linalg.norm(rgb - background, axis=2)
    alpha = np.clip((distance - 8) / 32, 0, 1)[..., None]
    brand = np.array([int(TEAL[i:i + 2], 16) for i in (1, 3, 5)], dtype=np.float32)
    fixed = Image.fromarray(np.clip(rgb * alpha + brand * (1 - alpha), 0, 255).round().astype(np.uint8))

    # 只取飛豬、屁雲和鍵盤的範圍，四邊多留一點背景，縮放時邊緣才乾淨。
    ys, xs = np.nonzero(distance > 25)
    pad = 8
    content = fixed.crop((max(0, xs.min() - pad), max(0, ys.min() - pad),
                          min(fixed.width, xs.max() + 1 + pad), min(fixed.height, ys.max() + 1 + pad)))
    size = (round(content.width * BANNER_SCALE), round(content.height * BANNER_SCALE))
    canvas = Image.new("RGB", (FEATURE_W, FEATURE_H), TEAL)
    canvas.paste(content.resize(size, Image.LANCZOS), (BANNER_LEFT - round(pad * BANNER_SCALE), (FEATURE_H - size[1]) // 2))
    return canvas, BANNER_LEFT + size[0] + TEXT_GAP


# ---- 設定頁的插圖 ----


def flying_cutout() -> Image.Image:
    """飛行插圖去掉青綠背景，只取飛豬、屁雲和鍵盤的範圍（透明底）。

    透明度按和背景色的距離漸變，和 banner_art 一樣；邊緣混進的青綠也減掉，所以放在任何底色上都沒有青綠的邊。
    """
    rgb = np.asarray(Image.open(BANNER).convert("RGB"), dtype=np.float32)
    edges = np.concatenate([rgb[:8].reshape(-1, 3), rgb[-8:].reshape(-1, 3)])
    background = np.median(edges, axis=0)
    distance = np.linalg.norm(rgb - background, axis=2)
    alpha = np.clip((distance - 8) / 32, 0, 1)[..., None]
    colour = (rgb - background * (1 - alpha)) / np.maximum(alpha, 1e-3)
    rgba = np.concatenate([np.clip(colour, 0, 255), alpha * 255], axis=2).round().astype(np.uint8)
    ys, xs = np.nonzero(distance > 25)
    pad = 4
    return Image.fromarray(rgba, "RGBA").crop((max(0, xs.min() - pad), max(0, ys.min() - pad),
                                               min(rgb.shape[1], xs.max() + 1 + pad), min(rgb.shape[0], ys.max() + 1 + pad)))


def settings_art(res: Path) -> None:
    """設定頁的插圖：頂部橫額的飛行插圖和「關於」的坐姿插圖，都是透明底的 WebP。"""
    out = res / "drawable-nodpi"
    out.mkdir(parents=True, exist_ok=True)
    flying = flying_cutout()
    flying = flying.resize((round(flying.width * FLYING_HEIGHT / flying.height), FLYING_HEIGHT), Image.LANCZOS)
    flying.save(out / "brand_flying.webp", quality=WEBP_QUALITY, method=6)
    sitting = Image.open(TYPING).convert("RGBA")
    sitting = sitting.crop(sitting.getchannel("A").getbbox())
    sitting = sitting.resize((SITTING_WIDTH, round(sitting.height * SITTING_WIDTH / sitting.width)), Image.LANCZOS)
    sitting.save(out / "brand_sitting.webp", quality=WEBP_QUALITY, method=6)


def feature_html(art_href: str, text_left: int, text: dict) -> str:
    width = FEATURE_W - text_left - TEXT_RIGHT
    return f"""<!doctype html>
<html lang="{text['lang']}"><head><meta charset="utf-8"><style>
html,body{{margin:0;padding:0;width:{FEATURE_W}px;height:{FEATURE_H}px;overflow:hidden}}
body{{background:{TEAL};font-family:'Noto Sans HK','Noto Sans TC',sans-serif;color:{INK};position:relative}}
.art{{position:absolute;left:0;top:0;width:{FEATURE_W}px;height:{FEATURE_H}px}}
.text{{position:absolute;left:{text_left}px;top:0;bottom:0;width:{width}px;
  display:flex;flex-direction:column;justify-content:center;align-items:flex-start}}
h1{{margin:0;font-size:76px;font-weight:900;line-height:1}}
h2{{margin:12px 0 0;font-size:{text['subtitle_size']}px;font-weight:900;line-height:1.15}}
p{{margin:14px 0 0;font-size:25px;font-weight:700;opacity:.82;line-height:1.35}}
.bubble{{margin-top:26px;background:#fff;border-radius:26px 26px 26px 6px;padding:12px 24px;white-space:nowrap;
  font-size:30px;font-weight:500;box-shadow:0 8px 20px rgba(28,28,28,.18)}}
.bubble b{{color:{BLUE_DARK};font-weight:700}}
</style></head><body>
<img class="art" src="{art_href}" alt="">
<div class="text">
  <h1>{text['title']}</h1>
  <h2>{text['subtitle']}</h2>
  <p>{text['tagline']}</p>
  <div class="bubble">今晚 <b>dinner</b> 食咩好？</div>
</div>
</body></html>"""


def main() -> None:
    res = ROOT / "app" / "src" / "main" / "res"
    settings_art(res)
    if "--settings-art" in sys.argv[1:]:
        print("完成：", res / "drawable-nodpi")
        return
    graphics = ROOT / "docs" / "play" / "graphics"
    graphics.mkdir(parents=True, exist_ok=True)

    foreground = fit_safe_zone(Image.open(TYPING).convert("RGBA"))
    save_densities(foreground, res, "ic_launcher_foreground")
    full = Image.new("RGBA", (CANVAS, CANVAS), TEAL)
    full.alpha_composite(foreground)
    visible(full).resize((512, 512), Image.LANCZOS).save(graphics / "icon-512.png", optimize=True)

    monochrome = fit_safe_zone(stencil())
    save_densities(monochrome, res, "ic_launcher_monochrome")
    monochrome_preview(monochrome).convert("RGB").save(graphics / "monochrome-preview.png", optimize=True)

    with tempfile.TemporaryDirectory() as tmp_name:
        tmp = Path(tmp_name)
        profile = tmp / "chrome-profile"

        # 主題圖片：飛行插圖在左，字在右。
        banner, text_left = banner_art()
        banner.save(tmp / "banner.png")
        for key, text in FEATURE_TEXT.items():
            page = tmp / f"feature-{key}.html"
            page.write_text(feature_html((tmp / "banner.png").as_uri(), text_left, text), encoding="utf-8")
            png = tmp / f"feature-{key}.png"
            render(page, png, FEATURE_W, FEATURE_H, profile)
            Image.open(png).convert("RGB").save(graphics / f"feature-graphic-{key}.png", optimize=True)

    print("完成：", res, graphics)


if __name__ == "__main__":
    main()
