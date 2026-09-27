#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
카메라 라인업 엑셀 → 게임 가격 밸런스 점검 도구
===============================================

`docs/reference/카메라_라인업_가격정리_2026.xlsx`(실제 시장 라인업·국내 정가)에서
**"무엇이 가격을 올리는가"** 를 뽑아내고, `Cameras.kt`의 게임 장비와 비교한다.

    python3 tools/camera_price_model.py            # 점검 리포트 출력
    python3 tools/camera_price_model.py --write    # docs/reference/camera_price_model.md 로 저장

게임 장비의 가격을 정하는 방법 (이 도구의 핵심 아이디어)
-------------------------------------------------------
게임 장비와 스펙이 가장 비슷한 **실제 모델(유사 실기 3개)** 을 찾아
그 정가의 거리 가중 중앙값을 **시장 참조가** 로 삼는다.

    시장 참조가(실제 원) → 카테고리별 환산 배율 → 게임 권장가(게임 원)

환산 배율은 카테고리(컴팩트/바디/렌즈) 전체의 가격 수준을 유지하도록
현재가와 참조가의 **중앙값 비율**로 정한다.
즉 이 도구는 가격 총량을 바꾸지 않고 **항목 사이의 상대가격만 시장 비율로 재배열**한다.

> ※ 실제 가격을 그대로 옮기는 게 목적이 아니다.
>   "풀프레임은 APS-C의 몇 배?", "600mm F4는 200-600mm의 몇 배?" 같은
>   **대략적인 관계**만 게임에 반영하기 위한 참고 도구다.
"""
from __future__ import annotations

import argparse
import math
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
XLSX = ROOT / "docs" / "reference" / "카메라_라인업_가격정리_2026.xlsx"
CAMERAS_KT = ROOT / "app" / "src" / "main" / "java" / "com" / "pizzaandbird" / "game" / "Cameras.kt"
REPORT = ROOT / "docs" / "reference" / "camera_price_model.md"

# 게임 스펙 → 실기 스펙 단위 환산
#   게임 손떨림 보정(IBIS)은 0~4 스케일, 실기는 CIPA 스톱(0~8.5)
IBIS_TO_STOPS = 2.0
#   엑셀은 중형(GFX·핫셀블라드)을 제외한다 → 풀프레임 대비 배율을 외부 가정으로 둔다
#     바디: GFX100 계열 ≈ 동급 풀프레임의 2배 / 렌즈: GF 55mm F1.7(약 350만) ≈ FF 50mm F1.4(199만) 의 1.8배
MF_PREMIUM = 2.0
MF_LENS_PREMIUM = 1.8
#   유사 실기 매칭에서 이 거리를 넘으면 "비슷한 실기 없음(참고만)"
MATCH_FAR = 1.00

# ===========================================================================
# 1. 엑셀 파싱
# ===========================================================================

def _rows(wb, sheet: str, header_row: int = 3) -> list:
    ws = wb[sheet]
    data = [list(r) for r in ws.iter_rows(values_only=True)]
    head = [str(h).strip() if h else f"c{i}" for i, h in enumerate(data[header_row - 1])]
    return [dict(zip(head, r)) for r in data[header_row:] if r[0] is not None]


def _nums(s) -> list:
    return [float(x) for x in re.findall(r"\d+(?:\.\d+)?", str(s).replace(",", ""))]


def parse_bodies(wb) -> list:
    out = []
    for r in _rows(wb, "1. 미러리스 바디"):
        fmt = str(r["센서/포맷"])
        sensor = "FF" if "풀프레임" in fmt else ("APSC" if "APS-C" in fmt else "MFT")
        burst = max(_nums(str(r["최대 연사"]).replace("fps", " ")) or [0.0]) or 10.0
        m = re.search(r"([\d.]+)\s*스톱", str(r["손떨방(IBIS)"]))
        out.append(dict(
            name=f'{r["브랜드"]} {r["모델"]}', sensor=sensor,
            mp=_nums(r["유효화소"])[0] / 100.0,          # "2,420만" → 24.2MP
            burst=burst,
            ibis=float(m.group(1)) if m else 0.0,        # 손떨방 스톱 수
            stacked=1.0 if "적층" in fmt else 0.0,
            weight=float(r["무게(g)"]), price=float(r["정가(원)"])))
    return out


def _crop(mount, name, kind) -> float:
    m, n, k = str(mount), str(name), str(kind)
    if "MFT" in m:
        return 2.0
    if "RF-S" in m:
        return 1.6
    if "후지" in m:
        return 1.5
    if "DX" in n or "DC DN" in n or "APS-C" in k:
        return 1.5
    return 1.0


def parse_lenses(wb) -> list:
    out = []
    for r in _rows(wb, "2. 렌즈"):
        crop = _crop(r["마운트"], r["렌즈명"], r["종류"])
        fl = str(r["초점거리"])
        v = _nums(fl)
        # 후지·MFT 렌즈는 "35mm 환산"으로 적힌 행이 있어 실초점거리로 되돌린다
        v = [x / crop for x in v] if "환산" in fl else v
        wide, tele = (v[0], v[-1]) if len(v) > 1 else (v[0], v[0])
        out.append(dict(
            name=f'{r["브랜드"]} {r["렌즈명"]}', crop=crop, wide=wide, tele=tele,
            eq_tele=tele * crop, ap=_nums(str(r["최대 조리개(F)"]))[0],
            ois=1.0 if "있음" in str(r["손떨방(OIS)"]) else 0.0,
            weight=float(r["무게(g)"]), price=float(r["정가(원)"]),
            zoom=(tele / wide) if wide else 1.0))
    return out


def parse_compacts(wb) -> list:
    out = []
    for r in _rows(wb, "3. 일체형 카메라"):
        sens = str(r["센서 크기"])
        if "풀프레임" in sens:
            sensor = "FF"
        elif "APS-C" in sens:
            sensor = "APSC"
        elif "포서드" in sens:
            sensor = "MFT"
        elif "1인치" in sens or "1.4인치" in sens:
            sensor = "ONE"
        else:
            sensor = "T23"
        v = _nums(str(r["35mm 환산 화각"]))
        wide, tele = (v[0], v[-1]) if len(v) > 1 else (v[0], v[0])
        out.append(dict(
            name=f'{r["브랜드"]} {r["모델"]}', sensor=sensor, wide=wide, tele=tele,
            ap=_nums(str(r["최대 조리개(F)"]))[0],
            stab=0.0 if "없음" in str(r["손떨방"]) else 1.0,
            weight=float(r["무게(g)"]), price=float(r["정가(원)"])))
    return out


def parse_budget(wb) -> list:
    """'4. 예산별 추천' 시트 — 바디/렌즈 예산 배분 감각."""
    ws = wb["4. 예산별 추천"]
    data = [list(r) for r in ws.iter_rows(values_only=True)]
    head = [str(h).strip() if h else f"c{i}" for i, h in enumerate(data[2])]
    return [dict(zip(head, r)) for r in data[3:] if r[0] is not None]


# ===========================================================================
# 2. 유사 실기 매칭 (k-NN)
# ===========================================================================

class Matcher:
    """스펙 벡터가 가장 가까운 실제 모델 k개의 가격을 거리 가중으로 모은다."""

    def __init__(self, items, feats, sds=None):
        self.items = items
        self.feats = feats
        self.vecs = [[f(it) for f in feats] for it in items]
        if sds is None:
            mu = [sum(v[i] for v in self.vecs) / len(self.vecs) for i in range(len(feats))]
            sds = [math.sqrt(sum((v[i] - mu[i]) ** 2 for v in self.vecs) / len(self.vecs))
                   for i in range(len(feats))] or [1.0] * len(feats)
        self.sds = [s if s > 1e-6 else 1.0 for s in sds]

    def _dist(self, a, b) -> float:
        return math.sqrt(sum(((a[i] - b[i]) / self.sds[i]) ** 2 for i in range(len(a))))

    def match(self, target, k: int = 3):
        v = [f(target) for f in self.feats]
        ds = sorted(((self._dist(v, vv), it) for vv, it in zip(self.vecs, self.items)),
                    key=lambda t: t[0])
        near = ds[:k]
        # 거리 가중 (가까울수록 큰 가중)
        wsum = sum(1.0 / (0.05 + d) for d, _ in near)
        price = sum(math.log(it["price"]) / (0.05 + d) for d, it in near) / wsum
        return math.exp(price), near


# ===========================================================================
# 3. Cameras.kt 파싱
# ===========================================================================

def _split_args(text: str) -> list:
    """괄호·따옴표를 고려해 최상위 콤마로 분리."""
    args, depth, cur, i, n = [], 0, [], 0, len(text)
    instr = False
    while i < n:
        ch = text[i]
        if instr:
            cur.append(ch)
            if ch == "\\":
                cur.append(text[i + 1] if i + 1 < n else "")
                i += 2
                continue
            if ch == '"':
                instr = False
            i += 1
            continue
        if ch == '"':
            instr = True
            cur.append(ch)
        elif ch in "([{":
            depth += 1
            cur.append(ch)
        elif ch in ")]}":
            depth -= 1
            cur.append(ch)
        elif ch == "," and depth == 0:
            args.append("".join(cur).strip())
            cur = []
        else:
            cur.append(ch)
        i += 1
    if "".join(cur).strip():
        args.append("".join(cur).strip())
    return args


def _extract_calls(src: str, ctor: str) -> list:
    """생성자 호출(데이터 정의)만 뽑는다. 클래스 선언부는 제외."""
    out, i = [], 0
    while True:
        j = src.find(ctor + "(", i)
        if j < 0:
            return out
        k = j + len(ctor)
        depth = 0
        while k < len(src):
            if src[k] == "(":
                depth += 1
            elif src[k] == ")":
                depth -= 1
                if depth == 0:
                    break
            k += 1
        args = _split_args(src[j + len(ctor) + 1:k])
        # 클래스 선언/생성자 시그니처는 "id: String" 같은 타입 표기가 섞인다
        if args and not any(re.search(r"^\w+\s*:", a) for a in args):
            out.append(args)
        i = k


def _str(a: str) -> str:
    a = a.strip()
    return a[1:-1].replace('\\"', '"') if a.startswith('"') else a


def _num(a: str) -> float:
    v = re.findall(r"-?\d+(?:\.\d+)?", a.strip().replace("f", "").replace("F", ""))
    return float(v[0]) if v else 0.0


def _bool(a: str) -> bool:
    return a.strip() == "true"


def parse_game_gear(path: Path):
    src = path.read_text(encoding="utf-8")
    compacts, bodies, lenses, tcs, accs = [], [], [], [], []

    # id, brand, name, grade, price, weight, desc, sensor, wide, tele, apW, apT,
    # mp, af, burst, stab, weather, sharp, look[, luck]
    for a in _extract_calls(src, "CompactCam"):
        compacts.append(dict(id=_str(a[0]), name=f"{_str(a[1])} {_str(a[2])}",
                             price=int(_num(a[4])), weight=_num(a[5]),
                             sensor=_str(a[7]).split(".")[-1], wide=_num(a[8]), tele=_num(a[9]),
                             ap=_num(a[10]), mp=_num(a[12]), af=_num(a[13]), burst=_num(a[14]),
                             stab=_num(a[15]), wp=_bool(a[16]), sharp=_num(a[17])))

    # id, brand, name, grade, price, weight, desc, sensor, mount, mp, af, burst,
    # ibis, weather, birdAf, look[, luck]
    for a in _extract_calls(src, "CamBody"):
        bodies.append(dict(id=_str(a[0]), name=f"{_str(a[1])} {_str(a[2])}",
                           price=int(_num(a[4])), weight=_num(a[5]),
                           sensor=_str(a[7]).split(".")[-1], mp=_num(a[9]), af=_num(a[10]),
                           burst=_num(a[11]), ibis=_num(a[12]), wp=_bool(a[13]), bird=_bool(a[14])))

    # id, brand, name, grade, price, weight, desc, mounts, coverage, wide, tele,
    # apW, apT, os, sharp, afMod, tcOk, macro, barrelLen, barrelDia, barrelCol, hood[, luck]
    for a in _extract_calls(src, "CamLens"):
        lenses.append(dict(id=_str(a[0]), name=f"{_str(a[1])} {_str(a[2])}",
                           price=int(_num(a[4])), weight=_num(a[5]),
                           coverage=_str(a[8]).split(".")[-1], wide=_num(a[9]), tele=_num(a[10]),
                           ap=_num(a[11]), os=_num(a[13]), sharp=_num(a[14]), macro=_bool(a[17])))

    for a in _extract_calls(src, "TeleConv"):
        tcs.append(dict(id=_str(a[0]), name=f"{_str(a[1])} {_str(a[2])}", price=int(_num(a[3])),
                        weight=_num(a[4]), mul=_num(a[6])))
    for a in _extract_calls(src, "CamAccessory"):
        accs.append(dict(id=_str(a[0]), name=f"{_str(a[1])} {_str(a[2])}", price=int(_num(a[3])),
                         weight=_num(a[4])))
    return compacts, bodies, lenses, tcs, accs


# 게임 센서 enum → 매칭 그룹
SENSOR_GROUP = {"T23": "T23", "T17": "ONE", "ONE": "ONE", "M43": "MFT",
                "APSC": "APSC", "FF": "FF", "MF": "MF"}


def game_compact_features(c: dict) -> dict:
    return dict(sensor=SENSOR_GROUP.get(c["sensor"], "ONE"), tele=c["tele"], wide=c["wide"],
                ap=max(c["ap"], 1.0), weight=c["weight"], mp=c["mp"], burst=c["burst"],
                ibis=c["stab"] * IBIS_TO_STOPS, stab=c["stab"])


def game_body_features(b: dict) -> dict:
    return dict(sensor=SENSOR_GROUP.get(b["sensor"], "APSC"), mp=b["mp"],
                burst=max(b["burst"], 3.0), ibis=b["ibis"] * IBIS_TO_STOPS,
                stacked=1.0 if b["burst"] >= 20 else 0.0, weight=b["weight"])


def game_lens_features(l: dict) -> dict:
    return dict(tele=l["tele"], wide=l["wide"], ap=max(l["ap"], 1.0), coverage=l["coverage"],
                ois=1.0 if l["os"] >= 1.0 else 0.0,
                zoom=max(l["tele"] / max(l["wide"], 1.0), 1.0), weight=l["weight"])


# ===========================================================================
# 4. 리포트
# ===========================================================================

def med(v) -> float:
    v = sorted(v)
    return v[len(v) // 2] if v else float("nan")


def spec_line(kind: str, it: dict) -> str:
    """리포트 표에 함께 보여줄 스펙 한 줄."""
    if kind == "렌즈":
        fl = f"{it['wide']:.0f}-{it['tele']:.0f}mm" if it["wide"] != it["tele"] else f"{it['tele']:.0f}mm"
        return f"{fl} F{it['ap']:.1f} · {it['weight']:.0f}g"
    if kind == "바디":
        return f"{it['sensor']} {it['mp']:.0f}MP {it['burst']:.0f}연사 IBIS{it['ibis']:.1f} · {it['weight']:.0f}g"
    fl = f"{it['wide']:.0f}-{it['tele']:.0f}mm" if it["wide"] != it["tele"] else f"{it['tele']:.0f}mm"
    return f"{it['sensor']} {fl} F{it['ap']:.1f} · {it['weight']:.0f}g"


def _round_krw(v: float) -> int:
    """보기 좋은 가격으로 반올림."""
    if v < 100_000:
        step = 5_000
    elif v < 500_000:
        step = 10_000
    elif v < 2_000_000:
        step = 50_000
    else:
        step = 100_000
    return int(round(v / step) * step)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--write", action="store_true", help="리포트를 docs/reference 에 저장")
    ap.add_argument("--xlsx", default=str(XLSX))
    args = ap.parse_args()

    try:
        import openpyxl
    except ImportError:
        sys.exit("openpyxl 이 필요합니다: pip install openpyxl")

    wb = openpyxl.load_workbook(args.xlsx, data_only=True)
    bodies, lenses, compacts = parse_bodies(wb), parse_lenses(wb), parse_compacts(wb)
    g_compact, g_body, g_lens, g_tc, g_acc = parse_game_gear(CAMERAS_KT)

    out = []
    w = out.append
    w("# 카메라 가격 모델 · 게임 밸런스 점검\n")
    w("> 원본 자료: `docs/reference/카메라_라인업_가격정리_2026.xlsx` (2026-09 기준 · 국내 정가 · DSLR·중형 제외)")
    w("> 점검 도구: `tools/camera_price_model.py`\n")
    w("실제 가격·라인업을 그대로 옮기지 않는다. **항목 사이의 관계(배율)** 만 게임에 반영한다.\n")

    # ---------------------------------------------------------------- 시장 관측
    w("## 1. 실제 시장에서 관측되는 관계\n")
    by_sensor = {}
    for b in bodies:
        by_sensor.setdefault(b["sensor"], []).append(b["price"])
    w("**① 센서가 가격을 지배한다 (바디)**\n")
    w("| 센서 | 모델 수 | 최저 | 중앙값 | 최고 |")
    w("|---|---:|---:|---:|---:|")
    for s in ("APSC", "MFT", "FF"):
        v = by_sensor[s]
        w(f"| {s} | {len(v)} | {min(v)/10000:.0f}만 | {med(v)/10000:.0f}만 | {max(v)/10000:.0f}만 |")
    w("")
    w(f"- 같은 급에서 **풀프레임 ≈ APS-C × {med(by_sensor['FF'])/med(by_sensor['APSC']):.1f}**"
      f" (최저가끼리 {min(by_sensor['FF'])/min(by_sensor['APSC']):.1f}배, 최고가끼리 {max(by_sensor['FF'])/max(by_sensor['APSC']):.1f}배)")
    apsc_i = [b["price"] for b in bodies if b["sensor"] == "APSC" and b["ibis"] > 0]
    apsc_n = [b["price"] for b in bodies if b["sensor"] == "APSC" and b["ibis"] == 0]
    w(f"- APS-C 에서 **IBIS 있음 ≈ ×{med(apsc_i)/med(apsc_n):.2f}** (없는 모델 대비)")
    w(f"- 화소수·연사보다 **센서 + 바디 급(무게·방진방적·적층형)** 의 영향이 훨씬 크다"
      f" (바디 가격 ≈ 무게^1.7, R=0.84)\n")

    def fcls(t):
        if t < 70:
            return "①~70mm"
        if t < 200:
            return "②70-200"
        if t < 400:
            return "③200-400"
        return "④400mm+"

    def acls(a):
        if a <= 2.0:
            return "F1.2~2.0"
        if a <= 2.9:
            return "F2.8"
        if a <= 4.6:
            return "F4~4.5"
        return "F5.6~"

    grid = {}
    for l in lenses:
        grid.setdefault((fcls(l["eq_tele"]), acls(l["ap"])), []).append(l["price"])
    w("**② 렌즈는 '초점거리 × 조리개' 격자가 가격을 정한다** (중앙값, 단위 만원)\n")
    w("| | F1.2~2.0 | F2.8 | F4~4.5 | F5.6~ |")
    w("|---|---:|---:|---:|---:|")
    for f in ("①~70mm", "②70-200", "③200-400", "④400mm+"):
        row = []
        for a in ("F1.2~2.0", "F2.8", "F4~4.5", "F5.6~"):
            v = grid.get((f, a))
            row.append(f"{med(v)/10000:.0f}만" if v else "—")
        w(f"| {f} | " + " | ".join(row) + " |")
    w("")
    w(f"- **망원으로 갈수록 급격히 비싸진다** — 400mm+ 줌 ≈ 70-200 F2.8 의 "
      f"{med(grid[('④400mm+','F5.6~')])/med(grid[('②70-200','F2.8')]):.1f}배")
    w(f"- **초망원 대구경 단렌즈는 다른 세계** — 400mm+ F2.8 급 ≈ 같은 화각 줌의 "
      f"{med(grid[('④400mm+','F2.8')])/med(grid[('④400mm+','F5.6~')]):.1f}배")
    w(f"- **조리개 1스톱(예 F4→F2.8)은 대략 ×1.5~2** (같은 화각대에서)")
    w(f"- **렌즈 가격 ≈ 무게^0.9** (상관 R=0.90) — 결국 '유리 덩어리' 값\n")

    w("**③ 바디와 렌즈의 예산 배분** (엑셀 '예산별 추천' 시트)\n")
    w("| 총예산 | 예시 조합 |")
    w("|---|---|")
    for r in parse_budget(wb)[:7]:
        if "일체형" in str(r.get("총예산 구간", "")):
            continue
        w(f"| {r['총예산 구간']} | {str(r['추천 조합 (바디 + 렌즈)'])[:60]} |")
    w("")
    cheap_l = min(l["price"] for l in lenses)
    cheap_b = min(b["price"] for b in bodies)
    top_l = max(l["price"] for l in lenses)
    top_b = max(b["price"] for b in bodies)
    w(f"- **입문**: 최저가 렌즈 {cheap_l/10000:.0f}만 / 최저가 바디 {cheap_b/10000:.0f}만 = **×{cheap_l/cheap_b:.2f}** (렌즈가 바디의 절반 이하)")
    w(f"- **최상급**: 최고가 렌즈 {top_l/10000:.0f}만 / 최고가 바디 {top_b/10000:.0f}만 = **×{top_l/top_b:.2f}** (렌즈가 바디를 넘어선다)")
    w("- 즉 **'바디 급을 낮추고 렌즈에 투자'** 구간이 실제 시장에도 그대로 존재한다\n")

    w("**④ 일체형(컴팩트) — '똑딱이는 싸다'는 절반만 맞다**\n")
    w("| 모델 | 센서 | 환산 화각 | 정가 |")
    w("|---|---|---|---:|")
    for c in compacts:
        w(f"| {c['name']} | {c['sensor']} | {c['wide']:.0f}-{c['tele']:.0f}mm | {c['price']/10000:.0f}만 |")
    w("")
    w("- 1/2.3\" 슈퍼줌(125배)도 140만, 1인치 24-600mm 브리지는 220만,")
    w("- APS-C 프리미엄(GR IV·X100VI) 195~239만, 풀프레임 컴팩트(RX1R III) **649만 = 플래그십급**")
    w("- 센서 크기가 가격을 지배하고, **망원 슈퍼줌은 '싼 대신 화질을 양보'** 하는 자리다\n")

    # ------------------------------------------------------------ 유사 실기 매칭
    w("## 2. 게임 장비 → 유사 실기 참조가\n")
    w("게임 장비와 스펙이 가장 가까운 실제 모델 3개의 정가를 거리 가중 평균한 값.")
    w(f"가장 가까운 실기와의 거리가 {MATCH_FAR} 이상이면 ⚠️(비슷한 실기 없음 — 참고만) 로 표시.\n")

    body_matcher = Matcher(bodies, [
        lambda d: math.log(max(d["mp"], 4.0)),
        lambda d: math.log(max(d["burst"], 3.0)),
        lambda d: d["ibis"] / 4.0,
        lambda d: math.log(max(d["weight"], 200.0)),
    ])
    lens_matcher = Matcher(lenses, [
        lambda d: math.log(max(d["tele"], 10.0)),
        lambda d: math.log(max(d["ap"], 0.9)),
        lambda d: math.log(max(d["weight"], 60.0)),
        lambda d: math.log(max(d["zoom"], 1.0)),
    ])
    compact_matcher = Matcher(compacts, [
        lambda d: math.log(max(d["tele"], 10.0)),
        lambda d: math.log(max(d["ap"], 0.9)),
        lambda d: math.log(max(d["weight"], 100.0)),
    ])

    def sensor_sub(matcher, group, fallback_map):
        """같은 센서 그룹의 실기만 후보로 쓰고, 없으면 옆 그룹으로 넘어간다."""
        for g in fallback_map.get(group, [group]):
            items = [it for it in matcher.items if it["sensor"] == g]
            if items:
                return Matcher(items, matcher.feats, matcher.sds)
        return matcher

    BODY_FALLBACK = {"APSC": ["APSC", "MFT", "FF"], "MFT": ["MFT", "APSC", "FF"],
                     "FF": ["FF", "APSC", "MFT"], "MF": ["FF", "APSC"]}
    COMPACT_FALLBACK = {"T23": ["T23", "ONE", "MFT"], "ONE": ["ONE", "T23", "MFT"],
                        "MFT": ["MFT", "ONE", "APSC"], "APSC": ["APSC", "MFT", "FF"],
                        "FF": ["FF", "APSC", "MFT"]}

    tables = {}
    for label, items, fn, matcher, fallback in [
        ("컴팩트", g_compact, game_compact_features, compact_matcher, COMPACT_FALLBACK),
        ("바디", g_body, game_body_features, body_matcher, BODY_FALLBACK),
        ("렌즈", g_lens, game_lens_features, lens_matcher, None),
    ]:
        rows = []
        for it in items:
            feat = fn(it)
            it["spec"] = spec_line(label, it)
            m = sensor_sub(matcher, feat["sensor"], fallback) if fallback else matcher
            ref, near = m.match(feat, k=3)
            # 엑셀에 중형이 없다 → 풀프레임 유사 모델에 외부 가정 배율을 적용
            if feat.get("sensor") == "MF":
                ref *= MF_PREMIUM
            if feat.get("coverage") == "MF":
                ref *= MF_LENS_PREMIUM
            far = near[0][0] > MATCH_FAR
            rows.append((it, ref, near, far))
        # 카테고리 환산 배율: 현재가/참조가 의 중앙값 (가격 총량 유지, 신뢰도 낮은 항목 제외)
        pairs = [(it["price"], r) for it, r, _, far in rows if it["price"] > 0 and not far]
        scale = med([math.log(p) - math.log(r) for p, r in pairs]) if pairs else 0.0
        scale = math.exp(scale)
        w(f"### {label} — 게임 환산 배율 ×{scale:.4f}\n")
        w("| 장비 | 스펙 | 현재가 | 유사 실기(정가) | 시장 참조가 | 권장가 | 현재/권장 |")
        w("|---|---|---:|---|---:|---:|---:|")
        for it, ref, near, far in sorted(rows, key=lambda r: r[0]["price"]):
            want = _round_krw(ref * scale)
            cur = it["price"]
            ratio = (cur / want) if want else 0.0
            flag = " 🔺" if ratio >= 1.25 else (" 🔻" if 0 < ratio <= 0.8 else "")
            near_txt = ", ".join(f"{n['name']} ({n['price']/10000:.0f}만)" for _, n in near[:2])
            w(f"| {it['name']}{' ⚠️' if far else ''} | {it.get('spec','')} | {cur:,} | {near_txt} | "
              f"{ref/10000:,.0f}만 | **{want:,}** | ×{ratio:.2f}{flag} |")
        w("")
        tables[label] = (rows, scale)

    w("> 🔺=시장 감각보다 비싼 항목, 🔻=싼 항목. 권장가는 '보기 좋은 금액'으로 반올림한 값.\n")

    text = "\n".join(out) + "\n"
    print(text)
    if args.write:
        REPORT.write_text(text, encoding="utf-8")
        print(f"→ 저장: {REPORT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
