# RELEASE.md — 피자와 새 릴리스 절차서

> [P10] 이 문서는 **v 태그 하나로 AAB·APK가 만들어져 스토어 제출 직전까지 가는 절차**를 정의한다.
> 운영자(OWNER)는 이 문서의 체크리스트를 위에서 아래로 따라 가면 된다.
> 워크플로: `.github/workflows/release-aab.yml` (기존 push CI `android-apk.yml`과 독립)

---

## 1. 전체 흐름 한눈에

```
버전 확정 → 게이트 통과 → git tag vX.Y.Z → push tag
    → GitHub Actions "Release AAB" 실행 (AAB + APK 빌드·서명)
    → GitHub Release에 첨부 확인
    → Play Console에 AAB 업로드 + 스토어 자료 붙여넣기
    → 심사 제출 (OWNER)
```

---

## 2. 버전 규칙 (semver)

- 형식: `vMAJOR.MINOR.PATCH` (+ 필요시 프리릴리스 접미사 `-betaNN`, `-rcNN`).
- **시즌 라벨은 통합 담당이 부여한다** (docs/plan/OVERVIEW.md 상단 참고 — 이 계획 시즌은
  v0.4.2 이후 시리즈). 이 문서의 절차는 라벨과 무관하게 동작한다.
- PATCH: 버그 수정 · 마이너: 기능 추가/시즌 · 메이저: 구조 변경·스토어 정책급 변화.

### versionCode 규칙 (자동 계산)

```
versionCode = major * 10000 + minor * 100 + patch      (릴리스 워크플로가 태그에서 자동 계산)
예) v0.4.2 → 402, v0.5.0 → 500, v1.0.0 → 10000
```

- `app/build.gradle.kts`의 `[P10]` 버전 라인: `-PVERSION_CODE/-PVERSION_NAME`이 없으면
  **기존 기본값**(현재 `9` / `0.4.2-beta01`)을 쓴다 — 로컬·push CI 빌드는 영향 없음.
- ⚠️ 프리릴리스(`-beta01` 등)는 같은 X.Y.Z의 정식판과 versionCode가 같다.
  → **Play Console에는 정식 태그(vX.Y.Z)의 AAB만 업로드한다.** 프리릴리스 AAB는
  GitHub Release 첨부물로만 사용 (베타 테스터 배포용).
- versionCode는 절대 되돌리지 않는다. 핫픽스도 항상 PATCH를 올린다 (v0.4.2 → v0.4.3).

---

## 3. 릴리스 전 체크리스트 (게이트)

하나라도 실패하면 태그를 찍지 않는다.

- [ ] `./gradlew assembleDebug` 성공 (로컬 또는 push CI 초록불)
- [ ] `python3 tools/kt_check.py` 통과
- [ ] 맵/스폰 로직을 건드린 릴리스라면 `tools/MapTest.kt` 통과
- [ ] **연기 테스트**(에뮬레이터 또는 실기기, 10분):
  - [ ] 타이틀 → 지역 선택 → 서울 월드 진입, 터널로 인접 지역 왕복 1회
  - [ ] 화덕피자 1회 굽기(타이밍 게이지) + 간식 먹기
  - [ ] 카메라 모드로 새 1회 촬영 → 폴라로이드 결과 확인
  - [ ] 설정에서 음악/효과음·화면연출 옵션 on/off → 즉시 반영 확인
  - [ ] 앱 완전 종료 후 재시작 → 세이브 복원 확인
- [ ] 이번 릴리스에 포함된 신기능의 PR 체크리스트가 전부 체크됨
- [ ] 스토어 문구가 신기능과 일치하는지 검수 (`store/listing_ko.md`, `store/listing_en.md`)
- [ ] 스크린샷이 최신 UI와 일치하는지 검수 (`store/SCREENSHOTS.md`)

---

## 4. 릴리스 절차 (태그 → Release → Play Console)

### 4.0 워크플로 활성화 (최초 1회)

> ⚠️ 에이전트 세션의 GitHub App은 `workflows` 권한이 없어 `.github/workflows/` 아래 파일을
> push할 수 없다. 이 때문에 릴리스 워크플로는 **`tools/ci/release-aab.yml` 미러**로 저장되어 있다
> (기존 `tools/ci/android-apk.yml`과 같은 컨벤션).
> **활성화 방법**: 웹 UI(GitHub 코드 페이지 "Add file → Create new file") 또는 권한 있는 계정으로
> `tools/ci/release-aab.yml` 내용을 그대로 `.github/workflows/release-aab.yml` 경로에 복사 커밋한다.
> 이후 이 절차의 태그 push가 곧바로 릴리스 빌드를 트리거한다. (미러와 실제 파일은 내용을 항상 일치시킬 것)

### 4.1 태그 & 빌드

```bash
# main이 릴리스 대상 커밋이며 §3 게이트를 통과했는지 확인 후:
git tag v0.4.3                 # 예시 — 실제 버전으로
git push origin v0.4.3
```

