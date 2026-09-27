#!/usr/bin/env python3
"""카메라 아이콘 / 인게임 카메라 스프라이트 미리보기.

`Assets.kt` 의 camIcon() / camHeld() 와 같은 알고리즘을 파이썬으로 옮겨 놓았다.
APK 빌드 없이 픽셀 결과를 눈으로 확인하기 위한 개발용 도구.

    python3 tools/preview/camera_art.py /tmp/cameras.png
"""
from __future__ import annotations

import sys

from pixelcanvas import Bitmap, Paint

# 색상 -----------------------------------------------------------------
BLACK = 0xFF3A3A44
BLACK_D = 0xFF23232B
GRAPH = 0xFF4E4E5C
GRAPH_D = 0xFF32323C
SILVER = 0xFFB9BEC6
SILVER_D = 0xFF8A8F98
LEATHER = 0xFF6B5A48
LEATHER_D = 0xFF4A3C30
WHITE_LENS = 0xFFE8E4D8
DARK_LENS = 0xFF2E2E38
GRAY_LENS = 0xFF5A5A66
RED = 0xFFE2574C
GOLD = 0xFFF2D06B
BLUE = 0xFF6FA8DC
GREEN = 0xFF6FBA6B

OUTLINE = 0xFF191920
GLASS = 0xFF3E6B8C
GLASS_HI = 0xFFBFE4F5
STRAP = 0xFF7A4A2B


class Look:
    def __init__(self, style, bodyCol, bodyDark, accent, barrelLen, barrelDia,
                 barrelCol, hood, evf, flash):
        self.style = style
        self.bodyCol = bodyCol
        self.bodyDark = bodyDark
        self.accent = accent
        self.barrelLen = barrelLen
        self.barrelDia = barrelDia
        self.barrelCol = barrelCol
        self.hood = hood
        self.evf = evf
        self.flash = flash


def _p(col):
    return Paint(col)


# ---------------------------------------------------------------------------
# 색 보정 유틸
# ---------------------------------------------------------------------------

def shade(col, k):
    a = (col >> 24) & 0xFF
    r = (col >> 16) & 0xFF
    g = (col >> 8) & 0xFF
    b = col & 0xFF
    if k >= 1.0:
        t = min(k - 1.0, 1.0)
        r = int(r + (255 - r) * t)
        g = int(g + (255 - g) * t)
        b = int(b + (255 - b) * t)
    else:
        r = int(r * k)
        g = int(g * k)
        b = int(b * k)
    return (a << 24) | (r << 16) | (g << 8) | b


# ---------------------------------------------------------------------------
# 정면 아이콘 (22 x 18) — HUD / 카메라 버튼
# ---------------------------------------------------------------------------

