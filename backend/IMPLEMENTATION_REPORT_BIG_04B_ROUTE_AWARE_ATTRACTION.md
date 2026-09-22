# 한끼여행 BIG-04B 이동부담 기반 관광지 추천 구현 보고서

- 작성일: 2026-09-17
- 대상: Backend 중심, Frontend 최소 연결
- 기준 문서: `HankkiTravel_BIG_04B_Route_Aware_Attraction_Task_20260917.md`
- 판정: **로컬 구현·회귀 PASS / 배포 후 JEJU·GYEONGJU 실제 Golden Flow NOT VERIFIED**

## 구현 결과

### NEARBY_COURSE

- 사용자 표시명: `가까운 코스`
- 현재 Day의 Slot 문맥을 구성한 뒤 이동 근거가 확인된 후보를 우선한다.
  - 오전 관광: `DAY_FOCUS → 후보 → BREAKFAST 또는 LUNCH`
  - 오후 관광: `LUNCH → 후보 → DINNER 또는 STAY`
- 대중교통은 실제 경로의 소요시간, 환승 횟수, 명시 도보거리만 반영한다.
- `unaccountedDistanceMeters`는 도보거리나 추천 점수에 더하지 않는다.
- 자동차는 직선거리만 참고값으로 사용하며 실제 주행시간·도로거리·통행료를 추정하지 않는다.
- 이동 문맥이 없거나 경로 조회가 실패하면 0점으로 처리하지 않고 `MOVEMENT_CONTEXT_REQUIRED`, `NOT_EVALUATED` 또는 `PARTIAL`로 노출한다.

### SIGNATURE_COURSE

- 사용자 표시명: `대표 명소 코스`
- TourAPI Live의 지역·관광지 유형 정합성과 한국관광공사의 공식 지역 관광 수요·자원 수요를 함께 반영한다.
- 이동 부담을 무시하지 않으며 설정된 대중교통 상한을 넘는 후보에는 강한 이동 penalty와 caution을 준다.
- SNS 인기, 리뷰 인기, 실시간 핫플 표현은 사용하지 않는다.

## Scoring weights

가중치와 cutoff는 `application.yml`의 `hankki.planner.attraction` 아래에서 환경변수로 조정할 수 있다.

| 관점 | 이동 부담 | 중심 장소 적합 | 가족 이동 조건 | 관광지 정합성 | 공식 지역 수요 |
| --- | ---: | ---: | ---: | ---: | ---: |
| NEARBY_COURSE | 50 | 20 | 15 | 15 | 0 |
| SIGNATURE_COURSE | 20 | 15 | 0 | 40 | 25 |

- 근거가 없는 dimension은 분모에서 제외한다. 미확인 근거를 0점으로 간주하지 않는다.
- 동점은 근거 커버리지와 contentId 순으로 안정적으로 정렬한다.
- `가까운 코스`는 실제 이동 근거가 있는 후보를 미평가 후보보다 먼저 배치한다.

## Transit call budget

- 기본 최대 Kakao Transit 호출: 요청당 `6회`
- 흐름: TourAPI Live list → 지역·유형·좌표·선택 중복 prefilter → detail 제한 6건 → 직선거리 shortlist → bounded transit calls
- 단위 시나리오 검증: 후보 3건에서 설정값 2회일 때 실제 호출 `2회`
- Kakao 실패 시 추천 전체를 실패시키지 않고 `TRANSIT_PARTIALLY_UNAVAILABLE`로 저하한다.

## 응답과 UI

- 추천 응답에 다음 필드를 additive하게 추가했다.
  - `perspective`
  - `overallScore`
  - `evidenceCoverage`
  - `routeBurden`
  - `distanceMeters`
  - `transitSummary`
  - `familyMobilityEvidence`
  - `reasons`
  - `cautions`
