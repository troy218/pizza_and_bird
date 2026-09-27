#!/usr/bin/env python3
"""CharacterArt.kt 프로토타입 — 골격(관절) 기반 캐릭터 애니메이션.

Kotlin `CharacterArt` 와 같은 구조/수식을 파이썬으로 옮겨 놓은 미리보기용 코드.
`pixelcanvas` 의 drawRect / drawCircle / drawOval / drawPath 네 가지만 사용하므로
두 구현의 결과가 픽셀 단위로 거의 같다.

    python3 tools/preview/render_people.py      # 시트 + GIF 출력
"""
from __future__ import annotations

import math

from pixelcanvas import Bitmap, Paint, Path

SIZE = 32
# 앉은 자세는 엉덩이를 좌석 높이로 올리므로 머리·모자가 프레임 위로 넘친다.
# render(..., top_pad=SIT_TOPPAD) 로 상단 여백을 두고, 그릴 때 y 에서 이만큼 뺀다.
SIT_TOPPAD = 9

# ---------------------------------------------------------------------------
# 색/배색
# ---------------------------------------------------------------------------


def shade(color: int, f: float) -> int:
    a = (color >> 24) & 0xFF
    r = min(255, int(((color >> 16) & 0xFF) * f))
    g = min(255, int(((color >> 8) & 0xFF) * f))
    b = min(255, int((color & 0xFF) * f))
    return (a << 24) | (r << 16) | (g << 8) | b


class Pal:
    def __init__(self, hair, hair2, skin, skin2, top, top2, pants, pants2,
                 shoe, line, pack, pack2, eye, blush):
        self.hair, self.hair2 = hair, hair2
        self.skin, self.skin2 = skin, skin2
        self.top, self.top2 = top, top2
        self.pants, self.pants2 = pants, pants2
        self.shoe, self.line = shoe, line
        self.pack, self.pack2 = pack, pack2
        self.eye, self.blush = eye, blush

    def copy(self, **kw):
        d = dict(self.__dict__)
        d.update(kw)
        return Pal(**d)


class Gear:
    def __init__(self, cap=None, capDark=0, vest=None, vestDark=0,
                 scarf=None, brim=False, feather=None):
        self.cap, self.capDark = cap, capDark
        self.vest, self.vestDark = vest, vestDark
        self.scarf, self.brim, self.feather = scarf, brim, feather


class Look:
    def __init__(self, pal, gear=None, glasses=False, apron=False, cane=False,
                 small=False, longHair=False, pack=True):
        self.pal, self.gear = pal, gear
        self.glasses, self.apron, self.cane = glasses, apron, cane
        self.small, self.longHair, self.pack = small, longHair, pack


HOLD_NONE = 0
HOLD_CAMERA = 1
HOLD_BARS = 2
HOLD_CANE = 3


class Pose:
    """관절 각도(도). 0도 = 아래를 향함, +각도 = 캐릭터가 바라보는 앞쪽."""

    def __init__(self, bodyY=0.0, bodyX=0.0, lean=0.0, crouch=0.0,
                 hipL=0.0, kneeL=0.0, footL=0.0,
                 hipR=0.0, kneeR=0.0, footR=0.0,
                 armL=0.0, elbowL=8.0, armR=0.0, elbowR=8.0,
                 shoulderL=0.0, shoulderR=0.0,
                 headY=0.0, headX=0.0, tilt=0.0, turn=0.0,
                 blink=0.0, mouth=0.0, brow=0.0, breath=0.0,
                 hairSway=0.0, clothSway=0.0, packBob=0.0,
                 hold=HOLD_NONE, holdT=0.0):
        self.bodyY, self.bodyX, self.lean, self.crouch = bodyY, bodyX, lean, crouch
        self.hipL, self.kneeL, self.footL = hipL, kneeL, footL
        self.hipR, self.kneeR, self.footR = hipR, kneeR, footR
        self.armL, self.elbowL, self.armR, self.elbowR = armL, elbowL, armR, elbowR
        self.shoulderL, self.shoulderR = shoulderL, shoulderR
        self.headY, self.headX, self.tilt, self.turn = headY, headX, tilt, turn
        self.blink, self.mouth, self.brow, self.breath = blink, mouth, brow, breath
        self.hairSway, self.clothSway, self.packBob = hairSway, clothSway, packBob
        self.hold, self.holdT = hold, holdT


# ---------------------------------------------------------------------------
# 그리기 헬퍼 (drawRect / drawCircle / drawOval / drawPath 만 사용)
# ---------------------------------------------------------------------------


class G:
    def __init__(self, cv, dy=0.0):
        self.cv = cv
        self.dy = dy              # 앉은 자세용 상단 여백(top_pad) 만큼 아래로 그리기
        self.p = Paint()

    def rect(self, l, t, r, b, col):
        self.p.color = col
        self.cv.drawRect(l, t + self.dy, r, b + self.dy, self.p)

    def circ(self, x, y, rad, col):
        self.p.color = col
        self.cv.drawCircle(x, y + self.dy, rad, self.p)

    def oval(self, l, t, r, b, col):
        self.p.color = col
        self.cv.drawOval((l, t + self.dy, r, b + self.dy), self.p)

    def poly(self, col, *pts):
        self.p.color = col
        path = Path()
        path.moveTo(pts[0], pts[1] + self.dy)
        i = 2
        while i < len(pts):
            path.lineTo(pts[i], pts[i + 1] + self.dy)
            i += 2
        path.close()
        self.cv.drawPath(path, self.p)

    def rrect(self, l, t, r, b, rad, col):
        rad = min(rad, (r - l) / 2, (b - t) / 2)
        self.rect(l + rad, t, r - rad, b, col)
        self.rect(l, t + rad, r, b - rad, col)
        self.circ(l + rad, t + rad, rad, col)
        self.circ(r - rad, t + rad, rad, col)
        self.circ(l + rad, b - rad, rad, col)
        self.circ(r - rad, b - rad, rad, col)

    def seg(self, x0, y0, x1, y1, w0, w1, col):
        """관절 마디 — 끝이 둥근 사다리꼴."""
        dx, dy = x1 - x0, y1 - y0
        ln = math.hypot(dx, dy)
        if ln < 0.001:
            self.circ(x0, y0, w0, col)
            return
        nx, ny = -dy / ln, dx / ln
        self.poly(col,
                  x0 + nx * w0, y0 + ny * w0,
                  x1 + nx * w1, y1 + ny * w1,
                  x1 - nx * w1, y1 - ny * w1,
                  x0 - nx * w0, y0 - ny * w0)
        self.circ(x0, y0, w0, col)
        self.circ(x1, y1, w1, col)


def fk(x, y, ang, length, depth=1.0):
    a = math.radians(ang)
    return x + math.sin(a) * length * depth, y + math.cos(a) * length


# ---------------------------------------------------------------------------
# 포즈 생성
# ---------------------------------------------------------------------------

WALK, RUN, SNEAK = 0, 1, 2


def _leg(p):
    """한쪽 다리의 위상 p(0=접지, 0.5=발끝 떼기) -> (엉덩이, 무릎, 발) 각도 계수."""
    tau = math.tau
    hip = math.cos(tau * p)
    swing = max(0.0, math.sin(tau * (p - 0.5)))
    knee = swing ** 1.15
    stance = max(0.0, math.sin(tau * p)) * 0.18
    foot = -math.cos(tau * p) + 1.15 * max(0.0, math.sin(tau * (p - 0.25)))
    return hip, knee + stance, foot


def walk_pose(phase, style=WALK):
    tau = math.tau
    p = phase % 1.0
    if style == RUN:
        amp, kneeAmp, armAmp, elbow, lean, bounce = 40.0, 76.0, 34.0, 62.0, 10.0, 1.15
        crouch, headAmp = 0.0, 0.5
    elif style == SNEAK:
        amp, kneeAmp, armAmp, elbow, lean, bounce = 18.0, 30.0, 10.0, 46.0, 15.0, 0.34
        crouch, headAmp = 1.0, 0.24
    else:
        amp, kneeAmp, armAmp, elbow, lean, bounce = 27.0, 44.0, 20.0, 16.0, 2.5, 0.72
        crouch, headAmp = 0.0, 0.34

    hR, kR, fR = _leg(p)
    hL, kL, fL = _leg((p + 0.5) % 1.0)
    swingSign = math.cos(tau * p)

    return Pose(
        bodyY=bounce * math.cos(2 * tau * p),
        bodyX=0.35 * math.sin(tau * p) * (0.4 if style == SNEAK else 1.0),
        lean=lean,
        crouch=crouch,
        hipR=amp * hR, kneeR=kneeAmp * kR, footR=14.0 * fR,
        hipL=amp * hL, kneeL=kneeAmp * kL, footL=14.0 * fL,
        armR=-armAmp * swingSign, elbowR=elbow + 10.0 * max(0.0, -swingSign),
        armL=armAmp * swingSign, elbowL=elbow + 10.0 * max(0.0, swingSign),
        shoulderR=-0.35 * swingSign, shoulderL=0.35 * swingSign,
        headY=headAmp * math.cos(2 * tau * (p - 0.07)),
        headX=0.3 * math.sin(tau * p),
        tilt=1.2 * math.sin(tau * p),
        breath=0.35 + 0.35 * math.sin(2 * tau * p),
        mouth=0.75 if style == RUN else 0.0,
        brow=0.6 if style == RUN else (0.4 if style == SNEAK else 0.0),
        blink=0.0,
        hairSway=-1.1 * math.sin(tau * (p - 0.12)) - (0.55 if style == RUN else 0.0),
        clothSway=-0.9 * math.sin(tau * (p - 0.18)),
        packBob=0.5 * math.cos(2 * tau * (p - 0.12)),
    )


