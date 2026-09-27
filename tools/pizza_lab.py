#!/usr/bin/env python3
"""
피자 아트 반복 검수용 — art/svg/items.svg 의 #art_pizza 를
1) 크게(zoom)  2) 게임과 동일한 22x14 픽셀 다운샘플(셀 중심 샘플링, filter=false 모사)
3) HUD 확대 예시 크기로 렌더해 한 장의 비교 PNG로 저장한다.

용법: python3 tools/pizza_lab.py [out.png]
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_art as B          # noqa: E402
import svg_preview as P        # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
ITEMS = ROOT / "art" / "svg" / "items.svg"
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "art" / "preview" / "pizza_lab.png"


def load_pizza():
    tree = ET.parse(ITEMS)
    for el in tree.getroot().iter():
        if el.tag.replace(P.NS, "") == "symbol" and el.get("id") == "art_pizza":
            return el
    raise SystemExit("art_pizza 심볼 없음")


# ---------------------------------------------------------------------------
# ASCII 스프라이트(Assets.kt pizzaArts 템플릿) 추출/미리보기
# ---------------------------------------------------------------------------

# 수집된 fill(#AARRGGBB) -> 템플릿 문자
COLOR_TO_CHAR = {
    "#FFE8A75C": "c",  # 크러스트 본체
    "#FFD18F4A": "d",  # 크러스트 어두운 림
    "#FFF2C078": "h",  # 크러스트 하이라이트
    "#FFBF7640": "k",  # 그을린 점
    "#FFD8453A": "T",  # 토마토 소스 링
    "#FFF7CE5B": "C",  # 치즈 본체 (baseColor)
    "#FFF2B63C": "S",  # 치즈 구운 가장자리/그늘
    "#FFFBE9A8": "L",  # 치즈 빛 웅덩이
    "#FF9E3236": "r",  # 토핑 A 테
    "#FFD64541": "R",  # 토핑 A 면
    "#FFF08878": "G",  # 토핑 A 윤
    "#FF3E7A44": "b",  # 토핑 B 테
    "#FF6FAE57": "A",  # 토핑 B 면
}


def _flatten_pts(segs):
    return [pts for pts, closed in P.flatten(segs) if closed and len(pts) >= 3]


def _contains(poly, x, y):
    inside = False
    j = len(poly) - 1
    for i in range(len(poly)):
        xi, yi = poly[i]; xj, yj = poly[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            inside = not inside
        j = i
    return inside


def sample_ascii(sym, fw=22, fh=14):
    """게임과 동일한 셀 중심 샘플링으로 22x14 문자 맵 생성."""
    vb = [float(n) for n in re.findall(B.NUM, sym.get("viewBox"))]
    minx, miny = vb[0], vb[1]
    paths, errs = B.collect(sym)
    if errs:
        raise ValueError("; ".join(errs))
    polys = [(_flatten_pts(B.transform_segs(p.segs, -minx, -miny, 1.0, 1.0)), p.color)
             for p in paths]
    rows, unknown = [], set()
    for j in range(fh):
        row = ""
        for i in range(fw):
            x, y = 2 * i + 1, 2 * j + 1
            ch = "."
            for pls, color in polys:
                if any(_contains(pl, x, y) for pl in pls):
                    ch = COLOR_TO_CHAR.get(color)
                    if ch is None:
                        unknown.add(color); ch = "?"
            row += ch
        rows.append(row)
    if unknown:
        print(f"[경고] 미분류 색: {sorted(unknown)}")
    return rows


def tone(col, k):
    """Assets.kt의 tone() 과 동일."""
    a = (col >> 24) & 0xFF; r = (col >> 16) & 0xFF; g = (col >> 8) & 0xFF; b = col & 0xFF
    if k >= 1.0:
        t = min(1.0, max(0.0, k - 1.0))
        r += int((255 - r) * t); g += int((255 - g) * t); b += int((255 - b) * t)
    else:
        r = int(r * k); g = int(g * k); b = int(b * k)
    return (a << 24) | (r << 16) | (g << 8) | b


def ascii_palette(base, topA, topB, oven=False):
    pal = {
        "c": 0xFFE8A75C, "d": 0xFFD18F4A, "h": 0xFFF2C078, "k": 0xFFBF7640,
        "T": 0xFFD8453A,
        "C": base, "L": tone(base, 1.18), "S": tone(base, 0.82),
        "R": topA, "r": tone(topA, 0.72), "G": tone(topA, 1.3),
        "A": topB, "b": tone(topB, 0.72),
    }
    if oven:
        pal.update({"c": 0xFFE0B070, "d": 0xFFB87A45, "h": 0xFFEDC293, "k": 0xFF5A3A2A})
    return pal


def render_ascii(rows, pal, scale):
    W, H = len(rows[0]), len(rows)
    img = Image.new("RGBA", (W * scale, H * scale), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal:
                col = pal[ch]
                c4 = ((col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, (col >> 24) & 0xFF)
                for dy in range(scale):
                    for dx in range(scale):
                        px[x * scale + dx, y * scale + dy] = c4
    return img


def render_game_raster(sym, fw=22, fh=14):
    """게임과 동일: viewBox 44x28 -> big 88x56 렌더 -> 22x14 최근접(필터 없음) 다운샘플."""
    vb = [float(n) for n in re.findall(B.NUM, sym.get("viewBox"))]
    vw, vh = vb[2], vb[3]
    big = P.render_symbol(sym, 2)                      # 88x56, 게임과 동일 해상도
    sx, sy = big.width / fw, big.height / fh
    out = Image.new("RGBA", (fw, fh), (0, 0, 0, 0))
    px = out.load(); src = big.load()
    for y in range(fh):
        for x in range(fw):
            px[x, y] = src[min(big.width - 1, int((x + 0.5) * sx)),
                           min(big.height - 1, int((y + 0.5) * sy))]
    return out, big


def main():
    sym = load_pizza()
    small, big = render_game_raster(sym)

    if "--dump-ascii" in sys.argv:
        rows = sample_ascii(sym)
        for r in rows:
            print('            "' + r + '",')
        # 팔레트 적용 미리보기 (일반 치즈피자 팔레트 기준)
        pal = ascii_palette(0xFFF7CE5B, 0xFFD64541, 0xFF6FAE57)
        render_ascii(rows, pal, 16).save(ROOT / "art" / "preview" / "pizza_ascii_reg.png")
        print("[ascii] art/preview/pizza_ascii_reg.png")
        return

    # --- 비교 시트 구성 ---
    z6 = P.render_symbol(sym, 6)                       # 벡터 상세 (264x168)
    pix16 = small.resize((small.width * 16, small.height * 16), Image.NEAREST)   # 게임 원본 픽셀 x16
    hud = small.resize((dp := 60, 38), Image.NEAREST)  # HUD 20dp 부근
    hud_sq = small.resize((54, 54), Image.NEAREST)     # HUD 정사각 스트레치 배치 모사
    logo = small.resize((88 * 2, 56 * 2), Image.NEAREST)  # 타이틀 pizzaIconBig 2x

    gap, lab = 24, 20
    W = gap * 6 + z6.width + pix16.width + hud.width + hud_sq.width + logo.width
    H = lab + 360
    sheet = Image.new("RGBA", (W, H), (28, 26, 40, 255))
    dr = ImageDraw.Draw(sheet)
    x = gap
    y0 = lab + 10
    for img, label in ((z6, "vector x6"), (pix16, "game raster 22x14 (x16)"),
                       (hud, "HUD ~20dp"), (hud_sq, "HUD square stretch"),
                       (logo, "title logo x2")):
        # 체커보드 배경
        bw, bh = img.size
        for yy in range(0, bh, 8):
            for xx in range(0, bw, 8):
                if (xx // 8 + yy // 8) % 2 == 0:
                    dr.rectangle([x + xx, y0 + yy, x + xx + 7, y0 + yy + 7],
                                 fill=(46, 43, 68, 255))
        sheet.alpha_composite(img, (x, y0))
        dr.text((x, 4), label, fill=(240, 235, 221, 255))
        x += bw + gap
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print(f"[피자 랩] {OUT}")


if __name__ == "__main__":
    main()
