#!/usr/bin/env python3
"""
Pizza and Bird — 아트 파이프라인 빌드 도구.

`art/svg/*.svg` 마스터 파일의 <symbol id="art_*"> 요소들을
1) Android VectorDrawable XML (`app/src/main/res/drawable/<id>.xml`)
2) 미리보기 갤러리 (`art/gallery.html`)
로 변환한다.

지원하는 SVG 서브셋 (마스터 작성 규칙):
  - <symbol id="art_xxx" viewBox="0 0 W H"> ... </symbol>  (id는 [a-z0-9_])
  - 도형: <rect> (rx 지원), <circle>, <ellipse>, <polygon>, <polyline>, <path>
  - path 명령: M/m L/l H/h V/v C/c Q/q Z  (축정렬 픽셀아트 위주)
  - 속성: fill(#rgb/#rrggbb/#rrggbbaa/none), fill-opacity, opacity
  - 그룹: <g transform="translate(x,y) | scale(s) | scale(sx,sy)"> (중첩 가능)
  - stroke 미지원 (마스터에서 사용 금지)

용법:  python3 tools/build_art.py
"""
import math
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SVG_DIR = ROOT / "art" / "svg"
OUT_DRAWABLE = ROOT / "app" / "src" / "main" / "res" / "drawable"
GALLERY = ROOT / "art" / "gallery.html"

NS = "{http://www.w3.org/2000/svg}"
ID_RE = re.compile(r"^art_[a-z0-9_]+$")
NUM = r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?"

# ---------------------------------------------------------------------------
# 색상
# ---------------------------------------------------------------------------

def parse_color(fill: str, fill_opacity: float, group_opacity: float):
    """SVG fill -> '#AARRGGBB' or None (투명)."""
    if fill is None:
        fill = "#000"
    fill = fill.strip()
    if fill == "none":
        return None
    if not fill.startswith("#"):
        raise ValueError(f"지원하지 않는 fill: {fill}")
    h = fill[1:]
    if len(h) == 3:
        r, g, b = (int(ch * 2, 16) for ch in h)
        a = 255
    elif len(h) == 4:
        r, g, b, a = (int(ch * 2, 16) for ch in h)
    elif len(h) == 6:
        r, g, b = int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)
        a = 255
    elif len(h) == 8:
        r, g, b, a = (int(h[i:i + 2], 16) for i in (0, 2, 4, 6))
    else:
        raise ValueError(f"지원하지 않는 색상: {fill}")
    alpha = a * fill_opacity * group_opacity
    ai = max(0, min(255, round(alpha)))
    if ai == 0:
        return None
    return f"#{ai:02X}{r:02X}{g:02X}{b:02X}"


def fmt(v: float) -> str:
    if abs(v) < 1e-9:
        v = 0.0
    s = f"{v:.3f}".rstrip("0").rstrip(".")
    return s if s not in ("-0", "") else "0"

# ---------------------------------------------------------------------------
# path 파서 (M L H V C Q Z, 절대/상대)
# ---------------------------------------------------------------------------

def tokenize_path(d: str):
    return re.findall(r"[MmLlHhVvCcQqAaZz]|" + NUM, d)


