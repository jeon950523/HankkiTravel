# BIG-03B TourAPI Live Place · Stay · Dynamic Day Planner 구현 보고서

- 작업일: 2026-09-16
- 기준: `HankkiTravel_BIG_03B_Backend_Live_Place_Stay_Planner_Task_20260916.md`
- 대상: `E:\학원\관광\travel-back`
- 범위: Backend only
- 선행 BIG-03A: commit `7d00a35` (`feat: 여행 일정과 식사 앵커 코어 구현`), origin/main push 완료
- BIG-03B commit/push: 수행하지 않음

## 구현 결과

Meal Anchor를 중심으로 관광지·숙소 후보를 TourAPI Live에서 조회하고, 사용자가 선택한 최소 KTO 참조를 V12 테이블에 저장한다. 하루 Planner는 저장 참조를 호출 시점의 TourAPI Live 정보로 hydrate하고 선택된 인접 장소 사이만 기존 Kakao 대중교통 경계로 조회한다.

복잡한 경로 최적화, 사용자 GPS, 가격·객실 예약 추정, 전체 여행 일괄 hydrate는 구현하지 않았다.

## Schema

신규 forward migration: `V12__add_trip_day_place_anchors.sql`.

`trip_day_place_anchors`:

| 컬럼 | 의미 |
|---|---|
| `id` | 내부 PK |
| `public_id` | 외부 UUID, unique |
| `trip_day_id` | Trip Day FK, Trip 삭제 시 cascade |
| `slot_type` | MORNING_ACTIVITY / AFTERNOON_ACTIVITY / STAY |
| `provider` | KTO만 허용 |
| `content_id` | 현재 TourAPI를 다시 조회할 외부 식별자 |
| `content_type` | 12 attraction / 32 lodging |
| timestamps | 생성·갱신 시각 |

`UNIQUE(public_id)`, `UNIQUE(trip_day_id, slot_type)`과 slot/provider/content CHECK를 DB에서 보장한다. 기존 V1~V11은 수정하지 않았다.

## Live Source

```text
Attraction source = TourAPI Live areaBasedList2 + detailCommon2
Stay source = TourAPI Live areaBasedList2 + detailCommon2
Planner hydration = TourAPI Live detailCommon2
Transit = 기존 TransitRouteFinder / Kakao public transit
Local Tourism Cache usage = 0
DB fallback on Live failure = 0
```

JEJU는 제주시·서귀포시 Live scope를 합치고, GYEONGJU는 경주시 scope를 사용한다. 목록은 지역·content type·좌표를 검증하고 contentId로 안정 정렬한다. 후보 상세 평가는 기본 최대 6건이며 환경변수 `PLANNER_LIST_SIZE`, `PLANNER_DETAIL_LIMIT`으로 bounded 설정을 제공한다.

식사 앵커 좌표가 현재 Live로 확인되면 관광지 후보에 직선거리 근거를 제공한다. 좌표가 없으면 가까움을 만들지 않고 현재 TourAPI의 지역·유형 확인 사실만 표시한다.

## Persistence Audit

| 검사 | 결과 |
|---|---|
| Planner가 `tourism_places` 읽기 | 0 |
| Planner가 `restaurants` 읽기 | 0 |
| 신규 Trip mapper의 관광 캐시 mapper/service 의존 | 0 |
| KTO raw payload/JSON 저장 | 0 |
| title/address/image/overview/menu 저장 | 0 |
| 후보/추천 결과 저장 | 0 |
| Planner response/route/duration/transfer 저장 | 0 |
| runtime nutrition 저장 | 0 |
| 사용자 GPS 수신·저장 | 0 |

`trip_day_place_anchors`의 업무 컬럼은 slot/provider/contentId/contentType 참조뿐이다. 테스트 DB의 `tourism_places`와 `restaurants`에 가짜 stale 행을 넣고 Live fake가 반환한 다른 content만 후보에 나타나는 회귀 테스트를 추가했다. 정적 검사도 Trip 소스의 cache mapper/service 및 관광 캐시 SQL 의존을 금지한다.

## API 계약

