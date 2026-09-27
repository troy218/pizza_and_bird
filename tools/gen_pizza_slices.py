#!/usr/bin/env python3
"""피자 '한 조각' SVG 20종을 생성한다.

출력: app/src/main/assets/pizza_slices/pizza_slice_<id>.svg   (id = Pizzas.ALL 인덱스 0..19)

피자 종류마다 색이 다른 통째 피자 아이콘(Assets.pizzaArts)과 달리, 이쪽은
**잘린 조각 하나**를 크게 보여 주는 일러스트다. 가방 목록·굽기 메뉴·HUD 간식 버튼처럼
"피자 한 판"보다 "한 조각"이 어울리는 자리에 쓴다.

⚠️ assets/ui/ 에 넣지 않는다 — SvgIllustrations.preloadUiIcons() 가 ui/ 아래를
   부팅 때 전부 래스터화하므로, 128px 20장이 부팅마다 실려 올라간다.
   이 조각들은 첫 사용 때 한 장씩 만들어지고 그 뒤로 캐시된다.

색은 Data.kt의 PizzaDef(baseColor / topColorA / topColorB)에서 그대로 가져오므로
도감·아이콘과 항상 같은 팔레트를 쓴다 (도우는 계열별 공통 색).

SvgIllustrations.kt의 미니 렌더러가 지원하는 도형만 사용한다:
  rect / circle / ellipse / line / polygon / polyline
  fill / stroke / stroke-width / opacity  (경로·그라데이션·변환 없음)

용법:
  python3 tools/gen_pizza_slices.py              # SVG 20종 생성
  python3 tools/gen_pizza_slices.py --preview    # + 검수용 접시 이미지(art/preview)
"""

import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "app" / "src" / "main" / "assets" / "pizza_slices"
PREVIEW_DIR = ROOT / "art" / "preview"

SIZE = 64
# 미니 렌더러(SvgIllustrations)는 viewBox 크기 그대로 비트맵을 만든다.
# 설계는 64 좌표계에서 하지만 래스터는 2배(128)로 뽑아 두면 가방 목록처럼
# 60dp(기기 180px)로 크게 그려도 계단이 보이지 않는다 (wood_fired_oven.svg 와 같은 해상도).
K = 2

# ---------------------------------------------------------------------------
# 기하 — 부채꼴(조각) 정의
#
#   꼭짓점(tip)이 위, 크러스트(arc)가 아래인 삼각 조각.
#   호의 중심 C 를 꼭짓점 근처(위)에 두면 먼 곳의 호가 완만하게 휘어
#   "피자에서 잘라 낸 한 조각" 모양이 된다.
#   각도는 아래(+y) 방향을 0° 로 두고 좌우로 spread 만큼 벌린다.
# ---------------------------------------------------------------------------
CX, CY = 32.0, 7.0          # 호 중심 (조각 꼭짓점보다 살짝 위)

OUTLINE_R = 52.0            # 검은 외곽선 (실루엣)
OUTLINE_SPREAD = 30.5       # 외곽선은 본체보다 살짝 넓게 → 테두리가 고르게 남는다
OUTLINE_TIP_Y = 5.5         # 꼭짓점이 뭉툭해지지 않게 본체 꼭짓점과 가까이 둔다

CRUST_OUTER_R = 50.0        # 크러스트 바깥 호
CRUST_SPREAD = 28.0
SAUCE_TIP_Y = 9.0           # 소스/치즈 부채꼴의 꼭짓점 (크러스트 안쪽)
CHEESE_TIP_Y = 13.0
CHEESE_SPREAD = 25.5
CHEESE_HI_TIP_Y = 16.5
CHEESE_HI_SPREAD = 22.5

# 계열별 크러스트 두께 — 화덕피자는 얇고 바삭, 일반 피자는 도톰하다
CRUST_INNER_R = {"R": 42.5, "O": 45.5}
SAUCE_RIM = 5.0             # 소스 가장자리 - 치즈 가장자리
CHEESE_HI_INSET = 6.5