def parse_path(d: str):
    """토큰 스트림 -> 세그먼트 리스트 [(cmd, [절대좌표...])] (M/L/C/Q/Z 만 반환)."""
    toks = tokenize_path(d)
    i = 0
    cmd = None
    segs = []
    x = y = 0.0
    sx = sy = 0.0  # 서브패스 시작점
    arity = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "Q": 4, "A": 7}

    def num():
        nonlocal i
        v = float(toks[i]); i += 1
        return v

    while i < len(toks):
        if re.fullmatch(r"[A-Za-z]", toks[i]):
            cmd = toks[i]; i += 1
        if cmd is None:
            raise ValueError("path가 명령 없이 시작됨")
        c = cmd.upper()
        rel = cmd.islower()
        if c == "Z":
            segs.append(("Z", []))
            x, y = sx, sy
            cmd = None
            continue
        if c not in arity:
            raise ValueError(f"지원하지 않는 path 명령: {cmd}")
        first = True
        while i < len(toks) and not re.fullmatch(r"[A-Za-z]", toks[i]):
            if c == "M":
                nx, ny = num(), num()
                if rel:
                    nx += x; ny += y
                x, y = nx, ny
                if first:
                    segs.append(("M", [x, y]))
                    sx, sy = x, y
                    c = "L"  # 이후 연속 좌표는 L
                else:
                    segs.append(("L", [x, y]))
            elif c == "L":
                nx, ny = num(), num()
                if rel:
                    nx += x; ny += y
                x, y = nx, ny
                segs.append(("L", [x, y]))
            elif c == "H":
                nx = num()
                if rel:
                    nx += x
                x = nx
                segs.append(("L", [x, y]))
            elif c == "V":
                ny = num()
                if rel:
                    ny += y
                y = ny
                segs.append(("L", [x, y]))
            elif c == "C":
                vals = [num() for _ in range(6)]
                if rel:
                    for k in range(0, 6, 2):
                        vals[k] += x; vals[k + 1] += y
                x, y = vals[4], vals[5]
                segs.append(("C", vals))
            elif c == "Q":
                vals = [num() for _ in range(4)]
                if rel:
                    for k in range(0, 4, 2):
                        vals[k] += x; vals[k + 1] += y
                x, y = vals[2], vals[3]
                segs.append(("Q", vals))
            elif c == "A":
                vals = [num() for _ in range(7)]
                if rel:
                    vals[5] += x; vals[6] += y
                x, y = vals[5], vals[6]
                segs.append(("A", vals))
            first = False
    return segs


def segs_to_pathdata(segs) -> str:
    out = []
    for cmd, v in segs:
        if cmd == "Z":
            out.append("Z")
        elif cmd == "A":
            # Android VectorDrawable 도 A/a 지원 — 그대로 통과
            out.append("A%s,%s %s %s %s %s,%s" % (
                fmt(v[0]), fmt(v[1]), fmt(v[2]),
                fmt(v[3]), fmt(v[4]), fmt(v[5]), fmt(v[6])))
        else:
            out.append(cmd + ",".join(fmt(n) for n in v))
    return "".join(out)

# ---------------------------------------------------------------------------
# 도형 -> path 세그먼트
# ---------------------------------------------------------------------------

KAPPA = 0.5522847498


def circle_to_segs(cx, cy, rx, ry):
    kx, ky = rx * KAPPA, ry * KAPPA
    return [
        ("M", [cx, cy - ry]),
        ("C", [cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy]),
        ("C", [cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry]),
        ("C", [cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy]),
        ("C", [cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry]),
        ("Z", []),
    ]


def rect_to_segs(x, y, w, h, rx=0.0, ry=0.0):
    if rx <= 0 and ry <= 0:
        return [("M", [x, y]), ("L", [x + w, y]), ("L", [x + w, y + h]),
                ("L", [x, y + h]), ("Z", [])]
    rx = rx or ry
    ry = ry or rx
    rx = min(rx, w / 2); ry = min(ry, h / 2)
    kx, ky = rx * KAPPA, ry * KAPPA
    return [
        ("M", [x + rx, y]),
        ("L", [x + w - rx, y]),
        ("C", [x + w - rx + kx, y, x + w, y + ry - ky, x + w, y + ry]),
        ("L", [x + w, y + h - ry]),
        ("C", [x + w, y + h - ry + ky, x + w - rx + kx, y + h, x + w - rx, y + h]),
        ("L", [x + rx, y + h]),
        ("C", [x + rx - kx, y + h, x, y + h - ry + ky, x, y + h - ry]),
        ("L", [x, y + ry]),
        ("C", [x, y + ry - ky, x + rx - kx, y, x + rx, y]),
        ("Z", []),
    ]


