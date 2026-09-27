#!/usr/bin/env python3
"""dialog_audit 분석기 — 패널 크기 대비 실제 콘텐츠(글자·버튼)가 차지하는 비율을 잰다.

사용: python3 tools/preview/dialog_audit_analyze.py [events.tsv]
출력: 프레임별 패널 크기(dp) · 세로 밴드 사용률 · 내부 최대 빈 띠(dp) · 글자 면적 비율

콘텐츠 판정 규칙:
  - 패널: 불투명 FILL (r)rect/path 중 면적 최대 (화면 8~90%)
  - 콘텐츠: 패널보다 **나중에** 그려진(=패널 위에 실제로 보이는) 이벤트 중
      · 글자(alpha>=150)
      · 비트맵(스프라이트)
      · 카드/버튼급 FILL 사각형(높이>=10dp, 패널 면적 80% 미만)
      · 지름 10dp 이상 원
    스테이크(테두리·구분선·광택)는 장식이므로 제외.
"""
import sys

TSV = sys.argv[1] if len(sys.argv) > 1 else "tools/preview/out/audit/events.tsv"
DENSITY = 2.6          # 감사 렌더 밀도
DEF_SCREEN = 2560 * 1440   # screen 메타가 없을 때의 기본 화면 크기
BAND_DP = 4.0          # 세로 밴드 해상도

frames = {}            # name -> list of events (기록 순서 유지)
screen = {}            # name -> (w, h) — 'screen' 메타 이벤트
with open(TSV) as f:
    for line in f:
        parts = line.rstrip("\n").split("\t")
        if len(parts) < 12:
            continue
        name, kind = parts[0], parts[1]
        l, t, r, b = map(float, parts[2:6])
        if kind == "screen":
            screen[name] = (int(r), int(b))
            continue
        color = int(parts[6], 16)
        alpha = int(parts[7])
        style = parts[8]
        shader = parts[9] == "true"
        tsize = float(parts[10])
        text = parts[11]
        frames.setdefault(name, []).append(dict(
            kind=kind, l=l, t=t, r=r, b=b, color=color, alpha=alpha,
            style=style, shader=shader, tsize=tsize, text=text))

def dp(px):
    return px / DENSITY

def area(e):
    return max(0.0, e["r"] - e["l"]) * max(0.0, e["b"] - e["t"])

def inside(e, p, tol=6.0):
    return (e["l"] >= p["l"] - tol and e["t"] >= p["t"] - tol and
            e["r"] <= p["r"] + tol and e["b"] <= p["b"] + tol)

results = []
for name, evs in frames.items():
    sw, sh = screen.get(name, (2560, 1440))
    SCREEN = sw * sh
    # ---- 패널 후보: 불투명 FILL 사각형/둥근사각형/패스, 화면의 8~90% ----
    # (비트맵은 배경 스프라이트일 수 있으므로 제외 — 패널은 항상 벡터 도형)
    cands = [e for e in evs if e["kind"] in ("rrect", "rect", "path")
             and e["style"] == "FILL" and e["alpha"] >= 200
             and 0.08 * SCREEN < area(e) < 0.90 * SCREEN]
    if not cands:
        continue
    pi = max(range(len(evs)), key=lambda i: area(evs[i]) if evs[i] in cands else -1)
    panel = evs[pi]
    pw, ph = dp(panel["r"] - panel["l"]), dp(panel["b"] - panel["t"])

    # ---- 콘텐츠: 패널 위에 그려진 것만 ----
    content = []
    for e in evs[pi + 1:]:
        if not inside(e, panel):
            continue
        if e["kind"] == "text":
            if e["text"] and e["alpha"] >= 150:
                content.append(e)
        elif e["kind"] == "bitmap":
            content.append(e)
        elif e["kind"] == "circle":
            if dp(e["r"] - e["l"]) >= 10:
                content.append(e)
        else:  # rect / rrect / path
            if e["style"] != "FILL" or e["alpha"] < 70:
                continue
            h = dp(e["b"] - e["t"])
            if h >= 10 and area(e) < 0.80 * area(panel):
                content.append(e)

    if not content:
        continue
    texts = [e for e in content if e["kind"] == "text"]

    # ---- 세로 밴드 사용률 & 내부 최대 빈 띠 ----
    nbands = int(ph / BAND_DP) + 1
    used = [False] * nbands
    for e in content:
        y0 = int(max(0, dp(e["t"] - panel["t"])) / BAND_DP)
        y1 = int(min(ph, dp(e["b"] - panel["t"])) / BAND_DP)
        for i in range(y0, min(nbands, y1 + 1)):
            used[i] = True
    first = next((i for i in range(nbands) if used[i]), 0)
    last = next((i for i in range(nbands - 1, -1, -1) if used[i]), nbands - 1)
    used_span = last - first + 1
    used_pct = 100.0 * sum(used) / max(1, nbands)
    gap, best = 0, 0
    for i in range(first, last + 1):
        gap = 0 if used[i] else gap + 1
        best = max(best, gap)
    max_inner_gap_dp = best * BAND_DP
    top_margin = dp(min(e["t"] for e in content) - panel["t"])
    bot_margin = dp(panel["b"] - max(e["b"] for e in content))

    # ---- 글자 면적 비율 (그림자 제외된 본문 글자) ----
    ta = sum(area(e) for e in texts)
    text_area_pct = 100.0 * ta / area(panel)

    # 본문 줄 수 (세로 위치로 그룹)
    ys = sorted(e["t"] for e in texts)
    lines = 0
    prev = -1e9
    for y in ys:
        if y - prev > DENSITY * 6:
            lines += 1
        prev = y

    results.append(dict(
        name=name, pw=pw, ph=ph, lines=lines,
        used_pct=used_pct, gap=max_inner_gap_dp,
        tm=top_margin, bm=bot_margin, tpct=text_area_pct,
        span_pct=100.0 * used_span / nbands))

results.sort(key=lambda r: (r["used_pct"], -r["gap"]))
hdr = (f"{'frame':<22}{'panel(dp)':>12}{'txt lines':>10}{'V-used%':>9}"
       f"{'inner-gap':>10}{'top/bot(dp)':>14}{'text-area%':>11}")
print(hdr)
print("-" * len(hdr))
for r in results:
    print(f"{r['name']:<22}{r['pw']:>6.0f}x{r['ph']:<5.0f}{r['lines']:>10}"
          f"{r['used_pct']:>8.0f}%{r['gap']:>8.0f}dp"
          f"{r['tm']:>7.0f}/{r['bm']:<6.0f}{r['tpct']:>10.1f}%")