ARC_STEPS = 26              # 호를 몇 개의 선분으로 쪼갤지 (많을수록 매끈)


def polar(r, deg):
    """아래(+y)를 0° 로 두는 극좌표 → 직교좌표."""
    a = math.radians(deg)
    return (CX + r * math.sin(a), CY + r * math.cos(a))


def fmt(p):
    """설계 좌표(64)를 래스터 좌표(128)로 뽑는다."""
    return f"{p[0] * K:.2f},{p[1] * K:.2f}"


def arc(r, spread, steps=ARC_STEPS):
    """-spread → +spread 구간의 호를 시계 반대 방향(왼쪽→오른쪽)으로 샘플링."""
    return [polar(r, -spread + 2 * spread * i / (steps - 1)) for i in range(steps)]


def arc_rev(r, spread, steps=ARC_STEPS):
    return list(reversed(arc(r, spread, steps)))


def poly(points):
    return " ".join(fmt(p) for p in points)


def wedge(tip_y, r, spread):
    """꼭짓점 + 호 로 이루어진 부채꼴 점 목록."""
    return [(CX, tip_y)] + arc(r, spread)


def band(r_in, r_out, spread):
    """두 호 사이의 띠(크러스트) 점 목록."""
    return arc(r_out, spread) + arc_rev(r_in, spread)


# ---------------------------------------------------------------------------
# 색 — Data.kt 의 PizzaDef 팔레트를 그대로 옮긴 것
#   (id, 파일명, baseColor, topColorA, topColorB, 계열 R=일반 / O=화덕)
# ---------------------------------------------------------------------------
PIZZAS = [
    (0,  "치즈",           "#F7CE5B", "#F2B63C", "#E8A75C", "R"),
    (1,  "버섯",           "#F7CE5B", "#B8926A", "#8A6A4A", "R"),
    (2,  "불고기",         "#F2C24E", "#8A4A2E", "#6FAE57", "R"),
    (3,  "페퍼로니",       "#F7CE5B", "#C8392B", "#A32E22", "R"),
    (4,  "고구마",         "#F2B84A", "#B8702C", "#FFF0A0", "R"),
    (5,  "콤비네이션",     "#F7CE5B", "#C8392B", "#5E9E4A", "R"),
    (6,  "마르게리타",     "#D9503F", "#FDF6E8", "#4F8F3F", "O"),
    (7,  "마리나라",       "#C94A3A", "#EFE2BC", "#5C8F3F", "O"),
    (8,  "콰트로 포르마지", "#F5E3A3", "#6B7FA3", "#E8A75C", "O"),
    (9,  "고르곤졸라",     "#F2E6C0", "#7A8BB0", "#E8B923", "O"),
    (10, "디아볼라",       "#D9503F", "#8F2B1E", "#F7CE5B", "O"),
    (11, "루꼴라 프로슈토", "#F5E3A3", "#E88A8A", "#4F8F3F", "O"),
    (12, "춘천 닭갈비",    "#D9503F", "#8F3A22", "#6FAE57", "O"),
    (13, "강릉 감자 옹심이", "#F5E3A3", "#D9B36B", "#EDE0C0", "O"),
    (14, "속초 오징어",    "#FDF0DC", "#E8A0A8", "#B06A78", "O"),
    (15, "전주 콩나물 비빔", "#F2D06B", "#FDF6E8", "#C8392B", "O"),
    (16, "대구 납작 치즈", "#F7E3A8", "#E8A75C", "#D98E3A", "O"),
    (17, "광주 상추 육전", "#E8D8A0", "#8A5A2E", "#6FAE57", "O"),
    (18, "부산 어묵 꼬치", "#F0DCB0", "#C89A5E", "#8A5A3A", "O"),
    (19, "제주 흑돼지",    "#E0A878", "#7A4226", "#5E8F4A", "O"),
]

# 계열별 도우 색 (Assets.buildIcons 의 pizza / pizzaOven 팔레트와 같은 톤)
CRUST = {
    "R": ("#E8A75C", "#F2C078", "#D18F4A"),   # 기본, 밝은 면, 그늘
    "O": ("#E0B070", "#EDC293", "#B87A45"),
}
OUTLINE_COLOR = "#6B431F"
SAUCE_COLOR = "#D8453A"
CHAR_COLOR = "#5A3A2A"

