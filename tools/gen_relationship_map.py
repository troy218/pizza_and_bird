#!/usr/bin/env python3
"""
docs/img/story/relationship_map.png — 「사람들의 계절」 인물 관계도를 그린다.

게임이 내장한 글꼴(Jua 주아 / Gowun Dodum 고운돋움)로 렌더하므로
게임 안 대사와 같은 얼굴로 읽힌다. 관계 정의는 docs/STORY.md §1.2 가 원장이다.
(글꼴 서브셋에 없는 글자 ①②③ 등은 쓰지 않는다.)

용법: python3 tools/gen_relationship_map.py
출력: docs/img/story/relationship_map.png
"""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
FONT_DIR = ROOT / "app" / "src" / "main" / "assets" / "font"
OUT = ROOT / "docs" / "img" / "story" / "relationship_map.png"

S = 2  # 슈퍼샘플 배율

# ── 팔레트 (게임 따뜻한 종이 톤) ────────────────────────────────────
PAPER = (247, 241, 228)
PAPER2 = (239, 231, 213)
INK = (58, 46, 42)
INK_SOFT = (122, 106, 96)
LINE_SOFT = (176, 158, 138)
WHITE = (255, 253, 247)

CAMEL = (201, 123, 74)      # 할머니 계열
SAGE = (111, 146, 92)       # 박사 계열
SKY = (47, 98, 165)         # 플레이어
ROSE = (194, 91, 106)       # 상점
PURPLE = (138, 111, 178)    # 이웃
AMBER = (205, 151, 48)      # 꼬마
TEAL = (72, 140, 136)       # 어르신


def font(name: str, px: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONT_DIR / name), px)


F_TITLE = font("display_jua.ttf", 44 * S)
F_SUB = font("body_gowundodum.ttf", 20 * S)
F_NODE = font("display_jua.ttf", 25 * S)
F_NODE_SM = font("display_jua.ttf", 21 * S)
F_NODE_ROLE = font("body_gowundodum.ttf", 16 * S)
F_EDGE = font("body_gowundodum.ttf", 15 * S)
F_NOTE = font("body_gowundodum.ttf", 15 * S)
F_NOTE_B = font("display_jua.ttf", 16 * S)


def text_size(draw: ImageDraw.ImageDraw, txt: str, f: ImageFont.FreeTypeFont) -> tuple[int, int]:
    l, t, r, b = draw.textbbox((0, 0), txt, font=f)
    return r - l, b - t


def center_text(draw, cx, cy, txt, f, fill=INK):
    w, h = text_size(draw, txt, f)
    draw.text((cx - w / 2, cy - h / 2 - 2 * S), txt, font=f, fill=fill)


class Node:
    """사각 노드 — 이름(주아) + 한 줄 역할(고운뎃움)."""

    def __init__(self, cx, cy, w, h, name, role, color, small=False):
        self.cx, self.cy, self.w, self.h = cx, cy, w, h
        self.name, self.role, self.color = name, role, color
        self.small = small

    def draw(self, draw):
        x0, y0 = self.cx - self.w / 2, self.cy - self.h / 2
        r = 14 * S
        draw.rounded_rectangle([x0 + 3 * S, y0 + 4 * S, x0 + self.w + 3 * S, y0 + self.h + 4 * S],
                               radius=r, fill=(211, 198, 177))
        draw.rounded_rectangle([x0, y0, x0 + self.w, y0 + self.h], radius=r, fill=WHITE,
                               outline=self.color, width=3 * S)
        draw.rounded_rectangle([x0, y0, x0 + self.w, y0 + 11 * S], radius=r, fill=self.color)
        draw.rectangle([x0, y0 + 5 * S, x0 + self.w, y0 + 11 * S], fill=self.color)
        f_name = F_NODE_SM if self.small else F_NODE
        center_text(draw, self.cx, self.cy - 4 * S, self.name, f_name)
        center_text(draw, self.cx, self.cy + 22 * S, self.role, F_NODE_ROLE, fill=INK_SOFT)