def _bump(x, a, b):
    """a~b 구간에서 0 -> 1 -> 0 으로 부드럽게 솟는 함수."""
    if x < a or x > b:
        return 0.0
    t = (x - a) / (b - a)
    return math.sin(math.pi * t) ** 2


def idle_pose(phase):
    tau = math.tau
    p = phase % 1.0
    breath = 0.5 - 0.5 * math.cos(2 * tau * p)          # 한 바퀴에 숨 두 번
    look = _bump(p, 0.30, 0.56)                          # 주위를 둘러본다
    shift = math.sin(tau * p)
    return Pose(
        bodyY=-0.35 * breath,
        bodyX=0.4 * shift,
        lean=0.0,
        hipR=1.5 * shift, hipL=-1.5 * shift,
        kneeR=3.0 + 2.0 * max(0.0, shift), kneeL=3.0 + 2.0 * max(0.0, -shift),
        footR=0.0, footL=0.0,
        armR=2.5 * shift - 1.2 * breath, elbowR=7.0 + 3.0 * breath,
        armL=-2.5 * shift + 1.2 * breath, elbowL=7.0 + 3.0 * breath,
        shoulderR=-0.45 * breath, shoulderL=-0.45 * breath,
        headY=-0.3 * breath + 0.25 * shift,
        headX=0.5 * shift + 0.7 * look,
        tilt=-1.6 * shift,
        turn=0.85 * look,
        blink=1.0 if (0.845 <= p < 0.885) else (0.5 if (0.885 <= p < 0.905) else 0.0),
        breath=breath,
        brow=0.35 * look,
        hairSway=0.5 * shift,
        clothSway=0.4 * shift,
        packBob=-0.3 * breath,
    )


def aim_pose(phase):
    """카메라를 들고 조준 — 숨죽이며 미세하게 흔들린다."""
    tau = math.tau
    p = phase % 1.0
    breath = 0.5 - 0.5 * math.cos(tau * p)
    return Pose(
        bodyY=-0.25 * breath,
        bodyX=0.18 * math.sin(tau * p),
        lean=4.0,
        crouch=0.25,
        hipR=6.0, kneeR=9.0, footR=0.0,
        hipL=-7.0, kneeL=13.0, footL=-4.0,
        armR=132.0 + 3.0 * breath, elbowR=108.0,
        armL=-128.0 - 3.0 * breath, elbowL=104.0,
        shoulderR=-0.9, shoulderL=-0.9,
        headY=0.35 - 0.2 * breath,
        headX=0.2 * math.sin(tau * p),
        tilt=0.6,
        blink=0.85,
        breath=breath,
        brow=1.0,
        hold=HOLD_CAMERA,
        holdT=breath,
    )


def sit_pose(phase):
    """벤치에 앉은 자세 — 허리를 펴고 앉아 숨쉬며 두리번거린다.

    앉으면 엉덩이가 좌석 높이(타일 아트 y≈16)로 올라가므로 머리·모자가 프레임
    위로 넘친다. 그래서 render(..., top_pad=SIT_TOPPAD) 로 상단 여백을 둔다.
    정면 기준: 허벅지는 앞으로(무릎 갈림), 종아리는 아래로 흔들리듯 내려간다.
    """
    tau = math.tau
    p = phase % 1.0
    breath = 0.5 - 0.5 * math.cos(tau * p)
    look = _bump(p, 0.30, 0.56)
    shift = math.sin(tau * p)
    settle = min(1.0, p * 8.0)          # 앉은 직후 1/8 사이클만 자세를 고정
    return Pose(
        bodyY=-5.2 - 0.22 * breath + 0.6 * (1.0 - settle),
        bodyX=0.22 * shift,
        lean=-1.5,
        # 허벅지는 앞으로(무릎 갈림), 종아리는 살짝 앞으로 내려가며 흔들
        hipR=88.0 + 1.5 * shift, kneeR=76.0, footR=2.0,
        hipL=-88.0 + 1.5 * shift, kneeL=-76.0, footL=-2.0,
        armR=34.0 + 2.0 * shift, elbowR=46.0,
        armL=-34.0 + 2.0 * shift, elbowL=46.0,
        shoulderR=-0.2 * breath, shoulderL=-0.2 * breath,
        headY=-0.2 * breath + 0.3 * (1.0 - settle),
        headX=0.55 * shift + 0.7 * look,
        tilt=-1.4 * shift,
        turn=0.8 * look,
        blink=(1.0 if 0.845 <= p < 0.885 else 0.5 if 0.885 <= p < 0.905 else 0.0),
        breath=breath,
        brow=0.3 * look,
        hairSway=0.4 * shift,
        clothSway=0.35 * shift,
        packBob=-0.25 * breath,
    )


# ---------------------------------------------------------------------------
# 렌더러
# ---------------------------------------------------------------------------

FRONT, BACK, SIDE = 0, 1, 2


