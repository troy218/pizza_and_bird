"""
새를 찍는 화면(카메라 뷰파인더) 프리뷰 생성 스크립트.

Viewfinder.kt / Overlays.kt(PhotoResultOverlay) 의 그리기 좌표를 그대로 옮겨
실제 게임에서 어떻게 보이는지 미리 확인한다. (게임 코드에는 영향 없음)

    python3 tools/preview_viewfinder.py

결과: docs/viewfinder_preview.png

- 폰트: 나눔고딕(있으면) → PIL 기본 폰트
    pip install koreanize-matplotlib   # 폰트가 함께 들어 있다
- PIL은 RGBA 이미지에 반투명 도형을 그릴 때 블렌딩하지 않으므로,
  모든 베이스 이미지를 RGB로 두고 ImageDraw.Draw(im, "RGBA") 로 그린다.
"""
import math
import os
import random
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "docs", "viewfinder_preview.png")

VW, VH = 960, 540          # 가상 해상도 (Game.kt)
WORLD_SCALE = 2.0
SCALE = 1.5                # 프리뷰 확대 배율

FONT_CANDIDATES = [
    "/tmp/fonts/NanumGothicBold.ttf",
    "/tmp/fonts/NanumGothic.ttf",
    os.path.join(HERE, "NanumGothicBold.ttf"),
    "/usr/share/fonts/truetype/nanum/NanumGothicBold.ttf",
    "/usr/share/fonts/opentype/noto/NotoSansCJKkr-Bold.otf",
]

_font_cache = {}


def font(size, bold=True):
    key = (int(round(size)), bold)
    if key in _font_cache:
        return _font_cache[key]
    path = None
    for p in FONT_CANDIDATES:
        if os.path.exists(p):
            path = p
            break
    if path is None:
        # 한글 글꼴이 하나도 없을 때: 기본 비트맵 폰트 대신 유니코드가 넓게 들어있는
        # 장식용(FLING) 글꼴로 대체한다. 프리뷰 품질은 떨어지지만 레이아웃 확인은 가능.
        path = os.environ.get("PB_PREVIEW_FONT", "/usr/share/fonts/opentype/urw-base35/"
                              "StandardSymbolsPS.otf")
        if not os.path.exists(path):
            path = None
    f = ImageFont.truetype(path, int(round(size))) if path else ImageFont.load_default(int(round(size)))
    _font_cache[key] = f
    return f


def measure(text, f, d):
    return d.textlength(text, font=f)


def new_img(w=VW, h=VH, color=(0, 0, 0)):
    im = Image.new("RGB", (int(w), int(h)), color)
    return im, ImageDraw.Draw(im, "RGBA")


# ---------------------------------------------------------------------------
# 프리뷰용 가짜 월드
# ---------------------------------------------------------------------------

def draw_world(d):
    rnd = random.Random(11)
    d.rectangle([0, 0, VW, VH], fill=(0x96, 0xD0, 0x7A))
    for _ in range(2800):
        x, y = rnd.randrange(0, VW), rnd.randrange(0, VH)
        c = rnd.choice([(0x87, 0xBC, 0x6E), (0xA5, 0xDC, 0x8A), (0x8E, 0xC7, 0x73), (0xA0, 0xD8, 0x84)])
        d.rectangle([x, y, x + 2, y + 1], fill=c)

    # 흙길
    d.rectangle([0, 300, VW, 344], fill=(0xC9, 0xA9, 0x7A))
    for _ in range(800):
        x, y = rnd.randrange(0, VW), rnd.randrange(300, 344)
        c = rnd.choice([(0xB9, 0x99, 0x6C), (0xDA, 0xBB, 0x8C)])
        d.rectangle([x, y, x + 3, y + 2], fill=c)

    # 연못
    d.ellipse([620, 150, 900, 245], fill=(0x6F, 0xB7, 0xD2))
    d.ellipse([632, 158, 884, 226], fill=(0x86, 0xC9, 0xDE))
    for i in range(4):
        d.rectangle([660 + i * 55, 176 + (i % 2) * 26, 700 + i * 55, 179 + (i % 2) * 26], fill=(255, 255, 255, 120))
    for i in range(9):
        d.ellipse([640 + i * 30, 200 + (i % 3) * 12, 656 + i * 30, 208 + (i % 3) * 12], fill=(0x5C, 0xA0, 0x74))

    # 나무 / 관목
    def tree(cx, cy, s=1.0):
        d.rectangle([cx - 4 * s, cy - 34 * s, cx + 4 * s, cy], fill=(0x8A, 0x5F, 0x3C))
        for r, col in ((30, (0x4F, 0x8B, 0x4A)), (23, (0x5D, 0xA0, 0x55)), (15, (0x6F, 0xB5, 0x63))):
            d.ellipse([cx - r * s, cy - 60 * s - r * s * 0.6, cx + r * s, cy - 60 * s + r * s * 0.9], fill=col)
    tree(120, 250, 1.15)
    tree(255, 205, 0.8)
    tree(880, 330, 1.0)
    for i in range(6):
        d.ellipse([40 + i * 46, 352, 74 + i * 46, 372], fill=(0x6F, 0xB5, 0x63))
        d.ellipse([52 + i * 46, 356, 64 + i * 46, 366], fill=(0x88, 0xC9, 0x77))

    # 벤치
    d.rectangle([470, 296, 560, 306], fill=(0xA9, 0x77, 0x4A))
    d.rectangle([478, 306, 486, 318], fill=(0x8A, 0x5F, 0x3C))
    d.rectangle([546, 306, 554, 318], fill=(0x8A, 0x5F, 0x3C))
    # 꽃
    for i in range(14):
        x, y = rnd.randrange(10, 950), rnd.randrange(360, 530)
        d.rectangle([x, y, x + 2, y + 4], fill=(0x6F, 0xAE, 0x57))
        d.ellipse([x - 2, y - 4, x + 4, y + 2], fill=rnd.choice([(0xFD, 0xF6, 0xE8), (0xF2, 0xA3, 0xB3), (0xF7, 0xE0, 0x6B)]))


def pixel_bird(d, cx, cy, kind="sparrow"):
    if kind == "magpie":
        body, wing, head, belly = (0x2E, 0x32, 0x3A), (0x1F, 0x22, 0x2A), (0x2E, 0x32, 0x3A), (0xF0, 0xF0, 0xEC)
    elif kind == "kingfisher":
        body, wing, head, belly = (0x2E, 0x86, 0xC1), (0x1F, 0x6B, 0xA0), (0x2E, 0x86, 0xC1), (0xF2, 0xA8, 0x54)
    elif kind == "heron":
        body, wing, head, belly = (0xE8, 0xEC, 0xF2), (0xC9, 0xD2, 0xDE), (0xE8, 0xEC, 0xF2), (0xFF, 0xFF, 0xFF)
    else:
        body, wing, head, belly = (0xA8, 0x7A, 0x54), (0x8C, 0x63, 0x42), (0xB6, 0x8A, 0x60), (0xE6, 0xD3, 0xB6)
    s = 2
    d.ellipse([cx - 7 * s, cy - 5 * s, cx + 7 * s, cy + 6 * s], fill=body)
    d.ellipse([cx - 3 * s, cy - 1 * s, cx + 7 * s, cy + 6 * s], fill=belly)
    d.ellipse([cx + 3 * s, cy - 10 * s, cx + 11 * s, cy - 2 * s], fill=head)
    d.ellipse([cx - 6 * s, cy - 4 * s, cx + 2 * s, cy + 4 * s], fill=wing)
    d.polygon([(cx + 11 * s, cy - 6 * s), (cx + 16 * s, cy - 4 * s), (cx + 11 * s, cy - 2 * s)], fill=(0xE8, 0xA0, 0x3C))
    d.polygon([(cx - 7 * s, cy - 2 * s), (cx - 15 * s, cy - 6 * s), (cx - 14 * s, cy + 3 * s)], fill=wing)
    d.rectangle([cx + 8 * s, cy - 7 * s, cx + 9 * s, cy - 6 * s], fill=(20, 20, 24))
    d.rectangle([cx - 2 * s, cy + 6 * s, cx - 1 * s, cy + 9 * s], fill=(0xD8, 0x9B, 0x3C))
    d.rectangle([cx + 3 * s, cy + 6 * s, cx + 4 * s, cy + 9 * s], fill=(0xD8, 0x9B, 0x3C))


