#!/usr/bin/env python3
"""android.graphics 흉내 — 픽셀 단위로 동일한 결과를 내는 미리보기용 캔버스.

게임 아트는 전부 코드로 그려지므로(Assets.kt / Roads.kt), 이 얇은 셰이드를 통해
안드로이드 기기 없이도 PNG로 결과를 확인할 수 있다.

- java.util.Random 과 동일한 LCG (같은 시드 -> 같은 노이즈)
- Canvas.drawRect/drawCircle/drawPath 의 안티에일리어싱 없는 픽셀 규칙(픽셀 중심 샘플링)
"""
from __future__ import annotations

import math

import numpy as np

MASK48 = (1 << 48) - 1
MULT = 0x5DEECE66D
ADD = 0xB


class Rnd:
    """java.util.Random 호환 난수."""

    def __init__(self, seed: int):
        self.seed = (seed ^ MULT) & MASK48

    def _next(self, bits: int) -> int:
        self.seed = (self.seed * MULT + ADD) & MASK48
        v = self.seed >> (48 - bits)
        if bits == 32 and v >= (1 << 31):
            v -= 1 << 32
        return v

    def nextInt(self, bound: int | None = None) -> int:
        if bound is None:
            return self._next(32)
        if bound <= 0:
            raise ValueError("bound must be positive")
        if (bound & -bound) == bound:
            return (bound * self._next(31)) >> 31
        while True:
            bits = self._next(31)
            val = bits % bound
            if bits - val + (bound - 1) < (1 << 31):
                return val

    def nextFloat(self) -> float:
        return self._next(24) / float(1 << 24)

    def nextDouble(self) -> float:
        return ((self._next(26) << 27) + self._next(27)) * (2.0 ** -53)

    def nextBoolean(self) -> bool:
        return self._next(1) != 0


