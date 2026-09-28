#!/usr/bin/env python3
"""DumpWorld.kt 의 JSON 덤프 → 실제 타일 아트로 지역 월드맵 PNG 렌더.

Kotlin MapBuilder 가 만든 실제 게임 맵(논리 타일·지면·포장·데칼)을 그대로 읽어,
tools/preview 의 타일/길 아트(tiles_legacy + roads)로 래스터화한다.
지형 레이아웃은 100% 실제 게임 결과물이고, 아트만 동일 규칙의 파이썬 이식본을 쓴다.

- 지역 팔레트(styles.json)의 MULTIPLY 틴트를 지면/물/모래/돌/나무에 적용
- 자연물 아트 변형(RegionMapStyle.NatureArt) 선택 해시를 그대로 이식
- GrassField(살아있는 풀)의 입지 계획을 이식해 풀잎 위치를 오버레이
  (--legacy-grass 로 이전 규칙도 재현 가능 — 전/후 비교용)

사용:
    python3 tools/preview/render_world.py <덤프폴터> --out <출력폴터> [지역id ...] [--no-grass]
    python3 tools/preview/render_world.py /tmp/maps_before --out /tmp/rb seoul busan --legacy-grass
"""
from __future__ import annotations

import argparse
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tiles_legacy as L                 # noqa: E402
import roads as R                        # noqa: E402
import render as legacy_render           # noqa: E402
from pixelcanvas import Rnd, Bitmap      # noqa: E402

# render.py 는 init() 호출 뒤에 MEDALLION/DRAIN/AO 가 채워진다 (지연 로딩)
from render import road_tile, tile_variant  # noqa: E402


def render_init():
    legacy_render.init()

PAVE_DIRT, PAVE_STONE = 1, 2

# 랜드마크는 전용 아트가 따로 있지만 미리보기에선 같은 건축물 패밀리로 대체한다
NAME_ALIAS = {
    'LM_ROOF': 'BLDG_ROOF', 'LM_WALL': 'BLDG_WALL', 'LM_WIN': 'BLDG_WIN',
    'LANDMARK_DOOR': 'HOUSE_DOOR',
    'RANGE': 'OVEN', 'RANGE_TOP': 'OVEN',
}

# GameMap.drawGroundTile 의 지면 렌더 조건: tile.ground | tile.prop | OVEN | 포장됨
GROUND_TILES = {'GRASS', 'TALLGRASS', 'FLOWER', 'SAND', 'WATER', 'REED', 'PATH', 'PLAZA',
                'FLOOR'}
PROP_TILES = {'TREE', 'ROCK', 'SIGN', 'BENCH', 'LAMP'}
BULK = {'BLDG_WALL', 'BLDG_WIN', 'BLDG_ROOF', 'HOUSE_ROOF', 'HOUSE_WALL', 'HOUSE_WIN',
        'MOUNTAIN', 'WALL_IN', 'WALL_WIN', 'TUNNEL', 'LM_ROOF', 'LM_WALL', 'LM_WIN'}

# GameMap.terrainPaint 와 같은 패밀리 — 이 패밀리 아트에만 지역 틴트를 곱한다
TINT_FAMILY = {
    'GRASS': 'foliage', 'TALLGRASS': 'foliage', 'FLOWER': 'foliage',
    'REED': 'foliage', 'TREE': 'foliage',
    'WATER': 'water', 'SAND': 'shore',
    'ROCK': 'stone', 'MOUNTAIN': 'stone',
}

NATURE_KEY = {
    'GRASS': 'grass', 'TALLGRASS': 'tallGrass', 'FLOWER': 'flowers',
    'REED': 'reeds', 'ROCK': 'rocks', 'MOUNTAIN': 'mountains', 'TREE': 'trees',
}


def java_hash(s: str) -> int:
    hsh = 0
    for ch in s:
        hsh = (hsh * 31 + ord(ch)) & 0xFFFFFFFF
    if hsh >= 1 << 31:
        hsh -= 1 << 32
    return hsh


