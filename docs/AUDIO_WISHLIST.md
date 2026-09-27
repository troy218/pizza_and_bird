# 🔊 없는 소리 발주서 — 피자와 새

> 지금 코드에 **소리가 아예 없거나, 다른 소리를 임시로 빌려 쓰는 순간**들을 정리한 목록입니다.
> 파일 이름 그대로 `app/src/main/res/raw/`에 넣으면 **코드 연결(호출 지점 추가)은 제가 이어서 합니다.**
> (파일이 없는 상태로 코드를 먼저 쓰면 빌드가 깨지므로, 소리부터 받고 한 번에 붙이는 순서가 안전합니다.)

## 현재 상태

| 종류 | 개수 | 폴더 | 용량 |
|---|---|---|---|
| BGM (`bgm_*`) | 5 | `app/src/main/res/raw/` | 약 9.2 MB |
| 환경음 (`amb_*`) | 7 | 〃 | 약 3.9 MB |
| 효과음 (`sfx_*`) | 24 | 〃 | 약 1.4 MB |

효과음 24종은 **모두 같은 소리를 여러 상황에서 돌려 쓰는 중**입니다 (예: 도감 신규 · 걸작 · 행운 상승 · 버섯 채집 전부 `sfx_sparkle`).
볼륨·피치(`rate`)로만 차이를 주고 있어서, **소리가 늘어날수록 체감 품질이 가장 크게 오르는 구간**입니다.

---

## 0. 넣는 방법 (먼저 읽어 주세요)

| 항목 | 규칙 |
|---|---|
| 위치 | `app/src/main/res/raw/` |
| 파일 이름 | **영문 소문자 · 숫자 · 밑줄(`_`)만.** 하이픈·공백·한글·대문자 금지 (Android 리소스 규칙). 표의 이름을 **그대로** |
| 형식 | 효과음 `mp3` / 환경음 `mp3` / BGM `m4a`(AAC) 또는 `mp3` — 기존 파일과 동일 |
| 효과음 스펙 | 길이 0.15~2.5초, **앞머리 무음 20ms 이내 제거**(탭 후 딜레이가 곧 반응 속도로 느껴짐), 피크 −1.5 dBFS |
| 환경음(루프) 스펙 | 60초 내외, **앞뒤 크로스페이드로 이음매 없이**(끊김 들리면 바로 티가 납니다), 피크 −3 dBFS 정도로 낮게 |
| BGM 스펙 | 90~150초 루프, 128~160 kbps |
| 용량 목표 | `P04` 계획 기준 **+25 MB 이내** (APK 크기) |
| 라이선스 | **CC0 / 직접 제작 권장.** 출처 표기가 필요한 소재면 알려주세요 — `README`에 크레딧 절을 만들겠습니다 |
| 검증 | 아래 파일만 넣어두면 `python3 tools/audio_check.py` 로 이름·개수·남은 목록을 바로 확인할 수 있습니다 |

---

## 1. 급한 순서 TOP 10

딱 열 개만 한다면 이것부터. (아래 표의 ⭐ 표시와 동일)

| 순위 | 파일 | 무엇 | 지금은 |
|---|---|---|---|
| 1 | `amb_rain.mp3` | 비 오는 날 빗소리 (루프) | **비가 와도 완전 무음** |
| 2 | `sfx_levelup.mp3` | 레벨업 팡파레 | **레벨업 화면에 소리 0** (만세 모션만) |
| 3 | `sfx_focus.mp3` | AF 초점 잡히는 '삐빅' | **뷰파인더에 소리 0** |
| 4 | `sfx_polaroid.mp3` | 폴라로이드 인화 모터 + 종이 배출 | 결과 카드 뜨기 전 0.3초 무음 |
| 5 | `sfx_cat_meow.mp3` | 고양이 야옹 | 쓰다듬기·간식·인사 전부 `sfx_sparkle` |
| 6 | `sfx_step_grass.mp3` (`sand`/`snow`/`water`/`stone`) | 지형별 발소리 | **자갈 소리 하나로 전국을 걷습니다** |
| 7 | `amb_insects.mp3` | 여름밤 풀벌레 (루프) | 여름밤에도 `amb_night` 하나 |
| 8 | `sfx_door_open.mp3` | 문 열고 닫기 | 집·랜드마크 입장이 `sfx_tap` |
| 9 | `sfx_tea.mp3` | 허브차 따르기 + 호호 불기 | `sfx_sparkle` |
| 10 | `sfx_money.mp3` | 동전 짤랑 | 구매 `sfx_buy` / 보수 `sfx_reward` 재사용 |

