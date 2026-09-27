#!/usr/bin/env python3
"""미리보기 렌더러 — base/pave/decal/tile 4개 레이어를 실제 게임과 같은 순서로 그린다."""
from __future__ import annotations

import tiles_legacy as L
import roads as R
from pixelcanvas import Bitmap

MEDALLION = None
DRAIN = None
AO = None
_road_cache: dict[tuple, Bitmap] = {}


def init():
    global MEDALLION, DRAIN, AO
    if MEDALLION is None:
        MEDALLION = R.build_medallion()
        DRAIN = R.build_drain()
        AO = R.build_ao()


def road_tile(mat, mask, variant, sandy):
    key = (mat, mask, variant, sandy)
    b = _road_cache.get(key)
    if b is None:
        b = R.road_tile(mat, mask, variant, sandy)
        _road_cache[key] = b
    return b


def tile_variant(name, x, y):
    n = len(L.ART[name])
    if n <= 1:
        return 0
    return ((x * 7 + y * 13) % n + n) % n


SOLID = {'WATER', 'TREE', 'ROCK', 'MOUNTAIN', 'BLDG_WALL', 'BLDG_WIN', 'BLDG_ROOF',
         'HOUSE_ROOF', 'HOUSE_WALL', 'HOUSE_WIN', 'WALL_IN', 'WALL_WIN', 'OVEN', 'BED', 'BOX',
         'SIGN', 'BENCH', 'LAMP'}
OVERLAY = {'SIGN', 'BENCH', 'LAMP'}       # 바닥을 깔고 그 위에 얹는 소품
GROUNDS = {'GRASS', 'TALLGRASS', 'FLOWER', 'SAND', 'WATER', 'REED', 'PATH', 'PLAZA', 'FLOOR'}


def render(grid, base, pave, decal, w, h, scale=1) -> Bitmap:
    """grid/base/pave/decal: [y][x] 문자열 또는 정수 레이어."""
    init()
    out = Bitmap(w * 32, h * 32)

    def paved(x, y):
        if x < 0 or y < 0 or x >= w or y >= h:
            return 0
        return pave[y][x]

    for y in range(h):
        for x in range(w):
            px, py = x * 32, y * 32
            t = grid[y][x]
            pv = pave[y][x]
            # 1) 바닥
            if pv or t in OVERLAY or t in GROUNDS:
                bn = base[y][x]
                out.drawBitmap(L.ART[bn][tile_variant(bn, x, y)], px, py)
            # 2) 포장
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
                sandy = base[y][x] == 'SAND'
                variant = (y & 1) if pv == R.PAVE_STONE else ((x * 5 + y * 11) % 3)
                out.drawBitmap(road_tile(pv, mask, variant, sandy), px, py)
            # 3) 데칼
            d = decal[y][x]
            if d:
                if 1 <= d <= 9:
                    out.drawBitmap(MEDALLION[d - 1], px, py)
                elif d == 10:
                    out.drawBitmap(DRAIN, px, py)
            # 4) 타일 본체
            if t in OVERLAY:
                out.drawBitmap(L.ART[t][0], px, py)
            elif not pv and t not in ('PATH', 'PLAZA'):
                out.drawBitmap(L.ART[t][tile_variant(t, x, y)], px, py)

    # 5) 접지 그림자 — 빛은 왼쪽 위에서 (구조물의 아래/오른쪽 바닥에만 그늘)
    BULK = {'BLDG_WALL', 'BLDG_WIN', 'BLDG_ROOF', 'HOUSE_ROOF', 'HOUSE_WALL', 'HOUSE_WIN',
            'MOUNTAIN', 'WALL_IN', 'WALL_WIN', 'TUNNEL'}
    for y in range(h):
        for x in range(w):
            if grid[y][x] in BULK:
                continue
            px, py = x * 32, y * 32

            def bulk(dx, dy):
                nx, ny = x + dx, y + dy
                if nx < 0 or ny < 0 or nx >= w or ny >= h:
                    return False
                return grid[ny][nx] in BULK
            if bulk(0, -1):
                out.drawBitmap(AO[0], px, py)
            if bulk(-1, 0):
                out.drawBitmap(AO[1], px, py)
            if bulk(-1, -1) and not bulk(-1, 0) and not bulk(0, -1):
                out.drawBitmap(AO[2], px, py)
    return out
