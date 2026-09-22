# HankkiTravel BIG-04D Progressive Planner / Route Search 구현 결과

- 작업일: 2026-09-17
- Backend 기능 커밋: `356c6ab97f30958095a48f879b1943875952370b`
- Backend 운영 복구 커밋: `896e17ed70cc644c688310be58039e805de314a2`, `2a0046049d1fc32a19528e26b98cfbafbf36bf04`
- Frontend 기능 커밋: `87f46c79ef1ceb0a4b56e04de5eda5b8c93de312`
- Backend 기준 브랜치: `main`
- Frontend 기준 브랜치: `main`

## 판정 기준

이 보고서는 정적 소스 검수, 로컬 자동 테스트, GitHub Actions와 실제 운영 수용 검증을 구분한다. 운영 수용 검증에서는 전용 guest와 CAR/PUBLIC_TRANSIT profile을 생성해 JEJU/GYEONGJU 여행 흐름을 실행했다. 생성한 모든 trip은 DELETE API로 정리했으며, 삭제 API가 없는 테스트 guest 1개와 profile 2개만 운영 DB에 남아 있다.

## Progressive UX

| 항목 | 결과 | 근거 |
| --- | --- | --- |
| 완료 선택 접기 | PASS | 선택 성공 후 큰 추천 목록을 닫고 Compact Summary를 유지한다. |
| 다음 미완료 결정 계산 | PASS | `resolveNextDecision` 한 곳에서 식사, 활동, 선택형 디저트, 숙소, Planner 순서를 계산한다. |
| 선택 후 자동 이동 | PASS | 다음 결정 섹션으로 스크롤하고 섹션 제목으로 키보드 포커스를 옮긴다. |
| 변경 후 다시 열기 | PASS | Compact Summary의 변경 동작이 해당 추천 영역을 다시 연다. |
| 선택형 디저트 건너뛰기 | PASS | 서버에 가짜 결정을 저장하지 않고 현재 화면 상태에서 다음 결정으로 진행한다. |

점진형 진행 상태는 후보 목록이나 화면 상태를 영속화하지 않는다. 저장되는 것은 기존의 사용자가 확정한 anchor뿐이다.

## Alternative Search

| 대상 | 다른 후보 | 직접 검색 | 선택 시 서버 재검증 |
| --- | --- | --- | --- |
| 식당 | PASS | PASS | PASS |
| 숙소 | PASS | PASS | PASS |
| 관광지 | PASS | PASS | PASS |

- 식당은 현재 DAY_FOCUS 주변을 우선하며, 제외된 후보와 이미 선택한 식당을 중복 노출하지 않는다.
- 직접 찾은 식당도 Live 상세 정보와 지역·콘텐츠 유형을 확인하고 가족의 명시적 제한 식재료 충돌을 적용한다.
- 숙소와 관광지는 TourAPI Live 검색 결과를 상세 조회한 뒤 region/contentType을 재검증한다.
- Alternative/Search 결과는 프런트 메모리 상태에만 두며 localStorage, DB, 관광 캐시에 기록하지 않는다.

## Stay Radius

| 항목 | 결과 |
| --- | --- |
| 당일 마지막 실제 anchor 사용 | PASS |
| 다음 날 DAY_FOCUS 보조 기준 | PASS |
| 점진 반경 `3000 → 8000 → 15000m` | PASS |
| 최소 후보 수 3개 도달 시 중단 | PASS |
| 20km 초과 원거리 주의 표시 | PASS |
| 거리 기반 안정 정렬 | PASS |

통합 테스트 fixture에서 DINNER를 당일 마지막 anchor로 사용했다. 반경 3000m 단계의 후보 1개에서 8000m 단계의 후보 3개로 확장됐으며, 최종 `movementContext`는 `DINNER;radius=8000;candidateCount=3`을 반환했다. fixture 좌표 기준 최종 TOP 후보 직선거리는 0m이고 나머지 후보에도 거리 값이 존재한다. 이는 알고리즘 검증값이며 실제 JEJU/GYEONGJU 운영 후보 거리 기록은 아래 Live 판정과 같이 별도 확인이 필요하다.

운영 TourAPI Live 숙소 수용 결과는 다음과 같다.

| 지역 | 기준 anchor | 사용 반경 | 후보 수 | 응답 순서 상위 거리 | 40~50km 후보의 첫 순위 노출 |
| --- | --- | ---: | ---: | --- | --- |
| JEJU | DINNER | 3,000m | 4 | 2,811m / 2,298m / 610m | 0 |
| GYEONGJU | DINNER | 3,000m | 6 | 28m / 51m / 438m | 0 |