# 토핑 배치 (치즈 부채꼴에 대한 상대 좌표 — 크기가 달라도 같이 커진다)
#   (반지름 비율, 각도 비율, 지름 배율, 색 A/B)
TOPPINGS = [
    (0.48, 0.00, 5.0, "A"),
    (0.70, -0.42, 4.3, "A"),
    (0.70, 0.42, 4.3, "B"),
    (0.83, 0.00, 3.8, "B"),
    (0.86, -0.62, 3.2, "A"),
    (0.86, 0.62, 3.2, "B"),
]
# 치즈 윤기 (타원 두 개 — 토핑 아래에 깔린다)
SHEEN = [(0.58, -0.50, 3.2, 1.5), (0.62, 0.45, 2.6, 1.2)]
# 화덕피자 크러스트의 그을음 자리 (각도, 반지름, 지름)
CHAR_SPOTS = [(-20.0, 47.75, 1.9), (0.0, 47.75, 2.4), (20.0, 47.75, 1.9)]


def shade(hex_color, k):
    """k>1 밝게, k<1 어둡게."""
    h = hex_color.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4))
    if k >= 1.0:
        t = min(k - 1.0, 1.0)
        r, g, b = (int(c + (255 - c) * t) for c in (r, g, b))
    else:
        r, g, b = (int(c * k) for c in (r, g, b))
    return "#%02X%02X%02X" % (
        max(0, min(255, r)), max(0, min(255, g)), max(0, min(255, b))
    )


