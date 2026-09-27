#!/usr/bin/env python3
"""🍕🐦 피자와 새 — 소리 변환 파이프라인 (audio_src/ → app/src/main/res/raw/)

원본 소리(사람이 받아 둔 mp3)를 게임에 넣을 수 있는 형태로 다듬는다:
  · 인프라소닉 럼블·DC 제거(하이패스) — 발소리 원본에 20Hz 이하 에너지가 78%씩 섞여 있다
  · 무음 트림 + 클릭 방지 페이드
  · 환경음은 앞뒤 크로스페이드로 **이음매 없는 루프**
  · 발소리는 온셋(걸음)을 자동 검출해 **걷는 리듬 그대로 여러 걸음 루프**로 만든다
  · 기존 res/raw 파일과 같은 레벨대(RMS)로 정규화 — 게임 코드의 볼륨 값이 그대로 통해야 한다

사용법
    python3 tools/build_audio.py --report        # 현재 res/raw 결과만 점검
    python3 tools/build_audio.py                 # 전체 다시 만들기
    python3 tools/build_audio.py --only sfx_step_snow
    python3 tools/build_audio.py --list          # 발주 목록만 보기

준비물
    pip install numpy imageio-ffmpeg            # ffmpeg 바이너리를 함께 받는다
    FFMPEG=/path/to/ffmpeg 로 직접 지정해도 된다.

발주서: docs/AUDIO_WISHLIST.md · 점검: tools/audio_check.py
"""
from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "audio_src"
OUT = ROOT / "app" / "src" / "main" / "res" / "raw"
SR = 44100


# ---------------------------------------------------------------------------
# ffmpeg 찾기 / 입출력
# ---------------------------------------------------------------------------

def find_ffmpeg() -> str:
    if os.environ.get("FFMPEG"):
        return os.environ["FFMPEG"]
    exe = shutil.which("ffmpeg")
    if exe:
        return exe
    try:
        import imageio_ffmpeg  # type: ignore
        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        pass
    sys.exit("ffmpeg 를 찾을 수 없습니다. `pip install imageio-ffmpeg` 또는 FFMPEG=/경로/ffmpeg 로 지정하세요.")


FF = find_ffmpeg()


def load(path: Path, ch: int = 2, sr: int = SR) -> np.ndarray:
    """디코딩해서 float64 (n, ch) 로. 모든 DSP 는 이 배열에서 한다."""
    raw = subprocess.run(
        [FF, "-v", "error", "-i", str(path), "-f", "f32le", "-ac", str(ch), "-ar", str(sr), "-"],
        capture_output=True, check=True,
    ).stdout
    a = np.frombuffer(raw, dtype="<f4").astype(np.float64)
    return a.reshape(-1, ch)


def encode(a: np.ndarray, path: Path, bitrate: str) -> None:
    """(n, ch) float64 → mp3 (libmp3lame). 리소스 이름 규칙은 호출부에서 지킨다."""
    ch = a.shape[1] if a.ndim > 1 else 1
    data = np.clip(a, -1.0, 1.0).astype("<f4").tobytes()
    path.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [FF, "-v", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", str(ch), "-i", "-",
         "-c:a", "libmp3lame", "-b:a", bitrate, str(path)],
        input=data, check=True,
    )


# ---------------------------------------------------------------------------
# DSP 조각들
# ---------------------------------------------------------------------------

def db(x: float) -> float:
    return 20 * np.log10(max(float(x), 1e-12))


def peak(a: np.ndarray) -> float:
    return float(np.max(np.abs(a))) if a.size else 0.0


def rms(a: np.ndarray) -> float:
    return float(np.sqrt(np.mean(np.square(a)))) if a.size else 0.0


