#!/usr/bin/env python3
"""캐릭터 애니메이션 미리보기 — 스프라이트 시트 PNG + GIF 를 만든다.

    python3 tools/preview/render_people.py [출력폴더]
"""
from __future__ import annotations

import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import people as P
from pixelcanvas import Bitmap

MALE = P.Pal(
    hair=0xFF4A2F1D, hair2=0xFF382314, skin=0xFFFFD9B0, skin2=0xFFE8B88C,
    top=0xFFF2B63C, top2=0xFFD99B26, pants=0xFF4A6FA5, pants2=0xFF3A5A8A,
    shoe=0xFF7A4A2B, line=0xFF33241C, pack=0xFFD9534F, pack2=0xFFB23F44,
    eye=0xFF2E2620, blush=0xFFF2A58C,
)
FEMALE = MALE.copy(hair=0xFF6A3155, hair2=0xFF4A203D, top=0xFFDB6B9A, top2=0xFFB84D7B,
                   pants=0xFF66529B, pants2=0xFF4D3C7C)

GEARS = [
    None,
    P.Gear(cap=0xFF4F8F6A, capDark=0xFF3C6E50),
    P.Gear(cap=0xFF3F6FA0, capDark=0xFF2F5580, vest=0xFF6B8E4E, vestDark=0xFF52703B),
    P.Gear(cap=0xFF8A5A2B, capDark=0xFF6E4620, vest=0xFF3E6B57, vestDark=0xFF2C4E3F,
           scarf=0xFFD9534F, brim=True, feather=0xFFF2D06B),
]


def to_img(bmp: Bitmap) -> Image.Image:
    return Image.fromarray(np.clip(bmp.buf, 0, 255).astype(np.uint8), "RGBA")


def sheet(frames_rows, scale=5, bg=(158, 208, 152, 255), pad=1):
    rows = len(frames_rows)
    cols = max(len(r) for r in frames_rows)
    w = cols * (P.SIZE + pad) * scale
    h = rows * (P.SIZE + pad) * scale
    img = Image.new("RGBA", (w, h), bg)
    for ry, row in enumerate(frames_rows):
        for cx, bmp in enumerate(row):
            cell = to_img(bmp).resize((P.SIZE * scale, P.SIZE * scale), Image.NEAREST)
            img.alpha_composite(cell, (cx * (P.SIZE + pad) * scale, ry * (P.SIZE + pad) * scale))
    return img


def clip(kind, direction, look, n):
    out = []
    for i in range(n):
        ph = i / n
        if kind == "idle":
            pose = P.idle_pose(ph)
        elif kind == "walk":
            pose = P.walk_pose(ph, P.WALK)
        elif kind == "run":
            pose = P.walk_pose(ph, P.RUN)
        elif kind == "sneak":
            pose = P.walk_pose(ph, P.SNEAK)
        elif kind == "aim":
            pose = P.aim_pose(ph)
        else:
            raise ValueError(kind)
        out.append(P.render(direction, pose, look))
    return out


COUNTS = {"idle": 12, "walk": 8, "run": 8, "sneak": 8, "aim": 4}


def main():
    outdir = sys.argv[1] if len(sys.argv) > 1 else "/tmp/people"
    os.makedirs(outdir, exist_ok=True)

    look = P.Look(MALE, gear=GEARS[3])
    rows = []
    for kind in ("idle", "walk", "run", "sneak", "aim"):
        for d in (P.FRONT, P.SIDE, P.BACK):
            rows.append(clip(kind, d, look, COUNTS[kind]))
    sheet(rows).save(os.path.join(outdir, "player_tier3.png"))

    look0 = P.Look(MALE)
    rows = []
    for kind in ("idle", "walk", "run"):
        for d in (P.FRONT, P.SIDE, P.BACK):
            rows.append(clip(kind, d, look0, COUNTS[kind]))
    sheet(rows).save(os.path.join(outdir, "player_tier0.png"))

    lookF = P.Look(FEMALE, gear=GEARS[2], longHair=True)
    rows = []
    for kind in ("idle", "walk", "run"):
        for d in (P.FRONT, P.SIDE, P.BACK):
            rows.append(clip(kind, d, lookF, COUNTS[kind]))
    sheet(rows).save(os.path.join(outdir, "player_female.png"))

    # GIF (걷기/달리기 측면 + 정면)
    for kind in ("idle", "walk", "run", "sneak", "aim"):
        for d, dn in ((P.FRONT, "front"), (P.SIDE, "side"), (P.BACK, "back")):
            fr = clip(kind, d, look, COUNTS[kind])
            imgs = [to_img(b).resize((P.SIZE * 6, P.SIZE * 6), Image.NEAREST) for b in fr]
            base = [Image.new("RGBA", im.size, (158, 208, 152, 255)) for im in imgs]
            for bg, im in zip(base, imgs):
                bg.alpha_composite(im)
            dur = {"idle": 150, "walk": 90, "run": 70, "sneak": 140, "aim": 200}[kind]
            base[0].convert("P").save(
                os.path.join(outdir, f"{kind}_{dn}.gif"), save_all=True,
                append_images=[b.convert("P") for b in base[1:]], duration=dur, loop=0)
    print("saved ->", outdir)


if __name__ == "__main__":
    main()