def render(direction, pose, look, top_pad=0):
    """top_pad: 앉은 자세처럼 머리가 위로 넘칠 때 상단에 둘 여백(px)."""
    bmp = Bitmap(SIZE, SIZE + top_pad)
    g = G(bmp, dy=float(top_pad))
    pal = look.pal
    sc = 0.86 if look.small else 1.0

    ground = 31.4
    footH = 1.7 * sc
    legLen = 8.3 * sc
    thigh, shin = 4.2 * sc, 3.9 * sc
    torsoH = 6.1 * sc
    headR = 6.5 * sc
    headGap = 6.3 * sc

    hipY = ground - footH - legLen + pose.bodyY + pose.crouch * 2.4 * sc
    if look.small:
        hipY += 0.6
    hipX = 16.0 + pose.bodyX
    leanPx = pose.lean * 0.085 * (1.0 if direction == SIDE else 0.0)
    shoulderY = hipY - torsoH + pose.crouch * 0.6
    shoulderX = hipX + leanPx
    headCx = shoulderX + pose.headX + leanPx * 0.8 + pose.tilt * 0.12
    headCy = shoulderY - headGap + pose.headY - pose.breath * 0.25

    depth = 1.0 if direction == SIDE else 0.32
    face = 1.0 if direction != BACK else -1.0     # 뒤돌아보면 앞/뒤가 뒤집힌다

    # ---- 몸통 (방향별 폭) ---------------------------------------------
    if direction == SIDE:
        tL, tR = shoulderX - 5.0 * sc, shoulderX + 5.0 * sc
    else:
        tL, tR = shoulderX - 6.2 * sc, shoulderX + 6.2 * sc

    hipHalf = 2.45 * sc
    shHalf = (5.0 if direction == SIDE else 7.0) * sc

    def leg_root(side):
        return hipX + side * hipHalf * (0.5 if direction == SIDE else 1.0)

    def arm_root(side):
        y = shoulderY + 1.3 * sc + (pose.shoulderR if side > 0 else pose.shoulderL)
        return hipX + leanPx + side * shHalf * (0.2 if direction == SIDE else 1.0), y

    # ---- 다리 -----------------------------------------------------------
    def draw_leg(side, hip, knee, foot, back):
        x0 = leg_root(side)
        d = depth * face
        kx, ky = fk(x0, hipY, hip, thigh, d)
        shinAng = hip - knee
        ax, ay = fk(kx, ky, shinAng, shin, d)
        pants = pal.pants2 if back else pal.pants
        shoe = shade(pal.shoe, 0.8) if back else pal.shoe
        g.seg(x0, hipY, kx, ky, 2.15 * sc, 1.8 * sc, pal.line)
        g.seg(kx, ky, ax, ay, 1.75 * sc, 1.45 * sc, pal.line)
        g.seg(x0, hipY - 0.3, kx, ky, 1.7 * sc, 1.4 * sc, pants)
        g.seg(kx, ky, ax, ay, 1.35 * sc, 1.1 * sc, pants)
        # 발
        if direction == SIDE:
            pitch = math.radians(foot)
            fl = 3.5 * sc
            tx = ax + math.cos(pitch) * fl * face
            ty = ay + math.sin(pitch) * fl * 0.55
            g.seg(ax - 0.7 * face, ay - 0.1, tx, ty, 1.5 * sc, 1.05 * sc, pal.line)
            g.seg(ax - 0.6 * face, ay - 0.1, tx, ty, 1.15 * sc, 0.75 * sc, shoe)
        else:
            lift = max(0.0, (ground - footH * 0.5) - ay)
            fw = 1.95 * sc
            fh = 1.55 * sc - 0.3 * min(1.0, lift * 0.35)
            g.rrect(ax - fw - 0.35, ay - 0.45, ax + fw + 0.35, ay + fh + 0.4, 0.9, pal.line)
            g.rrect(ax - fw, ay - 0.2, ax + fw, ay + fh, 0.8, shoe)
        return ax, ay

    # ---- 팔 -------------------------------------------------------------
    def draw_arm(side, ang, elbow, back):
        x0, y0 = arm_root(side)
        d = depth * face
        upper, fore = 3.6 * sc, 3.4 * sc
        ex, ey = fk(x0, y0, ang, upper, d)
        foreAng = ang + elbow          # 팔꿈치는 항상 앞쪽으로 접힌다
        hx, hy = fk(ex, ey, foreAng, fore, d)
        sleeve = shade(pal.top2, 0.88) if back else pal.top2
        skin = pal.skin2 if back else pal.skin
        g.seg(x0, y0, ex, ey, 1.85 * sc, 1.5 * sc, pal.line)
        g.seg(ex, ey, hx, hy, 1.45 * sc, 1.2 * sc, pal.line)
        g.seg(x0, y0, ex, ey, 1.4 * sc, 1.1 * sc, sleeve)
        g.seg(ex, ey, hx, hy, 1.0 * sc, 0.85 * sc, skin)
        g.circ(hx, hy, 1.25 * sc, pal.line)
        g.circ(hx, hy, 0.95 * sc, skin)
        return hx, hy

    # ---- 몸통 본체 -------------------------------------------------------
    def draw_torso():
        top = shoulderY - 0.6
        bot = hipY + 2.3 * sc
        bw = pose.breath * 0.32
        g.rrect(tL - 0.9 - bw, top - 0.9, tR + 0.9 + bw, bot + 0.6, 3.0 * sc, pal.line)
        g.rrect(tL - bw, top, tR + bw, bot, 2.6 * sc, pal.top)
        if direction == SIDE:
            g.rrect(tL + (tR - tL) * 0.45, top + 0.8, tR - 0.4, bot - 0.5, 2.2 * sc, pal.top2)
        else:
            g.rrect(tR - (tR - tL) * 0.30, top + 0.9, tR - 0.5, bot - 0.5, 2.2 * sc, pal.top2)
            g.rect(tL + (tR - tL) * 0.30, top, tL + (tR - tL) * 0.46, top + 2.1, pal.top2)
        if look.apron:
            g.rrect(tL + 1.6, top + 2.4, tR - 1.6, bot - 0.6, 2.4, 0xFFFDF6E8)
            g.rect(tL + 3.0, top + 2.4, tR - 3.0, top + 3.7, 0xFFE8DFC8)
            g.rect(tL + 4.0, top + 7.6, tR - 4.0, top + 8.9, 0xFFE8DFC8)

    def draw_pack():
        by = pose.packBob
        # 어깨끈
        g.rect(hipX - 4.6, shoulderY - 0.4, hipX - 3.2, hipY - 1.0, shade(pal.pack, 0.7))
        g.rect(hipX + 3.2, shoulderY - 0.4, hipX + 4.6, hipY - 1.0, shade(pal.pack, 0.7))
        g.rrect(hipX - 4.9, shoulderY + 1.3 + by, hipX + 4.9, hipY + 0.8 + by, 2.6, pal.line)
        g.rrect(hipX - 4.2, shoulderY + 2.0 + by, hipX + 4.2, hipY + 0.1 + by, 2.2, pal.pack)
        g.rect(hipX - 2.4, shoulderY + 3.2 + by, hipX + 2.4, hipY - 1.0 + by, pal.pack2)
        g.rect(hipX - 3.2, shoulderY + 2.3 + by, hipX + 3.2, shoulderY + 3.0 + by, pal.pack2)

    # ---- 머리 ------------------------------------------------------------
    def draw_head():
        cx, cy = headCx, headCy
        tilt = pose.tilt
        turn = pose.turn * face
        g.circ(cx, cy, headR + 0.85, pal.line)
        if direction == BACK:
            g.circ(cx, cy, headR, pal.hair)
            g.rect(cx - headR * 0.85, cy + 1.9, cx + headR * 0.85, cy + 3.7, pal.hair2)
            g.rect(cx - headR * 0.6 + pose.hairSway, cy - headR - 0.4,
                   cx + headR * 0.6 + pose.hairSway, cy - headR + 1.1, pal.hair2)
            if look.longHair:
                g.rrect(cx - 4.2, cy + 2.0, cx + 4.2, cy + 8.4, 2.4, pal.hair2)
            return

        g.circ(cx, cy, headR, pal.skin)
        # 머리카락 — 윗머리 + 옆머리 + 흔들리는 앞머리
        sway = pose.hairSway
        capBot = cy - 0.5
        g.oval(cx - headR - 0.15 + sway * 0.18, cy - headR - 0.35,
               cx + headR + 0.15 + sway * 0.18, capBot, pal.hair)
        if direction == SIDE:
            # 뒤통수 머리 (왼쪽) + 이마 앞머리
            g.rrect(cx - headR - 0.2, cy - 2.6, cx - headR + 2.7, cy + 5.0, 1.2, pal.hair)
            g.rect(cx - headR + 1.2, cy + 1.4, cx - headR + 2.7, cy + 5.0 + sway * 0.35, pal.hair2)
            g.poly(pal.hair,
                   cx + 0.4, capBot - 2.2,
                   cx + headR + 0.9 + sway, capBot - 0.4,
                   cx + headR - 1.8, capBot + 1.3,
                   cx + 0.6, capBot + 0.4)
        else:
            g.rrect(cx - headR * 0.98, cy - 2.6, cx - headR * 0.44, cy + 4.4, 0.9, pal.hair)
            g.rrect(cx + headR * 0.44, cy - 2.6, cx + headR * 0.98, cy + 4.4, 0.9, pal.hair)
            g.poly(pal.hair,
                   cx - 4.2 + sway, capBot - 1.4,
                   cx - 1.6 + sway * 1.4, capBot + 1.5,
                   cx - 0.6 + sway, capBot - 1.2)
            g.poly(pal.hair,
                   cx + 0.8 + sway, capBot - 1.2,
                   cx + 2.6 + sway * 1.4, capBot + 1.4,
                   cx + 4.3 + sway, capBot - 1.4)
            if look.longHair:
                g.rrect(cx - headR - 0.7, cy - 1.2, cx - headR + 1.5, cy + 7.4 + sway * 0.3, 1.1, pal.hair2)
                g.rrect(cx + headR - 1.5, cy - 1.2, cx + headR + 0.7, cy + 7.4 - sway * 0.3, 1.1, pal.hair2)

        # 얼굴 (눈/눈썹/입/볼) — tilt/turn 에 따라 이동
        ex = cx + turn * 1.7 + tilt * 0.1
        ey = cy + 1.5 + tilt * 0.05
        openH = 1.85 * (1.0 - pose.blink) + 0.35
        if direction == SIDE:
            g.rect(ex + 2.6, ey - 0.9, ex + 4.0, ey - 0.9 + openH, pal.eye)
            g.rect(ex + 2.5, ey - 2.1 - pose.brow * 0.6, ex + 4.2, ey - 1.5 - pose.brow * 0.6, pal.hair2)
            g.rect(ex + 5.4, ey + 0.1, ex + 6.4, ey + 1.3, pal.skin2)     # 코
            g.rect(ex + 2.6, ey + 1.9, ex + 4.2, ey + 2.9, pal.blush)
            if pose.mouth > 0.2:
                g.rect(ex + 4.6, ey + 1.4, ex + 5.6, ey + 1.4 + pose.mouth * 1.4, 0xFF7A4A3A)
        else:
            lx = ex - 2.9
            rx = ex + 1.5
            g.rect(lx, ey - 0.9, lx + 1.5, ey - 0.9 + openH, pal.eye)
            g.rect(rx, ey - 0.9, rx + 1.5, ey - 0.9 + openH, pal.eye)
            if pose.blink < 0.4:
                g.rect(lx + 0.9, ey - 0.7, lx + 1.4, ey + 0.0, 0xFFFFFFFF)
                g.rect(rx + 0.9, ey - 0.7, rx + 1.4, ey + 0.0, 0xFFFFFFFF)
            if pose.brow > 0.05:
                g.rect(lx - 0.2, ey - 2.3 - pose.brow * 0.7, lx + 1.7, ey - 1.7 - pose.brow * 0.7, pal.hair2)
                g.rect(rx - 0.2, ey - 2.3 - pose.brow * 0.7, rx + 1.7, ey - 1.7 - pose.brow * 0.7, pal.hair2)
            g.rect(cx - headR * 0.88, ey + 1.2, cx - headR * 0.88 + 1.6, ey + 2.4, pal.blush)
            g.rect(cx + headR * 0.88 - 1.6, ey + 1.2, cx + headR * 0.88, ey + 2.4, pal.blush)
            if pose.mouth > 0.2:
                mw = 0.7 + pose.mouth * 0.7
                g.rrect(ex - mw, ey + 2.2, ex + mw, ey + 2.2 + 0.8 + pose.mouth * 1.1, 0.5, 0xFF7A4A3A)

        if look.glasses:
            gx = ex + (2.6 if direction == SIDE else 0.0)
            if direction == SIDE:
                g.circ(gx + 0.7, ey - 0.1, 2.5, pal.line)
                g.circ(gx + 0.7, ey - 0.1, 1.8, 0x66DCF0FF)
                g.rect(gx - 2.4, ey - 0.5, gx - 0.8, ey - 0.1, pal.line)
            else:
                g.circ(ex - 2.1, ey - 0.1, 2.5, pal.line)
                g.circ(ex + 2.3, ey - 0.1, 2.5, pal.line)
                g.circ(ex - 2.1, ey - 0.1, 1.8, 0x66DCF0FF)
                g.circ(ex + 2.3, ey - 0.1, 1.8, 0x66DCF0FF)
                g.rect(ex - 0.4, ey - 0.4, ex + 0.5, ey, pal.line)

    # ---- 장비 -------------------------------------------------------------
    def draw_scarf():
        gear = look.gear
        if gear is None or gear.scarf is None:
            return
        sc_ = gear.scarf
        ny = shoulderY - 1.0
        if direction == SIDE:
            g.rrect(shoulderX - 3.4, ny - 0.9, shoulderX + 3.6, ny + 1.5, 1.1, sc_)
            g.seg(shoulderX - 2.2, ny + 0.8,
                  shoulderX - 3.0 + pose.clothSway * 1.6, ny + 4.6, 1.1, 0.8, sc_)
        else:
            g.rrect(shoulderX - 4.6, ny - 0.9, shoulderX + 4.6, ny + 1.6, 1.2, sc_)
            g.seg(shoulderX + 2.4, ny + 1.0,
                  shoulderX + 3.0 + pose.clothSway * 1.6, ny + 5.0, 1.1, 0.8, sc_)

    def draw_vest():
        gear = look.gear
        if gear is None or gear.vest is None or direction == BACK:
            return
        top = shoulderY - 0.2
        bot = hipY + 0.9
        if direction == SIDE:
            g.rrect(tL + 1.2, top + 0.6, tR - 0.6, bot, 1.8, gear.vest)
            g.rect(tL + 1.2, top + 0.6, tL + 2.0, bot, gear.vestDark)
        else:
            g.rrect(tL - 0.2, top + 0.6, tL + 3.4, bot, 1.4, gear.vest)
            g.rrect(tR - 3.4, top + 0.6, tR + 0.2, bot, 1.4, gear.vest)
            g.rect(tL + 3.0, top + 0.2, tR - 3.0, top + 2.0, gear.vest)
            g.rect(tL - 0.2, top + 0.6, tL + 0.7, bot, gear.vestDark)
            g.rect(tR - 0.7, top + 0.6, tR + 0.2, bot, gear.vestDark)

    def draw_cap():
        gear = look.gear
        if gear is None or gear.cap is None:
            return
        cx, cy = headCx, headCy
        tilt = pose.tilt
        cw = headR + 0.6
        top = cy - headR - 2.0
        bot = cy - headR * 0.18
        g.rrect(cx - cw - 0.5 + tilt * 0.12, top - 0.5, cx + cw + 0.5 + tilt * 0.12, bot + 0.4, 3.4, pal.line)
        g.rrect(cx - cw + tilt * 0.12, top, cx + cw + tilt * 0.12, bot, 3.0, gear.cap)
        g.rect(cx - cw + 0.7 + tilt * 0.12, top + 0.5, cx + cw - 0.7 + tilt * 0.12, top + 2.4, gear.capDark)
        s = face if direction == SIDE else 0.0
        if direction == SIDE:
            if gear.brim:
                g.rrect(cx - cw - 1.4, bot - 1.5, cx + cw + 2.6, bot + 0.5, 0.9, pal.line)
                g.rrect(cx - cw - 1.0, bot - 1.4, cx + cw + 2.2, bot + 0.1, 0.8, gear.cap)
            else:
                g.rect(cx + 2.2, bot - 1.5, cx + cw + 2.6, bot - 0.1, pal.line)
                g.rect(cx + 2.2, bot - 1.4, cx + cw + 2.3, bot - 0.4, gear.capDark)
        elif direction == FRONT:
            if gear.brim:
                g.rrect(cx - cw - 2.8, bot - 1.2, cx + cw + 2.8, bot + 0.9, 1.1, pal.line)
                g.rrect(cx - cw - 2.4, bot - 1.1, cx + cw + 2.4, bot + 0.6, 0.9, gear.cap)
            else:
                g.rect(cx - cw + 0.5, bot - 1.1, cx + cw - 0.5, bot + 0.6, pal.line)
                g.rect(cx - cw + 0.9, bot - 1.0, cx + cw - 0.9, bot + 0.3, gear.capDark)
        if gear.feather is not None:
            fx = cx + (-cw + 1.0 if direction == SIDE else cw - 0.8)
            sway = pose.hairSway * 0.8
            g.seg(fx, top + 1.2, fx + sway - 0.8, top - 3.0, 0.75, 0.45, gear.feather)
            g.circ(fx + sway - 0.9, top - 3.0, 0.7, gear.feather)

    def draw_camera():
        """양손으로 든 카메라 (조준 자세)."""
        if direction == BACK:
            return
        cx = headCx + (2.2 if direction == SIDE else 0.0)
        cy = headCy + 1.8
        g.rrect(cx - 2.7, cy - 1.7, cx + 2.7, cy + 1.6, 1.0, pal.line)
        g.rrect(cx - 2.3, cy - 1.3, cx + 2.3, cy + 1.2, 0.8, 0xFF3A3F49)
        g.rect(cx - 1.8, cy - 1.2, cx + 0.4, cy - 0.6, 0xFF5A616E)
        lensX = cx + (1.9 if direction != SIDE else 2.7)
        g.circ(lensX, cy - 0.1, 1.7, pal.line)
        g.circ(lensX, cy - 0.1, 1.25, 0xFF2B3038)
        g.circ(lensX + 0.3, cy - 0.5, 0.5, 0xFFBFE6FF)
        if pose.holdT > 0.7:
            g.rect(cx - 1.4, cy - 2.7, cx - 0.4, cy - 1.9, 0xFFF2D06B)

    def draw_cane():
        hx = hipX + (4.6 if direction != SIDE else 5.2)
        g.rect(hx, shoulderY + 0.5, hx + 1.4, ground - 0.4, 0xFF8A5A33)
        g.rect(hx - 1.2, shoulderY - 0.6, hx + 2.2, shoulderY + 1.0, 0xFF6B431F)

    # ---- 그리기 순서 -------------------------------------------------------
    backIsRight = pose.hipR < pose.hipL if direction != BACK else pose.hipR > pose.hipL
    if direction == SIDE:
        # 먼 쪽 팔다리 -> 몸통 -> 가까운 쪽 팔다리
        draw_leg(-1, pose.hipL, pose.kneeL, pose.footL, True)
        draw_arm(-1, pose.armL, pose.elbowL, True)
        draw_torso()
        draw_vest()
        if look.pack:
            pass
        draw_leg(1, pose.hipR, pose.kneeR, pose.footR, False)
        draw_scarf()
        draw_head()
        draw_cap()
        draw_arm(1, pose.armR, pose.elbowR, False)
    else:
        if backIsRight:
            draw_leg(1, pose.hipR, pose.kneeR, pose.footR, True)
        else:
            draw_leg(-1, pose.hipL, pose.kneeL, pose.footL, True)
        draw_torso()
        if direction == BACK:
            if look.pack:
                draw_pack()
        else:
            draw_vest()
        if backIsRight:
            draw_leg(-1, pose.hipL, pose.kneeL, pose.footL, False)
        else:
            draw_leg(1, pose.hipR, pose.kneeR, pose.footR, False)
        draw_arm(-1, pose.armL, pose.elbowL, backIsRight is False)
        draw_arm(1, pose.armR, pose.elbowR, backIsRight is True)
        draw_scarf()
        draw_head()
        draw_cap()

    if pose.hold == HOLD_CAMERA:
        draw_camera()
    if look.cane:
        draw_cane()
    return bmp