---

## 2. P0 — 지금 완전히 무음인 순간

### 🌦 날씨 · 자연

| 파일 | 루프 | 길이 | 무엇을 | 지금은 | 붙일 곳 |
|---|---|---|---|---|---|
| `amb_rain.mp3` ⭐ | 🔁 | 60초 | 창밖에 비 내리는 소리. 물웅덩이·빗줄기 연출과 함께 | **무음** (비 전용 분기 없음) | `WorldScene.updateAmbience()` |
| `amb_insects.mp3` ⭐ | 🔁 | 60초 | 여름밤 풀벌레 (숲·습지) | `amb_night` 재사용 | 〃 (밤 + 여름 + 숲/습지) |
| `sfx_thunder.mp3` | ▶️ | 2.5초 | 장마철 비 올 때 가끔 멀리서 '우르릉' | 없음 | `WorldScene` 날씨 타이머 (30~60초 랜덤) |
| `sfx_wind_gust.mp3` | ▶️ | 1.5초 | 강풍 날씨 돌풍 — 풀이 물결치는 연출은 있는데 소리가 없음 | `amb_wind`만 | `Grass` 돌풍 트리거 |
| `amb_snow.mp3` | 🔁 | 60초 | 눈 내리는 겨울의 아주 낮은 바람 (거의 정적) | 없음 (`amb_wind`/`amb_night`) | `WorldScene.updateAmbience()` |

### 🎁 성장 · 보상 연출

| 파일 | 루프 | 길이 | 무엇을 | 지금은 | 붙일 곳 |
|---|---|---|---|---|---|
| `sfx_levelup.mp3` ⭐ | ▶️ | 1.5~2.5초 | 짧고 따뜻한 팡파레 (과하지 않게, 힐링 톤) | **무음** | `LevelUpOverlay` 등장 |
| `sfx_stamp.mp3` | ▶️ | 0.4초 | 도장 '쿵' — 작은 기념 / 업적 / 라이퍼 등급 | `sfx_notify`·`sfx_reward` 재사용 | `Healing.unlock`(18곳), `Achievements.tick` |
| `sfx_money.mp3` | ▶️ | 0.5초 | 동전 짤랑 (구매·매입·보수) | `sfx_buy` / `sfx_reward` 재사용 | `Overlays` 상점, 의뢰 보수 |
| `sfx_rankup.mp3` | ▶️ | 1초 | 라이퍼 등급·칭호 상승 전용 (선택) | `sfx_reward` | `StatsOverlay` |

### 🐈 생물 상호작용

| 파일 | 루프 | 길이 | 무엇을 | 지금은 | 붙일 곳 |
|---|---|---|---|---|---|
| `sfx_cat_meow.mp3` ⭐ | ▶️ | 0.6초 | 야옹 (쓰다듬기·간식·벤치) | `sfx_sparkle` | `WorldScene.petCat()`, `feedCat()` |
| `sfx_cat_purr.mp3` | 🔁 | 2초 | 그르렁 (쓰다듬는 동안) | 없음 | `petCat()` |
| `sfx_cat_hiss.mp3` | ▶️ | 0.7초 | 하악 — 새를 노릴 때 긴장 | 없음 | `WorldScene` 고양이 잠복 시작 |
| `sfx_flock_wings.mp3` | ▶️ | 1.5초 | 철새 V자 떼의 날갯짓 | **무음** (`CritterType.FLOCK`) | `Healing.updateCritters` |
| `sfx_gull_cry.mp3` | ▶️ | 1초 | 갈매기 울음 한 마디 (먼 갈매기 연출) | `amb_sea`에 섞여 있음 | 〃 (`FAR_GULL`) |

