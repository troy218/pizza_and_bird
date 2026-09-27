#!/usr/bin/env python3
"""바위 소품 28종의 설계도 — 이름 · 크기급 · 암종.

`Assets.kt` 의 `T.ROCK` 변형 추가 순서와 1:1로 맞는다.
크기급(`size`)은 아트 자체가 갖고 있으며, `PEBBLE` 만 시야를 가리지 않는다
(발목만 넘는 자갈 뒤에 새가 숨을 이유가 없다).
"""
from __future__ import annotations

from rock_art import (  # noqa: F401
    band, crack, fade, fringe, lump, moss, raw, rock_body, rng, shade, shadow,
)

PEBBLE, LOW, TALL = 'PEBBLE', 'LOW', 'TALL'
SIZE_LABEL = {PEBBLE: '자갈(숨지 못함)', LOW: '낮은 돌', TALL: '큰 바위(시야 차단)'}

GRANITE = '화강암'
BASALT = '현무암'
SEDIMENT = '해식퇴적암'
LIMESTONE = '석회암'
SANDSTONE = '사암'
CONCRETE = '인공석재'
DEBRIS = '자연 쇳돌'

# 크기급별 표기 순서
CATALOG: list[tuple[str, str, str, str]] = []   # (id, 이름, size, kind)


def register(name, size, kind):
    def deco(fn):
        CATALOG.append((f"ROCK_{len(CATALOG)}", name, size, kind))
        fn.id = f"ROCK_{len(CATALOG) - 1}"
        return fn
    return deco


# ══════════════════════════════════════════════════════════════════════
#  화강암 — 설악·광릉·왕피의 비고지
# ══════════════════════════════════════════════════════════════════════
P_GRAN_D = 0xFF4C5158
P_GRAN_M = 0xFF8B9298
P_GRAN_L = 0xFFC3C7C7
P_GRAN_H = 0xFFEAE7DE


@register('화강암 노두', LOW, GRANITE)
def r00(cv, p, r):
    shadow(cv, p, 16, 28.5, 11.5, 3.2)
    body = [(3, 28), (5, 18), (8, 15), (14, 14), (19, 15), (22, 17), (27, 19),
            (29, 27), (19, 30), (8, 30)]
    clip = rock_body(cv, p, r, body, [P_GRAN_D, P_GRAN_M, P_GRAN_L, P_GRAN_H],
                     speck=8, top_band=0.12)
    # 각진 면 3개 — 평평한 면이 화강암 노두의 특징
    clip.line(cv, p, 9, 15.6, 18, 14.4, fade(P_GRAN_H, 170), 1.0)
    clip.line(cv, p, 18, 14.4, 22, 17.4, fade(P_GRAN_D, 150), 1.0)
    clip.line(cv, p, 22, 17.4, 19, 29.4, fade(P_GRAN_D, 110), 1.0)
    clip.oval(cv, p, 10, 21, 4, 4, fade(P_GRAN_L, 90))
    clip.oval(cv, p, 24, 24, 3.4, 3, fade(P_GRAN_D, 110))
    # 화강암 특유의 큰 결정 반점
    for x, y, sz in ((8, 19, 2), (15, 17, 1.6), (20, 22, 2.2), (12, 25, 1.4),
                     (25, 21, 1.6)):
        clip.rect(cv, p, x, y, sz, sz, fade(P_GRAN_H, 180))
    crack(clip, cv, p, 18, 15, 1.1, 2.4, fade(P_GRAN_D, 190), steps=4)
    crack(clip, cv, p, 24, 19, -1.3, -2.2, fade(P_GRAN_D, 150), steps=3)


@register('설악 암괴', TALL, GRANITE)
def r01(cv, p, r):
    shadow(cv, p, 16, 29.5, 12, 3.4)
    body = [(7, 30), (9, 12), (14, 3), (19, 9), (25, 14), (27, 30), (16, 31)]
    clip = rock_body(cv, p, r, body, [P_GRAN_D, P_GRAN_M, P_GRAN_L, P_GRAN_H], bands=6, speck=12)
    # 주 절리면 2개 — 각진 암괴의 특징
    band(clip, cv, p, 14, 6, 2.6, 22, fade(P_GRAN_D, 110))
    band(clip, cv, p, 15.4, 6, 1, 22, fade(P_GRAN_H, 80))
    band(clip, cv, p, 22, 16, 2.2, 13, fade(P_GRAN_D, 100))
    for x, y in ((11, 12), (17, 9), (21, 20), (12, 23)):
        clip.rect(cv, p, x, y, 1.6, 1.6, fade(P_GRAN_H, 150))
    clip.oval(cv, p, 12, 28.4, 5, 1.8, 0xFF5F824E)
    clip.oval(cv, p, 11, 27.8, 2.6, 1, 0xFF7FB15B)
    crack(clip, cv, p, 18, 3, 1.2, 2.6, fade(P_GRAN_D, 170), steps=5)


