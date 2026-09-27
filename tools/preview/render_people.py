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


def npc_pal(hair, top, top2, pants, pack):
    return P.Pal(hair=hair, hair2=P.shade(hair, 0.75), skin=0xFFFFD9B0, skin2=0xFFE8B88C,
                 top=top, top2=top2, pants=pants, pants2=P.shade(pants, 0.75), shoe=0xFF3A3A44,
                 line=0xFF33241C, pack=pack, pack2=P.shade(pack, 0.75), eye=0xFF2E2620,
                 blush=0xFFF2A58C)


NPC_LOOKS = [
    P.Look(npc_pal(0xFFCFD2D8, 0xFFF5F2EA, 0xFFD8D2C4, 0xFF5D6470, 0xFF9AA3AD), glasses=True),
    P.Look(npc_pal(0xFF4A2F1D, 0xFF6FAE57, 0xFF4F7D3F, 0xFF8A6A4F, 0xFFC89B6A), apron=True),
    P.Look(npc_pal(0xFF2E2620, 0xFFC3A3E8, 0xFF9F7FC8, 0xFF4A6FA5, 0xFF8A5A33)),
    P.Look(npc_pal(0xFF5B3A29, 0xFFE2574C, 0xFFB23F44, 0xFF3F6FB0, 0xFFF2B63C), small=True),
    P.Look(npc_pal(0xFFE8E4DC, 0xFF8A7360, 0xFF6B5A48, 0xFF5D6470, 0xFF4F463F), cane=True, longHair=True),
]


def labelled_sheet(rows, scale=4, pad=2, label_w=104, bg=(150, 200, 146, 255)):
    """[(라벨, [비트맵...])] 를 한 장의 PNG 로."""
    from PIL import ImageDraw
    cell = P.SIZE + pad
    cols = max(len(r[1]) for r in rows)
    w = label_w + cols * cell * scale
    h = len(rows) * cell * scale
    img = Image.new("RGBA", (w, h), bg)
    d = ImageDraw.Draw(img)
    for ry, (label, frames) in enumerate(rows):
        y = ry * cell * scale
        d.text((6, y + cell * scale // 2 - 4), label, fill=(40, 36, 28, 255))
        for cx, bmp in enumerate(frames):
            im = to_img(bmp).resize((bmp.w * scale, bmp.h * scale), Image.NEAREST)
            img.alpha_composite(im, (label_w + cx * cell * scale, y))
    return img


def gif(path, frames, dur, scale=6, bg=(150, 200, 146, 255)):
    imgs = []
    for b in frames:
        im = to_img(b).resize((b.w * scale, b.h * scale), Image.NEAREST)
        base = Image.new("RGBA", im.size, bg)
        base.alpha_composite(im)
        imgs.append(base.convert("P", palette=Image.ADAPTIVE))
    imgs[0].save(path, save_all=True, append_images=imgs[1:], duration=dur, loop=0)


def main():
    outdir = sys.argv[1] if len(sys.argv) > 1 else "/tmp/people"
    os.makedirs(outdir, exist_ok=True)

    look = P.Look(MALE, gear=GEARS[3])
    look0 = P.Look(MALE)
    lookF = P.Look(FEMALE, gear=GEARS[2], longHair=True)

    rows = []
    for kind in ("idle", "walk", "run", "sneak", "aim"):
        for d, dn in ((P.SIDE, "side"), (P.FRONT, "front"), (P.BACK, "back")):
            rows.append((f"{kind} {dn}", clip(kind, d, look, COUNTS[kind])))
    rows.append(("bike side", [P.render_bike(P.SIDE, i / 8, look) for i in range(8)]))
    rows.append(("bike front", [P.render_bike(P.FRONT, i / 8, look) for i in range(8)]))
    rows.append(("cat sit", [P.render_cat(False, i / 8) for i in range(8)]))
    rows.append(("cat walk", [P.render_cat(True, i / 6) for i in range(6)]))
    for i, name in enumerate(("professor", "shopkeeper", "villager", "kid", "elder")):
        rows.append((name, [P.render(P.FRONT, P.npc_pose(i, f / 8), NPC_LOOKS[i]) for f in range(8)]))
    labelled_sheet(rows).save(os.path.join(outdir, "character_anim.png"))

    rows = []
    for tier, lk in ((0, look0), (2, P.Look(MALE, gear=GEARS[2])), (3, look), (2, lookF)):
        rows.append((f"tier{tier} walk", clip("walk", P.SIDE, lk, 8)))
    labelled_sheet(rows).save(os.path.join(outdir, "character_tiers.png"))

    gif(os.path.join(outdir, "anim_walk.gif"), clip("walk", P.SIDE, look, 8), 90)
    gif(os.path.join(outdir, "anim_run.gif"), clip("run", P.SIDE, look, 8), 65)
    gif(os.path.join(outdir, "anim_idle.gif"), clip("idle", P.FRONT, look, 12), 170)
    gif(os.path.join(outdir, "anim_bike.gif"), [P.render_bike(P.SIDE, i / 8, look) for i in range(8)], 80)
    gif(os.path.join(outdir, "anim_cat.gif"), [P.render_cat(True, i / 6) for i in range(6)], 110)
    print("saved ->", outdir)


if __name__ == "__main__":
    main()
