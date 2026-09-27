#!/usr/bin/env python3
"""tools/preview/gen_r_stub.py — 프리뷰용 R 리소스 스텁 생성기.

프리뷰 파이프라인은 aapt 없이 Kotlin 만 컴파일하므로 R 클래스가 필요하다.
손으로 관리하던 tools/preview/src/r_stub.kt 는 새 사운드/그림이 추가될 때마다
낡아서 프리뷰 컴파일이 깨졌다(예: sfx_crow, amb_night 추가 후 compile-failed).

이 스크립트가 app/src/main/res 를 실제로 훑어서 R 스텁을 다시 쓴다.

    python3 tools/preview/gen_r_stub.py

- R.raw.<파일명>          : res/raw 의 mp3/m4a/wav … (1부터 차례로 번호)
- R.drawable.<이름>       : res/drawable 의 xml — Context.drawableRegistry 에 이름을 등록해
                            프리뷰 스텁이 실제 벡터 그림을 그릴 수 있게 한다.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, "app", "src", "main", "res")
OUT = os.path.join(ROOT, "tools", "preview", "src", "r_stub.kt")

AUDIO_EXT = {".mp3", ".m4a", ".wav", ".ogg", ".opus", ".aac", ".flac"}


def resource_names(folder, exts):
    path = os.path.join(RES, folder)
    if not os.path.isdir(path):
        return []
    names = []
    for f in os.listdir(path):
        base, ext = os.path.splitext(f)
        if ext.lower() not in exts:
            continue
        # 안드로이드 리소스 이름 규칙: [a-z0-9_]
        if not re.fullmatch(r"[a-z][a-z0-9_]*", base):
            continue
        names.append(base)
    return sorted(set(names))


def main():
    raws = resource_names("raw", AUDIO_EXT)
    drawables = resource_names("drawable", {".xml"})
    if not raws and not drawables:
        sys.exit("res 폴더에서 리소스를 찾지 못했습니다: %s" % RES)

    lines = [
        '@file:Suppress("unused", "MayBeConstant", "UNUSED_PARAMETER")',
        "",
        "/**",
        " * tools/preview — R 리소스 스텁 (aapt 대체).",
        " *",
        " * ⚠️ 손으로 고치지 말 것 — tools/preview/gen_r_stub.py 가 app/src/main/res 를 훑어 자동 생성한다.",
        " *    (새 사운드/그림을 추가한 뒤 프리뷰 컴파일이 깨지면 이 스크립트를 다시 실행)",
        " */",
        "package com.pizzaandbird.game",
        "",
        "object R {",
        "    object raw {",
    ]
    for i, name in enumerate(raws, start=1):
        lines.append("        val %s = %d" % (name, i))
    lines += [
        "    }",
        "",
        "    object drawable {",
        "        private var next = 10000",
        "        private fun art(name: String): Int {",
        "            val id = next++",
        "            android.content.Context.drawableRegistry[id] = name",
        "            return id",
        "        }",
        "",
    ]
    for name in drawables:
        lines.append('        val %s = art("%s")' % (name, name))
    lines += ["    }", "}", ""]

    text = "\n".join(lines)
    old = open(OUT, encoding="utf-8").read() if os.path.exists(OUT) else None
    if old == text:
        print("r_stub.kt: 변경 없음 (raw %d종 · drawable %d종)" % (len(raws), len(drawables)))
        return
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(text)
    print("r_stub.kt 생성: raw %d종 · drawable %d종" % (len(raws), len(drawables)))


if __name__ == "__main__":
    main()
