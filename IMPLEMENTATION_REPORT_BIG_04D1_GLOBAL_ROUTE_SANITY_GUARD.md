# HankkiTravel BIG-04D.1 Global Route Sanity Guard 구현 결과

- 작업일: 2026-09-18
- Backend 기능 커밋: `857704a369bf15da090073587196d15c7bdb10f8`
- Frontend 기능 커밋: `3d6a7d48c1a8a2c0fc9354cfe45507b65a165681`
- Backend 기준 브랜치: `main`
- Frontend 기준 브랜치: `main`

## 판정 기준

정적 소스 검수, 로컬 자동 테스트, GitHub Actions/AWS 배포, 운영 API 수용 검증을 분리해 확인했다. 운영 검증은 전용 guest/profile/trip을 생성해 실제 TourAPI·Kakao route 결과로 Planner를 호출했으며, 생성한 trip은 모든 성공·실패 경로에서 삭제했다. 운영 DB에 직접 접속하거나 운영 데이터를 임의 수정하지 않았다.

## 구현 범위

### Backend

- Planner 응답에 additive `routeSanity` summary와 각 leg의 `burdenSeverity`, `burdenReasons`를 추가했다.
- PUBLIC_TRANSIT은 `CURRENT_DATA` route가 제공하는 실제 duration, transfer, explicit walking만 평가한다.
- CAR은 Haversine 직선거리만 평가하며 자동차 시간과 도로거리를 만들지 않는다.
- 평가 가능한 leg만 집계하고 미확인 leg를 0으로 합산하지 않는다.
- 계산 실패 시 Planner 전체를 실패시키지 않고 `NOT_EVALUATED`로 내린다.
- 결과는 요청 시점에 결정적으로 계산하며 DB, 캐시, anchor에 저장하지 않는다.

### Frontend

- Day 상단에 오늘 이동 부담, 근거 coverage, 문제 구간 이동, 상황별 재선택 CTA를 표시한다.
- 문제 leg를 Timeline과 Map에서 같은 leg id로 강조한다.
- CAR 경고는 직선거리임을 표시하고 PUBLIC_TRANSIT은 실제 시간·환승 근거만 표시한다.
- `그래도 이 일정 유지`는 화면 상태만 닫으며 anchor나 추천 점수를 변경하지 않는다.
- 완료 선택의 compact summary에 이동 부담 badge를 표시한다.

## Thresholds

| 평가 | CAUTION 시작 | HIGH 시작 | 추가 조건 |
| --- | ---: | ---: | --- |
| PUBLIC_TRANSIT 단일 leg | 60분 | 120분 | 환승 2회 이상은 최소 CAUTION |
| CAR 단일 leg 직선거리 | 20km | 40km | 자동차 시간 추정 없음 |
| PUBLIC_TRANSIT Day 합계 | 120분 | 240분 | 평가 가능한 leg duration만 합산 |

설정 경로는 다음과 같다.

```text
planner.route-sanity.public-transit.warn-minutes=60
planner.route-sanity.public-transit.critical-minutes=120
planner.route-sanity.public-transit.warn-transfers=2
planner.route-sanity.car.warn-distance-km=20
planner.route-sanity.car.critical-distance-km=40
planner.route-sanity.day.warn-total-transit-minutes=120
planner.route-sanity.day.critical-total-transit-minutes=240
```

모든 값은 양수이며 warn이 critical보다 작아야 한다. 위반 시 애플리케이션 시작 단계의 생성자 검증에서 실패한다.

## JEJU Live

운영 정본 `https://api.hankki.r-e.kr`에서 장거리 후보를 의도적으로 선택해 확인했다.

| 이동수단 | Longest leg | Total burden | Evidence | Severity | CTA |
| --- | ---: | ---: | ---: | --- | --- |
| CAR | 직선거리 67,293m | 직선거리 67,293m | 1/1, 100% | HIGH | 가까운 식당 다시 보기, 그대로 유지 |
| PUBLIC_TRANSIT | 약 189.52분, 직선 참고 43,015m | 약 189.52분 | 1/1, 100% | HIGH | 가까운 식당 다시 보기, 그대로 유지 |

- CAR 응답의 시간은 `null`이었으며 가짜 자동차 이동시간 생성은 0건이다.
- PUBLIC_TRANSIT은 실제 route duration으로 판정했으며 임의 직선 geometry를 대중교통 경로로 사용하지 않았다.
- 경고가 anchor를 자동 삭제하거나 다른 후보로 자동 교체한 사례는 0건이다.

## GYEONGJU Live

### 근거리 단일 leg

| 이동수단 | 결과 | Evidence | 판정 |
| --- | --- | ---: | --- |
| CAR | 직선거리 380m | 1/1, 100% | NORMAL |
| PUBLIC_TRANSIT | upstream route 근거 없음 | 0/1, 0% | NOT_EVALUATED |

PUBLIC_TRANSIT의 근거 부재를 0분 또는 이용 불가로 오판하지 않았다.

### 1박2일 정상 경로

| Day | 확인 흐름 | Longest leg | Evidence | Severity |
| --- | --- | ---: | ---: | --- |
| Day 1 | DAY_FOCUS → 저녁 → 숙소 | 직선거리 380m | 2/2, 100% | NORMAL |
| Day 2 | DAY_FOCUS → 점심 → 관광지 | 직선거리 526m | 2/2, 100% | NORMAL |