- 기존 `candidates`, `fitReasons`, `checkBeforeVisit` 계약은 유지한다.
- Frontend에 `가까운 코스`, `대표 명소 코스` 탭을 추가했다.
- 대중교통 실제 시간과 자동차 직선거리 참고값을 다른 문구로 표시한다.
- 누적 대중교통 시간·환승·명시 도보거리로 Day 이동 부담을 표시하며 임계값은 config로 관리한다.
- 360px, 390px, 768px E2E에서 overflow가 발생하지 않았다.

## 중복·저장·데이터 경계

- 이미 선택된 `DAY_FOCUS`, `MORNING_ACTIVITY`, `AFTERNOON_ACTIVITY` contentId는 추천 후보에서 제외한다.
- 같은 후보가 두 관점에 포함되는 것은 허용하며 관점별 badge와 점수를 유지한다.
- `tourism_places` read: `0`
- `restaurants` read: `0`
- stale cache fallback: `0`
- 추천 결과, 점수, 거리, 대중교통 결과, 관점, 공식 수요 결과 persistence: `0`
- 저장되는 값은 사용자가 최종 선택한 Anchor 최소 참조뿐이다.

## Before / After

| 항목 | 결과 |
| --- | --- |
| 운영 감사 전 추천 1순위 | 식당 기준 직선거리 약 `18.9km`인 거문오름 |
| 구현 후 자동화된 가까운 코스 시나리오 | 약 `0.9km` 후보가 1순위, Transit call budget 준수 |
| 해석 | 정렬 개선은 자동화 시나리오로 확인했으며, 배포 후 동일 운영 일정의 실제 top distance 재측정은 아직 필요하다. |

## JEJU live result

- TourAPI 실제 호출: **PASS**
- Kakao 대중교통 실제 호출: **PASS**
- 로컬 04B 경로 인지 추천 시나리오: **PASS**
- 04B 배포 후 운영 Golden Flow top 후보 거리 재측정: **NOT VERIFIED**

## GYEONGJU live result

- 1박 2일 Day 전환, 지도 marker 교체, 768px UI: **PASS (mocked E2E)**
- 04B 배포 후 실제 TourAPI·Kakao 기반 Slot 문맥 검증: **NOT VERIFIED**

## 검증 결과

| 검증 | 결과 |
| --- | --- |
| Backend `clean verify` | PASS — 138 tests, failures 0, errors 0 |
| Route-aware 신규 Backend tests | PASS — 5 tests |
| TourAPI/Kakao 기존 LiveIntegrationTest | PASS — 2 tests, skipped 0 |
| Frontend unit | PASS — 43 tests |
| Frontend build | PASS |
| Frontend E2E | PASS — 16 tests |
| Mobile 360/390/768 | PASS |
| Flyway migration 추가/변경 | 0 |
| Infra/K8s/AWS/Vercel/DNS/TLS 변경 | 0 |

## 종료 조건 판정

| 조건 | 판정 |
| --- | --- |
| Route-aware attraction ranking | PASS |
| Nearby Course | PASS |
| Signature Course | PASS |
| PUBLIC_TRANSIT evidence | PASS |
| CAR limitation | PASS |
| Selected-place duplicate defense | PASS |
| Kakao bounded calls | PASS |
| TourAPI Live First | PASS |
| Local tourism DB read | 0 |
| Recommendation persistence | 0 |
| JEJU Live Smoke | PARTIAL — 외부 연동 PASS, 배포 후 04B 운영 흐름 미확인 |
| GYEONGJU Live Smoke | NOT VERIFIED |
| Mobile 360/390/768 | PASS |
| Infra Changes | 0 |

## 남은 확인

코드 배포 후 제주 당일 일정과 경주 1박 2일 일정을 운영 UI에서 다시 실행해 두 관점의 실제 top 후보, 거리, 대중교통 근거, Slot 문맥을 확인해야 한다. 이 확인 전에는 BIG-04B의 운영 종료 조건을 모두 충족했다고 선언하지 않는다.