### 🏠 집 · 랜드마크 생활

| 파일 | 루프 | 길이 | 무엇을 | 지금은 | 붙일 곳 |
|---|---|---|---|---|---|
| `sfx_door_open.mp3` ⭐ | ▶️ | 0.5초 | 나무 문 삐걱 + 열림 (집·랜드마크) | `sfx_tap` | `WorldScene.enterHome()/enterLandmark()` |
| `sfx_door_close.mp3` | ▶️ | 0.5초 | 문 닫힘 '턱' | 없음 | 씬 전환 뒤 |
| `sfx_tea.mp3` ⭐ | ▶️ | 1.5초 | 찻주전자 따르는 소리 + 호호 불기 | `sfx_sparkle` | `HomeScene.brewTea()` |
| `sfx_bed.mp3` | ▶️ | 1.5초 | 이불 파고들기 + 하품 | `sfx_sparkle` | `HomeScene.sleepNow()` |
| `sfx_morning.mp3` | ▶️ | 2초 | 아침 — 부드러운 차임 + 새소리 | `sfx_sparkle` + `sfx_bird_chirp1` | 〃 (기상) |
| `amb_room.mp3` | 🔁 | 60초 | 실내 룸톤(낮은 공기 + 발소리 울림) — **랜드마크 실내가 지금 완전 무음** (`stopAmb()`) | 무음 | `LandmarkScene` |

### 📷 카메라 (연출은 화려한데 소리가 없음)

| 파일 | 루프 | 길이 | 무엇을 | 지금은 | 붙일 곳 |
|---|---|---|---|---|---|
| `sfx_focus.mp3` ⭐ | ▶️ | 0.3초 | AF 초점 잡히는 '삐빅' (AF 박스가 좁혀오는 순간) | **무음** | `Viewfinder` AF 획득 |
| `sfx_zoom.mp3` | ▶️ | 0.5초 | 렌즈 줌 모터 '찍찍찍' (카메라 모드 진입/이탈) | **무음** | `WorldScene` 카메라 모드 토글 |
| `sfx_polaroid.mp3` ⭐ | ▶️ | 1.2초 | 인화 모터 + 종이 배출 (지금 0.3초 정적) | 무음 | `PhotoResultOverlay.update()` 도입부 |
| `sfx_flash_charge.mp3` | ▶️ | 0.8초 | 플래시 충전 '찌이잉' (밤 촬영) | 없음 | `WorldScene.shutterFx()` |
| `sfx_mirror_slap.mp3` | ▶️ | 0.3초 | 미러 슬랩 — **렌즈교환식 바디 전용** (컴팩트와 소리가 다름) | `sfx_shutter` 하나로 공용 | 촬영 시 바디 종류 분기 |

### 🚲 이동 — 자전거 · 발소리

| 파일 | 루프 | 길이 | 무엇을 | 지금은 | 붙일 곳 |
|---|---|---|---|---|---|
| `sfx_bike_roll.mp3` | 🔁 | 2~4초 | 자전거 굴러가는 소리(타이어 + 체인) — **속도에 따라 재생 속도를 올려 씁니다** | **무음** ("자전거는 소리 없이 쌩~") | `WorldScene` `player.bike` |
| `sfx_bike_skid.mp3` | ▶️ | 0.6초 | 급제동 '끼익~' (충돌 직전) | `sfx_bike_brake` 재사용 | 충돌 판정 |
| `sfx_step_grass.mp3` ⭐ | 🔁성 | 0.6초 | 풀·꽃밭 발소리 (사각사각) | **자갈 소리로 통일** | `WorldScene` 타일별 발소리 |
| `sfx_step_sand.mp3` ⭐ | 🔁성 | 0.6초 | 모래·갯벌 (사박사박) | 〃 | 〃 (`T.SAND`, `T.REED`) |
| `sfx_step_snow.mp3` ⭐ | 🔁성 | 0.6초 | 눈 밟는 소리 (뽀득뽀득, 겨울) | 〃 | 〃 (겨울 눈 덮인 타일) |
| `sfx_step_water.mp3` ⭐ | 🔁성 | 0.6초 | 얕은 물·습지 (철퍽) | 〃 | 〃 (`T.WATER` 변두리) |
| `sfx_step_stone.mp3` ⭐ | 🔁성 | 0.6초 | 돌바닥·광장·실내 (또각또각) | 나무(`_wood`) 하나 | 〃 (`T.PLAZA`, `T.FLOOR`) |

