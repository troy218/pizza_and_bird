# 길 디자인 미리보기 (Python)

안드로이드 기기 없이 **맵 레이아웃과 타일 아트를 그대로 렌더링해 보는** 개발용 도구다.
게임 코드(`Assets.kt`, `Maps.kt`, `Roads.kt`)와 같은 알고리즘을 파이썬으로 옮겨 놓았기 때문에
길 모양을 고칠 때 APK를 빌드하지 않고도 결과를 눈으로 확인할 수 있다.

```bash
pip3 install pillow numpy

# 1) 144개(지역 12 x 홈 12) 맵 조합의 규칙 검사 — tools/MapTest.kt 의 파이썬 판
python3 tools/preview/check.py

# 2) 맵 한 장 렌더링
python3 - <<'PY'
import sys; sys.path.insert(0, 'tools/preview')
import render, mapgen
m = mapgen.build('seoul', 'seoul')
render.render(m.tile, m.base, m.pave, m.deco, m.w, m.h).save('/tmp/seoul.png')
PY
```

| 파일 | 역할 |
| --- | --- |
| `pixelcanvas.py` | `android.graphics.Canvas` / `java.util.Random` 의 픽셀 단위 클론 (안티에일리어싱 없음) |
| `roads.py` | `Roads.kt` 프로토타입 — 포장 실루엣·바퀴자국·판석·연석·문양 |
| `mapgen.py` | `MapBuilder.build()` 프로토타입 — 간선도로/샛길/광장 배치 |
| `render.py` | 지면 → 포장 → 데칼 → 구조물 → 그림자 순서로 합성 |
| `tiles_legacy.py` | `Assets.kt` 의 기존 타일 아트를 옮겨 온 **자동 생성** 파일 |
| `_gen_tiles_legacy.py` | 위 파일을 `Assets.kt` 에서 다시 만들어 내는 스크립트 |

> 게임 동작의 기준은 어디까지나 Kotlin 쪽 코드다. 이 도구는 "빠른 눈 확인"용이며,
> 길 규칙을 바꿀 때는 `mapgen.py` 와 `Maps.kt` 를 **같이** 고쳐야 한다.