JEJU는 다음 날 DAY_FOCUS까지 선택한 상태에서 현재 일정과 다음 날 동선을 함께 평가했다. 따라서 응답 순서는 현재 anchor 거리만의 오름차순이 아니라 두 동선의 적합도를 합산한 순서다.

## Route Visualization

| 항목 | 결과 | 설명 |
| --- | --- | --- |
| PUBLIC_TRANSIT 시간·환승·명시적 도보 | PASS | Kakao 응답에서 확인된 값만 표시한다. |
| PUBLIC_TRANSIT 실제 geometry | NOT_AVAILABLE_HONESTLY | 현재 upstream 응답 모델과 fixture에 `step.path.points`가 없어 선을 만들지 않았다. |
| PUBLIC_TRANSIT 가짜 geometry | 0 | 좌표를 임의 연결하지 않는다. |
| CAR 직선거리 | PASS | 인접 anchor의 Haversine 직선거리를 제공한다. |
| CAR 가짜 이동시간 | 0 | 시간을 생성하지 않는다. |
| CAR 가짜 도로 geometry | 0 | 점선은 도로 경로가 아닌 직선거리 참고선으로 명시한다. |
| 경로 hover/tap 정보 | PASS | 지도 선을 가리키거나 누르면 출발지, 도착지, 데이터 성격을 표시한다. |
| Timeline ↔ Map Leg 동기화 | PASS | Timeline leg와 지도 선이 같은 `plannerLegId`를 사용한다. |
| 오래된 경로 잔존 | 0 | Day/items/legs 변경 시 기존 선을 제거하고 다시 그린다. |

## Live

| 시나리오 | 결과 | 확인 범위 |
| --- | --- | --- |
| 운영 Front 공개 경로 | PASS | `https://hankki.kro.kr/` HTTP 200 |
| 운영 배포 번들 | PASS | BIG-04D의 `직선거리 참고선`, 다른 식당/숙소/관광지 UI가 운영 번들에 포함됨 |
| 운영 API health | PASS | 배포 번들이 사용하는 `https://api.hankki.r-e.kr/actuator/health` HTTP 200 |
| JEJU CAR 전체 흐름 | PASS | 3 anchors, CAR leg 2개, 직선거리 43,015m / 2,138m, 생성 시간 0, 가짜 geometry 0 |
| JEJU PUBLIC_TRANSIT 전체 흐름 | PASS | 3 anchors, CURRENT_DATA leg 2개, 실제 시간·환승·명시 도보 제공, 가짜 geometry 0 |
| GYEONGJU 1N2D 전체 흐름 | PASS | Day 1/2 각각 3 anchors, 숙소·디저트·다른 후보·직접 검색 확인 |
| JEJU/GYEONGJU 실제 숙소 TOP 거리 | PASS | JEJU 4개, GYEONGJU 6개 Live 후보의 거리와 반경 기록 |

운영 API 정본은 `https://api.hankki.r-e.kr`이며, 프런트 운영 번들의 API base와 일치한다.

### 운영 수용 상세

- JEJU CAR: 식당 다른 후보 4개, 관광지 다른 후보 6개, Planner item 3개와 leg 2개를 확인했다. 자동차 소요시간은 생성하지 않았다.
- JEJU PUBLIC_TRANSIT: 두 leg 모두 `CURRENT_DATA`였다. 확인된 시간은 약 195.18분/21.55분, 환승은 3회/0회, 명시 도보는 179m/0m였다.
- GYEONGJU 1N2D: Day 1/2 모두 item 3개였고, 디저트 1개, 숙소 다른 후보 6개, 숙소 직접 검색 1개를 확인했다.
- 직접 검색: 일반 검색어 기준 관광지 1개와 식당 6개가 `CURRENT_DATA`로 반환됐다. 선택된 상호명 그대로 검색해 결과가 없을 때는 HTTP 200의 빈 후보와 `CURRENT_DATA_PARTIALLY_UNAVAILABLE`로 안전하게 내려간다.
- PUBLIC_TRANSIT upstream 응답에는 안정적인 geometry가 없었으므로 실제 polyline은 `NOT_AVAILABLE_HONESTLY`이며 임의 geometry는 만들지 않았다.
- 모든 수용 테스트 trip은 삭제했다.

