"""
살아있는 풀(Living Grass) 프리뷰 생성기.

Assets.kt 의 grassKinds()/grassPose() 와 Grass.kt 의 바람 필드·상태 머신을
Python(PIL)로 1:1 재현한다. (게임 코드에는 영향 없음, 시각 확인용)

출력:
  tools/grass_rig_sheet.png   풀잎 5종 x lean 11 x curl 7 포즈 컨택트시트
  tools/grass_live_still.png  바람이 부는 중 초원 한 장면
  tools/grass_live_anim.gif  캐릭터가 밭을 헤치며 걷는 애니메이션

사용: python3 tools/preview_grass_live.py
"""
import math
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TS = 32          # 타일 크기 (Assets.kt / Maps.kt 와 동일)

# ---------------------------------------------------------------------------
# Assets.kt 상수 미러
# ---------------------------------------------------------------------------
GRASS_W = 18
GRASS_CX = 9
LEAN_MAX = 5
CURL_MAX = 3
LEAN_UNIT = 1.5
CURL_UNIT = 1.25

#  (h, bh, oy, wBase, wMid, deep, body, lit, hi, vein, restLean, restCurl, seed, fold)
KINDS = [
    (8,  12, 10, 2, 2, 0x2E5A2A, 0x4E8A3C, 0x7FBE5E, 0xA8DA7E, 0x3D7033,  0, 0, 0,        False),
    (12, 16, 14, 2, 2, 0x35632F, 0x4E8A3C, 0x6FAE57, 0x8CC46C, 0x447C34,  0, 0, 0,        True),
    (17, 21, 19, 3, 2, 0x3A6B33, 0x5B9A45, 0x8CC46C, 0xB7E08C, 0x4C8539,  0, 0, 0,        False),
    (14, 18, 16, 3, 2, 0x4A5A2A, 0x7E9440, 0xA8BC5E, 0xC9D88A, 0x6A7F34,  2, 1, 0,        True),
    (16, 24, 22, 2, 2, 0x3E6B4A, 0x5F9A5E, 0x8FCB8C, 0xC3E6B0, 0x4C7F4C, -1, 0, 0xD9C58A, False),
]
KIND_NAMES = ["새순", "기본 풀잎", "긴 풀", "마른 풀", "이삭/갈대"]


def shade(color, f):
    return (
        min(255, max(0, int(((color >> 16) & 0xFF) * f))),
        min(255, max(0, int(((color >> 8) & 0xFF) * f))),
        min(255, max(0, int((color & 0xFF) * f))),
    )


