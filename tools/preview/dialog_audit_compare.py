#!/usr/bin/env python3
"""before/after 감사 프레임을 패널 단위로 잘라 한 장의 비교 시트로 합친다.

사용: python3 tools/preview/dialog_audit_compare.py \
         tools/preview/out/audit_before tools/preview/out/audit out.png
"""
import os
import sys
from PIL import Image, ImageDraw, ImageFont

BEFORE_DIR, AFTER_DIR, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
DENSITY = 2.6
FONT = "tools/preview/fonts/NotoSansKR-Bold.ttf"
FONT_REG = "tools/preview/fonts/NotoSansKR-Regular.ttf"

# (케이스, 한글 제목, 표시 폭)
CASES = [
    ("gear_pick_body_0",   "장비 선택 — 보유 0개 (빈 상태)", 720),
    ("gear_pick_lens_1",   "장비 선택 — 렌즈 1개", 720),
    ("gear_pick_lens_5_p2","장비 선택 — 마지막 페이지(1개)", 720),
    ("gearbag_1",          "장비 가방", 780),
    ("house_style",        "인테리어 선택", 700),
    ("dlg_kid_1line",      "대화상자 — 한 줄 대사", 1060),
    ("dlg_quest",          "대화상자 — 세 줄 대사 (버튼 겹침 수정)", 1060),
    ("dlg_long_8line",     "대화상자 — 여덟 줄 대사 (버튼 겹침 수정)", 1060),
    ("dlg13_villager_4line","대화상자 — 글자 크게(1.3×) 넉 줄", 1060),
]

def panel_rect(tsv, name):
    """프레임에서 가장 큰 불투명 벡터 FILL = 패널"""
    best, best_a = None, -1
    sw = sh = None
    with open(tsv) as f:
        for line in f:
            p = line.rstrip("\n").split("\t")
            if len(p) < 12 or p[0] != name:
                continue
            if p[1] == "screen":
                sw, sh = int(float(p[4])), int(float(p[5]))
                continue
            if p[1] not in ("rrect", "rect", "path") or p[8] != "FILL":
                continue
            if int(p[7]) < 200:
                continue
            l, t, r, b = map(float, p[2:6])
            a = (r - l) * (b - t)
            if sw and not (0.08 * sw * sh < a < 0.90 * sw * sh):
                continue
            if a > best_a:
                best_a, best = a, (l, t, r, b)
    return best

def crop(png, rect, pad=70):
    im = Image.open(png)
    l, t, r, b = rect
    l = max(0, int(l - pad)); t = max(0, int(t - pad))
    r = min(im.width, int(r + pad)); b = min(im.height, int(b + pad))
    return im.crop((l, t, r, b))

font_title = ImageFont.truetype(FONT, 34)
font_tag = ImageFont.truetype(FONT, 28)
font_small = ImageFont.truetype(FONT_REG, 24)

CELL_W = 1080          # 한 쪽 셀 폭
GAP = 24
PAD = 28

rows = []
for name, title, disp_w in CASES:
    pb = panel_rect(os.path.join(BEFORE_DIR, "events.tsv"), name)
    pa = panel_rect(os.path.join(AFTER_DIR, "events.tsv"), name)
    if pb is None or pa is None:
        print("skip", name)
        continue
    ib = crop(os.path.join(BEFORE_DIR, name + ".png"), pb)
    ia = crop(os.path.join(AFTER_DIR, name + ".png"), pa)
    hb = max(1, round(ib.height * disp_w / ib.width))
    ha = max(1, round(ia.height * disp_w / ia.width))
    ib = ib.resize((disp_w, hb), Image.LANCZOS)
    ia = ia.resize((disp_w, ha), Image.LANCZOS)
    rows.append((title, ib, ia))

row_h = [max(b.height, a.height) + 96 for _, b, a in rows]
W = PAD * 2 + CELL_W * 2 + GAP
H = PAD * 2 + sum(row_h) + GAP * (len(rows) - 1)
sheet = Image.new("RGB", (W, H), (46, 40, 58))
d = ImageDraw.Draw(sheet)

y = PAD
for (title, ib, ia), rh in zip(rows, row_h):
    d.text((PAD, y + 8), title, font=font_title, fill=(248, 239, 220))
    # 이전
    x0 = PAD
    d.rectangle([x0 - 6, y + 56, x0 + ib.width + 6, y + 60 + ib.height], outline=(226, 87, 76), width=3)
    sheet.paste(ib, (x0, y + 60))
    d.text((x0, y + 64 + ib.height), "이전", font=font_tag, fill=(226, 87, 76))
    # 이후
    x1 = PAD + CELL_W + GAP
    d.rectangle([x1 - 6, y + 56, x1 + ia.width + 6, y + 60 + ia.height], outline=(111, 186, 107), width=3)
    sheet.paste(ia, (x1, y + 60))
    d.text((x1, y + 64 + ia.height), "개선 후", font=font_tag, fill=(111, 186, 107))
    y += rh + GAP

sheet.save(OUT)
print("saved", OUT, sheet.size)