def edge(draw, a: Node, b: Node, label: str, color, dashed=False, t=0.5,
         bend=0.0, off=(0, 0)):
    """노드 테두리에서 테두리까지 곡선 + 라벨 pill (화살촉은 b 쪽)."""
    x1, y1 = a.cx, a.cy
    x2, y2 = b.cx, b.cy
    dx, dy = x2 - x1, y2 - y1
    dist = math.hypot(dx, dy) or 1
    ux, uy = dx / dist, dy / dist
    def boundary(w: float, h: float) -> float:
        # 중심에서 u 방향으로 뻗은 광선이 사각형 테두리를 빠져나가는 거리
        cands = []
        if abs(ux) > 1e-6:
            cands.append((w / 2) / abs(ux))
        if abs(uy) > 1e-6:
            cands.append((h / 2) / abs(uy))
        return min(cands) if cands else 0.0

    ra = min(boundary(a.w, a.h) + 4 * S, dist * 0.45)
    rb = min(boundary(b.w, b.h) + 4 * S, dist * 0.45)
    x1, y1 = x1 + ux * ra, y1 + uy * ra
    x2, y2 = x2 - ux * rb, y2 - uy * rb
    mx, my = (x1 + x2) / 2 - uy * bend, (y1 + y2) / 2 + ux * bend
    pts = []
    n = 48
    for i in range(n + 1):
        tt = i / n
        bx = (1 - tt) ** 2 * x1 + 2 * (1 - tt) * tt * mx + tt ** 2 * x2
        by = (1 - tt) ** 2 * y1 + 2 * (1 - tt) * tt * my + tt ** 2 * y2
        pts.append((bx, by))
    if dashed:
        seg = []
        for i in range(len(pts) - 1):
            seg.append(pts[i])
            if i % 4 == 3:
                draw.line(seg, fill=color, width=3 * S)
                seg = []
        if len(seg) > 1:
            draw.line(seg, fill=color, width=3 * S)
    else:
        draw.line(pts, fill=color, width=5 * S)

    ax, ay = pts[-1]
    ang = math.atan2(pts[-1][1] - pts[-2][1], pts[-1][0] - pts[-2][0])
    s = 10 * S
    draw.polygon([(ax, ay),
                  (ax - s * math.cos(ang - 0.52), ay - s * math.sin(ang - 0.52)),
                  (ax - s * math.cos(ang + 0.52), ay - s * math.sin(ang + 0.52))], fill=color)

    idx = max(2, min(len(pts) - 3, int(len(pts) * t)))
    lx, ly = pts[idx][0] + off[0], pts[idx][1] + off[1]
    tw, th = text_size(draw, label, F_EDGE)
    pad = 5 * S
    draw.rounded_rectangle([lx - tw / 2 - pad, ly - th / 2 - pad,
                            lx + tw / 2 + pad, ly + th / 2 + pad],
                           radius=8 * S, fill=WHITE, outline=color, width=2 * S)
    draw.text((lx - tw / 2, ly - th / 2 - 2 * S), label, font=F_EDGE, fill=INK)


