#!/usr/bin/env python3
"""
새 템플릿 + 실제 종 팔레트 치환 미리보기 (Kotlin remap과 동일 규칙).

용법: python3 tools/bird_preview.py -> art/preview/birds_species.png
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image

import build_art as B
import svg_preview as P

ROOT = Path(__file__).resolve().parent.parent
NS = B.NS

# 플레이스홀더 -> Assets.kt 팔레트 문자
KEYS = {
    "FF00FF": "B", "B800B8": "b", "FF6BFF": "H",
    "00E5FF": "W", "00A3B8": "w", "FFD500": "t", "B89500": "T",
    "7CFF00": "k", "FF7A00": "c", "0094FF": "l",
    "FFFFFF": "e", "101010": "E", "B44CFF": "v",
}
KEY_RGB = {k: tuple(int(k[i:i + 2], 16) for i in (0, 2, 4)) for k in KEYS}


def shade(rgb, f):
    return tuple(max(0, min(255, round(c * f))) for c in rgb)


def species_palette(body, belly, wing, beak, crest, leg):
    return {
        "B": body, "b": shade(body, 0.72), "H": shade(body, 1.18),
        "W": belly, "w": shade(belly, 0.82),
        "t": wing, "T": shade(wing, 0.72),
        "k": beak, "c": crest, "l": leg,
        "e": (253, 253, 248), "E": (26, 22, 17), "v": (143, 212, 234),
    }


def hex2rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def nearest_key(r, g, b):
    best, bd = None, 1 << 30
    for k, (kr, kg, kb) in KEY_RGB.items():
        d = (r - kr) ** 2 + (g - kg) ** 2 + (b - kb) ** 2
        if d < bd:
            best, bd = k, d
    return best, bd


def render_species(sym, pal, zoom=6, thr=140):
    img = P.render_symbol(sym, zoom)
    px = img.load()
    w, h = img.size
    switched = fallback = 0
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a < 8:
                continue
            hexv = f"{r:02X}{g:02X}{b:02X}"
            key = KEYS.get(hexv)
            if key is None:
                key, d = nearest_key(r, g, b)
                if d > thr * thr * 3:
                    continue
                fallback += 1
            nr, ng, nb = pal[key]
            px[x, y] = (nr, ng, nb, a)
            switched += 1
    return img, switched, fallback


def main():
    tree = ET.parse(ROOT / "art" / "svg" / "creatures.svg")
    syms = {el.get("id"): el for el in tree.getroot().iter() if el.tag.replace(NS, "") == "symbol"}

    # (표시이름, 템플릿, body/belly/wing/beak/crest/leg) — Data.kt CURATED 일부
    C = lambda s: hex2rgb(s)
    species = [
        ("sparrow 참새", "art_bird_songbird", C("9C7A54"), C("EFE3CF"), C("6B4A33"), C("4A3728"), C("6B4A33"), C("B98A4A")),
        ("magpie 까치", "art_bird_songbird", C("3C3F47"), C("F2F2F0"), C("23252B"), C("23252B"), C("3C3F47"), C("3A3A44")),
        ("greattit 박새", "art_bird_songbird", C("4F6F52"), C("F2D65A"), C("3A5440"), C("23252B"), C("23252B"), C("B98A4A")),
        ("mandarin 원앙", "art_bird_waterfowl", C("8F4632"), C("E8C468"), C("3E5F8F"), C("C94F4F"), C("8F4632"), C("E8A75C")),
        ("egret 중대백로", "art_bird_wader", C("F5F5F0"), C("FFFFFF"), C("E8E8E0"), C("E8B14E"), C("F5F5F0"), C("3A3A44")),
        ("goshawk 참매", "art_bird_raptor", C("5D6068"), C("E8E4DC"), C("43454C"), C("3A3A44"), C("5D6068"), C("F2B63C")),
        ("owl 올빼미", "art_bird_owl", C("A3826A"), C("E8DCC8"), C("7D6249"), C("4A3728"), C("A3826A"), C("B98A4A")),
        ("eagleowl 수리부엉이", "art_bird_owl", C("8F6B4A"), C("D5C4A8"), C("6B4F35"), C("3A322C"), C("8F6B4A"), C("C9A227")),
    ]

    zoom = 6
    cells = []
    for name, tid, *cols in species:
        sym = syms[tid]
        pal = species_palette(*cols)
        img, sw, fb = render_species(sym, pal, zoom)
        cells.append((name, img, sw, fb))
        print(f"{name}: {sw}px 치환 (+AA {fb}px)")

    cols_n = 4
    cw = max(i.width for _, i, _, _ in cells) + 24
    ch = max(i.height for _, i, _, _ in cells) + 34
    rows = (len(cells) + cols_n - 1) // cols_n
    sheet = Image.new("RGBA", (cols_n * cw, rows * ch), P.BG)
    from PIL import ImageFont, ImageDraw
    dr = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype("DejaVuSans.ttf", 13)
    except OSError:
        font = ImageFont.load_default()
    for idx, (name, img, _, _) in enumerate(cells):
        r, c = divmod(idx, cols_n)
        x0, y0 = c * cw, r * ch
        dr.rectangle([x0 + 3, y0 + 3, x0 + cw - 4, y0 + ch - 4], outline=(58, 53, 80, 255))
        sheet.alpha_composite(img, (x0 + (cw - img.width) // 2, y0 + 6))
        dr.text((x0 + 10, y0 + ch - 22), name, font=font, fill=P.LABEL)
    out = ROOT / "art" / "preview" / "birds_species.png"
    sheet.save(out)
    print("->", out)


if __name__ == "__main__":
    sys.exit(main())