## Persistence

| 항목 | 기록 수 |
| --- | ---: |
| KTO raw write | 0 |
| route write | 0 |
| candidate write | 0 |
| 신규 Flyway migration | 0 |
| schema 변경 | 0 |

통합 테스트는 추천 전후 `tourism_places`, `restaurants` 행 수가 증가하지 않는 것도 검증한다.

## 자동 검증

### Backend

```text
.\mvnw.cmd -B -ntp clean verify
BUILD SUCCESS
Tests run: 146, Failures: 0, Errors: 0, Skipped: 0
```

추가 집중 검증:

```text
.\mvnw.cmd -B -ntp -Dtest=TripPlannerMysqlIntegrationTest test
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
```

운영 수용 중 확인된 TourAPI 부분 장애를 재현하는 회귀 테스트를 추가했다.

- 위치 기반 식당 조회 실패 시 TourAPI Live 지역 목록 폴백
- 제주 두 권역 중 한 권역 검색 실패 시 정상 권역 결과 유지
- 식당·관광지·숙소 검색의 부분 장애 격리

### Frontend

```text
npm test -- --run
Test Files: 9 passed
Tests: 58 passed

npm run build
PASS

npm run test:e2e
development: 4 passed
production: 14 passed
total: 18 passed
```

360px, 390px, 768px viewport 회귀 검증과 Planner/Map 장애 격리 테스트가 포함됐다.

## GitHub Actions 및 배포

| 저장소 | 실행 | 결과 |
| --- | --- | --- |
| Backend 기능 | [Backend CI and GHCR #35222775682](https://github.com/jeon950523/hankki_travel_back/actions/runs/35222775682) | SUCCESS |
| Backend Live 반경 폴백 | [Backend CI and GHCR #35225030666](https://github.com/jeon950523/hankki_travel_back/actions/runs/35225030666) | SUCCESS |
| Backend 검색 부분 장애 격리 | [Backend CI and GHCR #35226761343](https://github.com/jeon950523/hankki_travel_back/actions/runs/35226761343) | SUCCESS |
| Frontend | [Frontend CI #35221563499](https://github.com/jeon950523/hankki-travel/actions/runs/35221563499) | SUCCESS |

Backend 실행에서 다음 작업이 모두 성공했다.

1. Backend 전체 검증 및 DB migration 검증
2. GHCR SHA 이미지 build/push
3. GitOps deployment image SHA 갱신
4. AWS OIDC 인증
5. SSM 배포와 기존 health check

## 종료 조건

```text
Completed Decision Collapse             PASS
Next Decision Auto Navigation           PASS
Change Re-open                          PASS

Restaurant Other Candidates             PASS
Restaurant Direct Search                PASS
Stay Other Candidates                   PASS
Stay Direct Search                      PASS
Attraction Other Candidates             PASS
Attraction Direct Search                PASS

Stay Last-Anchor Retrieval              PASS
Stay Progressive Radius                 PASS
Stay 40~50km Blind Ranking              0 (algorithm/test evidence)

PUBLIC_TRANSIT Leg Evidence             PASS
PUBLIC_TRANSIT Real Geometry            NOT_AVAILABLE_HONESTLY
CAR Straight Distance                   PASS
CAR Fake Travel Time                    0
CAR Fake Route Geometry                 0

Map Marker 1→2→3                        PASS
Map Leg Visualization                   PASS
Map Leg Hover                           PASS
Map Leg Mobile Tap                      PASS
Timeline↔Map Leg Sync                   PASS
Old Route Residual                      0

TourAPI Live First                      PASS
Local Tourism Cache User Read           0
User GPS                                0
Route Persistence                       0
Candidate Persistence                   0

JEJU Live                               PASS
GYEONGJU Live                           PASS
Mobile 360/390/768                      PASS
Infra Changes                           0
```

## 인프라 동결 확인

- `.github/workflows`: 변경 없음
- Docker 배포 전략: 변경 없음
- AWS/Caddy: 변경 없음
- K8s/ArgoCD: 변경 없음
- Vercel 설정: 변경 없음
- DNS/TLS: 변경 없음

## 운영 테스트 데이터 정리

- 생성한 모든 수용 테스트 trip: 삭제 완료
- 테스트 guest/profile: 삭제 API가 없어 guest 1개와 profile 2개가 남아 있음
- 실제 사용자 데이터 변경: 없음
