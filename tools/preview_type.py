#!/usr/bin/env python3
"""
Pizza and Bird : 피자와 새 — 타이포그래피 미리보기 (외부 의존성 없음)

Type.kt 의 5x7 픽셀 디스플레이 폰트 글리프와 Type.text() 의 크기 계산식을 그대로
재현해서, 타이틀 화면과 HUD 를 1dp = 1px 로 그린다. 즉 "폰트가 실제로 이만큼
커 보인다"를 확인할 수 있는 목업이다.

한글은 이 환경에 폰트가 없어 그릴 수 없으므로, 한글은 실제 글자 폭에 맞춘
막대로 대신한다(가로 1em 기준). 라틴/숫자/기호는 픽셀 폰트로 진짜 그린다.

사용: python3 tools/preview_type.py
출력: tools/type_preview.png
"""
import os
import re
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
TYPE_KT = os.path.join(HERE, "..", "app", "src", "main", "java", "com", "pizzaandbird", "game", "Type.kt")
OUT = os.path.join(HERE, "type_preview.png")

GW, GH, ADVANCE, CELL_H = 5, 7, 6, 8
# 미리보기를 그리는 기기 밀도. 실제 폰/태블릿(2~3.5)에 가깝게 잡았다.
# 픽셀 폰트는 정수 배율로 그려지므로 밀도가 낮으면 작게 보인다.
DENSITY = 1.5

# ------------------------------------------------------------------ PNG


def write_png(path, px, w, h):
    raw = b"".join(b"\x00" + bytes(px[y * w * 4:(y + 1) * w * 4]) for y in range(h))

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)


# ------------------------------------------------------------------ 글리프

ENTRY = re.compile(r"""^\s*(?:'((?:\\.|[^'\\]))*'|"((?:\\.|[^"\\])*)")\s+to\s+"([^"]*)",\s*$""")


def unescape(s):
    out, i = [], 0
    while i < len(s):
        if s[i] == "\\" and i + 1 < len(s):
            out.append(s[i + 1]); i += 2
        else:
            out.append(s[i]); i += 1
    return "".join(out)


def load_glyphs():
    glyphs, in_table = {}, False
    for line in open(TYPE_KT, encoding="utf-8"):
        if "private val TABLE" in line:
            in_table = True
            continue
        if in_table:
            if line.strip() == ")":
                break
            m = ENTRY.match(line)
            if m:
                ch = m.group(1) if m.group(1) is not None else m.group(2)
                glyphs[unescape(ch)] = m.group(3).split("/")
    return glyphs


G = load_glyphs()


def supports(s):
    return all(ch in G for ch in s)


# ------------------------------------------------------------------ 캔버스