> 발소리는 기존 `sfx_step_gravel1/2` 규칙처럼 **걷기/달리기 2벌(`_1`/`_2`)** 로 주시면 가장 좋고,
> 1벌만 주셔도 제가 `rate`로 걷기/달리기를 구분해 붙이겠습니다.

### 🍕 피자 · 요리

| 파일 | 루프 | 길이 | 무엇을 | 지금은 | 붙일 곳 |
|---|---|---|---|---|---|
| `sfx_dough.mp3` | ▶️ | 1초 | 반죽 늘리고 밀대 미는 소리 | `sfx_tap` | `DoughOverlay` 도우 선택 |
| `sfx_sauce.mp3` | ▶️ | 0.8초 | 소스 바르는 '쓱쓱' | 없음 | `BakeOverlay` |
| `sfx_topping.mp3` | ▶️ | 0.6초 | 토핑 흩뿌리는 소리 (특산 재료 선택) | `sfx_tap` | 〃 |
| `sfx_oven_door.mp3` | ▶️ | 0.8초 | 화덕 철문 여닫는 경첩 소리 | `sfx_tap` | 〃 (굽기 시작) |
| `sfx_sizzle.mp3` | ▶️ | 1초 | 반죽을 화덕에 넣을 때 '치익' | `amb_fire`만 | 〃 |
| `sfx_slice.mp3` | ▶️ | 0.5초 | 피자 자르는 소리 | 없음 | 완성 결과 |
| `sfx_bite.mp3` | ▶️ | 0.5초 | 바삭 베어무는 소리 (EAT보다 경쾌) | `sfx_eat` | `WorldScene` 간식 |
| `sfx_cheese.mp3` | ▶️ | 0.4초 | 치즈 늘어나는 소리 (선택) | 없음 | 〃 |

---

## 3. 새 울음 다양화 — 파일 12개로 598종 커버

지금 새 소리는 **6종**(`chirp1/2`, `cuckoo1/2`, `owl`, `crow`)뿐이라, 두루미를 찍어도 참새 소리가 납니다.
그런데 `Data.kt`가 598종을 **13개 체형(`template`)으로 분류**해 두었으니, **체형별 울음 1개**만 있으면 전 종이 자기 소리를 갖게 됩니다.
(`template 4 = 올빼미`는 `sfx_owl`이 이미 있으므로 12개면 충분합니다.)

| 파일 | template | 해당 무리 | 소리 느낌 |
|---|---|---|---|
| `sfx_call_songbird.mp3` | 0 | 참새·박새·멧새·휘파람새 (가장 흔함) | 짧고 맑은 지저귐 2~3음 |
| `sfx_call_duck.mp3` | 1 | 오리·기러기·고니 | 낮은 '꽥', 물 위 울림 |
| `sfx_call_egret.mp3` | 2 | 백로·왜가리·두루미·황새 | 거친 '까악' 또는 멀리 퍼지는 울음 |
| `sfx_call_raptor.mp3` | 3 | 수리·매·말똥가리 | 높고 날카로운 '끼이익' |
| `sfx_call_shorebird.mp3` | 5 | 도요·물떼새 (갯벌) | 가늘고 빠른 '삐삐' |
| `sfx_call_seabird.mp3` | 6 | 갈매기·바다오리 | 갈매기 울음 |
| `sfx_call_woodpecker.mp3` | 7 | 딱다구리 | **나무 두드리는 '딱딱딱딱'** (울음보다 이게 상징적) |
| `sfx_call_dove.mp3` | 8 | 비둘기·멧비둘기 | 낮은 '구구구' |
| `sfx_call_kingfisher.mp3` | 9 | 물총새·파랑새·호반새 | 날카로운 '치이-' |
| `sfx_call_pitta.mp3` | 10 | 팔색조 (희귀) | 신비롭고 낮은 휘파람 |
| `sfx_call_pheasant.mp3` | 11 | 꿩·뜸부기 | '꾸엑' 짧은 한 마디 |
| `sfx_call_swallow.mp3` | 12 | 제비·칼새 (하늘) | 높고 빠른 '챠챠챠' |

