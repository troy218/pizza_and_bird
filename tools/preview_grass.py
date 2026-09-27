"""
Assets.kt의 GRASS/TALLGRASS/FLOWER 타일 생성 로직을 Python(PIL)으로 재현하여
업그레이드 전/후 비교 프리뷰 PNG를 만드는 스크립트 (게임 코드에는 영향 없음, 시각 확인용).
"""
import random
from PIL import Image, ImageDraw

TS = 32  # 타일 크기 (Assets.kt와 동일)

def shade(color, f):
    r, g, b = color
    return (min(255, max(0, int(r * f))), min(255, max(0, int(g * f))), min(255, max(0, int(b * f))))

def fill(draw, color):
    draw.rectangle([0, 0, TS, TS], fill=color)

def specks(draw, rnd, color, n):
    for _ in range(n):
        x = rnd.randint(0, 30)
        y = rnd.randint(0, 30)
        draw.rectangle([x, y, x + 2, y + 1.5], fill=color)

BASE = (0x96, 0xD0, 0x7A)
TALL_BASE = (0x8C, 0xC4, 0x6C)

# ------------------------------------------------------------------
# BEFORE (업그레이드 전)
# ------------------------------------------------------------------

def grass_base_old(draw, rnd, base=BASE):
    fill(draw, base)
    specks(draw, rnd, shade(base, 0.9), 7)
    specks(draw, rnd, shade(base, 1.08), 6)
    col = shade(base, 0.82)
    for _ in range(5):
        x = rnd.randint(0, 28)
        y = rnd.randint(0, 23)
        draw.rectangle([x, y, x + 1.4, y + 3.5], fill=col)
    col = shade(base, 1.14)
    for _ in range(3):
        x = rnd.randint(0, 28)
        y = rnd.randint(0, 23)
        draw.rectangle([x, y, x + 1.4, y + 2.8], fill=col)

def grass_tile_old(i, seed):
    rnd = random.Random(seed)
    img = Image.new('RGB', (TS, TS))
    d = ImageDraw.Draw(img)
    grass_base_old(d, rnd)
    if i == 1:
        d.rectangle([5, 6, 9, 7.2], fill=(0x6F, 0xAE, 0x57))
        d.rectangle([7, 5, 8.2, 9], fill=(0x6F, 0xAE, 0x57))
        d.rectangle([22, 20, 26, 21.2], fill=(0x6F, 0xAE, 0x57))
        d.rectangle([24, 19, 25.2, 23], fill=(0x6F, 0xAE, 0x57))
    if i == 2:
        d.rectangle([14, 18, 17, 20], fill=(0xA8, 0xB0, 0xA0))
        d.rectangle([14.8, 17.4, 16.2, 20.6], fill=(0xA8, 0xB0, 0xA0))
    if i == 3:
        d.rectangle([20, 9, 22, 11], fill=(0xFD, 0xF6, 0xE8))
        d.rectangle([19.4, 9.6, 22.6, 10.4], fill=(0xFD, 0xF6, 0xE8))
    return img

# ------------------------------------------------------------------
# AFTER (업그레이드 후)
# ------------------------------------------------------------------

def grass_tuft(draw, x, y, dark, tip):
    draw.rectangle([x, y + 1.4, x + 1, y + 4.4], fill=dark)
    draw.rectangle([x + 3, y + 1.8, x + 4, y + 4.2], fill=dark)
    draw.rectangle([x + 1.5, y, x + 2.5, y + 4.6], fill=dark)
    draw.rectangle([x + 1.5, y, x + 2.5, y + 1.3], fill=tip)

def grass_base_new(draw, rnd, base=BASE):
    fill(draw, base)
    col = shade(base, 0.88)
    for _ in range(20):
        x = rnd.randint(0, 31); y = rnd.randint(0, 31)
        if (x + y) % 2 == 0:
            draw.rectangle([x, y, x + 1, y + 1], fill=col)
    col = shade(base, 1.16)
    for _ in range(14):
        x = rnd.randint(0, 31); y = rnd.randint(0, 31)
        if (x + y) % 2 == 1:
            draw.rectangle([x, y, x + 1, y + 1], fill=col)
    specks(draw, rnd, shade(base, 0.9), 3)
    for _ in range(4):
        bx = 2 + rnd.randint(0, 26); by = 3 + rnd.randint(0, 19)
        grass_tuft(draw, bx, by, shade(base, 0.74), shade(base, 0.9))
    for _ in range(2):
        bx = 2 + rnd.randint(0, 26); by = 3 + rnd.randint(0, 19)
        grass_tuft(draw, bx, by, shade(base, 1.22), shade(base, 1.38))