# ---------------------------------------------------------------------------
# 자전거 (페달을 밟는 라이더)
# ---------------------------------------------------------------------------

RIM = 0xFF23232B
METAL = 0xFF9AA0AD


def _draw_rider_cap(g, look, pal, hx, hy, head_r, direction, sway):
    """라이더의 탐조 모자 — 자전거를 타도 걸을 때(draw_cap)와 같은 모습."""
    gear = look.gear
    if gear is None or gear.cap is None:
        return
    cw = head_r + 0.7
    cap_top = hy - head_r - 1.4
    cap_bot = hy - head_r * 0.18
    g.rrect(hx - cw - 0.4, cap_top - 0.4, hx + cw + 0.4, cap_bot + 0.3, 2.2, pal.line)
    g.rrect(hx - cw, cap_top, hx + cw, cap_bot, 2.1, gear.cap)
    g.rect(hx - cw + 0.6, cap_top + 0.4, hx + cw - 0.6, cap_top + 1.8, gear.capDark)
    if direction == SIDE:
        if gear.brim:
            g.rrect(hx - cw - 1, cap_bot - 1, hx + cw + 2.1, cap_bot + 0.4, 0.8, pal.line)
            g.rrect(hx - cw - 0.7, cap_bot - 1, hx + cw + 1.8, cap_bot + 0.1, 0.7, gear.cap)
        else:
            g.rect(hx + 1.5, cap_bot - 1, hx + cw + 2.1, cap_bot - 0.1, pal.line)
            g.rect(hx + 1.5, cap_bot - 0.9, hx + cw + 1.8, cap_bot - 0.4, gear.capDark)
    elif direction == FRONT:
        if gear.brim:
            g.rrect(hx - cw - 1.7, cap_bot - 1, hx + cw + 1.7, cap_bot + 0.6, 0.9, pal.line)
            g.rrect(hx - cw - 1.3, cap_bot - 1, hx + cw + 1.3, cap_bot + 0.3, 0.8, gear.cap)
        else:
            g.rect(hx - cw + 0.5, cap_bot - 1, hx + cw - 0.5, cap_bot + 0.5, pal.line)
            g.rect(hx - cw + 0.8, cap_bot - 0.9, hx + cw - 0.8, cap_bot + 0.2, gear.capDark)
    if gear.feather is not None:
        fx = hx + (-cw + 0.9 if direction == SIDE else cw - 0.9)
        fs = sway * 0.6
        g.seg(fx, cap_top + 1, fx + fs - 0.7, cap_top - 1.7, 0.65, 0.4, gear.feather)
        g.circ(fx + fs - 0.75, cap_top - 1.7, 0.55, gear.feather)


