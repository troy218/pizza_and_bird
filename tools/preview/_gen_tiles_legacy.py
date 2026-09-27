#!/usr/bin/env python3
"""Assets.kt -> tiles_legacy.py 1회 생성 스크립트 (미리보기 전용)."""
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', '..', 'app/src/main/java/com/pizzaandbird/game/Assets.kt')

src = open(ASSETS, encoding='utf-8').read().splitlines()
s0 = next(i for i, l in enumerate(src) if '// GRASS (4종 변형)' in l)
e0 = next(i for i, l in enumerate(src) if 'medallion = RoadArt.medallion' in l)
open('/tmp/_bt.kt', 'w', encoding='utf-8').write("\n".join(src[s0:e0]) + "\n")

gen = subprocess.run([sys.executable, os.path.join(HERE, '_kt2py.py'), '/tmp/_bt.kt'],
                     capture_output=True, text=True, check=True).stdout
gen = gen.replace("xs = ([5, 12, 20, 27] if i == 0 else [8), 15, 24]",
                  "xs = [5, 12, 20, 27] if i == 0 else [8, 15, 24]")

NAMES = {'GRASS', 'TALLGRASS', 'FLOWER', 'PATH', 'PLAZA', 'SAND', 'WATER', 'REED', 'TREE', 'ROCK',
         'MOUNTAIN', 'BLDG_WALL', 'BLDG_WIN', 'BLDG_ROOF', 'HOUSE_ROOF', 'HOUSE_WALL', 'HOUSE_WIN',
         'HOUSE_DOOR', 'TUNNEL', 'FLOOR', 'WALL_IN', 'WALL_WIN', 'OVEN', 'BED', 'BOX', 'DECOR',
         'SIGN', 'BENCH', 'LAMP'}
# 지면 레이어 위에 얹는 소품 — 배경을 지우고 발밑 그림자를 넣는다
NO_BG_GRASS = {'TREE', 'ROCK', 'SIGN'}
NO_BG_PLAZA = {'BENCH', 'LAMP'}
SHADOW = {'TREE': (16, 29.5, 9.5, 3.4), 'ROCK': (16, 26.5, 9.0, 3.0), 'SIGN': (16, 29.5, 6.5, 2.4),
          'BENCH': (16, 27.0, 12.0, 3.2), 'LAMP': (16, 30.0, 8.0, 2.4)}
PLAZA_BG = {'fill(c, p, 0xFFD9C9A7)', 'p.color = 0xFFC6B58F', 'c.drawRect(0, 0, 32, 1.6, p)',
            'c.drawRect(0, 15.5, 32, 17, p)', 'p.color = 0xFFE9DCBC',
            'c.drawRect(2.4, 2.4, 14.8, 14.8, p)', 'c.drawRect(18.2, 18.2, 30, 30, p)'}

out = []
cur = None
for ln in gen.splitlines():
    st = ln.strip()
    m = re.match(r"^begin\(T\.([A-Z_]+)\)", st)
    if m and m.group(1) in NAMES:
        cur = m.group(1)
        out.append(f"begin('{cur}')")
        continue
    # 길(PATH/PLAZA)은 roads.py 가 대신 그린다 — 여기서는 건너뛴다
    if 'RoadArt.tile' in st:
        continue
    out.append(ln.replace('propShadow(', 'prop_shadow('))
out.append('flush()')
out.append("import roads as _R")
out.append("ART['PATH'] = [_R.road_tile(_R.PAVE_DIRT, 255, i, False) for i in range(3)]")
out.append("ART['PLAZA'] = [_R.road_tile(_R.PAVE_STONE, 255, i, False) for i in range(2)]")

HEAD = '''#!/usr/bin/env python3
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
'''
body = "\n".join(("    " + l) if l.strip() else l for l in out)
open(os.path.join(HERE, 'tiles_legacy.py'), 'w', encoding='utf-8').write(HEAD + body + "\n\n\nbuild()\n")
print('tiles_legacy.py 생성 완료')
