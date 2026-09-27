#!/usr/bin/env python3
"""바위 소품 아트(32x32) 의 설계용 미리보기 포트.

`Assets.kt` 의 `RockArt`(바위 28종) 를 파이썬으로 옮겨 놓은 **설계용** 사본이다.
안드로이드 기기/APK 없이 바위 실루엣·크기·암종 조합을 눈으로 고르기 위한 도구다.
게임을 실제로 그리는 코드는 언제나 `Assets.kt` 쪽이 기준이다.

렌더링 규칙은 게임과 같게 맞춘다.
- 타일 32x32, 배경은 투명(바닥은 GameMap 이 깔아 준다)
- 빛은 **왼쪽 위** — 윗면은 밝게, 오른쪽 아래는 눌러 준다
- 안티에일리어싱 없음 · 1~2px 단위 디더 밴드 그라데이션
"""
from __future__ import annotations

import math

# ── 색 유틸 ────────────────────────────────────────────────────────────
def fade(col: int, a: int) -> int:
    r = (col >> 16) & 0xFF
    g = (col >> 8) & 0xFF
    b = col & 0xFF
    return (max(0, min(255, a)) << 24) | (r << 16) | (g << 8) | b


def shade(col: int, f: float) -> int:
    r = int(((col >> 16) & 0xFF) * f)
    g = int(((col >> 8) & 0xFF) * f)
    b = int((col & 0xFF) * f)
    return 0xFF000000 | (min(255, r) << 16) | (min(255, g) << 8) | min(255, b)


def lerp_col(c0: int, c1: int, t: float) -> int:
    t = max(0.0, min(1.0, t))
    r = int(round(((c0 >> 16) & 0xFF) * (1 - t) + ((c1 >> 16) & 0xFF) * t))
    g = int(round(((c0 >> 8) & 0xFF) * (1 - t) + ((c1 >> 8) & 0xFF) * t))
    b = int(round((c0 & 0xFF) * (1 - t) + (c1 & 0xFF) * t))
    return 0xFF000000 | (r << 16) | (g << 8) | b


class Clip:
    """다각형 내부만 그리는 간단한 클립. 행(y)별 [x0,x1) 스팬을 미리 계산한다."""

    def __init__(self, pts):
        self.pts = pts
        self.rows: dict[int, list[tuple[float, float]]] = {}
        n = len(pts)
        for y in range(0, 33):
            yc = y + 0.5
            hits = []
            for i in range(n):
                x0, y0 = pts[i]
                x1, y1 = pts[(i + 1) % n]
                if (y0 <= yc < y1) or (y1 <= yc < y0):
                    t = (yc - y0) / (y1 - y0)
                    hits.append((x0 + t * (x1 - x0), x0, x1))
            hits.sort()
            for i in range(0, len(hits) - 1, 2):
                self.rows.setdefault(y, []).append((hits[i][0], hits[i + 1][0]))

    def spans(self, y: int):
        return self.rows.get(y, [])

    def contains(self, x: float, y: float) -> bool:
        for yi in range(int(y), int(y) + 1):
            yy = yi + 0.5
            for a, b in self.spans(yi):
                if a <= x < b:
                    return True
        return False

    def rect(self, cv, p, x, y, w, h, col):
        p.color = col
        for yi in range(max(0, int(y)), min(cv.h, int(y + h) + 1)):
            for a, b in self.spans(yi):
                lo = max(a, x)
                hi = min(b, x + w)
                if hi > lo:
                    cv.drawRect(lo, yi, hi, yi + 1, p)

    def dot(self, cv, p, x, y, col):
        if self.contains(x + 0.5, y + 0.5):
            p.color = col
            cv.drawRect(int(x), int(y), int(x) + 1, int(y) + 1, p)

    def line(self, cv, p, x0, y0, x1, y1, col, w=1.0):
        steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
        p.color = col
        for i in range(steps + 1):
            t = i / steps
            x = x0 + (x1 - x0) * t
            y = y0 + (y1 - y0) * t
            self.rect(cv, p, x - w / 2, y - w / 2, w, w, col)

    def oval(self, cv, p, cx, cy, rx, ry, col):
        for yi in range(max(0, int(cy - ry)), min(cv.h, int(cy + ry) + 2)):
            for a, b in self.spans(yi):
                lo = max(a, cx - rx)
                hi = min(b, cx + rx)
                if hi <= lo:
                    continue
                dy = (yi + 0.5) - cy
                if abs(dy) > ry:
                    continue
                k = rx * math.sqrt(max(0.0, 1 - (dy / ry) ** 2))
                l = max(lo, cx - k)
                r = min(hi, cx + k)
                if r > l:
                    p.color = col
                    cv.drawRect(l, yi, r, yi + 1, p)