def points_to_segs(points: str, close: bool):
    nums = [float(n) for n in re.findall(NUM, points)]
    pts = list(zip(nums[0::2], nums[1::2]))
    if not pts:
        raise ValueError("빈 points")
    segs = [("M", list(pts[0]))] + [("L", list(p)) for p in pts[1:]]
    if close:
        segs.append(("Z", []))
    return segs


def transform_segs(segs, tx, ty, sx, sy):
    out = []
    for cmd, v in segs:
        if cmd == "A":
            # [rx,ry,rot,laf,sf,x,y] — 반지름은 배율만, 끝점에 평행이동+배율
            out.append(("A", [abs(v[0] * sx), abs(v[1] * sy), v[2], v[3], v[4],
                              v[5] * sx + tx, v[6] * sy + ty]))
        else:
            out.append((cmd, [v[i] * sx + tx if i % 2 == 0 else v[i] * sy + ty
                              for i in range(len(v))]))
    return out

# ---------------------------------------------------------------------------
# symbol 수집
# ---------------------------------------------------------------------------

class PaintPath:
    __slots__ = ("segs", "color")

    def __init__(self, segs, color):
        self.segs = segs
        self.color = color


def collect(node, tx=0.0, ty=0.0, sx=1.0, sy=1.0, opacity=1.0, out=None, errs=None, where=""):
    if out is None:
        out, errs = [], []
    tag = node.tag.replace(NS, "")
    if tag in ("g", "a", "symbol", "svg"):
        t = node.get("transform", "")
        ntx, nty, nsx, nsy = tx, ty, sx, sy
        m = re.fullmatch(r"\s*translate\(\s*(%s)(?:[,\s]+(%s))?\s*\)\s*" % (NUM, NUM), t or "translate(0)")
        applied = False
        if t:
            if m:
                ux = float(m.group(1)); uy = float(m.group(2) or 0)
                ux *= sx; uy *= sy
                ntx, nty = tx + ux, ty + uy
                applied = True
            else:
                m2 = re.fullmatch(r"\s*scale\(\s*(%s)(?:[,\s]+(%s))?\s*\)\s*" % (NUM, NUM), t)
                if m2:
                    kx = float(m2.group(1)); ky = float(m2.group(2) or kx)
                    nsx, nsy = sx * kx, sy * ky
                    ntx, nty = tx * kx, ty * ky
                    applied = True
        if t and not applied:
            errs.append(f"{where}: 미지원 transform '{t}'")
        nop = opacity * float(node.get("opacity", "1"))
        for ch in node:
            collect(ch, ntx, nty, nsx, nsy, nop, out, errs, where)
        return out, errs

    if tag not in ("rect", "circle", "ellipse", "polygon", "polyline", "path"):
        if tag not in ("title", "desc", "metadata"):
            errs.append(f"{where}: 미지원 태그 <{tag}>")
        return out, errs

    fill = node.get("fill")
    fo = float(node.get("fill-opacity", "1"))
    color = parse_color(fill, fo, opacity)
    if node.get("stroke") not in (None, "none"):
        errs.append(f"{where}: stroke는 지원하지 않음")
    if color is None:
        return out, errs

    try:
        if tag == "rect":
            x = float(node.get("x", 0)); y = float(node.get("y", 0))
            w = float(node.get("width", 0)); h = float(node.get("height", 0))
            rx = float(node.get("rx", 0)); ry = float(node.get("ry", 0))
            segs = rect_to_segs(x, y, w, h, rx, ry)
        elif tag == "circle":
            cx = float(node.get("cx", 0)); cy = float(node.get("cy", 0))
            r = float(node.get("r", 0))
            segs = circle_to_segs(cx, cy, r, r)
        elif tag == "ellipse":
            cx = float(node.get("cx", 0)); cy = float(node.get("cy", 0))
            rx = float(node.get("rx", 0)); ry = float(node.get("ry", 0))
            segs = circle_to_segs(cx, cy, rx, ry)
        elif tag == "polygon":
            segs = points_to_segs(node.get("points", ""), True)
        elif tag == "polyline":
            segs = points_to_segs(node.get("points", ""), False)
        else:
            segs = parse_path(node.get("d", ""))
    except ValueError as e:
        errs.append(f"{where}: {e}")
        return out, errs

    out.append(PaintPath(transform_segs(segs, tx, ty, sx, sy), color))
    return out, errs

