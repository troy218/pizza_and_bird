#!/usr/bin/env python3
"""tools/preview/src/r_stub.kt 재생성 — res/raw·res/drawable 목록을 그대로 반영한다.

프리뷰(JVM 스크린샷·스모크 테스트)는 aapt 를 쓰지 않고 이 스텁으로 R 클래스를 흉내낸다.
소리나 그림 파일을 추가하면 이 스크립트를 다시 돌려야 프리뷰 파이프라인이 컴파일된다.

    python3 tools/preview/gen_r_stub.py            # 파일 갱신
    python3 tools/preview/gen_r_stub.py --check    # 어긋나면 종료 코드 1 (CI 용)
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app" / "src" / "main" / "res"
OUT = Path(__file__).resolve().parent / "src" / "r_stub.kt"

HEADER = '''@file:Suppress("unused", "MayBeConstant", "UNUSED_PARAMETER")

/**
 * tools/preview — R 리소스 ID 스텁 (aapt 대체). res/raw·drawable의 파일 목록 기준 자동 생성.
 * drawable은 Context.drawableRegistry에 이름을 등록해 VectorArtDrawable 렌더링에 쓰인다.
 * (갱신: python3 tools/preview/gen_r_stub.py — res/raw·res/drawable 목록이 바뀌면 다시 돌릴 것)
 */
package com.pizzaandbird.game
object R {
'''


def build() -> str:
    raw = sorted(p.stem for p in (RES / "raw").iterdir() if p.is_file())
    draws = sorted(p.stem for p in (RES / "drawable").iterdir() if p.is_file())
    lines = [HEADER, "    object raw {"]
    for i, name in enumerate(raw, start=1):
        lines.append(f"        val {name} = {i}")
    lines.append("    }")
    lines.append("")
    lines.append("    object drawable {")
    lines.append("        private var next = 10000")
    lines.append("        private fun art(name: String): Int {")
    lines.append("            val id = next++")
    lines.append("            android.content.Context.drawableRegistry[id] = name")
    lines.append("            return id")
    lines.append("        }")
    lines.append("")
    for name in draws:
        lines.append(f'        val {name} = art("{name}")')
    lines.append("    }")
    lines.append("}")
    return "\n".join(lines) + "\n"


def main() -> int:
    text = build()
    if "--check" in sys.argv:
        cur = OUT.read_text(encoding="utf-8") if OUT.exists() else ""
        if cur != text:
            print("✗ tools/preview/src/r_stub.kt 가 res 목록과 다릅니다 — `python3 tools/preview/gen_r_stub.py` 를 실행하세요")
            return 1
        print("✓ r_stub.kt 최신")
        return 0
    OUT.write_text(text, encoding="utf-8")
    print(f"✓ {OUT.relative_to(ROOT)} 갱신 ({text.count('val ')}개 리소스)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