def grass_tile_new(i, seed):
    rnd = random.Random(seed)
    img = Image.new('RGB', (TS, TS))
    d = ImageDraw.Draw(img)
    grass_base_new(d, rnd)
    if i == 1:
        d.rectangle([5, 6, 9, 7.2], fill=(0x6F, 0xAE, 0x57))
        d.rectangle([7, 5, 8.2, 9], fill=(0x6F, 0xAE, 0x57))
        d.rectangle([22, 20, 26, 21.2], fill=(0x6F, 0xAE, 0x57))
        d.rectangle([24, 19, 25.2, 23], fill=(0x6F, 0xAE, 0x57))
    elif i == 2:
        d.rectangle([14, 18.4, 17.4, 20.6], fill=(0x6B, 0x74, 0x7E))
        d.rectangle([14.4, 17.6, 16.8, 19.8], fill=(0xA8, 0xB0, 0xA0))
        d.rectangle([14.8, 17.8, 15.8, 18.6], fill=(0xC8, 0xCF, 0xD6))
    elif i == 3:
        d.rectangle([20.6, 10.6, 21.4, 14], fill=(0x6F, 0xAE, 0x57))
        d.ellipse([21 - 2.1, 9.6 - 2.1, 21 + 2.1, 9.6 + 2.1], fill=(0xFD, 0xF6, 0xE8))
        d.ellipse([21 - 0.9, 9.6 - 0.9, 21 + 0.9, 9.6 + 0.9], fill=(0xF0, 0xEA, 0xE0))
    elif i == 4:
        d.rectangle([9.4, 14.4, 10.2, 17.4], fill=(0x5D, 0x8A, 0x4A))
        for cx, cy in [(8.4, 13.6), (11, 13.6), (9.7, 11.8)]:
            d.ellipse([cx - 1.7, cy - 1.7, cx + 1.7, cy + 1.7], fill=(0x4F, 0xA2, 0x5A))
        d.ellipse([9.7 - 0.8, 12.8 - 0.8, 9.7 + 0.8, 12.8 + 0.8], fill=(0x6B, 0xBA, 0x72))
    elif i == 5:
        d.rectangle([23.6, 22.6, 24.4, 24.4], fill=(0xB0, 0x79, 0x3F))
        d.rectangle([22.4, 20.6, 25.6, 23], fill=(0xE2, 0x57, 0x4C))
        d.rectangle([23, 21, 23.7, 21.6], fill=(0xFD, 0xF6, 0xE8))
        d.rectangle([24.6, 21.6, 25.2, 22.2], fill=(0xFD, 0xF6, 0xE8))
    return img

def tallgrass_tile_new(i, seed):
    rnd = random.Random(seed)
    img = Image.new('RGB', (TS, TS))
    d = ImageDraw.Draw(img)
    grass_base_new(d, rnd, TALL_BASE)
    for k in range(5 + i):
        x = 1 + rnd.randint(0, 27)
        h = 10 + rnd.randint(0, 9)
        bend = 1.3 if k % 2 == 0 else -1.3
        d.rectangle([x, 32 - h, x + 2.2, 32], fill=(0x5D, 0x8A, 0x4A))
        d.rectangle([x + bend, 32 - h, x + bend + 1.6, 32 - h + 3], fill=(0x5D, 0x8A, 0x4A))
    for _ in range(5 + i):
        x = 1 + rnd.randint(0, 27)
        h = 8 + rnd.randint(0, 7)
        top = 32 - h
        d.rectangle([x, top, x + 1.8, 32], fill=(0x6F, 0xAE, 0x57))
        d.rectangle([x, top, x + 1.8, top + 2.2], fill=(0xB7, 0xE0, 0x8C))
    d.rectangle([6, 8, 7.6, 18], fill=(0x5D, 0x8A, 0x4A))
    d.rectangle([22, 6, 23.6, 20], fill=(0x5D, 0x8A, 0x4A))
    d.rectangle([6, 8, 7, 10], fill=(0x8C, 0xC4, 0x6C))
    d.rectangle([22, 6, 22.8, 8], fill=(0x8C, 0xC4, 0x6C))
    return img

def tallgrass_tile_old(i, seed):
    rnd = random.Random(seed)
    img = Image.new('RGB', (TS, TS))
    d = ImageDraw.Draw(img)
    grass_base_old(d, rnd, TALL_BASE)
    for k in range(6 + i):
        x = 1 + rnd.randint(0, 27)
        h = 9 + rnd.randint(0, 8)
        d.rectangle([x, 32 - h, x + 2, 32], fill=(0x6F, 0xAE, 0x57))
    for _ in range(4):
        x = 1 + rnd.randint(0, 27)
        h = 7 + rnd.randint(0, 6)
        d.rectangle([x, 32 - h, x + 1.6, 32], fill=(0x8C, 0xC4, 0x6C))
    d.rectangle([6, 8, 7.4, 18], fill=(0x5D, 0x8A, 0x4A))
    d.rectangle([22, 6, 23.4, 20], fill=(0x5D, 0x8A, 0x4A))
    return img

FLOWER_COLS_OLD = [(0xF2, 0xA3, 0xB3), (0xF2, 0xD0, 0x6B), (0xFD, 0xFD, 0xF8)]
FLOWER_COLS_NEW = [(0xF2, 0xA3, 0xB3), (0xF2, 0xD0, 0x6B), (0xFD, 0xFD, 0xF8), (0xC9, 0xA8, 0xE8)]