def ik2(hx, hy, tx, ty, l1, l2, sign):
    """2관절 IK — 엉덩이(hx,hy)에서 발(tx,ty)까지, 무릎 위치를 돌려준다."""
    dx, dy = tx - hx, ty - hy
    d = math.hypot(dx, dy)
    d = max(0.001, min(d, l1 + l2 - 0.02))
    a = (l1 * l1 - l2 * l2 + d * d) / (2 * d)
    h = math.sqrt(max(0.0, l1 * l1 - a * a))
    ux, uy = dx / d, dy / d
    bx, by = hx + ux * a, hy + uy * a
    return bx + (-uy) * h * sign, by + ux * h * sign


# 자전거 도색·외형 — Data.kt의 BikeColors / BikeStyle 과 값을 맞춘다

class BikeColor:
    def __init__(self, id, name, argb):
        self.id, self.name, self.argb = id, name, argb


FRAME_COLORS = [
    BikeColor("red", "레드", 0xFFC9503A),
    BikeColor("orange", "오렌지", 0xFFE2874C),
    BikeColor("yellow", "옐로우", 0xFFF2B63C),
    BikeColor("green", "그린", 0xFF4F8F6A),
    BikeColor("mint", "민트", 0xFF6FB6C9),
    BikeColor("blue", "블루", 0xFF3F6FA0),
    BikeColor("navy", "네이비", 0xFF2F4A6B),
    BikeColor("purple", "퍼플", 0xFF9F7FC8),
    BikeColor("pink", "핑크", 0xFFDB6B9A),
    BikeColor("brown", "브라운", 0xFF8A5A33),
    BikeColor("black", "블랙", 0xFF3A3A44),
    BikeColor("ivory", "아이보리", 0xFFF3EDE2),
]

TIRE_COLORS = [
    BikeColor("black", "블랙", 0xFF3A3A44),
    BikeColor("brown", "브라운", 0xFF6B431F),
    BikeColor("ivory", "아이보리", 0xFFE8E4DC),
    BikeColor("red", "레드", 0xFFB23F44),
    BikeColor("blue", "블루", 0xFF4A6FA5),
    BikeColor("gray", "그레이", 0xFF8A8F9A),
]

SADDLE_COLORS = [
    BikeColor("black", "블랙", 0xFF33241C),
    BikeColor("brown", "브라운", 0xFF8A5A33),
    BikeColor("red", "레드", 0xFFB23F44),
    BikeColor("ivory", "아이보리", 0xFFF0E6D2),
    BikeColor("blue", "블루", 0xFF3F6FA0),
    BikeColor("green", "그린", 0xFF4F8F6A),
]


class BikeStyle:
    """자전거 외형 (모델 + 도색 + 부속품) — 스프라이트 생성에 쓰인다."""

    MODELS = ["basic", "city", "minivelo", "mtb", "road", "cruiser",
              "bmx", "fixie", "ebike", "tandem", "vintage"]

    def __init__(self, model_id="basic", frame=None, tire=None, saddle=None,
                 basket=False, rack=False, light=False, streamers=False, bell=False):
        self.modelId = model_id
        self.frame = frame or FRAME_COLORS[0]
        self.tire = tire or TIRE_COLORS[0]
        self.saddle = saddle or SADDLE_COLORS[0]
        self.basket, self.rack, self.light = basket, rack, light
        self.streamers, self.bell = streamers, bell