def build():
    W, H = 1560 * S, 1060 * S
    im = Image.new("RGB", (W, H), PAPER)
    draw = ImageDraw.Draw(im)

    for x in range(0, W, 46 * S):
        draw.line([(x, 0), (x, H)], fill=PAPER2, width=1 * S)
    for y in range(0, H, 46 * S):
        draw.line([(0, y), (W, y)], fill=PAPER2, width=1 * S)

    center_text(draw, W / 2, 52 * S, "「사람들의 계절」 인물 관계도", F_TITLE)
    center_text(draw, W / 2, 98 * S,
                "낡은 수첩을 사이에 둔 사람들의 지도 — 원장은 docs/STORY.md 인물 관계도 절", F_SUB, fill=INK_SOFT)

    def node(cx, cy, w, h, name, role, color, small=False):
        return Node(cx * S, cy * S, w * S, h * S, name, role, color, small)

    grandma = node(300, 285, 244, 112, "할머니", "수첩의 앞장 · 화덕과 참새", CAMEL)
    prof = node(1230, 285, 252, 112, "보리 박사", "광릉숲 · 조류학자", SAGE)
    player = node(760, 505, 288, 128, "플레이어", "수첩을 이어 쓰는 사람", SKY)
    shop = node(1268, 545, 262, 112, "카메라샵 사장님들", "12개 도시 · 진열대마다 다르게", ROSE, small=True)
    bo = node(1005, 762, 230, 112, "보경 · 강릉", "안목 카페 · 첫 손님의 기억", ROSE, small=True)
    elder = node(240, 575, 232, 112, "동네 어르신들", "옛 이웃의 기억", TEAL, small=True)
    villager = node(295, 855, 242, 112, "동네 주민들", "창밖의 첫 스승", PURPLE, small=True)
    kid = node(760, 890, 242, 112, "꼬마들", "후배 탐조인", AMBER, small=True)
    folks = node(1262, 855, 264, 112, "32개 동네 이웃 66명", "한 동네에 한 사람", PURPLE, small=True)

    # ── 관계선 (docs/STORY.md 인물 관계표와 같은 순서) ──
    edge(draw, grandma, prof, "이십 년, 쌍안경 가방을 나눠 멘 동료", CAMEL, t=0.5, bend=88 * S, off=(0, 6 * S))
    edge(draw, grandma, player, "수첩 · 화덕피자 · 가르침의 계승", CAMEL, t=0.42, bend=26 * S, off=(-40 * S, -22 * S))
    edge(draw, prof, player, "멘토에서 동반자로 (6장의 고백)", SAGE, t=0.42, bend=-26 * S, off=(44 * S, -22 * S))
    edge(draw, prof, shop, "오래된 인연 — 수첩 이야기가 오가면", ROSE, t=0.5, bend=-44 * S, off=(64 * S, -8 * S))
    edge(draw, shop, player, "장비를 건네는 손님", ROSE, t=0.38, bend=22 * S, off=(0, 24 * S))
    edge(draw, elder, grandma, "그 시절을 기억하는 이웃", TEAL, dashed=True, t=0.5, bend=22 * S, off=(-64 * S, -10 * S))
    edge(draw, elder, player, "회상으로 건네는 돌봄", TEAL, dashed=True, t=0.5, bend=10 * S, off=(0, 22 * S))
    edge(draw, villager, player, "이름을 가르쳐 준 첫 스승", PURPLE, t=0.5, bend=30 * S, off=(-24 * S, -18 * S))
    edge(draw, kid, prof, "수첩을 찾던 박사를 본 목격자", AMBER, dashed=True, t=0.34, bend=-120 * S, off=(-46 * S, 50 * S))
    edge(draw, kid, player, "따라 배우고 나중에 물려받는 사이", AMBER, t=0.5, bend=14 * S, off=(-140 * S, 30 * S))
    edge(draw, folks, player, "각 동네의 스승 · 지역 이야기", PURPLE, t=0.5, bend=-28 * S, off=(30 * S, -18 * S))
    edge(draw, bo, prof, "첫 손님은 카메라 든 소년 (R4 회수)", ROSE, t=0.45, bend=-36 * S, off=(-30 * S, -60 * S))

    for n in (grandma, prof, player, shop, bo, elder, villager, kid, folks):
        n.draw(draw)

    # ── 세 개의 서사 축 노트 박스 (맨 위 가운데 빈자리) ──
    nx0, ny0, nx1, ny1 = 540 * S, 132 * S, 980 * S, 246 * S
    draw.rounded_rectangle([nx0, ny0, nx1, ny1], radius=12 * S, fill=(255, 251, 241),
                           outline=INK_SOFT, width=2 * S)
    center_text(draw, (nx0 + nx1) / 2, ny0 + 22 * S, "세 개의 서사 축 (복선 회수 구조)", F_NOTE_B)
    lines = [
        "1. 할머니와 박사의 이십 년 — 프롤로그에서 6장까지",
        "2. 화덕과 새 — 프롤로그에서 마지막 장까지",
        "3. 박사의 물러섬 — 2장에서 6장까지",
    ]
    for i, t in enumerate(lines):
        draw.text((nx0 + 22 * S, ny0 + (44 + i * 26) * S), t, font=F_NOTE, fill=INK)

    # ── 범례 ──
    ly = 1012 * S
    draw.rounded_rectangle([70 * S, ly - 26 * S, W - 70 * S, ly + 30 * S], radius=12 * S,
                           fill=PAPER2, outline=LINE_SOFT, width=2 * S)
    draw.line([(112 * S, ly), (186 * S, ly)], fill=INK, width=5 * S)
    draw.text((200 * S, ly - 11 * S), "직접적 인연 (함께 걷는 사람)", font=F_NOTE, fill=INK)
    for i in range(3):
        draw.line([(468 * S + i * 26 * S, ly), (468 * S + i * 26 * S + 15 * S, ly)], fill=INK_SOFT, width=3 * S)
    draw.text((568 * S, ly - 11 * S), "기억 · 목격 (곁에서 지켜 본 사람)", font=F_NOTE, fill=INK)
    draw.text((900 * S, ly - 11 * S),
              "관계선 위의 말은 게임 안 대사로 다시 살아난다 (Dialogues.kt 관계 대사 풀)",
              font=F_NOTE, fill=INK_SOFT)

    out = im.resize((W // S, H // S), Image.LANCZOS)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    out.save(OUT)
    print(f"+ {OUT.relative_to(ROOT)}  ({out.size[0]}x{out.size[1]})")


if __name__ == "__main__":
    build()
