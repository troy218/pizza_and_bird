#!/usr/bin/env python3
"""MapTest.kt 와 동일한 불변식을 파이썬 프로토타입에서 미리 검증한다."""
from __future__ import annotations

from collections import deque

import mapgen as M


def reach(m, sx, sy, tx, ty):
    if m.solid(sx, sy):
        return False
    seen = {(sx, sy)}
    q = deque([(sx, sy)])
    while q:
        x, y = q.popleft()
        if (x, y) == (tx, ty):
            return True
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if not m.inb(nx, ny) or (nx, ny) in seen:
                continue
            if m.solid(nx, ny) or m.tile[ny][nx] == M.TUNNEL:
                continue
            seen.add((nx, ny))
            q.append((nx, ny))
    return False


def solid_box(m, px, py):
    for (ax, ay) in ((px + 3, py + 10), (px + 13, py + 10), (px + 3, py + 16), (px + 13, py + 16)):
        if m.solid(int(ax // 16), int(ay // 16)):
            return True
    return False


def main():
    fails = []

    def check(cond, msg):
        if not cond:
            fails.append(msg)

    for home in M.REGIONS:
        for rid in M.REGIONS:
            m = M.build(rid, home)
            w, h = m.w, m.h
            ex = M.exits(rid)

            spawns = []
            if rid == home:
                spawns.append(('HOME', 376.0, 12.2 * 16))
            for d in ex:
                if d == M.N:
                    spawns.append(('N', 312.0, 3 * 16))
                elif d == M.S:
                    spawns.append(('S', 312.0, (h - 4) * 16))
                elif d == M.W:
                    spawns.append(('W', 3 * 16, 15.5 * 16))
                else:
                    spawns.append(('E', (w - 4) * 16, 15.5 * 16))
            for (nm, sx, sy) in spawns:
                check(not solid_box(m, sx, sy), f'스폰 막힘 {rid}/{home} {nm}')
                if nm != 'HOME':
                    tx, ty = int((sx + 8) // 16), int((sy + 13) // 16)
                    check(m.pave[ty][tx] != 0, f'터널 스폰이 길 위가 아님 {rid} {nm} ({tx},{ty})={m.tile[ty][tx]}')

            # 사람(NPC) 배치는 프로토타입에 없다 — Kotlin `MapBuilder.placeCast` 가 담당하고,
            # 자리 검증은 `tools/MapTest.kt`(캐스팅 무결성 + 서기/대화/도달/간격/박사·상점 위치)가 한다.

            for d in ex:
                tt = {M.N: [(19, 0), (20, 0)], M.S: [(19, h - 1), (20, h - 1)],
                      M.W: [(0, 15), (0, 16)], M.E: [(w - 1, 15), (w - 1, 16)]}[d]
                for (tx, ty) in tt:
                    check(m.tile[ty][tx] == M.TUNNEL, f'터널 타일 아님 {rid} {d} ({tx},{ty})={m.tile[ty][tx]}')
                st = {M.N: (19, 1), M.S: (19, h - 2), M.W: (1, 15), M.E: (w - 2, 15)}[d]
                check(reach(m, st[0], st[1], 20, 15), f'터널->광장 경로 없음 {rid} {d}')
                sp = {M.N: (22, 1), M.S: (22, h - 4), M.W: (2, 13), M.E: (w - 3, 13)}[d]
                check(m.tile[sp[1]][sp[0]] == M.SIGN, f'이정표 없음 {rid} {d} {m.tile[sp[1]][sp[0]]}')
                open_side = any(not m.solid(sp[0] + dx, sp[1] + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                check(open_side, f'이정표 막힘 {rid} {d}')

            if rid == home:
                check(m.has_house, f'집 없음 {rid}')
                dx, dy = m.door
                check(m.tile[dy][dx] == M.HOUSE_DOOR, f'집 문 오류 {rid}')
                check(m.tile[dy + 1][dx] == M.PLAZA, f'문 앞이 광장이 아님 {rid} = {m.tile[dy+1][dx]}')
                check(not m.solid(20, 12), f'세로길 상단 막힘 {rid}')
                check(reach(m, 19, 2, 20, 15), f'북쪽 터널->광장 {rid}')

            # 경계
            for x in range(w):
                check(m.solid(x, 0) or m.tile[0][x] in (M.TUNNEL, M.SAND), f'상단 경계 뚫림 {rid} ({x},0)={m.tile[0][x]}')
                check(m.solid(x, h - 1) or m.tile[h - 1][x] in (M.TUNNEL, M.SAND), f'하단 경계 뚫림 {rid} ({x},{h-1})={m.tile[h-1][x]}')
            for y in range(h):
                check(m.solid(0, y) or m.tile[y][0] in (M.TUNNEL, M.SAND), f'좌측 경계 뚫림 {rid} (0,{y})={m.tile[y][0]}')
                check(m.solid(w - 1, y) or m.tile[y][w - 1] in (M.TUNNEL, M.SAND), f'우측 경계 뚫림 {rid} ({w-1},{y})={m.tile[y][w-1]}')

            # --- 길 전용 불변식 ---
            for y in range(h):
                for x in range(w):
                    if m.pave[y][x] and m.structure[y][x]:
                        fails.append(f'길이 건물 위에 {rid} ({x},{y})')
            # 모든 길 타일이 하나로 이어져 있어야 한다
            road = [(x, y) for y in range(h) for x in range(w) if m.pave[y][x]]
            if road:
                seen = {road[0]}
                q = deque([road[0]])
                while q:
                    x, y = q.popleft()
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        nx, ny = x + dx, y + dy
                        if m.inb(nx, ny) and m.pave[ny][nx] and (nx, ny) not in seen:
                            seen.add((nx, ny))
                            q.append((nx, ny))
                check(len(seen) == len(road), f'길 조각이 끊어짐 {rid}/{home}: {len(road)-len(seen)}칸 고립')
            # 광장 한가운데는 늘 비어 있어야 (reach 목표)
            check(not m.solid(20, 15), f'광장 중앙 막힘 {rid}')

    uniq = sorted(set(fails))
    if uniq:
        print(f'FAIL {len(fails)}건 (고유 {len(uniq)})')
        for f in uniq[:40]:
            print('  -', f)
    else:
        print(f'OK — {len(M.REGIONS)**2}개 조합 전부 통과')
    return 1 if uniq else 0


if __name__ == '__main__':
    raise SystemExit(main())