- GitHub Actions → **Release AAB (Play Store)** 워크플로가 자동 실행된다.
  - versionName = `0.4.3`, versionCode = `403` (태그에서 자동 계산 — §2)
  - 시크릿 키스토어가 등록돼 있으면 정식 키 서명, 없으면 debug 키 서명 (§5)
- 워크플로가 초록불이면 **Releases** 페이지에 `pizza-and-bird-release-aab` (AAB)와
  `pizza-and-bird-release-apk` (APK)가 첨부됐는지 확인.
- ⚠️ 태그 트리거 워크플로는 **태그가 가리키는 커밋에 워크플로 파일이 있어야 실행된다.**
  최초 릴리스 태그는 반드시 이 워크플로가 main에 머지된 이후에 찍는다.
- 검증용 리허설: Actions → Run workflow(수동 실행)로 태그 없이 빌드만 확인 가능.

### 4.2 Play Console 업로드 (제출은 OWNER — 계정/가격 결정 필요)

1. **테스트 트랙 먼저**: 내부 테스트 → 업로드한 AAB가 설치·실행되는지 확인.
2. 프로덕션(또는 비공개 테스트) → 새 릴리스 → **AAB 업로드** (GitHub Release 첨부물).
3. 릴리스 노트 입력 — 초안: `store/listing_ko.md` §릴리스 노트.
4. 스토어 등록정보: 제목·설명(국/영) — `store/listing_ko.md`, `store/listing_en.md` 복붙.
5. 그래픽: 아이콘 512×512, 피처 그래픽 1024×500, 스크린샷 8컷 — `store/SCREENSHOTS.md`.
6. 앱 콘텐츠 양식: 데이터 안전·콘텐츠 등급 등 — `store/PLAY_FORMS.md` 답변 초안 복붙.
7. 개인정보처리방침 URL: `store/PRIVACY.md`를 정적 호스팅(GitHub Pages 등)한 URL.
8. 심사 제출.

---

## 5. 서명 키 관리

- 시크릿 4종: `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`
  등록 절차는 `release-aab.yml` 상단 주석과 동일. (기존 push CI의 `PB_*` 시크릿과 별개 이름.)
- **Play App Signing 사용 권장**: 첫 AAB 업로드 시 Play가 서명 키를 관리하고, 우리 키는
  업로드 키(upload key)가 된다. 이후 분실해도 Play Console에서 업로드 키 재설정 가능.
- ⚠️ 첫 Play 업로드는 반드시 정식 키스토어로 서명된 AAB로 한다. debug 키로 서명해도 Play가
  재서명하긴 하지만, 이후 매 릴리스를 같은 debug 키로 서명해야 하므로 금물.
- 키스토어 파일 자체는 저장소에 커밋하지 않는다 (`.gitignore`에 `*.keystore` 확인).
  백업은 OWNER가 오프라인 금고 2곳 이상에 보관.

---

## 6. 롤백 절차

1. Play Console → 프로덕션 → **릴리스 중단(halt)** — 신규 사용자에게만 중단되고 기존 설치자는 유지.
2. 이전 정식 태그의 AAB를 Release 페이지에서 내려 **이전 버전으로 새 릴리스** 생성
   (Play Console은 versionCode가 낮은 업로드도 "롤백 릴리스"로 허용 — 기존 사용자 업데이트는
   versionCode가 높아야 되돌려지므로, 긴급 수정이 필요하면 PATCH를 올린 핫픽스 태그가 정석).
3. 핫픽스 절차: main에 수정 커밋 → §3 게이트 → `vX.Y.(Z+1)` 태그 → §4 절차 재수행.
4. 사후: 원인을 이 파일 §3 체크리스트에 반영 (재발 방지 항목 추가).

---

## 7. 스토어 갱신 주기 메모

| 항목 | 주기 | 담당 |
|---|---|---|
| 리스팅 문구(국/영) | 마이너 릴리스(vX.**Y**.0)마다 신기능 반영 검수 | P10 소유 |
| 스크린샷 | UI가 크게 바뀐 릴리스마다 (최소 시즌당 1회) | P10 소유 |
| 데이터 안전·개인정보 양식 | 연 1회 + 권한/네트워크 변화가 생길 때 즉시 | OWNER |
| 릴리스 노트 | 매 릴리스 | 통합 담당 초안 → OWNER 확정 |
| 타깃 API 레벨 | Google 연례 요구 공고 시 (현재 targetSdk 35) | 통합 담당 |

---

## 8. 「사계」 시즌 마감(W3) 체크리스트 — docs/plan/P10_release.md §6

- [ ] 최종 스크린샷 8컷 촬영·첨부 (store/SCREENSHOTS.md §스토리보드 — 컷 5~8 신기능 반영 교체)
- [ ] 리스팅 문구에 최종 기능 반영 (계절·앨범·사이드 스토리·지역 특산 피자 문구 검수)
- [ ] 통합 QA(OVERVIEW §7) 결과를 store/PLAY_FORMS.md에 근거 표기
- [ ] 시즌 버전 태그 릴리스 실행 + Release 페이지에 AAB/APK 첨부 확인
- [ ] Play Console 입력 순서(§4.2)를 따라 심사 제출 직전 상태 도달 (제출은 OWNER)