def argb(v: int):
    return ((v >> 24) & 0xFF, (v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF)


class Paint:
    def __init__(self, color: int = 0xFF000000):
        self.color = color
        self.alpha = 255

    def rgba(self):
        a, r, g, b = argb(self.color)
        return r, g, b, int(a * self.alpha / 255)


class Path:
    def __init__(self):
        self.pts: list[tuple[float, float]] = []

    def reset(self):
        self.pts = []

    def moveTo(self, x, y):
        self.pts = [(x, y)]

    def lineTo(self, x, y):
        self.pts.append((x, y))

    def close(self):
        pass


class Canvas:
    """RGBA(float 누적 없이 8bit) 버퍼 위에 그리는 단순 래스터라이저."""

    def __init__(self, w: int, h: int):
        self.w = w
        self.h = h
        self.buf = np.zeros((h, w, 4), dtype=np.float32)

    # -- 내부 -----------------------------------------------------------
    def _blend(self, xs, ys, rgba):
        if len(xs) == 0 or len(ys) == 0:
            return
        r, g, b, a = rgba
        if a <= 0:
            return
        al = a / 255.0
        region = self.buf[np.ix_(ys, xs)]
        src = np.array([r, g, b, 255.0], dtype=np.float32)
        region[..., :3] = region[..., :3] * (1 - al) + src[:3] * al
        region[..., 3] = region[..., 3] * (1 - al) + 255.0 * al
        self.buf[np.ix_(ys, xs)] = region

    def _span(self, lo: float, hi: float, limit: int):
        a = int(math.ceil(lo - 0.5))
        b = int(math.ceil(hi - 0.5))
        a = max(a, 0)
        b = min(b, limit)
        return np.arange(a, b)

    # -- 공개 API --------------------------------------------------------
    def drawColor(self, color: int):
        self.drawRect(0, 0, self.w, self.h, Paint(color))

    def drawRect(self, l, t, r, b, paint: Paint):
        self._blend(self._span(l, r, self.w), self._span(t, b, self.h), paint.rgba())

    def drawCircle(self, cx, cy, radius, paint: Paint):
        xs = self._span(cx - radius, cx + radius, self.w)
        ys = self._span(cy - radius, cy + radius, self.h)
        if len(xs) == 0 or len(ys) == 0:
            return
        gx = xs[None, :] + 0.5 - cx
        gy = ys[:, None] + 0.5 - cy
        m = (gx * gx + gy * gy) <= radius * radius
        r, g, b, a = paint.rgba()
        al = a / 255.0
        region = self.buf[np.ix_(ys, xs)]
        src = np.array([r, g, b], dtype=np.float32)
        region[..., :3] = np.where(m[..., None], region[..., :3] * (1 - al) + src * al, region[..., :3])
        region[..., 3] = np.where(m, region[..., 3] * (1 - al) + 255.0 * al, region[..., 3])
        self.buf[np.ix_(ys, xs)] = region

    def drawOval(self, rect, paint: Paint):
        l, t, r, b = rect
        cx = (l + r) / 2
        cy = (t + b) / 2
        rx = (r - l) / 2
        ry = (b - t) / 2
        xs = self._span(l, r, self.w)
        ys = self._span(t, b, self.h)
        if len(xs) == 0 or len(ys) == 0 or rx <= 0 or ry <= 0:
            return
        gx = (xs[None, :] + 0.5 - cx) / rx
        gy = (ys[:, None] + 0.5 - cy) / ry
        m = (gx * gx + gy * gy) <= 1.0
        rr, gg, bb, aa = paint.rgba()
        al = aa / 255.0
        region = self.buf[np.ix_(ys, xs)]
        src = np.array([rr, gg, bb], dtype=np.float32)
        region[..., :3] = np.where(m[..., None], region[..., :3] * (1 - al) + src * al, region[..., :3])
        region[..., 3] = np.where(m, region[..., 3] * (1 - al) + 255.0 * al, region[..., 3])
        self.buf[np.ix_(ys, xs)] = region

    def drawPath(self, path: Path, paint: Paint):
        pts = path.pts
        if len(pts) < 3:
            return
        ys = np.arange(0, self.h)
        for y in ys:
            yc = y + 0.5
            xs_hit = []
            n = len(pts)
            for i in range(n):
                x0, y0 = pts[i]
                x1, y1 = pts[(i + 1) % n]
                if (y0 <= yc < y1) or (y1 <= yc < y0):
                    t = (yc - y0) / (y1 - y0)
                    xs_hit.append(x0 + t * (x1 - x0))
            xs_hit.sort()
            for i in range(0, len(xs_hit) - 1, 2):
                self._blend(self._span(xs_hit[i], xs_hit[i + 1], self.w), np.array([y]), paint.rgba())

    def drawBitmap(self, bmp: "Bitmap", x: float, y: float, paint: Paint | None = None):
        ix = int(round(x))
        iy = int(round(y))
        sx0 = max(0, -ix)
        sy0 = max(0, -iy)
        sx1 = min(bmp.w, self.w - ix)
        sy1 = min(bmp.h, self.h - iy)
        if sx1 <= sx0 or sy1 <= sy0:
            return
        src = bmp.buf[sy0:sy1, sx0:sx1]
        dst = self.buf[iy + sy0: iy + sy1, ix + sx0: ix + sx1]
        al = (src[..., 3:4] / 255.0)
        if paint is not None and paint.alpha < 255:
            al = al * (paint.alpha / 255.0)
        dst[..., :3] = dst[..., :3] * (1 - al) + src[..., :3] * al
        dst[..., 3] = np.maximum(dst[..., 3], src[..., 3])


class Bitmap(Canvas):
    """Canvas 를 그대로 쓰되 비트맵으로도 취급."""

    def __init__(self, w: int, h: int):
        super().__init__(w, h)

    def setPixel(self, x, y, color):
        if 0 <= x < self.w and 0 <= y < self.h:
            a, r, g, b = argb(color)
            self.buf[y, x] = (r, g, b, a)

    def to_pil(self):
        from PIL import Image

        return Image.fromarray(np.clip(self.buf, 0, 255).astype(np.uint8), "RGBA")

    def save(self, path, scale=1):
        img = self.to_pil()
        if scale != 1:
            img = img.resize((self.w * scale, self.h * scale), 0)
        img.save(path)


def bitmap_from_pixels(px, w, h) -> Bitmap:
    """Bitmap.createBitmap(IntArray, w, h, ARGB_8888) 대응."""
    b = Bitmap(w, h)
    arr = np.array(px, dtype=np.uint32).reshape(h, w)
    b.buf[..., 3] = (arr >> 24) & 0xFF
    b.buf[..., 0] = (arr >> 16) & 0xFF
    b.buf[..., 1] = (arr >> 8) & 0xFF
    b.buf[..., 2] = arr & 0xFF
    return b


def shade(color: int, f: float) -> int:
    a, r, g, b = argb(color)
    return (255 << 24) | (min(255, int(r * f)) << 16) | (min(255, int(g * f)) << 8) | min(255, int(b * f))
