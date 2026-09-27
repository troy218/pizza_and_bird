#!/usr/bin/env python3
"""Assets.kt 의 타일 아트를 미리보기용으로 옮겨 놓은 자동 생성 파일.

_gen_tiles_legacy.py 로 만든다. 길(PATH/PLAZA)은 roads.py 가 대신 그리므로
여기 있는 PATH/PLAZA 는 "개선 전" 비교용으로만 쓴다.
"""
from pixelcanvas import Bitmap, Canvas, Paint, Path, Rnd, shade

ART = {}
_cur_name = None
_cur_list = []
_seed = [9017]


def tile_painter(fn):
    b = Bitmap(32, 32)
    fn(b, Paint(), Rnd(_seed[0]))
    _seed[0] += 1013
    return b


def add(b):
    _cur_list.append(b)


def begin(name):
    flush()
    globals()['_cur_name'] = name


def flush():
    global _cur_list
    if _cur_name and _cur_list:
        ART[_cur_name] = list(_cur_list)
    _cur_list = []


def prop_shadow(c, p, cx, cy, rx, ry):
    p.color = 0x40202C20
    c.drawOval((cx - rx, cy - ry, cx + rx, cy + ry), p)


def fill(c, p, color):
    p.color = color
    c.drawRect(0, 0, 32, 32, p)


def specks(c, p, r, color, n):
    p.color = color
    for _ in range(n):
        x = r.nextInt(31)
        y = r.nextInt(31)
        c.drawRect(x, y, x + 2, y + 1.5, p)


def grassBase(c, p, r, base=0xFF96D07A):
    fill(c, p, base)
    specks(c, p, r, shade(base, 0.9), 7)
    specks(c, p, r, shade(base, 1.08), 6)
    p.color = shade(base, 0.82)
    for _ in range(5):
        x = r.nextInt(29)
        y = r.nextInt(24)
        c.drawRect(x, y, x + 1.4, y + 3.5, p)
    p.color = shade(base, 1.14)
    for _ in range(3):
        x = r.nextInt(29)
        y = r.nextInt(24)
        c.drawRect(x, y, x + 1.4, y + 2.8, p)