def i32(v: int) -> int:
    v &= 0xFFFFFFFF
    return v - (1 << 32) if v >= 1 << 31 else v


def art_variant(nature, name, x, y, rid, ordinal):
    """RegionMapStyle.NatureArt.variant 와 동일한 해시 — 같은 칸엔 같은 실루엣."""
    options = nature.get(NATURE_KEY.get(name, '')) or []
    if not options:
        return None
    h = i32(x * 0x45D9F3B) + i32(y * 0x119DE1F3) + java_hash(rid) * 31 + ordinal * 0x27D4EB2D
    h = i32(h)
    h = i32((h ^ ((h & 0xFFFFFFFF) >> 16)) * 0x45D9F3B)
    h = h ^ ((h & 0xFFFFFFFF) >> 16)
    return options[h % len(options)]


def hex_rgb(s: str):
    v = int(s.lstrip('#'), 16)
    return (v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF


# ---------------------------------------------------------------------------
# 풀 — GrassField.planFor / build 의 파이썬 이식 (난수 소모 순서까지 동일하게)
# ---------------------------------------------------------------------------

def grass_blades(dump, names, legacy: bool):
    rid = dump['id']
    city = dump['city']
    w, h = dump['w'], dump['h']
    tiles, base, pave = dump['tiles'], dump['base'], dump['pave']
    seed = (java_hash(rid) * 31 + 0x9E3779B9) & 0xFFFFFFFFFFFFFFFF
    if seed >= 1 << 63:
        seed -= 1 << 64
    r = Rnd(seed)
    NO_GRASS = -2
    blades = []

    def plan_for(tx, ty):
        tile = names[tiles[ty][tx]]
        if not legacy:
            p = pave[ty][tx]
            if p == PAVE_STONE:
                return 0, NO_GRASS                    # 돌바닥 틈풀 금지
            if p == PAVE_DIRT:                        # 흙길 잡초는 시골만, 아주 드물게
                return (1, 0) if (not city and r.nextFloat() < 0.07) else (0, NO_GRASS)
            if names[base[ty][tx]] == 'WATER':
                return 0, NO_GRASS                    # 물 위엔 풀이 없다
        if tile == 'GRASS':
            return (2 if r.nextFloat() < 0.35 else 1), -1
        if tile == 'FLOWER':
            return 1, -1
        if tile == 'TALLGRASS':
            return 2 + r.nextInt(2), 2
        if tile == 'REED':
            return 2, 4
        if tile == 'SAND':
            return (0, NO_GRASS) if r.nextFloat() > 0.22 else (1, 3)
        if legacy and tile in ('PATH', 'PLAZA'):      # 이전 규칙 — 돌길/광장 틈새 잡초 10%
            return (0, NO_GRASS) if r.nextFloat() > 0.10 else (1, 0)
        return 0, NO_GRASS

    def pick_kind(tile):
        if tile == 'TALLGRASS':
            return 2 if r.nextFloat() < 0.65 else 1
        if tile == 'REED':
            return 4 if r.nextFloat() < 0.60 else 2
        if tile == 'FLOWER':
            return 0 if r.nextFloat() < 0.45 else 1
        if tile == 'SAND':
            return 3
        if r.nextFloat() < 0.42:
            return 0
        return 1 if r.nextFloat() < 0.78 else 2

    for ty in range(h):
        for tx in range(w):
            tile = names[tiles[ty][tx]]
            cnt, forced = plan_for(tx, ty)
            if forced == NO_GRASS:
                continue
            for _ in range(cnt):
                if r.nextFloat() < 0.55:              # 55% 는 타일 경계에 덩어리로
                    if r.nextBoolean():
                        cx = tx * 32 + r.nextInt(4)
                    else:
                        cx = (tx + 1) * 32 - 1 - r.nextInt(4)
                else:
                    cx = tx * 32 + 1 + r.nextInt(30)
                cy = ty * 32 + 20 + r.nextInt(11)     # 타일 아래쪽에 뿌리
                for _ in range(1 + r.nextInt(2)):
                    kind = forced if (forced >= 0 and r.nextFloat() < 0.74) else pick_kind(tile)
                    bx = cx + r.nextInt(5) - 2
                    by = cy + r.nextInt(4) - 1
                    r.nextFloat()                     # phase01
                    r.nextFloat()                     # stiff
                    blades.append((bx, by, kind))
    return blades


# 풀잎 종류별 색 (입지 확인용 마커)
KIND_RGB = {
    0: (202, 214, 110),   # 잡초 — 연두
    1: (118, 176, 80),    # 보통 풀
    2: (88, 148, 66),     # 억새 — 짙은 풀
    3: (170, 189, 126),   # 갯보리 — 회녹
    4: (128, 144, 82),    # 갈대 — 올리브
}


class Tinter:
    """지역 팔레트 MULTIPLY 틴트를 아트 비트맵에 미리 입혀 캐시한다."""

    def __init__(self, style):
        self.tinted = {}
        self.style = style
        self.fams = {fam: hex_rgb(style[fam]) for fam in ('foliage', 'water', 'shore', 'stone')}

    def art(self, name, idx):
        real = NAME_ALIAS.get(name, name)
        variants = L.ART[real]
        fam = TINT_FAMILY.get(name)
        key = (real, idx % len(variants), fam)
        bmp = self.tinted.get(key)
        if bmp is None:
            src = variants[key[1]]
            b = Bitmap(src.w, src.h)
            if fam is None:
                b.buf[...] = src.buf
            else:
                tr, tg, tb = self.fams[fam]
                b.buf[...] = src.buf
                b.buf[..., 0] = b.buf[..., 0] * tr / 255.0
                b.buf[..., 1] = b.buf[..., 1] * tg / 255.0
                b.buf[..., 2] = b.buf[..., 2] * tb / 255.0
            self.tinted[key] = b
            bmp = b
        return bmp


# ---------------------------------------------------------------------------
# 월드 렌더 — Maps.kt GameMap.draw 와 같은 레이어 순서(지면→포장→데칼→타일→접지 그림자)
# ---------------------------------------------------------------------------

def render_world(dump, style, draw_grass=True, legacy_grass=False):
    render_init()
    names = dump['tileNames']
    w, h = dump['w'], dump['h']
    tiles, base, pave = dump['tiles'], dump['base'], dump['pave']
    deco = dump.get('deco') or [[0] * w for _ in range(h)]
    rid = dump['id']
    tinter = Tinter(style)
    ord_of = {n: i for i, n in enumerate(names)}

    def variant_of(name, x, y):
        vi = art_variant(style, name, x, y, rid, ord_of[name])
        if vi is None:
            vi = tile_variant(NAME_ALIAS.get(name, name), x, y)
        return vi

    out = Bitmap(w * 32, h * 32)

    def paved(x, y):
        return 0 <= x < w and 0 <= y < h and pave[y][x]

    for y in range(h):
        for x in range(w):
            px, py = x * 32, y * 32
            tname = names[tiles[y][x]]
            pv = pave[y][x]
            # 1) 지면 — 포장/소품 아래에 깔린다 (불투명 구조물 아래는 생략)
            if pv or tname in GROUND_TILES or tname in PROP_TILES or tname == 'OVEN':
                bname = names[base[y][x]]
                out.drawBitmap(tinter.art(bname, variant_of(bname, x, y)), px, py)
            # 2) 포장면 (오토타일)
            if pv:
                mask = 0
                if paved(x, y - 1):
                    mask |= R.BIT_N
                if paved(x + 1, y):
                    mask |= R.BIT_E
                if paved(x, y + 1):
                    mask |= R.BIT_S
                if paved(x - 1, y):
                    mask |= R.BIT_W
                if paved(x + 1, y - 1):
                    mask |= R.BIT_NE
                if paved(x + 1, y + 1):
                    mask |= R.BIT_SE
                if paved(x - 1, y + 1):
                    mask |= R.BIT_SW
                if paved(x - 1, y - 1):
                    mask |= R.BIT_NW
                sandy = names[base[y][x]] == 'SAND'
                variant = (y & 1) if pv == PAVE_STONE else ((x * 5 + y * 11) % 3)
                out.drawBitmap(road_tile(pv, mask, variant, sandy), px, py)
            # 3) 데칼 (광장 문양 / 빗물받이)
            d = deco[y][x]
            if d:
                if 1 <= d <= 9:
                    out.drawBitmap(legacy_render.MEDALLION[d - 1], px, py)
                elif d == 10:
                    out.drawBitmap(legacy_render.DRAIN, px, py)
            # 4) 구조물 / 소품
            if tname not in GROUND_TILES and tname != 'OVEN':
                out.drawBitmap(tinter.art(tname, variant_of(tname, x, y)), px, py)

    # 5) 접지 그림자 — 빛은 왼쪽 위에서
    for y in range(h):
        for x in range(w):
            if names[tiles[y][x]] in BULK:
                continue

            def bulk(dx, dy):
                nx, ny = x + dx, y + dy
                if not (0 <= nx < w and 0 <= ny < h):
                    return False
                return names[tiles[ny][nx]] in BULK

            px, py = x * 32, y * 32
            if bulk(0, -1):
                out.drawBitmap(legacy_render.AO[0], px, py)
            if bulk(-1, 0):
                out.drawBitmap(legacy_render.AO[1], px, py)
            if bulk(-1, -1) and not bulk(-1, 0) and not bulk(0, -1):
                out.drawBitmap(legacy_render.AO[2], px, py)

    pil = out.to_pil()
    if draw_grass:
        arr = np.array(pil.convert('RGBA'), dtype=np.uint16)
        fr, fg, fb = hex_rgb(style['foliage'])
        for bx, by, kind in grass_blades(dump, names, legacy_grass):
            cr, cg, cb = KIND_RGB[kind]
            cr = cr * fr // 255
            cg = cg * fg // 255
            cb = cb * fb // 255
            for ddx in (0, 1):                        # 2픽셀 폭 작은 풀다발 마커
                xx = bx + ddx
                for ddy in (-2, -1, 0):
                    yy = by + ddy
                    if 0 <= xx < arr.shape[1] and 0 <= yy < arr.shape[0]:
                        arr[yy, xx, 0] = cr
                        arr[yy, xx, 1] = cg
                        arr[yy, xx, 2] = cb
                        arr[yy, xx, 3] = 255
        pil = Image.fromarray(arr.astype(np.uint8), 'RGBA')
    return pil


def load_dump(path):
    with open(path, encoding='utf-8') as f:
        return json.load(f)


def main():
    ap = argparse.ArgumentParser(description='DumpWorld JSON → 지역 월드맵 PNG 렌더')
    ap.add_argument('dump_dir')
    ap.add_argument('--out', required=True)
    ap.add_argument('regions', nargs='*')
    ap.add_argument('--no-grass', action='store_true', help='풀 오버레이 끄기')
    ap.add_argument('--legacy-grass', action='store_true', help='이전 풀 입지 규칙으로 오버레이 (비교용)')
    ap.add_argument('--scale', type=int, default=1)
    args, extra = ap.parse_known_args()
    args.regions = list(args.regions) + extra

    with open(os.path.join(args.dump_dir, 'styles.json'), encoding='utf-8') as f:
        styles = json.load(f)

    ids = args.regions or sorted(fn[:-5] for fn in os.listdir(args.dump_dir)
                                 if fn.endswith('.json') and fn != 'styles.json')
    os.makedirs(args.out, exist_ok=True)
    for rid in ids:
        dump = load_dump(os.path.join(args.dump_dir, f'{rid}.json'))
        img = render_world(dump, styles[rid], draw_grass=not args.no_grass,
                           legacy_grass=args.legacy_grass)
        if args.scale != 1:
            img = img.resize((img.width * args.scale, img.height * args.scale), Image.NEAREST)
        suffix = '_legacygrass' if args.legacy_grass else ''
        path = os.path.join(args.out, f'{rid}{suffix}.png')
        img.convert('RGB').save(path)
        print(f'rendered {rid} -> {path}')


if __name__ == '__main__':
    main()
