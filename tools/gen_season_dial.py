#!/usr/bin/env python3
"""사계절 다이얼 SVG 마스터를 생성한다.

출력: app/src/main/assets/season_dial.svg

HUD의 계절 표시(assets의 wood_fired_oven.svg와 같은 경로)로 쓰이는 원형 다이얼로,
네 계절이 90도씩 시계 방향으로 배치되고 한가운데는 바늘 축 자리다.
바늘 자체는 계절 진행에 따라 회전해야 해서 Hud.kt가 코드로 그린다.

SvgIllustrations.kt의 미니 렌더러가 지원하는 도형만 사용한다:
  rect / circle / ellipse / line / polygon / polyline
  fill / stroke / stroke-width / opacity (그라데이션·경로·변환 없음)

용법: python3 tools/gen_season_dial.py
"""
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "main" / "assets" / "season_dial.svg"

SIZE = 256
C = SIZE / 2

# 0도 = 12시 방향, 시계 방향 증가. 사계절 순서(봄→여름→가을→겨울)와 일치한다.
def pt(angle_deg: float, r: float, cx: float = C, cy: float = C):
    a = math.radians(angle_deg)
    return (cx + r * math.sin(a), cy - r * math.cos(a))


def fmt(p) -> str:
    return f"{p[0]:.2f},{p[1]:.2f}"


def pts_str(points) -> str:
    return " ".join(fmt(p) for p in points)


def wedge(start: float, end: float, r: float):
    """중심 + 호 샘플링으로 부채꼴 다각형 점 목록을 만든다."""
    points = [(C, C)]
    a = start
    while a < end:
        points.append(pt(a, r))
        a += 4
    points.append(pt(end, r))
    return points


def rot(p, deg: float):
    a = math.radians(deg)
    x, y = p
    return (x * math.cos(a) - y * math.sin(a), x * math.sin(a) + y * math.cos(a))


lines = []


def add(s: str):
    lines.append(s)


def circle(cx, cy, r, fill=None, stroke=None, sw=1.0, opacity=None):
    attrs = f'cx="{cx:.2f}" cy="{cy:.2f}" r="{r:.2f}"'
    attrs += f' fill="{fill}"' if fill else ' fill="none"'
    if stroke:
        attrs += f' stroke="{stroke}" stroke-width="{sw:.2f}"'
    if opacity is not None:
        attrs += f' opacity="{opacity:.2f}"'
    add(f"  <circle {attrs}/>")


def ellipse(cx, cy, rx, ry, fill=None, stroke=None, sw=1.0, opacity=None):
    attrs = f'cx="{cx:.2f}" cy="{cy:.2f}" rx="{rx:.2f}" ry="{ry:.2f}"'
    attrs += f' fill="{fill}"' if fill else ' fill="none"'
    if stroke:
        attrs += f' stroke="{stroke}" stroke-width="{sw:.2f}"'
    if opacity is not None:
        attrs += f' opacity="{opacity:.2f}"'
    add(f"  <ellipse {attrs}/>")


def line(p1, p2, stroke, sw, opacity=None):
    attrs = (
        f'x1="{p1[0]:.2f}" y1="{p1[1]:.2f}" x2="{p2[0]:.2f}" y2="{p2[1]:.2f}"'
        f' fill="none" stroke="{stroke}" stroke-width="{sw:.2f}"'
    )
    if opacity is not None:
        attrs += f' opacity="{opacity:.2f}"'
    add(f"  <line {attrs}/>")


def polygon(points, fill=None, stroke=None, sw=1.0, opacity=None):
    attrs = f'points="{pts_str(points)}"'
    attrs += f' fill="{fill}"' if fill else ' fill="none"'
    if stroke:
        attrs += f' stroke="{stroke}" stroke-width="{sw:.2f}"'
    if opacity is not None:
        attrs += f' opacity="{opacity:.2f}"'
    add(f"  <polygon {attrs}/>")


# ---------------------------------------------------------------------
add(f'<svg xmlns="http://www.w3.org/2000/svg" width="{SIZE}" height="{SIZE}" viewBox="0 0 {SIZE} {SIZE}">')
add("  <!-- 사계절 다이얼: 봄(12시)에서 시작해 시계 방향으로 여름/가을/겨울. -->")
add("  <!-- 바늘은 계절 진행에 따라 Hud.kt가 코드 그린다. -->")

# 부드러운 바닥 그림자
circle(C, C + 2.5, 123.5, fill="#2e2118", opacity=0.26)

# 황동 베젤 (바깥부터: 어두운 테 → 황동 → 밝은 띠 → 홈)
circle(C, C, 124, fill="#5c3d1e")
circle(C, C, 120.5, fill="#c89648")
circle(C, C, 117, fill="#e9c476")
circle(C, C, 113.5, fill="#8a5e2a")
# 크림 문자판
circle(C, C, 110, fill="#fbf2dc")

# 베젤의 작은 나사 4개 (미니맵 나침반과 같은 디테일)
for deg in (45, 135, 225, 315):
    x, y = pt(deg, 117)
    circle(x, y, 2.6, fill="#7a5426", stroke="#4a2f14", sw=0.8)
    circle(x - 0.7, y - 0.8, 0.8, fill="#f2dca4")

