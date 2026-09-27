#!/usr/bin/env python3
"""자전거 프리뷰 — 모델 11종 · 도색 · 부속품 이미지(docs/img)를 만든다.

    python3 tools/preview/render_bikes.py [출력폴더]

게임(CharacterArt.renderBike)과 같은 알고리즘을 옮긴 people.render_bike 로
README에 들어가는 세 장을 다시 생성한다.

    models_side.png   모델 11종 (4x3 격자)
    paints.png        프레임 12색 / 타이어 6색 + 안장 6색 (12x2 격자)
    accessories.png   바구니·짐받이·전조등·스트리머·방울·전부 (3x2 격자)
"""
from __future__ import annotations

import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import people as P
from pixelcanvas import Bitmap

# 게임의 남자 탐조가 팔레트 (Assets.kt 와 동일) — 외형은 기본(티어 0) 라이더
MALE = P.Pal(
    hair=0xFF4A2F1D, hair2=0xFF382314, skin=0xFFFFD9B0, skin2=0xFFE8B88C,
    top=0xFFF2B63C, top2=0xFFD99B26, pants=0xFF4A6FA5, pants2=0xFF3A5A8A,
    shoe=0xFF7A4A2B, line=0xFF33241C, pack=0xFFD9534F, pack2=0xFFB23F44,
    eye=0xFF2E2620, blush=0xFFF2A58C,
)
RIDER = P.Look(MALE)

BG = (246, 236, 216, 255)
SCALE = 6
PHASE = 0.15


def to_img(bmp: Bitmap) -> Image.Image:
    return Image.fromarray(np.clip(bmp.buf, 0, 255).astype(np.uint8), "RGBA")


def place(canvas: Image.Image, style: P.BikeStyle, px: float, py: float):
    bmp = P.render_bike(P.SIDE, PHASE, RIDER, style)
    im = to_img(bmp).resize((P.SIZE * SCALE, P.SIZE * SCALE), Image.NEAREST)
    canvas.alpha_composite(im, (round(px), round(py)))


def models_side(path: str):
    cell = 212
    w, h = 848, 640
    img = Image.new("RGBA", (w, h), BG)
    for i, model in enumerate(P.BikeStyle.MODELS):
        col, row = i % 4, i // 4
        x = col * cell + (cell - P.SIZE * SCALE) / 2
        y = row * cell + (cell - P.SIZE * SCALE) / 2 + 4
        place(img, P.BikeStyle(model), x, y)
    img.save(path)


def paints(path: str):
    w, h = 2512, 432
    img = Image.new("RGBA", (w, h), BG)
    cell_w, cell_h = w / 12, h / 2

    def spot(i: int, row: int):
        return (i * cell_w + (cell_w - P.SIZE * SCALE) / 2,
                row * cell_h + (cell_h - P.SIZE * SCALE) / 2)

    for i, col in enumerate(P.FRAME_COLORS):
        x, y = spot(i, 0)
        place(img, P.BikeStyle("basic", frame=col), x, y)
    for i, col in enumerate(P.TIRE_COLORS):
        x, y = spot(i, 1)
        place(img, P.BikeStyle("basic", tire=col), x, y)
    for i, col in enumerate(P.SADDLE_COLORS):
        x, y = spot(len(P.TIRE_COLORS) + i, 1)
        place(img, P.BikeStyle("basic", saddle=col), x, y)
    img.save(path)


def accessories(path: str):
    w, h = 640, 432
    img = Image.new("RGBA", (w, h), BG)
    cell_w, cell_h = w / 3, h / 2
    parts = [
        P.BikeStyle(basket=True),                    # 라탄 바구니
        P.BikeStyle(rack=True),                      # 뒷바퀴 짐받이
        P.BikeStyle(light=True),                     # 전조등
        P.BikeStyle(streamers=True),                 # 바람개비 스트리머
        P.BikeStyle(bell=True),                      # 예쁜 방울
        P.BikeStyle(basket=True, rack=True, light=True, streamers=True, bell=True),
    ]
    for i, style in enumerate(parts):
        col, row = i % 3, i // 3
        x = col * cell_w + (cell_w - P.SIZE * SCALE) / 2
        y = row * cell_h + (cell_h - P.SIZE * SCALE) / 2
        place(img, style, x, y)
    img.save(path)


def main():
    outdir = sys.argv[1] if len(sys.argv) > 1 else "/tmp/bikes"
    os.makedirs(outdir, exist_ok=True)
    models_side(os.path.join(outdir, "models_side.png"))
    paints(os.path.join(outdir, "paints.png"))
    accessories(os.path.join(outdir, "accessories.png"))
    print("saved ->", outdir)


if __name__ == "__main__":
    main()