모든 응답은 `Cache-Control: no-store`다. `{G}`는 Guest public UUID, `{T}`는 Trip public UUID다.

### Attraction Recommendation

```http
POST /api/guests/{G}/trips/{T}/days/{dayNumber}/place-recommendations
Content-Type: application/json

{"slotType":"MORNING_ACTIVITY"}
```

`slotType`은 `MORNING_ACTIVITY` 또는 `AFTERNOON_ACTIVITY`만 허용한다. 응답은 `slotType`, `candidates`, `callSummary`, `dataAvailability`, `sourceAttribution`을 반환한다. Candidate는 `contentId`, `contentType`, 현재 `title/imageUrl/address/coordinates`, `informationEvidence`, `fitReasons`, `checkBeforeVisit`를 포함한다.

### Attraction / Stay Anchor PUT · DELETE

```http
PUT /api/guests/{G}/trips/{T}/days/{dayNumber}/place-anchors/MORNING_ACTIVITY
{"contentId":"현재 Live 후보의 ID"}

DELETE /api/guests/{G}/trips/{T}/days/{dayNumber}/place-anchors/MORNING_ACTIVITY
```

숙소는 같은 경로에서 slot type을 `STAY`로 사용한다. PUT은 현재 Live existence/content type/Trip region을 검증한다. 동일 slot 재PUT은 public ID를 유지하고 참조만 idempotent하게 유지·교체한다. DELETE는 앵커가 없어도 204다.

### Stay Recommendation

```http
POST /api/guests/{G}/trips/{T}/days/{dayNumber}/stay-recommendations
```

1박 이상이고 마지막 날이 아닌 Day에만 제공한다. 마지막 날은 400 `STAY_NOT_REQUIRED`. 가격·객실·예약 가능 여부는 반환하거나 추정하지 않는다.

### Day Planner

```http
GET /api/guests/{G}/trips/{T}/days/{dayNumber}/planner
```

한 Day만 다음 순서로 조립하고 없는 항목은 건너뛴다.

```text
BREAKFAST → MORNING_ACTIVITY → LUNCH → AFTERNOON_ACTIVITY → DINNER → STAY
```

Item은 저장 참조와 현재 Live title/address/image/coordinates, `dataAvailability`를 반환한다. 현재 content 조회가 불가능하면 참조는 유지하면서 해당 Item을 `CURRENT_DATA_UNAVAILABLE`로 표시한다.

Leg는 인접 Item 사이만 생성한다. 현재 프로필이 PUBLIC_TRANSIT이고 양쪽 좌표가 있을 때만 기존 Kakao 경계를 호출한다. `durationMinutes`, `transferCount`, `explicitWalkingDistanceMeters`, `unaccountedDistanceMeters`를 구분한다. Kakao 실패 시 장소 Item은 유지하고 Leg만 `UNAVAILABLE`; 임의 이동시간을 만들지 않는다.

## Ownership / Transaction

Guest→Trip→Day 관계를 숫자 내부 ID가 아닌 소유 Guest와 Trip public UUID, dayNumber로 재확인한다. 다른 Guest/Trip/Day 조합은 404다. Profile은 추천·Planner 호출 시 현재 소유 프로필을 다시 읽으며 snapshot을 만들지 않는다.

흐름은 `짧은 context read transaction → transaction 종료 → TourAPI/Kakao → 검증 → 짧은 write transaction에서 소유권 재확인 및 upsert`다. Fake boundary에서 Live 호출 시 활성 transaction이 없음을 검증했다.

## Tests