class Canvas:
    def __init__(self, w, h, bg=(250, 244, 233, 255)):
        self.w, self.h = w, h
        self.k = DENSITY
        self.px = bytearray(w * h * 4)
        for i in range(0, len(self.px), 4):
            self.px[i:i + 4] = bytes(bg)

    def rect(self, x, y, w, h, col, r=0):
        x, y, w, h = x * self.k, y * self.k, w * self.k, h * self.k
        for yy in range(int(y), int(y + h)):
            if yy < 0 or yy >= self.h:
                continue
            for xx in range(int(x), int(x + w)):
                if xx < 0 or xx >= self.w:
                    continue
                if r > 0:
                    dx = min(xx - x, x + w - 1 - xx)
                    dy = min(yy - y, y + h - 1 - yy)
                    if dx < r and dy < r and (r - dx) ** 2 + (r - dy) ** 2 > r * r:
                        continue
                o = (yy * self.w + xx) * 4
                self.px[o:o + 4] = bytes(col)

    # --- Type.kt 의 Type.text / PixelFont.draw 재현 ---

    def pixel_width(self, s, scale):
        return 0 if not s else (len(s) * ADVANCE - 1) * scale

    def _pass(self, s, left, top, scale, col):
        for ch in s:
            rows = G.get(ch)
            if rows is not None:
                for ry, row in enumerate(rows[:CELL_H]):
                    for rx, cell in enumerate(row):
                        if cell == "#":
                            self.rect(left + rx * scale, top + ry * scale, scale, scale, col)
            left += ADVANCE * scale

    def pixel(self, s, x, baseline, scale, col, align=0.0, outline=None, shadow=None):
        w = self.pixel_width(s, scale) * DENSITY
        left = x - align * w
        top = baseline - GH * scale
        if shadow:
            self._pass(s, left + scale, top + scale, scale, shadow)
        if outline:
            for ox, oy in ((-scale, 0), (scale, 0), (0, -scale), (0, scale)):
                self._pass(s, left + ox, top + oy, scale, outline)
        self._pass(s, left, top, scale, col)
        return w

    @staticmethod
    def _em(ch):
        """글자 폭을 em 기준으로 근사 (한글 1em · 라틴 0.55em · 공백 0.3em)"""
        o = ord(ch)
        if ch == " ":
            return 0.3
        if 0xAC00 <= o <= 0xD7A3 or 0x3130 <= o <= 0x318F:
            return 1.0
        if ch.isascii() and ch.isalnum():
            return 0.56
        return 0.34

    def hangul(self, s, x, baseline, size_dp, col, align=0.0):
        """시스템 폰트로 그려질 문장(한글 포함)을 막대로 근사"""
        w = sum(self._em(ch) for ch in s) * size_dp * DENSITY
        left = x - align * w
        cap = size_dp * 0.72
        asc = size_dp * 0.78
        cx = left
        for ch in s:
            ew = self._em(ch) * size_dp
            if ch != " ":
                self.rect(cx, baseline - asc, ew * 0.86, cap, col, r=max(0.5, size_dp * 0.06 * DENSITY))
            cx += ew
        return w

    def text(self, s, x, baseline, size_dp, bold, col, align=0.0, outline=None, shadow=None):
        """Type.text() 와 같은 규칙: 진한 글 + 라틴/숫자만 이면 픽셀 폰트"""
        scale = max(1, round(size_dp * DENSITY * 0.72 / GH))
        if bold and size_dp >= 10 and supports(s):
            return self.pixel(s, x, baseline, scale, col, align, outline, shadow)
        return self.hangul(s, x, baseline, size_dp, col, align)

    def measure(self, s, size_dp, bold):
        scale = max(1, round(size_dp * DENSITY * 0.72 / GH))
        if supports(s):
            return self.pixel_width(s, scale) * DENSITY
        return sum(self._em(ch) for ch in s) * size_dp

    def save(self, path):
        write_png(path, self.px, self.w, self.h)


# ------------------------------------------------------------------ 색

INK = (74, 55, 40, 255)
SOFT = (138, 115, 96, 255)
MUTED = (107, 90, 72, 255)
CARAMEL = (181, 101, 29, 255)
CREAM = (248, 239, 220, 255)
PAPER = (255, 248, 232, 255)
LEAF = (111, 174, 111, 255)
BERRY = (226, 87, 76, 255)
DROP = (172, 150, 120, 255)
SKY = (163, 216, 232, 255)
GRASS = (140, 196, 132, 255)
PANEL = (248, 239, 220, 255)
EDGE = (107, 79, 53, 255)
FAINT = (214, 202, 182, 255)


def title_mock(c, ox, oy):
    """Scenes.kt TitleScene.drawHud 를 그대로 옮긴 것 (dp = px)"""
    w, h = 960, 540
    x0, y0 = ox, oy

    def R(x, y, ww, hh, col, r=0):
        c.rect(ox + x, oy + y, ww, hh, col, r)

    def T(s, x, y, size, bold, col, align=0.0, outline=None, shadow=None):
        return c.text(s, x0 + x, y0 + y, size, bold, col, align, outline, shadow)

    # 하늘/잔디
    R(0, 0, w, 330, SKY)
    R(0, 300, w, 40, (196, 230, 214, 255))
    R(0, 330, w, 210, GRASS)

    # 로고 (픽셀 폰트 + 크림 테두리 + 그림자)
    T("PIZZA and BIRD", w / 2, 58, 38, True, INK, 0.5, outline=PAPER, shadow=DROP)
    # 한글 제목 (시스템 폰트 + 테두리)
    T("피자와 새", w / 2, 98, 21, True, CARAMEL, 0.5, outline=PAPER, shadow=DROP)
    T("피자를 굽고, 자전거를 타고, 새를 찍는 힐링 여행", w / 2, 126, 11, False, LEAF, 0.5)

    # 버튼
    bw, bh = 210, 44
    bx = (w - bw) / 2
    by = h * 0.56
    for i, (label, on) in enumerate((("새로 시작하기", True), ("이어하기", False))):
        top = by + i * (bh + 14)
        R(bx, top, bw, bh, PANEL if on else (216, 208, 196, 255), r=13)
        pcol = INK if on else (150, 142, 130, 255)
        if on:
            T(label, w / 2, top + bh / 2 + 5.4 + 1.2, 16, True, DROP, 0.5)
        T(label, w / 2, top + bh / 2 + 5.4, 16, True, pcol, 0.5)
    T("v0.2.1 beta · 오프라인 · 한국 12곳 · 공식 새 598종 · made with 🍕",
      w / 2, h - 12, 11, False, (150, 132, 110, 255), 0.5)