경주 정상 근거리에서 CAUTION/HIGH가 발생하지 않았으므로 false positive는 0건이다.

## Evidence

- 운영 JEJU 장거리: CAR 1/1, PUBLIC_TRANSIT 1/1 평가 가능
- 운영 GYEONGJU 단일 근거리: CAR 1/1, PUBLIC_TRANSIT 0/1
- 운영 GYEONGJU 1박2일: Day 1 2/2, Day 2 2/2
- 부분 coverage와 unknown leg 처리는 unit test에서 별도로 검증했다.
- 동일 입력의 결과가 같은지 unit test에서 검증했다.

## CTA 및 UI 검증

| 항목 | 결과 | 근거 |
| --- | --- | --- |
| Near Restaurant Re-entry | PASS | 기존 Restaurant alternative flow를 연다. |
| Near Attraction Re-entry | PASS | 기존 Attraction `NEARBY_COURSE` flow를 연다. |
| Near Stay Re-entry | PASS | 기존 Stay progressive radius 결과를 연다. |
| Keep Existing Itinerary | PASS | 화면 상태만 닫고 anchor mutation을 호출하지 않는다. |
| Timeline Problem Leg Focus | PASS | 문제 leg로 scroll/focus하고 같은 leg id를 활성화한다. |
| Map Problem Leg Highlight | PASS | desktop hover/mobile tap과 외부 active leg id를 함께 처리한다. |
| Compact Summary Badge | PASS | 경고가 연결된 선택에 이동 부담 badge를 표시한다. |

## Persistence

| 항목 | 기록 수 |
| --- | ---: |
| sanity write | 0 |
| route write | 0 |
| candidate write | 0 |
| 신규 Flyway migration | 0 |
| schema 변경 | 0 |
| 영구 warning dismissed flag | 0 |

`RouteSanityEvaluator`는 persistence dependency가 없는 요청 범위 계산기다. 기존 MySQL 통합 테스트는 Planner 호출 전후 `tourism_places`, `restaurants` 행 수가 동일함을 검증한다. 운영 수용 검증에서 생성한 trip은 모두 DELETE API로 정리했다. guest/profile 삭제 API가 없어 검증 과정에서 만든 guest 4개와 profile 11개는 남아 있으며, 실제 사용자 trip/anchor는 변경하지 않았다.

## 자동 검증

### Backend

```text
Java 21 / Docker 29.6.1
clean verify
BUILD SUCCESS
Tests run: 152, Failures: 0, Errors: 0, Skipped: 0
```

추가 집중 검증:

```text
RouteSanityEvaluatorTest
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0

TripPlannerMysqlIntegrationTest
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
```

PUBLIC_TRANSIT/CAR의 NORMAL·CAUTION·HIGH, 최장 leg, Day 합계, partial/unknown, 설정 검증, 결정성을 포함한다.

### Frontend

```text
npm test -- --run
Test Files: 10 passed
Tests: 64 passed

npm run build
PASS

npm run test:e2e
development: 4 passed
production: 14 passed
total: 18 passed
```

NORMAL/CAUTION/HIGH/NOT_EVALUATED, 세 가지 재진입 CTA, 일정 유지, Timeline/Map 강조와 모바일 회귀를 포함한다.

## GitHub Actions 및 배포

| 저장소 | 실행 | 결과 |
| --- | --- | --- |
| Backend | [Backend CI and GHCR #35236912776](https://github.com/jeon950523/hankki_travel_back/actions/runs/35236912776) | SUCCESS |
| Frontend | [Frontend CI #35236940662](https://github.com/jeon950523/hankki-travel/actions/runs/35236940662) | SUCCESS |

Backend 실행에서 전체 검증, GHCR SHA 이미지 발행, AWS OIDC, SSM EC2 배포, health check, GitOps image SHA 기록이 모두 성공했다. Frontend 실행에서 unit, build, development/production E2E가 모두 성공했다.

운영 확인:

```text
https://api.hankki.r-e.kr/actuator/health = HTTP 200
https://hankki.kro.kr = HTTP 200
```

## 종료 조건

```text
Global Route Sanity Summary            PASS
Longest Leg Detection                  PASS
PUBLIC_TRANSIT High Burden Detection   PASS
CAR Long-distance Detection            PASS
Unknown Leg Zero-assumption            0

Near Restaurant Re-entry               PASS
Near Attraction Re-entry               PASS
Near Stay Re-entry                     PASS
Keep Existing Itinerary                PASS
Automatic Anchor Mutation              0

Timeline Problem Leg Focus             PASS
Map Problem Leg Highlight              PASS

JEJU Long-route Live                   PASS
GYEONGJU Normal-route Live             PASS
False Positive                         0

CAR Fake Travel Time                   0
PUBLIC_TRANSIT Fake Geometry           0

Runtime-only Sanity State              PASS
Sanity Persistence                     0
Route Persistence                      0
Local Tourism Cache User Read          0
Infra Changes                          0
```

## 인프라 동결 확인

- `.github/workflows`: 변경 없음
- Docker/AWS/Caddy: 변경 없음
- K8s/ArgoCD: 기능 구현에서 변경 없음
- Vercel/DNS/TLS: 변경 없음
- API contract: 기존 필드 의미 변경 없이 additive field만 추가

이번 Core Guard 이후 신규 Core Feature 추가는 중단하고 Submission UX Hardening, 기능설명서, 대표 이미지, 최종 운영 Smoke 순서로 전환한다.