def draw_player(d, cx, cy):
    d.ellipse([cx - 12, cy + 12, cx + 12, cy + 18], fill=(30, 40, 30, 70))
    d.rectangle([cx - 6, cy - 4, cx + 6, cy + 10], fill=(0x4F, 0x86, 0xC8))
    d.rectangle([cx - 5, cy + 10, cx - 1, cy + 18], fill=(0x3A, 0x3F, 0x52))
    d.rectangle([cx + 1, cy + 10, cx + 5, cy + 18], fill=(0x3A, 0x3F, 0x52))
    d.rectangle([cx - 6, cy - 14, cx + 6, cy - 4], fill=(0xF0, 0xC9, 0xA0))
    d.rectangle([cx - 8, cy - 18, cx + 8, cy - 11], fill=(0x53, 0x3A, 0x2A))
    d.rectangle([cx - 10, cy - 12, cx + 10, cy - 10], fill=(0x6B, 0x4A, 0x33))



# ---------------------------------------------------------------------------
# HUD 컨트롤 (Hud.kt 와 같은 dp 좌표) — 카메라 모드에선 스탯/미니맵은 숨겨진다
# ---------------------------------------------------------------------------

def draw_hud_controls(d, W, H, density, photo_mode=True, clock=2.6, toast=None, active=()):
    dp = lambda v: v * density

    # D패드
    r = dp(54)
    cx, cy = dp(26) + r, H - dp(24) - r
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(40, 36, 54, 88))
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(248, 239, 220, 150), width=max(1, int(dp(2))))
    tri = dp(10)
    inn = r * 0.62
    for (dx, dy) in ((0, -1), (0, 1), (-1, 0), (1, 0)):
        px, py = -dy, dx
        pts = [(cx + dx * inn + dx * tri, cy + dy * inn + dy * tri),
               (cx + dx * inn - dx * tri * 0.5 + px * tri * 0.8, cy + dy * inn - dy * tri * 0.5 + py * tri * 0.8),
               (cx + dx * inn - dx * tri * 0.5 - px * tri * 0.8, cy + dy * inn - dy * tri * 0.5 - py * tri * 0.8)]
        d.polygon(pts, fill=(248, 239, 220, 205))

    a_r = dp(31)
    a_cx, a_cy = W - dp(26) - a_r, H - dp(26) - a_r
    arc_r = dp(19)
    arc_dist = a_r + dp(9) + arc_r

    def arc(angle_deg):
        rad = math.radians(angle_deg)
        return a_cx + arc_dist * math.cos(rad), a_cy - arc_dist * math.sin(rad)

    b_r = arc_r
    b_cx, b_cy = arc(72)
    cam_r = arc_r
    cam_cx, cam_cy = arc(120)
    eat_r = arc_r
    eat_cx, eat_cy = arc(168)
    menu_r = dp(17)
    menu_cx, menu_cy = dp(18) + menu_r, H - dp(18) - menu_r

    def circle_button(cx, cy, rr, col, outline_a=190):
        d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=col)
        d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], outline=(248, 239, 220, outline_a), width=max(1, int(dp(2))))

    # 카메라 (촬영 모드에선 붉게 + 펄스 링)
    if photo_mode:
        pulse = 0.5 + 0.5 * math.sin(clock * 3.4)
        pr = cam_r + dp(5) + dp(4) * pulse
        d.ellipse([cam_cx - pr, cam_cy - pr, cam_cx + pr, cam_cy + pr], outline=(226, 87, 76, 46), width=max(1, int(dp(9))))
        p2 = cam_r + dp(3) + dp(3) * pulse
        d.ellipse([cam_cx - p2, cam_cy - p2, cam_cx + p2, cam_cy + p2], outline=(226, 87, 76, 210), width=max(1, int(dp(2.6))))
        arc_r = cam_r + dp(8)
        for i in range(6):
            base = (clock * 96) % 360 + i * 60
            d.arc([cam_cx - arc_r, cam_cy - arc_r, cam_cx + arc_r, cam_cy + arc_r], base, base + 20,
                  fill=(255, 232, 220, 225), width=max(1, int(dp(3))))
        circle_button(cam_cx, cam_cy, cam_r, (226, 87, 76), 200)
    else:
        circle_button(cam_cx, cam_cy, cam_r, (74, 74, 88, 220))

    icon_camera(d, cam_cx - dp(10), cam_cy - dp(8.5), dp(20) / 16, (248, 239, 220, 240))
    circle_button(a_cx, a_cy, a_r, (242, 182, 60) if "A" not in active else (217, 155, 38))
    circle_button(b_cx, b_cy, b_r, (195, 163, 232) if "B" not in active else (159, 127, 200))
    circle_button(eat_cx, eat_cy, eat_r, (242, 182, 60, 235) if "EAT" not in active else (217, 155, 38, 235))
    circle_button(menu_cx, menu_cy, menu_r, (74, 74, 88, 220))

    f = font(dp(18))
    tw = measure("A", f, d)
    d.text((a_cx - tw / 2, a_cy - dp(12)), "A", font=f, fill=(255, 252, 244))
    f = font(dp(14))
    tw = measure("B", f, d)
    d.text((b_cx - tw / 2, b_cy - dp(9)), "B", font=f, fill=(255, 252, 244))
    # 피자 아이콘 (간식 버튼)
    ps = dp(20)
    d.polygon([(eat_cx, eat_cy - ps / 2), (eat_cx - ps / 2, eat_cy + ps / 2), (eat_cx + ps / 2, eat_cy + ps / 2)],
              fill=(247, 206, 91))
    d.polygon([(eat_cx, eat_cy - ps / 2), (eat_cx - ps / 2, eat_cy + ps / 2), (eat_cx + ps / 2, eat_cy + ps / 2)],
              outline=(203, 150, 60), width=1)
    d.ellipse([eat_cx - ps * 0.11, eat_cy + ps * 0.02, eat_cx + ps * 0.11, eat_cy + ps * 0.24], fill=(196, 92, 74))
    d.ellipse([eat_cx - ps * 0.26, eat_cy + ps * 0.16, eat_cx - ps * 0.08, eat_cy + ps * 0.34], fill=(196, 92, 74))
    # 메뉴 (≡)
    for i in (-1, 0, 1):
        y = menu_cy + i * dp(4)
        d.line([menu_cx - dp(6), y, menu_cx + dp(6), y], fill=(248, 239, 220, 220), width=max(1, int(dp(2.2))))

    # 토스트 (카메라 모드에선 상단 뷰파인더 바를 피해 내려서)
    if toast:
        f = font(dp(12.5))
        tw = measure(toast, f, d)
        cxm = W / 2
        ty = dp(20) + dp(62)
        pad = dp(9)
        d.rounded_rectangle([cxm - tw / 2 - pad, ty - dp(12), cxm + tw / 2 + pad, ty + dp(13)],
                            radius=dp(12), fill=(248, 239, 220, 210), outline=(107, 79, 53, 230), width=max(1, int(dp(1.5))))
        d.text((cxm - tw / 2, ty - dp(9)), toast, font=f, fill=(74, 55, 40))


