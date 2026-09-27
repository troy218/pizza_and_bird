#!/usr/bin/env python3
"""NPC 전원 외형 시트 — NpcRoster.kt를 직접 읽어 66명+안내인 6명을 렌더한다.

Kotlin이 유일한 원본이다. 이 스크립트는 NpcRoster.kt의 Looks 정의와
CAST 항목을 파싱해 people.py 미러로 그린다 (색·파츠·시드까지 동일).

    python3 tools/preview/render_cast.py [출력폴더]
"""
from __future__ import annotations

import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import people as P
from pixelcanvas import Bitmap

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ROSTER = os.path.join(ROOT, "app/src/main/java/com/pizzaandbird/game/NpcRoster.kt")
FONT = os.path.join(ROOT, "app/src/main/assets/font/display_jua.ttf")

KINDS = {
    "PROFESSOR": P.NPC_PROFESSOR, "SHOP": P.NPC_SHOP, "VILLAGER": P.NPC_VILLAGER,
    "KID": P.NPC_KID, "ELDER": P.NPC_ELDER,
}

SKINS = {
    P.SKIN_FAIR: (0xFFFFE3C4, 0xFFF0C39E),
    P.SKIN_TAN: (0xFFF5B885, 0xFFE09A6E),
    P.SKIN_DEEP: (0xFFD99A6B, 0xFFB87A52),
    P.SKIN_NORMAL: (0xFFFFD9B0, 0xFFE8B88C),
}


def jhash(s: str) -> int:
    """Java/Kotlin String.hashCode — 게임과 같은 시드를 얻는다."""
    h = 0
    for ch in s:
        h = (31 * h + ord(ch)) & 0xFFFFFFFF
    if h & 0x80000000:
        h -= 0x100000000
    return h


def parse_value(v: str):
    v = v.strip()
    m = re.match(r"CharacterArt\.(\w+)", v)
    if m:
        return getattr(P, m.group(1))
    m = re.match(r"(0x[0-9A-Fa-f]+)(\.toInt\(\))?", v)
    if m:
        return int(m.group(1), 16)
    if v == "true":
        return True
    if v == "false":
        return False
    raise ValueError(f"unknown value: {v}")


def split_top_commas(s: str):
    parts, depth, cur = [], 0, ""
    for ch in s:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append(cur)
            cur = ""
        else:
            cur += ch
    if cur.strip():
        parts.append(cur)
    return parts


def match_paren(text: str, open_idx: int) -> str:
    """open_idx의 '('에 대응하는 안쪽 문자열."""
    assert text[open_idx] == "("
    depth = 0
    for i in range(open_idx, len(text)):
        if text[i] == "(":
            depth += 1
        elif text[i] == ")":
            depth -= 1
            if depth == 0:
                return text[open_idx + 1:i]
    raise ValueError("unbalanced paren")


def parse_roster():
    src = open(ROSTER, encoding="utf-8").read()
    # --- Looks 정의 ---
    looks = {}
    for m in re.finditer(r"val (\w+) = look\((0x[0-9A-Fa-f]+),\s*(0x[0-9A-Fa-f]+),\s*(0x[0-9A-Fa-f]+),\s*(0x[0-9A-Fa-f]+),\s*(0x[0-9A-Fa-f]+)(.*?)\)\s*\n", src):
        name = m.group(1)
        cols = [int(g, 16) for g in m.groups()[1:6]]
        kw = dict(re.findall(r"(\w+)\s*=\s*(0x[0-9A-Fa-f]+|true|false)", m.group(6)))
        looks[name] = {
            "hair": cols[0], "top": cols[1], "top2": cols[2], "pants": cols[3], "pack": cols[4],
            "glasses": kw.get("glasses") == "true",
            "apron": kw.get("apron") == "true",
            "cane": kw.get("cane") == "true",
            "longHair": kw.get("longHair") == "true",
            "cap": int(kw["cap"], 16) if "cap" in kw else None,
            "vest": int(kw["vest"], 16) if "vest" in kw else None,
            "scarf": int(kw["scarf"], 16) if "scarf" in kw else None,
        }
    # --- CAST 항목 (local/resident 한 줄 호출) ---
    cast = []
    for m in re.finditer(
        r'''(local|resident)\("([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*NpcKind\.(\w+),\s*Looks\.(\w+)''',
        src,
    ):
        fn, region, name, title, kind, look_name = m.groups()
        args = {}
        tail = src[m.end():m.end() + 6]
        if tail.startswith(".copy("):
            inner = match_paren(src, m.end() + 5)
            for part in split_top_commas(inner):
                k, v = part.split("=", 1)
                args[k.strip()] = parse_value(v)
        pid = f"{region}:{fn}"
        cast.append({"id": pid, "region": region, "name": name, "title": title,
                     "kind": kind, "look": look_name, "args": args})
    # --- 보리 박사 · 사진용품점 (여러 줄 NpcPerson 블록) ---
    for pid, pname in (("professor", "보리 박사"), ("shop", "사진용품점")):
        m = re.search(r'"%s",\s*"%s".*?NpcKind\.(\w+),\s*Looks\.(\w+)(\.copy\()?\(?' % (pid, pname), src, re.S)
        kind, look_name = m.group(1), m.group(2)
        args = {}
        if m.group(3):
            # ".copy(" 뒤의 '(' 위치 = 매치 끝 - 1
            inner = match_paren(src, m.end() - 1)
            for part in split_top_commas(inner):
                k, v = part.split("=", 1)
                args[k.strip()] = parse_value(v)
        cast.append({"id": pid, "region": "gwangneung" if pid == "professor" else "seoul",
                     "name": pname, "title": "", "kind": kind, "look": look_name, "args": args})
    return looks, cast