def cam_icon(lk: Look) -> Bitmap:
    b = Bitmap(22, 18)
    cv = b

    def r(l, t, rr, bb, col):
        cv.drawRect(l, t, rr, bb, _p(col))

    def o(l, t, rr, bb, rad, col):
        cv.drawRoundRect((l, t, rr, bb), rad, rad, _p(col))

    def cir(cx, cy, rad, col):
        cv.drawCircle(cx, cy, rad, _p(col))

    big = lk.style in (3, 4, 5)
    pro = lk.style == 4
    top = 4.2 if big else 5.0
    bot = 17.0 if pro else 16.4

    # 펜타프리즘 / EVF / 플래시
    if big:
        o(7.4, 0.8, 14.6, 5.2, 1.2, OUTLINE)
        o(8.0, 1.4, 14.0, 5.0, 1.0, lk.bodyCol)
        r(9.0, 2.0, 13.0, 3.0, shade(lk.bodyCol, 1.25))
    elif lk.evf:
        o(2.0, 2.0, 8.0, 5.6, 1.1, OUTLINE)
        o(2.6, 2.6, 7.4, 5.2, 0.9, lk.bodyCol)
    elif lk.flash:
        o(2.2, 2.4, 6.4, 5.4, 0.9, OUTLINE)
        o(2.7, 2.9, 5.9, 5.0, 0.7, 0xFFF7EFD2)

    # 바디
    o(0.4, top - 0.6, 21.6, bot + 0.8, 2.8, OUTLINE)
    o(1.0, top, 21.0, bot, 2.4, lk.bodyCol)
    r(1.6, top + 0.8, 20.4, top + 1.8, shade(lk.bodyCol, 1.18))     # 상단 하이라이트
    o(16.0, top + 1.2, 20.6, bot - 0.6, 1.8, lk.bodyDark)           # 그립
    r(17.0, top + 2.4, 19.6, bot - 2.0, shade(lk.bodyDark, 0.8))
    r(2.0, top - 1.8, 4.2, top + 0.2, lk.accent)                    # 셔터 버튼
    if pro:
        r(1.0, bot - 2.4, 21.0, bot, lk.bodyDark)                   # 세로그립

    # 렌즈 (정면에서는 지름이 곧 존재감)
    lr = 3.2 + lk.barrelDia * 0.52 + lk.barrelLen * 0.13
    lr = min(lr, 6.6)
    cx, cy = 10.2, (top + bot) / 2 + 0.4
    if lk.hood:
        cir(cx, cy, lr + 1.6, OUTLINE)
        cir(cx, cy, lr + 1.0, shade(lk.barrelCol, 0.85))
    cir(cx, cy, lr + 0.9, OUTLINE)
    cir(cx, cy, lr, lk.barrelCol)
    cir(cx, cy, lr * 0.72, shade(lk.barrelCol, 0.6))
    cir(cx, cy, lr * 0.58, GLASS)
    cir(cx - lr * 0.26, cy - lr * 0.28, lr * 0.26, GLASS_HI)
    return b


# ---------------------------------------------------------------------------
# 측면 아이콘 (32 x 20) — 상점 / 장비 가방 (경통 길이가 한눈에 보인다)
# ---------------------------------------------------------------------------

