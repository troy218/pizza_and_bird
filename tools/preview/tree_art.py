#!/usr/bin/env python3
"""지역 수종 4종(느티나무·향나무·야자수·오리나무) 미리보기 포트.

`Assets.kt` 의 `regionalTreeArt()` (T.TREE 변형 인덱스 15·16·17·18) 사본.
"""
from __future__ import annotations

import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from PIL import Image, ImageDraw  # noqa: E402

from pixelcanvas import Bitmap, Paint, Rnd  # noqa: E402
from render_rocks import grass_bg  # noqa: E402

NAMES = ['느티나무', '향나무(메타세쿼oia)', '야자수(소노베)', '오리나무']
NOTES = ['가로수', '공원 침엽수', '제주 해안', '강원 산지']


def prop_shadow(cv, p, cx, cy, rx, ry, a=0x40):
    p.color = (a << 24) | 0x202C20
    cv.drawOval((cx - rx, cy - ry, cx + rx, cy + ry), p)


def noise(cv, p, r, x0, y0, x1, y1, col, n, minS=1.0, maxS=2.2):
    p.color = col
    for _ in range(n):
        s = minS + r.nextFloat() * (maxS - minS)
        x = x0 + r.nextFloat() * (x1 - x0 - s)
        y = y0 + r.nextFloat() * (y1 - y0 - s)
        cv.drawRect(x, y, x + s, y + s * 0.85, p)


def t00(cv, p, r):
    prop_shadow(cv, p, 16, 28, 10.5, 3)
    cv.drawRect(14.2, 15, 19.6, 31, Paint(0xFF9A8C79))
    cv.drawRect(15.2, 15, 17.6, 31, Paint(0xFFBCAF9B))
    for i in range(4):
        cv.drawRect(12.4 - i * 0.5, 12 - i * 3.4, 12.4 - i * 0.5 + 2.4 - i * 0.3, 12 - i * 3.4 + 4,
                    Paint(0xFF9A8C79))
        cv.drawRect(18.2 + i * 0.4, 11 - i * 3.1, 18.2 + i * 0.4 + 2.2 - i * 0.3, 11 - i * 3.1 + 3.6,
                    Paint(0xFF8E806E))
    cv.drawRect(14.6, 22, 16.2, 24.2, Paint(0xFFD8CFBE))
    cv.drawRect(15, 17, 17, 18.4, Paint(0xFFD8CFBE))
    cv.drawRect(14.4, 27, 16.4, 28.2, Paint(0xFFD8CFBE))
    p.color = 0xFF3B6B41; cv.drawCircle(16, 10, 11.5, p)
    p.color = 0xFF4E8A52; cv.drawCircle(11, 8, 7, p); cv.drawCircle(21, 9, 7, p)
    p.color = 0xFF66A868; cv.drawCircle(9, 12, 4.4, p); cv.drawCircle(23, 12, 4.4, p)
    p.color = 0xFF7EC07E; cv.drawCircle(12, 6, 3.2, p); cv.drawCircle(20, 6.6, 2.6, p)
    noise(cv, p, r, 4, 1, 28, 21, 0xFF34603A, 10, 1, 1.8)


def t01(cv, p, r):
    prop_shadow(cv, p, 16, 28, 10.5, 3)
    cv.drawRect(14.4, 19, 19, 31, Paint(0xFF7A4A34))
    cv.drawRect(15.2, 19, 16.8, 31, Paint(0xFF9E6446))
    noise(cv, p, r, 14.6, 19, 18.4, 30, 0xFF5E3624, 5, 1, 1.4)
    for i in range(6):
        y = 3 + i * 4.4
        half = 3.2 + i * 2.1
        cv.drawRect(16 - half, y + 1.6, 16 - half + half * 2, y + 4, Paint(0xFF1E4C36))
        cv.drawRect(16 - half + 0.8, y, 16 - half + 0.8 + half * 2 - 1.6, y + 1.8, Paint(0xFF2E6B48))
        for d in range(4):
            fx = 16 - half + 1.4 + d * (half * 2 - 2.8) / 3
            cv.drawRect(fx, y - 0.6, fx + 1.2, y + 0.6, Paint(0xFF4B8C5C))
    cv.drawRect(15, 0, 17.4, 4, Paint(0xFF2E6B48))