# ---------------------------------------------------------------------------
# 뷰파인더 (Viewfinder.kt 와 같은 좌표)
# ---------------------------------------------------------------------------

DASH = (11.0, 9.0)


def dashed_circle(d, cx, cy, r, color, width=2, dash=DASH, phase=0.0):
    circ = 2 * math.pi * r
    unit = circ / 240.0
    on, remaining, a = True, dash[0] - (phase % (dash[0] + dash[1])), 0.0
    while a < circ:
        step = min(unit, remaining)
        if on:
            a1, a2 = a / r, (a + step) / r
            d.line([cx + r * math.cos(a1), cy + r * math.sin(a1),
                    cx + r * math.cos(a2), cy + r * math.sin(a2)], fill=color, width=int(width))
        a += step
        remaining -= step
        if remaining <= 0:
            on = not on
            remaining = dash[0] if on else dash[1]


def arrow(d, x, y, color):
    """작은 픽셀 화살표(▼) — 텍스트 대신"""
    d.polygon([(x - 4, y - 4), (x + 4, y - 4), (x, y + 2)], fill=color)


def icon_camera(d, x, y, s=1.0, color=(246, 240, 224, 210)):
    d.rounded_rectangle([x, y, x + 13 * s, y + 10 * s], radius=2 * s, fill=color)
    d.rectangle([x + 4 * s, y - 2 * s, x + 8 * s, y + 1 * s], fill=color)
    d.ellipse([x + 4 * s, y + 2 * s, x + 9 * s, y + 8 * s], fill=(16, 14, 24, 230))


def icon_sun(d, x, y, r=5.0):
    d.ellipse([x - r, y - r, x + r, y + r], fill=(247, 206, 91))
    for i in range(8):
        a = math.radians(i * 45)
        d.line([x + math.cos(a) * (r + 2), y + math.sin(a) * (r + 2),
                x + math.cos(a) * (r + 4), y + math.sin(a) * (r + 4)], fill=(247, 206, 91), width=2)


def icon_moon(d, x, y, r=5.0):
    d.ellipse([x - r, y - r, x + r, y + r], fill=(247, 233, 168))
    d.ellipse([x - r + 3, y - r - 1, x + r + 3, y + r - 1], fill=(14, 12, 22))