def flavor_of(prop: int, kind: int) -> int:
    if prop == P.PROP_BRUSH:
        return P.FLAVOR_PAINT
    if prop == P.PROP_CUP:
        return P.FLAVOR_SIP
    if prop == P.PROP_BINOCS:
        return P.FLAVOR_SCAN
    if prop == P.PROP_BOOK:
        return P.FLAVOR_READ
    if prop in (P.PROP_ROD, P.PROP_NET, P.PROP_PADDLE):
        return P.FLAVOR_SWAY
    if kind == P.NPC_KID:
        return P.FLAVOR_BOUNCE
    if kind == P.NPC_ELDER:
        return P.FLAVOR_NOD
    return P.FLAVOR_NONE


def build_look(base: dict, args: dict, kind: int) -> P.Look:
    """Assets.npcLook 미러."""
    skin = args.get("skin", P.SKIN_NORMAL)
    sk, sk2 = SKINS[skin]
    pal = P.Pal(
        hair=base["hair"], hair2=P.shade(base["hair"], 0.75),
        skin=sk, skin2=sk2,
        top=base["top"], top2=base["top2"],
        pants=base["pants"], pants2=P.shade(base["pants"], 0.75),
        shoe=0xFF3A3A44, line=0xFF33241C,
        pack=base["pack"], pack2=P.shade(base["pack"], 0.75),
        eye=0xFF2E2620, blush=0xFFF2A58C,
    )
    hat = args.get("hat", P.HAT_NONE)
    use_hat = hat != P.HAT_NONE
    cap = None if use_hat else base["cap"]
    vest = args.get("vest", base["vest"])
    scarf = args.get("scarf", base["scarf"])
    gear = None
    if use_hat or base["cap"] is not None or vest is not None or scarf is not None:
        gear = P.Gear(cap=cap, capDark=P.shade(base["cap"] or 0, 0.72),
                      vest=vest, vestDark=P.shade(vest or 0, 0.75),
                      scarf=scarf, brim=base["cap"] is not None)
    return P.Look(
        pal, gear=gear,
        glasses=args.get("glasses", base["glasses"]),
        apron=args.get("apron", base["apron"]),
        cane=args.get("cane", base["cane"]),
        small=kind == P.NPC_KID,
        longHair=base["longHair"],
        body=args.get("body", P.BODY_STANDARD),
        hairStyle=args.get("hairStyle", P.HAIR_SHORT),
        beard=args.get("beard", P.BEARD_NONE),
        hat=hat,
        hatColor=args.get("hatColor", base["cap"] or 0),
        bottom=args.get("bottom", P.BOTTOM_PANTS),
        prop=args.get("prop", P.PROP_NONE),
        propColor=args.get("propColor", 0),
        wrinkles=args.get("wrinkles", False) or kind == P.NPC_ELDER,
        freckles=args.get("freckles", False),
    )


def to_img(bmp: Bitmap) -> Image.Image:
    return Image.fromarray(np.clip(bmp.buf, 0, 255).astype(np.uint8), "RGBA")