def band_filter(a: np.ndarray, hi: float | None = None, lo: float | None = None, order: int = 4) -> np.ndarray:
    """제로위상 FFT 대역 필터 — hi(하이패스)/lo(로우패스) 컷오프(Hz)."""
    n = 1 << int(np.ceil(np.log2(max(len(a), 2))))
    f = np.fft.rfftfreq(n, 1 / SR)
    f[0] = f[1] if len(f) > 1 else 1.0
    H = np.ones_like(f)
    if hi:
        H = H / np.sqrt(1 + (hi / f) ** (2 * order))
    if lo:
        H = H / np.sqrt(1 + (f / lo) ** (2 * order))
    spec = np.fft.rfft(a, n, axis=0)
    H = H if a.ndim == 1 else H[:, None]
    return np.fft.irfft(spec * H, n, axis=0)[: len(a)]


def fade(a: np.ndarray, in_ms: float = 0.0, out_ms: float = 0.0) -> np.ndarray:
    a = a.copy()
    n_in, n_out = int(in_ms / 1000 * SR), int(out_ms / 1000 * SR)
    if n_in > 1:
        a[:n_in] *= np.linspace(0, 1, n_in)[:, None]
    if n_out > 1:
        a[-n_out:] *= np.linspace(1, 0, n_out)[:, None]
    return a


def trim(a: np.ndarray, thresh_db: float = -45.0, pre_ms: float = 15.0, post_ms: float = 60.0) -> np.ndarray:
    """앞뒤 무음 제거 — 탭 후 반응이 느껴지지 않도록 앞머리를 최소한만 남긴다."""
    mono = np.abs(a).max(axis=1) if a.ndim > 1 else np.abs(a)
    thr = 10 ** (thresh_db / 20)
    idx = np.where(mono > thr)[0]
    if len(idx) == 0:
        return a
    s = max(0, idx[0] - int(pre_ms / 1000 * SR))
    e = min(len(a), idx[-1] + int(post_ms / 1000 * SR))
    return a[s:e]


def normalize(a: np.ndarray, target_rms_db: float, ceiling_db: float = -1.5) -> np.ndarray:
    """RMS 를 기준으로 맞추고, 피크가 천장을 넘지 않게 한 번 더 조인다.

    기존 res/raw 파일의 실측 RMS(효과음 -22~-26 dB, 환경음 -29 dB)에 맞추기 위한 것 —
    게임 코드가 쓰는 볼륨 값(0.2~0.5)이 그대로 통하도록."""
    g = (10 ** (target_rms_db / 20)) / max(rms(a), 1e-9)
    a = a * g
    p = peak(a)
    ceil = 10 ** (ceiling_db / 20)
    if p > ceil:
        a = a * (ceil / p)
    return a


