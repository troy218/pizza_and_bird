#!/usr/bin/env python3
"""
Kotlin 정적 점검 (컴파일러 없이 괄호/문자열 균형 + Type API 사용 확인).

- 문자열/주석을 제외한 {} () [] 균형
- Type / PixelFont / Role 멤버가 실제로 있는지 대조
- 쓰이지 않는 private val 같은 흔한 실수 탐지용 아님(경고만)

사용: python3 tools/kt_check.py
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "app", "src", "main", "java", "com", "pizzaandbird", "game")

# Type.kt / PixelFont 에 실제로 있는 공개 멤버
TYPE_MEMBERS = {
    "init", "bind", "face", "paint", "paintAt", "paintPx", "size", "lineHeight",
    "midBaseline", "width", "text", "textCentered", "sticker", "stickerCentered", "wrap",
    "INK", "SOFT", "MUTED", "BROWN", "CARAMEL", "CREAM", "PAPER", "LEAF", "BERRY", "SKY", "DROP", "d",
}
PIXEL_MEMBERS = {"GW", "GH", "ADVANCE", "supports", "width", "capHeight", "midY", "draw"}
ROLES = {"HERO", "DISPLAY", "TITLE", "HEADING", "LABEL", "BODY", "CAPTION", "MICRO", "EMOJI"}


def strip_code(src):
    """문자열/문자/주석을 공백으로 바꾼다(괄호 균형 검사용)."""
    out = []
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        if c == '"':
            if src.startswith('"""', i):
                j = src.find('"""', i + 3)
                j = n if j < 0 else j + 3
            else:
                j = i + 1
                while j < n:
                    if src[j] == "\\":
                        j += 2
                        continue
                    if src[j] == '"' or src[j] == "\n":
                        j += 1
                        break
                    j += 1
            out.append(" " * (j - i))
            i = j
            continue
        if c == "'":
            j = i + 1
            while j < n:
                if src[j] == "\\":
                    j += 2
                    continue
                if src[j] == "'":
                    j += 1
                    break
                j += 1
            out.append(" " * (j - i))
            i = j
            continue
        if src.startswith("//", i):
            j = src.find("\n", i)
            j = n if j < 0 else j
            out.append(" " * (j - i))
            i = j
            continue
        if src.startswith("/*", i):
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            out.append("".join(ch if ch == "\n" else " " for ch in src[i:j]))
            i = j
            continue
        out.append(c)
        i += 1
    return "".join(out)


def check(path):
    src = open(path, encoding="utf-8").read()
    code = strip_code(src)
    problems = []
    stack = []
    pairs = {")": "(", "]": "[", "}": "{"}
    line = 1
    for ch in code:
        if ch == "\n":
            line += 1
        elif ch in "([{":
            stack.append((ch, line))
        elif ch in ")]}":
            if not stack or stack[-1][0] != pairs[ch]:
                problems.append("%d: '%s' 짝이 맞지 않음" % (line, ch))
                break
            stack.pop()
    if stack:
        problems.append("%d: 닫히지 않은 '%s' (줄 %d)" % (len(code.splitlines()), stack[-1][0], stack[-1][1]))
    return problems


def main():
    bad = 0
    for name in sorted(os.listdir(SRC)):
        if not name.endswith(".kt"):
            continue
        path = os.path.join(SRC, name)
        problems = check(path)
        src = open(path, encoding="utf-8").read()
        for m in re.finditer(r"(?<!Insets\.)(?<!Font)Type\.([A-Za-z_][A-Za-z0-9_]*)", src):
            if m.group(1) not in TYPE_MEMBERS:
                problems.append("Type.%s 없음" % m.group(1))
        for m in re.finditer(r"(?<!Insets)PixelFont\.([A-Za-z_][A-Za-z0-9_]*)", src):
            if m.group(1) not in PIXEL_MEMBERS:
                problems.append("PixelFont.%s 없음" % m.group(1))
        for m in re.finditer(r"\bRole\.([A-Z_]+)", src):
            if m.group(1) not in ROLES:
                problems.append("Role.%s 없음" % m.group(1))
        if problems:
            bad += 1
            print("[%s]" % name)
            for p in sorted(set(problems)):
                print("   -", p)
    print("문제 파일 %d개" % bad)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
