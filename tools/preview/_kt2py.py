#!/usr/bin/env python3
"""Assets.kt 의 buildTiles() 블록을 미리보기용 파이썬으로 1회 변환하는 보조 스크립트.

생성 결과(_tiles_gen.py)는 손으로 다듬어서 쓰는 1회성 산출물이다.
"""
import re
import sys

src = open(sys.argv[1], encoding="utf-8").read().splitlines()

out = []
indent = 0
pending_tile = []


def conv_expr(e: str) -> str:
    e = re.sub(r"\bc\(0x([0-9A-Fa-f]{8})\)", r"0x\1", e)
    e = re.sub(r"(\d)f\b", r"\1", e)
    e = re.sub(r"\.toFloat\(\)", "", e)
    e = re.sub(r"\.toInt\(\)", "", e)
    e = re.sub(r"intArrayOf\(([^()]*)\)", r"[\1]", e)
    e = e.replace("shade(", "shade(")
    # kotlin ternary  if (a) b else c  ->  (b if a else c)
    for _ in range(4):
        m = re.search(r"if \(([^()]*(?:\([^()]*\)[^()]*)*)\) ([^ ].*?) else ([^,)]+)", e)
        if not m:
            break
        e = e[: m.start()] + "(" + m.group(2) + " if " + m.group(1) + " else " + m.group(3) + ")" + e[m.end():]
    return e


def emit(line):
    out.append("    " * indent + line)


i = 0
while i < len(src):
    raw = src[i]
    line = raw.strip()
    i += 1
    if not line or line.startswith("//"):
        if line.startswith("//"):
            emit("#" + line[2:])
        continue
    line = re.sub(r"\s*//.*$", "", line)
    if not line:
        continue

    if line.startswith("private fun buildTiles") or line.startswith("val list =") or line.startswith("fun add("):
        continue
    if line.startswith("list.add("):
        continue
    if line.startswith("tiles = list"):
        continue

    m = re.match(r"^for \((\w+) in 0 until (.+?)\) \{$", line)
    if m:
        emit(f"for {m.group(1)} in range({conv_expr(m.group(2))}):")
        indent += 1
        continue
    m = re.match(r"^for \((\w+) in (.+?)\) \{$", line)
    if m:
        rng = conv_expr(m.group(2))
        if rng.startswith("["):
            rng = rng + "]" if not rng.endswith("]") else rng
        emit(f"for {m.group(1)} in {rng}:")
        indent += 1
        continue
    m = re.match(r"^repeat\((.+?)\) \{$", line)
    if m:
        emit(f"for _rep in range({conv_expr(m.group(1))}):")
        indent += 1
        continue
    m = re.match(r"^if \((.+)\) \{$", line)
    if m:
        emit(f"if {conv_expr(m.group(1))}:")
        indent += 1
        continue
    if line == "} else {":
        indent -= 1
        emit("else:")
        indent += 1
        continue
    if line.startswith("add(tilePainter { c, p, r ->"):
        emit("def _tp(c, p, r, **kw):")
        indent += 1
        emit("globals().update(kw)")
        continue
    if line in ("})", "}),"):
        indent -= 1
        emit("add(tile_painter(_tp))")
        continue
    if line == "}":
        indent -= 1
        continue
    if line.startswith("add(tilePainter {"):
        emit("### UNHANDLED " + line)
        continue

    # 일반 문장
    stmts = [s.strip() for s in line.split(";") if s.strip()]
    for s in stmts:
        s = re.sub(r"^val |^var ", "", s)
        s = conv_expr(s)
        s = s.replace("c.draw", "c.draw")
        emit(s)

print("\n".join(out))
