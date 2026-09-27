#!/usr/bin/env python3
"""없는 소리 점검 — docs/AUDIO_WISHLIST.md 의 발주 목록과 res/raw 를 비교한다.

사용법:
    python3 tools/audio_check.py              # 발주 대비 채워진/빠진 파일
    python3 tools/audio_check.py --loudness   # ffmpeg 있으면 피크(dBFS)까지 점검
    python3 tools/audio_check.py --durations  # ffprobe 있으면 길이(초)까지 점검

원칙: Android 리소스 파일명은 소문자·숫자·밑줄만 (하이픈/공백/대문자 금지).
      새 파일 이름이 규칙을 어기면 종료 코드 1 로 알린다.
"""
from __future__ import annotations

import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "app" / "src" / "main" / "res" / "raw"
WISHLIST = ROOT / "docs" / "AUDIO_WISHLIST.md"

# 발주서 표에서 뽑을 파일 이름 (백틱 안의 실제 파일명만)
NAME_RE = re.compile(r"`((?:sfx|amb|bgm)_[a-z0-9_]+\.(?:mp3|m4a))`")
# Android res/raw 규칙: 소문자로 시작, 소문자·숫자·밑줄, 확장자는 오디오만
VALID_RE = re.compile(r"^[a-z][a-z0-9_]*\.(mp3|m4a|ogg|wav|opus|flac)$")


def wanted_from_doc() -> dict[str, str]:
    """발주서 '표 행'에서만 파일명을 모은다. (설명 줄의 예시는 제외)"""
    if not WISHLIST.exists():
        print(f"⚠  발주서가 없습니다: {WISHLIST.relative_to(ROOT)}")
        return {}
    names: dict[str, str] = {}
    for line in WISHLIST.read_text(encoding="utf-8").splitlines():
        if not line.lstrip().startswith("|"):
            continue
        for name in NAME_RE.findall(line):
            names.setdefault(name, line.strip())
    return names


def size_mb(path: Path) -> float:
    return path.stat().st_size / (1024 * 1024)


def ffprobe_seconds(path: Path) -> float | None:
    if not shutil.which("ffprobe"):
        return None
    try:
        out = subprocess.run(
            ["ffprobe", "-v", "error", "-show_entries", "format=duration",
             "-of", "csv=p=0", str(path)],
            capture_output=True, text=True, timeout=20,
        )
        return float(out.stdout.strip())
    except Exception:
        return None


def peak_dbfs(path: Path) -> float | None:
    """ffmpeg volumedetect 로 최대 볼륨(dBFS)을 읽는다."""
    if not shutil.which("ffmpeg"):
        return None
    try:
        out = subprocess.run(
            ["ffmpeg", "-hide_banner", "-i", str(path),
             "-af", "volumedetect", "-f", "null", "-"],
            capture_output=True, text=True, timeout=60,
        )
        m = re.search(r"max_volume:\s*(-?\d+(?:\.\d+)?) dB", out.stderr)
        return float(m.group(1)) if m else None
    except Exception:
        return None


def main() -> int:
    args = sys.argv[1:]
    show_loudness = "--loudness" in args
    show_durations = "--durations" in args

    if not RAW.exists():
        print(f"✗ res/raw 를 찾을 수 없습니다: {RAW}")
        return 1

    existing = sorted(p for p in RAW.iterdir() if p.is_file())
    existing_names = {p.name for p in existing}

    # 1) 파일명 규칙 검사
    bad = [p.name for p in existing if not VALID_RE.match(p.name)]
    if bad:
        print("✗ 파일명 규칙 위반 (소문자·숫자·밑줄만, 하이픈/공백/대문자 금지):")
        for n in bad:
            print(f"   - {n}")
        print("   → 파일명을 고치기 전에는 aapt(=빌드)가 실패할 수 있습니다.\n")

    # 2) 발주 대비 진행 상황
    wanted = wanted_from_doc()
    missing = sorted(n for n in wanted if n not in existing_names)
    done = sorted(n for n in wanted if n in existing_names)

    print(f"📦 res/raw: 파일 {len(existing)}개 · {sum(size_mb(p) for p in existing):.1f} MB")
    print(f"📋 발주 목록: {len(wanted)}개  →  채움 {len(done)} · 남음 {len(missing)}\n")

    if done:
        print("✅ 이미 들어온 발주 파일")
        for n in done:
            p = RAW / n
            extra = f"  {size_mb(p):.2f} MB"
            if show_durations:
                d = ffprobe_seconds(p)
                if d:
                    extra += f" · {d:.1f}s"
            if show_loudness:
                v = peak_dbfs(p)
                if v is not None:
                    extra += f" · peak {v:+.1f} dBFS"
                    if v > -0.5:
                        extra += "  ⚠ 클리핑 위험"
            print(f"   {n:28s}{extra}")
        print()

    if missing:
        print(f"⬜ 아직 없는 파일 {len(missing)}개 — 우선순위(⭐)는 발주서 TOP 10 참고")
        for n in missing:
            print(f"   {n}")
        print()
    else:
        print("🎉 발주서의 모든 파일이 들어왔습니다. 이제 코드 연결만 남았어요!\n")

    # 3) 발주서에 없는 파일 (기존 보유분 또는 직접 추가하신 것)
    extra_files = sorted(n for n in existing_names if n not in wanted and n not in bad)
    if extra_files:
        print("ℹ res/raw 에 있지만 발주 목록에는 없는 파일 (기존 보유분 또는 직접 추가)")
        for n in extra_files:
            print(f"   {n}")
        print()

    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main())