def render_bike(direction, phase, look, style=None, pal_override=None):
    """direction: SIDE(오른쪽) / FRONT / BACK, phase: 0~1 페달 회전."""
    style = style or BikeStyle()
    bike_col = style.frame.argb
    bike_dark = shade(bike_col, 0.68)
    tire_col = style.tire.argb
    leather = style.saddle.argb
    kind = style.modelId
    bmp = Bitmap(SIZE, SIZE)
    g = G(bmp)
    pal = pal_override or look.pal
    tau = math.tau
    ang = tau * (phase % 1.0)
    bob = 0.35 * math.cos(2 * ang)
    sway = 0.5 * math.sin(ang)

    def ring(cx, cy, r, w, col, n=14):
        px, py = cx + r, cy
        for i in range(1, n + 1):
            a = tau * i / n
            nx, ny = cx + math.cos(a) * r, cy + math.sin(a) * r
            g.seg(px, py, nx, ny, w, w, col)
            px, py = nx, ny

    def wheel(cx, cy, r, spin, ti=1.1):
        ring(cx, cy, r - 0.5, 1.0, RIM)
        ring(cx, cy, r - ti, max(ti * 0.5, 0.4), tire_col)
        for i in range(4):
            a = spin + i * math.pi / 4
            g.seg(cx - math.cos(a) * (r - 1.8), cy - math.sin(a) * (r - 1.8),
                  cx + math.cos(a) * (r - 1.8), cy + math.sin(a) * (r - 1.8), 0.35, 0.35, METAL)
        g.circ(cx, cy, 1.3, RIM)
        g.circ(cx, cy, 0.7, METAL)

    if direction == SIDE:
        crank = (16.0, 24.2)
        pr = 2.7
        sad_x, sad_y = 11.6, 16.6 + bob
        bar_x, bar_y = 23.0, 15.4 + bob
        front_y, front_r = 0.0, 0.0
        if kind == "minivelo":
            rear, front, wy, wr, ti = 8.4, 23.6, 26.3, 4.3, 1.0
        elif kind == "bmx":
            rear, front, wy, wr, ti = 8.0, 24.0, 26.0, 4.8, 1.5
            sad_y -= 0.6
            bar_y -= 1.3
        elif kind in ("road", "fixie"):
            rear, front, wy, wr, ti = 7.4, 24.6, 25.6, 5.4, 0.9
            bar_y -= 0.7
        elif kind == "mtb":
            rear, front, wy, wr, ti = 7.3, 24.7, 25.5, 5.9, 1.7
        elif kind == "cruiser":
            rear, front, wy, wr, ti = 7.1, 24.9, 25.2, 6.1, 1.9
            sad_y -= 0.3
            bar_y += 0.5
        elif kind == "ebike":
            rear, front, wy, wr, ti = 7.4, 24.6, 25.6, 5.6, 1.3
        elif kind == "tandem":
            rear, front, wy, wr, ti = 6.2, 25.8, 25.6, 5.5, 1.1
        elif kind == "vintage":
            rear, front, wy, wr, ti = 7.6, 22.8, 25.9, 4.2, 1.0
            front_y, front_r = 21.6, 8.2
            sad_y -= 1.6
            bar_x = 24.2
            bar_y -= 3.2
        else:
            rear, front, wy, wr, ti = 7.4, 24.6, 25.6, 5.6, 1.1  # basic/city
        wheel(rear, wy, wr, -ang, ti)
        if kind == "vintage":
            wheel(front, front_y, front_r, -ang, 1.3)
        else:
            wheel(front, wy, wr, -ang, ti)
        if kind == "vintage":
            # 대형 앞바퀴 + 작은 뒷바퀴의 백본 프레임
            g.seg(rear, wy, sad_x + 1, sad_y + 1.3, 0.9, 0.8, bike_col)
            g.seg(sad_x + 1, sad_y + 1.3, bar_x - 1.2, bar_y + 1.3, 0.9, 0.8, bike_col)
            g.seg(bar_x - 1.2, bar_y + 1.3, front, front_y, 0.9, 0.8, bike_col)
            g.seg(rear, wy, crank[0] - 1.2, crank[1] - 1, 0.7, 0.7, bike_dark)
        elif kind == "tandem":
            # 두 사람이 타는 긴 프레임 + 뒤 탑승자 안장
            g.seg(rear, wy, crank[0] - 4.8, crank[1] - 0.4, 0.9, 0.9, bike_dark)
            g.seg(rear, wy, sad_x - 4.2, sad_y + 1.6, 0.9, 0.8, bike_col)
            g.seg(crank[0] - 4.8, crank[1] - 0.4, sad_x - 4.2, sad_y + 1.6, 1.0, 0.9, bike_col)
            g.seg(crank[0] - 4.8, crank[1] - 0.4, crank[0], crank[1], 1.0, 1.0, bike_dark)
            g.seg(crank[0], crank[1], sad_x, sad_y + 1.4, 1.0, 0.9, bike_col)
            g.seg(crank[0], crank[1], bar_x - 0.6, bar_y + 1.6, 1.0, 0.8, bike_col)
            g.seg(sad_x, sad_y + 1.4, bar_x - 0.6, bar_y + 1.6, 0.8, 0.7, bike_col)
            g.seg(bar_x - 0.6, bar_y + 1.6, front, wy, 0.85, 0.75, METAL)
            g.rrect(sad_x - 6.8, sad_y - 0.1, sad_x - 2.8, sad_y + 1.3, 0.8, leather)  # 뒤 안장
        else:
            g.seg(rear, wy, crank[0], crank[1], 0.9, 0.9, bike_dark)
            g.seg(rear, wy, sad_x, sad_y + 1.4, 0.9, 0.8, bike_col)
            g.seg(crank[0], crank[1], sad_x, sad_y + 1.4, 1.0, 0.9, bike_col)
            g.seg(crank[0], crank[1], bar_x - 0.6, bar_y + 1.6, 1.0, 0.8, bike_col)
            g.seg(sad_x, sad_y + 1.4, bar_x - 0.6, bar_y + 1.6, 0.8, 0.7, bike_col)
            g.seg(bar_x - 0.6, bar_y + 1.6, front, wy, 0.85, 0.75, METAL)
            if kind == "mtb":
                g.rrect(bar_x - 1.3, bar_y + 1.6, bar_x + 0.1, bar_y + 5.4, 0.7, METAL)  # 서스펜션 포크
            if kind == "ebike":
                g.rrect(13.6, 21.2, 19.2, 24.2, 1.0, RIM)                    # 배터리
                g.rrect(14.0, 21.5, 18.8, 23.9, 0.9, 0xFF4A4A56)
                g.rect(14.8, 22.1, 17.8, 22.9, 0xFF6FB6C9)
        sad_w = 3.2 if kind == "cruiser" else (1.9 if kind in ("road", "fixie", "bmx") else 2.6)
        g.rrect(sad_x - sad_w, sad_y - 0.2, sad_x + 2.0, sad_y + 1.4, 0.8, leather)
        g.rrect(bar_x - 2.6, bar_y - 0.4, bar_x + 1.6, bar_y + 1.0, 0.7, leather)
        if kind in ("road", "fixie"):
            g.rrect(bar_x + 0.2, bar_y + 0.4, bar_x + 1.8, bar_y + 2.8, 0.9, leather)  # 드롭바
        g.circ(bar_x + 1.4, bar_y + 0.3, 1.1, RIM)

        # 부속품 — 뒤쪽(짐받이)은 라이더보다 먼저
        if style.rack:
            g.rrect(rear - 1.4, wy - wr - 2.4, rear + 5.0, wy - wr - 1.2, 0.5, METAL)
            g.seg(rear - 0.8, wy - wr - 1.4, rear - 0.4, wy - wr + 1.2, 0.5, 0.5, METAL)
            g.seg(rear + 4.2, wy - wr - 1.4, rear + 4.4, wy - wr + 1.2, 0.5, 0.5, METAL)

        # 라이더
        hip = (sad_x + 0.6, sad_y - 0.6)
        shoulder = (hip[0] + 4.2, hip[1] - 6.2)
        for i in range(2):
            a = ang + (0 if i == 0 else math.pi)
            fx, fy = crank[0] + math.cos(a) * pr, crank[1] + math.sin(a) * pr
            kx, ky = ik2(hip[0], hip[1], fx, fy, 4.4, 4.4, -1)
            pants = pal.pants if i == 0 else pal.pants2
            shoe = pal.shoe if i == 0 else shade(pal.shoe, 0.8)
            g.seg(hip[0], hip[1], kx, ky, 2.0, 1.6, pal.line)
            g.seg(kx, ky, fx, fy, 1.6, 1.2, pal.line)
            g.seg(hip[0], hip[1], kx, ky, 1.55, 1.2, pants)
            g.seg(kx, ky, fx, fy, 1.15, 0.9, pants)
            g.seg(crank[0], crank[1], fx, fy, 0.5, 0.5, METAL)
            g.seg(fx - 1.0, fy + 0.4, fx + 1.6, fy + 0.4, 1.05, 0.9, shoe)
            g.rect(fx - 1.4, fy + 1.0, fx + 1.8, fy + 1.9, RIM)
        # 몸통 (앞으로 숙임)
        g.seg(hip[0], hip[1], shoulder[0], shoulder[1], 4.4, 3.9, pal.line)
        g.seg(hip[0], hip[1], shoulder[0], shoulder[1], 3.7, 3.2, pal.top)
        g.seg(hip[0] + 1.0, hip[1] - 0.6, shoulder[0] + 0.8, shoulder[1] + 0.4, 2.0, 1.7, pal.top2)
        if look.gear is not None and look.gear.vest is not None:
            g.seg(hip[0] + 0.4, hip[1] - 0.4, shoulder[0] + 0.2, shoulder[1] + 0.6, 2.4, 2.1, look.gear.vest)
        # 팔 (핸들까지)
        ex, ey = ik2(shoulder[0], shoulder[1], bar_x - 1.0, bar_y - 0.2, 3.6, 3.4, -1)
        g.seg(shoulder[0], shoulder[1], ex, ey, 1.8, 1.5, pal.line)
        g.seg(ex, ey, bar_x - 1.0, bar_y - 0.2, 1.4, 1.2, pal.line)
        g.seg(shoulder[0], shoulder[1], ex, ey, 1.35, 1.1, pal.top2)
        g.seg(ex, ey, bar_x - 1.0, bar_y - 0.2, 0.95, 0.85, pal.skin)
        g.circ(bar_x - 1.0, bar_y - 0.2, 1.15, pal.skin)
        # 목도리 — 달리는 바람에 뒤로 나부낌
        if look.gear is not None and look.gear.scarf is not None:
            sc = look.gear.scarf
            g.rrect(shoulder[0] - 2.4, shoulder[1] - 1.9, shoulder[0] + 2.8, shoulder[1] + 0.2, 1.0, sc)
            g.seg(shoulder[0] - 1.8, shoulder[1] - 0.6, shoulder[0] - 5.4,
                  shoulder[1] + 0.2 + sway * 1.2, 1.1, 0.65, sc)
        # 머리 — 걸을 때(draw_head)와 같은 헤어스타일 (헬멧으로 바뀌지 않음)
        hx, hy = shoulder[0] + 1.6, shoulder[1] - 4.6
        g.circ(hx, hy, 5.0, pal.line)
        g.circ(hx, hy, 4.3, pal.skin)
        hair_back = 5.2 if look.longHair else 3.1
        g.rrect(hx - 4.6, hy - 1.8, hx - 2.4, hy + hair_back, 0.9, pal.hair)
        g.rect(hx - 4.1, hy + 0.8, hx - 2.9, hy + hair_back + 0.4 + sway * 0.5, pal.hair2)
        g.oval(hx - 4.4, hy - 4.7, hx + 4.4, hy - 0.3, pal.hair)
        g.poly(pal.hair, hx + 0.2, hy - 2.0, hx + 4.9 - sway * 0.4, hy - 0.6,
               hx + 3.1, hy + 0.6, hx + 0.4, hy)
        g.rect(hx + 1.6, hy + 0.2, hx + 3.0, hy + 1.8, pal.eye)
        g.rect(hx + 4.0, hy + 1.2, hx + 4.8, hy + 2.2, pal.skin2)
        g.rect(hx + 1.4, hy + 2.6, hx + 2.8, hy + 3.5, pal.blush)
        _draw_rider_cap(g, look, pal, hx, hy, 4.3, SIDE, sway)
        # 부속품 — 앞쪽(바구니/전조등/방울/스트리머)
        if style.basket:
            g.rrect(bar_x + 1.2, bar_y + 1.2, bar_x + 6.6, bar_y + 5.8, 1.0, 0xFFC9A05C)
            g.rect(bar_x + 1.8, bar_y + 2.6, bar_x + 6.0, bar_y + 3.2, 0xFFB08840)
            g.rect(bar_x + 1.8, bar_y + 4.2, bar_x + 6.0, bar_y + 4.8, 0xFFB08840)
        if style.light:
            g.circ(bar_x + 1.8, bar_y - 1.3, 1.3, METAL)
            g.circ(bar_x + 2.4, bar_y - 1.3, 0.75, 0xFFF2E3C2)
        if style.bell:
            g.circ(bar_x - 1.3, bar_y - 1.3, 1.05, 0xFFD9A03C)
            g.circ(bar_x - 1.3, bar_y - 1.7, 0.5, 0xFFF2D06B)
        if style.streamers:
            g.seg(bar_x - 1.2, bar_y + 0.6, bar_x - 4.2, bar_y + 1.8, 0.7, 0.4, 0xFFDB6B9A)
            g.seg(bar_x - 1.4, bar_y + 1.5, bar_x - 4.6, bar_y + 3.2, 0.6, 0.35, 0xFFF2D06B)
        return bmp

    # 정면 / 뒷면
    cx = 16.0 + sway * 0.6
    if kind == "minivelo":
        wr_f = 3.5
    elif kind in ("bmx", "road", "fixie"):
        wr_f = 3.9
    elif kind in ("cruiser", "mtb"):
        wr_f = 4.8
    elif kind == "vintage":
        wr_f = 5.0
    else:
        wr_f = 4.5
    if kind in ("road", "fixie"):   # 핸들바 반폭
        hw = 6.6
    elif kind == "cruiser":
        hw = 9.2
    elif kind == "bmx":
        hw = 7.2
    else:
        hw = 8.0
    tf = 1.7 if kind in ("cruiser", "mtb") else (0.9 if kind in ("road", "fixie") else 1.1)
    wheel(16.0, 27.4, wr_f, -ang, tf)
    g.seg(16.0, 19.0 + bob, 16.0, 24.0, 1.1, 1.0, bike_col)
    g.seg(14.2, 24.0, 14.2, 27.4, 0.8, 0.7, METAL)
    g.seg(17.8, 24.0, 17.8, 27.4, 0.8, 0.7, METAL)
    g.rrect(12.6, 23.2, 19.4, 24.8, 0.8, bike_dark)
    if style.rack:
        g.rrect(11.6, 23.2, 20.4, 24.2, 0.5, METAL)              # 짐받이(뒤에서 보임)
    hip_y = 21.6 + bob
    # 다리 (번갈아 오르내림)
    for i, sx in ((0, 1), (1, -1)):
        a = ang + (0 if i == 0 else math.pi)
        py = 25.4 + math.sin(a) * 2.2
        px = cx + sx * 3.6 + math.cos(a) * 0.5
        root_x = cx + sx * 2.4
        kx, ky = ik2(root_x, hip_y, px, py, 3.6, 3.6, sx)
        pants = pal.pants if math.sin(a) < 0 else pal.pants2
        g.seg(root_x, hip_y, kx, ky, 2.0, 1.6, pal.line)
        g.seg(kx, ky, px, py, 1.6, 1.3, pal.line)
        g.seg(root_x, hip_y, kx, ky, 1.5, 1.2, pants)
        g.seg(kx, ky, px, py, 1.15, 0.95, pants)
        g.rrect(px - 1.9, py - 0.3, px + 1.9, py + 1.5, 0.8, pal.shoe)
    # 몸통
    top = 13.6 + bob
    g.rrect(cx - 6.0, top - 0.9, cx + 6.0, hip_y + 1.4, 3.6, pal.line)
    g.rrect(cx - 5.3, top, cx + 5.3, hip_y + 0.8, 3.2, pal.top)
    if direction == BACK:
        g.rrect(cx - 4.6, top + 1.0, cx + 4.6, hip_y + 0.2, 2.4, pal.pack)
        g.rect(cx - 2.6, top + 2.4, cx + 2.6, hip_y - 1.0, pal.pack2)
    else:
        g.rect(cx + 2.4, top + 0.8, cx + 5.0, hip_y + 0.4, pal.top2)
        if look.gear is not None and look.gear.vest is not None:
            g.rrect(cx - 5.3, top + 0.6, cx - 2.2, hip_y + 0.6, 1.3, look.gear.vest)
            g.rrect(cx + 2.2, top + 0.6, cx + 5.3, hip_y + 0.6, 1.3, look.gear.vest)
    # 팔 (핸들로)
    tilt = sway * 0.8
    g.seg(16.0 - hw, 18.4 + bob - tilt, 16.0 + hw, 18.4 + bob + tilt, 0.9, 0.9, leather)
    if style.basket and direction == FRONT:
        g.rrect(11.4, 19.9, 20.6, 23.4, 1.0, 0xFFC9A05C)          # 앞바구니
        g.rect(12.2, 21.1, 19.8, 21.7, 0xFFB08840)
    if style.light:
        g.circ(16.0, 17.4 + bob, 1.3, METAL)                      # 전조등
        g.circ(16.0, 17.4 + bob, 0.75, 0xFFF2E3C2)
    if style.bell:
        g.circ(16.0 + hw - 1.6, 17.2 + bob + tilt, 1.05, 0xFFD9A03C)
    for sx in (-1, 1):
        sh = (cx + sx * 5.0, top + 2.2)
        hand = (16.0 - hw + 0.4 if sx < 0 else 16.0 + hw - 0.4, 18.4 + bob + sx * tilt)
        ex, ey = ik2(sh[0], sh[1], hand[0], hand[1], 3.4, 3.2, sx)
        g.seg(sh[0], sh[1], ex, ey, 1.8, 1.5, pal.line)
        g.seg(ex, ey, hand[0], hand[1], 1.45, 1.2, pal.line)
        g.seg(sh[0], sh[1], ex, ey, 1.35, 1.1, pal.top2)
        g.seg(ex, ey, hand[0], hand[1], 1.0, 0.85, pal.skin)
        g.circ(hand[0], hand[1], 1.15, pal.skin)
    # 손잡이 (손 위로)
    g.circ(16.0 - hw, 18.4 + bob - tilt, 1.4, RIM)
    g.circ(16.0 + hw, 18.4 + bob + tilt, 1.4, RIM)
    if style.streamers:
        # 스트리머 — 손잡이에서 나풀나풀
        g.seg(16.0 - hw, 19.6 + bob, 16.0 - hw - 1.4, 23 + bob, 0.7, 0.4, 0xFFDB6B9A)
        g.seg(16.0 - hw + 0.6, 19.8 + bob, 16.0 - hw - 0.4, 23.6 + bob, 0.6, 0.35, 0xFFF2D06B)
        g.seg(16.0 + hw, 19.6 + bob, 16.0 + hw + 1.4, 23 + bob, 0.7, 0.4, 0xFFDB6B9A)
        g.seg(16.0 + hw - 0.6, 19.8 + bob, 16.0 + hw + 0.4, 23.6 + bob, 0.6, 0.35, 0xFFF2D06B)
    # 목도리 — 목에 두르고 자락은 앞으로 남긴다
    if look.gear is not None and look.gear.scarf is not None:
        sc = look.gear.scarf
        g.rrect(cx - 3.4, top - 1.7, cx + 3.4, top + 0.3, 1.0, sc)
        g.seg(cx + 2.1, top - 0.2, cx + 3.1 + sway * 1.4, top + 3.6, 1.0, 0.7, sc)
    # 머리 — 걸을 때(draw_head)와 같은 헤어스타일 (헬멧으로 바뀌지 않음)
    hx, hy = cx + sway * 0.5, top - 5.6
    g.circ(hx, hy, 5.4, pal.line)
    g.circ(hx, hy, 4.7, pal.skin if direction == FRONT else pal.hair)
    if direction == FRONT:
        g.oval(hx - 4.8, hy - 5.1, hx + 4.8, hy - 0.5, pal.hair)
        hair_len = 5.8 if look.longHair else 3.4
        g.rrect(hx - 4.8, hy - 1.5, hx - 3.1, hy + hair_len, 0.8, pal.hair)
        g.rrect(hx + 3.1, hy - 1.5, hx + 4.8, hy + hair_len, 0.8, pal.hair)
        g.poly(pal.hair, hx - 3.1 + sway * 0.4, hy - 1.2, hx - 1.2, hy + 0.6,
               hx - 0.4 + sway * 0.4, hy - 1.2)
        g.poly(pal.hair, hx + 0.6 + sway * 0.4, hy - 1.1, hx + 1.9, hy + 0.6,
               hx + 3.1 + sway * 0.4, hy - 1.2)
        g.rect(hx - 2.9, hy + 0.4, hx - 1.4, hy + 2.2, pal.eye)
        g.rect(hx + 1.4, hy + 0.4, hx + 2.9, hy + 2.2, pal.eye)
        g.rect(hx - 4.2, hy + 2.6, hx - 2.6, hy + 3.7, pal.blush)
        g.rect(hx + 2.6, hy + 2.6, hx + 4.2, hy + 3.7, pal.blush)
    else:
        g.rect(hx - 3.6, hy + 1.6, hx + 3.6, hy + 3.4, pal.hair2)
        if look.longHair:
            g.rrect(hx - 3.0, hy + 1.6, hx + 3.0, hy + 6.2, 1.8, pal.hair2)
    _draw_rider_cap(g, look, pal, hx, hy, 4.7, direction, sway)
    return bmp


