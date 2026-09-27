#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pizza and Bird : 피자와 새 — 내장 글꼴(서브셋) 생성기 🖋

게임에 넣는 두 글꼴을 원본에서 내려받아 **필요한 글자만 남긴 서브셋**으로 만든다.

    app/src/main/assets/font/display_jua.ttf       제목·버튼 (Jua, 배달의민족 주아)
    app/src/main/assets/font/body_gowundodum.ttf   본문·설명 (Gowun Dodum, 고운돋움)

둘 다 SIL Open Font License 1.1 (예약 글꼴 이름 없음) — 서브셋 재배포가 가능하다.
라이선스 전문은 app/src/main/assets/font/OFL.txt 에 함께 넣어 배포한다.

남기는 글자
  · Jua 가 가진 한글 음절 전체(2,367자 = KS X 1001 2,350자 + α)
    → 게임 대사·지역명·새 이름(공식 598종 전부)이 모두 들어간다. 검증까지 한다.
  · 게임 소스/데이터에 등장하는 모든 문자, ASCII, 문장부호, 화살표, ₩, 낱자(ㄱㄴㄷ)
  · 없는 글자는 안드로이드가 시스템 글꼴로 자동 대체하므로 두부(□)가 되지 않는다.

사용:
    pip3 install fonttools
    python3 tools/build_fonts.py            # 원본 다운로드 → 서브셋 → assets 갱신
"""
import glob
import io
import os
import re
import subprocess
import sys
import tarfile
import tempfile
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DST = os.path.join(ROOT, "app", "src", "main", "assets", "font")

# 원본 글꼴 — @expo-google-fonts 가 구글 폰트의 TTF 를 그대로 재배포한다(OFL 동봉).
FONTS = [
    # (npm 패키지, 패키지 안 경로, 결과 파일명)
    ("jua", "package/400Regular/Jua_400Regular.ttf", "display_jua.ttf"),
    ("gowun-dodum", "package/400Regular/GowunDodum_400Regular.ttf", "body_gowundodum.ttf"),
]
NPM = "https://registry.npmjs.org/@expo-google-fonts/{pkg}/-/{pkg}-{ver}.tgz"


def fetch(pkg: str) -> bytes:
    """npm 레지스트리에서 최신 tarball 을 받아 온다."""
    import json
    meta = json.load(urllib.request.urlopen(
        f"https://registry.npmjs.org/@expo-google-fonts/{pkg}", timeout=60))
    url = meta["versions"][meta["dist-tags"]["latest"]]["dist"]["tarball"]
    return urllib.request.urlopen(url, timeout=120).read()


def extract(tgz: bytes, member: str, out: str) -> str:
    with tarfile.open(fileobj=io.BytesIO(tgz), mode="r:gz") as t:
        data = t.extractfile(member).read()
    with open(out, "wb") as f:
        f.write(data)
    return out


def game_chars() -> set:
    """게임 소스·리소스·조류 목록에 실제로 등장하는 문자."""
    used = set()
    src = glob.glob(os.path.join(ROOT, "app/src/main/java/com/pizzaandbird/game/*.kt"))
    src += glob.glob(os.path.join(ROOT, "app/src/main/res/values/*.xml"))
    for path in src:
        text = open(path, encoding="utf-8").read()
        for m in re.finditer(r'"((?:\\.|[^"\\])*)"', text):   # 문자열 리터럴만
            used.update(m.group(1))
    birds = os.path.join(ROOT, "한반도_조류_전체목록_2025.txt")
    if os.path.exists(birds):
        used.update(open(birds, encoding="utf-8").read())
    return {c for c in used if ord(c) > 0x1F}


def main() -> int:
    try:
        from fontTools.ttLib import TTFont
    except ImportError:
        print("fonttools 가 필요합니다:  pip3 install fonttools")
        return 1

    os.makedirs(DST, exist_ok=True)
    tmp = tempfile.mkdtemp(prefix="pnb-font-")
    originals = []
    for pkg, member, name in FONTS:
        print(f">> {pkg} 내려받는 중...")
        originals.append(extract(fetch(pkg), member, os.path.join(tmp, name)))

    used = game_chars()
    # 한글 범위는 제목 글꼴(Jua)이 가진 음절로 맞춘다 — 두 글꼴의 지원 범위를 같게.
    hangul = {cp for cp in TTFont(originals[0], lazy=True).getBestCmap()
              if 0xAC00 <= cp <= 0xD7A3}
    keep = set(hangul)
    keep |= {ord(c) for c in used}
    keep |= set(range(0x20, 0x7F))        # ASCII
    keep |= set(range(0xA0, 0x100))       # Latin-1
    keep |= set(range(0x2000, 0x2070))    # … ‘ ’ · — 등 문장부호
    keep |= set(range(0x20A0, 0x20C0))    # ₩ 등 통화기호
    keep |= set(range(0x2190, 0x21A0))    # ← ↑ → ↓
    keep |= set(range(0x3000, 0x3040))    # 「」 등 CJK 부호
    keep |= set(range(0x3130, 0x3190))    # 낱자 ㄱㄴㄷ (줄바꿈 금칙 문자)
    keep |= set(range(0xFF01, 0xFF61))    # 전각 기호
    unicodes = ",".join(f"U+{cp:04X}" for cp in sorted(keep))

    for src, (_, _, name) in zip(originals, FONTS):
        out = os.path.join(DST, name)
        subprocess.run([sys.executable, "-m", "fontTools.subset", src,
                        f"--unicodes={unicodes}", f"--output-file={out}",
                        "--no-hinting", "--desubroutinize", "--name-IDs=*",
                        "--drop-tables+=DSIG", "--layout-features=*",
                        "--recalc-bounds"], check=True)
        cmap = set(TTFont(out, lazy=True).getBestCmap())
        miss = sorted({c for c in used if 0xAC00 <= ord(c) <= 0xD7A3 and ord(c) not in cmap})
        print(f"{name:22} {os.path.getsize(src) / 1e6:5.2f}MB -> "
              f"{os.path.getsize(out) / 1e6:5.2f}MB  "
              f"한글 {len([c for c in cmap if 0xAC00 <= c <= 0xD7A3])}자  "
              f"게임 한글 누락 {''.join(miss) or '없음'}")

    print(f"\n완료 — {DST}")
    print("라이선스(OFL.txt)도 함께 배포해야 한다.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
