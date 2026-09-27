#!/usr/bin/env python3
"""MapBuilder(Maps.kt) 의 새 길 설계 프로토타입.

Kotlin 이식본과 같은 순서/같은 규칙으로 동작하도록 작성한다.
"""
from __future__ import annotations

from pixelcanvas import Rnd

N, E, S, W = 'N', 'E', 'S', 'W'
OPP = {N: S, S: N, E: W, W: E}

# --- 지역 데이터 (Data.kt 사본) -------------------------------------------
REGIONS = {
    'seoul':     dict(w=40, h=30, water=set(), sand=set(), tree=0.06, rock=0.02, flower=0.07, city=True,  lake=False),
    'incheon':   dict(w=40, h=30, water={W}, sand={W}, tree=0.05, rock=0.02, flower=0.06, city=True,  lake=False),
    'chuncheon': dict(w=40, h=30, water=set(), sand=set(), tree=0.10, rock=0.02, flower=0.06, city=False, lake=True),
    'gangneung': dict(w=40, h=30, water={E}, sand={E}, tree=0.09, rock=0.02, flower=0.06, city=True,  lake=False),
    'sokcho':    dict(w=40, h=30, water={E}, sand={E}, tree=0.13, rock=0.09, flower=0.05, city=False, lake=False),
    'daejeon':   dict(w=40, h=30, water=set(), sand=set(), tree=0.07, rock=0.02, flower=0.06, city=True,  lake=False),
    'jeonju':    dict(w=40, h=30, water=set(), sand=set(), tree=0.08, rock=0.02, flower=0.09, city=True,  lake=False),
    'daegu':     dict(w=40, h=30, water=set(), sand=set(), tree=0.07, rock=0.05, flower=0.06, city=True,  lake=False),
    'gwangju':   dict(w=40, h=30, water=set(), sand=set(), tree=0.08, rock=0.02, flower=0.06, city=True,  lake=True),
    'ulsan':     dict(w=40, h=30, water={E}, sand={E}, tree=0.09, rock=0.06, flower=0.05, city=True,  lake=False),
    'busan':     dict(w=40, h=30, water={E, S}, sand={E, S}, tree=0.06, rock=0.05, flower=0.05, city=True, lake=False),
    'jeju':      dict(w=40, h=30, water={S, E, W}, sand={S, E, W}, tree=0.07, rock=0.12, flower=0.05, city=False, lake=False),
}
LINKS = [('seoul', 'incheon', W), ('seoul', 'chuncheon', N), ('seoul', 'daejeon', S),
         ('chuncheon', 'gangneung', E), ('gangneung', 'sokcho', N), ('gwangju', 'jeonju', E),
         ('jeonju', 'daejeon', E), ('daejeon', 'gwangju', S), ('daejeon', 'daegu', E),
         ('daegu', 'ulsan', E), ('ulsan', 'busan', S), ('busan', 'jeju', S)]


def exits(rid):
    out = {}
    for a, b, d in LINKS:
        if a == rid:
            out[d] = b
        elif b == rid:
            out[OPP[d]] = a
    return out


def java_hash(s: str) -> int:
    hsh = 0
    for ch in s:
        hsh = (hsh * 31 + ord(ch)) & 0xFFFFFFFF
    if hsh >= 1 << 31:
        hsh -= 1 << 32
    return hsh


# --- 타일 이름 -------------------------------------------------------------
GRASS, TALLGRASS, FLOWER, PATH, PLAZA, SAND, WATER, REED = 'GRASS', 'TALLGRASS', 'FLOWER', 'PATH', 'PLAZA', 'SAND', 'WATER', 'REED'
TREE, ROCK, MOUNTAIN = 'TREE', 'ROCK', 'MOUNTAIN'
BLDG_WALL, BLDG_WIN, BLDG_ROOF = 'BLDG_WALL', 'BLDG_WIN', 'BLDG_ROOF'
HOUSE_ROOF, HOUSE_WALL, HOUSE_WIN, HOUSE_DOOR = 'HOUSE_ROOF', 'HOUSE_WALL', 'HOUSE_WIN', 'HOUSE_DOOR'
TUNNEL, SIGN, BENCH, LAMP = 'TUNNEL', 'SIGN', 'BENCH', 'LAMP'

