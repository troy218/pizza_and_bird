#!/usr/bin/env python3
"""Roads.kt 프로토타입 — 길(도로) 아트 시스템.

Kotlin 으로 1:1 이식하기 쉽도록 픽셀 버퍼 + java.util.Random 만 사용한다.
"""
from __future__ import annotations

import math

from pixelcanvas import Bitmap, Rnd, bitmap_from_pixels

# ---------------------------------------------------------------------------
# 포장 재질
# ---------------------------------------------------------------------------
PAVE_NONE = 0
PAVE_DIRT = 1    # 흙길 — 마을 도로 / 샛길
PAVE_STONE = 2   # 석재 포장 — 광장

# 방향 비트 (이웃이 "포장면" 인가)
BIT_N, BIT_E, BIT_S, BIT_W = 1, 2, 4, 8
BIT_NE, BIT_SE, BIT_SW, BIT_NW = 16, 32, 64, 128


class PixBuf:
    def __init__(self, w: int, h: int):
        self.w = w
        self.h = h
        self.px = [0] * (w * h)

    def set(self, x: int, y: int, col: int):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = col

    def get(self, x: int, y: int) -> int:
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.px[y * self.w + x]
        return 0

    def rect(self, x0: int, y0: int, x1: int, y1: int, col: int):
        for y in range(max(0, y0), min(self.h, y1)):
            for x in range(max(0, x0), min(self.w, x1)):
                self.px[y * self.w + x] = col

    def blend(self, x: int, y: int, col: int, a: float):
        """이미 칠해진 픽셀 위에만 알파 합성 (투명 영역은 건드리지 않음)."""
        if not (0 <= x < self.w and 0 <= y < self.h):
            return
        dst = self.px[y * self.w + x]
        if (dst >> 24) & 0xFF == 0:
            return
        sr, sg, sb = (col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF
        dr, dg, db = (dst >> 16) & 0xFF, (dst >> 8) & 0xFF, dst & 0xFF
        r = int(dr + (sr - dr) * a)
        g = int(dg + (sg - dg) * a)
        b = int(db + (sb - db) * a)
        self.px[y * self.w + x] = (0xFF << 24) | (r << 16) | (g << 8) | b

    def blend_rect(self, x0, y0, x1, y1, col, a):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.blend(x, y, col, a)

    def to_bitmap(self) -> Bitmap:
        return bitmap_from_pixels(self.px, self.w, self.h)


def mix(c0: int, c1: int, t: float) -> int:
    r = int(((c0 >> 16) & 0xFF) + (((c1 >> 16) & 0xFF) - ((c0 >> 16) & 0xFF)) * t)
    g = int(((c0 >> 8) & 0xFF) + (((c1 >> 8) & 0xFF) - ((c0 >> 8) & 0xFF)) * t)
    b = int((c0 & 0xFF) + ((c1 & 0xFF) - (c0 & 0xFF)) * t)
    return (0xFF << 24) | (r << 16) | (g << 8) | b


# ---------------------------------------------------------------------------
# 팔레트
# ---------------------------------------------------------------------------
# 흙길 — 잔디(#96D07A)/모래(#F2E1B0) 사이에서 또렷하게 읽히는 따뜻한 황토색
DIRT_BASE = 0xFFD6B983
DIRT_LIGHT = 0xFFE4CE9E
DIRT_PALE = 0xFFF0E2BE
DIRT_DARK = 0xFFBE9F6C
DIRT_DEEP = 0xFFA58555
DIRT_RIM = 0xFF93764B
DIRT_PEBBLE = 0xFFC6C0AC
DIRT_PEBBLE_HI = 0xFFE2DCC8

# 석재 포장 — 차분한 회베이지 화강암
STONE_BASE = 0xFFCFC5AC
STONE_LIGHT = 0xFFDED5BE
STONE_PALE = 0xFFEAE2CE
STONE_DARK = 0xFFB9AE93
STONE_DEEP = 0xFFA1957A
STONE_MORTAR = 0xFFA79B7E
STONE_RIM = 0xFF8E8268
STONE_MOSS = 0xFF9FB081
CURB_TOP = 0xFFE3DAC4
CURB_SIDE = 0xFFB3A88D

GRASS_TUFT = 0xFF7FBE64
GRASS_TUFT2 = 0xFF6FAE57
SAND_TUFT = 0xFFE9D6A4


def _seed_of(mat: int, mask: int, variant: int, sandy: bool) -> int:
    return (mat * 7717 + mask * 131 + variant * 29 + (1013 if sandy else 0)) & 0x7FFFFFFF


# ---------------------------------------------------------------------------
# 실루엣 (오토타일)
# ---------------------------------------------------------------------------

def _profile(r: Rnd, mat: int, n: int) -> list[int]:
    """가장자리 안쪽 여백 프로파일.

    타일 경계(첫/끝 2픽셀)는 항상 기준값으로 고정한다 -> 옆 타일과 윤곽선이 딱 맞는다.
    가운데는 흙길이면 들쭉날쭉하게, 석재면 반듯하게.
    """
    base = 1 if mat == PAVE_STONE else 2
    out = [base] * n
    if mat == PAVE_STONE:
        return out
    v = base
    for i in range(2, n - 2):
        remain = (n - 3) - i
        if remain <= abs(v - base):
            v += 1 if v < base else -1
        elif r.nextInt(3) == 0:
            v += 1 if r.nextBoolean() else -1
            v = max(base - 1, min(base + 1, v))
        out[i] = v
    return out


def build_silhouette(mat: int, mask: int, r: Rnd):
    n = (mask & BIT_N) != 0
    e = (mask & BIT_E) != 0
    s = (mask & BIT_S) != 0
    w = (mask & BIT_W) != 0
    ne = (mask & BIT_NE) != 0
    se = (mask & BIT_SE) != 0
    sw = (mask & BIT_SW) != 0
    nw = (mask & BIT_NW) != 0

    pN = _profile(r, mat, 32)
    pS = _profile(r, mat, 32)
    pW = _profile(r, mat, 32)
    pE = _profile(r, mat, 32)

    outer = 5.5 if mat == PAVE_DIRT else 3.5   # 바깥 모서리 둥글기
    inner = 5.0 if mat == PAVE_DIRT else 3.0   # 안쪽 모서리 필렛

    sol = [[False] * 32 for _ in range(32)]
    for y in range(32):
        for x in range(32):
            ok = True
            if not n and y < pN[x]:
                ok = False
            if not s and (31 - y) < pS[x]:
                ok = False
            if not w and x < pW[y]:
                ok = False
            if not e and (31 - x) < pE[y]:
                ok = False
            if ok:
                # 바깥 모서리 둥글리기
                if not n and not w and x < outer and y < outer:
                    if (x - outer) ** 2 + (y - outer) ** 2 > outer * outer:
                        ok = False
                if not n and not e and (31 - x) < outer and y < outer:
                    if ((31 - x) - outer) ** 2 + (y - outer) ** 2 > outer * outer:
                        ok = False
                if not s and not w and x < outer and (31 - y) < outer:
                    if (x - outer) ** 2 + ((31 - y) - outer) ** 2 > outer * outer:
                        ok = False
                if not s and not e and (31 - x) < outer and (31 - y) < outer:
                    if ((31 - x) - outer) ** 2 + ((31 - y) - outer) ** 2 > outer * outer:
                        ok = False
                # 안쪽 모서리 필렛 (대각 이웃만 비어 있을 때)
                if n and w and not nw and x * x + y * y < inner * inner:
                    ok = False
                if n and e and not ne and (31 - x) ** 2 + y * y < inner * inner:
                    ok = False
                if s and w and not sw and x * x + (31 - y) ** 2 < inner * inner:
                    ok = False
                if s and e and not se and (31 - x) ** 2 + (31 - y) ** 2 < inner * inner:
                    ok = False
            sol[y][x] = ok
    return sol


def _edge_distance(sol):
    """각 픽셀에서 포장면 경계까지의 체비셰프 거리(0 = 경계 픽셀)."""
    INF = 99
    d = [[INF] * 32 for _ in range(32)]
    for y in range(32):
        for x in range(32):
            if not sol[y][x]:
                continue
            border = False
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    nx, ny = x + dx, y + dy
                    if nx < 0 or ny < 0 or nx > 31 or ny > 31:
                        continue
                    if not sol[ny][nx]:
                        border = True
            d[y][x] = 0 if border else INF
    # 간단한 BFS 확장
    for it in range(1, 5):
        for y in range(32):
            for x in range(32):
                if not sol[y][x] or d[y][x] < INF:
                    continue
                near = False
                for dy in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        nx, ny = x + dx, y + dy
                        if 0 <= nx < 32 and 0 <= ny < 32 and d[ny][nx] == it - 1:
                            near = True
                if near:
                    d[y][x] = it
    return d


# ---------------------------------------------------------------------------
# 표면 텍스처
# ---------------------------------------------------------------------------

def paint_dirt(buf: PixBuf, sol, r: Rnd, mask: int, variant: int):
    n = (mask & BIT_N) != 0
    e = (mask & BIT_E) != 0
    s = (mask & BIT_S) != 0
    w = (mask & BIT_W) != 0

    for y in range(32):
        for x in range(32):
            if sol[y][x]:
                buf.set(x, y, DIRT_BASE)

    # 넓은 얼룩 (다짐 정도 차이)
    for _ in range(9):
        cx = r.nextInt(32)
        cy = r.nextInt(32)
        rad = 3 + r.nextInt(5)
        col = DIRT_LIGHT if r.nextBoolean() else DIRT_DARK
        for y in range(cy - rad, cy + rad + 1):
            for x in range(cx - rad, cx + rad + 1):
                if 0 <= x < 32 and 0 <= y < 32 and sol[y][x]:
                    dx = x - cx
                    dy = (y - cy) * 1.35
                    if dx * dx + dy * dy <= rad * rad:
                        buf.blend(x, y, col, 0.45)

    # 바퀴 자국 — 길의 방향에 따라
    vertical = n and s and not (e and w)
    horizontal = e and w and not (n and s)
    if vertical:
        if e and not w:
            centers = [21.5]
        elif w and not e:
            centers = [10.5]
        else:
            centers = [16.0]          # 1칸 폭 오솔길 — 가운데 한 줄
        for cxf in centers:
            _rut_vertical(buf, sol, r, cxf, single=(not e and not w))
    elif horizontal:
        if s and not n:
            centers = [21.5]
        elif n and not s:
            centers = [10.5]
        else:
            centers = [16.0]
        for cyf in centers:
            _rut_horizontal(buf, sol, r, cyf, single=(not n and not s))

    # 자갈/모래알
    for _ in range(26):
        x = r.nextInt(32)
        y = r.nextInt(32)
        if sol[y][x]:
            buf.blend(x, y, DIRT_PALE if r.nextBoolean() else DIRT_DARK, 0.55)
    # 박힌 돌멩이
    for _ in range(3):
        x = 2 + r.nextInt(27)
        y = 2 + r.nextInt(27)
        if not sol[y][x]:
            continue
        if sol[y][x]:
            buf.set(x, y, DIRT_PEBBLE)
        if sol[y][x + 1] if x + 1 < 32 else False:
            buf.set(x + 1, y, DIRT_PEBBLE_HI)
        if sol[y + 1][x] if y + 1 < 32 else False:
            buf.set(x, y + 1, DIRT_DEEP)


def _rut_vertical(buf: PixBuf, sol, r: Rnd, cxf: float, single: bool):
    half = 1 if single else 2
    core = DIRT_DARK if single else DIRT_DEEP
    strength = 0.30 if single else 0.55
    off = 0
    for y in range(32):
        if r.nextInt(5) == 0:
            off += 1 if r.nextBoolean() else -1
            off = max(-1, min(1, off))
        cx = int(cxf + off)
        for x in range(cx - half, cx + half + 1):
            if 0 <= x < 32 and sol[y][x]:
                edge = abs(x - cx) == half
                buf.blend(x, y, core, strength * (0.45 if edge else 1.0))
        if not single and 0 <= cx + half + 1 < 32 and sol[y][cx + half + 1]:
            buf.blend(cx + half + 1, y, DIRT_LIGHT, 0.35)


def _rut_horizontal(buf: PixBuf, sol, r: Rnd, cyf: float, single: bool):
    half = 1 if single else 2
    core = DIRT_DARK if single else DIRT_DEEP
    strength = 0.30 if single else 0.55
    off = 0
    for x in range(32):
        if r.nextInt(5) == 0:
            off += 1 if r.nextBoolean() else -1
            off = max(-1, min(1, off))
        cy = int(cyf + off)
        for y in range(cy - half, cy + half + 1):
            if 0 <= y < 32 and sol[y][x]:
                edge = abs(y - cy) == half
                buf.blend(x, y, core, strength * (0.45 if edge else 1.0))
        if not single and 0 <= cy + half + 1 < 32 and sol[cy + half + 1][x]:
            buf.blend(x, cy + half + 1, DIRT_LIGHT, 0.35)


def paint_stone(buf: PixBuf, sol, r: Rnd, mask: int, variant: int):
    """줄눈이 어긋난 장방형 판석 (러닝본드) — variant 로 단 어긋남을 이어붙인다."""
    for y in range(32):
        for x in range(32):
            if sol[y][x]:
                buf.set(x, y, STONE_MORTAR)

    row_base = variant * 4
    for row in range(4):
        y0 = row * 8
        grow = row_base + row
        offset = 8 if (grow % 2) else 0
        bx = -offset
        col_i = 0
        while bx < 32:
            x0 = bx
            x1 = bx + 16
            tone = (grow * 5 + col_i * 3 + (x0 // 16)) % 4
            base = [STONE_BASE, STONE_LIGHT, mix(STONE_BASE, STONE_DARK, 0.35), mix(STONE_LIGHT, STONE_PALE, 0.5)][tone]
            if (grow * 7 + col_i * 11) % 23 == 0:
                base = mix(base, STONE_MOSS, 0.16)
            for y in range(y0 + 1, y0 + 8):
                for x in range(x0 + 1, x1):
                    if 0 <= x < 32 and 0 <= y < 32 and sol[y][x]:
                        buf.set(x, y, base)
            # 판석 윗면 하이라이트 / 아랫면 그림자
            for x in range(x0 + 1, x1):
                if 0 <= x < 32 and 0 <= y0 + 1 < 32 and sol[y0 + 1][x]:
                    buf.blend(x, y0 + 1, STONE_PALE, 0.40)
                if 0 <= x < 32 and y0 + 7 < 32 and sol[y0 + 7][x]:
                    buf.blend(x, y0 + 7, STONE_DEEP, 0.30)
            bx += 16
            col_i += 1

    # 풍화 — 잔금과 얼룩
    for _ in range(3):
        x = 1 + r.nextInt(29)
        y = 1 + r.nextInt(29)
        ln = 2 + r.nextInt(4)
        for k in range(ln):
            xx = x + k
            yy = y + (k // 2)
            if 0 <= xx < 32 and 0 <= yy < 32 and sol[yy][xx]:
                buf.blend(xx, yy, STONE_DEEP, 0.30)
    for _ in range(18):
        x = r.nextInt(32)
        y = r.nextInt(32)
        if sol[y][x]:
            buf.blend(x, y, STONE_PALE if r.nextBoolean() else STONE_DARK, 0.30)


# ---------------------------------------------------------------------------
# 가장자리 마감
# ---------------------------------------------------------------------------

def paint_edges(buf: PixBuf, sol, r: Rnd, mat: int, mask: int, sandy: bool):
    d = _edge_distance(sol)
    rim = DIRT_RIM if mat == PAVE_DIRT else STONE_RIM
    for y in range(32):
        for x in range(32):
            if not sol[y][x]:
                continue
            dd = d[y][x]
            if mat == PAVE_STONE:
                # 연석(curb): 바깥 1px 밝은 윗면 + 안쪽 1px 그늘
                if dd == 0:
                    buf.set(x, y, CURB_TOP)
                elif dd == 1:
                    buf.set(x, y, CURB_SIDE)
                elif dd == 2:
                    buf.blend(x, y, STONE_DEEP, 0.35)
            else:
                if dd == 0:
                    buf.blend(x, y, rim, 0.62)
                elif dd == 1:
                    buf.blend(x, y, rim, 0.30)
                elif dd == 2:
                    buf.blend(x, y, rim, 0.12)

    if mat == PAVE_DIRT:
        # 길섶 — 잔디가 살짝 덮어온 느낌
        tuft = SAND_TUFT if sandy else GRASS_TUFT
        tuft2 = SAND_TUFT if sandy else GRASS_TUFT2
        for _ in range(7):
            x = r.nextInt(32)
            y = r.nextInt(32)
            if not sol[y][x] or d[y][x] > 1:
                continue
            buf.set(x, y, tuft)
            if r.nextBoolean() and y + 1 < 32 and sol[y + 1][x]:
                buf.set(x, y + 1, tuft2)
        # 길가에 밀려난 잔자갈
        for _ in range(6):
            x = r.nextInt(32)
            y = r.nextInt(32)
            if sol[y][x] and d[y][x] == 2:
                buf.blend(x, y, DIRT_PALE, 0.7)


def road_tile(mat: int, mask: int, variant: int, sandy: bool) -> Bitmap:
    r = Rnd(_seed_of(mat, mask, variant, sandy))
    sol = build_silhouette(mat, mask, r)
    buf = PixBuf(32, 32)
    if mat == PAVE_STONE:
        paint_stone(buf, sol, r, mask, variant)
    else:
        paint_dirt(buf, sol, r, mask, variant)
    paint_edges(buf, sol, r, mat, mask, sandy)
    return buf.to_bitmap()


# ---------------------------------------------------------------------------
# 데칼 (광장 문양 / 빗물받이)
# ---------------------------------------------------------------------------
DECAL_NONE = 0
DECAL_MEDAL_TL = 1
DECAL_MEDAL_TR = 2
DECAL_MEDAL_BL = 3
DECAL_MEDAL_BR = 4
DECAL_DRAIN = 5


def build_medallion(n: int = 3) -> list[Bitmap]:
    """광장 한가운데 n x n 문양 — 팔방 나침반 (Canvas/Path 로 또렷하게)."""
    from pixelcanvas import Canvas, Paint, Path

    size = n * 32
    b = Bitmap(size, size)
    p = Paint()
    cx = cy = size / 2.0
    k = size / 64.0
    p.color = STONE_RIM
    b.drawCircle(cx, cy, 28 * k, p)
    p.color = CURB_TOP
    b.drawCircle(cx, cy, 26.5 * k, p)
    p.color = STONE_RIM
    b.drawCircle(cx, cy, 23.5 * k, p)
    p.color = mix(STONE_LIGHT, STONE_PALE, 0.5)
    b.drawCircle(cx, cy, 22.0 * k, p)

    def ray(ang: float, length: float, halfw: float, light: bool):
        ca, sa = math.cos(ang), math.sin(ang)
        nx, ny = -sa, ca
        path = Path()
        path.moveTo(cx + ca * length, cy + sa * length)
        path.lineTo(cx + nx * halfw, cy + ny * halfw)
        path.lineTo(cx - nx * halfw, cy - ny * halfw)
        p.color = STONE_DARK if light else STONE_DEEP
        b.drawPath(path, p)

    for i in range(4):
        a = i * math.pi / 2
        ray(a, 21.0 * k, 4.2 * k, i % 2 == 0)
    for i in range(4):
        a = i * math.pi / 2 + math.pi / 4
        ray(a, 13.0 * k, 3.0 * k, i % 2 == 1)

    p.color = STONE_RIM
    b.drawCircle(cx, cy, 5.2 * k, p)
    p.color = CURB_TOP
    b.drawCircle(cx, cy, 3.6 * k, p)

    out = []
    for qy in range(n):
        for qx in range(n):
            q = Bitmap(32, 32)
            q.buf[:, :] = b.buf[qy * 32: qy * 32 + 32, qx * 32: qx * 32 + 32]
            out.append(q)
    return out


def build_drain() -> Bitmap:
    buf = PixBuf(32, 32)
    buf.rect(9, 11, 23, 21, 0xFF6E6A5E)
    buf.rect(10, 12, 22, 20, 0xFF4A4740)
    for i in range(4):
        buf.rect(11 + i * 3, 13, 13 + i * 3, 19, 0xFF8A8579)
    buf.rect(9, 11, 23, 12, 0xFF8F8A7C)
    buf.rect(9, 20, 23, 21, 0xFF35332E)
    return buf.to_bitmap()


# ---------------------------------------------------------------------------
# 접지 그림자 (건물/나무가 바닥에 드리우는 그늘)
# ---------------------------------------------------------------------------

def build_ao() -> list[Bitmap]:
    """건물/산이 바닥에 드리우는 그림자 [윗변, 왼쪽변, 왼쪽위 모서리].

    빛은 왼쪽 위에서 온다 — 구조물의 남/동쪽 바닥에만 그늘이 생긴다.
    """
    out = []
    depth = 8

    def side(vertical: bool) -> Bitmap:
        b = Bitmap(32, 32)
        for y in range(32):
            for x in range(32):
                t = y if vertical else x
                if t < depth:
                    a = (1.0 - t / depth) ** 1.8 * 80
                    b.buf[y, x] = (16, 24, 18, a)
        return b

    def corner() -> Bitmap:
        b = Bitmap(32, 32)
        for y in range(32):
            for x in range(32):
                d = math.sqrt(x * x + y * y)
                if d < depth:
                    a = (1.0 - d / depth) ** 1.8 * 72
                    b.buf[y, x] = (16, 24, 18, a)
        return b

    out.append(side(True))     # 위쪽에 구조물
    out.append(side(False))    # 왼쪽에 구조물
    out.append(corner())       # 왼쪽 위 대각
    return out