DOCENTS = [
    ("전망대 안내원", dict(hat=P.HAT_CAP, hatColor=0xFF2F4A6B, vest=0xFFE2853C,
                          prop=P.PROP_BINOCS, body=P.BODY_TALL),
     (0xFF2A2F3A, 0xFF3F6FA0, 0xFF2F5580, 0xFF33383F, 0xFF9AA3AD), P.NPC_SHOP),
    ("한옥 지킴이", dict(hairStyle=P.HAIR_BUN, beard=P.BEARD_MUSTACHE, vest=0xFFE0B24A,
                        prop=P.PROP_BOOK, wrinkles=True),
     (0xFF4A2F1D, 0xFFC89B6A, 0xFFA97C50, 0xFF6B5A48, 0xFF8A5A33), P.NPC_ELDER),
    ("등대지기", dict(beard=P.BEARD_FULL, hat=P.HAT_FISHER, hatColor=0xFFF2B63C,
                     prop=P.PROP_CUP, body=P.BODY_STOCKY, wrinkles=True),
     (0xFF5B4632, 0xFFEAF3F6, 0xFFCBD9E0, 0xFF4A6FA5, 0xFF8A6A4F), P.NPC_VILLAGER),
    ("탐조 연구원", dict(hat=P.HAT_BUCKET, hatColor=0xFF5C7C3A, vest=0xFF5C7C3A,
                        prop=P.PROP_BOOK, glasses=True),
     (0xFF2E2620, 0xFF7A9E4F, 0xFF5C7C3A, 0xFF6B5A48, 0xFF9AA3AD), P.NPC_PROFESSOR),
    ("숲 레인저", dict(hat=P.HAT_CAP, hatColor=0xFF4F7D3F, vest=0xFF4F7D3F,
                      prop=P.PROP_BINOCS, body=P.BODY_TALL),
     (0xFF3F3A2E, 0xFFC9B47E, 0xFFA99460, 0xFF5D6470, 0xFF8A5A33), P.NPC_VILLAGER),
    ("학예사", dict(hairStyle=P.HAIR_SWEEP, vest=0xFF9F7FC8, prop=P.PROP_BOOK, glasses=True),
     (0xFF26221E, 0xFF8FB8E8, 0xFF6F97C8, 0xFF33383F, 0xFF8A7360), P.NPC_SHOP),
]


def main():
    outdir = sys.argv[1] if len(sys.argv) > 1 else "/tmp/cast"
    os.makedirs(outdir, exist_ok=True)
    looks, cast = parse_roster()
    print(f"looks={len(looks)} cast={len(cast)}")
    assert len(cast) == 66, f"expected 66, got {len(cast)}"

    font = ImageFont.truetype(FONT, 17)
    scale, pad = 4, 2
    phases = [0.12, 0.55]
    cell = P.SIZE + pad
    label_w = 300

    rows = []
    for i, c in enumerate(cast):
        base = looks[c["look"]]
        kind = KINDS[c["kind"]]
        look = build_look(base, c["args"], kind)
        seed = ((jhash(c["id"]) & 0x7FFFFFFF) % 1000) / 1000
        flavor = flavor_of(look.prop, kind)
        frames = [P.render(P.FRONT, P.npc_pose(kind, ph, flavor, seed), look) for ph in phases]
        rows.append((f"{i+1:02d} {c['region']} {c['name']}", frames))
    for name, args, cols, kind in DOCENTS:
        hair, top, top2, pants, pack = cols
        pal = P.Pal(hair=hair, hair2=P.shade(hair, 0.75),
                    skin=0xFFFFD9B0 if name != "등대지기" else 0xFFF5B885,
                    skin2=0xFFE8B88C if name != "등대지기" else 0xFFE09A6E,
                    top=top, top2=top2, pants=pants, pants2=P.shade(pants, 0.75),
                    shoe=0xFF3A3A44, line=0xFF33241C, pack=pack, pack2=P.shade(pack, 0.75),
                    eye=0xFF2E2620, blush=0xFFF2A58C)
        vest = args.get("vest")
        gear = P.Gear(vest=vest, vestDark=P.shade(vest or 0, 0.75)) if vest else None
        look = P.Look(pal, gear=gear, glasses=args.get("glasses", False),
                      body=args.get("body", P.BODY_STANDARD),
                      hairStyle=args.get("hairStyle", P.HAIR_SHORT),
                      beard=args.get("beard", P.BEARD_NONE),
                      hat=args.get("hat", P.HAT_NONE), hatColor=args.get("hatColor", 0),
                      prop=args.get("prop", P.PROP_NONE),
                      wrinkles=args.get("wrinkles", False))
        seed = ((jhash("docent:" + name) & 0x7FFFFFFF) % 1000) / 1000
        flavor = flavor_of(look.prop, kind)
        frames = [P.render(P.FRONT, P.npc_pose(kind, ph, flavor, seed), look) for ph in phases]
        rows.append((f"D {name}", frames))

    w = label_w + len(phases) * cell * scale
    h = len(rows) * cell * scale
    img = Image.new("RGBA", (w, h), (150, 200, 146, 255))
    d = ImageDraw.Draw(img)
    for ry, (label, frames) in enumerate(rows):
        y = ry * cell * scale
        d.text((8, y + cell * scale // 2 - 10), label, font=font, fill=(40, 36, 28, 255))
        for cx, bmp in enumerate(frames):
            im = to_img(bmp).resize((bmp.w * scale, bmp.h * scale), Image.NEAREST)
            img.alpha_composite(im, (label_w + cx * cell * scale, y))
    out = os.path.join(outdir, "cast.png")
    img.save(out)
    print("saved ->", out, img.size)


if __name__ == "__main__":
    main()