최종 명령:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\mvnw.cmd -B -ntp '-DargLine=-Xmx3g -Dspring.test.context.cache.maxSize=1' -DreuseForks=false clean verify
```

결과: **118 tests, failures 0, errors 0, skipped 0; BUILD SUCCESS**. 실행 시간 4분 37초, 종료 2026-09-16 22:20:17 KST. 테스트 skip/완화 없이 신규 8개 테스트를 포함한 전체 회귀다.

| 검증 | 결과 |
|---|---|
| Flyway fresh V1→V12 / restart pending 0 | PASS |
| V10→V11 legacy 보존→V12 | PASS |
| Testcontainers MySQL 8.4 | PASS |
| No local tourism cache regression | PASS |
| Transit adjacent-only / failure degradation | PASS |
| BIG-02 recommendation regression | PASS |
| BIG-03A Trip/Meal Anchor regression | PASS |
| Nutrition / TourAPI Live / ArchUnit | PASS |

추가 회귀 범위:

- fresh Flyway V1→V12 및 restart pending 0
- V10→V11 legacy 보존 후 V12 적용
- MySQL morning/afternoon/stay unique, replace, idempotent clear, Trip cascade
- 마지막 날 STAY select/recommendation 거부
- 다른 Guest 접근 거부
- 가짜 stale 관광/식당 DB 행이 후보·Planner에 미포함
- 현재 Live title/address/image/coordinates hydrate
- Live missing/outage 시 no DB fallback 및 CURRENT_DATA_UNAVAILABLE
- BREAKFAST→MORNING→LUNCH→AFTERNOON→DINNER→STAY 순서
- 최대 인접 5개 leg만 Kakao 호출
- Kakao 장애 시 장소 유지, route만 unavailable
- explicit walking과 unaccounted distance 분리
- HTTP no-store 및 최소 anchor response

## Live Smoke

로컬 MySQL 8.4.11의 기존 `hankki_planb`에 V12를 실제 적용하고 서버 health `UP`을 확인했다. 기존 영양 기준 19,534행을 유지했다.

실제 키로 동적 후보를 선택했으며 contentId/title/payload/secret은 보고서에 기록하지 않았다.

| 흐름 | 후보 | 추천 TourAPI list/detail | 추천 시간 | Planner detail/transit | Planner 시간 | 결과 |
|---|---:|---:|---:|---:|---:|---|
| JEJU / LUNCH → Attraction → Planner | 6 | 2 / 7 | 950ms | 2 / 1 | 447ms | 전체 HTTP 200, clear/delete 204 |
| GYEONGJU 1박2일 / DINNER → Stay → Planner | 6 | 1 / 7 | 771ms | 2 / 1 | 453ms | 전체 HTTP 200, clear/delete 204 |

두 Planner 모두 Item 2개와 인접 Leg 1개를 만들었고, Item과 Leg가 모두 CURRENT_DATA였다. Kakao 실제 대중교통 호출도 각 1회 성공했다.

Smoke 전/후 DB:

```text
tourism_places row delta = 0
restaurants row delta = 0
trip/day/meal/place anchor fixture rows after cleanup = 0
```

즉 실제 TourAPI/Kakao 응답은 메모리에서만 사용됐고 관광 캐시와 Planner 결과를 저장하지 않았다. smoke 전용 Guest/Profile은 로컬 DB에 남아 있다.

## Known Issues / 범위 제한

- Frontend BIG-03C, 운영 배포, 학원 DB는 이번 작업 범위가 아니다.
- 사용자 현재 GPS는 Backend에 보내지 않는다.
- CAR 프로필은 현재 재사용 가능한 자동차 길찾기 경계가 없으므로 Planner가 대중교통 시간을 임의 생성하지 않는다.
- 가격·실시간 객실·예약 가능 여부, 다수 관광지 자유 편집, TSP/VRP/LLM 최적화는 후속 범위다.

## Infra / Secret Freeze

Dockerfile, GitHub Actions, K8s, ApplicationSet, ArgoCD, HTTPRoute, Vercel, DNS, TLS, Production Secret을 변경하지 않는다. `.env.local`은 계속 Git ignore 상태이며 보고서와 소스에 실제 key/password를 기록하지 않는다.

최종 감사: BIG-03B 변경/신규 파일 21개, `git diff --check` PASS, V1~V11 변경 0, 인프라 변경 0, 변경 후보에서 실제 local secret 값 일치 0. Frontend와 GitOps working tree는 clean이다. Backend local HEAD와 origin/main은 모두 BIG-03A `7d00a358a3a627b24a1a25b56b9a4159b17080c6`이고, BIG-03B는 검수 가능한 미커밋 로컬 변경으로 남겼다. 조회 시점에 BIG-03A 커밋에 대한 GitHub Actions run은 발견되지 않았다.