def build_svg(pid, name, base, top_a, top_b, kind):
    crust, crust_hi, crust_dark = CRUST[kind]
    oven = kind == "O"

    crust_inner = CRUST_INNER_R[kind]
    sauce_r = crust_inner
    cheese_r = sauce_r - SAUCE_RIM
    cheese_hi_r = cheese_r - CHEESE_HI_INSET
    scale = cheese_r / 37.5                       # 기준 크기(일반 피자) 대비 배율
    top_k = scale * (1.12 if oven else 1.0)       # 화덕피자는 토핑이 큼직하다

    cheese_lit = shade(base, 1.16)
    cheese_dim = shade(base, 0.86)

    o = []
    add = o.append

    def n(v):
        """설계 좌표 1개를 래스터 좌표로."""
        return f"{v * K:.2f}"

    def dot(x, y, r=None, extra=""):
        """(x, y) 또는 (x, y, r) 을 래스터 좌표의 circle 속성으로."""
        s = f'cx="{n(x)}" cy="{n(y)}"'
        if r is not None:
            s += f' r="{n(r)}"'
        return s + extra

    add(f'<svg viewBox="0 0 {SIZE * K} {SIZE * K}">')
    add(f'  <!-- 피자 한 조각 · {name} (id {pid}) -->')

    # 1) 실루엣 — 진한 외곽선 역할 (맨 아래에 깔고 나머지를 안쪽에 겹쳐 그린다)
    silhouette = [(CX, OUTLINE_TIP_Y)] + arc(OUTLINE_R, OUTLINE_SPREAD)
    add(f'  <polygon points="{poly(silhouette)}" fill="{OUTLINE_COLOR}"/>')

    # 2) 크러스트 — 도톰한 띠 + 안쪽 하이라이트
    add(f'  <polygon points="{poly(band(crust_inner, CRUST_OUTER_R, CRUST_SPREAD))}" fill="{crust}"/>')
    hl_in = crust_inner + 1.4
    hl_out = min(crust_inner + 1.4 + (CRUST_OUTER_R - crust_inner) * 0.52, CRUST_OUTER_R - 0.6)
    add(f'  <polygon points="{poly(band(hl_in, hl_out, CRUST_SPREAD))}" fill="{crust_hi}" opacity="0.55"/>')
    add(f'  <polygon points="{poly(band(CRUST_OUTER_R - 1.6, CRUST_OUTER_R, CRUST_SPREAD))}" fill="{crust_dark}" opacity="0.7"/>')

    # 3) 화덕피자는 불에 그을린 자국 (얇은 도우의 특징)
    if oven:
        for deg, r, rad in CHAR_SPOTS:
            x, y = polar(r, deg)
            add(f'  <circle {dot(x, y, rad)} fill="{CHAR_COLOR}" opacity="0.5"/>')

    # 4) 토마토 소스
    add(f'  <polygon points="{poly(wedge(SAUCE_TIP_Y, sauce_r, CRUST_SPREAD))}" fill="{SAUCE_COLOR}"/>')

    # 5) 치즈 (+ 위쪽 윤기)
    add(f'  <polygon points="{poly(wedge(CHEESE_TIP_Y, cheese_r, CHEESE_SPREAD))}" fill="{base}"/>')
    add(
        f'  <polygon points="{poly(wedge(CHEESE_HI_TIP_Y, cheese_hi_r, CHEESE_HI_SPREAD))}"'
        f' fill="{cheese_lit}" opacity="0.45"/>'
    )

    # 6) 치즈 윤기 (길게 늘어난 치즈 결)
    for rf, af, rx, ry in SHEEN:
        x, y = polar(cheese_r * rf, CHEESE_SPREAD * af)
        add(
            f'  <ellipse cx="{n(x)}" cy="{n(y)}" rx="{n(rx * scale)}" ry="{n(ry * scale)}"'
            f' fill="{cheese_lit}" opacity="0.5"/>'
        )
    for rf, af, rx, ry in [(0.90, -0.30, 2.4, 1.0), (0.92, 0.34, 2.0, 0.9)]:
        x, y = polar(cheese_r * rf, CHEESE_SPREAD * af)
        add(
            f'  <ellipse cx="{n(x)}" cy="{n(y)}" rx="{n(rx * scale)}" ry="{n(ry * scale)}"'
            f' fill="{cheese_dim}" opacity="0.4"/>'
        )

    # 7) 토핑
    for rf, af, rad, which in TOPPINGS:
        col = top_a if which == "A" else top_b
        x, y = polar(cheese_r * rf, CHEESE_SPREAD * af)
        r = rad * top_k
        add(
            f'  <circle {dot(x, y, r)} fill="{col}"'
            f' stroke="{shade(col, 0.72)}" stroke-width="{n(1.0)}"/>'
        )
        # 토핑 윤기 (왼쪽 위 작은 점)
        add(
            f'  <circle {dot(x - r * 0.3, y - r * 0.32, r * 0.26)}'
            f' fill="{shade(col, 1.35)}" opacity="0.75"/>'
        )

    add("</svg>")
    return "\n".join(o) + "\n"


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    made = []
    for pid, name, base, top_a, top_b, kind in PIZZAS:
        svg = build_svg(pid, name, base, top_a, top_b, kind)
        path = OUT_DIR / f"pizza_slice_{pid}.svg"
        path.write_text(svg, encoding="utf-8")
        made.append(path)
        print(f"[생성] app/src/main/assets/pizza_slices/pizza_slice_{pid}.svg  ({name})")
    print(f"\n총 {len(made)}종 생성 완료")

    if "--preview" in sys.argv:
        preview()