def rgb(color):
    return ((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF)


def pack(color):
    """shade() 가 돌려준 튜플도 rgb() 를 통과할 수 있게 정규화"""
    if isinstance(color, tuple):
        return (color[0] << 16) | (color[1] << 8) | color[2]
    return color


# ---------------------------------------------------------------------------
# Assets.kt grassPose() 미러
# ---------------------------------------------------------------------------

def grass_pose(kind, lean, curl):
    h, bh, oy, w_base, w_mid, deep, body, lit, hi, vein, rest_lean, rest_curl, seed, fold = KINDS[kind]
    img = Image.new("RGBA", (GRASS_W, bh), (0, 0, 0, 0))
    px = img.load()

    tip_x = (lean + rest_lean) * LEAN_UNIT
    arc = (curl + rest_curl) * CURL_UNIT
    c1 = tip_x * 0.12
    c2 = tip_x * 0.60 + arc
    tip_px = GRASS_CX

    for i in range(h + 1):
        t = i / h
        u = 1.0 - t
        x = 3 * u * u * t * c1 + 3 * u * t * t * c2 + t * t * t * tip_x
        cx = GRASS_CX + int(math.floor(x + 0.5))
        if i == h:
            tip_px = cx
        y = oy - i
        if y < 0:
            break

        # 잎폭 테이퍼 (Assets.kt grassPose 와 동일)
        if t < 0.14:
            w = w_base
        elif t < 0.58:
            w = w_mid
        else:
            w = 1
        tip_light = t > 0.84
        root_dark = t < 0.11
        vein_on = (w >= 3) and 0.14 < t < 0.55 and (i % 3 == 0)
        fold_on = fold and int(h * 0.38) <= i <= int(h * 0.38) + 1

        for j in range(w):
            col = cx - ((w - 1) // 2) + j
            is_left = j == 0
            is_right = j == w - 1
            if tip_light:
                color = hi
            elif w == 1:
                color = body
            elif is_right:
                color = deep
            elif is_left:
                color = hi if fold_on else lit
            else:
                color = vein if vein_on else body
            if root_dark:
                color = shade(color, 0.80)
            if 0 <= col < GRASS_W:
                px[col, y] = rgb(pack(color)) + (255,)

    if seed != 0:
        for j in range(1, 5):
            y2 = oy - h - j
            if y2 < 0:
                break
            drift = int(math.floor(tip_x * 0.07 * j + 0.5))
            half_w = 1 if j <= 2 else 0
            for dx in range(-half_w, half_w + 1):
                x2 = tip_px + drift + dx
                if 0 <= x2 < GRASS_W:
                    color = seed if dx == 0 else pack(shade(seed, 0.76))
                    px[x2, y2] = rgb(color) + (255,)
    return img


# 5 x 11 x 7 = 385장 사전 (Assets.kt buildGrassRig 와 동일)
POSES = [[[grass_pose(k, l, c) for c in range(-CURL_MAX, CURL_MAX + 1)]
          for l in range(-LEAN_MAX, LEAN_MAX + 1)] for k in range(len(KINDS))]


def pose(kind, lean, curl):
    li = min(LEAN_MAX, max(-LEAN_MAX, lean)) + LEAN_MAX
    ci = min(CURL_MAX, max(-CURL_MAX, curl)) + CURL_MAX
    return POSES[kind][li][ci]


# ---------------------------------------------------------------------------
# Grass.kt 상태 머신 미러
# ---------------------------------------------------------------------------
S_IDLE, S_SWAY, S_GUST, S_BEND, S_RECOVER = 0, 1, 2, 3, 4
K = [15.0, 27.0, 42.0, 150.0, 28.0]
D = [6.4, 5.6, 4.1, 9.0, 3.2]
SWAY_TH, GUST_TH = 0.22, 0.56
LEAN_AMP, CURL_AMP = 5.2, 1.6
RECOVER_T = 0.6
SQUALL_PERIOD, SQUALL_R, SQUALL_AMP = 7.2, 210.0, 0.92
COL_W = 8.0
PHASE_LAG = 0.55


def osc_at(x, t):
    """진동 성분 — 풀잎마다 다른 시점을 보간한다"""
    breathe = math.sin(t * 0.58) * 0.15
    ripple = math.sin(t * 1.28 - x * 0.021) * 0.33
    ripple2 = math.sin(t * 2.85 - x * 0.048 + 1.3) * 0.12
    w = 0.10 + breathe + ripple + ripple2
    return max(-1.0, min(1.2, w))


def squall_at(x, t, span):
    """돌풍 — 하나의 전선으로 화면을 가로지른다 (위상 분리를 하지 않음)"""
    p = t % SQUALL_PERIOD
    head = (p / SQUALL_PERIOD) * (span + SQUALL_R * 2.2) - SQUALL_R * 1.1
    d = abs(x - head)
    if d >= SQUALL_R:
        return 0.0
    k = 1.0 - d / SQUALL_R
    return k * k * SQUALL_AMP


class Blade:
    __slots__ = ("x", "y", "kind", "phase01", "stiff", "lean", "leanV",
                 "curl", "curlV", "state", "rec")

    def __init__(self, x, y, kind, phase01, stiff):
        self.x, self.y, self.kind = x, y, kind
        self.phase01, self.stiff = phase01, stiff
        self.lean = self.leanV = self.curl = self.curlV = 0.0
        self.state, self.rec = S_IDLE, 0.0


def pick_kind(r):
    v = r.random()
    if v < 0.42:
        return 0
    if v < 0.78:
        return 1
    return 2


class Rng:
    """위치/밀도 검증용 난수 — 파이썬 random 과 시드 의존성을 끊기 위한 자체 LCG"""
    def __init__(self, seed):
        self.s = seed & 0xFFFFFFFFFFFF

    def next(self):
        self.s = (self.s * 6364136223846793005 + 1442695040888963407) & 0xFFFFFFFFFFFF
        return (self.s >> 16) & 0xFFFFFFFF

    def randint(self, a, b):
        return a + self.next() % (b - a + 1)

    def random(self):
        return self.next() / 0xFFFFFFFF


def build_meadow(tw, th, seed=0x9E3779B9):
    r = Rng(seed)
    blades = []
    for ty in range(th):
        for tx in range(tw):
            if r.random() < 0.10:          # 잡초/꽃/잔디밭 섞인 지형 비율
                continue
            forced = -1
            if r.random() < 0.12:
                forced = 2                  # 풀숲
            clumps = 2 + r.randint(0, 1) if forced == 2 else 1 + (1 if r.random() < 0.35 else 0)
            for _ in range(clumps):
                if r.random() < 0.55:
                    cx = tx * 32 + (r.randint(0, 3) if r.random() < 0.5
                                    else 31 - r.randint(0, 3))
                else:
                    cx = tx * 32 + 1 + r.randint(0, 29)
                cy = ty * 32 + 20 + r.randint(0, 10)
                for _ in range(1 + r.randint(0, 1)):
                    kind = forced if (forced >= 0 and r.random() < 0.74) else pick_kind(r)
                    blades.append(Blade(
                        cx + r.randint(-2, 2), cy + r.randint(-1, 2),
                        kind, r.random(), 0.78 + r.random() * 0.50))
    blades.sort(key=lambda b: b.y)
    return blades


class Wind:
    def __init__(self, span):
        self.cols = int(span / COL_W) + 6
        self.osc_a = [0.0] * self.cols
        self.osc_b = [0.0] * self.cols
        self.squall = [0.0] * self.cols

    def refresh(self, t, span):
        for i in range(self.cols):
            wx = i * COL_W
            self.squall[i] = squall_at(wx, t, span)
            self.osc_a[i] = osc_at(wx, t)
            self.osc_b[i] = osc_at(wx, t + PHASE_LAG)


def step(blades, wind, dt, t, span, fx, fy, bike=False):
    wind.refresh(t, span)
    last = wind.cols - 1
    rad = 15.5 if bike else 11.0
    rad2 = rad * rad
    half = min(dt * 0.5, 1.0 / 60.0)

    for b in blades:
        ci = min(last, max(0, int(b.x / COL_W)))
        oa = wind.osc_a[ci]
        w = wind.squall[ci] + oa + (wind.osc_b[ci] - oa) * b.phase01
        m = abs(w)
        st = S_GUST if m > GUST_TH else (S_SWAY if m > SWAY_TH else S_IDLE)
        t_lean = w * LEAN_AMP
        t_curl = w * CURL_AMP

        dx, dy = b.x - fx, b.y - fy
        d2 = dx * dx + dy * dy
        if d2 < rad2:
            push = 1.0 - math.sqrt(d2) / rad
            s = -1.0 if dx < 0 else 1.0
            t_lean = w * LEAN_AMP * 0.25 + s * push * 5.5
            t_curl = w * CURL_AMP * 0.25 + s * push * 3.0
            st = S_BEND
            b.rec = RECOVER_T
        elif b.rec > 0:
            st = S_RECOVER
        if st == S_RECOVER:
            b.rec -= dt
            if b.rec <= 0:
                b.rec = 0.0
                st = S_GUST if m > GUST_TH else (S_SWAY if m > SWAY_TH else S_IDLE)
        b.state = st

        kk = K[st] * b.stiff
        dd = D[st] * b.stiff
        for _ in range(2):
            b.leanV += ((t_lean - b.lean) * kk - b.leanV * dd) * half
            b.lean += b.leanV * half
            b.curlV += ((t_curl - b.curl) * kk * 0.8 - b.curlV * dd * 0.8) * half
            b.curl += b.curlV * half


# ---------------------------------------------------------------------------
# 1) 포즈 컨택트시트
# ---------------------------------------------------------------------------

def rig_sheet():
    cols = LEAN_MAX * 2 + 1
    rows = len(KINDS)
    cw = max(p.width for k in KINDS for p in [POSES[0][0][0]]) + 2
    cell_w = GRASS_W * 2 + 2
    cell_h = max(k[1] for k in KINDS) * 2 + 14
    pad_l, pad_t, label = 76, 34, 12
    W = pad_l + cols * cell_w + 20
    H = pad_t + rows * cell_h + 40
    img = Image.new("RGB", (W, H), (26, 28, 24))
    d = ImageDraw = __import__("PIL.ImageDraw", fromlist=["ImageDraw"]).ImageDraw(img)

    d.text((12, 10), "Living Grass RIG - 5 kinds x lean 11 x curl 7  (x4 nearest)",
           fill=(236, 236, 226))
    for ci, curl in enumerate(range(-CURL_MAX, CURL_MAX + 1)):
        x = pad_l + ci * cell_w + 2
        d.text((x, pad_t - 14), f"c{curl:+d}", fill=(150, 190, 150))
    for ki, k in enumerate(KINDS):
        y0 = pad_t + ki * cell_h
        d.text((10, y0 + 14), KIND_NAMES[ki], fill=(226, 200, 160))
        d.text((10, y0 + 28), f"h{k[0]}", fill=(140, 150, 140))
        for li, lean in enumerate(range(-LEAN_MAX, LEAN_MAX + 1)):
            x0 = pad_l + li * cell_w
            if lean == 0:
                d.rectangle([x0, y0, x0 + cell_w, y0 + cell_h], outline=(70, 80, 70))
            for ci, curl in enumerate(range(-CURL_MAX, CURL_MAX + 1)):
                p = POSES[ki][li][ci]
                big = p.resize((p.width * 2, p.height * 2), Image.NEAREST)
                img.paste(big, (x0 + 1, y0 + 1), big)
    d.text((10, H - 24), "lean = tip offset (wind)   curl = arc/bend (trample)   vein + fold + tip light = internal detail",
           fill=(150, 160, 150))
    img.save(os.path.join(HERE, "grass_rig_sheet.png"))
    print("  grass_rig_sheet.png")


# ---------------------------------------------------------------------------
# 2) 장면 렌더 (밭 + 캐릭터)
# ---------------------------------------------------------------------------

GROUND = 0x96D07A


def ground_tile(seed):
    img = Image.new("RGB", (TS, TS), rgb(GROUND))
    px = img.load()
    r = Rng(seed)
    for _ in range(20):
        x, y = r.randint(0, 31), r.randint(0, 31)
        if (x + y) % 2 == 0:
            px[x, y] = shade(GROUND, 0.88)
    for _ in range(14):
        x, y = r.randint(0, 31), r.randint(0, 31)
        if (x + y) % 2 == 1:
            px[x, y] = shade(GROUND, 1.16)
    return img


def player_sprite(frame):
    """겉옷/모자/다리/가방 정도만 — 깊이 확인용 placeholder"""
    img = Image.new("RGBA", (16, 20), (0, 0, 0, 0))
    p = img.load()
    def rect(x0, y0, x1, y1, col):
        for y in range(y0, y1):
            for x in range(x0, x1):
                p[x, y] = col + (255,)
    rect(5, 1, 11, 4, (0x3A, 0x2E, 0x26))      # 머리/모자
    rect(6, 2, 10, 3, (0xF0, 0xC8, 0xA0))      # 얼굴
    rect(4, 5, 12, 12, (0xE2, 0x57, 0x4C))     # 상의
    rect(4, 5, 12, 6, (0xF2, 0x8A, 0x7C))      # 상의 하이라이트
    off = (0, 1, 0)[frame % 3]
    rect(5, 13 + off, 7, 18, (0x35, 0x46, 0x6B))   # 다리
    rect(9, 13 - off, 11, 18, (0x35, 0x46, 0x6B))
    return img


def render(blades, w, h, t, fx, fy, feet_y, wind, span):
    img = Image.new("RGB", (w, h), rgb(GROUND))
    d = __import__("PIL.ImageDraw", fromlist=["ImageDraw"]).ImageDraw(img)
    tw, th = w // TS, h // TS
    for ty in range(th):
        for tx in range(tw):
            img.paste(ground_tile(1000 + tx * 101 + ty * 977), (tx * TS, ty * TS))

    back = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    front = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for b in blades:
        if b.x < -20 or b.x > w + 20 or b.y < -20 or b.y > h + 20:
            continue
        p = pose(b.kind, int(b.lean + 0.5), int(b.curl + 0.5))
        layer = front if b.y > feet_y else back
        layer.alpha_composite(p, (int(b.x - GRASS_CX), int(b.y - KINDS[b.kind][2])))
    img = Image.alpha_composite(img.convert("RGBA"), back)

    # 캐릭터 (그림자 + 스프라이트)
    d = __import__("PIL.ImageDraw", fromlist=["ImageDraw"]).ImageDraw(img)
    d.ellipse([fx - 5, fy + 4, fx + 5, fy + 8], fill=(46, 56, 46))
    sp = player_sprite(int(t * 7))
    img.alpha_composite(sp, (int(fx - 8), int(fy - 14)))

    img = Image.alpha_composite(img, front)
    return img.convert("RGB")


def scenes():
    tw, th = 30, 17
    w, h = tw * TS, th * TS
    span = tw * TS
    blades = build_meadow(tw, th)
    print(f"  풀잎 {len(blades)}개 / {tw}x{th} 타일  (≈{len(blades)/(tw*th):.2f}개 per tile)")

    wind = Wind(span)
    dt = 1.0 / 60.0

    # --- 정지 화면: 돌풍이 중앙을 지나는 순간 ---
    t = 3.55
    for _ in range(int(4.0 / dt)):
        step(blades, wind, dt, t, span, -999, -999)
        t += dt
    fx, fy = w * 0.30, h * 0.72
    for _ in range(40):
        step(blades, wind, dt, t, span, fx, fy)
        t += dt
    render(blades, w, h, t, fx, fy, fy, wind, span).save(
        os.path.join(HERE, "grass_live_still.png"))
    print("  grass_live_still.png")

    # --- 애니메이션: 캐릭터가 오른쪽으로 걸어가며 밟고 지나간다 ---
    frames = []
    t = 0.0
    x0, x1 = w * 0.10, w * 0.92
    fy = h * 0.62
    total = int(4.0 / dt)
    for i in range(total):
        u = i / total
        fx = x0 + (x1 - x0) * u
        fy = h * (0.58 + 0.10 * math.sin(u * 3.0))
        step(blades, wind, dt, t, span, fx, fy)
        t += dt
        if i % 2 == 0:      # 30fps 로 줄여 GIF 용량 절약
            frames.append(render(blades, w, h, t, fx, fy, fy, wind, span)
                           .resize((w // 2, h // 2), Image.NEAREST))
    frames[0].save(
        os.path.join(HERE, "grass_live_anim.gif"),
        save_all=True, append_images=frames[1:], duration=66, loop=0, optimize=True)
    print(f"  grass_live_anim.gif ({len(frames)} frames)")


def wave_strip():
    """같은 영역을 시간순으로 — 돌풍이 화면을 가로지르는 것을 확인"""
    tw, th = 30, 17
    w, h = tw * TS, th * TS
    span = tw * TS
    blades = build_meadow(tw, th, seed=0x51AB77)
    wind = Wind(span)
    dt = 1.0 / 60.0
    t = 0.0
    for _ in range(int(1.2 / dt)):
        step(blades, wind, dt, t, span, -999, -999)
        t += dt

    cw, ch = 480, 270
    cx0, cy0 = 40, 40
    shots, marks = [], []
    for i in range(6):
        for _ in range(int(0.6 / dt)):
            step(blades, wind, dt, t, span, -999, -999)
            t += dt
        f = render(blades, w, h, t, -999, -999, -9999, wind, span)
        shots.append(f.crop((cx0, cy0, cx0 + cw, cy0 + ch)).resize((cw, ch), Image.NEAREST))
        # 돌풍 전선 위치
        head = (t % SQUALL_PERIOD) / SQUALL_PERIOD * (span + SQUALL_R * 2.2) - SQUALL_R * 1.1
        marks.append(head - cx0)
    sheet = Image.new("RGB", (cw * 3, ch * 2 + 22), (24, 26, 22))
    d = __import__("PIL.ImageDraw", fromlist=["ImageDraw"]).ImageDraw(sheet)
    for i, s in enumerate(shots):
        x, y = (i % 3) * cw, (i // 3) * (ch + 11) + 18
        sheet.paste(s, (x, y))
        d.text((x + 4, y - 12), f"t={i*0.6+1.2:.1f}s   gust front x={marks[i]:.0f}", fill=(150, 200, 150))
        if 0 <= marks[i] <= cw:
            d.line([x + marks[i], y, x + marks[i], y + ch], fill=(255, 120, 90))
    sheet.save(os.path.join(HERE, "grass_wave_strip.png"))
    print("  grass_wave_strip.png")


def trample_strip():
    """캐릭터가 밟고 지나가는 순간 (4배 확대) — BEND -> RECOVER 오버슛 확인"""
    tw, th = 30, 17
    w, h = tw * TS, th * TS
    span = tw * TS
    blades = build_meadow(tw, th, seed=0x2BEEF1)
    wind = Wind(span)
    dt = 1.0 / 60.0
    t = 0.0
    for _ in range(int(1.2 / dt)):
        step(blades, wind, dt, t, span, -999, -999)
        t += dt

    fy = h * 0.5
    fx = w * 0.18
    cw, ch = 200, 120
    zoom = 4
    shots, labels = [], []
    state_hits = {}
    for i in range(8):
        for _ in range(int(0.13 / dt)):
            step(blades, wind, dt, t, span, fx, fy)
            t += dt
        for b in blades:
            if abs(b.x - fx) < 40 and abs(b.y - fy) < 30:
                state_hits[b.state] = state_hits.get(b.state, 0) + 1
        f = render(blades, w, h, t, fx, fy, fy, wind, span)
        crop = f.crop((int(fx - cw / 2), int(fy - ch + 18), int(fx - cw / 2) + cw, int(fy - ch + 18) + ch))
        shots.append(crop.resize((cw * zoom, ch * zoom), Image.NEAREST))
        labels.append(f"+{i*0.13:.2f}s")
        fx += 26
    sheet = Image.new("RGB", (cw * zoom * 4, ch * zoom * 2 + 26), (24, 26, 22))
    d = __import__("PIL.ImageDraw", fromlist=["ImageDraw"]).ImageDraw(sheet)
    for i, s in enumerate(shots):
        x, y = (i % 4) * (cw * zoom), (i // 4) * (ch * zoom + 13) + 20
        sheet.paste(s, (x, y))
        d.text((x + 4, y - 12), labels[i], fill=(150, 200, 150))
    sheet.save(os.path.join(HERE, "grass_trample_strip.png"))
    print("  grass_trample_strip.png")
    print("   관측 상태 분포:", {["IDLE", "SWAY", "GUST", "BEND", "RECOVER"][k]: v
                                for k, v in sorted(state_hits.items())})


def main():
    print("Living Grass preview ->", HERE)
    rig_sheet()
    scenes()
    wave_strip()
    trample_strip()
    print("done")


if __name__ == "__main__":
    main()