def cam_profile(lk: Look) -> Bitmap:
    b = Bitmap(32, 20)
    cv = b

    def r(l, t, rr, bb, col):
        cv.drawRect(l, t, rr, bb, _p(col))

    def o(l, t, rr, bb, rad, col):
        cv.drawRoundRect((l, t, rr, bb), rad, rad, _p(col))

    def ov(l, t, rr, bb, col):
        cv.drawOval((l, t, rr, bb), _p(col))

    big = lk.style in (3, 4, 5)
    pro = lk.style == 4
    top = 4.6 if big else 5.4
    bot = 18.0 if pro else 17.2
    bodyL, bodyR = 1.0, 11.6
    cy = (top + bot) / 2 + 0.3

    if big:
        o(3.4, 0.8, 10.2, 5.6, 1.2, OUTLINE)
        o(4.0, 1.4, 9.6, 5.4, 1.0, lk.bodyCol)
    elif lk.evf:
        o(1.6, 2.4, 6.8, 6.0, 1.0, OUTLINE)
        o(2.2, 3.0, 6.2, 5.8, 0.8, lk.bodyCol)
    elif lk.flash:
        o(2.0, 2.6, 5.6, 5.6, 0.8, OUTLINE)
        o(2.5, 3.1, 5.1, 5.2, 0.6, 0xFFF7EFD2)

    # 바디
    o(bodyL - 0.6, top - 0.6, bodyR + 0.6, bot + 0.8, 2.4, OUTLINE)
    o(bodyL, top, bodyR, bot, 2.0, lk.bodyCol)
    r(bodyL + 0.6, top + 0.8, bodyR - 0.6, top + 1.7, shade(lk.bodyCol, 1.18))
    o(bodyL, top + 1.0, bodyL + 3.2, bot, 1.6, lk.bodyDark)          # 그립
    r(bodyL + 4.6, top - 1.6, bodyL + 6.4, top + 0.2, lk.accent)     # 셔터 버튼
    if pro:
        r(bodyL, bot - 2.2, bodyR, bot, lk.bodyDark)

    # 경통
    ln = min(2.6 + lk.barrelLen * 1.9, 19.4)
    dia = min(5.0 + lk.barrelDia * 1.15, 14.6)
    x0 = bodyR - 0.6
    x1 = x0 + ln
    t0, b0 = cy - dia / 2, cy + dia / 2
    # 마운트 링
    r(x0 - 0.6, t0 - 1.0, x0 + 1.4, b0 + 1.0, OUTLINE)
    r(x0 - 0.2, t0 - 0.6, x0 + 1.2, b0 + 0.6, shade(lk.bodyCol, 1.5))
    # 경통 본체
    r(x0 + 1.2, t0 - 0.7, x1 + 0.7, b0 + 0.7, OUTLINE)
    r(x0 + 1.2, t0, x1, b0, lk.barrelCol)
    r(x0 + 1.2, t0, x1, t0 + 1.2, shade(lk.barrelCol, 1.2))          # 윗면 하이라이트
    r(x0 + 1.2, b0 - 1.0, x1, b0, shade(lk.barrelCol, 0.78))         # 아랫면 그림자
    # 줌/포커스 링
    rg = shade(lk.barrelCol, 0.66)
    r(x0 + 1.2 + ln * 0.30, t0, x0 + 1.2 + ln * 0.42, b0, rg)
    if ln > 9:
        r(x0 + 1.2 + ln * 0.58, t0, x0 + 1.2 + ln * 0.68, b0, rg)
    if lk.barrelCol == WHITE_LENS:
        r(x0 + 1.6, t0, x0 + 2.6, b0, GOLD)                          # 고급 렌즈 링
    # 삼각대 발
    if dia > 10:
        r(x0 + 3.0, b0, x0 + 7.0, b0 + 1.8, lk.bodyDark)
    # 후드
    if lk.hood:
        r(x1 - 2.4, t0 - 1.6, x1 + 0.8, b0 + 1.6, OUTLINE)
        r(x1 - 2.2, t0 - 1.2, x1 + 0.2, b0 + 1.2, shade(lk.barrelCol, 0.88))
    # 전면 렌즈알
    gx1 = x1 - 0.4 if not lk.hood else x1 - 1.6
    ov(gx1 - 2.4, cy - dia * 0.34, gx1, cy + dia * 0.34, GLASS)
    ov(gx1 - 2.0, cy - dia * 0.2, gx1 - 1.0, cy + dia * 0.02, GLASS_HI)
    return b


# ---------------------------------------------------------------------------
# 인게임 스프라이트 (32 x 32) — 플레이어 위에 겹쳐 그린다
#   dir: 0 정면 / 1 뒤 / 2 오른쪽 측면
# ---------------------------------------------------------------------------