def hud_mock(c, ox, oy):
    """Hud.kt drawStats 를 옮긴 것"""
    x0, y0 = ox, oy

    def R(x, y, ww, hh, col, r=0):
        c.rect(ox + x, oy + y, ww, hh, col, r)

    def T(s, x, y, size, bold, col, align=0.0):
        return c.text(s, x0 + x, y0 + y, size, bold, col, align)

    left, top, w, h = 12, 12, 162, 126
    R(left, top, w, h, (248, 239, 220, 216), r=10)

    def bar(x, y, bw, bh, col):
        R(x, y, bw, bh, (214, 197, 164, 255), r=bh / 2)
        R(x + 1.5, y + 1.5, bw * 0.72, bh - 3, col, r=(bh - 3) / 2)

    bar(left + 36, top + 14, 112, 12, (242, 145, 60, 255))
    iy2 = top + 12 + 22
    bar(left + 36, iy2 + 2, 112, 12, (111, 186, 107, 255))
    T("₩124,000", left + 12, iy2 + 36, 13.5, True, INK)
    R(left + 12, iy2 + 44, 14, 14, (242, 166, 60, 255), r=3)     # 피자 아이콘
    T("×12", left + 30, iy2 + 53, 12.2, True, INK)
    R(left + 54, iy2 + 44, 17, 14, (74, 74, 88, 220), r=3)       # 카메라 아이콘
    T("Lv.3", left + 76, iy2 + 53, 12.2, True, INK)
    R(left + 11, iy2 + 64, 14, 14, (242, 208, 107, 255), r=3)    # 달/해
    T("07:30", left + 30, iy2 + 73, 12.2, True, MUTED)
    R(left + 58, iy2 + 66, 15, 13, (74, 74, 88, 220), r=3)
    T("7", left + 76, iy2 + 73, 12.2, True, MUTED)

    # 토스트 + 배너 (오른쪽 여백에 배치)
    tx = x0 + 220
    tw = c.measure("참새를 발견했어요!", 13.5, True)
    c.rect(tx, y0, tw + 22, 27, PANEL, r=13)
    c.text("참새를 발견했어요!", tx + 11, y0 + 19, 13.5, True, INK)
    bt = "✨ 희귀한 새가 나타났어요 · 팔색조"
    bw = c.measure(bt, 21, True)
    c.rect(tx, y0 + 36, bw + 48, 52, (66, 58, 76, 220), r=18)
    c.text(bt, tx + (bw + 48) / 2, y0 + 70, 21, True, CREAM, 0.5)


def roles_mock(c, ox, oy):
    """역할별 크기 표"""
    x0, y0 = ox, oy
    roles = [("HERO", 38, True, 0.06), ("DISPLAY", 21, True, 0.06), ("TITLE", 16, True, 0.04),
             ("HEADING", 13.5, True, 0.03), ("LABEL", 12.2, True, 0.02), ("BODY", 12.6, False, 0),
             ("CAPTION", 11, False, 0), ("MICRO", 8.4, False, 0)]
    y = 0
    for name, size, bold, tr in roles:
        c.text(name, x0, y0 + y + size, 7, False, SOFT)
        c.text("PIZZA 0123", x0 + 62, y0 + y + size, size, bold, INK)
        c.hangul("피자와 새를 찾아 Heisenberg 12", x0 + 330, y0 + y + size, size, FAINT)
        y += size + 10


def main():
    if not G:
        raise SystemExit("Type.kt 에서 글리프 표를 찾지 못했다")
    print("글리프 %d개 로드 (미리보기 밀도 %sx)" % (len(G), DENSITY))

    W, H = int(1060 * DENSITY), int(1000 * DENSITY)
    c = Canvas(W, H)
    c.text("TYPE PREVIEW — Type.kt 와 같은 계산식", 16, 18, 7, False, SOFT)

    title_mock(c, 30, 30)
    y = 30 + 540 + 26

    c.text("HUD — 숫자는 5x7 픽셀 폰트 / 한글은 시스템 폰트(막대)", 30, y, 7, False, CARAMEL)
    hud_mock(c, 30, y + 14)
    roles_mock(c, 30, y + 180)

    c.save(OUT)
    print("저장:", os.path.relpath(OUT, os.path.join(HERE, "..")))


if __name__ == "__main__":
    main()
