#!/usr/bin/env python3
"""
Assets.kt의 피자 스프라이트 템플릿 2종(일반/화덕)을 추출해
12종 피자 전체 아이콘을 게임과 동일한 팔레트 로직으로 렌더. (pizzaArts 검수용)

용법: python3 tools/pizza_arts_preview.py
"""
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
from pizza_lab import tone  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "java" / "com" / "pizzaandbird" / "game" / "Assets.kt"
OUT = ROOT / "art" / "preview" / "pizza_arts.png"

# Pizzas.ALL (Data.kt) 순서: (이름, 종류, baseColor, topColorA, topColorB)
PIZZAS = [
    ("치즈", "REGULAR", 0xFFF7CE5B, 0xFFF2B63C, 0xFFE8A75C),
    ("버섯", "REGULAR", 0xFFF7CE5B, 0xFFB8926A, 0xFF8A6A4A),
    ("불고기", "REGULAR", 0xFFF2C24E, 0xFF8A4A2E, 0xFF6FAE57),
    ("페퍼로니", "REGULAR", 0xFFF7CE5B, 0xFFC8392B, 0xFFA32E22),
    ("고구마", "REGULAR", 0xFFF2B84A, 0xFFB8702C, 0xFFFFF0A0),
    ("콤비네이션", "REGULAR", 0xFFF7CE5B, 0xFFC8392B, 0xFF5E9E4A),
    ("마르게리타", "OVEN", 0xFFD9503F, 0xFFFDF6E8, 0xFF4F8F3F),
    ("마리나라", "OVEN", 0xFFC94A3A, 0xFFEFE2BC, 0xFF5C8F3F),
    ("콰트로 포륵마지", "OVEN", 0xFFF5E3A3, 0xFF6B7FA3, 0xFFE8A75C),
    ("고르곤졸라", "OVEN", 0xFFF2E6C0, 0xFF7A8BB0, 0xFFE8B923),
    ("디아볼라", "OVEN", 0xFFD9503F, 0xFF8F2B1E, 0xFFF7CE5B),
    ("루꼬라 프로슈토", "OVEN", 0xFFF5E3A3, 0xFFE88A8A, 0xFF4F8F3F),
]

BASE_PAL = {
    "T": 0xFFD8453A,
}
CRUST = {
    "REGULAR": {"c": 0xFFE8A75C, "d": 0xFFD18F4A, "h": 0xFFF2C078, "k": 0xFFBF7640},
    "OVEN": {"c": 0xFFE0B070, "d": 0xFFB87A45, "h": 0xFFEDC293, "k": 0xFF5A3A2A},
}


def load_templates():
    src = ASSETS.read_text()
    out = {}
    for name in ("pizza", "pizzaOven"):
        m = re.search(rf"val {name} = listOf\((.*?)\)\n", src, re.S)
        rows = re.findall(r'"((?:\.|[A-Za-z])*)"', m.group(1))
        out[name] = rows
    return out


def render(rows, scale=10):
    W, H = len(rows[0]), len(rows)
    return W, H, scale


def main():
    tpl = load_templates()
    for key, rows in tpl.items():
        assert all(len(r) == 22 for r in rows), (key, [len(r) for r in rows])
        assert len(rows) == 14, (key, len(rows))
        print(f"[템플릿] {key}: 22x14 OK, 사용 문자: {sorted(set(''.join(rows)) - {'.'})}")

    try:
        font = ImageFont.truetype("DejaVuSans.ttf", 16)
    except OSError:
        font = ImageFont.load_default()

    scale = 10
    cw, ch = 22 * scale + 30, 14 * scale + 42
    cols = 6
    rows_n = (len(PIZZAS) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cw, rows_n * ch), (28, 26, 40, 255))
    dr = ImageDraw.Draw(sheet)

    for idx, (name, kind, base, topA, topB) in enumerate(PIZZAS):
        pal = dict(BASE_PAL)
        pal.update({
            "C": base, "L": tone(base, 1.18), "S": tone(base, 0.82),
            "R": topA, "r": tone(topA, 0.72), "G": tone(topA, 1.3),
            "A": topB, "b": tone(topB, 0.72),
        })
        pal.update(CRUST[kind])
        rows = tpl["pizzaOven"] if kind == "OVEN" else tpl["pizza"]
        img = Image.new("RGBA", (22 * scale, 14 * scale), (0, 0, 0, 0))
        px = img.load()
        for y, row in enumerate(rows):
            for x, chc in enumerate(row):
                if chc in pal:
                    col = pal[chc]
                    c4 = ((col >> 16) & 255, (col >> 8) & 255, col & 255, (col >> 24) & 255)
                    for dy in range(scale):
                        for dx in range(scale):
                            px[x * scale + dx, y * scale + dy] = c4
        r, q = divmod(idx, cols)
        x0, y0 = q * cw, r * ch
        dr.rectangle([x0 + 2, y0 + 2, x0 + cw - 3, y0 + ch - 3], outline=(58, 53, 80, 255))
        sheet.alpha_composite(img, (x0 + 15, y0 + 8))
        dr.text((x0 + 8, y0 + ch - 26), f"{idx} {name} ({'화덕' if kind == 'OVEN' else '일반'})",
                font=ImageFont.load_default(), fill=(240, 235, 221, 255))
    sheet.save(OUT)
    print(f"[미리보기] {OUT}")


if __name__ == "__main__":
    main()