# ── 도형 ───────────────────────────────────────────────────────────────
def poly(cv, p, col, pts):
    p.color = col
    n = len(pts)
    for yi in range(cv.h):
        yc = yi + 0.5
        hits = []
        for i in range(n):
            x0, y0 = pts[i]
            x1, y1 = pts[(i + 1) % n]
            if (y0 <= yc < y1) or (y1 <= yc < y0):
                t = (yc - y0) / (y1 - y0)
                hits.append(x0 + t * (x1 - x0))
        hits.sort()
        for i in range(0, len(hits) - 1, 2):
            cv.drawRect(hits[i], yi, hits[i + 1], yi + 1, p)


def lump(rnd, cx, cy, rx, ry, n, rough):
    """불규칙 타원 실루엣 정점 (결정적 시드)."""
    out = []
    for i in range(n):
        a = (i / n) * 2 * math.pi
        k = 1.0 + (rnd.nextFloat() - 0.5) * rough
        out.append((cx + math.cos(a) * rx * k, cy + math.sin(a) * ry * k))
    return out


def rng(seed):
    from pixelcanvas import Rnd
    return Rnd(seed)


def shadow(cv, p, cx, cy, rx, ry, a=0x40):
    p.color = fade(0x202C20, a)
    cv.drawOval((cx - rx, cy - ry, cx + rx, cy + ry), p)


# ── 바위 본체 ──────────────────────────────────────────────────────────
def rock_body(cv, p, r, pts, pal, bands=5, speck=0, speck_col=None, rim=True,
              top_band=0.15):
    """실루엣 + 윗빛/아랫그늘 디더 그라데이션 + 표면 알갱이 + 어두운 테두리.

    pal = [그늘, 중간, 빛, 하이라이트]
    """
    clip = Clip(pts)
    minx = min(q[0] for q in pts)
    maxx = max(q[0] for q in pts)
    miny = min(q[1] for q in pts)
    maxy = max(q[1] for q in pts)
    h = max(1.0, maxy - miny)
    w = max(1.0, maxx - minx)
    for yi in range(cv.h):
        t = (yi + 0.5 - miny) / h
        if t < top_band:
            col = lerp_col(pal[3], pal[2], t / top_band)
            k = 0.0
        else:
            k = (t - top_band) / (1 - top_band)
            col = lerp_col(pal[2], pal[0], k)
        # 밴드 경계는 1/2 확률로 디더 → 계단질이 픽셀아트처럼 읽힌다
        frac = k * bands
        bi = int(frac)
        if frac - bi < 0.5 and bi < bands - 1 and (yi % 2 == 0):
            col = lerp_col(pal[2], pal[0], (bi + 1) / bands)
        for a, b in clip.spans(yi):
            p.color = col
            cv.drawRect(a, yi, b, yi + 1, p)
    # 왼쪽 위 능선 하이라이트
    clip.oval(cv, p, minx + w * 0.34, miny + h * 0.22, w * 0.26, h * 0.14, fade(pal[3], 78))
    # 오른쪽 아래 그늘
    clip.oval(cv, p, maxx - w * 0.22, maxy - h * 0.2, w * 0.36, h * 0.3, fade(pal[0], 120))
    if speck:
        sc = speck_col if speck_col is not None else shade(pal[0], 0.86)
        for _ in range(speck):
            x = minx + r.nextFloat() * w
            y = miny + r.nextFloat() * h
            s = 1.0 + r.nextFloat() * 1.6
            clip.rect(cv, p, x, y, s, s * 0.85, sc)
    if rim:
        # 빛이 왼쪽 위에서 오므로 테두리는 오른쪽·아래에만 진다
        dark = fade(pal[0], 150)
        for yi in range(cv.h):
            spans = clip.spans(yi)
            if not spans:
                continue
            p.color = dark
            cv.drawRect(spans[-1][1] - 0.9, yi, spans[-1][1], yi + 1, p)
        last = max(clip.rows)
        for a, b in clip.spans(last):
            p.color = dark
            cv.drawRect(a, last, b, last + 1, p)
    return clip


def crack(clip, cv, p, x, y, dx, dy, col, steps=4, w=1.0):
    for i in range(steps):
        clip.rect(cv, p, x + dx * i, y + dy * i, w, w, col)


def band(clip, cv, p, x, y, w, h, col):
    clip.rect(cv, p, x, y, w, h, col)


def raw(cv, p, x, y, w, h, col):
    p.color = col
    cv.drawRect(x, y, x + w, y + h, p)


def moss(clip, cv, p, x, y, w, h, base=0xFF6FAE57, lite=0xFF8CC46C):
    clip.rect(cv, p, x, y, w, h, base)
    clip.rect(cv, p, x + 0.6, y + 0.6, w * 0.55, h * 0.4, lite)


def fringe(clip, cv, p, x, y, w, h, col):
    for i in range(int(w)):
        clip.rect(cv, p, x + i, y + (i % 2) * 1.2, 1, h, col)