@register('노두 자갈', PEBBLE, GRANITE)
def r02(cv, p, r):
    shadow(cv, p, 16, 28.5, 9, 2.4)
    # 노두 아래 흘러내린 부순 사면 — 뒤쪽으로 갈수록 작아진다
    chips = [(3, 27, 2.6, 2), (6, 26, 3, 2.4), (10, 27, 2.8, 2.2), (14, 28, 2.4, 2),
             (18, 27, 3, 2.2), (22, 26, 2.6, 2), (26, 27, 2.4, 2),
             (7, 23, 3.4, 2.6), (12, 24, 3, 2.4), (17, 23, 3.6, 2.8), (22, 23, 3, 2.4),
             (10, 20, 2.8, 2.2), (15, 21, 2.4, 2), (19, 20, 2.6, 2),
             (14, 17.5, 2, 1.8), (17, 18, 1.8, 1.6)]
    for x, y, w, h in chips:
        body = lump(r, x + w / 2, y + h / 2, w, h, 6, 0.5)
        rock_body(cv, p, r, body, [P_GRAN_D, P_GRAN_M, P_GRAN_L, P_GRAN_H],
                  bands=2, speck=1, rim=False, top_band=0.32)


@register('이끼 화강암', LOW, GRANITE)
def r03(cv, p, r):
    shadow(cv, p, 16, 28.5, 10, 3)
    body = lump(r, 16, 22, 11, 6.4, 11, 0.26)
    clip = rock_body(cv, p, r, body, [P_GRAN_D, P_GRAN_M, P_GRAN_L, P_GRAN_H], speck=6)
    clip.oval(cv, p, 13, 17, 7, 3, 0xFF5F824E)
    clip.oval(cv, p, 11, 16, 4.4, 2, 0xFF7FB15B)
    clip.oval(cv, p, 20, 15.2, 3.8, 1.8, 0xFF6FAE57)
    clip.oval(cv, p, 19, 14.6, 2.2, 1, 0xFF8CC46C)
    for i in range(3):
        clip.rect(cv, p, 4 + i * 1.9, 24 - i * 1.8, 1.1, 4 + i * 1.6, 0xFF4F824A)
        clip.rect(cv, p, 3.4 + i * 1.9, 24 - i * 1.8, 1.8, 1, 0xFF7FB15B)


# ══════════════════════════════════════════════════════════════════════
#  현무암 — 제주·하도리·한라
# ══════════════════════════════════════════════════════════════════════
P_BAS_D = 0xFF1E2326
P_BAS_M = 0xFF454D50
P_BAS_L = 0xFF6E777A
P_BAS_H = 0xFF9BA3A2


@register('현무암 기둥', TALL, BASALT)
def r04(cv, p, r):
    shadow(cv, p, 16, 29.5, 12, 3.2)
    for x, top, w in ((4, 12, 6.2), (10, 4, 7.4), (18, 8, 6.4), (24, 15, 5.4)):
        body = [(x, 30), (x + 0.6, top + 2), (x + w / 2, top), (x + w - 0.6, top + 2.4), (x + w, 30)]
        clip = rock_body(cv, p, r, body, [P_BAS_D, P_BAS_M, P_BAS_L, P_BAS_H],
                         bands=4, speck=3, rim=True)
        band(clip, cv, p, x + w * 0.42, top + 2, 1, 26, fade(P_BAS_H, 90))
        band(clip, cv, p, x + w * 0.62, top + 3, 1, 25, fade(P_BAS_D, 150))
    # 기둥 사이 어두운 틈
    for x in (9.6, 17.2, 23.4):
        raw(cv, p, x, 8, 1.4, 22, fade(0xFF101416, 190))