# ---------------------------------------------------------------------------
# VectorDrawable 출력
# ---------------------------------------------------------------------------

VD_HEADER = ('<?xml version="1.0" encoding="utf-8"?>\n'
             '<!-- 자동 생성: tools/build_art.py (원본: art/svg/{src}.svg) — 직접 수정하지 말 것 -->\n'
             '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
             '    android:width="{w}dp"\n'
             '    android:height="{h}dp"\n'
             '    android:viewportWidth="{vw}"\n'
             '    android:viewportHeight="{vh}">\n')
VD_PATH = '    <path\n        android:fillColor="{color}"\n        android:pathData="{data}"/>\n'


def convert_symbol(sym, src_name):
    sid = sym.get("id", "")
    vb = sym.get("viewBox", "")
    nums = [float(n) for n in re.findall(NUM, vb)]
    if len(nums) != 4:
        raise ValueError(f"{sid}: viewBox 파싱 실패")
    minx, miny, vw, vh = nums
    paths, errs = collect(sym)
    if errs:
        raise ValueError(f"{sid}: " + "; ".join(errs))
    if not paths:
        raise ValueError(f"{sid}: 도형 없음")
    body = []
    for p in paths:
        segs = transform_segs(p.segs, -minx, -miny, 1.0, 1.0)
        body.append(VD_PATH.format(color=p.color, data=segs_to_pathdata(segs)))
    xml = VD_HEADER.format(src=src_name, w=fmt(vw), h=fmt(vh), vw=fmt(vw), vh=fmt(vh))
    xml += "".join(body) + "</vector>\n"
    return xml, len(paths)

# ---------------------------------------------------------------------------
# 메인
# ---------------------------------------------------------------------------

def main():
    svg_files = sorted(SVG_DIR.glob("*.svg"))
    if not svg_files:
        print("art/svg/*.svg 없음", file=sys.stderr)
        return 1
    OUT_DRAWABLE.mkdir(parents=True, exist_ok=True)

    manifest = []  # (id, vw, vh, src, npaths)
    errors = 0
    for f in svg_files:
        try:
            tree = ET.parse(f)
        except ET.ParseError as e:
            print(f"[오류] {f.name}: XML 파싱 실패: {e}", file=sys.stderr)
            errors += 1
            continue
        root = tree.getroot()
        syms = [el for el in root.iter() if el.tag.replace(NS, "") == "symbol"]
        if not syms:
            print(f"[경고] {f.name}: <symbol> 없음")
        for sym in syms:
            sid = sym.get("id", "")
            if not ID_RE.match(sid):
                print(f"[오류] {f.name}: 잘못된 id '{sid}' (art_[a-z0-9_] 규칙)", file=sys.stderr)
                errors += 1
                continue
            try:
                xml, npaths = convert_symbol(sym, f.stem)
            except ValueError as e:
                print(f"[오류] {e}", file=sys.stderr)
                errors += 1
                continue
            out = OUT_DRAWABLE / f"{sid}.xml"
            prev = out.read_text() if out.exists() else None
            if prev != xml:
                out.write_text(xml)
            vb = [float(n) for n in re.findall(NUM, sym.get("viewBox"))]
            manifest.append((sid, vb[2], vb[3], f.stem, npaths))

    # 중복 id 검사
    seen = {}
    dupes = set()
    for sid, *_ in manifest:
        if sid in seen:
            dupes.add(sid)
        seen[sid] = True
    if dupes:
        print(f"[오류] 중복 id: {sorted(dupes)}", file=sys.stderr)
        errors += 1

    # 스테일 durable 정리: 더 이상 없는 art_* 삭제
    valid_ids = {m[0] for m in manifest}
    for old in OUT_DRAWABLE.glob("art_*.xml"):
        if old.stem not in valid_ids:
            old.unlink()
            print(f"[정리] {old.name} 삭제")

    write_gallery(svg_files, manifest)
    print(f"[완료] symbol {len(manifest)}개 -> drawable {len(valid_ids)}개, gallery: {GALLERY}")
    return 1 if errors else 0