**대표종 전용 (있으면 확실히 좋아지는 4종)**

| 파일 | 무엇 | 왜 |
|---|---|---|
| `sfx_magpie.mp3` | 까치 '까악까악' | 도시·마을에서 가장 먼저 눈에 띄는 새 |
| `sfx_crane.mp3` | 두루미 | 겨울 철원의 상징. 지금은 참새 소리가 납니다 |
| `sfx_sparrow.mp3` | 참새 짹짹 | 시작 마을의 얼굴 |
| `sfx_nightjar.mp3` | 소쩍새 '솟쩍' | 여름밤 `sfx_owl` 대신 |

**연결 방식(제가 구현)**: 새가 등장·도망·촬영될 때 `def.art.template`으로 소리를 고르고,
대표종은 전용 파일이 있으면 우선 사용 → 없으면 체형 소리 → 그것도 없으면 기존 `chirp1/2`로 폴백. **파일이 일부만 있어도 안전하게 동작**합니다.

---

## 4. P2 — BGM · UI

### 🎵 지역 BGM (`docs/plan/P04_audio.md` 계획 중 아직 없는 것)

| 파일 | 해당 지역 | 무드 | 길이 |
|---|---|---|---|
| `bgm_town.m4a` | 서울·대전·전주·대구·광주·울산 등 도시 | 어쿠스틱 기타 + 피아노, 산책 템포 | 90~150초 루프 |
| `bgm_wetland.m4a` | 춘천·공릉천·우포늪·주남저수지 | 잔잔한 패드 + 물방울 퍼커션 | 〃 |
| `bgm_forest.m4a` | 광릉숲 등 숲 중심 | 미니멀 피아노 + 새소리 여백 | 〃 |
| `bgm_jeju.m4a` | 제주·하도리 | 오보에/우쿨렐레, 섬 바람 | 〃 |

> `bgm_mountain.m4a` · `bgm_sea.m4a`는 이미 있어서, **랜드마크 실내가 계속 `bgm_home`을 빌려 쓰는 것**도 함께 해결하고 싶습니다.
> 랜드마크 32곳이 테마별로 3~4곡만 나눠 가져도 충분합니다: `bgm_landmark_view.m4a`(전망대·등대) / `bgm_landmark_hall.m4a`(박물관·탐조센터) / `bgm_landmark_hanok.m4a`(한옥·전통).

### 🖱 UI · 연출 폴리시

| 파일 | 무엇 | 지금은 |
|---|---|---|
| `sfx_menu_open.mp3` / `sfx_menu_close.mp3` | 메뉴(☰) 열고 닫기 | `sfx_tap` |
| `sfx_page_flip.mp3` | 도감·사진집 페이지 넘김 | `sfx_tap` |
| `sfx_dialog_tick.mp3` | 대사창 글자 타이핑 (0.03초 간격, 아주 작게) | **대사창은 글자가 한 번에 뜹니다 — 타이핑 연출도 같이 넣어 드릴게요** |
| `sfx_error.mp3` | '지금은 안 돼요' (실패와 구분되는 부드러운 안내음) | `sfx_fail` |
| `sfx_toggle.mp3` | 설정 스위치·탭 전환 | `sfx_tap` |
| `sfx_stamp.mp3` | (위 P0와 동일) 도장 찍기 | `sfx_notify` |

---

## 5. 체크리스트

