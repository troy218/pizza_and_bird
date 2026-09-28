#!/usr/bin/env python3
"""바위 28종 접촉 시트 — 실루엣·크기·암종을 한 장에서 비교한다.

    python3 tools/preview/render_rocks.py                 # out/rocks_sheet.png
    python3 tools/preview/render_rocks.py /tmp/rocks.png
"""
from __future__ import annotations

import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from PIL import Image, ImageDraw  # noqa: E402

import rock_catalog as RC  # noqa: E402
from pixelcanvas import Bitmap, Paint  # noqa: E402
from rock_art import rng  # noqa: E402

COLS = 7
CELL = 32
PAD = 4
LABEL = 34
ZOOM = 4

# 미리보기 배경 잔디 (Grass.kt GRASS 변형 0 근사)
GRASS_A = 0xFF96D07A
GRASS_B = 0xFF88BE6C
GRASS_C = 0xFFA2D98A


def grass_bg(w, h, tile=32):
    b = Bitmap(w, h)
    p = Paint()
    for ty in range(0, h, tile):
        for tx in range(0, w, tile):
            p.color = GRASS_A if (tx // tile + ty // tile) % 2 == 0 else GRASS_B
            b.drawRect(tx, ty, tx + tile, ty + tile, p)
    r = rng(4242)
    for _ in range(w * h // 26):
        x = r.nextInt(w)
        y = r.nextInt(h)
        p.color = GRASS_C if r.nextBoolean() else shade2(GRASS_A, 0.86)
        b.drawRect(x, y, x + 1, y + 3, p)
    return b


def shade2(col, f):
    r = int(((col >> 16) & 0xFF) * f)
    g = int(((col >> 8) & 0xFF) * f)
    b = int((col & 0xFF) * f)
    return 0xFF000000 | (min(255, r) << 16) | (min(255, g) << 8) | min(255, b)


# ── 지역별 바위 조합 시트 ───────────────────────────────────────────────
ROOTS = os.path.join(HERE, '..', '..', 'app/src/main/java/com/pizzaandbird/game')
FONT = os.path.join(HERE, '..', '..', 'app/src/main/assets/font/body_gowundodum.ttf')

# 지역 표시명(RegionMapStyle 의 한글 주석에서 뽑지 않고 직접 둔다 — 시트 라벨용)
REGION_LABEL = {
    'seoul': '서울', 'incheon': '인천', 'chuncheon': '춘천', 'gangneung': '강릉',
    'sokcho': '속초', 'daejeon': '대전', 'jeonju': '전주', 'daegu': '대구',
    'gwangju': '광주', 'ulsan': '울산', 'busan': '부산', 'jeju': '제주',
    'ganghwa': '강화 갯벌', 'cheorwon': '철원 평야', 'eulsukdo': '을숙도 하구',
    'gongneung': '공릉천', 'gwangneung': '광릉숲', 'songdo': '송도 갯벌', 'sihwa': '시화호',
    'hwaseong': '화성 습지', 'ansan': '안산 갈대습지', 'maehyang': '매향리 해안',
    'junam': '주남저수지', 'suncheon': '순천만', 'geumgang': '금강 하구', 'gochang': '고창 갯벌',
    'taean': '태안 천수만', 'upo': '우포늪', 'hadori': '제주 하도리', 'hallasan': '한라산',
    'imjin': '임진강', 'wangpi': '왕피천',
}


def parse_region_rocks():
    """RegionMapStyle.kt 의 natureArtFor() 에서 지역별 바위 번호를 뽑는다."""
    src = open(os.path.join(ROOTS, 'RegionMapStyle.kt'), encoding='utf-8').read()
    body = src[src.index('private fun natureArtFor('):src.index('fun forRegion(')]
    # 묶음 기본 바위 목록 (선언은 natureArtFor 보다 앞에 있다)
    fam = {}
    for m in re.finditer(r'private val (\w+Nature) = NatureArtSet\((.*?)\n    \)', src, re.S):
        rm = re.search(r'rocks = listOf\(([^)]*)\)', m.group(2))
        fam[m.group(1)] = [int(v) for v in rm.group(1).split(',')] if rm else []
    out = {}
    for m in re.finditer(r'"([a-z]+)"\s*->\s*(\w+Nature)(\.copy\((.*?)\))?\s*\n', body, re.S):
        rid, family, _, copts = m.group(1), m.group(2), m.group(3), m.group(4)
        rocks = list(fam.get(family, []))
        if copts:
            rm = re.search(r'rocks = listOf\(([^)]*)\)', copts)
            if rm:
                rocks = [int(v) for v in rm.group(1).split(',')]
        out[rid] = rocks
    return out, fam


def region_sheet(path):
    from PIL import ImageFont
    rocks, fam = parse_region_rocks()
    missing = [r for r in REGION_LABEL if r not in rocks]
    if missing:
        print('  ! 파싱 실패:', missing)
    font_path = FONT
    ft = ImageFont.truetype(font_path, 12)
    ft2 = ImageFont.truetype(font_path, 10)
    cols = 4
    cell = CELL * 3 + PAD
    rows = (len(REGION_LABEL) + cols - 1) // cols
    W = cols * (cell * 6 + PAD) + PAD
    H = rows * (cell + 30) + PAD + 4
    img = Image.new('RGBA', (W, H), (0xF2EFE6, 0xF2, 0xF6, 255))
    dr = ImageDraw.Draw(img)
    for i, (rid, label) in enumerate(REGION_LABEL.items()):
        col, row = i % cols, i // cols
        cx = PAD + col * (cell * 6 + PAD)
        cy = PAD + row * (cell + 30)
        picks = rocks.get(rid, [])
        strip = Bitmap(cell * 6, cell)
        strip.drawBitmap(grass_bg(cell * 6, cell), 0, 0, None)
        for j, look in enumerate(picks[:6]):
            if 0 <= look < len(RC.CATALOG):
                strip.drawBitmap(RC.render_rock(look), j * cell, 0, None)
        img.alpha_composite(strip.to_pil(), (cx, cy))
        dr.rectangle([cx, cy, cx + cell * 6, cy + cell], outline=(0xB0, 0xAC, 0xA0, 255))
        dr.text((cx + 2, cy + cell + 3), f'{label}', font=ft, fill=(0x20, 0x20, 0x20, 255))
        names = ' · '.join(RC.CATALOG[l][1] for l in picks[:4] if 0 <= l < len(RC.CATALOG))
        dr.text((cx + 2, cy + cell + 17), names, font=ft2, fill=(0x60, 0x60, 0x60, 255))
    img.save(path)
    print(path, img.size)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, 'out', 'rocks_sheet.png')
    os.makedirs(os.path.dirname(out) or '.', exist_ok=True)

    from PIL import ImageFont
    ft = ImageFont.truetype(FONT, 12)
    ft2 = ImageFont.truetype(FONT, 10)

    n = len(RC.CATALOG)
    rows = (n + COLS - 1) // COLS
    cell = CELL * ZOOM + PAD
    W = COLS * cell + PAD
    H = rows * (cell + LABEL) + PAD + 4

    img = Image.new('RGBA', (W, H), (0xF2EFE6, 0xF2, 0xF6, 255))
    dr = ImageDraw.Draw(img)

    for i in range(n):
        rid, name, size, kind = RC.CATALOG[i]
        col, row = i % COLS, i // COLS
        cx = PAD + col * cell
        cy = PAD + row * (cell + LABEL)
        # 배경 — 회색(빈 칸) / 초록(월드)
        if size == RC.PEBBLE:
            dr.rectangle([cx, cy, cx + cell - PAD, cy + cell - PAD], fill=(0xD8, 0xD4, 0xC8, 255))
        else:
            g = grass_bg(CELL, CELL).to_pil().resize((CELL * ZOOM, CELL * ZOOM), 0)
            img.alpha_composite(g, (cx, cy))
        bmp = RC.render_rock(i)
        spr = bmp.to_pil().resize((CELL * ZOOM, CELL * ZOOM), 0)
        img.alpha_composite(spr, (cx, cy))
        dr.rectangle([cx, cy, cx + cell - PAD - 1, cy + cell - PAD - 1], outline=(0xB0, 0xAC, 0xA0, 255))
        # 타일 중앙선
        dr.line([cx + (cell - PAD) // 2, cy, cx + (cell - PAD) // 2, cy + cell - PAD],
                fill=(0xC8, 0xC4, 0xB8, 255))

        tx, ty = cx + 2, cy + cell - PAD + 2
        dr.text((tx, ty), f'{i:2d} {name}', font=ft, fill=(0x20, 0x20, 0x20, 255))
        dr.text((tx, ty + 14), f'{kind} · {RC.SIZE_LABEL[size]}', font=ft2,
                fill=(0x60, 0x60, 0x60, 255))

    img.save(out)
    print(out, img.size)
    region_sheet(out.replace('.png', '_regions.png'))
    for i, (rid, name, size, kind) in enumerate(RC.CATALOG):
        print(f'  {i:2d} {name:<10} {size:<7} {kind}')


if __name__ == '__main__':
    main()
