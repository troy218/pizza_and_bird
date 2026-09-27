#!/usr/bin/env python3
"""Pizza and Bird — 번들 글꼴 서브셋 생성기
============================================

게임은 완전 오프라인이라 글꼴을 assets/fonts/ 에 번들한다.
원본 글꼴(주아/고운 돋움/고운 바탕/가경)은 한글 완성자 전체 + 한자 등으로
개당 수 MB라, 게임 소스에 실제로 등장하는 글자만 남겨 서브셋한다.
(빠진 글자가 혹시 나오면 안드로이드가 시스템 글꼴로 자동 대체한다.)

사용법
------
    pip install fonttools brotli
    python3 tools/fonts/subset_fonts.py

원본 TTF를 이 스크립트와 같은 폴더(또는 --src 로 지정한 폴더)에 두고 돌리면
app/src/main/assets/fonts/ 에 서브셋 결과를 덮어쓴다.
원본은 구글 폰트 저장소(https://github.com/google/fonts)의 각 ofl/ 폴더에서
받을 수 있으며, 라이선스 전문은 app/src/main/assets/fonts/OFL_LICENSE.txt 참고.

대사·새 이름 등 게임 텍스트를 추가했다면 이 스크립트를 다시 실행해
서브셋에 새 글자가 포함되도록 한다.
"""

import argparse
import os
import subprocess
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC_MAIN = os.path.join(REPO_ROOT, "app", "src", "main")
OUT_DIR = os.path.join(SRC_MAIN, "assets", "fonts")

# (원본 파일, 저장될 이름)
FONTS = [
    ("Jua-Regular.ttf", "jua.ttf"),
    ("GowunDodum-Regular.ttf", "gowun_dodum.ttf"),
    ("GowunBatang-Regular.ttf", "gowun_batang.ttf"),
    ("Gaegu-Regular.ttf", "gaegu.ttf"),
    ("Gaegu-Bold.ttf", "gaegu_bold.ttf"),
]


def collect_chars():
    """게임 소스(.kt/.xml)에 등장하는 모든 문자를 모은다."""
    chars = set()
    for root, _, files in os.walk(SRC_MAIN):
        for fn in files:
            if fn.endswith((".kt", ".xml")):
                with open(os.path.join(root, fn), encoding="utf-8") as f:
                    chars.update(f.read())
    return chars


def build_codepoints(chars):
    cps = set(ord(c) for c in chars if ord(c) >= 0x20)
    # 게임 텍스트에 없어도 안전하게 남길 범위
    ranges = [
        (0x0020, 0x007E),  # 기본 라틴
        (0x00A0, 0x00FF),  # 라틴-1 보충 (°, × 등)
        (0x2000, 0x206F),  # 일반 구두점 (… ‘ ’ “ ” – —)
        (0x20A0, 0x20CF),  # 화폐 기호 (₩)
        (0x2190, 0x21FF),  # 화살표 (→ ←)
        (0x2460, 0x24FF),  # 원 문자 (①)
        (0x25A0, 0x25FF),  # 도형 (● ○ ■ □ ▲)
        (0x2600, 0x26FF),  # 기타 기호 (★ ☀)
        (0x2700, 0x27BF),  # 딩벳 (✓ ✕)
        (0x2B00, 0x2BFF),  # 기타 기호와 화살표 (⭐)
        (0x3000, 0x303F),  # CJK 구두점 (。 · 「 」)
        (0x3130, 0x318F),  # 한글 호환 자모
        (0xFF00, 0xFFEF),  # 전각/반각 형태
    ]
    for lo, hi in ranges:
        cps.update(range(lo, hi + 1))
    return cps


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--src", default=os.path.dirname(os.path.abspath(__file__)),
                    help="원본 TTF가 있는 폴더 (기본: 스크립트와 같은 폴더)")
    args = ap.parse_args()

    cps = build_codepoints(collect_chars())
    hangul = sum(1 for c in cps if 0xAC00 <= c <= 0xD7A3)
    print(f"코드포인트 {len(cps)}자 (한글 완성자 {hangul}자 포함)")
    unicodes = ",".join("U+%04X" % c for c in sorted(cps))

    os.makedirs(OUT_DIR, exist_ok=True)
    for src, dst in FONTS:
        src_path = os.path.join(args.src, src)
        if not os.path.exists(src_path):
            print(f"건너뜀: {src} (원본 없음 — {args.src} 에 넣어주세요)")
            continue
        out_path = os.path.join(OUT_DIR, dst)
        cmd = [
            sys.executable, "-m", "fontTools.subset", src_path,
            "--output-file=" + out_path,
            "--unicodes=" + unicodes,
            "--layout-features=*",
            "--name-IDs=*",
            "--no-hinting",
            "--desubroutinize",
        ]
        r = subprocess.run(cmd)
        if r.returncode != 0:
            print(f"실패: {src}")
            sys.exit(r.returncode)
        print("%-26s -> %-18s %.2f MB" % (src, dst, os.path.getsize(out_path) / 1e6))
    print("완료:", OUT_DIR)


if __name__ == "__main__":
    main()