@register('용암 성벽', TALL, BASALT)
def r05(cv, p, r):
    shadow(cv, p, 16, 30, 12, 3)
    body = [(2, 30), (3, 12), (7, 7), (13, 10), (19, 4), (25, 9), (29, 14), (30, 30)]
    clip = rock_body(cv, p, r, body, [P_BAS_D, P_BAS_M, P_BAS_L, P_BAS_H], bands=6, speck=6)
    # 기둥상 단열
    for x, y0 in ((6, 8), (11, 11), (17, 5), (23, 10), (27, 15)):
        band(clip, cv, p, x, y0, 1.3, 30 - y0, fade(0xFF14181A, 170))
        band(clip, cv, p, x + 1.4, y0 + 1, 1.1, 29 - y0, fade(P_BAS_L, 120))
        band(clip, cv, p, x + 2.6, y0 + 2, 0.8, 28 - y0, fade(P_BAS_H, 70))
    # 용암가루가 쌓인 밑동
    band(clip, cv, p, 3, 27, 26, 3, fade(0xFF2C3234, 200))


@register('현무암 자갈', PEBBLE, BASALT)
def r06(cv, p, r):
    shadow(cv, p, 16, 28.5, 9, 2.2)
    for x, y, w, h in ((4, 26, 3, 2.6), (8, 24, 3.6, 3), (12, 26, 3, 2.4),
                       (16, 24, 3.4, 3), (21, 25, 3, 2.6), (25, 23, 3.2, 2.8),
                       (7, 21, 3, 2.4), (12, 21.5, 3.4, 2.6), (17, 20, 3, 2.4),
                       (22, 21, 2.8, 2.2), (14, 18, 2.4, 2), (19, 18.5, 2.2, 1.8)):
        body = lump(r, x + w / 2, y + h / 2, w, h, 7, 0.45)
        clip = rock_body(cv, p, r, body, [P_BAS_D, P_BAS_M, P_BAS_L, P_BAS_H],
                         bands=2, speck=1, rim=False, top_band=0.36)
        # 기공(구멍) — 현무암 특징
        clip.rect(cv, p, x + w * 0.35, y + h * 0.32, 1, 1, fade(0xFF0C0F10, 200))
        clip.rect(cv, p, x + w * 0.58, y + h * 0.5, 1, 1, fade(0xFF0C0F10, 170))


@register('현무암 방패바위', LOW, BASALT)
def r07(cv, p, r):
    shadow(cv, p, 16, 28.5, 10.5, 3)
    body = lump(r, 16, 21, 11, 7, 12, 0.2)
    clip = rock_body(cv, p, r, body, [P_BAS_D, P_BAS_M, P_BAS_L, P_BAS_H], bands=5, speck=4)
    clip.oval(cv, p, 11, 18, 4, 2, fade(P_BAS_H, 80))
    for x, y in ((19, 17), (22, 21), (13, 25), (17, 23)):
        clip.rect(cv, p, x, y, 1, 1, fade(0xFF0C0F10, 170))


# ══════════════════════════════════════════════════════════════════════
#  해식 퇴적암 — 동해안(강릉·속초·울산·부산)
# ══════════════════════════════════════════════════════════════════════
P_SED_D = 0xFF4A555C
P_SED_M = 0xFF7C8892
P_SED_L = 0xFFA3AFB7
P_SED_H = 0xFFC9D2D6
P_SED_A = 0xFF5E8A4E      # 깃물


@register('동해 층리 바위', LOW, SEDIMENT)
def r08(cv, p, r):
    shadow(cv, p, 16, 28.5, 11, 3)
    body = [(3, 28), (5, 18), (11, 15), (21, 16), (28, 21), (30, 27), (20, 30), (8, 30)]
    clip = rock_body(cv, p, r, body, [P_SED_D, P_SED_M, P_SED_L, P_SED_H], bands=5, speck=4)
    for y, hh, col in ((19, 1.4, P_SED_D), (22.4, 1.2, P_SED_D), (25.4, 1.6, P_SED_D)):
        band(clip, cv, p, 4, y, 25, hh, fade(col, 120))
        band(clip, cv, p, 4, y + hh, 25, 0.9, fade(P_SED_H, 110))
    # 지층이 어긋난 단절
    clip.line(cv, p, 18, 17, 20, 28, fade(P_SED_D, 110), 1.0)
    fringe(clip, cv, p, 22, 24.4, 7, 2.4, fade(P_SED_A, 170))


