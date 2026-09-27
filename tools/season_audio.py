#!/usr/bin/env python3
"""계절 환경음 합성 — 완전 오프라인 게임이라 외부 음원 없이 numpy로 직접 만든다.

생성 파일 (app/src/main/res/raw):
  amb_cicada.wav   여름 낮 — 참매미 + 쓰르라미 합창 (14초 루프)
  amb_cricket.wav  가을 밤 — 귀뚜라미 (12초 루프)
  amb_frog.wav     봄 밤 — 개구리 + 물소리 (10초 루프)

눈 발걸음은 실황 녹음(sfx_step_snow.mp3, tools/build_audio.py)으로 대체되어 여기선 만들지 않는다.

모든 루프는 패턴 주기가 전체 길이로 나누어떨어지게 만들고,
마지막 120ms를 시작 부분으로 크로스페이드해 이음매 클릭을 없앤다.
"""
import wave
import numpy as np

SR = 22050
OUT = "app/src/main/res/raw"


def write_wav(path: str, x: np.ndarray, peak: float = 0.55):
    x = np.asarray(x, dtype=np.float64)
    m = np.max(np.abs(x))
    if m > 1e-6:
        x = x / m * peak
    x = np.clip(x, -1.0, 1.0)
    pcm = (x * 32767).astype(np.int16)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    print(f"{path}: {len(pcm) / SR:.1f}s peak={np.max(np.abs(pcm)) / 32767:.2f}")


def loop_blend(x: np.ndarray, fade_sec: float = 0.12) -> np.ndarray:
    """끝 fade 구간이 시작으로 자연스럽게 이어지도록 블렌딩 (길이 유지)."""
    n = int(fade_sec * SR)
    y = x.copy()
    a = np.linspace(0.0, 1.0, n)
    y[-n:] = x[-n:] * (1.0 - a) + x[:n] * a
    return y


def smooth_gate(n: int, edge: int) -> np.ndarray:
    """0/1 게이트의 가장자리를 raised-cosine으로 둥글게."""
    g = np.ones(n)
    if edge * 2 >= n:
        return g
    r = 0.5 - 0.5 * np.cos(np.linspace(0, np.pi, edge))
    g[:edge] = r
    g[-edge:] = r[::-1]
    return g


# ---------------------------------------------------------------- 매미 (여름 낮)
def cicada(dur: float = 14.0) -> np.ndarray:
    t = np.arange(int(dur * SR)) / SR
    y = np.zeros_like(t)

    # 참매미 "맴맴맴" — 3.5초 프레이즈 × 4 (14초와 정확히 맞아떨어짐)
    phrase = 0.5 + 0.5 * np.sin(2 * np.pi * t / 3.5 - np.pi / 2)   # 0..1 swell
    phrase = 0.30 + 0.70 * phrase ** 1.6
    metallic = 0.55 + 0.45 * np.sin(2 * np.pi * 175 * t)          # 금속성 떨림
    y += phrase * metallic * (
        np.sin(2 * np.pi * 4300 * t)
        + 0.45 * np.sin(2 * np.pi * 6450 * t + 1.1)
        + 0.25 * np.sin(2 * np.pi * 8600 * t + 2.3)
    )

    # 쓰르라미 "쓰르르" — 7초 주기 × 2
    swell2 = 0.35 + 0.65 * (0.5 + 0.5 * np.sin(2 * np.pi * t / 7.0 + 1.0)) ** 2
    y += 0.55 * swell2 * (0.6 + 0.4 * np.sin(2 * np.pi * 26 * t)) * (
        np.sin(2 * np.pi * 6050 * t + 0.7)
        + 0.4 * np.sin(2 * np.pi * 9075 * t)
    )

    # 멀리서 깔리는 매미 벽 — 두 음의 맥놀이
    wall = 0.5 + 0.5 * np.sin(2 * np.pi * t / 7.0 + 2.2)
    y += 0.30 * wall * (np.sin(2 * np.pi * 5200 * t) + np.sin(2 * np.pi * 5235 * t))

    # 살짝 섞이는 여름바람 (고주파 노이즈)
    rng = np.random.default_rng(7)
    noise = np.diff(rng.standard_normal(len(t) + 1)) * 0.5
    y += 0.035 * noise
    return loop_blend(y)


