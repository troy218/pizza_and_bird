#!/usr/bin/env python3
"""
art/svg 마스터의 미리보기 PNG를 만든다 (아트 검수용).

tools/build_art.py 와 동일한 SVG 서브셋을 PIL로 렌더 — 변환 결과와
동일한 도형 해석 경로를 쓰므로 미리보기가 곧 게임 내 결과물이다.

용법: python3 tools/svg_preview.py            -> art/preview/<src>_sheet.png
      python3 tools/svg_preview.py --zoom 8
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

import build_art as B

ROOT = Path(__file__).resolve().parent.parent
SVG_DIR = ROOT / "art" / "svg"
PREVIEW_DIR = ROOT / "art" / "preview"
NS = B.NS

BG = (38, 36, 54, 255)
LABEL = (240, 235, 221, 255)
SUB = (150, 146, 179, 255)
PANEL = (32, 30, 48, 255)


def flatten(segs, steps=8):
    """C/Q 곡선을 선분으로 펼친 다각형 리스트 [(pts, closed)]"""
    polys = []
    pts = []
    x = y = 0.0
    for cmd, v in segs:
        if cmd == "M":
            if pts:
                polys.append((pts, True))
            x, y = v
            pts = [(x, y)]
        elif cmd == "L":
            x, y = v
            pts.append((x, y))
        elif cmd == "C":
            x0, y0 = x, y
            for i in range(1, steps + 1):
                t = i / steps
                mt = 1 - t
                px = mt**3 * x0 + 3 * mt * mt * t * v[0] + 3 * mt * t * t * v[2] + t**3 * v[4]
                py = mt**3 * y0 + 3 * mt * mt * t * v[1] + 3 * mt * t * t * v[3] + t**3 * v[5]
                pts.append((px, py))
            x, y = v[4], v[5]
        elif cmd == "Q":
            x0, y0 = x, y
            for i in range(1, steps + 1):
                t = i / steps
                mt = 1 - t
                px = mt * mt * x0 + 2 * mt * t * v[0] + t * t * v[2]
                py = mt * mt * y0 + 2 * mt * t * v[1] + t * t * v[3]
                pts.append((px, py))
            x, y = v[2], v[3]
        elif cmd == "A":
            arc_pts, x, y = _arc_points(x, y, *v)
            pts.extend(arc_pts)
        elif cmd == "Z":
            if pts:
                polys.append((pts, True))
            pts = []
    if pts:
        polys.append((pts, False))
    return polys


def _arc_points(x1, y1, rx, ry, phi_deg, fa, fs, x2, y2):
    """SVG endpoint-arc -> 중심 파라미터 변환 후 샘플링 (SVG spec F.6.5)."""
    import math
    if rx == 0 or ry == 0:
        return [(x2, y2)], x2, y2
    phi = math.radians(phi_deg % 360)
    cp, sp = math.cos(phi), math.sin(phi)
    dx, dy = (x1 - x2) / 2.0, (y1 - y2) / 2.0
    x1p = cp * dx + sp * dy
    y1p = -sp * dx + cp * dy
    rx, ry = abs(rx), abs(ry)
    lam = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry)
    if lam > 1:
        s = math.sqrt(lam)
        rx *= s
        ry *= s
    num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    co = math.sqrt(max(0.0, num / den)) if den else 0.0
    if fa == fs:
        co = -co
    cxp = co * rx * y1p / ry
    cyp = -co * ry * x1p / rx
    cx = cp * cxp - sp * cyp + (x1 + x2) / 2.0
    cy = sp * cxp + cp * cyp + (y1 + y2) / 2.0

    def ang(ux, uy, vx, vy):
        d = ux * vx + uy * vy
        l = math.hypot(ux, uy) * math.hypot(vx, vy)
        a = math.acos(max(-1.0, min(1.0, d / l))) if l else 0.0
        if ux * vy - uy * vx < 0:
            a = -a
        return a

    th1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dth = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not fs and dth > 0:
        dth -= 2 * math.pi
    elif fs and dth < 0:
        dth += 2 * math.pi
    steps = max(4, int(abs(dth) / (math.pi / 14)))
    pts = []
    for i in range(1, steps + 1):
        t = th1 + dth * i / steps
        x = cp * rx * math.cos(t) - sp * ry * math.sin(t) + cx
        y = sp * rx * math.cos(t) + cp * ry * math.sin(t) + cy
        pts.append((x, y))
    pts.append((x2, y2))
    return pts, x2, y2


def parse_color_argb(c: str):
    a = int(c[1:3], 16)
    return (int(c[3:5], 16), int(c[5:7], 16), int(c[7:9], 16), a)


def render_symbol(sym, zoom: int) -> Image.Image:
    vb = [float(n) for n in re.findall(B.NUM, sym.get("viewBox"))]
    minx, miny, vw, vh = vb
    W, H = max(1, round(vw * zoom)), max(1, round(vh * zoom))
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    paths, errs = B.collect(sym)
    if errs:
        raise ValueError("; ".join(errs))
    for p in paths:
        for pts, closed in flatten(B.transform_segs(p.segs, -minx, -miny, 1.0, 1.0)):
            spts = [(x * zoom, y * zoom) for x, y in pts]
            if closed and len(spts) >= 3:
                dr.polygon(spts, fill=parse_color_argb(p.color))
            elif len(spts) >= 2:
                dr.line(spts, fill=parse_color_argb(p.color), width=max(1, zoom // 2))
    return img


def main():
    zoom = 5
    if "--zoom" in sys.argv:
        zoom = int(sys.argv[sys.argv.index("--zoom") + 1])
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    try:
        font = ImageFont.truetype("DejaVuSans.ttf", 15)
        small = ImageFont.truetype("DejaVuSans.ttf", 12)
    except OSError:
        font = ImageFont.load_default()
        small = font

    for f in sorted(SVG_DIR.glob("*.svg")):
        tree = ET.parse(f)
        syms = [el for el in tree.getroot().iter() if el.tag.replace(NS, "") == "symbol"]
        cells = []
        for sym in syms:
            try:
                img = render_symbol(sym, zoom)
            except ValueError as e:
                print(f"[오류] {f.name} {sym.get('id')}: {e}")
                continue
            cells.append((sym.get("id", "?")[4:], img))

        if not cells:
            continue
        cw = max(c.width for c, _ in [(i, c) for c, i in cells]) + 28
        ch = max(i.height for _, i in cells) + 52
        cols = max(1, min(8, (1400 // cw)))
        rows = (len(cells) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * cw, rows * ch), BG)
        dr = ImageDraw.Draw(sheet)
        for idx, (name, img) in enumerate(cells):
            r, c = divmod(idx, cols)
            x0, y0 = c * cw, r * ch
            dr.rectangle([x0 + 3, y0 + 3, x0 + cw - 4, y0 + ch - 4], outline=(58, 53, 80, 255))
            sheet.alpha_composite(img, (x0 + (cw - img.width) // 2, y0 + 8))
            dr.text((x0 + 10, y0 + ch - 22), f"{name}", font=small, fill=LABEL)
        out = PREVIEW_DIR / f"{f.stem}_sheet.png"
        sheet.save(out)
        print(f"[미리보기] {out} ({len(cells)}종)")


if __name__ == "__main__":
    main()