def cam_held(lk: Look, d: int, raised: bool) -> Bitmap:
    b = Bitmap(32, 32)
    cv = b

    def r(l, t, rr, bb, col):
        cv.drawRect(l, t, rr, bb, _p(col))

    def o(l, t, rr, bb, rad, col):
        cv.drawRoundRect((l, t, rr, bb), rad, rad, _p(col))

    def cir(cx, cy, rad, col):
        cv.drawCircle(cx, cy, rad, _p(col))

    ln = min(2.0 + lk.barrelLen * 0.95, 12.0)
    dia = min(3.4 + lk.barrelDia * 0.62, 8.0)
    big = lk.style in (3, 4, 5)

    if raised:
        if d == 0:                       # 정면 — 얼굴 앞에 든 카메라
            o(10.2, 7.0, 21.8, 15.4, 1.6, OUTLINE)
            o(10.8, 7.6, 21.2, 14.8, 1.4, lk.bodyCol)
            r(11.4, 8.2, 20.6, 9.2, lk.bodyDark)
            if big or lk.evf:
                r(13.6, 5.6, 18.4, 7.4, OUTLINE)
                r(14.2, 6.0, 17.8, 7.4, lk.bodyCol)
            r(19.4, 6.4, 20.8, 7.6, lk.accent)
            gr = dia * 0.42 + 1.4
            cir(16.0, 11.4, gr + 0.9, OUTLINE)
            cir(16.0, 11.4, gr, lk.barrelCol)
            cir(16.0, 11.4, gr * 0.68, GLASS)
            cir(15.2, 10.6, gr * 0.28, GLASS_HI)
            if lk.hood:
                cir(16.0, 11.4, gr + 1.8, OUTLINE)
                cir(16.0, 11.4, gr + 1.2, lk.barrelCol)
                cir(16.0, 11.4, gr * 0.68, GLASS)
                cir(15.2, 10.6, gr * 0.28, GLASS_HI)
            # 팔 (카메라를 받친 손)
            r(8.6, 12.4, 11.0, 15.6, 0xFFFFD9B0)
            r(21.0, 12.4, 23.4, 15.6, 0xFFFFD9B0)
        elif d == 1:                     # 뒤 — 카메라 뒷면과 팔꿈치
            o(11.6, 7.4, 20.4, 14.2, 1.4, OUTLINE)
            o(12.2, 8.0, 19.8, 13.6, 1.2, lk.bodyDark)
            r(13.2, 9.0, 18.8, 12.6, 0xFF6E8FA6)      # 액정
            r(8.8, 11.6, 11.6, 15.0, 0xFFFFD9B0)
            r(20.4, 11.6, 23.2, 15.0, 0xFFFFD9B0)
        else:                            # 측면 — 경통이 앞으로 뻗는다
            o(12.6, 7.6, 19.6, 14.6, 1.5, OUTLINE)
            o(13.2, 8.2, 19.0, 14.0, 1.3, lk.bodyCol)
            if big or lk.evf:
                r(14.0, 6.0, 17.6, 7.8, OUTLINE)
                r(14.4, 6.4, 17.2, 7.8, lk.bodyCol)
            x0, x1 = 19.0, min(19.0 + ln, 31.0)
            cy = 11.2
            r(x0, cy - dia / 2 - 0.7, x1 + 0.7, cy + dia / 2 + 0.7, OUTLINE)
            r(x0, cy - dia / 2, x1, cy + dia / 2, lk.barrelCol)
            r(x0 + (x1 - x0) * 0.4, cy - dia / 2, x0 + (x1 - x0) * 0.52, cy + dia / 2, lk.bodyDark)
            if lk.barrelCol == WHITE_LENS:
                r(x0 + 0.8, cy - dia / 2, x0 + 1.6, cy + dia / 2, GOLD)
            if lk.hood:
                r(x1 - 2.2, cy - dia / 2 - 1.2, x1 + 0.7, cy + dia / 2 + 1.2, OUTLINE)
                r(x1 - 2.0, cy - dia / 2 - 0.8, x1, cy + dia / 2 + 0.8, lk.barrelCol)
            cir(x1 - 1.3, cy, dia * 0.3 + 0.7, GLASS)
            cir(x1 - 1.8, cy - 0.6, dia * 0.14 + 0.3, GLASS_HI)
            r(11.4, 12.0, 13.8, 15.2, 0xFFFFD9B0)
    else:
        if d == 0:                       # 정면 — 가슴에 매달린 카메라
            r(12.0, 15.6, 13.2, 17.8, STRAP)
            r(19.0, 15.6, 20.2, 17.8, STRAP)
            o(12.0, 17.4, 20.2, 22.6, 1.3, OUTLINE)
            o(12.5, 17.9, 19.7, 22.1, 1.1, lk.bodyCol)
            r(13.0, 18.3, 19.2, 19.1, lk.bodyDark)
            gr = dia * 0.3 + 0.9
            cir(16.1, 20.2, gr + 0.7, OUTLINE)
            cir(16.1, 20.2, gr, lk.barrelCol)
            cir(16.1, 20.2, gr * 0.6, GLASS)
            r(18.6, 17.0, 19.6, 17.9, lk.accent)
        elif d == 1:                     # 뒤 — 어깨 위 스트랩만 보인다
            r(11.8, 15.4, 13.2, 19.2, STRAP)
            r(19.0, 15.4, 20.4, 19.2, STRAP)
            o(18.8, 18.6, 23.0, 22.4, 1.2, OUTLINE)
            o(19.3, 19.1, 22.5, 21.9, 1.0, lk.bodyDark)
        else:                            # 측면 — 옆구리에 걸친 카메라
            r(14.6, 15.2, 15.8, 18.4, STRAP)
            o(13.8, 18.0, 20.0, 22.6, 1.3, OUTLINE)
            o(14.3, 18.5, 19.5, 22.1, 1.1, lk.bodyCol)
            x0, x1 = 19.2, min(19.2 + ln * 0.55, 27.0)
            cy = 20.3
            hd = dia * 0.42
            r(x0, cy - hd - 0.6, x1 + 0.6, cy + hd + 0.6, OUTLINE)
            r(x0, cy - hd, x1, cy + hd, lk.barrelCol)
            cir(x1 - 1.0, cy, hd * 0.75, GLASS)
    return b


