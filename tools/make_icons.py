#!/usr/bin/env python3
"""
Pizza and Bird : 피자와 새 — 런처 아이콘 생성기 (외부 의존성 없음)

24x24 픽셀 아트를 순수 Python으로 PNG로 렌더링하고,
각 밀도(mipmap-*dpi) 크기로 최근접 이웃 업스케일합니다.

사용: python3 tools/make_icons.py
"""
import os
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "app", "src", "main", "res")

# 24x24 픽셀 아트 ('.' = 투명)
# b: 새 몸통, w: 새 날개, c: 새 배, k: 새 부리, e: 눈, t: 꼬리
# P: 피자 크러스트, C: 치즈, R: 페퍼로니, G: 바탕, g: 바탕 음영
ART = [
    "........................",
    "..GGGGGGGGGGGGGGGGGGGG..",
    "..GggggggggggggggggggG..",
    "..GGGGGGGGGGGGGGGGGGGG..",
    "..GGGbb.....GGGGGGGGGG..",
    "..GGbbbb....GGGGGGGGGG..",
    "..GGbebb....GGGGGGGGGG..",
    "..GGbbbb....GGGGGGGGGG..",
    "..GGkbbb....GGGGGGGGGG..",
    "..GGbbbbbb..GGGGGGGGGG..",
    "..GGbccccbb.GGGGGGGGGG..",
    "..GGbcccccbtGGGGGGGGGG..",
    "..GGbbccccbttGGGGGGGGG..",
    "..GGGGbbbbbbtGGGGGGGGG..",
    "..GGGPPPPPPPGGGGGGGGGG..",
    "..GPCCCCCCCCPGGGGGGGGG..",
    "..GPCRRCCRRCPGGGGGGGGG..",
    "..GPCCCCCCCCPGGGGGGGGG..",
    "..GPCRCCCCRCPGGGGGGGGG..",
    "..GPCCCRRCCCPGGGGGGGGG..",
    "..GPCCCCCCCCPGGGGGGGGG..",
    "..GPCCRCCCCRPGGGGGGGGG..",
    "..GPPPPPPPPPPGGGGGGGGG..",
    "..GGGGGGGGGGGGGGGGGGGG..",
]

PALETTE = {
    "G": (0xCF, 0xEE, 0xDC, 0xFF),  # 민트 바탕
    "g": (0xB8, 0xE0, 0xC8, 0xFF),
    "b": (0xA3, 0x70, 0x3F, 0xFF),  # 참새 몸통
    "c": (0xF2, 0xE2, 0xC4, 0xFF),  # 배
    "w": (0x7A, 0x4F, 0x2A, 0xFF),  # 날개
    "k": (0xF2, 0xA3, 0x3C, 0xFF),  # 부리
    "e": (0x2E, 0x26, 0x20, 0xFF),  # 눈
    "t": (0x7A, 0x4F, 0x2A, 0xFF),  # 꼬리
    "P": (0xE8, 0xA7, 0x5C, 0xFF),  # 크러스트
    "C": (0xF7, 0xCE, 0x5B, 0xFF),  # 치즈
    "R": (0xE2, 0x57, 0x4C, 0xFF),  # 페퍼로니
}


def render_base_pixels():
    """ART 문자열 -> RGBA 2차원 배열 + 라운드 코너 처리"""
    h = len(ART)
    w = max(len(r) for r in ART)
    px = []
    for y in range(h):
        row = []
        for x in range(w):
            ch = ART[y][x] if x < len(ART[y]) else "."
            row.append(PALETTE.get(ch, (0, 0, 0, 0)))
        px.append(row)
    # 라운드 코너 (반경 4px)
    r = 4
    for y in range(h):
        for x in range(w):
            if x < r and y < r:
                if (x - r) ** 2 + (y - r) ** 2 > r * r:
                    px[y][x] = (0, 0, 0, 0)
            if x >= w - r and y < r:
                if (x - (w - r - 1)) ** 2 + (y - r) ** 2 > r * r:
                    px[y][x] = (0, 0, 0, 0)
            if x < r and y >= h - r:
                if (x - r) ** 2 + (y - (h - r - 1)) ** 2 > r * r:
                    px[y][x] = (0, 0, 0, 0)
            if x >= w - r and y >= h - r:
                if (x - (w - r - 1)) ** 2 + (y - (h - r - 1)) ** 2 > r * r:
                    px[y][x] = (0, 0, 0, 0)
    return px, w, h


def write_png(path, px, w, h):
    """순수 Python PNG 작성 (RGBA, 필터 0)"""
    raw = b""
    for y in range(h):
        raw += b"\x00"
        for x in range(w):
            raw += bytes(px[y][x])

    def chunk(tag, data):
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    with open(path, "wb") as f:
        f.write(png)


def scale_up(px, w, h, k):
    out = []
    for y in range(h * k):
        row = []
        sy = y // k
        for x in range(w * k):
            row.append(px[sy][x // k])
        out.append(row)
    return out, w * k, h * k


def main():
    px, w, h = render_base_pixels()
    sizes = {"mdpi": 2, "hdpi": 3, "xhdpi": 4, "xxhdpi": 6, "xxxhdpi": 8}
    for name, k in sizes.items():
        d = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(d, exist_ok=True)
        up, uw, uh = scale_up(px, w, h, k)
        for fname in ("ic_launcher.png", "ic_launcher_round.png"):
            path = os.path.join(d, fname)
            write_png(path, up, uw, uh)
            print(f"{path} ({uw}x{uh})")
    print("아이콘 생성 완료!")


if __name__ == "__main__":
    main()
