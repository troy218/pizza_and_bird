#!/usr/bin/env python3
"""
Pizza and Bird : 피자와 새 — 런처 아이콘 생성기 (외부 의존성 없음)

갤럭시(One UI) 기본 아이콘 모양은 스쿼클이다. Android 8+ 에서는 시스템이
적응형 아이콘(108dp 레이어)에 마스크를 씌우므로, 여기서는 모양을 미리
깎지 않는다. 대신 구도를 그 마스크에 맞춘다.

  * 배경은 108dp 전체를 불투명하게 채운다. 투명 모서리면 One UI가 흰 판을
    깔거나, 이미 둥근 아이콘 위에 스쿼클을 한 번 더 씌워 이중으로 깎인다.
  * 참새와 피자는 어떤 마스크에도 안 잘리는 66dp 안전 원 안에 둔다.
  * 해·구름·언덕은 스쿼클이 원보다 더 보여주는 어깨(상단 좌우, 하단)를
    채워서, 갤럭시에서 아이콘이 창처럼 보이게 한다.
  * ic_launcher 와 ic_launcher_round 는 같은 적응형 아이콘을 가리킨다.
    둥근 PNG를 roundIcon으로 두면 일부 One UI가 원 위에 스쿼클을 씌운다.
  * 모노크롬 레이어는 One UI 5+ 테마 아이콘(색상 팔레트)용이다.

사용: python3 tools/make_icons.py
"""
import math
import os
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
RES = os.path.join(ROOT, "app", "src", "main", "res")

# 108dp 캔버스. 셀 1칸 = 1dp 기준, 밀도마다 정수 배.
# mdpi 1 / hdpi 는 1.5라 324를 2배 축소 / xhdpi 2 / xxhdpi 3 / xxxhdpi 4
GRID = 108
SPRITE_SCALE = 1.26

SKY_TOP = (127, 212, 232)
SKY_MID = (164, 228, 238)
SKY_HOR = (214, 242, 246)
SKY_GROUND = (232, 246, 242)
SUN = (255, 214, 92)
SUN_CORE = (255, 251, 224)
SUN_RING = (247, 228, 150)
CLOUD = (255, 255, 255)
CLOUD_SH = (198, 226, 234)
HILL_FAR = (126, 196, 118)
HILL = (92, 184, 88)
HILL_LT = (156, 214, 112)

PAL = {
    "K": (54, 38, 28),
    "C": (146, 82, 40),
    "B": (186, 118, 64),
    "D": (148, 88, 46),
    "L": (214, 158, 96),
    "b": (255, 244, 220),
    "W": (112, 68, 38),
    "w": (84, 50, 30),
    "e": (36, 28, 24),
    "H": (255, 252, 246),
    "k": (236, 148, 42),
    "q": (196, 108, 28),
    "S": (42, 34, 30),
    "F": (214, 132, 58),
    "P": (214, 138, 62),
    "p": (168, 96, 42),
    "Y": (242, 186, 84),
    "c": (255, 208, 64),
    "l": (255, 236, 150),
    "d": (236, 176, 48),
    "R": (220, 52, 46),
    "r": (248, 140, 112),
    "G": (56, 148, 68),
    "g": (36, 112, 48),
}