# ---------------------------------------------------------------------------
# 검수용 미리보기 — 게임의 미니 렌더러와 같은 순서·같은 도형 해석으로 PIL 렌더
# ---------------------------------------------------------------------------
def preview(zoom=3):
    import re
    import xml.etree.ElementTree as ET
    from PIL import Image, ImageDraw, ImageFont

    def col(s, opacity):
        s = (s or "#000000").strip()
        if s == "none":
            return None
        h = s.lstrip("#")
        if len(h) == 3:
            h = "".join(ch * 2 for ch in h)
        r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4))
        return (r, g, b, int(255 * opacity))

    def nums(v):
        return [float(x) for x in re.findall(r"[-+]?(?:\d*\.)?\d+", v or "")]

    def draw(svg_path, img, ox, oy):
        dr = ImageDraw.Draw(img, "RGBA")
        root = ET.parse(svg_path).getroot()
        vb = [float(x) for x in root.get("viewBox", f"0 0 {SIZE} {SIZE}").split()]
        scale = (SIZE * zoom) / (vb[2] or SIZE)   # viewBox → 미리보기 칸 크기
        for el in root.iter():
            tag = el.tag.split("}")[-1]
            a = el.attrib
            op = float(a.get("opacity", "1"))
            fo = op * float(a.get("fill-opacity", "1"))
            so = op * float(a.get("stroke-opacity", "1"))
            fill = col(a.get("fill"), fo)
            stroke = col(a.get("stroke"), so) if a.get("stroke") else None
            sw = float(a.get("stroke-width", "1")) * scale
            zx = lambda x: ox + x * scale
            zy = lambda y: oy + y * scale
            if tag == "circle":
                cx, cy, r = nums(a.get("cx", "0")), nums(a.get("cy", "0")), nums(a.get("r", "0"))
                cx, cy, r = cx[0], cy[0], r[0]
                bb = [zx(cx - r), zy(cy - r), zx(cx + r), zy(cy + r)]
                if fill:
                    dr.ellipse(bb, fill=fill)
                if stroke:
                    dr.ellipse(bb, outline=stroke, width=max(1, int(sw)))
            elif tag == "ellipse":
                cx = nums(a.get("cx", "0"))[0]
                cy = nums(a.get("cy", "0"))[0]
                rx = nums(a.get("rx", "0"))[0]
                ry = nums(a.get("ry", "0"))[0]
                bb = [zx(cx - rx), zy(cy - ry), zx(cx + rx), zy(cy + ry)]
                if fill:
                    dr.ellipse(bb, fill=fill)
                if stroke:
                    dr.ellipse(bb, outline=stroke, width=max(1, int(sw)))
            elif tag == "polygon":
                p = nums(a.get("points", ""))
                pts = [(zx(p[i]), zy(p[i + 1])) for i in range(0, len(p) - 1, 2)]
                if len(pts) >= 3:
                    if fill:
                        dr.polygon(pts, fill=fill)
                    if stroke:
                        dr.line(pts + [pts[0]], fill=stroke, width=max(1, int(sw)), joint="curve")
            elif tag == "rect":
                p = [a.get("x", "0"), a.get("y", "0"), a.get("width", "0"), a.get("height", "0")]
                x, y, w, h = (nums(v)[0] for v in p)
                bb = [zx(x), zy(y), zx(x + w), zy(y + h)]
                if fill:
                    dr.rectangle(bb, fill=fill)
                if stroke:
                    dr.rectangle(bb, outline=stroke, width=max(1, int(sw)))

    cell = SIZE * zoom
    cols_n = 5
    rows_n = (len(PIZZAS) + cols_n - 1) // cols_n
    pad, lab = 14, 26
    sheet = Image.new("RGBA",
                      (cols_n * (cell + pad) + pad, rows_n * (cell + pad + lab) + pad),
                      (46, 42, 62, 255))
    dr = ImageDraw.Draw(sheet, "RGBA")
    try:
        font = ImageFont.truetype("DejaVuSans.ttf", 13)
    except OSError:
        font = ImageFont.load_default()
    for i, (pid, name, *_rest) in enumerate(PIZZAS):
        r, c = divmod(i, cols_n)
        x0 = pad + c * (cell + pad)
        y0 = pad + r * (cell + pad + lab)
        dr.rectangle([x0 - 4, y0 - 4, x0 + cell + 3, y0 + cell + 3], outline=(72, 66, 96, 255))
        draw(OUT_DIR / f"pizza_slice_{pid}.svg", sheet, x0, y0)
        dr.text((x0, y0 + cell + 6), f"{pid} {name}", font=font, fill=(240, 235, 221, 255))
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    out = PREVIEW_DIR / "pizza_slices.png"
    sheet.save(out)
    print(f"[미리보기] {out}")


if __name__ == "__main__":
    main()