def loop_crossfade(a: np.ndarray, seconds: float = 2.0) -> np.ndarray:
    """앞뒤를 겹쳐 이음매 없는 루프로 — 끝에서 시작으로 넘어갈 때 티가 나지 않는다."""
    n = min(int(seconds * SR), len(a) // 3)
    if n < 64:
        return a
    t = np.linspace(0, 1, n)[:, None]
    head, tail = a[:n], a[-n:]
    body = a[n:-n]
    return np.concatenate([body, tail * (1 - t) + head * t])


def envelope(a: np.ndarray, frame_ms: float = 20.0) -> tuple[np.ndarray, float]:
    mono = a.mean(axis=1) if a.ndim > 1 else a
    hop = int(frame_ms / 1000 * SR)
    n = len(mono) // hop
    return np.sqrt(np.mean(mono[: n * hop].reshape(n, hop) ** 2, axis=1)), frame_ms / 1000


def detect_onsets(a: np.ndarray, thr_ratio: float = 0.25, min_gap: float = 0.18) -> list[float]:
    """걸음(발소리) 후보 시각. 발소리 원본에서 '걷는 리듬' 구간을 찾는 데 쓴다."""
    e, dt = envelope(a)
    if len(e) < 3:
        return []
    thr = e.max() * thr_ratio
    out, last = [], -99.0
    for i in range(1, len(e)):
        if e[i] > thr and e[i] > e[i - 1] * 1.5 and (i - last) * dt > min_gap:
            out.append(i * dt)
            last = i
    return out


def pick_window(onsets: list[float], n_steps: int, max_len: float,
                limit: float = 1e9, tail: float = 0.30) -> tuple[float, int, int]:
    """걸음 여러 개를 담으면서 가장 느린(자연스러운) 구간을 고른다.

    파일 끝을 넘지 않는 구간만 쓴다 — 마지막 걸음의 여운이 잘리면 루프 이음매가 들린다.
    돌려주는 값: (시작 시각, 시작 인덱스, 걸음 수)"""
    best: tuple[float, int, int] | None = None
    for i in range(len(onsets)):
        for k in range(2, n_steps + 1):
            if i + k > len(onsets):
                continue
            span = onsets[i + k - 1] - onsets[i]
            if span > max_len or onsets[i + k - 1] + tail > limit:
                continue
            if best is None or span > best[0]:
                best = (span, i, k)
    if best is None:
        return (onsets[0] if onsets else 0.0, 0, 1)
    return (onsets[best[1]], best[1], best[2])


def variation(x: np.ndarray, rate: float, gain: float) -> np.ndarray:
    """한 걸음을 살짝 다른 속도/크기로 — 같은 소리가 기계처럼 반복되지 않게."""
    n = max(2, int(len(x) / rate))
    src = np.arange(n) * rate
    idx = np.clip(src.astype(int), 0, len(x) - 1)
    frac = (src - idx)[:, None]
    y = x[idx] * (1 - frac) + x[np.clip(idx + 1, 0, len(x) - 1)] * frac
    return y * gain


def step_loop(a: np.ndarray, cadence: float = 0.56, length: float = 4.4, seed: int = 7) -> np.ndarray:
    """한 걸음짜리 원본 → 걷는 리듬의 루프로. (짧은 원본용)"""
    rng = np.random.default_rng(seed)
    out = np.zeros((int(length * SR) + len(a), 1), dtype=np.float64)
    t = 0.05
    while t < length:
        x = variation(a, 1 + rng.uniform(-0.05, 0.05), rng.uniform(0.82, 1.0))
        s = int(t * SR)
        out[s:s + len(x)] += x
        t += cadence * (1 + rng.uniform(-0.06, 0.06))
    out = out[: int(length * SR)]
    return fade(out, in_ms=4, out_ms=25)


def repeat_segment(seg: np.ndarray, times: int, seed: int = 11) -> np.ndarray:
    """여러 걸음 구간을 살짝씩 다르게 이어 붙인다 — 짧은 원본의 반복감을 줄인다."""
    rng = np.random.default_rng(seed)
    parts = [seg]
    for _ in range(times - 1):
        parts.append(variation(seg, 1 + rng.uniform(-0.03, 0.03), rng.uniform(0.88, 1.0)))
    return fade(np.concatenate(parts), in_ms=4, out_ms=25)


# ---------------------------------------------------------------------------
# 발주서 — 원본 → res/raw 레시피
#   kind: loop(환경음 루프) / step(발소리 루프) / one(효과음 한 방)
# ---------------------------------------------------------------------------

@dataclass
class Recipe:
    out: str                  # res/raw 에 들어갈 이름 (소문자·숫자·밑줄만)
    src: str                  # audio_src 안의 원본 파일 이름
    kind: str
    note: str = ""            # 왜 이렇게 쓰는지 (문서·리뷰용)
    start: float = 0.0        # 원본에서 잘라 쓸 시작 시각
    length: float = 0.0       # 0이면 끝까지
    hi: float = 60.0          # 하이패스 (Hz)
    lo: float | None = None   # 로우패스 (Hz)
    rms_db: float = -26.0     # 목표 RMS (기존 파일 실측대에 맞춤)
    ceiling_db: float = -1.5  # 피크 천장
    stereo: bool = False      # 효과음은 모노, 환경음은 스테레오
    bitrate: str = "96k"
    xfade: float = 2.0        # 루프 크로스페이드(초)
    cadence: float = 0.56     # 발소리 걸음 간격(초)
    n_steps: int = 6          # 원본에서 묶어 쓸 걸음 수
    max_span: float = 3.2     # 걸음 묶음의 최대 길이(초)
    repeats: int = 1          # 묶음을 몇 번 반복할지
    steps: list[tuple[float, float]] = field(default_factory=list)  # 직접 지정 구간 (start,end)


RECIPES: list[Recipe] = [
    # ---------------------------------------------------------------- 환경음
    Recipe(
        out="amb_rain.mp3", src="boons_freak-rain-sound-188158.mp3", kind="loop",
        note="일반적인 빗소리(밝은 소리) — 비 오는 낮/밤의 기본 환경음",
        start=10.0, length=60.0, hi=60, rms_db=-28.5, ceiling_db=-3.0, stereo=True,
    ),
    Recipe(
        out="amb_rain_heavy.mp3", src="kanematsutei-heavy-rain-144282.mp3", kind="loop",
        note="세찬 장맛비 — 여름(장마) 비에 쓴다. 저역이 많아 '쏟아진다'는 느낌",
        start=2.0, length=42.0, hi=50, rms_db=-25.5, ceiling_db=-2.0, stereo=True,
    ),
    Recipe(
        out="amb_rain_roof.mp3", src="kanematsutei-the-sound-of-rain-falling-on-an-umbrella-145474.mp3",
        kind="loop",
        note="지붕·우산에 떨어지는 비 — 비 오는 날 집/랜드마크 실내에서 은은하게",
        start=6.0, length=60.0, hi=60, rms_db=-30.5, ceiling_db=-3.0, stereo=True,
    ),
    # ---------------------------------------------------------------- 효과음
    Recipe(
        out="sfx_levelup.mp3", src="universfield-level-up-05-326133.mp3", kind="one",
        note="레벨업 팡파레 — 지금까지 무음이던 레벨업 화면",
        hi=80, rms_db=-21.0, ceiling_db=-1.5,
    ),
    Recipe(
        out="sfx_cat_punch.mp3", src="dragon-studio-cat-meow which is punched.mp3", kind="one",
        note="펀치 맞은 고양이 — 지금은 whoosh+탭으로 때우던 순간",
        hi=90, rms_db=-22.0, ceiling_db=-2.0,
    ),
    # ---------------------------------------------------------------- 발소리
    Recipe(
        out="sfx_step_grass.mp3", src="joentnt-walk-on-grass-1-291984.mp3", kind="step",
        note="풀밭 한 걸음짜리 원본 → 걷는 리듬 루프로 합성(짧아서 반복 생성)",
        hi=180, cadence=0.56, length=4.4, rms_db=-25.5, ceiling_db=-2.0,
    ),
    Recipe(
        out="sfx_step_sand.mp3", src="freesound_community-sand_step-87182.mp3", kind="step",
        note="모래·갯벌 한 걸음(원본이 -26dB로 아주 작아 정규화로 끌어올림)",
        hi=140, cadence=0.58, length=4.4, rms_db=-25.5, ceiling_db=-2.0,
    ),
    Recipe(
        out="sfx_step_snow.mp3", src="dragon-studio-footsteps-in-snow-335506.mp3", kind="step",
        note="눈 밟는 소리 — 20Hz 이하 럼블이 78%라 250Hz 하이패스로 '뽀득'만 남긴다",
        hi=250, n_steps=5, max_span=2.9, repeats=2, rms_db=-25.5, ceiling_db=-2.0,
    ),
    Recipe(
        out="sfx_step_stone.mp3", src="freesound_community-stone-steps-6748.mp3", kind="step",
        note="돌바닥 — 원본 뒤쪽 3걸음 구간을 반복해 리듬 루프로",
        hi=110, n_steps=3, max_span=1.25, repeats=3, rms_db=-25.5, ceiling_db=-2.0,
    ),
    Recipe(
        out="sfx_step_water.mp3", src="nematoki-walking-puddles-wet-sand-road-493332.mp3", kind="step",
        note="물웅덩이·젖은 흙 — 가장 느린 걸음 구간을 골라 루프로",
        hi=110, n_steps=6, max_span=2.6, repeats=2, rms_db=-25.5, ceiling_db=-2.0,
    ),
]

# 고양이 울음: 11.9초 원본에서 구간을 자동 분리해 3개를 뽑는다 (아래 build_cat_meows)
CAT_SRC = "dragon-studio-meowing-cat.mp3"


# ---------------------------------------------------------------------------
# 빌더
# ---------------------------------------------------------------------------

def src_path(name: str) -> Path:
    p = SRC / name
    if not p.exists():
        sys.exit(f"원본이 없습니다: {p.relative_to(ROOT)}\n  audio_src/ 에 올려 주세요 (docs/AUDIO_WISHLIST.md 참고)")
    return p


def build_loop(r: Recipe) -> np.ndarray:
    a = load(src_path(r.src), ch=2 if r.stereo else 1)
    s, e = int(r.start * SR), int((r.start + r.length) * SR) if r.length else len(a)
    a = a[s:min(e, len(a))]
    a = band_filter(a, hi=r.hi, lo=r.lo)
    a = loop_crossfade(a, r.xfade)
    return normalize(a, r.rms_db, r.ceiling_db)


def build_step(r: Recipe) -> np.ndarray:
    a = load(src_path(r.src), ch=1)
    a = band_filter(a, hi=r.hi, lo=r.lo)
    if r.steps:
        segs = [a[int(s * SR):int(e * SR)] for s, e in r.steps]
        return normalize(repeat_segment(np.concatenate(segs), max(1, r.repeats)), r.rms_db, r.ceiling_db)
    ons = detect_onsets(a)
    if len(ons) < 2:                      # 한 걸음짜리 원본
        return normalize(step_loop(a, r.cadence, r.length), r.rms_db, r.ceiling_db)
    start, idx, n = pick_window(ons, r.n_steps, r.max_span, limit=len(a) / SR - 0.02)
    if n <= 1:                            # 걸음이 흩어져 있으면 첫 걸음만 반복
        seg = a[int(start * SR):int((start + r.cadence + 0.35) * SR)]
        return normalize(repeat_segment(seg, 6), r.rms_db, r.ceiling_db)
    end = ons[idx + n - 1] + 0.30
    seg = a[max(0, int((start - 0.03) * SR)):int(min(end, len(a) / SR) * SR)]
    seg = fade(seg, in_ms=4, out_ms=25)
    if r.repeats > 1:
        seg = repeat_segment(seg, r.repeats)
    return normalize(seg, r.rms_db, r.ceiling_db)


def build_one(r: Recipe) -> np.ndarray:
    a = load(src_path(r.src), ch=1)
    a = band_filter(a, hi=r.hi, lo=r.lo)
    a = trim(a)
    a = fade(a, in_ms=2, out_ms=18)
    return normalize(a, r.rms_db, r.ceiling_db)


def find_voice_segments(a: np.ndarray, gap: float = 0.28) -> list[tuple[int, int]]:
    """울음 덩어리(야옹 한 번) 구간 찾기 — 문턱값 위 구간을 gap 이상 벌어지면 나눈다."""
    mono = np.abs(a).max(axis=1) if a.ndim > 1 else np.abs(a)
    e, dt = envelope(mono.reshape(-1, 1))
    thr = max(e.max() * 0.06, 1e-4)
    on = e > thr
    segs, i = [], 0
    while i < len(on):
        if not on[i]:
            i += 1
            continue
        j = i
        while j < len(on) and (on[j] or any(on[j:j + int(gap / dt)])):
            j += 1
        segs.append((int(i * dt * SR), int(min(len(mono), j * dt * SR))))
        i = j + 1
    return [(s, e) for s, e in segs if e - s > int(0.25 * SR)]


def build_cat_meows(write: bool, report: bool) -> list[dict]:
    """야옹 원본에서 서로 다른 울음 3개를 뽑아 sfx_cat_meow1~3 으로."""
    a = load(src_path(CAT_SRC), ch=1)
    a = band_filter(a, hi=110)
    segs = find_voice_segments(a)
    segs.sort(key=lambda se: rms(a[se[0]:se[1]]), reverse=True)
    segs = segs[:3]
    segs.sort(key=lambda se: se[0])                     # 원본 순서 유지
    rows = []
    for i, (s, e) in enumerate(segs, start=1):
        clip = fade(trim(a[max(0, s - int(0.02 * SR)):e]), in_ms=2, out_ms=14)
        clip = normalize(clip, -23.0, -2.0)
        out = OUT / f"sfx_cat_meow{i}.mp3"
        rows.append(report_row(out.name, clip, "one", f"야옹 {i} — 원본 {s/SR:.2f}s 지점"))
        if write:
            encode(clip, out, "96k")
    if len(segs) < 3:
        print(f"  ⚠ {CAT_SRC} 에서 울음 3개를 못 찾았습니다 ({len(segs)}개) — 원본을 확인하세요")
    return rows


# ---------------------------------------------------------------------------
# 점검 / 실행
# ---------------------------------------------------------------------------

def report_row(name: str, a: np.ndarray, kind: str, note: str) -> dict:
    ch = a.shape[1] if a.ndim > 1 else 1
    return dict(name=name, dur=len(a) / SR, ch=ch, peak=db(peak(a)), rms=db(rms(a)),
                kind=kind, note=note)


def print_report(rows: list[dict]) -> None:
    print(f"\n{'파일':30s} {'길이':>7s} {'ch':>3s} {'peak':>7s} {'rms':>7s}  메모")
    print("-" * 100)
    for r in sorted(rows, key=lambda x: x["name"]):
        print(f"{r['name']:30s} {r['dur']:6.2f}s {r['ch']:3d} {r['peak']:+6.1f}dB {r['rms']:+6.1f}dB  {r['note']}")
    total = sum((OUT / r["name"]).stat().st_size for r in rows if (OUT / r["name"]).exists())
    print(f"\n총 {total/1024/1024:.2f} MB (신규/갱신 {len(rows)}개)")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", help="이름에 이 글자가 들어간 것만")
    ap.add_argument("--report", action="store_true", help="파일을 만들지 않고 결과만 점검")
    ap.add_argument("--list", action="store_true", help="레시피 목록만")
    args = ap.parse_args()

    targets = [r for r in RECIPES if not args.only or args.only in r.out]
    do_cats = not args.only or "cat_meow" in args.only

    if args.list:
        for r in targets:
            print(f"{r.out:26s} ← {r.src:52s} {r.note}")
        print(f"{'sfx_cat_meow1~3.mp3':26s} ← {CAT_SRC:52s} 야옹 자동 분리")
        return 0

    if not SRC.exists():
        sys.exit(f"원본 폴더가 없습니다: {SRC.relative_to(ROOT)}\n  올려 주신 mp3 를 audio_src/ 로 옮기면 됩니다.")

    rows: list[dict] = []
    print(f"🎧 피자와 새 소리 변환 — {SRC.name}/ → res/raw/   (ffmpeg: {Path(FF).name})")
    for r in targets:
        a = {"loop": build_loop, "step": build_step, "one": build_one}[r.kind](r)
        if args.report:
            a = load(OUT / r.out, ch=2 if r.stereo else 1) if (OUT / r.out).exists() else a
        elif not args.report:
            encode(a, OUT / r.out, r.bitrate)
        rows.append(report_row(r.out, a, r.kind, r.note))

    if do_cats:
        rows += build_cat_meows(write=not args.report, report=args.report)

    print_report(rows)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