올리실 때 여기에 체크해 두시면 진행 상황을 서로 바로 알 수 있습니다. `python3 tools/audio_check.py` 로도 확인됩니다.

```
[ ] amb_rain.mp3            [ ] sfx_levelup.mp3        [ ] sfx_focus.mp3
[ ] sfx_polaroid.mp3        [ ] sfx_cat_meow.mp3       [ ] sfx_step_grass.mp3
[ ] sfx_step_sand.mp3       [ ] sfx_step_snow.mp3      [ ] sfx_step_water.mp3
[ ] sfx_step_stone.mp3      [ ] amb_insects.mp3        [ ] sfx_door_open.mp3
[ ] sfx_tea.mp3             [ ] sfx_money.mp3          [ ] sfx_stamp.mp3
[ ] sfx_thunder.mp3         [ ] sfx_wind_gust.mp3      [ ] amb_snow.mp3
[ ] sfx_cat_purr.mp3        [ ] sfx_cat_hiss.mp3       [ ] sfx_flock_wings.mp3
[ ] sfx_gull_cry.mp3        [ ] sfx_door_close.mp3     [ ] sfx_bed.mp3
[ ] sfx_morning.mp3         [ ] amb_room.mp3           [ ] sfx_zoom.mp3
[ ] sfx_flash_charge.mp3    [ ] sfx_mirror_slap.mp3    [ ] sfx_bike_roll.mp3
[ ] sfx_bike_skid.mp3       [ ] sfx_dough.mp3          [ ] sfx_sauce.mp3
[ ] sfx_topping.mp3         [ ] sfx_oven_door.mp3      [ ] sfx_sizzle.mp3
[ ] sfx_slice.mp3           [ ] sfx_bite.mp3           [ ] sfx_cheese.mp3
[ ] sfx_call_songbird.mp3   [ ] sfx_call_duck.mp3      [ ] sfx_call_egret.mp3
[ ] sfx_call_raptor.mp3     [ ] sfx_call_shorebird.mp3 [ ] sfx_call_seabird.mp3
[ ] sfx_call_woodpecker.mp3 [ ] sfx_call_dove.mp3      [ ] sfx_call_kingfisher.mp3
[ ] sfx_call_pitta.mp3      [ ] sfx_call_pheasant.mp3  [ ] sfx_call_swallow.mp3
[ ] sfx_magpie.mp3          [ ] sfx_crane.mp3          [ ] sfx_sparrow.mp3
[ ] sfx_nightjar.mp3        [ ] bgm_town.m4a           [ ] bgm_wetland.m4a
[ ] bgm_forest.m4a          [ ] bgm_jeju.m4a           [ ] bgm_landmark_view.m4a
[ ] bgm_landmark_hall.m4a   [ ] bgm_landmark_hanok.m4a [ ] sfx_menu_open.mp3
[ ] sfx_menu_close.mp3      [ ] sfx_page_flip.mp3      [ ] sfx_dialog_tick.mp3
[ ] sfx_error.mp3           [ ] sfx_toggle.mp3
```

---

## 6. 파일을 올리면 제가 하는 일

1. `Audio.kt`의 `Sfx` enum / `Steps` enum에 새 항목 추가 (기존 순서는 건드리지 않습니다)
2. 위 표의 "붙일 곳"에 맞춰 호출 지점 연결 — 발소리는 타일 종류, 새 울음은 `template`·종 ID로 분기
3. `sfxOn`/`musicOn` 설정 토글과 페이드 규칙 그대로 유지 (환경음은 `playAmb`, BGM은 `playBgm`)
4. `README.md`의 🔊 사운드 표·`P04` 문서 갱신, `tools/audio_check.py`로 누락 파일 확인
5. 빌드(`./gradlew assembleDebug`) + `tools/typecheck.sh` 통과 확인, APK 크기 변화 보고

> 순서 팁: **P0 10개 → 새 울음 12개 → 나머지** 순으로 주시면, 중간중간 붙여서 바로 들어보실 수 있습니다.
> 한 번에 다 주셔도 되지만, 그때는 커밋을 여러 개로 나눠 올리겠습니다.