@register('해식 절벽', TALL, SEDIMENT)
def r09(cv, p, r):
    shadow(cv, p, 16, 30, 12, 3)
    body = [(6, 30), (8, 12), (11, 5), (17, 3), (24, 6), (27, 14), (28, 30), (15, 31)]
    clip = rock_body(cv, p, r, body, [P_SED_D, P_SED_M, P_SED_L, P_SED_H], bands=6, speck=5)
    for y in (11, 15.5, 19.5):
        band(clip, cv, p, 6, y, 22, 1.2, fade(P_SED_D, 95))
        band(clip, cv, p, 6, y + 1.2, 22, 0.9, fade(P_SED_H, 80))
    # 파식 홈 — 바다 밑부분에 파먹은 오목한 띠
    band(clip, cv, p, 5, 23.4, 24, 3.2, fade(0xFF2B343A, 200))
    band(clip, cv, p, 5, 22.6, 24, 1, fade(P_SED_H, 140))
    band(clip, cv, p, 5, 26.6, 24, 3.4, fade(P_SED_D, 90))
    # 해면선 이끼
    fringe(clip, cv, p, 7, 26.4, 19, 2.2, fade(P_SED_A, 150))


@register('해안 사암 블록', LOW, SEDIMENT)
def r10(cv, p, r):
    shadow(cv, p, 16, 28.5, 9.5, 2.8)
    body = [(5, 28), (6, 18), (8, 15), (16, 16), (21, 14), (26, 18), (27, 27), (15, 30)]
    clip = rock_body(cv, p, r, body, [P_SED_D, P_SED_M, P_SED_L, P_SED_H], bands=4, speck=4)
    # 깨진 모서리 + 두 개의 지층
    clip.line(cv, p, 8, 15.4, 16, 16.4, fade(P_SED_H, 190), 1.0)
    clip.line(cv, p, 21, 14.4, 26, 18.4, fade(P_SED_H, 190), 1.0)
    band(clip, cv, p, 6, 21, 21, 1.4, fade(P_SED_D, 140))
    band(clip, cv, p, 6, 22.4, 21, 0.9, fade(P_SED_H, 120))
    band(clip, cv, p, 6, 25.4, 21, 1.2, fade(P_SED_D, 130))
    clip.line(cv, p, 12, 18, 10, 30, fade(P_SED_D, 150), 1.0)
    clip.oval(cv, p, 20, 23, 3, 2.6, fade(P_SED_D, 90))


@register('갯바위 선반', LOW, SEDIMENT)
def r11(cv, p, r):
    shadow(cv, p, 16, 27.5, 12, 2.6)
    body = [(2, 24), (5, 19), (13, 18), (22, 19), (30, 23), (30, 26), (16, 28), (3, 26)]
    clip = rock_body(cv, p, r, body, [P_SED_D, P_SED_M, P_SED_L, P_SED_H], bands=4, speck=5)
    band(clip, cv, p, 2, 22, 30, 1.4, fade(0xFF3A4A50, 200))
    fringe(clip, cv, p, 4, 24.4, 24, 2.4, fade(P_SED_A, 190))
    # 조개·따라
    for x, y in ((9, 24), (18, 25), (24, 23.6), (14, 22.4)):
        clip.rect(cv, p, x, y, 1.6, 1.2, 0xFFF0E4CB)
    clip.oval(cv, p, 16, 21, 8, 1.4, fade(P_SED_H, 90))


# ══════════════════════════════════════════════════════════════════════
#  석회암 — 전주·광주·영치면의 밝은 암반
# ══════════════════════════════════════════════════════════════════════
P_LIM_D = 0xFF6B6F63
P_LIM_M = 0xFFA6AC9C
P_LIM_L = 0xFFC9CCB7
P_LIM_H = 0xFFEDEBDA


@register('밝은 석회암', LOW, LIMESTONE)
def r12(cv, p, r):
    shadow(cv, p, 16, 28.5, 10, 3)
    body = [(5, 28), (6, 16), (12, 13), (23, 15), (28, 21), (28, 28), (16, 30)]
    clip = rock_body(cv, p, r, body, [P_LIM_D, P_LIM_M, P_LIM_L, P_LIM_H], bands=5, speck=6)
    # 용식 구멍
    for cx, cy, rad in ((11, 20, 1.9), (20, 18, 1.5), (23, 24, 1.7)):
        clip.oval(cv, p, cx, cy, rad, rad * 0.9, fade(0xFF4A5247, 200))
        clip.oval(cv, p, cx - 0.5, cy - 0.6, rad * 0.45, rad * 0.4, fade(P_LIM_H, 190))
    band(clip, cv, p, 6, 26, 22, 1.6, fade(P_LIM_D, 120))