# 사계절 부채꼴 — 반지름 96, 사이는 크림 구분선
WEDGE_R = 96
seasons = [
    # (시작각, 끝각, 면색, 모서리색)
    (0, 90, "#f8cfdd", "#e29ab6"),    # 봄
    (90, 180, "#c4e5a9", "#8fbf6f"),  # 여름
    (180, 270, "#f2c083", "#d98a3c"), # 가을
    (270, 360, "#cbdef1", "#8fb6de"), # 겨울
]
for start, end, fill, edge in seasons:
    polygon(wedge(start, end, WEDGE_R), fill=fill, stroke=edge, sw=1.6)

# 계절 구분선 (크림)
for deg in (0, 90, 180, 270):
    line(pt(deg, 14), pt(deg, WEDGE_R), "#fbf2dc", 4)

# ---------------------------------------------------------------------
# 계절 문양 (중심에서 반지름 62, 각 계절 한가운데)
MOTIF_R = 62

# --- 봄: 벚꽃 (꽃잎 5장 + 노란 꽃술) ---
fx, fy = pt(45, MOTIF_R)
for k in range(5):
    px, py = pt(45 + k * 72, 8.4, fx, fy)
    circle(px, py, 7.4, fill="#f4a0be", stroke="#db7fa5", sw=1.0)
circle(fx, fy, 5.0, fill="#ffd35c", stroke="#dfa335", sw=1.2)
circle(fx - 1.8, fy - 1.9, 1.5, fill="#fff3c9", opacity=0.9)

# --- 여름: 태양 (핵 + 광선 8개) ---
sx, sy = pt(135, MOTIF_R)
for k in range(8):
    a = k * 45
    line(pt(a, 13.5, sx, sy), pt(a, 19.5, sx, sy), "#efa22c", 3.4)
circle(sx, sy, 9.8, fill="#ffc63e", stroke="#e0952a", sw=1.6)
circle(sx - 2.6, sy - 2.8, 2.2, fill="#ffe9a8", opacity=0.9)

# --- 가을: 단풍잎 ---
lx, ly = pt(225, MOTIF_R)
half = [
    (0, -16.5), (2.7, -11.0), (8.4, -13.6), (6.6, -7.9), (13.4, -5.8),
    (8.7, -1.5), (11.2, 4.8), (4.7, 3.6), (2.3, 9.6), (0, 7.4),
]
leaf = half + [(-x, y) for x, y in reversed(half[:-1])]
leaf = [rot(p, -24) for p in leaf]
leaf = [(lx + x, ly + y) for x, y in leaf]
polygon(leaf, fill="#dd7430", stroke="#a85420", sw=1.5)
line(rot((0, -13.2), -24), rot((0, 6.6), -24), "#b05a20", 1.3, opacity=0.75)
line(rot((0, -3.5), -24), rot((5.6, -7.6), -24), "#b05a20", 1.1, opacity=0.6)
line(rot((0, -3.5), -24), rot((-5.6, -7.6), -24), "#b05a20", 1.1, opacity=0.6)
stem0 = rot((0, 7.4), -24)
stem1 = rot((1.9, 14.8), -24)
line((lx + stem0[0], ly + stem0[1]), (lx + stem1[0], ly + stem1[1]), "#8f4a1e", 2.4)

# --- 겨울: 눈꽃 (팔 3개 + 끝 가지) ---
wx, wy = pt(315, MOTIF_R)
for base in (0, 60, 120):
    line(pt(base, -14.5, wx, wy), pt(base, 14.5, wx, wy), "#6e9fd4", 3.2)
for tip_deg in (0, 60, 120, 180, 240, 300):
    t = pt(tip_deg, 14.5, wx, wy)
    line(t, pt(tip_deg - 24, 10.0, wx, wy), "#6e9fd4", 2.2)
    line(t, pt(tip_deg + 24, 10.0, wx, wy), "#6e9fd4", 2.2)
circle(wx, wy, 2.8, fill="#a9cbee", stroke="#6e9fd4", sw=1.2)
sp1 = pt(32, 19.5, wx, wy)
sp2 = pt(205, 18.5, wx, wy)
circle(sp1[0], sp1[1], 1.3, fill="#ffffff", opacity=0.85)
circle(sp2[0], sp2[1], 1.1, fill="#ffffff", opacity=0.7)

# 은은한 유리 광택
ellipse(C, 64, 86, 34, fill="#ffffff", opacity=0.09)

# ---------------------------------------------------------------------
# 날짜 점 28개 (하루 하나, 7일 = 한 계절). 계절이 시작하는 자리는 금색 큰 점.
for d in range(28):
    deg = d * 360 / 28
    x, y = pt(deg, 103)
    if d % 7 == 0:
        circle(x, y, 3.2, fill="#b5822e", stroke="#8a5e2a", sw=1.0)
        circle(x - 0.6, y - 0.7, 1.1, fill="#f6dfac")
    else:
        circle(x, y, 1.9, fill="#c4a269")

# 안쪽 장식 링 + 바늘 축 받침
circle(C, C, 34, stroke="#e7d4aa", sw=1.6)
circle(C, C, 17, fill="#dcbe8c", stroke="#8a5e2a", sw=1.6)
circle(C, C, 12.5, fill="#fbf2dc")

add("</svg>")

OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"wrote {OUT.relative_to(ROOT)} ({len(lines)} lines)")