# ---------------------------------------------------------------- 귀뚜라미 (가을 밤)
def cricket(dur: float = 12.0) -> np.ndarray:
    t = np.arange(int(dur * SR)) / SR
    y = np.zeros_like(t)

    def one_cricket(freq: float, offset: float, gain: float, chirp_len: float):
        # 1초 주기: chirp_len만큼 울고 쉰다 — 12초 루프와 정확히 맞아떨어짐
        ph = (t + offset) % 1.0
        gate = np.zeros_like(t)
        on = ph < chirp_len
        gate[on] = 1.0
        # 펄스 (초당 24개) — 게이트 안에서만
        pulse = (np.sin(2 * np.pi * 24 * t) > -0.1).astype(float) * 0.75 + 0.25
        sig = gate * pulse * np.sin(2 * np.pi * freq * t)
        # 울음 시작/끝 15ms 페이드 (클릭 방지)
        edge = int(0.015 * SR)
        idx = np.flatnonzero(on)
        if len(idx) > 0:
            # 연속 구간별로 페이드
            splits = np.flatnonzero(np.diff(idx) > 1)
            starts = np.concatenate([[idx[0]], idx[splits + 1]])
            ends = np.concatenate([idx[splits], [idx[-1]]])
            for s, e in zip(starts, ends):
                m = min(edge, (e - s) // 2)
                if m > 2:
                    r = 0.5 - 0.5 * np.cos(np.linspace(0, np.pi, m))
                    sig[s:s + m] *= r
                    sig[e - m + 1:e + 1] *= r[::-1]
        return gain * sig

    y += one_cricket(4300, 0.00, 1.00, 0.32)
    y += one_cricket(4750, 0.45, 0.55, 0.26)
    y += one_cricket(3900, 0.70, 0.30, 0.22)

    # 밤 공기 (아주 낮은 노이즈 베드)
    rng = np.random.default_rng(11)
    bed = np.cumsum(rng.standard_normal(len(t))) * 0.0006
    bed -= np.linspace(bed[0], bed[-1], len(t))  # DC 제거 → 루프 이음매 안전
    y += bed
    return loop_blend(y)


# ---------------------------------------------------------------- 개구리 (봄 밤)
def frog(dur: float = 10.0) -> np.ndarray:
    t = np.arange(int(dur * SR)) / SR
    y = np.zeros_like(t)

    def croak(f0: float, at: float, syllables: int, gain: float):
        sig = np.zeros_like(t)
        for rep in range(int(dur / 2.0)):          # 2초 주기 × 5
            base = rep * 2.0 + at
            for s in range(syllables):
                start = base + s * 0.26
                n = int(0.17 * SR)
                i0 = int(start * SR)
                if i0 + n >= len(t):
                    continue
                tt = np.arange(n) / SR
                # "개굴" — 저음 + 그로울(28Hz 진폭변조) + 배음
                env = smooth_gate(n, int(0.02 * SR))
                growl = 0.5 + 0.5 * np.sin(2 * np.pi * 28 * tt)
                tone = (np.sin(2 * np.pi * f0 * tt)
                        + 0.6 * np.sin(2 * np.pi * f0 * 2 * tt + 0.8)
                        + 0.3 * np.sin(2 * np.pi * f0 * 3 * tt + 1.7))
                sig[i0:i0 + n] += gain * env * (0.35 + 0.65 * growl) * tone
        return sig

    y += croak(150, 0.15, 2, 1.0)    # 가까이서 "개굴개굴"
    y += croak(128, 1.05, 3, 0.55)   # 멀리서 대답
    y += croak(168, 1.55, 2, 0.35)

    # 물결 베드 — 느리게 출렁이는 저역 노이즈
    rng = np.random.default_rng(21)
    noise = rng.standard_normal(len(t))
    k = np.ones(64) / 64
    lap = np.convolve(noise, k, mode="same") * 0.5
    lap *= 0.6 + 0.4 * np.sin(2 * np.pi * t / 5.0)   # 5초 주기 × 2
    y += 0.10 * lap
    return loop_blend(y)


if __name__ == "__main__":
    write_wav(f"{OUT}/amb_cicada.wav", cicada(), peak=0.5)
    write_wav(f"{OUT}/amb_cricket.wav", cricket(), peak=0.5)
    write_wav(f"{OUT}/amb_frog.wav", frog(), peak=0.55)