@register('석회암 절벽', TALL, LIMESTONE)
def r13(cv, p, r):
    shadow(cv, p, 16, 30, 11.5, 3)
    body = [(7, 30), (8, 10), (13, 4), (22, 6), (27, 13), (27, 30), (15, 31)]
    clip = rock_body(cv, p, r, body, [P_LIM_D, P_LIM_M, P_LIM_L, P_LIM_H], bands=6, speck=8)
    for cx, cy, rad in ((13, 14, 2.2), (21, 19, 1.8), (16, 24, 1.6), (23, 9, 1.4)):
        clip.oval(cv, p, cx, cy, rad, rad, fade(0xFF515949, 180))
        clip.oval(cv, p, cx - 0.6, cy - 0.7, rad * 0.4, rad * 0.4, fade(P_LIM_H, 170))
    band(clip, cv, p, 8, 27, 19, 2, fade(P_LIM_D, 110))


@register('석회암 자갈', PEBBLE, LIMESTONE)
def r14(cv, p, r):
    shadow(cv, p, 16, 28.5, 7.5, 2)
    for x, y, w, h in ((5, 26, 3.2, 2.6), (9, 24, 3.6, 3), (14, 26, 3, 2.4),
                       (19, 24, 3.4, 2.8), (24, 25, 3, 2.4), (27, 27, 2.2, 1.8),
                       (7, 21, 3, 2.4), (12, 21.5, 3.4, 2.6), (17, 20, 3, 2.4),
                       (22, 21, 2.6, 2.2), (14, 18, 2.4, 2)):
        body = lump(r, x + w / 2, y + h / 2, w, h, 7, 0.4)
        clip = rock_body(cv, p, r, body, [P_LIM_D, P_LIM_M, P_LIM_L, P_LIM_H],
                         bands=2, speck=1, rim=False, top_band=0.36)
        clip.rect(cv, p, x + w * 0.3, y + h * 0.34, 1, 1, fade(0xFF4A5247, 200))


# ══════════════════════════════════════════════════════════════════════
#  사암 — 철원 평야·대구 분지·전주 논둑
# ══════════════════════════════════════════════════════════════════════
P_SAN_D = 0xFF6A5646
P_SAN_M = 0xFFA5825F
P_SAN_L = 0xFFD0AC7F
P_SAN_H = 0xFFEBD2A6


@register('사암 단층', LOW, SANDSTONE)
def r15(cv, p, r):
    shadow(cv, p, 16, 28.5, 10.5, 3)
    body = [(4, 28), (6, 15), (13, 12), (24, 13), (29, 19), (29, 28), (17, 30)]
    clip = rock_body(cv, p, r, body, [P_SAN_D, P_SAN_M, P_SAN_L, P_SAN_H], bands=5, speck=5)
    for y, hh in ((17, 1.6), (20, 1.4), (23.5, 1.6)):
        band(clip, cv, p, 5, y, 25, hh, fade(P_SAN_D, 140))
        band(clip, cv, p, 5, y + hh, 25, 0.9, fade(P_SAN_H, 110))
    crack(clip, cv, p, 20, 13, 1.4, 2.4, fade(P_SAN_D, 200), steps=4)


@register('사암 절벽', TALL, SANDSTONE)
def r16(cv, p, r):
    shadow(cv, p, 16, 30, 12, 3)
    body = [(6, 30), (7, 9), (14, 3), (23, 6), (28, 13), (28, 30), (15, 31)]
    clip = rock_body(cv, p, r, body, [P_SAN_D, P_SAN_M, P_SAN_L, P_SAN_H], bands=6, speck=7)
    for y in range(6, 29, 4):
        band(clip, cv, p, 6, y, 23, 1.6, fade(P_SAN_D, 130))
        band(clip, cv, p, 6, y + 1.6, 23, 1, fade(P_SAN_H, 100))
    # 사교층리 — 비스듬한 나뭇결
    for y in (13, 20):
        clip.line(cv, p, 7, y + 2.4, 28, y, fade(P_SAN_H, 85), 1.0)