SOLID = {WATER, TREE, ROCK, MOUNTAIN, BLDG_WALL, BLDG_WIN, BLDG_ROOF,
         HOUSE_ROOF, HOUSE_WALL, HOUSE_WIN, SIGN, BENCH, LAMP}

PAVE_DIRT, PAVE_STONE = 1, 2

PLAZA_X0, PLAZA_X1 = 17, 25
PLAZA_Y0, PLAZA_Y1 = 12, 18
AVE_X = 19          # 남북 간선 기준 x (2칸 폭 -> 19,20)
AVE_Y = 15          # 동서 간선 기준 y (2칸 폭 -> 15,16)


class GameMap:
    def __init__(self, rid, w, h):
        self.rid = rid
        self.w = w
        self.h = h
        self.tile = [[GRASS] * w for _ in range(h)]
        self.base = [[GRASS] * w for _ in range(h)]
        self.pave = [[0] * w for _ in range(h)]
        self.deco = [[0] * w for _ in range(h)]
        self.reserved = [[False] * w for _ in range(h)]
        self.structure = [[False] * w for _ in range(h)]
        # (사람 배치는 Kotlin `placeCast` 담당 — 프로토타입에서는 만들지 않는다)
        self.has_house = False
        self.door = (-1, -1)

    def inb(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def solid(self, x, y):
        if not self.inb(x, y):
            return True
        return self.tile[y][x] in SOLID


def build(rid: str, home: str) -> GameMap:
    rd = REGIONS[rid]
    w, h = rd['w'], rd['h']
    m = GameMap(rid, w, h)
    rnd = Rnd(java_hash(rid))
    ex = exits(rid)

    def set_ground(x, y, t):
        m.tile[y][x] = t
        m.base[y][x] = t

    # 1. 바다/모래 가장자리 ---------------------------------------------
    def band(d, depth, t):
        if d == N:
            for y in range(depth):
                for x in range(w):
                    set_ground(x, y, t)
        elif d == S:
            for y in range(h - depth, h):
                for x in range(w):
                    set_ground(x, y, t)
        elif d == W:
            for x in range(depth):
                for y in range(h):
                    set_ground(x, y, t)
        else:
            for x in range(w - depth, w):
                for y in range(h):
                    set_ground(x, y, t)

    for d in rd['water']:
        band(d, 3, WATER)
    for d in rd['sand']:
        band(d, 2, SAND)

    # 2. 육지 가장자리 (산맥/수림) ---------------------------------------
    border = MOUNTAIN if rd['rock'] >= 0.08 else TREE
    for y in range(h):
        for x in range(w):
            if (x < 2 or y < 2 or x >= w - 2 or y >= h - 2) and m.tile[y][x] == GRASS:
                m.tile[y][x] = border
                m.base[y][x] = GRASS

    # 3. 호수/습지 (길보다 먼저 — 길이 호숫가를 피해 돌아가도록) -----------
    lake_shore = None
    if rd['lake']:
        cx, cy, rx, ry = 30, 22, 4, 3
        for y in range(cy - ry - 1, cy + ry + 2):
            for x in range(cx - rx - 1, cx + rx + 2):
                if not (2 <= x < w - 2 and 2 <= y < h - 2):
                    continue
                dx = (x - cx) / rx
                dy = (y - cy) / ry
                d2 = dx * dx + dy * dy
                if d2 <= 1.0:
                    set_ground(x, y, WATER)
                    m.reserved[y][x] = True
                elif d2 <= 1.6 and m.tile[y][x] == GRASS:
                    set_ground(x, y, REED)
                    m.reserved[y][x] = True
        lake_shore = (cx, cy - ry - 1)

    # 4. 도시 건물 (길보다 먼저 — 앞으로 샛길을 내기 위해) ------------------
    buildings = []
    if rd['city']:
        for (bx, by) in [(6, 5), (31, 5), (6, 24), (31, 24)]:
            ok = True
            for y in range(by, by + 3):
                for x in range(bx, bx + 3):
                    if not (2 <= x < w - 2 and 2 <= y < h - 2) or m.tile[y][x] != GRASS:
                        ok = False
            if not ok:
                continue
            for y in range(by, by + 3):
                for x in range(bx, bx + 3):
                    m.tile[y][x] = BLDG_ROOF if y == by else (BLDG_WIN if (x + y) % 2 == 0 else BLDG_WALL)
                    m.structure[y][x] = True
                    m.reserved[y][x] = True
            # 정문은 간선도로를 바라보는 쪽에
            buildings.append((bx + 1, by + 3) if by + 3 <= AVE_Y else (bx + 1, by - 1))

    # 5. 우리 집 -----------------------------------------------------------
    m.has_house = (home == rid)
    if m.has_house:
        for x in range(21, 26):
            for y in (8, 9):
                m.tile[y][x] = HOUSE_ROOF
                m.structure[y][x] = True
        row10 = [HOUSE_WALL, HOUSE_WIN, HOUSE_WALL, HOUSE_WIN, HOUSE_WALL]
        for i, x in enumerate(range(21, 26)):
            m.tile[10][x] = row10[i]
            m.structure[10][x] = True
        row11 = [HOUSE_WALL, HOUSE_WALL, HOUSE_DOOR, HOUSE_DOOR, HOUSE_WALL]
        for i, x in enumerate(range(21, 26)):
            m.tile[11][x] = row11[i]
            m.structure[11][x] = row11[i] != HOUSE_DOOR
        for y in range(8, 13):
            for x in range(20, 27):
                m.reserved[y][x] = True
        m.door = (23, 11)
        # 현관 앞은 포장 (문턱 -> 광장)
        for x in (23, 24):
            m.pave[11][x] = PAVE_STONE

    # 6. 광장 ---------------------------------------------------------------
    for y in range(PLAZA_Y0, PLAZA_Y1 + 1):
        for x in range(PLAZA_X0, PLAZA_X1 + 1):
            if m.structure[y][x]:
                continue
            m.tile[y][x] = PLAZA
            m.pave[y][x] = PAVE_STONE
            m.base[y][x] = GRASS
            m.reserved[y][x] = True
    # 네 모서리를 깎아 팔각으로 (모서리엔 가로등/그늘나무가 선다)
    plaza_corners = [(PLAZA_X0, PLAZA_Y0), (PLAZA_X1, PLAZA_Y0), (PLAZA_X0, PLAZA_Y1), (PLAZA_X1, PLAZA_Y1)]
    for (cx0, cy0) in plaza_corners:
        if m.structure[cy0][cx0]:
            continue
        m.tile[cy0][cx0] = GRASS
        m.base[cy0][cx0] = GRASS
        m.pave[cy0][cx0] = 0

    # 한가운데 문양 (2x2) + 빗물받이
    mx, my = (PLAZA_X0 + PLAZA_X1) // 2 - 1, (PLAZA_Y0 + PLAZA_Y1) // 2 - 1
    for dy in range(3):
        for dx in range(3):
            m.deco[my + dy][mx + dx] = 1 + dy * 3 + dx
    m.deco[PLAZA_Y0][PLAZA_X0 + 1] = 10
    m.deco[PLAZA_Y1][PLAZA_X1 - 1] = 10

    # 7. 간선 도로 ----------------------------------------------------------
    def can_pave(x, y):
        # 가장자리 링(산맥/바다)은 터널 진입로에서만 뚫는다
        if not m.inb(x, y) or m.structure[y][x]:
            return False
        return 2 <= x < w - 2 and 2 <= y < h - 2

    def stamp(x, y, size, mat=PAVE_DIRT):
        for yy in range(y, y + size):
            for xx in range(x, x + size):
                if not can_pave(xx, yy):
                    continue
                if m.pave[yy][xx] == PAVE_STONE:
                    continue
                if m.base[yy][xx] == WATER:
                    m.base[yy][xx] = SAND       # 물 위를 지나면 모래 둑길
                m.tile[yy][xx] = PATH if mat == PAVE_DIRT else PLAZA
                m.pave[yy][xx] = mat
                m.reserved[yy][xx] = True

    def walk(points, size, mat=PAVE_DIRT):
        """control point 를 1칸씩 이어가며 브러시를 찍는다 (연결 보장). 중심선을 돌려준다."""
        px, py = points[0]
        line = [(px, py)]
        stamp(px, py, size, mat)
        for (tx, ty) in points[1:]:
            while (px, py) != (tx, ty):
                if abs(tx - px) >= abs(ty - py) and px != tx:
                    px += 1 if tx > px else -1
                elif py != ty:
                    py += 1 if ty > py else -1
                else:
                    px += 1 if tx > px else -1
                stamp(px, py, size, mat)
                line.append((px, py))
        return line

    def stamp_round(cx, cy, rad):
        """컬드삭(회차 공간) — 모서리를 깎은 원형 광장."""
        for y in range(cy - rad, cy + rad + 1):
            for x in range(cx - rad, cx + rad + 1):
                dx, dy = x - cx, y - cy
                if dx * dx + dy * dy <= rad * rad + 1:
                    stamp(x, y, 1)

    def path_clear(points):
        """1칸 샛길 예정 경로가 전부 지나갈 수 있는지 미리 검사."""
        px, py = points[0]
        seq = [(px, py)]
        for (tx, ty) in points[1:]:
            while (px, py) != (tx, ty):
                if abs(tx - px) >= abs(ty - py) and px != tx:
                    px += 1 if tx > px else -1
                elif py != ty:
                    py += 1 if ty > py else -1
                else:
                    px += 1 if tx > px else -1
                seq.append((px, py))
        for (x, y) in seq:
            if not m.inb(x, y) or m.structure[y][x] or m.base[y][x] == WATER:
                return None
        return seq

    # 사행은 1칸까지만 — 2칸을 한 번에 꺾으면 길이 뭉개져 보인다
    bend_n = -(rnd.nextInt(2))          # 북쪽 구간 (-1..0, 집을 피해 서쪽으로만)
    bend_s = rnd.nextInt(2)             # 남쪽 구간 (0..1)
    bend_w = rnd.nextInt(3) - 1         # 서쪽 구간 (-1..1)
    bend_e = rnd.nextInt(3) - 1         # 동쪽 구간 (-1..1)

    # 남북 간선: 북쪽 끝 -> 광장 -> 남쪽 끝
    north_open = N in ex
    south_open = S in ex
    n_end = 2
    s_end = h - 3
    centerlines = []
    centerlines.append(('V', walk([(AVE_X, n_end), (AVE_X, 4), (AVE_X + bend_n, 6), (AVE_X + bend_n, 9), (AVE_X, 10), (AVE_X, 11)], 2)))
    centerlines.append(('V', walk([(AVE_X, PLAZA_Y1 - 1), (AVE_X, 20), (AVE_X + bend_s, 22), (AVE_X + bend_s, 24), (AVE_X, s_end - 2), (AVE_X, s_end)], 2)))
    # 동서 간선
    west_open = W in ex
    east_open = E in ex
    w_end = 2
    e_end = w - 3
    centerlines.append(('H', walk([(w_end, AVE_Y), (6, AVE_Y), (9, AVE_Y + bend_w), (12, AVE_Y + bend_w), (PLAZA_X0 - 3, AVE_Y), (PLAZA_X0 - 1, AVE_Y)], 2)))
    centerlines.append(('H', walk([(PLAZA_X1, AVE_Y), (28, AVE_Y), (31, AVE_Y + bend_e), (34, AVE_Y + bend_e), (e_end - 2, AVE_Y), (e_end, AVE_Y)], 2)))

    # 막다른 방향은 회차 공간(컬드삭)으로 마무리
    if not north_open:
        stamp_round(AVE_X, 3, 1)
    if not south_open:
        stamp_round(AVE_X, h - 4, 1)
    if not west_open:
        stamp_round(3, AVE_Y, 1)
    if not east_open:
        stamp_round(w - 4, AVE_Y, 1)

    # 광장 진입부 나팔목 (길이 넓어지며 광장으로 이어진다)
    for (fx, fy) in ((AVE_X - 1, PLAZA_Y0 - 1), (AVE_X + 2, PLAZA_Y0 - 1),
                     (AVE_X - 1, PLAZA_Y1 + 1), (AVE_X + 2, PLAZA_Y1 + 1),
                     (PLAZA_X0 - 1, AVE_Y - 1), (PLAZA_X0 - 1, AVE_Y + 2),
                     (PLAZA_X1 + 1, AVE_Y - 1), (PLAZA_X1 + 1, AVE_Y + 2)):
        stamp(fx, fy, 1)

    # 8. 터널 & 진입로 -------------------------------------------------------
    def open_tunnel(x, y):
        m.tile[y][x] = TUNNEL
        if m.base[y][x] == WATER:
            m.base[y][x] = SAND
        m.structure[y][x] = False
        m.reserved[y][x] = True

    def approach(x, y):
        m.tile[y][x] = PATH
        m.pave[y][x] = PAVE_DIRT
        if m.base[y][x] == WATER:
            m.base[y][x] = SAND     # 바다를 건너면 모래 둑길
        m.reserved[y][x] = True

    for d in ex:
        if d == N:
            for x in (19, 20):
                open_tunnel(x, 0)
                approach(x, 1)
                approach(x, 2)
        elif d == S:
            for x in (19, 20):
                open_tunnel(x, h - 1)
                approach(x, h - 2)
                approach(x, h - 3)
        elif d == W:
            for y in (15, 16):
                open_tunnel(0, y)
                approach(1, y)
                approach(2, y)
        else:
            for y in (15, 16):
                open_tunnel(w - 1, y)
                approach(w - 2, y)
                approach(w - 3, y)

    # 9. 샛길 ---------------------------------------------------------------
    def lane_to_avenue(front):
        fx, fy = front
        target_y = AVE_Y if fy < AVE_Y else AVE_Y + 1
        seq = path_clear([(fx, fy), (fx, target_y)])
        if seq is None:
            return False
        for (x, y) in seq:
            stamp(x, y, 1)
        return True

    for front in buildings:
        lane_to_avenue(front)

    # 호숫가 전망 데크
    if lake_shore is not None:
        lx, ly = lake_shore
        seq = path_clear([(lx, AVE_Y + 1), (lx, ly)])
        if seq is not None:
            for (x, y) in seq:
                stamp(x, y, 1)
            for yy in range(ly - 1, ly + 1):
                for xx in range(lx - 1, lx + 1):
                    if m.inb(xx, yy) and m.base[yy][xx] != WATER and not m.structure[yy][xx]:
                        stamp(xx, yy, 1, PAVE_STONE)
            if m.inb(lx - 1, ly - 1) and m.pave[ly - 1][lx - 1]:
                m.tile[ly - 1][lx - 1] = BENCH

    # 숲속 쉼터 (사진 찍기 좋은 자리)
    rest_x = 9 if rd['lake'] else 30
    rest_y = 22
    seq = path_clear([(rest_x, AVE_Y + 2), (rest_x, rest_y)])
    if seq is not None:
        for (x, y) in seq:
            stamp(x, y, 1)
        stamp(rest_x - 1, rest_y - 1, 3)
        if m.pave[rest_y - 1][rest_x - 1]:
            m.tile[rest_y - 1][rest_x - 1] = BENCH

    # 10. 이정표 --------------------------------------------------------------
    for d in ex:
        if d == N:
            sx, sy = 22, 1
        elif d == S:
            sx, sy = 22, h - 4
        elif d == W:
            sx, sy = 2, 13
        else:
            sx, sy = w - 3, 13
        m.tile[sy][sx] = SIGN
        if m.pave[sy][sx] == 0:
            m.base[sy][sx] = GRASS
        m.reserved[sy][sx] = True
        for (ax, ay) in ((sx + 1, sy), (sx - 1, sy), (sx, sy + 1), (sx, sy - 1)):
            if m.inb(ax, ay):
                m.reserved[ay][ax] = True

    # 11. 광장 시설 (벤치/가로등) ----------------------------------------------
    #     사람은 여기에 고정하지 않는다. Kotlin 쪽(Maps.kt)은 14번 `placeCast` 에서
    #     NpcRoster(지역별 고유 인물)의 자리(NpcSpot)를 지형이 다 자란 뒤에 검증·배치한다.
    #     한 사람은 한 장소에만 살고, 광장 한복판이 아니라 데크·갈대밭·갯벌·해변·숲에 선다.
    #     이 프로토타입은 길·광장 설계용이라 사람 배치는 모델링하지 않는다.
    def put_prop(x, y, t):
        if not m.inb(x, y) or m.structure[y][x]:
            return False
        if m.tile[y][x] in (TUNNEL, SIGN, HOUSE_DOOR) or m.base[y][x] == WATER:
            return False
        m.tile[y][x] = t
        m.reserved[y][x] = True
        return True

    for (bx, by) in ((18, 13), (24, 17)):
        put_prop(bx, by, BENCH)
    # 깎아낸 네 모서리 — 도시는 가로등, 시골은 그늘나무
    for (cx0, cy0) in plaza_corners:
        put_prop(cx0, cy0, LAMP if rd['city'] else TREE)

    # 12. 가로수/가로등 도열 + 길섶 꽃 -------------------------------------------
    def roadside(x, y):
        """길에 붙어 있고 비어 있는 갓길인지."""
        if not m.inb(x, y) or m.reserved[y][x] or m.structure[y][x]:
            return False
        if m.tile[y][x] != GRASS or m.pave[y][x] != 0:
            return False
        if PLAZA_X0 - 1 <= x <= PLAZA_X1 + 1 and PLAZA_Y0 - 1 <= y <= PLAZA_Y1 + 1:
            return False
        for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if m.inb(nx, ny) and m.pave[ny][nx] == PAVE_DIRT:
                return True
        return False

    def dress(axis, line):
        """간선 중심선을 따라 일정 간격으로 가로수/가로등을 세운다."""
        for i, (x, y) in enumerate(line):
            if axis == 'V':
                left, right = (x - 1, y), (x + 2, y)
            else:
                left, right = (x, y - 1), (x, y + 2)
            spot = left if (i // 6) % 2 == 0 else right
            if i % 6 == 3 and rd['city']:
                if roadside(*spot):
                    m.tile[spot[1]][spot[0]] = TREE
                    m.reserved[spot[1]][spot[0]] = True
            elif i % 13 == 7:
                other = right if spot is left else left
                if roadside(*other):
                    m.tile[other[1]][other[0]] = LAMP if rd['city'] else TREE
                    m.reserved[other[1]][other[0]] = True

    for (axis, line) in centerlines:
        dress(axis, line)

    for y in range(3, h - 3):
        for x in range(3, w - 3):
            if roadside(x, y) and rnd.nextDouble() < 0.22:
                m.tile[y][x] = FLOWER
                m.base[y][x] = FLOWER
                m.reserved[y][x] = True

    # 13. 자연물 -----------------------------------------------------------
    for _ in range(3):
        gx = 5 + rnd.nextInt(w - 10)
        gy = 5 + rnd.nextInt(h - 10)
        gr = 2 + rnd.nextInt(2)
        for y in range(gy - gr, gy + gr + 1):
            for x in range(gx - gr, gx + gr + 1):
                if not (2 <= x < w - 2 and 2 <= y < h - 2):
                    continue
                if m.reserved[y][x] or m.tile[y][x] != GRASS:
                    continue
                dx, dy = x - gx, y - gy
                if dx * dx + dy * dy <= gr * gr and rnd.nextFloat() < 0.8:
                    m.tile[y][x] = TREE
                    m.reserved[y][x] = True

    for y in range(2, h - 2):
        for x in range(2, w - 2):
            if m.reserved[y][x] or m.tile[y][x] != GRASS:
                continue
            r = rnd.nextDouble()
            if r < rd['tree']:
                m.tile[y][x] = TREE
            elif r < rd['tree'] + rd['flower']:
                m.tile[y][x] = FLOWER
                m.base[y][x] = FLOWER
            elif r < rd['tree'] + rd['flower'] + rd['rock']:
                m.tile[y][x] = ROCK

    for _ in range(4):
        bx = 4 + rnd.nextInt(w - 8)
        by = 4 + rnd.nextInt(h - 8)
        br = 1 + rnd.nextInt(2)
        for y in range(by - br, by + br + 1):
            for x in range(bx - br, bx + br + 1):
                if (2 <= x < w - 2 and 2 <= y < h - 2 and not m.reserved[y][x]
                        and m.tile[y][x] == GRASS and rnd.nextFloat() < 0.75):
                    m.tile[y][x] = TALLGRASS
                    m.base[y][x] = TALLGRASS
    return m