class Img:
    def __init__(self, w, h, color=(0, 0, 0, 0)):
        self.w = w
        self.h = h
        self.b = bytearray(w * h * 4)
        if color[3]:
            pix = bytes(color)
            buf = self.b
            for i in range(0, len(buf), 4):
                buf[i : i + 4] = pix

    def set(self, x, y, c):
        i = (y * self.w + x) * 4
        self.b[i] = c[0]
        self.b[i + 1] = c[1]
        self.b[i + 2] = c[2]
        self.b[i + 3] = c[3]

    def blend(self, x, y, sr, sg, sb, sa):
        if sa <= 0 or x < 0 or y < 0 or x >= self.w or y >= self.h:
            return
        i = (y * self.w + x) * 4
        b = self.b
        if sa >= 255:
            b[i] = sr
            b[i + 1] = sg
            b[i + 2] = sb
            b[i + 3] = 255
            return
        dr, dg, db, da = b[i], b[i + 1], b[i + 2], b[i + 3]
        inv = 255 - sa
        out_a = sa + (da * inv + 127) // 255
        if out_a <= 0:
            b[i : i + 4] = b"\0\0\0\0"
            return
        b[i] = (sr * sa + dr * ((da * inv + 127) // 255) + out_a // 2) // out_a
        b[i + 1] = (sg * sa + dg * ((da * inv + 127) // 255) + out_a // 2) // out_a
        b[i + 2] = (sb * sa + db * ((da * inv + 127) // 255) + out_a // 2) // out_a
        b[i + 3] = out_a


def lerp(a, b, t):
    t = 0.0 if t < 0 else 1.0 if t > 1 else t
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def fill_ellipse(img, cx, cy, rx, ry, color):
    if rx <= 0.2 or ry <= 0.2:
        return
    x0 = max(0, int(cx - rx - 1))
    x1 = min(img.w - 1, int(math.ceil(cx + rx + 1)))
    y0 = max(0, int(cy - ry - 1))
    y1 = min(img.h - 1, int(math.ceil(cy + ry + 1)))
    inv_rx = 1.0 / (rx * rx)
    inv_ry = 1.0 / (ry * ry)
    opaque = color[3] >= 255
    for y in range(y0, y1 + 1):
        dy = y + 0.5 - cy
        yy = dy * dy * inv_ry
        if yy > 1:
            continue
        span = rx * math.sqrt(max(0.0, 1 - yy))
        xa = max(x0, int(cx - span))
        xb = min(x1, int(cx + span))
        for x in range(xa, xb + 1):
            dx = x + 0.5 - cx
            if dx * dx * inv_rx + yy <= 1.0:
                if opaque:
                    img.set(x, y, color)
                else:
                    img.blend(x, y, color[0], color[1], color[2], color[3])


def fill_rect(img, x0, y0, x1, y1, color):
    x0 = max(0, x0)
    y0 = max(0, y0)
    x1 = min(img.w - 1, x1)
    y1 = min(img.h - 1, y1)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            img.set(x, y, color)


def composite(base, over, ox=0, oy=0):
    bw, bh = base.w, base.h
    for y in range(over.h):
        dy = oy + y
        if dy < 0 or dy >= bh:
            continue
        for x in range(over.w):
            dx = ox + x
            if dx < 0 or dx >= bw:
                continue
            i = (y * over.w + x) * 4
            sa = over.b[i + 3]
            if sa == 0:
                continue
            if sa == 255:
                base.set(dx, dy, (over.b[i], over.b[i + 1], over.b[i + 2], 255))
            else:
                base.blend(dx, dy, over.b[i], over.b[i + 1], over.b[i + 2], sa)


def box_blur_alpha(img, radius):
    """그림자용. 색은 유지하고 알파만 부드럽게."""
    if radius < 1:
        return
    w, h = img.w, img.h
    src = img.b

    def pass1(horizontal):
        out = bytearray(src)
        if horizontal:
            for y in range(h):
                row = y * w
                for x in range(w):
                    s = n = 0
                    x0 = x - radius
                    x1 = x + radius
                    if x0 < 0:
                        x0 = 0
                    if x1 >= w:
                        x1 = w - 1
                    for xx in range(x0, x1 + 1):
                        s += src[(row + xx) * 4 + 3]
                        n += 1
                    out[(row + x) * 4 + 3] = s // n
        else:
            for x in range(w):
                for y in range(h):
                    s = n = 0
                    y0 = y - radius
                    y1 = y + radius
                    if y0 < 0:
                        y0 = 0
                    if y1 >= h:
                        y1 = h - 1
                    for yy in range(y0, y1 + 1):
                        s += src[(yy * w + x) * 4 + 3]
                        n += 1
                    out[(y * w + x) * 4 + 3] = s // n
        return out

    img.b = pass1(True)
    src = img.b
    img.b = pass1(False)


def write_png(path, img, opaque=False):
    w, h = img.w, img.h
    raw = bytearray()
    b = img.b
    if opaque:
        for y in range(h):
            raw.append(0)
            row = y * w * 4
            for x in range(w):
                i = row + x * 4
                raw.extend(b[i : i + 3])
        color_type = 2
    else:
        for y in range(h):
            raw.append(0)
            row = y * w * 4
            raw.extend(b[row : row + w * 4])
        color_type = 6

    def chunk(tag, data):
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    ihdr = struct.pack(">IIBBBBB", w, h, 8, color_type, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


def downscale(img, nw, nh):
    out = Img(nw, nh)
    sx = img.w / nw
    sy = img.h / nh
    ob = out.b
    ib = img.b
    iw = img.w
    for y in range(nh):
        y0 = int(y * sy)
        y1 = int((y + 1) * sy)
        if y1 <= y0:
            y1 = y0 + 1
        for x in range(nw):
            x0 = int(x * sx)
            x1 = int((x + 1) * sx)
            if x1 <= x0:
                x1 = x0 + 1
            sr = sg = sb = sa = n = 0
            for yy in range(y0, min(y1, img.h)):
                row = yy * iw
                for xx in range(x0, min(x1, iw)):
                    i = (row + xx) * 4
                    a = ib[i + 3]
                    sr += ib[i] * a
                    sg += ib[i + 1] * a
                    sb += ib[i + 2] * a
                    sa += a
                    n += 1
            o = (y * nw + x) * 4
            if sa == 0 or n == 0:
                continue
            ob[o] = sr // sa
            ob[o + 1] = sg // sa
            ob[o + 2] = sb // sa
            ob[o + 3] = sa // n
    return out


# ---------------------------------------------------------------- sprites
def new_grid(w, h):
    return [["." for _ in range(w)] for _ in range(h)]


def plot(g, x, y, ch):
    if ch != "." and 0 <= y < len(g) and 0 <= x < len(g[0]):
        g[y][x] = ch


def grid_ellipse(g, cx, cy, rx, ry, ch):
    if rx <= 0.2 or ry <= 0.2:
        return
    for y in range(int(cy - ry - 1), int(cy + ry + 2)):
        for x in range(int(cx - rx - 1), int(cx + rx + 2)):
            if ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1:
                plot(g, x, y, ch)


def outline(g, ch="K"):
    h, w = len(g), len(g[0])
    add = []
    for y in range(h):
        for x in range(w):
            if g[y][x] != ".":
                continue
            for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                ny, nx = y + dy, x + dx
                if 0 <= ny < h and 0 <= nx < w and g[ny][nx] != ".":
                    add.append((x, y))
                    break
    for x, y in add:
        g[y][x] = ch


def build_sparrow(scale=SPRITE_SCALE):
    s = scale
    g = new_grid(int(36 * s) + 4, int(28 * s) + 4)

    def P(x, y, ch):
        plot(g, int(round(x * s)), int(round(y * s)), ch)

    def E(cx, cy, rx, ry, ch):
        grid_ellipse(g, cx * s, cy * s, rx * s, ry * s, ch)

    for y, row in enumerate((".WWW..", "WWWWW.", "WWWWWW", "WWWWW.", ".WWW..")):
        for x, ch in enumerate(row):
            if ch != ".":
                P(1 + x, 13 + y, ch)
    E(16.2, 15.4, 9.2, 6.4, "B")
    E(17.4, 16.8, 6.0, 4.0, "b")
    E(14.0, 15.2, 5.4, 4.0, "W")
    for x in range(11, 18):
        sx, sy = int(round(x * s)), int(round(15 * s))
        if 0 <= sy < len(g) and 0 <= sx < len(g[0]) and g[sy][sx] == "W":
            plot(g, sx, sy, "w")
        sy2 = int(round(17 * s))
        if 0 <= sy2 < len(g) and 0 <= sx < len(g[0]) and g[sy2][sx] == "W":
            plot(g, sx, sy2, "L")
    E(23.6, 10.6, 6.5, 6.0, "B")
    E(22.8, 7.6, 5.6, 3.3, "C")
    E(25.4, 12.0, 3.3, 2.8, "b")
    E(26.8, 12.2, 1.25, 1.15, "S")
    E(24.4, 14.8, 2.0, 1.35, "S")
    E(24.0, 9.2, 2.15, 2.25, "e")
    P(25, 8, "H")
    P(25, 9, "H")
    P(24, 8, "H")
    P(25.4, 8.6, "H")
    E(31.4, 11.3, 2.5, 1.25, "k")
    E(33.0, 11.5, 1.15, 0.75, "q")
    for y, row in enumerate(("FF..FF", "F.FF.F")):
        for x, ch in enumerate(row):
            if ch != ".":
                P(17 + x, 21 + y, ch)
    outline(g)
    return g


def build_pizza(scale=SPRITE_SCALE):
    s = scale
    w, h = 44, 22
    W, H = int(w * s) + 4, int(h * s) + 4
    g = new_grid(W, H)
    cx, cy = (w / 2 - 0.5) * s, 8.2 * s
    grid_ellipse(g, cx, cy + 3.2 * s, 16.2 * s, 7.6 * s, "p")
    grid_ellipse(g, cx, cy, 16.2 * s, 7.2 * s, "P")
    grid_ellipse(g, cx, cy - 0.4 * s, 14.6 * s, 6.2 * s, "Y")
    grid_ellipse(g, cx, cy - 0.6 * s, 12.4 * s, 5.2 * s, "c")
    grid_ellipse(g, cx - 3.5 * s, cy - 2.2 * s, 5.5 * s, 2.2 * s, "l")
    for y in range(H):
        for x in range(W):
            if g[y][x] == "c" and y > cy + 2.4 * s:
                g[y][x] = "d"
    for ox, oy, rr in ((-5.6, -1.5, 2.35), (1.6, -2.15, 2.45), (-1.8, 1.55, 2.15)):
        px, py, rad = cx + ox * s, cy + oy * s, rr * s
        grid_ellipse(g, px, py, rad, rad * 0.78, "R")
        grid_ellipse(g, px - 0.55 * s, py - 0.45 * s, rad * 0.42, rad * 0.32, "r")
    bx, by = int(round(cx + 5.4 * s)), int(round(cy + 0.4 * s))
    for dy, row in enumerate(("GG.", "gGG", ".g.")):
        for dx, ch in enumerate(row):
            if ch != ".":
                plot(g, bx + dx, by + dy, ch)
    outline(g)
    return g


def blit_sprite(img, grid, ox, oy, cell, pal, mono=False):
    for y, row in enumerate(grid):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            # 테마 아이콘(One UI 색상 팔레트)은 눈만 구멍으로 남겨 실루엣이 새처럼 보이게 한다.
            if mono and ch in ("e", "H"):
                continue
            col = (255, 255, 255, 255) if mono else (*pal[ch], 255)
            x0 = ox + x * cell
            y0 = oy + y * cell
            for dy in range(cell):
                yy = y0 + dy
                if yy < 0 or yy >= img.h:
                    continue
                for dx in range(cell):
                    xx = x0 + dx
                    if 0 <= xx < img.w:
                        img.set(xx, yy, col)


# ---------------------------------------------------------------- scene
def draw_background(size):
    img = Img(size, size, (255, 255, 255, 255))
    for y in range(size):
        t = y / (size - 1) if size > 1 else 0
        if t < 0.42:
            c = lerp(SKY_TOP, SKY_MID, t / 0.42)
        elif t < 0.68:
            c = lerp(SKY_MID, SKY_HOR, (t - 0.42) / 0.26)
        else:
            c = lerp(SKY_HOR, SKY_GROUND, (t - 0.68) / 0.32)
        for x in range(size):
            u = x / (size - 1) if size > 1 else 0
            light = (1 - u) * 0.10 * (1 - t)
            col = tuple(min(255, int(c[i] + 28 * light)) for i in range(3))
            img.set(x, y, (*col, 255))

    # 해 — 스쿼클 오른쪽 어깨. 잘리는 모서리(우상단 끝)에는 두지 않는다.
    scx, scy = int(size * 0.72), int(size * 0.255)
    sr = max(2, int(size * 0.048))
    for i in range(10, 0, -1):
        rr = sr + int(size * 0.011 * i)
        a = 10 + (10 - i) * 7
        fill_ellipse(img, scx, scy, rr, rr, (*SUN, a))
    fill_ellipse(img, scx, scy, sr, sr, (*SUN_RING, 255))
    cr = max(1, int(sr * 0.68))
    fill_ellipse(img, scx, scy - 1, cr, cr, (*SUN_CORE, 255))

    def cloud(cx, cy, s):
        parts = ((-1.0, 0.2, 0.55), (-0.25, -0.25, 0.72), (0.45, 0.0, 0.62), (1.05, 0.25, 0.42))
        for ox, oy, r in parts:
            rr = s * r
            fill_ellipse(img, cx + ox * s, cy + oy * s + s * 0.16, rr, rr, (*CLOUD_SH, 230))
        for ox, oy, r in parts:
            rr = s * r
            fill_ellipse(img, cx + ox * s, cy + oy * s, rr, rr, (*CLOUD, 255))

    cloud(size * 0.28, size * 0.29, size * 0.038)

    # 언덕은 스쿼클 아래쪽을 끝까지 채운다. 가장자리를 비우면 마스크 밖으로
    # 흰 테두리가 생긴다.
    band = max(2, size // 90)
    for x in range(size):
        u = x / size
        y_far = int(size * (0.60 + 0.06 * math.cos((u - 0.25) * math.pi) + 0.015 * math.sin(u * 5)))
        y_near = int(size * (0.675 + 0.028 * math.cos((u - 0.5) * math.pi * 1.05) + 0.006 * math.sin(u * 9)))
        for y in range(max(0, y_far), size):
            img.set(x, y, (*HILL_FAR, 255))
        for y in range(max(0, y_near), size):
            img.set(x, y, (*HILL, 255))
        for y in range(max(0, y_near), min(size, y_near + band)):
            img.set(x, y, (*HILL_LT, 255))
    return img


def draw_foreground(size, mono=False):
    cell = size // GRID
    if cell < 1:
        raise ValueError("foreground cell < 1")
    img = Img(size, size)
    pizza = build_pizza()
    bird = build_sparrow()
    pw, ph = len(pizza[0]) * cell, len(pizza) * cell
    bh = len(bird) * cell
    px = (size - pw) // 2 + cell
    py = int(size * 0.50)

    if not mono:
        shadow = Img(size, size)
        fill_ellipse(
            shadow,
            px + pw / 2,
            py + ph * 0.86,
            pw * 0.34,
            max(2, cell * 2.2),
            (48, 96, 48, 90),
        )
        box_blur_alpha(shadow, max(1, cell))
        composite(img, shadow)

    blit_sprite(img, pizza, px, py, cell, PAL, mono=mono)
    bird_x = px + int(pw * 0.18)
    bird_y = py - int(bh * 0.70)
    blit_sprite(img, bird, bird_x, bird_y, cell, PAL, mono=mono)

    if not mono:
        flowers = (
            (30, 72, (242, 120, 150), (255, 206, 214)),
            (34, 76, (255, 228, 96), (255, 246, 190)),
            (74, 73, (255, 255, 255), (236, 246, 250)),
            (78, 77, (242, 146, 168), (255, 214, 222)),
        )
        for cx, cy, col, hi in flowers:
            x, y = cx * cell, cy * cell
            fill_rect(img, x, y, x + 2 * cell - 1, y + 2 * cell - 1, (*col, 255))
            fill_rect(img, x, y, x + cell - 1, y + cell - 1, (*hi, 255))
    return img


def render_layers(cell):
    size = GRID * cell
    bg = draw_background(size)
    fg = draw_foreground(size, mono=False)
    comp = Img(size, size)
    comp.b[:] = bg.b
    composite(comp, fg)
    return bg, fg, comp


# ---------------------------------------------------------------- masks
def _squircle_poly(size, inset_frac=18 / 108, samples=90):
    inset = size * inset_frac
    view = size - 2 * inset
    segs = (
        ((50, 0), (10, 0), (0, 10), (0, 50)),
        ((0, 50), (0, 90), (10, 100), (50, 100)),
        ((50, 100), (90, 100), (100, 90), (100, 50)),
        ((100, 50), (100, 10), (90, 0), (50, 0)),
    )
    poly = []
    for p0, p1, p2, p3 in segs:
        for i in range(samples):
            t = i / samples
            u = 1 - t
            x = u**3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t**3 * p3[0]
            y = u**3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t**3 * p3[1]
            poly.append((inset + x / 100 * view, inset + y / 100 * view))
    return poly


def _fill_mask(size, poly):
    mask = bytearray(size * size)
    n = len(poly)
    ys = [p[1] for p in poly]
    y0 = max(0, int(min(ys)))
    y1 = min(size - 1, int(max(ys)))
    for y in range(y0, y1 + 1):
        mid = y + 0.5
        xs = []
        for i in range(n):
            x1, y1 = poly[i]
            x2, y2 = poly[(i + 1) % n]
            if (y1 <= mid < y2) or (y2 <= mid < y1):
                if y2 == y1:
                    continue
                xs.append(x1 + (mid - y1) * (x2 - x1) / (y2 - y1))
        xs.sort()
        row = y * size
        for i in range(0, len(xs) - 1, 2):
            a = max(0, int(xs[i]))
            b = min(size - 1, int(xs[i + 1]))
            for x in range(a, b + 1):
                mask[row + x] = 255
    return mask


def squircle_alpha(size):
    """One UI 기본 마스크. 적응형 아이콘의 보이는 72dp 영역에 AOSP 스쿼클을 씌운다."""
    ss = 3
    big = size * ss
    poly = _squircle_poly(big)
    raw = _fill_mask(big, poly)
    out = bytearray(size * size)
    area = ss * ss
    for y in range(size):
        for x in range(size):
            s = 0
            for dy in range(ss):
                row = (y * ss + dy) * big
                base = row + x * ss
                for dx in range(ss):
                    s += raw[base + dx]
            out[y * size + x] = s // area
    return out


def apply_alpha(img, alpha):
    out = Img(img.w, img.h)
    out.b[:] = img.b
    for i, a in enumerate(alpha):
        j = i * 4 + 3
        out.b[j] = out.b[j] * a // 255
    return out


def circle_alpha(size, inset=0):
    out = bytearray(size * size)
    cx = cy = (size - 1) / 2
    r = size / 2 - inset
    for y in range(size):
        for x in range(size):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= r - 0.75:
                a = 255
            elif d >= r + 0.75:
                a = 0
            else:
                a = int(255 * (r + 0.75 - d) / 1.5)
            out[y * size + x] = a
    return out


def galaxy_preview(comp):
    """홈 화면에서 보이는 스쿼클. 시스템 그림자라 아이콘 파일에는 넣지 않는다."""
    tile = 560
    icon_s = 392
    masked = apply_alpha(comp, squircle_alpha(comp.w))
    icon = downscale(masked, icon_s, icon_s)
    canvas = Img(tile, tile, (236, 232, 226, 255))
    for y in range(tile):
        for x in range(tile):
            t = (x * 0.25 + y) / (tile * 1.3)
            c = lerp((232, 236, 228), (246, 232, 214), t)
            canvas.set(x, y, (*c, 255))
    ox = (tile - icon_s) // 2
    oy = (tile - icon_s) // 2 - 8
    # One UI 스타일 부드러운 그림자
    sh = Img(tile, tile)
    for y in range(icon_s):
        for x in range(icon_s):
            a = icon.b[(y * icon_s + x) * 4 + 3]
            if a:
                sh.blend(ox + x, oy + y + 14, 30, 36, 32, a * 40 // 255)
    box_blur_alpha(sh, 8)
    box_blur_alpha(sh, 4)
    composite(canvas, sh)
    composite(canvas, icon, ox, oy)
    return canvas


ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<!--
  갤럭시 One UI는 이 레이어에 스쿼클 마스크를 씌운다.
  배경은 108dp 풀블리드(투명 없음). 둥근 코너를 미리 넣지 말 것 — 이중 마스킹된다.
  ic_launcher_round 도 같은 레이어를 쓴다. 원형 PNG를 roundIcon으로 주면
  일부 One UI가 원 위에 스쿼클을 한 번 더 씌운다.
-->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""


def main():
    # 정수 셀로 그릴 수 있는 밀도. hdpi(162)·mdpi(108)는 여기서 축소한다.
    masters = {}
    for cell in (2, 3, 4, 6):
        print(f"render {GRID * cell}px …")
        masters[cell] = render_layers(cell)

    # drawable-*: 적응형 레이어. xxhdpi(갤럭시 대부분)·xxxhdpi 는 정수 픽셀.
    density_cell = {
        "mdpi": None,       # 216 → 108
        "hdpi": None,       # 324 → 162
        "xhdpi": 2,         # 216
        "xxhdpi": 3,        # 324
        "xxxhdpi": 4,       # 432
    }
    for name, cell in density_cell.items():
        folder = os.path.join(RES, f"drawable-{name}")
        if cell is None:
            if name == "mdpi":
                bg, fg, _ = masters[2]
                bg, fg = downscale(bg, 108, 108), downscale(fg, 108, 108)
                mono_src = draw_foreground(216, mono=True)
                mono = downscale(mono_src, 108, 108)
            else:
                bg, fg, _ = masters[3]
                bg, fg = downscale(bg, 162, 162), downscale(fg, 162, 162)
                mono = downscale(draw_foreground(324, mono=True), 162, 162)
        else:
            bg, fg, _ = masters[cell]
            mono = draw_foreground(GRID * cell, mono=True)
        write_png(os.path.join(folder, "ic_launcher_background.png"), bg, opaque=True)
        write_png(os.path.join(folder, "ic_launcher_foreground.png"), fg)
        write_png(os.path.join(folder, "ic_launcher_monochrome.png"), mono)
        print(f"  drawable-{name}")

    anydpi = os.path.join(RES, "mipmap-anydpi-v26")
    os.makedirs(anydpi, exist_ok=True)
    for fname in ("ic_launcher.xml", "ic_launcher_round.xml"):
        with open(os.path.join(anydpi, fname), "w", encoding="utf-8") as f:
            f.write(ADAPTIVE_XML)
        print(f"  {fname}")

    # 레거시(API 24–25). 갤럭시는 위의 적응형 XML을 쓴다.
    # 사각 아이콘은 풀블리드(투명 없음) — 런처가 스쿼클로 깎아도 테두리가 비지 않는다.
    _, _, comp432 = masters[4]
    legacy = {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192,
    }
    for name, px in legacy.items():
        folder = os.path.join(RES, f"mipmap-{name}")
        icon = downscale(comp432, px, px)
        write_png(os.path.join(folder, "ic_launcher.png"), icon, opaque=True)
        write_png(os.path.join(folder, "ic_launcher_round.png"), apply_alpha(icon, circle_alpha(px)), )
        print(f"  mipmap-{name} {px}px")

    # Play 스토어 아이콘. 마스크는 스토어가 씌우므로 풀블리드·불투명.
    store = downscale(masters[6][2], 512, 512)
    write_png(os.path.join(ROOT, "store", "icon_512.png"), store, opaque=True)
    print("  store/icon_512.png")

    preview = galaxy_preview(comp432)
    preview_path = os.path.join(ROOT, "preview", "30_icon_galaxy.png")
    write_png(preview_path, preview, opaque=True)
    print(f"  {preview_path}")
    print("아이콘 생성 완료 — 갤럭시 스쿼클에 맞춰 적응형 레이어를 넣었습니다.")


if __name__ == "__main__":
    main()