NPC_PROFESSOR, NPC_SHOP, NPC_VILLAGER, NPC_KID, NPC_ELDER = 0, 1, 2, 3, 4


def npc_pose(kind, phase):
    tau = math.tau
    p = phase % 1.0
    ps = idle_pose(p)
    if kind == NPC_PROFESSOR:
        # 고개를 끄덕이고, 가끔 안경을 고쳐 쓴다
        push = _bump(p, 0.55, 0.85)
        ps.headY += 0.55 * math.sin(2 * tau * p)
        ps.tilt += 1.4 * math.sin(tau * p)
        ps.armR = -150.0 * push + 3.0
        ps.elbowR = 8.0 + 112.0 * push
        ps.brow = 0.5 * push
        ps.blink = 1.0 if 0.60 <= p < 0.64 else ps.blink
    elif kind == NPC_SHOP:
        # 손 흔들기
        wave = _bump(p, 0.10, 0.62)
        ps.armR = -20.0 - 128.0 * wave
        ps.elbowR = 12.0 + 26.0 * wave + 22.0 * wave * math.sin(tau * 3.0 * p)
        ps.mouth = 0.5 * wave
        ps.headX += 0.3 * wave
        ps.tilt += -1.2 * wave
    elif kind == NPC_VILLAGER:
        look = math.sin(tau * p)
        ps.turn = 0.9 * look
        ps.headX = 0.9 * look
        ps.tilt = -1.8 * look
        ps.bodyX = 0.5 * look
    elif kind == NPC_KID:
        # 제자리에서 통통
        hop = max(0.0, math.sin(tau * 2.0 * p)) ** 0.8
        ps.bodyY = -3.2 * hop
        ps.kneeL = 16.0 + 26.0 * (1.0 - hop)
        ps.kneeR = 16.0 + 26.0 * (1.0 - hop)
        ps.hipL = -9.0 * hop
        ps.hipR = 9.0 * hop
        ps.footL = -18.0 * hop
        ps.footR = -18.0 * hop
        ps.armL = -26.0 - 52.0 * hop
        ps.armR = 26.0 + 52.0 * hop
        ps.elbowL = ps.elbowR = 18.0
        ps.mouth = 0.8
        ps.hairSway = -1.4 * hop
        ps.headY = -0.5 * hop
    elif kind == NPC_ELDER:
        tap = _bump(p, 0.44, 0.60)
        ps.bodyY += 0.5 + 0.25 * math.sin(tau * p)
        ps.lean = 8.0
        ps.crouch = 0.35
        ps.armR = 16.0 + 10.0 * tap
        ps.elbowR = 14.0
        ps.tilt += 1.0
        ps.headY += 0.6
    return ps