# ---------------------------------------------------------------------------

LOOKS = {
    "시작 컴팩트": Look(0, SILVER, SILVER_D, BLUE, 1.6, 3.0, DARK_LENS, False, False, True),
    "슈퍼줌 컴팩트": Look(0, BLACK, BLACK_D, RED, 2.6, 3.4, DARK_LENS, False, False, True),
    "레트로 컴팩트": Look(0, LEATHER, LEATHER_D, SILVER, 1.8, 3.2, DARK_LENS, False, True, False),
    "브릿지 60x": Look(1, BLACK, BLACK_D, RED, 5.0, 4.6, DARK_LENS, True, True, True),
    "풀프레임 컴팩트": Look(0, GRAPH, GRAPH_D, SILVER, 1.6, 3.8, DARK_LENS, True, True, False),
    "미러리스+번들": Look(2, BLACK, BLACK_D, BLUE, 2.0, 3.2, DARK_LENS, False, False, True),
    "미러리스+70-300": Look(2, BLACK, BLACK_D, GOLD, 4.2, 3.8, DARK_LENS, True, True, False),
    "DSLR+150-600": Look(3, BLACK, BLACK_D, GOLD, 7.0, 5.0, DARK_LENS, True, True, False),
    "미러리스+200-600": Look(2, GRAPH, GRAPH_D, SILVER, 7.6, 5.2, GRAY_LENS, True, True, False),
    "플래그십+600F4": Look(4, BLACK, BLACK_D, RED, 9.4, 7.4, WHITE_LENS, True, True, False),
    "M43+300F4": Look(2, BLACK, BLACK_D, GREEN, 6.6, 5.2, WHITE_LENS, True, True, False),
    "중형+55mm": Look(5, GRAPH, GRAPH_D, SILVER, 2.6, 4.6, DARK_LENS, False, True, False),
}


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/cameras.png"
    from PIL import Image

    scale = 6
    cols = len(LOOKS)
    cell_w, cell_h = 34, 34
    sheet = Image.new("RGBA", (cell_w * 8 * scale, cell_h * cols * scale), (40, 44, 52, 255))
    for row, (nm, lk) in enumerate(LOOKS.items()):
        tiles = [
            cam_icon(lk), cam_profile(lk),
            cam_held(lk, 0, True), cam_held(lk, 2, True), cam_held(lk, 1, True),
            cam_held(lk, 0, False), cam_held(lk, 2, False), cam_held(lk, 1, False),
        ]
        for col, t in enumerate(tiles):
            img = t.to_pil().resize((t.w * scale, t.h * scale), 0)
            sheet.paste(img, (col * cell_w * scale, row * cell_h * scale), img)
    sheet.save(out)
    print(f"saved {out}  ({cols} looks)")


if __name__ == "__main__":
    main()