def draw_viewfinder(d, birds, player, cam, range_tiles, camera_level, time_label,
                    photos, night, clock, camera_name, focus_idx, focus_dist=None, enter=1.0):
    range_px = range_tiles * 16 * WORLD_SCALE
    px = (player[0] - cam[0]) * WORLD_SCALE
    py = (player[1] - cam[1]) * WORLD_SCALE
    sx = lambda wx: (wx - cam[0]) * WORLD_SCALE
    sy = lambda wy: (wy - cam[1]) * WORLD_SCALE

    d.rectangle([0, 0, VW, VH], fill=(42, 60, 92, 16))          # 카메라 색감

    rnd = random.Random(5)                                      # 필름 그레인
    for _ in range(1500):
        x, y = rnd.randrange(0, VW), rnd.randrange(0, VH)
        d.point((x, y), fill=(255, 252, 240, 40) if rnd.random() < 0.5 else (20, 16, 30, 42))

    inset, steps = 96.0, 8                                      # 비네트
    band = inset / steps
    for i in range(steps):
        k = (steps - i) / steps
        a = max(1, int(132 * k * k))
        o = band * i
        d.rectangle([0, o, VW, o + band], fill=(12, 10, 20, a))
        d.rectangle([0, VH - o - band, VW, VH - o], fill=(12, 10, 20, a))
        d.rectangle([o, 0, o + band, VH], fill=(12, 10, 20, a))
        d.rectangle([VW - o - band, 0, VW - o, VH], fill=(12, 10, 20, a))
    d.rectangle([0, 0, VW, 78], fill=(10, 8, 18, 40))
    d.rectangle([0, VH - 150, VW, VH], fill=(10, 8, 18, 30))

    # 사거리
    d.ellipse([px - range_px, py - range_px, px + range_px, py + range_px], fill=(255, 250, 235, 10))
    d.ellipse([px - range_px * 0.67, py - range_px * 0.67, px + range_px * 0.67, py + range_px * 0.67], fill=(255, 250, 235, 10))
    dashed_circle(d, px, py, range_px, (255, 250, 235, 120), 2, DASH, clock * 14)
    d.ellipse([px - range_px * 0.34, py - range_px * 0.34, px + range_px * 0.34, py + range_px * 0.34],
              outline=(111, 186, 107, 74), width=1)
    d.ellipse([px - range_px * 0.67, py - range_px * 0.67, px + range_px * 0.67, py + range_px * 0.67],
              outline=(242, 182, 60, 74), width=1)

    f = font(13)
    lbl = "사거리 %.1f칸" % range_tiles
    tw = measure(lbl, f, d)
    label_y = min(max(py + range_px + 18, 120), VH - 104)
    d.rounded_rectangle([px - tw / 2 - 7, label_y - 12, px + tw / 2 + 7, label_y + 5], radius=5, fill=(16, 14, 24, 150))
    d.text((px - tw / 2, label_y - 11), lbl, font=f, fill=(246, 240, 224, 215))

    f = font(12)
    for txt, x, col in (("★3", px - range_px * 0.34 - 6, (0x9B, 0xD9, 0x8F)),
                        ("★2", px - range_px * 0.67 - 6, (0xF2, 0xC8, 0x6B))):
        tw = measure(txt, f, d)
        if x - tw < 8:
            continue
        d.rounded_rectangle([x - tw - 5, py - 10, x + 5, py + 6], radius=4, fill=(16, 14, 24, 150))
        d.text((x - tw, py - 9), txt, font=f, fill=col)

    # ----- 새 -----
    for i, b in enumerate(birds):
        bx, by = sx(b["x"]), sy(b["y"])
        d_tiles = math.hypot(b["x"] - player[0], b["y"] - player[1]) / 16
        in_range = d_tiles <= range_tiles
        ratio = max(0.001, d_tiles / range_tiles)
        stars = 3 if ratio < 0.34 else (2 if ratio < 0.67 else 1)
        col = (0x7F, 0xD0, 0x7A) if stars == 3 else ((0xF2, 0xC8, 0x6B) if stars == 2 else (0xE2, 0x57, 0x4C))

        if i == focus_idx:
            pulse = (1 + 0.02 * math.sin(clock * 5.5)) if in_range else (1 + 0.05 * math.sin(clock * 3))
            half_w = (16 * WORLD_SCALE * 0.5 + 13) * pulse
            half_h = (15 * WORLD_SCALE * 0.5 + 13) * pulse
            box = [bx - half_w, by - half_h, bx + half_w, by + half_h]
            d.rectangle(box, fill=(10, 8, 18, 26))
            tick = min(12, (box[2] - box[0]) * 0.34)
            for (x1, y1, x2, y2, x3, y3) in (
                (box[0], box[1] + tick, box[0], box[1], box[0] + tick, box[1]),
                (box[2] - tick, box[1], box[2], box[1], box[2], box[1] + tick),
                (box[2], box[3] - tick, box[2], box[3], box[2] - tick, box[3]),
                (box[0] + tick, box[3], box[0], box[3], box[0], box[3] - tick),
            ):
                d.line([x1, y1, x2, y2], fill=col + (240,), width=2)
                d.line([x2, y2, x3, y3], fill=col + (240,), width=2)

            name = b["name"] if b["seen"] else "??? 미확인"
            star_txt = "★" * stars + "☆" * (3 - stars)
            info = ("%s  ·  %.1f칸" % (star_txt, d_tiles)) if in_range else ("더 가까이!  ·  %.1f칸" % d_tiles)
            fn, fi = font(14), font(12)
            plate_w = max(measure(name, fn, d), measure(info, fi, d)) + 22
            plate_h = 34
            plate_cx = min(max(bx, 46 + plate_w / 2), VW - 46 - plate_w / 2)
            plate_top = box[1] - plate_h - 8
            if plate_top < 84:
                plate_top = box[3] + 8
            plate = [plate_cx - plate_w / 2, plate_top, plate_cx + plate_w / 2, plate_top + plate_h]
            d.rounded_rectangle(plate, radius=7, fill=(14, 12, 22, 205), outline=col + (215,), width=2)
            d.ellipse([plate[0] + 7.6, plate[1] + plate_h / 2 - 3.4, plate[0] + 14.4, plate[1] + plate_h / 2 + 3.4],
                      fill=col + (235,))
            d.text((plate[0] + 20, plate[1] + 3), name, font=fn, fill=(250, 246, 236) if b["seen"] else (208, 203, 218))
            d.text((plate[0] + 20, plate[1] + 19), info, font=fi, fill=col + (235,))
        elif in_range:
            star_txt = "★" * stars + "☆" * (3 - stars)
            f2 = font(12)
            tw = measure(star_txt, f2, d)
            top = by - 15 * WORLD_SCALE * 0.5 - 24
            d.rounded_rectangle([bx - tw / 2 - 6, top - 11, bx + tw / 2 + 6, top + 6], radius=5,
                                fill=(16, 14, 24, 150), outline=col + (155,), width=1)
            d.text((bx - tw / 2, top - 10), star_txt, font=f2, fill=(246, 240, 224, 235))

    # ----- 프레임 -----
    m, ln = 22.0, 54.0
    white = (255, 250, 235, 235)
    gold = (242, 208, 107, 195)
    for (x1, y1, x2, y2) in (
        (m, m + ln, m, m), (m, m, m + ln, m),
        (VW - m - ln, m, VW - m, m), (VW - m, m, VW - m, m + ln),
        (VW - m, VH - m - ln, VW - m, VH - m), (VW - m, VH - m, VW - m - ln, VH - m),
        (m + ln, VH - m, m, VH - m), (m, VH - m, m, VH - m - ln),
    ):
        d.line([x1, y1, x2, y2], fill=white, width=3)
    for (x1, y1, x2, y2) in (
        (m + 9, m + 20, m + 9, m + 9), (m + 9, m + 9, m + 20, m + 9),
        (VW - m - 20, m + 9, VW - m - 9, m + 9), (VW - m - 9, m + 9, VW - m - 9, m + 20),
        (VW - m - 9, VH - m - 20, VW - m - 9, VH - m - 9), (VW - m - 9, VH - m - 9, VW - m - 20, VH - m - 9),
        (m + 20, VH - m - 9, m + 9, VH - m - 9), (m + 9, VH - m - 9, m + 9, VH - m - 20),
    ):
        d.line([x1, y1, x2, y2], fill=gold, width=2)

    gw = (VW - m * 2) / 3
    gh = (VH - m * 2) / 3
    for i in (1, 2):
        d.line([m + gw * i, m, m + gw * i, VH - m], fill=(255, 250, 235, 30), width=1)
        d.line([m, m + gh * i, VW - m, m + gh * i], fill=(255, 250, 235, 30), width=1)

    y = m + 24
    while y < VH - m - 24:
        tl = 11 if int(y / 44) % 2 == 0 else 6
        d.line([m, y, m + tl, y], fill=(255, 250, 235, 50), width=1)
        d.line([VW - m, y, VW - m - tl, y], fill=(255, 250, 235, 50), width=1)
        y += 22

    for (x1, y1, x2, y2) in ((VW / 2 - 12, VH / 2, VW / 2 - 4, VH / 2), (VW / 2 + 4, VH / 2, VW / 2 + 12, VH / 2),
                             (VW / 2, VH / 2 - 12, VW / 2, VH / 2 - 4), (VW / 2, VH / 2 + 4, VW / 2, VH / 2 + 12)):
        d.line([x1, y1, x2, y2], fill=(255, 250, 235, 75), width=1)

    draw_top_bar(d, time_label, photos, camera_name, camera_level, range_tiles, night, clock, enter)
    draw_bottom_bar(d, range_tiles, camera_level, clock, focus_dist, enter)
    if focus_idx is None and clock < 10:
        fade = 1 - max(0, min(1, (clock - 8) / 2))
        a = int(255 * fade)
        bob = math.sin(clock * 2.2) * 3
        f = font(15)
        s = "새를 탭해 촬영하세요"
        tw = measure(s, f, d)
        d.text((VW / 2 - tw / 2, VH * 0.60 + bob), s, font=f, fill=(255, 250, 235, a))
        f2 = font(12)
        s2 = "카메라 버튼을 다시 누르면 나갑니다"
        tw2 = measure(s2, f2, d)
        d.text((VW / 2 - tw2 / 2, VH * 0.60 + 24 + bob), s2, font=f2, fill=(226, 220, 206, int(a * 0.75)))


def draw_top_bar(d, time_label, photos, camera_name, camera_level, range_tiles, night, clock, enter=1.0):
    y = 26 - (1 - enter) * 16
    blink = 1.0 if math.sin(clock * 4.2) > -0.2 else 0.25
    f = font(13)
    bw = measure("PHOTO", f, d) + 46
    badge = [40, y, 40 + bw, y + 28]
    d.rounded_rectangle(badge, radius=7, fill=(14, 12, 22, int(168 * enter)), outline=(246, 240, 224, int(90 * enter)), width=1)
    d.ellipse([badge[0] + 5, badge[1] + 5, badge[0] + 23, badge[1] + 23], fill=(226, 87, 76, int(70 * enter * blink)))
    d.ellipse([badge[0] + 9.4, badge[1] + 9.4, badge[0] + 18.6, badge[1] + 18.6], fill=(226, 87, 76, int(240 * enter * blink)))
    d.text((badge[0] + 26, badge[1] + 6), "PHOTO", font=f, fill=(250, 246, 236, int(242 * enter)))

    fm = font(16)
    cw = measure(time_label, fm, d) + 42
    cr = [VW / 2 - cw / 2, y, VW / 2 + cw / 2, y + 28]
    d.rounded_rectangle(cr, radius=7, fill=(14, 12, 22, int(168 * enter)))
    if night:
        icon_moon(d, cr[0] + 15, cr[1] + 14)
    else:
        icon_sun(d, cr[0] + 15, cr[1] + 14, 4.5)
    d.text((cr[0] + 27, cr[1] + 4), time_label, font=fm, fill=(250, 246, 236, int(242 * enter)))

    f = font(13)
    f2 = font(12)
    name_txt = "%s  Lv.%d" % (camera_name, camera_level)
    sub_txt = "사거리 %.1f칸 · 찍은 사진 %d장" % (range_tiles, photos)
    bw = max(measure(name_txt, f, d), measure(sub_txt, f2, d)) + 24
    br = [VW - 40 - bw, y, VW - 40, y + 44]
    d.rounded_rectangle(br, radius=7, fill=(14, 12, 22, int(168 * enter)), outline=(246, 240, 224, int(90 * enter)), width=1)
    d.text((br[0] + 12, br[1] + 5), name_txt, font=f, fill=(250, 246, 236, int(242 * enter)))
    d.text((br[0] + 12, br[1] + 23), sub_txt, font=f2, fill=(214, 208, 224, int(205 * enter)))