def flower_tile_old(i, seed):
    rnd = random.Random(seed)
    img = Image.new('RGB', (TS, TS))
    d = ImageDraw.Draw(img)
    grass_base_old(d, rnd)
    for _ in range(4):
        x = 3 + rnd.randint(0, 22)
        y = 3 + rnd.randint(0, 21)
        d.rectangle([x + 1.6, y + 2.4, x + 2.6, y + 5.2], fill=(0x5D, 0x8A, 0x4A))
        col = FLOWER_COLS_OLD[(i + rnd.randint(0, 2)) % 3]
        d.rectangle([x, y, x + 4.2, y + 2.6], fill=col)
        d.rectangle([x + 1, y - 1, x + 3.2, y + 3.6], fill=col)
        d.rectangle([x + 1.4, y + 0.4, x + 2.8, y + 1.8], fill=(0xF7, 0xCE, 0x5B))
    return img

def flower_tile_new(i, seed):
    rnd = random.Random(seed)
    img = Image.new('RGB', (TS, TS))
    d = ImageDraw.Draw(img)
    grass_base_new(d, rnd)
    for _ in range(4):
        x = 4 + rnd.randint(0, 20)
        y = 5 + rnd.randint(0, 17)
        d.rectangle([x + 1.7, y + 2.6, x + 2.5, y + 6], fill=(0x5D, 0x8A, 0x4A))
        d.rectangle([x + 0.4, y + 3.8, x + 2, y + 5], fill=(0x6F, 0xAE, 0x57))
        col = FLOWER_COLS_NEW[(i + rnd.randint(0, 3)) % 4]
        for cx, cy in [(x + 2.1, y - 0.3), (x + 2.1, y + 2.5), (x + 0.5, y + 1.1), (x + 3.7, y + 1.1)]:
            d.ellipse([cx - 1.8, cy - 1.8, cx + 1.8, cy + 1.8], fill=col)
        cx, cy = x + 2.1, y + 1.1
        d.ellipse([cx - 1.3, cy - 1.3, cx + 1.3, cy + 1.3], fill=(0xF7, 0xCE, 0x5B))
        d.ellipse([cx - 0.6, cy - 0.6, cx + 0.6, cy + 0.6], fill=(0xE8, 0xB1, 0x4E))
    return img

# ------------------------------------------------------------------
# 메도우(초원) 프리뷰 합성: tileVariant(x,y) = (x*7+y*13) % n 방식 재현
# ------------------------------------------------------------------

def variant(x, y, n):
    return ((x * 7 + y * 13) % n + n) % n

def build_meadow(tile_fn, n_variants, cols, rows, seed_base):
    img = Image.new('RGB', (cols * TS, rows * TS))
    for row in range(rows):
        for col in range(cols):
            v = variant(col, row, n_variants)
            t = tile_fn(v, seed_base + col * 101 + row * 977 + v * 31)
            img.paste(t, (col * TS, row * TS))
    return img

SCALE = 6
COLS, ROWS = 8, 5

before_grass = build_meadow(grass_tile_old, 4, COLS, ROWS, 1000)
after_grass = build_meadow(grass_tile_new, 6, COLS, ROWS, 2000)
before_tall = build_meadow(tallgrass_tile_old, 2, COLS, ROWS, 3000)
after_tall = build_meadow(tallgrass_tile_new, 3, COLS, ROWS, 4000)
before_flower = build_meadow(flower_tile_old, 3, COLS, ROWS, 5000)
after_flower = build_meadow(flower_tile_new, 4, COLS, ROWS, 6000)

def upscale(img):
    return img.resize((img.width * SCALE, img.height * SCALE), Image.NEAREST)

pairs = [
    ("GRASS", before_grass, after_grass),
    ("TALLGRASS", before_tall, after_tall),
    ("FLOWER", before_flower, after_flower),
]

pad = 24
label_h = 40
tile_w = COLS * TS * SCALE
tile_h = ROWS * TS * SCALE
canvas_w = pad * 3 + tile_w * 2
canvas_h = pad * 2 + label_h + (label_h + tile_h + pad) * len(pairs)

canvas = Image.new('RGB', (canvas_w, canvas_h), (28, 30, 26))
draw = ImageDraw.Draw(canvas)
draw.text((pad, 10), "Pizza and Bird - Grass Tile Redesign Preview (Before / After)", fill=(240, 240, 230))

y = pad + label_h
for name, before, after in pairs:
    draw.text((pad, y), f"[{name}]  BEFORE", fill=(220, 180, 150))
    draw.text((pad * 2 + tile_w, y), f"[{name}]  AFTER", fill=(170, 230, 170))
    y += label_h
    canvas.paste(upscale(before), (pad, y))
    canvas.paste(upscale(after), (pad * 2 + tile_w, y))
    y += tile_h + pad

canvas.save('/home/user/pizza_and_bird/tools/grass_preview.png')
print("saved")