def t02(cv, p, r):
    prop_shadow(cv, p, 16, 28, 10.5, 3)
    for i in range(6):
        y = 12 + i * 3.1
        cv.drawRect(15.2 - i * 0.25, y, 15.2 - i * 0.25 + 3.6, y + 3.1, Paint(0xFF7A5A38))
        cv.drawRect(15.6 - i * 0.25, y + 0.4, 15.6 - i * 0.25 + 2.2, y + 2.6, Paint(0xFF93704A))
        cv.drawRect(15 - i * 0.25, y + 2.6, 15 - i * 0.25 + 4.4, y + 3.4, Paint(0xFF5E432A))
    for i in range(7):
        a = math.radians(-160 + i * 47)
        ln = 11 - (i % 2) * 1.5
        dx = math.cos(a) * ln
        dy = math.sin(a) * ln * 0.72
        # 줄기 (두께 2.4) — 캔버스가 선 두께를 지원하지 않아 사각형으로 대체
        steps = 14
        col = 0xFF2E7A46 if i % 2 == 0 else 0xFF3E9455
        for s in range(steps + 1):
            tt = s / steps
            cv.drawRect(16 + dx * tt - 1.2, 11 + dy * tt - 1.2, 16 + dx * tt + 1.2,
                        11 + dy * tt + 1.2, Paint(col))
        t = 0.35
        while t < 1.05:
            lx = 16 + dx * t
            ly = 11 + dy * t
            pl = 2.4 * (1 - (t - 0.35) * 0.6)
            cv.drawRect(lx, ly - pl, lx + 1.1, ly + pl,
                        Paint(0xFF4B9E60 if i % 2 == 0 else 0xFF63B46F))
            t += 0.16
    p.color = 0xFF8A6A2E; cv.drawCircle(16, 13.5, 2.6, p)
    p.color = 0xFFB98C3A
    cv.drawCircle(15, 12.8, 1, p); cv.drawCircle(17, 14.2, 0.9, p)


def t03(cv, p, r):
    prop_shadow(cv, p, 16, 28, 10.5, 3)
    cv.drawRect(14.6, 12, 18.2, 31, Paint(0xFFD9DCC8))
    cv.drawRect(15.4, 12, 16.8, 31, Paint(0xFFB4B9A4))
    for x, y in ((15, 16), (16.4, 21), (15, 26), (17.2, 18)):
        cv.drawRect(x, y, x + 2.2, y + 1.4, Paint(0xFF4A4F43))
    for i in range(3):
        cv.drawRect(15 - i * 0.4, 14 - i * 4.4, 15 - i * 0.4 + 1.6, 14 - i * 4.4 + 5, Paint(0xFFD9DCC8))
        cv.drawRect(16.6 + i * 0.3, 13 - i * 4, 16.6 + i * 0.3 + 1.4, 13 - i * 4 + 4.4,
                    Paint(0xFFC6CAB4))
    p.color = 0xFF3A6B40; cv.drawCircle(16, 8, 8.4, p)
    p.color = 0xFF4F8B55; cv.drawCircle(13, 7, 5.4, p); cv.drawCircle(19, 8, 5, p)
    p.color = 0xFF6BA96C; cv.drawCircle(12, 5, 3, p); cv.drawCircle(20, 5.6, 2.6, p)
    noise(cv, p, r, 7, 1, 25, 17, 0xFF335C36, 8, 1, 1.5)


TREES = [t00, t01, t02, t03]


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, 'out', 'trees_sheet.png')
    os.makedirs(os.path.dirname(out) or '.', exist_ok=True)
    font_path = os.path.join(HERE, '..', '..', 'app/src/main/assets/font/body_gowundodum.ttf')
    font = ImageDraw.Draw(Image.new('RGBA', (1, 1)))
    f1 = ImageDraw.Draw(Image.new('RGBA', (1, 1)))
    from PIL import ImageFont
    ft = ImageFont.truetype(font_path, 13)
    ft2 = ImageFont.truetype(font_path, 11)

    zoom = 6
    cell = 32 * zoom + 6
    W = 4 * cell + 6
    H = cell + 40
    img = Image.new('RGBA', (W, H), (0xF2EFE6, 0xF2, 0xF6, 255))
    dr = ImageDraw.Draw(img)
    for i, fn in enumerate(TREES):
        cx = 6 + i * cell
        img.alpha_composite(grass_bg(32, 32).to_pil().resize((32 * zoom, 32 * zoom), 0), (cx, 6))
        b = Bitmap(32, 32)
        fn(b, Paint(), Rnd(9017 + i * 1013))
        img.alpha_composite(b.to_pil().resize((32 * zoom, 32 * zoom), 0), (cx, 6))
        dr.rectangle([cx, 6, cx + 32 * zoom, 6 + 32 * zoom], outline=(0xB0, 0xAC, 0xA0, 255))
        dr.text((cx + 2, 6 + 32 * zoom + 4), f'TREE_{15 + i} {NAMES[i]}', font=ft, fill=(0x20, 0x20, 0x20, 255))
        dr.text((cx + 2, 6 + 32 * zoom + 20), NOTES[i], font=ft2, fill=(0x60, 0x60, 0x60, 255))
    img.save(out)
    print(out, img.size)


if __name__ == '__main__':
    main()