def build():
    # GRASS (4종 변형)
    begin('GRASS')
    for i in range(4):
        def _tp(c, p, r, **kw):
            globals().update(kw)
            grassBase(c, p, r)
            if i == 1:
                p.color = 0xFF6FAE57
                c.drawRect(5, 6, 9, 7.2, p)
                c.drawRect(7, 5, 8.2, 9, p)
                c.drawRect(22, 20, 26, 21.2, p)
                c.drawRect(24, 19, 25.2, 23, p)
            if i == 2:
                p.color = 0xFFA8B0A0
                c.drawRect(14, 18, 17, 20, p)
                c.drawRect(14.8, 17.4, 16.2, 20.6, p)
            if i == 3:
                p.color = 0xFFFDF6E8
                c.drawRect(20, 9, 22, 11, p)
                c.drawRect(19.4, 9.6, 22.6, 10.4, p)
        add(tile_painter(_tp))
    # TALLGRASS (2종)
    begin('TALLGRASS')
    for i in range(2):
        def _tp(c, p, r, **kw):
            globals().update(kw)
            grassBase(c, p, r, 0xFF8CC46C)
            p.color = 0xFF6FAE57
            for k in range(6 + i):
                x = 1 + r.nextInt(28)
                h = 9 + r.nextInt(9)
                c.drawRect(x, (32 - h), x + 2, 32, p)
            p.color = 0xFF8CC46C
            for _rep in range(4):
                x = 1 + r.nextInt(28)
                h = 7 + r.nextInt(7)
                c.drawRect(x, (32 - h), x + 1.6, 32, p)
            p.color = 0xFF5D8A4A
            c.drawRect(6, 8, 7.4, 18, p)
            c.drawRect(22, 6, 23.4, 20, p)
        add(tile_painter(_tp))
    # FLOWER (3색)
    begin('FLOWER')
    flowerCols = [0xFFF2A3B3, 0xFFF2D06B, 0xFFFDFDF8]
    for i in range(3):
        def _tp(c, p, r, **kw):
            globals().update(kw)
            grassBase(c, p, r)
            for _rep in range(4):
                x = 3 + r.nextInt(23)
                y = 3 + r.nextInt(22)
                p.color = 0xFF5D8A4A
                c.drawRect((x + 1.6), (y + 2.4), (x + 2.6), (y + 5.2), p)
                col = flowerCols[(i + r.nextInt(3)) % 3]
                p.color = col
                c.drawRect(x, y, x + 4.2, y + 2.6, p)
                c.drawRect(x + 1, y - 1, x + 3.2, y + 3.6, p)
                p.color = 0xFFF7CE5B
                c.drawRect(x + 1.4, y + 0.4, x + 2.8, y + 1.8, p)
        add(tile_painter(_tp))
    # PATH (길) — 실제 화면에서는 Roads.kt 오토타일이 그린다.
    # 여기 있는 것은 "사방이 모두 길" 인 안쪽 조각 (미니맵/예비용).
    begin('PATH')
    # PLAZA (석재 포장)
    begin('PLAZA')
    # SAND (3종)
    begin('SAND')
    for i in range(3):
        def _tp(c, p, r, **kw):
            globals().update(kw)
            fill(c, p, 0xFFF2E1B0)
            specks(c, p, r, 0xFFE4CF96, 9)
            specks(c, p, r, 0xFFF8ECC8, 7)
            if i == 1:
                p.color = 0xFFFDF6E8
                c.drawRect(13, 17, 17, 19.4, p)
                p.color = 0xFFE8B14E
                c.drawRect(14.4, 18, 15.6, 19, p)
            if i == 2:
                p.color = 0xFFE4CF96
                c.drawRect(3, 12, 12, 13.2, p)
                c.drawRect(18, 24, 28, 25.2, p)
        add(tile_painter(_tp))
    # WATER (4프레임 애니메이션)
    begin('WATER')
    for f in range(4):
        def _tp(c, p, r, **kw):
            globals().update(kw)
            fill(c, p, 0xFF4FA8D8)
            p.color = 0xFF63B8E4
            c.drawRect(0, 3, 32, 6, p)
            c.drawRect(0, 14, 32, 16, p)
            c.drawRect(0, 25, 32, 27, p)
            off = f * 3
            p.color = 0xFF93D4EF
            c.drawRect((2 + off) % 28, 4.4, (2 + off) % 28 + 7, 5.8, p)
            c.drawRect((18 + off) % 26, 15, (18 + off) % 26 + 8, 16.4, p)
            c.drawRect((8 + off) % 26, 25.6, (8 + off) % 26 + 7, 27, p)
            p.color = 0xFFC9ECF8
            c.drawRect((3 + off) % 28, 4.6, (3 + off) % 28 + 2.4, 5.6, p)
            c.drawRect((19 + off) % 26, 15.2, (19 + off) % 26 + 2.4, 16.2, p)
        add(tile_painter(_tp))
    # REED (2종)
    begin('REED')
    for i in range(2):
        def _tp(c, p, r, **kw):
            globals().update(kw)
            grassBase(c, p, r, 0xFF8CC46C)
            xs = [5, 12, 20, 27] if i == 0 else [8, 15, 24]
            for x in xs:
                p.color = 0xFF7D9C4F
                c.drawRect(x, ((4 if x % 2 == 0 else 1)), x + 2.2, 32, p)
                p.color = 0xFFB0793F
                ty = (4 if x % 2 == 0 else 1)
                c.drawRect((x - 0.8), ty, (x + 3), ty + 7, p)
                p.color = 0xFF8A5A33
                c.drawRect(x, ty + 1.6, x + 1.2, ty + 5, p)
            p.color = 0xFF6FAE57
            c.drawRect(10, 22, 14, 23.2, p)
        add(tile_painter(_tp))
    # TREE (2종: 활엽수 + 침엽수)
    begin('TREE')
    def _tp(c, p, r, **kw):
        globals().update(kw)
        prop_shadow(c, p, 16, 29.5, 9.5, 3.4)
        p.color = 0xFF5D3A20
        c.drawRect(14, 18, 18, 31, p)
        p.color = 0xFF7A4E2B
        c.drawRect(14.6, 18, 16.2, 31, p)
        p.color = 0xFF33602F
        c.drawCircle(16, 12, 11.4, p)
        p.color = 0xFF3F7D46
        c.drawCircle(16, 11, 10.2, p)
        p.color = 0xFF4F9E57
        c.drawCircle(14, 8.6, 6.4, p)
        c.drawCircle(21, 12.6, 4.6, p)
        p.color = 0xFF6BBA72
        c.drawCircle(12.4, 7, 3.4, p)
        p.color = 0xFF2C5429
        c.drawRect(9, 17.4, 24, 18.6, p)
    add(tile_painter(_tp))
    def _tp(c, p, r, **kw):
        globals().update(kw)
        prop_shadow(c, p, 16, 29.5, 9.5, 3.4)
        p.color = 0xFF5D3A20
        c.drawRect(14.6, 24, 17.4, 31, p)
        path = Path()
        p.color = 0xFF2C5A34
        path.moveTo(16, 0)
        path.lineTo(25, 13)
        path.lineTo(7, 13)
        path.close()
        c.drawPath(path, p)
        p.color = 0xFF3A7044
        path.reset()
        path.moveTo(16, 7)
        path.lineTo(27, 21)
        path.lineTo(5, 21)
        path.close()
        c.drawPath(path, p)
        p.color = 0xFF2C5A34
        path.reset()
        path.moveTo(16, 14)
        path.lineTo(29, 28)
        path.lineTo(3, 28)
        path.close()
        c.drawPath(path, p)
        p.color = 0xFF4F9E57
        c.drawRect(12.4, 9, 15, 10.4, p)
        c.drawRect(8, 22, 10.6, 23.4, p)
    add(tile_painter(_tp))
    # ROCK (2종)
    begin('ROCK')
    for i in range(2):
        def _tp(c, p, r, **kw):
            globals().update(kw)
            prop_shadow(c, p, 16, 26.5, 9, 3)
            if i == 0:
                p.color = 0xFF5A626C
                c.drawRect(6, 10, 26, 28, p)
                p.color = 0xFF7C8590
                c.drawRect(7.4, 8.6, 24.6, 26, p)
                p.color = 0xFF9AA3AD
                c.drawRect(9, 10, 16, 15, p)
                p.color = 0xFFB5BDC6
                c.drawRect(10.4, 11, 13, 13, p)
                p.color = 0xFF4A5158
                c.drawRect(9, 24, 24, 26, p)
            else:
                p.color = 0xFF7C8590
                c.drawRect(10, 16, 22, 26, p)
                p.color = 0xFF9AA3AD
                c.drawRect(11, 14.6, 20.6, 24, p)
                p.color = 0xFFB5BDC6
                c.drawRect(12.4, 15.6, 15, 18, p)
                p.color = 0xFF6FAE57
                c.drawRect(10, 24, 13, 26, p)
        add(tile_painter(_tp))
    # MOUNTAIN (2종)
    begin('MOUNTAIN')
    for i in range(2):
        def _tp(c, p, r, **kw):
            globals().update(kw)
            fill(c, p, 0xFF77848F)
            p.color = 0xFF8D9AA8
            c.drawRect(0, 0, 32, 6, p)
            c.drawRect(0, 12, 12, 20, p)
            c.drawRect(20, 8, 32, 18, p)
            c.drawRect(0, 24, 8, 32, p)
            p.color = 0xFFA5B2BD
            c.drawRect(4, 2, 10, 4, p)
            c.drawRect(22, 10, 28, 12, p)
            p.color = 0xFF5D6772
            c.drawRect(0, 6, 32, 8, p)
            c.drawRect(0, 20, 32, 22, p)
            c.drawRect(0, 30, 32, 32, p)
            if i == 1:
                p.color = 0xFFE8EEF2
                c.drawRect(2, 9, 8, 11, p)
                c.drawRect(24, 23, 29, 25, p)
        add(tile_painter(_tp))
    # BLDG_WALL
    begin('BLDG_WALL')
    def _tp(c, p, r, **kw):
        globals().update(kw)
        fill(c, p, 0xFFE9E2D3)
        p.color = 0xFFD8CFBA
        c.drawRect(0, 8, 32, 9.6, p)
        c.drawRect(0, 20, 32, 21.6, p)
        c.drawRect(0, 30, 32, 32, p)
        p.color = 0xFFF4EFDF
        c.drawRect(0, 0, 32, 1.4, p)
        p.color = 0xFFCCC2AA
        c.drawRect(15, 0, 16.4, 8, p)
        c.drawRect(15, 9.6, 16.4, 20, p)
    add(tile_painter(_tp))
    # BLDG_WIN (2종)
    begin('BLDG_WIN')
    curtains = [0xFFF2D06B, 0xFFC3A3E8]
    for i in range(2):
        def _tp(c, p, r, **kw):
            globals().update(kw)
            fill(c, p, 0xFFE9E2D3)
            p.color = 0xFFD8CFBA
            c.drawRect(0, 30, 32, 32, p)
            p.color = 0xFFC9BFA8
            c.drawRect(6, 6, 26, 24, p)
            p.color = 0xFF7FB6D9
            c.drawRect(7.4, 7.4, 24.6, 22.6, p)
            p.color = 0xFFB9DDF0
            c.drawRect(7.4, 7.4, 13, 13, p)
            p.color = 0xFFC9BFA8
            c.drawRect(15.4, 7.4, 16.6, 22.6, p)
            c.drawRect(7.4, 14.6, 24.6, 15.8, p)
            p.color = curtains[i]
            c.drawRect(7.4, 7.4, 10, 22.6, p)
            c.drawRect(22, 7.4, 24.6, 22.6, p)
        add(tile_painter(_tp))
    # BLDG_ROOF
    begin('BLDG_ROOF')
    def _tp(c, p, r, **kw):
        globals().update(kw)
        fill(c, p, 0xFFC96A4D)
        p.color = 0xFFB2583F
        c.drawRect(0, 8, 32, 9.6, p)
        c.drawRect(0, 20, 32, 21.6, p)
        c.drawRect(8, 0, 9.6, 8, p)
        c.drawRect(24, 9.6, 25.6, 20, p)
        c.drawRect(8, 21.6, 9.6, 32, p)
        p.color = 0xFFDB8266
        c.drawRect(0, 0, 32, 1.6, p)
        c.drawRect(4, 11, 12, 12.4, p)
        c.drawRect(18, 25, 26, 26.4, p)
    add(tile_painter(_tp))
    # HOUSE_ROOF
    begin('HOUSE_ROOF')
    def _tp(c, p, r, **kw):
        globals().update(kw)
        fill(c, p, 0xFFD4694A)
        p.color = 0xFFB55338
        c.drawRect(0, 6, 32, 7.6, p)
        c.drawRect(0, 16, 32, 17.6, p)
        c.drawRect(0, 26, 32, 27.6, p)
        c.drawRect(10, 0, 11.4, 6, p)
        c.drawRect(10, 17.6, 11.4, 26, p)
        p.color = 0xFFE08A67
        c.drawRect(0, 0, 32, 1.4, p)
        c.drawRect(4, 10, 14, 11.2, p)
        c.drawRect(18, 20, 28, 21.2, p)
    add(tile_painter(_tp))
    # HOUSE_WALL
    begin('HOUSE_WALL')
    def _tp(c, p, r, **kw):
        globals().update(kw)
        fill(c, p, 0xFFF6E7C6)
        p.color = 0xFFE0C9A2
        c.drawRect(0, 14, 32, 16, p)
        p.color = 0xFFC9A87B
        c.drawRect(0, 0, 2, 32, p)
        c.drawRect(30, 0, 32, 32, p)
        c.drawRect(0, 28, 32, 32, p)
        p.color = 0xFFD9B98C
        c.drawRect(13, 0, 15, 14, p)
    add(tile_painter(_tp))
    # HOUSE_WIN (꽃상자 있는 창문)
    begin('HOUSE_WIN')
    def _tp(c, p, r, **kw):
        globals().update(kw)
        fill(c, p, 0xFFF6E7C6)
        p.color = 0xFFC9A87B
        c.drawRect(6, 5, 26, 22, p)
        p.color = 0xFF9FD0E8
        c.drawRect(7.4, 6.4, 24.6, 20.6, p)
        p.color = 0xFFC3E4F2
        c.drawRect(7.4, 6.4, 14, 12, p)
        p.color = 0xFFC9A87B
        c.drawRect(15.4, 6.4, 16.6, 20.6, p)
        c.drawRect(7.4, 12.6, 24.6, 13.8, p)
        # 꽃상자
        p.color = 0xFF8A5A33
        c.drawRect(6, 22, 26, 27, p)
        p.color = 0xFFA87B4F
        c.drawRect(7, 23, 25, 26, p)
        p.color = 0xFFF2A3B3
        c.drawRect(8, 20.4, 10.4, 22.4, p)
        p.color = 0xFFF2D06B
        c.drawRect(14, 20, 16.4, 22.4, p)
        p.color = 0xFFC3A3E8
        c.drawRect(21, 20.6, 23.4, 22.4, p)
    add(tile_painter(_tp))
    # HOUSE_DOOR
    begin('HOUSE_DOOR')
    def _tp(c, p, r, **kw):
        globals().update(kw)
        fill(c, p, 0xFFF6E7C6)
        p.color = 0xFFC9A87B
        c.drawRect(2, 3, 30, 32, p)
        p.color = 0xFF8A5A33
        c.drawRect(3.4, 4.4, 28.6, 32, p)
        p.color = 0xFF7A4A2B
        c.drawRect(5, 8, 27, 10, p)
        c.drawRect(5, 16, 27, 18, p)
        p.color = 0xFF9FD0E8
        c.drawRect(11, 19.6, 21, 26, p)
        p.color = 0xFFC3E4F2
        c.drawRect(11, 19.6, 15, 23, p)
        p.color = 0xFFF2D06B
        c.drawRect(23, 14, 26, 16.6, p)
        p.color = 0xFFE0C9A2
        c.drawRect(0, 28, 32, 32, p)
    add(tile_painter(_tp))
    # TUNNEL
    begin('TUNNEL')
    def _tp(c, p, r, **kw):
        globals().update(kw)
        fill(c, p, 0xFF77848F)
        p.color = 0xFF8D9AA8
        c.drawRect(0, 0, 32, 8, p)
        p.color = 0xFFA5B2BD
        c.drawRect(4, 1, 12, 3, p)
        p.color = 0xFF6B4F35
        c.drawRect(4, 4, 28, 32, p)
        p.color = 0xFF5A3F28
        c.drawRect(2, 8, 6, 32, p)
        c.drawRect(26, 8, 30, 32, p)
        p.color = 0xFF191921
        c.drawRect(8, 8, 24, 32, p)
        p.color = 0xFF2E2E3A
        c.drawRect(10, 10, 22, 32, p)
        # 입구 등
        p.color = 0xFFF2D06B
        c.drawRect(14, 2, 18, 6, p)
        p.color = 0xFFF7E9A8
        c.drawRect(15, 3, 17, 5, p)
        # 노면
        p.color = 0xFF8A8074
        c.drawRect(8, 28, 24, 32, p)
    add(tile_painter(_tp))
    # FLOOR (2종)
    begin('FLOOR')
    for i in range(2):
        def _tp(c, p, r, **kw):
            globals().update(kw)
            base = (0xFFCDA775 if i == 0 else 0xFFC49E6C)
            fill(c, p, base)
            p.color = 0xFFB98F5E
            c.drawRect(0, 10, 32, 11.6, p)
            c.drawRect(0, 21, 32, 22.6, p)
            seam = (10 if i == 0 else 22)
            c.drawRect(seam, 0, seam + 1.4, 10, p)
            c.drawRect(32 - seam, 11.6, 33.4 - seam, 21, p)
            p.color = 0xFFDBB684
            c.drawRect(0, 0, 32, 1.2, p)
            c.drawRect(0, 11.6, 32, 12.6, p)
            c.drawRect(0, 22.6, 32, 23.6, p)
            p.color = 0xFFA87B4F
            c.drawRect(3, 4, 4, 5, p)
            c.drawRect(27, 25, 28, 26, p)
        add(tile_painter(_tp))
    # WALL_IN
    begin('WALL_IN')
    def _tp(c, p, r, **kw):
        globals().update(kw)
        fill(c, p, 0xFFF2E3C2)
        p.color = 0xFFE8D5AE
        c.drawRect(5, 4, 7, 26, p)
        c.drawRect(14, 4, 16, 26, p)
        c.drawRect(23, 4, 25, 26, p)
        p.color = 0xFFC9A87B
        c.drawRect(0, 0, 32, 3, p)
        c.drawRect(0, 26, 32, 27.4, p)
        p.color = 0xFFE0C9A2
        c.drawRect(0, 27.4, 32, 32, p)
    add(tile_painter(_tp))
    # WALL_WIN
    begin('WALL_WIN')
    def _tp(c, p, r, **kw):
        globals().update(kw)
        fill(c, p, 0xFFF2E3C2)
        p.color = 0xFFC9A87B
        c.drawRect(0, 0, 32, 3, p)
        p.color = 0xFFC9A87B
        c.drawRect(6, 6, 26, 23, p)
        p.color = 0xFFA8D8E8
        c.drawRect(7.4, 7.4, 24.6, 21.6, p)
        p.color = 0xFFC9E8F2
        c.drawRect(7.4, 7.4, 15, 13, p)
        p.color = 0xFF8CC46C
        c.drawRect(7.4, 16, 12, 19, p)
        c.drawRect(18, 13, 20, 18, p)
        p.color = 0xFFC9A87B
        c.drawRect(15.4, 7.4, 16.6, 21.6, p)
        c.drawRect(7.4, 13.6, 24.6, 14.8, p)
        p.color = 0xFFE0C9A2
        c.drawRect(0, 23, 32, 24, p)
        c.drawRect(0, 27.4, 32, 32, p)
    add(tile_painter(_tp))
    # OVEN (2프레임 — 불꽃 애니메이션)
    begin('OVEN')
    for f in range(2):
        def _tp(c, p, r, **kw):
            globals().update(kw)
            fill(c, p, 0xFF8F8F99)
            p.color = 0xFF7A7A85
            c.drawRect(0, 0, 32, 2.4, p)
            c.drawRect(0, 14, 32, 16, p)
            c.drawRect(15.4, 0, 17, 14, p)
            c.drawRect(6, 16, 8, 32, p)
            c.drawRect(24, 16, 26, 32, p)
            p.color = 0xFFA8A8B2
            c.drawRect(0, 2.4, 32, 3.6, p)
            # 아치 화구
            p.color = 0xFF23232B
            c.drawCircle(16, 21, 9.6, p)
            p.color = 0xFF33333D
            c.drawCircle(16, 21, 8.6, p)
            # 불꽃
            p.color = 0xFFE2574C
            c.drawRect(9, 18, 23, 28, p)
            p.color = 0xFFF2913C
            c.drawRect(11, 16 + ((0 if f == 0 else 1.6)), 21, 24, p)
            p.color = 0xFFF7CE5B
            c.drawRect(13, 15 + ((1.6 if f == 0 else 0)), 19, 21, p)
            p.color = 0xFFFDF6E8
            c.drawRect(15, 18 + ((0 if f == 0 else 1.4)), 17, 21, p)
            p.color = 0xFF6B6B78
            c.drawRect(9, 29, 23, 30.6, p)
        add(tile_painter(_tp))
    # BED
    begin('BED')
    def _tp(c, p, r, **kw):
        globals().update(kw)
        fill(c, p, 0xFFCDA775)
        p.color = 0xFF8A5A33
        c.drawRect(2, 2, 30, 30, p)
        p.color = 0xFFB5651D
        c.drawRect(3.4, 3.4, 28.6, 28.6, p)
        p.color = 0xFFE8867A
        c.drawRect(4.6, 8, 27.4, 27.4, p)
        p.color = 0xFFD96C64
        c.drawRect(4.6, 16, 27.4, 18, p)
        c.drawRect(12, 18, 14, 27.4, p)
        p.color = 0xFFF5EFE0
        c.drawRect(5.6, 3.8, 15, 9.4, p)
        p.color = 0xFFE0D8C4
        c.drawRect(5.6, 8, 15, 9.4, p)
        p.color = 0xFFF7B2A8
        c.drawRect(18, 11, 27.4, 14, p)
    add(tile_painter(_tp))
    # BOX
    begin('BOX')
    def _tp(c, p, r, **kw):
        globals().update(kw)
        fill(c, p, 0xFFCDA775)
        p.color = 0xFFC89B6A
        c.drawRect(2, 4, 30, 30, p)
        p.color = 0xFFA87B4F
        c.drawRect(2, 4, 30, 6.4, p)
        c.drawRect(2, 17, 30, 19, p)
        c.drawRect(2, 4, 4, 30, p)
        c.drawRect(28, 4, 30, 30, p)
        p.color = 0xFFD9C39A
        c.drawRect(12, 6.4, 20, 17, p)
        p.color = 0xFF7A5A33
        c.drawRect(14, 9, 18, 10.4, p)
        c.drawRect(14.8, 10.4, 16, 13, p)
        c.drawRect(16.8, 12, 18, 13, p)
        p.color = 0xFFE8D5A3
        c.drawRect(13, 20, 19, 26, p)
        p.color = 0xFF8A6A4F
        c.drawRect(13.6, 21, 18.4, 22, p)
    add(tile_painter(_tp))
    # DECOR (장식 칸 — 점선 표시)
    begin('DECOR')
    def _tp(c, p, r, **kw):
        globals().update(kw)
        fill(c, p, 0xFFCDA775)
        p.color = 0xFFB98F5E
        c.drawRect(0, 10, 32, 11.6, p)
        c.drawRect(0, 21, 32, 22.6, p)
        p.color = 0xFFB08A5C
        # 점선 사각형
        for i in range(13):
            c.drawRect((4 + i * 2), 4, (5 + i * 2), 5.4, p)
            c.drawRect((4 + i * 2), 26.6, (5 + i * 2), 28, p)
            c.drawRect(4, (4 + i * 2), 5.4, (5 + i * 2), p)
            c.drawRect(26.6, (4 + i * 2), 28, (5 + i * 2), p)
        p.color = 0xFFE8D5A3
        c.drawRect(14.6, 14.6, 17.4, 17.4, p)
    add(tile_painter(_tp))
    # SIGN (터널 이정표)
    begin('SIGN')
    def _tp(c, p, r, **kw):
        globals().update(kw)
        prop_shadow(c, p, 16, 29.5, 6.5, 2.4)
        p.color = 0xFF6B431F
        c.drawRect(14.6, 10, 17.4, 30, p)
        p.color = 0xFF8A5A33
        c.drawRect(15, 10, 16, 30, p)
        # 판
        p.color = 0xFF6B431F
        c.drawRect(5, 4, 27, 15, p)
        p.color = 0xFFC89B6A
        c.drawRect(6.2, 5.2, 25.8, 13.8, p)
        p.color = 0xFF8A6A4F
        c.drawRect(7.4, 6.4, 18, 7.8, p)
        c.drawRect(7.4, 9.4, 16, 10.8, p)
        c.drawRect(7.4, 12.4, 18, 13.2, p)
        # 화살표
        path = Path()
        p.color = 0xFF4A3728
        path.moveTo(20, 7)
        path.lineTo(24, 9.6)
        path.lineTo(20, 12.2)
        path.close()
        c.drawPath(path, p)
        c.drawRect(16.4, 8.8, 20.4, 10.4, p)
    add(tile_painter(_tp))
    # BENCH (벤치)
    begin('BENCH')
    def _tp(c, p, r, **kw):
        globals().update(kw)
        prop_shadow(c, p, 16, 27, 12, 3.2)
        # 등받이
        p.color = 0xFF6B431F
        c.drawRect(3, 3, 29, 5.4, p)
        p.color = 0xFF8A5A33
        c.drawRect(4, 4, 28, 5, p)
        c.drawRect(6, 3.4, 8, 12, p)
        c.drawRect(24, 3.4, 26, 12, p)
        # 좌석
        p.color = 0xFF6B431F
        c.drawRect(2.4, 14, 29.6, 19, p)
        p.color = 0xFFA87B4F
        c.drawRect(3.4, 15, 28.6, 18, p)
        p.color = 0xFFC89B6A
        c.drawRect(3.4, 15, 28.6, 16, p)
        # 다리
        p.color = 0xFF4A3320
        c.drawRect(4, 19, 6.4, 27, p)
        c.drawRect(25.6, 19, 28, 27, p)
    add(tile_painter(_tp))
    # LAMP (가로등)
    begin('LAMP')
    def _tp(c, p, r, **kw):
        globals().update(kw)
        prop_shadow(c, p, 16, 30, 8, 2.4)
        # 기둥
        p.color = 0xFF3A3F4A
        c.drawRect(14.4, 6, 17.6, 30, p)
        p.color = 0xFF5A626C
        c.drawRect(15.2, 6, 16.2, 30, p)
        p.color = 0xFF3A3F4A
        c.drawRect(8, 29, 24, 31, p)
        # 머리
        p.color = 0xFF3A3F4A
        c.drawRect(9, 2, 23, 7, p)
        p.color = 0xFFF7E9A8
        c.drawRect(10.4, 3.4, 21.6, 6, p)
        p.color = 0xFFFFFBE0
        c.drawRect(12, 4, 20, 5.4, p)
        p.color = 0xFF23232B
        c.drawRect(15.4, 7, 16.6, 8.4, p)
    add(tile_painter(_tp))
    flush()
    import roads as _R
    ART['PATH'] = [_R.road_tile(_R.PAVE_DIRT, 255, i, False) for i in range(3)]
    ART['PLAZA'] = [_R.road_tile(_R.PAVE_STONE, 255, i, False) for i in range(2)]


build()