def draw_bottom_bar(d, range_tiles, camera_level, clock, focus_dist=None, enter=1.0):
    y = VH - 92 + (1 - enter) * 18
    box_w, box_h = 432.0, 58.0
    r = [VW / 2 - box_w / 2, y, VW / 2 + box_w / 2, y + box_h]
    d.rounded_rectangle(r, radius=10, fill=(14, 12, 22, int(168 * enter)), outline=(246, 240, 224, int(90 * enter)), width=1)

    iso = 100 * (1 << (min(max(camera_level - 1, 0), 4) // 2))
    fnum = {1: "f/2.2", 2: "f/2.2", 3: "f/2.8", 4: "f/2.8"}.get(camera_level, "f/4.0")
    shutter = {1: "1/60", 2: "1/90", 3: "1/125", 4: "1/200"}.get(camera_level, "1/320")
    d.text((r[0] + 14, r[1] + 8), "ISO %d  %s  %s" % (iso, fnum, shutter), font=font(13), fill=(232, 226, 240, 225))
    d.text((r[0] + 14, r[1] + 28), "도감 12종 · 관측 34컷", font=font(11), fill=(206, 200, 216, 195))

    gx, gy, gw, gh = r[2] - 190, r[1] + 24, 160.0, 9.0
    d.rounded_rectangle([gx, gy, gx + gw, gy + gh], radius=4, fill=(32, 28, 44, 200))
    d.rounded_rectangle([gx + 1, gy + 1, gx + gw * 0.34, gy + gh - 1], radius=4, fill=(111, 186, 107, 175))
    d.rounded_rectangle([gx + gw * 0.34, gy + 1, gx + gw * 0.67, gy + gh - 1], radius=4, fill=(242, 182, 60, 155))
    d.rounded_rectangle([gx + gw * 0.67, gy + 1, gx + gw - 1, gy + gh - 1], radius=4, fill=(226, 87, 76, 135))

    f = font(11)
    if focus_dist is not None:
        ratio = min(max(focus_dist / range_tiles, 0), 1)
        mx = gx + gw * ratio
        d.line([mx, gy - 5, mx, gy + gh + 5], fill=(255, 252, 244, 250), width=2)
        stars = 3 if ratio < 0.34 else (2 if ratio < 0.67 else 1)
        col = (0x7F, 0xD0, 0x7A) if stars == 3 else ((0xF2, 0xC8, 0x6B) if stars == 2 else (0xE2, 0x57, 0x4C))
        s = "예상 " + "★" * stars + "☆" * (3 - stars)
        d.text((gx + gw - measure(s, f, d), r[1] + 42), s, font=f, fill=col + (240,))
        d.text((gx, r[1] + 42), "거리 %.1f칸" % focus_dist, font=f, fill=(214, 208, 224, 205))
    else:
        d.text((gx, r[1] + 42), "새를 찾는 중…", font=f, fill=(214, 208, 224, 205))
        sx = gx + (0.5 + 0.5 * math.sin(clock * 3)) * gw
        d.ellipse([sx - 3.2, gy + gh / 2 - 3.2, sx + 3.2, gy + gh / 2 + 3.2], fill=(246, 240, 224, 225))



def draw_shutter(d, e, closing_p=1.0):
    mid = VH / 2.0
    edge = mid * e
    curve = 18 * e

    def pts(top):
        base = edge if top else VH - edge
        out = [(-4, base)]
        for i in range(21):
            t = i / 20
            bulge = curve * (1 - ((t - 0.5) * 2) ** 2)
            out.append((-4 + t * (VW + 8), base + bulge if top else base - bulge))
        out.append((VW + 4, base))
        return out

    d.polygon([(-4, -4), (VW + 4, -4), (VW + 4, edge)] + pts(True)[:0] + [(VW / 2, edge + curve), (-4, edge)],
              fill=(0x12, 0x0F, 0x1A))
    d.polygon([(-4, VH + 4), (VW + 4, VH + 4), (VW + 4, VH - edge), (VW / 2, VH - edge - curve), (-4, VH - edge)],
              fill=(0x12, 0x0F, 0x1A))
    d.line(pts(True), fill=(255, 236, 190, int(90 * e)), width=13)
    d.line(pts(True), fill=(255, 244, 214, int(200 * e)), width=2)
    d.line(pts(False), fill=(255, 236, 190, int(90 * e)), width=13)
    d.line(pts(False), fill=(255, 244, 214, int(200 * e)), width=2)

    if closing_p > 0.45:
        a = int(255 * ((closing_p - 0.45) / 0.55))
        f = font(28)
        tw = measure("찰칵!", f, d)
        d.text((VW / 2 - tw / 2 + 2, VH / 2 - 8), "찰칵!", font=f, fill=(10, 8, 16, int(a * 0.5)))
        d.text((VW / 2 - tw / 2, VH / 2 - 10), "찰칵!", font=f, fill=(255, 248, 232, a))


# ---------------------------------------------------------------------------
# 촬영 결과 (폴라로이드 인화)
# ---------------------------------------------------------------------------

def star_pts(cx, cy, r, n=10):
    inner = r * 0.44
    return [(cx + math.cos(math.radians(-90 + i * 36)) * (r if i % 2 == 0 else inner),
             cy + math.sin(math.radians(-90 + i * 36)) * (r if i % 2 == 0 else inner)) for i in range(n)]


def draw_photo(d, r, habitat, night, dp):
    x0, y0, x1, y1 = r
    w, h = x1 - x0, y1 - y0
    sky = {
        "coast": [(0x87, 0xCF, 0xE6), (0x9E, 0xDC, 0xEA), (0xB6, 0xE6, 0xEF), (0xCD, 0xEE, 0xF1), (0xDF, 0xF4, 0xEF), (0xED, 0xF8, 0xEE)],
        "water": [(0x87, 0xCF, 0xE6), (0x9E, 0xDC, 0xEA), (0xB6, 0xE6, 0xEF), (0xCD, 0xEE, 0xF1), (0xDF, 0xF4, 0xEF), (0xED, 0xF8, 0xEE)],
        "city": [(0x9C, 0xC2, 0xE4), (0xB0, 0xD0, 0xEA), (0xC4, 0xDC, 0xEF), (0xD6, 0xE5, 0xEE), (0xE5, 0xEB, 0xE8), (0xF1, 0xEF, 0xE4)],
        "forest": [(0x83, 0xC3, 0xE8), (0x9B, 0xD3, 0xEC), (0xB5, 0xE0, 0xEF), (0xCB, 0xE9, 0xE9), (0xDD, 0xF1, 0xE4), (0xEC, 0xF8, 0xEC)],
        "mountain": [(0x83, 0xC3, 0xE8), (0x9B, 0xD3, 0xEC), (0xB5, 0xE0, 0xEF), (0xCB, 0xE9, 0xE9), (0xDD, 0xF1, 0xE4), (0xEC, 0xF8, 0xEC)],
    }.get(habitat, [(0x8B, 0xCE, 0xEC), (0xA4, 0xDC, 0xF0), (0xBE, 0xE5, 0xF0), (0xD2, 0xEE, 0xE8), (0xE2, 0xF4, 0xE2), (0xF0, 0xF8, 0xE0)])
    if night:
        sky = [(0x0C, 0x15, 0x26), (0x13, 0x20, 0x38), (0x1B, 0x2C, 0x48), (0x24, 0x38, 0x57), (0x30, 0x45, 0x66), (0x3E, 0x52, 0x75)]
    sky_h = h * 0.64
    band = sky_h / len(sky)
    for i in range(len(sky)):
        d.rectangle([x0, y0 + band * i, x1, y0 + band * (i + 1) + 1], fill=sky[i])
    d.rectangle([x0, y0 + sky_h - band * 1.15, x1, y0 + sky_h + h * 0.012], fill=(255, 255, 250, 150))
    d.rectangle([x0, y0 + sky_h - band * 2.0, x1, y0 + sky_h - band * 1.15], fill=(255, 255, 250, 90))

    if night:
        for i in range(16):
            sx = x0 + w * ((i * 37 % 100) / 100)
            sy = y0 + h * 0.08 + h * 0.34 * ((i * 61 % 100) / 100)
            d.rectangle([sx, sy, sx + max(1.4, dp(1.6)), sy + max(1.4, dp(1.6))], fill=(252, 250, 236, 235))
    else:
        for (fx, fy, s) in ((0.16, 0.15, 0.18), (0.62, 0.26, 0.24)):
            cxx, cyy = x0 + w * fx, y0 + h * fy
            d.rounded_rectangle([cxx, cyy, cxx + w * s, cyy + h * s * 0.36], radius=h * s * 0.18, fill=(255, 255, 255, 230))
            d.ellipse([cxx + w * s * 0.16, cyy - h * s * 0.16, cxx + w * s * 0.5, cyy + h * s * 0.22], fill=(255, 255, 255, 230))
            d.ellipse([cxx + w * s * 0.45, cyy - h * s * 0.08, cxx + w * s * 0.86, cyy + h * s * 0.26], fill=(255, 255, 255, 230))

    disc_x, disc_y = x0 + w * 0.78, y0 + h * 0.2
    if night:
        mr = w * 0.062
        d.ellipse([disc_x - mr * 2.3, disc_y - mr * 2.3, disc_x + mr * 2.3, disc_y + mr * 2.3], fill=(247, 233, 168, 30))
        d.ellipse([disc_x - mr * 3.3, disc_y - mr * 3.3, disc_x + mr * 3.3, disc_y + mr * 3.3], fill=(247, 233, 168, 20))
        d.ellipse([disc_x - mr, disc_y - mr, disc_x + mr, disc_y + mr], fill=(247, 233, 168))
        d.ellipse([disc_x - mr * 0.54, disc_y - mr * 0.14, disc_x - mr * 0.14, disc_y + mr * 0.26], fill=(233, 222, 168, 150))
        d.ellipse([disc_x + mr * 0.16, disc_y - mr * 0.48, disc_x + mr * 0.44, disc_y - mr * 0.2], fill=(233, 222, 168, 150))
    else:
        sr = w * 0.05
        d.ellipse([disc_x - sr * 2.2, disc_y - sr * 2.2, disc_x + sr * 2.2, disc_y + sr * 2.2], fill=(247, 206, 91, 46))
        d.ellipse([disc_x - sr * 3.1, disc_y - sr * 3.1, disc_x + sr * 3.1, disc_y + sr * 3.1], fill=(247, 206, 91, 30))
        d.ellipse([disc_x - sr, disc_y - sr, disc_x + sr, disc_y + sr], fill=(247, 206, 91))
        d.ellipse([disc_x - sr * 0.7, disc_y - sr * 0.7, disc_x + sr * 0.7, disc_y + sr * 0.7], fill=(252, 239, 192))

    ground_y = y1 - h * 0.24
    if habitat in ("coast", "water"):
        d.rectangle([x0, ground_y, x1, y1], fill=(0x6F, 0xB7, 0xD2))
        for i in range(4):
            wy = ground_y + h * (0.05 + i * 0.05)
            d.rectangle([x0 + w * (0.08 + i * 0.07), wy, x0 + w * (0.34 + i * 0.07), wy + dp(1.4)], fill=(255, 255, 255, 120))
        d.rectangle([x0, y1 - h * 0.09, x1, y1], fill=(0xE4, 0xD8, 0xB4))
    else:
        far = {"forest": (0x4F, 0x7C, 0x50), "mountain": (0x5E, 0x7A, 0x63), "wetland": (0x6A, 0x9A, 0x6A),
               "city": (0x9A, 0x9E, 0x98)}.get(habitat, (0x7F, 0xAE, 0x70))
        d.rectangle([x0, ground_y - h * 0.045, x1, ground_y + h * 0.03], fill=far + (95,))
        for (fx, fr, fy) in ((0.20, 0.21, 0.055), (0.70, 0.24, 0.085), (0.98, 0.19, 0.065)):
            cxx, cyy = x0 + w * fx, ground_y + h * fy
            d.ellipse([cxx - w * fr, cyy - w * fr, cxx + w * fr, cyy + w * fr], fill=far)
        near = {"forest": (0x6B, 0xA0, 0x5F), "mountain": (0x7C, 0x9A, 0x76), "wetland": (0x87, 0xB5, 0x7F),
                "city": (0xB9, 0xBC, 0xB4)}.get(habitat, (0xA6, 0xCC, 0x7C))
        d.rectangle([x0, ground_y, x1, y1], fill=near)
        ground_h = y1 - ground_y
        for i in range(11):
            gx = x0 + w * ((i * 17 % 100) / 100)
            gh = ground_h * (0.34 + (i % 3) * 0.22)
            d.rectangle([gx, ground_y, gx + dp(2), ground_y + gh], fill=(56, 86, 48, 105))

    # 새
    sc = min(w, h) / 150.0
    bx, by = x0 + w * 0.5, ground_y + h * 0.035
    d.ellipse([bx - 20 * sc, by + 6 * sc, bx + 20 * sc, by + 12 * sc], fill=(34, 40, 32, 60))
    body, wing, head, belly = (0xF2, 0xC1, 0x3A), (0xD9, 0xA8, 0x28), (0xF7, 0xD0, 0x4A), (0xFA, 0xE2, 0x8A)
    d.ellipse([bx - 16 * sc, by - 14 * sc, bx + 10 * sc, by + 8 * sc], fill=body)
    d.ellipse([bx - 6 * sc, by - 4 * sc, bx + 10 * sc, by + 8 * sc], fill=belly)
    d.ellipse([bx - 22 * sc, by - 12 * sc, bx - 6 * sc, by + 2 * sc], fill=wing)
    d.ellipse([bx + 2 * sc, by - 24 * sc, bx + 20 * sc, by - 6 * sc], fill=head)
    d.polygon([(bx + 19 * sc, by - 18 * sc), (bx + 30 * sc, by - 15 * sc), (bx + 19 * sc, by - 12 * sc)], fill=(0xE2, 0x6A, 0x2C))
    d.rectangle([bx + 10 * sc, by - 20 * sc, bx + 13 * sc, by - 17 * sc], fill=(20, 20, 24))
    d.rectangle([bx - 6 * sc, by + 8 * sc, bx - 3 * sc, by + 17 * sc], fill=(0x8A, 0x6A, 0x3A))
    d.rectangle([bx + 3 * sc, by + 8 * sc, bx + 6 * sc, by + 17 * sc], fill=(0x8A, 0x6A, 0x3A))

    for i in range(4):                                            # 인화 비네트
        dd = w * 0.035 * (4 - i)
        t = w * 0.035
        d.rectangle([x0 + dd, y0, x0 + dd + t, y1], fill=(30, 24, 20, 16))
        d.rectangle([x1 - dd - t, y0, x1 - dd, y1], fill=(30, 24, 20, 16))
        d.rectangle([x0, y0 + dd, x1, y0 + dd + t], fill=(30, 24, 20, 16))
        d.rectangle([x0, y1 - dd - t, x1, y1 - dd], fill=(30, 24, 20, 16))
    rnd = random.Random(99)                                       # 그레인
    for _ in range(1400):
        gx, gy = x0 + rnd.random() * w, y0 + rnd.random() * h
        d.point((gx, gy), fill=(255, 250, 236, 34) if rnd.random() < 0.5 else (26, 20, 32, 38))


def draw_result(base, W, H, density, def_name="꾀꼬리", english="Black-naped Oriole",
                tier="보통", active="낮새", stars=3, is_new=True, count=3, dist=3.4,
                time_txt="07:42", camera_txt="미러리스", night=False, habitat="forest",
                quest="의뢰 완료! +₩6,500"):
    dp = lambda v: v * density
    d = ImageDraw.Draw(base, "RGBA")
    d.rectangle([0, 0, W, H], fill=(20, 16, 28, 172))

    inset = dp(11)
    card_w = min(min(W * 0.62, dp(340)), H * 0.68 / 1.26)
    card_h = card_w * 1.26
    cx, cy = W / 2, H / 2 - dp(6)
    pad = dp(34)

    px0, py0 = cx - card_w / 2 - pad, cy - card_h / 2 - pad
    ix0, iy0 = int(px0), int(py0)
    pw, ph = int(round(card_w + pad * 2)), int(round(card_h + pad * 2))

    patch = base.crop((ix0, iy0, ix0 + pw, iy0 + ph))
    mask = Image.new("L", (pw, ph), 0)
    md = ImageDraw.Draw(mask)
    pd = ImageDraw.Draw(patch, "RGBA")

    ox, oy = pad, pad
    card = [ox, oy, ox + card_w, oy + card_h]

    # 그림자 (마스크도 함께)
    md.rounded_rectangle([card[0] + dp(5), card[1] + dp(8), card[2] + dp(7), card[3] + dp(10)], radius=dp(6), fill=110)
    md.rounded_rectangle([card[0] + dp(9), card[1] + dp(14), card[2] + dp(12), card[3] + dp(17)], radius=dp(8), fill=170)
    md.rounded_rectangle(card, radius=dp(3), fill=255)
    pd.rounded_rectangle([card[0] + dp(5), card[1] + dp(8), card[2] + dp(7), card[3] + dp(10)], radius=dp(5), fill=(8, 6, 14, 60))
    pd.rounded_rectangle([card[0] + dp(9), card[1] + dp(14), card[2] + dp(12), card[3] + dp(17)], radius=dp(7), fill=(8, 6, 14, 42))
    pd.rounded_rectangle(card, radius=dp(3), fill=(253, 251, 243))

    photo_w = card_w - inset * 2
    photo_h = min(photo_w * 0.74, card_h - inset - dp(96))
    photo = [card[0] + inset, card[1] + inset, card[0] + inset + photo_w, card[1] + inset + photo_h]
    draw_photo(pd, photo, habitat, night, dp)
    pd.rectangle(photo, outline=(40, 32, 24, 60), width=1)

    cap_top = photo[3] + dp(12)
    cxm = card[0] + card_w / 2
    f = font(dp(17))
    tw = measure(def_name, f, pd)
    pd.text((cxm - tw / 2, cap_top), def_name, font=f, fill=(59, 47, 36))
    f = font(dp(11))
    sub = "%s · %s · %s" % (tier, active, english)
    tw = measure(sub, f, pd)
    pd.text((cxm - tw / 2 + dp(7), cap_top + dp(17)), sub, font=f, fill=(138, 115, 96))
    pd.ellipse([cxm - tw / 2 - dp(9) - dp(2.6), cap_top + dp(25) - dp(2.6),
                cxm - tw / 2 - dp(9) + dp(2.6), cap_top + dp(25) + dp(2.6)], fill=(0x5E, 0x93, 0x4F))

    star_r, gap = dp(13), dp(34)
    star_y = cap_top + dp(50)
    for i in range(3):
        sxx = cxm + (i - 1) * gap
        if i < stars:
            pd.polygon(star_pts(sxx, star_y, star_r), fill=(242, 190, 66))
            pd.polygon(star_pts(sxx, star_y, star_r * 0.55), fill=(255, 232, 158, 220))
        else:
            pts = star_pts(sxx, star_y, star_r * 0.86)
            pd.line(pts + [pts[0]], fill=(190, 178, 158, 170), width=max(1, int(dp(1.6))))

    f = font(dp(11))
    info = "%s · %.1f칸 · %s · 촬영 %d회" % (camera_txt, dist, time_txt, count)
    tw = measure(info, f, pd)
    pd.text((cxm - tw / 2, card[3] - dp(19)), info, font=f, fill=(154, 133, 112))

    # 첫 발견 스티커 (따로 회전해서 붙인다)
    if is_new:
        sw, sh = dp(80), dp(24)
        pad2 = dp(12)
        st_img = Image.new("RGB", (int(sw + pad2 * 2), int(sh + pad2 * 2)), (253, 251, 243))
        st_mask = Image.new("L", st_img.size, 0)
        sd = ImageDraw.Draw(st_img, "RGBA")
        sm = ImageDraw.Draw(st_mask)
        sx0, sy0 = pad2, pad2
        sm.rounded_rectangle([sx0, sy0, sx0 + sw, sy0 + sh], radius=dp(4), fill=255)
        sd.rounded_rectangle([sx0, sy0, sx0 + sw, sy0 + sh], radius=dp(4), fill=(226, 87, 76))
        sd.rounded_rectangle([sx0 + dp(2.5), sy0 + dp(2.5), sx0 + sw - dp(2.5), sy0 + sh - dp(2.5)],
                             radius=dp(3), outline=(255, 243, 226), width=max(1, int(dp(1.4))))
        f = font(dp(11))
        st = "NEW 첫 발견"
        tw = measure(st, f, sd)
        sd.text((sx0 + sw / 2 - tw / 2, sy0 + sh / 2 - dp(8.5)), st, font=f, fill=(255, 243, 226))
        st_r = st_img.rotate(-9, resample=Image.BICUBIC, expand=True, fillcolor=(253, 251, 243))
        mk_r = st_mask.rotate(-9, resample=Image.BICUBIC, expand=True)
        # 스티커 중심이 카드 오른쪽 위 모서리에 오도록
        tcx, tcy = card[2] - dp(60), card[1] + dp(22)
        patch.paste(st_r, (int(tcx - st_r.size[0] / 2), int(tcy - st_r.size[1] / 2)), mk_r)

    patch = patch.rotate(-2.1, resample=Image.BICUBIC, expand=False, fillcolor=(0, 0, 0))
    mask_r = mask.rotate(-2.1, resample=Image.BICUBIC, expand=False, fillcolor=0)
    base.paste(patch, (ix0, iy0), mask_r)

    d = ImageDraw.Draw(base, "RGBA")
    if quest:
        f = font(dp(12.5))
        qw = measure(quest, f, d) + dp(26)
        qy = cy + card_h / 2 + dp(30)
        qr = [cx - qw / 2, qy - dp(15), cx + qw / 2, qy + dp(15)]
        d.rounded_rectangle(qr, radius=dp(15), fill=(43, 38, 58, 225), outline=(242, 208, 107), width=max(1, int(dp(1.6))))
        d.text((qr[0] + dp(13), qy - dp(9)), quest, font=f, fill=(247, 233, 168))

    f = font(dp(11.5))
    hint = "화면을 탭해 탐조를 계속해요"
    tw = measure(hint, f, d)
    d.text((cx - tw / 2, H - dp(26)), hint, font=f, fill=(240, 236, 226, 220))


# ---------------------------------------------------------------------------

CAPTION_H = 46


def caption(im, text, sub=""):
    """패널 아래에 캡션 띠를 덧붙인다 (게임 화면을 가리지 않도록)"""
    w, h = im.size
    out = Image.new("RGB", (w, h + CAPTION_H), (18, 16, 24))
    out.paste(im, (0, 0))
    d = ImageDraw.Draw(out, "RGBA")
    d.rectangle([0, h, w, h + CAPTION_H], fill=(24, 20, 32, 255))
    f = font(21)
    d.text((18, h + 10), text, font=f, fill=(255, 246, 226))
    if sub:
        f2 = font(15)
        d.text((30 + measure(text, f, d), h + 15), sub, font=f2, fill=(196, 188, 208))
    return out


def main():
    cam = (560.0, 250.0)
    player = (cam[0] + VW / (2 * WORLD_SCALE) - 60, cam[1] + VH / (2 * WORLD_SCALE) - 20)
    birds = [
        {"x": cam[0] + 300 / WORLD_SCALE, "y": cam[1] + 250 / WORLD_SCALE, "name": "꾀꼬리", "seen": True},
        {"x": cam[0] + 470 / WORLD_SCALE, "y": cam[1] + 300 / WORLD_SCALE, "name": "직박구리", "seen": True},
        {"x": cam[0] + 250 / WORLD_SCALE, "y": cam[1] + 356 / WORLD_SCALE, "name": "참새", "seen": False},
    ]
    range_tiles = 7.5
    focus_dist = math.hypot(birds[0]["x"] - player[0], birds[0]["y"] - player[1]) / 16

    # ---- 1) 뷰파인더 ----
    vf, d = new_img()
    draw_world(d)
    for b, kind in zip(birds, ("kingfisher", "magpie", "sparrow")):
        bx, by = (b["x"] - cam[0]) * WORLD_SCALE, (b["y"] - cam[1]) * WORLD_SCALE
        d.ellipse([bx - 14, by + 6, bx + 14, by + 11], fill=(30, 40, 30, 60))
        pixel_bird(d, bx, by - 11, kind)
    draw_player(d, (player[0] - cam[0]) * WORLD_SCALE, (player[1] - cam[1]) * WORLD_SCALE - 6)
    draw_viewfinder(d, birds, player, cam, range_tiles, 3, "07:42", 34, False, 2.6,
                    "미러리스", 0, focus_dist)
    p1 = vf.resize((int(VW * SCALE), int(VH * SCALE)), Image.LANCZOS)
    # HUD(실제 화면 좌표) 는 가상 캔버스 위에 그려지므로 확대 후 같은 비율로 얹는다
    d1 = ImageDraw.Draw(p1, "RGBA")
    draw_hud_controls(d1, p1.size[0], p1.size[1], density=2.25, photo_mode=True, clock=2.6,
                      toast="카메라 모드 — 새를 탭해 촬영하세요")
    caption(p1, "① 카메라 모드 — 뷰파인더", "AF 박스 · 사거리 링 · 별점 예상 · 촬영 정보 (스탯/미니맵은 숨김)")

    # ---- 2) 셔터 순간 ----
    p2 = vf.copy()
    d2 = ImageDraw.Draw(p2, "RGBA")
    draw_shutter(d2, 0.66, 0.95)
    p2 = p2.resize((int(VW * SCALE), int(VH * SCALE)), Image.LANCZOS)
    d2 = ImageDraw.Draw(p2, "RGBA")
    draw_hud_controls(d2, p2.size[0], p2.size[1], density=2.25, photo_mode=True, clock=2.6)
    caption(p2, "② 촬영 순간", "셔터 블레이드가 닫히며 '찰칵!'")

    # ---- 3) 촬영 결과 ----
    W, H = int(VW * SCALE), int(VH * SCALE)
    p3 = vf.resize((W, H), Image.LANCZOS)
    d3 = ImageDraw.Draw(p3, "RGBA")
    draw_hud_controls(d3, W, H, density=2.25, photo_mode=True, clock=6.0)
    draw_result(p3, W, H, density=2.25, quest=None)
    caption(p3, "③ 촬영 결과", "폴라로이드 인화 · 별점 · 첫 발견 스티커")

    # ---- 4) 밤 + 의뢰 보수 ----
    vfn, dn = new_img()
    draw_world(dn)
    dn.rectangle([0, 0, VW, VH], fill=(24, 28, 66, 96))   # 실제 게임 밤 틴트와 동일
    p4 = vfn.resize((W, H), Image.LANCZOS)
    d4 = ImageDraw.Draw(p4, "RGBA")
    draw_hud_controls(d4, W, H, density=2.25, photo_mode=True, clock=6.0)
    draw_result(p4, W, H, density=2.25, def_name="수리부엉이", english="Eurasian Eagle-Owl",
                tier="희귀", active="밤새", stars=2, is_new=False, count=5, dist=6.2,
                time_txt="21:18", camera_txt="DSLR", night=True, habitat="mountain",
                quest="의뢰 완료! +₩22,000")
    caption(p4, "④ 밤 촬영 결과", "밤 풍경 · 의뢰 보수 · 별점 2개")

    panels = [p1, p2, p3, p4]
    gap = 18
    total = sum(p.size[1] for p in panels) + gap * (len(panels) - 1)
    out = Image.new("RGB", (panels[0].size[0], total), (18, 16, 24))
    y = 0
    for p in panels:
        out.paste(p, (0, y))
        y += p.size[1] + gap
    out.save(OUT)
    print("saved", os.path.abspath(OUT), out.size)


if __name__ == "__main__":
    main()