@register('사암 조각돌', PEBBLE, SANDSTONE)
def r17(cv, p, r):
    shadow(cv, p, 16, 28.5, 7.5, 2)
    for x, y, w, h in ((4, 26, 3, 2.4), (8, 25, 3.2, 2.6), (12, 27, 2.8, 2.2),
                       (17, 26, 3, 2.4), (22, 26, 2.8, 2.2), (26, 27, 2.2, 1.8),
                       (7, 22, 3.2, 2.6), (12, 22, 3.4, 2.8), (17, 22, 3, 2.4),
                       (22, 22, 3.2, 2.6), (14, 19, 2.6, 2.2), (19, 19, 2.4, 2)):
        body = lump(r, x + w / 2, y + h / 2, w, h, 7, 0.42)
        clip = rock_body(cv, p, r, body, [P_SAN_D, P_SAN_M, P_SAN_L, P_SAN_H],
                         bands=2, speck=1, rim=False, top_band=0.34)
        band(clip, cv, p, x, y + h * 0.5, w, 0.9, fade(P_SAN_D, 110))


# ══════════════════════════════════════════════════════════════════════
#  인공 석재 — 항구 방파제·전주 한옥 돌담·도시 화단
# ══════════════════════════════════════════════════════════════════════
P_CON_D = 0xFF5F656B
P_CON_M = 0xFF8E959B
P_CON_L = 0xFFB8BFC3
P_CON_H = 0xFFD8DDDE


@register('방파제 블록', LOW, CONCRETE)
def r18(cv, p, r):
    shadow(cv, p, 16, 28.5, 12, 3)
    # 이중으로 쌓인 계기초 블록 — 마구모난 사각 기둥이 서서리 쌓인다
    blocks = [(3, 23, 8, 6), (11, 23.5, 7.5, 5.5), (18, 23, 8, 6), (25, 24, 5, 5),
              (5, 16.5, 7, 6.5), (12, 17, 7, 6), (19, 16.5, 7.5, 6.5),
              (9, 11, 6, 5.5), (16, 11.5, 6.5, 5), (22, 13, 5, 5)]
    for x, y, w, h in blocks:
        body = [(x, y + h), (x + 0.4, y + 0.8), (x + w * 0.45, y), (x + w, y + 1.2),
                (x + w, y + h - 0.8)]
        clip = rock_body(cv, p, r, body, [P_CON_D, P_CON_M, P_CON_L, P_CON_H],
                         bands=3, speck=2, top_band=0.3)
        band(clip, cv, p, x + 0.4, y + 0.8, w * 0.5, 1, fade(P_CON_H, 170))
        # 기둥 모서리 이음
        band(clip, cv, p, x + 0.4, y + h - 1.6, w - 0.8, 1, fade(P_CON_D, 150))