# ---------------------------------------------------------------------------
# 골목 고양이 (32x26) — 꼬리·귀·눈·다리가 따로 움직인다
# ---------------------------------------------------------------------------

CAT_W, CAT_H = 32, 26
C_ORANGE = 0xFFE8944A
C_ORANGE2 = 0xFFC97430
C_CREAM = 0xFFFBEFD8
C_LINE = 0xFF33241C
C_EYE = 0xFF4F8F52
C_PINK = 0xFFF2A3B3


def render_cat(walking, phase):
    """왼쪽을 바라보는 고양이. walking=False 면 앉은 자세."""
    bmp = Bitmap(CAT_W, CAT_H)
    g = G(bmp)
    tau = math.tau
    p = phase % 1.0

    def ear(cx, cy, dx, twitch):
        g.poly(C_ORANGE, cx - 4.4 + dx, cy - 2.6, cx - 3.4 + dx, cy - 6.8 - twitch, cx - 1.0 + dx, cy - 3.4)
        g.poly(C_ORANGE, cx + 1.0 + dx, cy - 3.4, cx + 3.2 + dx, cy - 6.6 + twitch, cx + 4.2 + dx, cy - 2.6)
        g.poly(C_PINK, cx - 3.7 + dx, cy - 3.0, cx - 3.1 + dx, cy - 5.6 - twitch * 0.8, cx - 1.8 + dx, cy - 3.6)
        g.poly(C_PINK, cx + 1.8 + dx, cy - 3.6, cx + 3.0 + dx, cy - 5.4 + twitch * 0.8, cx + 3.6 + dx, cy - 3.0)

    def head(cx, cy, twitch, blink, look):
        g.circ(cx, cy, 5.2, C_LINE)
        g.circ(cx, cy, 4.6, C_ORANGE)
        ear(cx, cy, 0.0, twitch)
        eh = 1.6 * (1.0 - blink) + 0.25
        g.rect(cx - 2.6 + look, cy - 1.4, cx - 1.2 + look, cy - 1.4 + eh, C_EYE)
        g.rect(cx + 1.2 + look, cy - 1.4, cx + 2.6 + look, cy - 1.4 + eh, C_EYE)
        g.rect(cx - 0.7, cy + 1.4, cx + 0.7, cy + 2.6, C_PINK)
        for dy, dx in ((0.6, -3.8), (1.8, -3.6), (0.6, 3.8), (1.8, 3.6)):
            g.seg(cx + (4.6 if dx > 0 else -4.6), cy + dy,
                  cx + dx * 2.2, cy + dy - (0.9 if dy < 1.0 else -0.6), 0.4, 0.3, 0xCCFDF6E8)

    if not walking:
        # ---- 앉은 자세: 꼬리를 살랑, 가끔 귀를 쫑긋, 눈을 깜빡 ----
        swing = math.sin(tau * p)
        twitch = 1.2 * _bump(p, 0.62, 0.72)
        blink = 1.0 if 0.86 <= p < 0.90 else 0.0
        look = 0.6 * math.sin(tau * (p - 0.15))
        breath = 0.35 * math.sin(tau * 2 * p)
        # 꼬리 (아래에서 위로 흔들림)
        tx0, ty0 = 23.0, 20.0
        for i in range(4):
            t0, t1 = i / 4.0, (i + 1) / 4.0
            def tail_pt(t):
                a = -1.35 + t * (0.5 + 0.55 * swing)
                r = 9.5 * t
                return tx0 + math.cos(a) * r * 0.75 + 0.6 * t, ty0 - abs(math.sin(a)) * r * 0.1 - r * 0.92
            x0, y0 = tail_pt(t0)
            x1, y1 = tail_pt(t1)
            g.seg(x0, y0, x1, y1, 1.5 - 0.15 * i, 1.35 - 0.15 * i, C_ORANGE2 if i < 3 else C_CREAM)
        # 몸
        g.rrect(6.5, 12.4 - breath * 0.3, 24.5, 24.6, 6.5, C_LINE)
        g.rrect(7.5, 13.4 - breath * 0.3, 23.5, 23.6, 5.8, C_ORANGE)
        g.rect(11.0, 13.6, 13.0, 22.6, C_ORANGE2)
        g.rect(15.4, 13.4, 17.4, 23.0, C_ORANGE2)
        g.rect(19.6, 13.8, 21.6, 22.4, C_ORANGE2)
        g.rrect(9.5, 15.5, 19.5, 22.5, 3.5, C_CREAM)
        g.rrect(9.5, 22.4, 13.4, 24.9, 1.0, C_CREAM)
        g.rrect(15.8, 22.4, 19.6, 24.9, 1.0, C_CREAM)
        head(14.0, 9.4 - breath * 0.35, twitch, blink, look)
        return bmp

    # ---- 걷는 자세: 네 다리가 교차, 꼬리는 세워서 살랑 ----
    bob = 0.4 * math.cos(2 * tau * p)
    top = 10.4 + bob
    swing = math.sin(tau * p)
    # 꼬리 (세워서 살랑)
    for i in range(4):
        t0, t1 = i / 4.0, (i + 1) / 4.0
        def tail_pt(t):
            a = -1.5 + t * (0.35 + 0.5 * math.sin(tau * (p - 0.2)))
            r = 10.0 * t
            return 24.0 + math.cos(a) * r * 0.55, top + 4.0 - r * 0.95
        x0, y0 = tail_pt(t0)
        x1, y1 = tail_pt(t1)
        g.seg(x0, y0, x1, y1, 1.45 - 0.15 * i, 1.3 - 0.15 * i, C_ORANGE2 if i < 3 else C_CREAM)
    # 뒷다리 -> 몸 -> 앞다리 순서
    def leg(x, ph, back):
        a = math.sin(tau * (p + ph))
        kx = x + a * 2.2
        ky = top + 9.4
        fx = x + a * 3.4
        fy = 24.4 - max(0.0, math.cos(tau * (p + ph))) * 1.8
        col = C_ORANGE2 if back else C_ORANGE
        g.seg(x, top + 7.2, kx, ky, 1.5, 1.2, C_LINE)
        g.seg(kx, ky, fx, fy, 1.25, 1.0, C_LINE)
        g.seg(x, top + 7.2, kx, ky, 1.1, 0.85, col)
        g.seg(kx, ky, fx, fy, 0.9, 0.7, col)
        g.rrect(fx - 1.5, fy - 0.2, fx + 1.5, fy + 1.4, 0.7, C_CREAM if not back else C_ORANGE2)
    leg(21.0, 0.5, True)
    leg(9.0, 0.0, True)
    g.rrect(3.5, top, 26.5, top + 10.2, 5.0, C_LINE)
    g.rrect(4.5, top + 1.0, 25.5, top + 9.2, 4.4, C_ORANGE)
    g.rect(9.0, top + 1.2, 11.0, top + 9.0, C_ORANGE2)
    g.rect(14.6, top + 1.0, 16.6, top + 9.2, C_ORANGE2)
    g.rect(20.0, top + 1.4, 22.0, top + 8.8, C_ORANGE2)
    g.rrect(6.5, top + 5.0, 23.5, top + 9.0, 2.5, C_CREAM)
    leg(22.6, 0.0, False)
    leg(10.6, 0.5, False)
    head(6.2, 8.6 + bob, 1.1 * _bump(p, 0.70, 0.80), 0.0, -0.3 * swing)
    return bmp