def write_gallery(svg_files, manifest):
    """모든 마스터 symbol을 한 페이지에서 미리보기."""
    defs_parts = []
    for f in svg_files:
        txt = f.read_text()
        m = re.search(r"<svg[^>]*>(.*)</svg>", txt, re.S)
        if m:
            defs_parts.append(m.group(1))
    defs = "\n".join(defs_parts)

    sections = {}
    for sid, vw, vh, src, npaths in manifest:
        sections.setdefault(src, []).append((sid, vw, vh, npaths))

    cards = []
    for src, items in sorted(sections.items()):
        cards.append(f'<h2>{src}.svg <span class="cnt">{len(items)}종</span></h2><div class="grid">')
        for sid, vw, vh, npaths in items:
            scale = 2
            w = max(96, min(224, int(vw * scale)))
            h = int(w * vh / vw)
            cards.append(
                f'<figure><svg class="px" width="{w}" height="{h}" '
                f'viewBox="0 0 {fmt(vw)} {fmt(vh)}"><use href="#{sid}"/></svg>'
                f'<figcaption>{sid[4:]}<br><span>{fmt(vw)}×{fmt(vh)} · path {npaths}</span></figcaption></figure>'
            )
        cards.append("</div>")

    html = f"""<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="utf-8">
<title>Pizza and Bird — 벡터 아트 갤러리</title>
<style>
  body {{ background:#1c1a26; color:#F2EBDD; font-family:'Pretendard',system-ui,sans-serif; margin:0; padding:32px; }}
  h1 {{ font-size:26px; margin:0 0 4px; }}
  p.sub {{ color:#9a92b3; margin:0 0 28px; font-size:14px; }}
  h2 {{ font-size:20px; margin:36px 0 14px; border-bottom:2px solid #3a3550; padding-bottom:6px; }}
  h2 .cnt {{ font-size:13px; color:#8fd48a; }}
  .grid {{ display:flex; flex-wrap:wrap; gap:14px; }}
  figure {{ margin:0; background:#26233a; border:1px solid #3a3550; border-radius:10px; padding:12px 12px 8px; text-align:center; }}
  svg.px {{ shape-rendering:crispEdges; image-rendering:pixelated; background:
    repeating-conic-gradient(#2e2b44 0% 25%, #26233a 0% 50%) 0 0 / 16px 16px; border-radius:6px; }}
  figcaption {{ font-size:12px; margin-top:8px; line-height:1.5; }}
  figcaption span {{ color:#8a83a6; font-size:11px; }}
</style>
</head>
<body>
<h1>🍕🐦 Pizza and Bird — 벡터 아트 갤러리</h1>
<p class="sub">art/svg 마스터({len(manifest)}종) → app/src/main/res/drawable (VectorDrawable) 자동 생성물.
재생성: <code>python3 tools/build_art.py</code></p>
<svg width="0" height="0" style="position:absolute">{defs}</svg>
{''.join(cards)}
</body>
</html>"""
    GALLERY.write_text(html)


if __name__ == "__main__":
    sys.exit(main())