@register('옹벽 돌담', TALL, CONCRETE)
def r19(cv, p, r):
    shadow(cv, p, 16, 30, 11, 2.8)
    rows = [(6, 5, 20, 5), (4, 10, 24, 5), (5, 15, 22, 5), (4, 20, 25, 5), (7, 25, 19, 4.4)]
    for ri, (x, y, w, h) in enumerate(rows):
        n = max(2, w // 7)
        for i in range(n):
            bw = w / n
            bx = x + i * bw + (0.6 if ri % 2 else 0)
            body = [(bx, y + h), (bx + 0.5, y + 0.8), (bx + bw - 1, y),
                    (bx + bw - 0.4, y + h - 0.6)]
            clip = rock_body(cv, p, r, body, [P_CON_D, P_CON_M, P_CON_L, P_CON_H],
                             bands=3, speck=1)
            band(clip, cv, p, bx + 0.5, y + 0.8, bw - 2, 0.9, fade(P_CON_H, 130))
    # 이끼
    for i in range(5):
        raw(cv, p, 5 + i * 4.4, 20, 2.4, 1.6, fade(0xFF5F824E, 170))


@register('조경 화단석', PEBBLE, CONCRETE)
def r20(cv, p, r):
    shadow(cv, p, 16, 28, 7, 1.8)
    for x, y, w, h in ((7, 23, 8, 5), (16, 22, 9, 5.4), (11, 18, 6, 4.4), (18, 17, 6, 4.4)):
        body = [(x, y + h), (x + 0.5, y + 0.6), (x + w - 0.8, y), (x + w, y + h - 0.5)]
        clip = rock_body(cv, p, r, body, [P_CON_D, P_CON_M, P_CON_L, P_CON_H],
                         bands=2, speck=0)
    # 화단 가장자리 판석
    raw(cv, p, 4, 28, 24, 1.6, fade(0xFFB4B9B4, 220))


@register('호안석 가비온', LOW, CONCRETE)
def r21(cv, p, r):
    shadow(cv, p, 16, 28.5, 11, 3)
    body = [(4, 28), (5, 15), (12, 12), (24, 14), (28, 20), (28, 28), (16, 30)]
    clip = rock_body(cv, p, r, body, [0xFF3B4145, 0xFF6E757A, 0xFF949BA0, 0xFFBCC2C4],
                     bands=4, speck=10, speck_col=0xFF525A5F)
    # 철망 눈 — 옅게, 돌 사이로 비쳐 보인다
    wire = fade(0xFF2E363A, 205)
    for y in (15.6, 19.6, 23.6, 27.4):
        band(clip, cv, p, 4, y, 25, 1, wire)
    for x in range(4, 29, 3):
        band(clip, cv, p, x, 12, 1, 16, wire)
    # 눈 사이로 비치는 돌 알갱이
    for x, y, w, h in ((7, 17, 4, 3.4), (14, 17.4, 4.6, 3.2), (21, 16.6, 4, 3.4),
                       (9, 21, 4.4, 3.6), (17, 21.4, 4, 3.2), (24, 21, 3.4, 3)):
        body = lump(r, x + w / 2, y + h / 2, w, h, 7, 0.35)
        clip2 = rock_body(cv, p, r, body, [0xFF525A5F, 0xFF8B9399, 0xFFB2B9BC, 0xFFD2D8DA],
                          bands=2, speck=1, rim=False, top_band=0.34)
    # 철망이 덮인 윗면
    band(clip, cv, p, 6, 12.6, 20, 1.4, fade(0xFF8E959B, 190))


# ══════════════════════════════════════════════════════════════════════
#  자연 쇳돌 — 강가·갯벌·숲·해안
# ══════════════════════════════════════════════════════════════════════
P_COB_D = 0xFF525C63
P_COB_M = 0xFF818C92
P_COB_L = 0xFFA8B2B5
P_COB_H = 0xFFD2DADA


@register('강 자갈 더미', PEBBLE, DEBRIS)
def r22(cv, p, r):
    shadow(cv, p, 16, 28.5, 9, 2.4)
    for x, y, w, h in ((5, 24, 6, 4.4), (12, 22, 7, 5), (19, 24, 6, 4.4),
                       (24, 21, 5, 4), (9, 18, 5, 3.6), (16, 17, 6, 4)):
        body = lump(r, x + w / 2, y + h / 2, w, h, 9, 0.18)
        clip = rock_body(cv, p, r, body, [P_COB_D, P_COB_M, P_COB_L, P_COB_H],
                         bands=3, speck=1, rim=False)
        clip.rect(cv, p, x + w * 0.28, y + h * 0.24, w * 0.34, 1, fade(P_COB_H, 140))


@register('숲 이끼 바위', LOW, DEBRIS)
def r23(cv, p, r):
    shadow(cv, p, 16, 28.5, 10, 3)
    body = lump(r, 16, 21, 11, 7.4, 12, 0.2)
    clip = rock_body(cv, p, r, body, [P_COB_D, P_COB_M, P_COB_L, P_COB_H], bands=5, speck=4)
    clip.oval(cv, p, 14, 16, 7.4, 2.8, 0xFF5F824E)
    clip.oval(cv, p, 12, 15, 4.4, 1.8, 0xFF7FB15B)
    clip.oval(cv, p, 20, 14.4, 3.6, 1.6, 0xFF6FAE57)
    clip.oval(cv, p, 19, 13.8, 2.2, 1, 0xFF8CC46C)
    clip.oval(cv, p, 8, 19, 2.6, 1.4, 0xFF4E7040)
    for i in range(3):
        clip.rect(cv, p, 3.5 + i * 2, 23.4 - i * 1.7, 1.1, 4.4 + i * 1.5, 0xFF4F824A)
        clip.rect(cv, p, 2.9 + i * 2, 23.4 - i * 1.7, 1.8, 1, 0xFF7FB15B)


@register('갯벌 사구돌', PEBBLE, DEBRIS)
def r24(cv, p, r):
    shadow(cv, p, 16, 28.5, 8, 2.2)
    for x, y, rad in ((8, 25, 3.2), (14, 22, 4), (21, 24, 3.4), (25, 21, 2.6), (11, 19, 2.6)):
        body = lump(r, x, y, rad, rad * 0.86, 9, 0.3)
        clip = rock_body(cv, p, r, body, [P_SAN_D, P_SAN_M, P_SAN_L, P_SAN_H],
                         bands=3, speck=2, rim=False)
        clip.oval(cv, p, x, y, rad * 0.5, rad * 0.4, fade(0xFF8A6A4E, 150))


@register('조개 자갈', PEBBLE, DEBRIS)
def r25(cv, p, r):
    shadow(cv, p, 16, 28.5, 9, 2.2)
    shells = [(4, 27, 3.4, 2.4), (8, 25, 3.8, 2.8), (13, 27, 3.2, 2.2),
              (18, 26, 3.4, 2.4), (23, 27, 3, 2), (27, 25, 2.6, 2.2),
              (6, 22, 3.4, 2.6), (11, 22, 3.6, 2.8), (16, 21, 3.2, 2.4),
              (21, 22, 3.4, 2.6), (25, 21, 2.8, 2.2), (13, 18.5, 2.8, 2.2),
              (19, 18, 2.6, 2), (10, 19, 2.2, 1.8)]
    for i, (x, y, w, h) in enumerate(shells):
        warm = (i % 3 == 0)
        p.color = 0xFFFBF4E2 if warm else 0xFFF0E4CD
        cv.drawOval((x, y, x + w, y + h), p)
        p.color = 0xFFD6C3A0
        cv.drawOval((x + 0.3, y + h - 1.2, x + w - 0.3, y + h), p)
        # 조개 줄무늬
        for k in range(2):
            raw(cv, p, x + 0.6 + k * (w - 1.6) / 2, y + 0.4, 0.9, h - 1.8, 0xFFE3D2B2)


@register('대왕암 첨탑', TALL, DEBRIS)
def r26(cv, p, r):
    shadow(cv, p, 16, 30, 9, 2.8)
    body = [(10, 30), (11, 14), (14, 3), (19, 6), (22, 16), (22, 30), (15, 31)]
    clip = rock_body(cv, p, r, body, [P_SED_D, P_SED_M, P_SED_L, P_SED_H], bands=6, speck=5)
    for y in range(8, 28, 5):
        band(clip, cv, p, 10, y, 13, 1.4, fade(P_SED_D, 140))
    # 해식 홈 + 깃물
    band(clip, cv, p, 9, 24, 15, 3, fade(0xFF2F383E, 170))
    fringe(clip, cv, p, 10, 27, 11, 2, fade(P_SED_A, 170))
    # 파에 깎인 밑동
    band(clip, cv, p, 9, 28.4, 15, 2, fade(0xFF3E4A50, 200))


@register('갈대 곁 도라돌', PEBBLE, DEBRIS)
def r27(cv, p, r):
    shadow(cv, p, 16, 28, 8, 2.2)
    for x, y, w, h in ((9, 23, 8, 6), (18, 22, 7, 5.4), (13, 20, 6, 4.4)):
        body = lump(r, x + w / 2, y + h / 2, w, h, 10, 0.22)
        rock_body(cv, p, r, body, [P_COB_D, P_COB_M, P_COB_L, P_COB_H],
                  bands=3, speck=2, rim=False)
    # 곁의 갈대
    for x, hh in ((5, 9), (7.5, 12), (25, 10), (27, 8), (22, 6)):
        p.color = 0xFF8FAE5C
        cv.drawRect(x, 28 - hh, x + 1.2, 28, p)
        p.color = 0xFFC4C877
        cv.drawRect(x - 0.9, 28 - hh + 2.2, x + 2.1, 28 - hh + 3.4, p)
    for x, hh in ((6.2, 14), (26.2, 13), (24.5, 9)):
        p.color = 0xFF5F8249
        cv.drawRect(x, 28 - hh, x + 1, 28, p)


ROCKS = [fn for fn in (r00, r01, r02, r03, r04, r05, r06, r07, r08, r09, r10, r11, r12,
                       r13, r14, r15, r16, r17, r18, r19, r20, r21, r22, r23, r24, r25,
                       r26, r27)]
assert len(ROCKS) == 28 and len(CATALOG) == 28, (len(ROCKS), len(CATALOG))


def render_rock(look: int, seed: int = 9017) -> "object":
    from pixelcanvas import Bitmap, Paint
    bmp = Bitmap(32, 32)
    ROCKS[look](bmp, Paint(), rng(seed + look * 1013))
    return bmp
