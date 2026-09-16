# BIG-03A Trip · Day · Meal Slot · Meal Anchor 구현 보고서

- 작업일: 2026-09-16
- 기준 지시서: `HankkiTravel_BIG_03A_Backend_Trip_Meal_Anchor_Task_20260916.md`
- 대상: `E:\학원\관광\travel-back`
- 기준 Authority: Current Authority v0.14 Plan B 및 BIG-02 구현
- 범위: Backend only. BIG-03B 관광지/숙소/Dynamic Planner 미구현.
- 커밋/푸시: 실행하지 않음. 검수 가능한 로컬 변경으로 보존.

## Preflight 및 Git 기준

작업 전 working tree 안전성 확인 후 fetch하여 아래 세 저장소의 local HEAD와 origin/main이 동일함을 확인했다. 모두 main, clean, ahead/behind 0/0이었다. 기존 backend stash는 적용하거나 삭제하지 않았다.

| 저장소 | 작업 시작 HEAD |
|---|---|
| Backend | `e26f3132e4205d6890b2bab3f6c882b19f3d34be` |
| Frontend/Authority | `50154a262de6ebac690e686a04484e068c45fcb6` |
| K8s GitOps | `5ee9de03b394b1256adf9c98edbfeabb273690cd` |

지시서의 `E:\projects` 경로 대신 실제 origin이 일치하는 위 작업 경로를 사용했다. 구현 후 backend working tree에 이번 변경이 존재하므로 현재 소스가 원격과 같다는 뜻은 아니다. 원격에는 이번 구현을 반영하지 않았다.

## 구현 및 변경 이유

- `trip/api/TripController`: 일정 생성·목록·상세·삭제, 슬롯 추천, 앵커 선택·해제의 7개 API. 성공과 오류에 no-store 적용.
- `TripPlan`: 날짜 포함 1~4일, JEJU/GYEONGJU, DAY1~DAYN의 정확한 구성, 하루 0~3끼/전체 1~12끼, 중복 방지, 아침→점심→저녁 정렬.
- `TripScheduleService` / `TripScheduleMapper`: Guest/Profile 소유권과 Trip–Slot 소속 검증. 생성 전체를 단일 transaction으로 저장. 변경 시 Trip 행 잠금과 소유권 재확인. DB unique + upsert로 슬롯당 앵커 1개 유지.
- `TripDecisionService`: 저장된 profileId/region/date/mealType으로 기존 BIG-02 추천 서비스를 호출. 현재 프로필을 사용하며 snapshot과 새 추천 알고리즘을 만들지 않는다. 출발점이 없는 이동 평가의 기존 정보 부족 정책 유지.
- 앵커 선택은 transaction 밖에서 TourAPI Live로 식당 식별자/콘텐츠 유형/MEAL 또는 MIXED 분류/명백한 지역 불일치를 검증한 뒤 짧은 저장 transaction을 연다. 지역 metadata가 없을 때 지역 일치를 임의로 확정하지 않는다.
- `TourApiRestaurantPresentationParser` 및 runtime detail 모델에 Live 식별자·법정동 코드를 전달한다. 기존 생성자 호환성을 보존하며 metadata는 DB에 저장하지 않는다.
- 기존 추천 response 변환 함수를 재사용하도록 public으로 노출했다. 기존 추천 API의 응답 구조와 알고리즘은 유지한다.
- Guest API CORS 허용 method에 DELETE를 추가했다. 기존 Guest public UUID 접근 계약을 유지한다.
- ArchUnit의 Trip 의존 관계에 이번 public application/API 사용을 명시하고 새 테이블 ownership을 Trip으로 등록했다. 타 모듈 mapper 접근 금지와 순환 의존 검사는 유지한다.

## Flyway 및 영속성

신규 `V11__extend_trip_meal_anchor.sql`만 추가했다. V1~V10은 수정하지 않았다.

- 기존 V4 `trips` 테이블에 public UUID, Guest, 지역, 날짜를 확장한다.
- 기존 draft는 임의 날짜·UUID·Guest를 만들어 채우지 않고 새 컬럼을 모두 null로 유지한다. 새 API에서는 이러한 legacy draft를 노출하지 않는다.
- 새 일정은 새 컬럼 전체를 채워야 하며 CHECK로 지역/날짜/최대 4일을 검증한다. 일부 컬럼만 채운 상태는 거부한다.
- `trip_days`: Trip/일차 및 Trip/날짜 unique.
- `trip_meal_slots`: public UUID unique, Day/mealType unique.
- `meal_anchors`: 슬롯 unique, provider KTO, 비어 있지 않은 contentId.
- Trip 삭제 시 Day→Slot→Anchor cascade; Guest/Profile 유지.
- 앵커의 업무 데이터는 `provider`, `content_id`, `content_type`뿐이다. PK/FK/timestamps 외 payload 컬럼이 없다.

| 영속성 제한 | 결과 |
|---|---|
| KTO title/address/image/menu/raw JSON 추가 저장 | 0 |
| 추천 TOP3/점수/근거/이동 결과 저장 | 0 |
| runtime nutrition match 결과 저장 | 0 |
| 가족 프로필 snapshot 복제 | 0 |
| 오래된 관광 DB cache fallback | 없음 |

## Frontend API 계약

`G`는 기존 Guest public UUID, `T`는 tripPublicId, `S`는 mealSlotPublicId다. Profile ID는 기존 API의 숫자 식별자를 유지하며 Trip/Day/Slot 내부 숫자 PK는 노출하지 않는다.

| 동작 | Method / 경로 | 성공 |
|---|---|---|
| Trip Create | POST `/api/guests/{G}/trips` | 201, Trip detail |
| Trip List | GET `/api/guests/{G}/trips` | 200, Summary 배열 |
| Trip Detail | GET `/api/guests/{G}/trips/{T}` | 200, Trip detail |
| Slot Recommendation | POST `/api/guests/{G}/trips/{T}/meal-slots/{S}/recommendations` | 200, 기존 BIG-02 response |
| Anchor PUT | PUT `/api/guests/{G}/trips/{T}/meal-slots/{S}/anchor` | 200, 최소 외부 참조 |
| Anchor DELETE | DELETE `/api/guests/{G}/trips/{T}/meal-slots/{S}/anchor` | 204, 비어 있어도 성공 |
| Trip DELETE | DELETE `/api/guests/{G}/trips/{T}` | 204 |

Create 요청 예시(식별자는 설명용):

```json
{
  "profileId": 12,
  "regionKey": "JEJU",
  "startDate": "2026-09-20",
  "endDate": "2026-09-21",
  "days": [
    { "dayNumber": 1, "mealTypes": [] },
    { "dayNumber": 2, "mealTypes": ["LUNCH"] }
  ]
}
```

Create / Detail 응답 예시:

```json
{
  "tripPublicId": "00000000-0000-0000-0000-000000000001",
  "profileId": 12,
  "regionKey": "JEJU",
  "startDate": "2026-09-20",
  "endDate": "2026-09-21",
  "durationDays": 2,
  "days": [
    { "dayNumber": 1, "travelDate": "2026-09-20", "mealSlots": [] },
    { "dayNumber": 2, "travelDate": "2026-09-21", "mealSlots": [
      { "mealSlotPublicId": "00000000-0000-0000-0000-000000000002", "mealType": "LUNCH", "anchor": null }
    ] }
  ]
}
```

List 응답은 배열이며 각 항목에 `tripPublicId`, `regionKey`, `startDate`, `endDate`, `durationDays`, `profileId`, `mealSlotCount`, `selectedAnchorCount`, `createdAt`이 포함된다. 생성 시각 내림차순으로 반환하며 Live API를 호출하지 않는다. Detail도 Live 호출 없이 저장 참조만 반환한다.

Recommendation은 요청 body 없이 호출한다. 기존 응답 필드 `context`, `perspectives`, `candidateCount`, `callSummary`, `dataAvailability`, `sourceAttribution`, `nutritionNotice`를 유지한다. 실제 준비되지 않은 이동 정보를 0점이나 성공으로 바꾸지 않는다.

Anchor PUT 요청: `{"contentId":"91001"}` (설명용 가상 ID; 실제로는 Live 추천 결과의 ID 사용).
응답: `{"provider":"KTO","contentId":"91001","contentType":"39"}`.
동일 ID 재요청은 같은 참조 유지, 다른 유효 ID는 교체한다. 클라이언트의 title/address/provider/score 등을 신뢰해 저장하지 않는다.

오류는 `{ "code": "...", "message": "..." }` 구조다. 날짜/일차/중복/식당 검증 실패는 400, 소유권/부모-자식 불일치는 404, Live 연결 장애는 503 `CURRENT_TOURISM_DATA_UNAVAILABLE`. 장애 시 기존 앵커는 보존한다.

## 로컬 실행 환경

- JDK 21 고정.
- `.env.local` 작성: 로컬 DB 연결과 제공된 공공데이터/Kakao REST 키. 실제 값은 보고서에 기록하지 않는다.
- `.env.local`은 기존 gitignore에 의해 제외되며 source/commit 후보에 포함하지 않는다.
- 기존 백엔드 환경변수 계약 `DATA_GO_KR_SERVICE_KEY`, `KAKAO_REST_API_KEY`를 사용한다. 프론트용 Kakao JavaScript 키는 백엔드에 불필요하므로 추가하지 않았다.
- 로컬 MySQL 8.4 container: `hankki-big03a-mysql`.
- 접속: `127.0.0.1:13306`, DB `hankki_planb`, user `hankki_local`.
- DB/root 비밀번호는 별도 생성한 로컬 전용 값. 학원 DB 비밀번호를 로컬 DB에 재사용하지 않는다.
- persistent volume: `hankki-big03a-mysql-data`.
- 포트는 loopback에만 게시했다. 기존 3306 서비스와 기존 Docker 데이터는 유지했다.
- 학원 DB와 운영 클러스터에는 접속하거나 변경하지 않았다.

## 검증 결과

### 자동 검증

기준 코드 전체 검증: 86 tests, failures 0, errors 0, skipped 0.
최종 코드 전체 검증: **110 tests, failures 0, errors 0, skipped 0; BUILD SUCCESS**.
실행 종료 시각: 2026-09-16 21:25:44 KST. 실행 시간 5분 24초.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\mvnw.cmd -B -ntp '-DargLine=-Xmx3g -Dspring.test.context.cache.maxSize=1' -DreuseForks=false clean verify
```

힙 및 fork 설정은 기존 영양 XLSX 회귀 검증의 메모리 사용을 위한 실행 옵션이며 테스트 skip/비활성화는 없다.

| 검증 | 결과 |
|---|---|
| 1~4일 / 최대 12끼 / 0끼 Day / 정렬 | PASS |
| 5일·역전 날짜·일차 누락/중복·식사 중복·전체 0끼·미지원 지역 거부 | PASS |
| Profile 소유권 / 다른 Guest / 다른 Trip 슬롯 조작 | PASS |
| Trip 원자적 생성 및 중간 실패 rollback | PASS |
| MySQL Day/Slot/Anchor unique 및 cascade | PASS |
| 같은 앵커 반복·다른 앵커 교체·동시 PUT 1행·반복 해제 | PASS |
| 비식사·타지역·잘못된 ID 거부 및 Live 장애 시 원래 앵커 보존 | PASS |
| 프로필 수정 반영 / 지역·날짜·mealType 전달 / 이동 정보 부족 방어 | PASS |
| 새 MySQL DB V1→V11 / 재실행 pending 0 | PASS |
| V10 legacy draft 존재 DB 업그레이드 / 행 보존 | PASS |
| Guest/Profile/기존 추천/Live/Nutrition/ArchUnit 회귀 | PASS |
| HTTP 7개 경로 / no-store / DELETE CORS | PASS |

검증 과정에서 신규 rollback 테스트의 MyBatis mock 호출 방식을 수정했고, V11의 날짜 계산을 H2와 MySQL이 모두 지원하는 TIMESTAMPDIFF로 수정했다. 이후 전체 검증을 다시 통과했다. 기존 마이그레이션을 고치거나 테스트 조건을 완화하지 않았다.

### 로컬 Docker 실기동

- MySQL engine 실제 버전: 8.4.11.
- Flyway latest V11, 성공 이력 11개. 서버 재기동 시 `No migration necessary` 확인.
- `/actuator/health`: UP.
- 영양 XLSX 원본 19,617건 → 적재 19,534건 / 제외 83건, dataset version 2026-08-28.
- import 전용 실행은 최초 `-Xmx2g`에서 메모리 부족으로 실패했다. `-Xmx4g`로 재실행하여 정상 종료했고 적재 건수를 DB로 확인했다. 정상 웹 서버는 import profile 없이 `-Xmx1g`로 기동했다.
- 로컬 서버: `http://localhost:8300`. 테스트 완료 시 서버와 전용 DB를 실행 상태로 유지했다.
- Smoke에서 만든 Trip/Day/Slot/Anchor는 API로 정리했다. 전용 local smoke Guest/Profile은 남아 있다.

재기동 방법(backend 디렉터리에서):

```powershell
docker start hankki-big03a-mysql
& 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\java.exe' -Xmx1g -jar target/hankki-travel-0.1.0-SNAPSHOT.jar
```

이미 서버가 실행 중이면 중복 실행하지 않는다. `.env.local`은 현재 작업 디렉터리에서 자동 로드된다. 일반 재기동 시 영양 import는 필요 없다.

### Live Smoke: RUN

가짜 관광 원문을 사용하지 않고 새 슬롯 추천 API를 통해 실제 TourAPI를 호출했다. 아래 시간은 추천 응답 자체의 계측값이다.

| 시나리오 | HTTP / 데이터 | 후보 수 | 추천 시간 | 목록/상세 조회 | 앵커 PUT/재PUT | 참조 재조회 | 해제/Trip 삭제 |
|---|---|---:|---:|---|---|---|---|
| JEJU / LUNCH | 200 / CURRENT_DATA | 4 | 2,419ms | 200/200 | 200/200 | 일치 | 204/204 |
| GYEONGJU / DINNER | 200 / CURRENT_DATA | 2 | 2,027ms | 200/200 | 200/200 | 일치 | 204/204 |

두 응답 모두 관점 상태 `READY`, `READY`, `MOVEMENT_CONTEXT_REQUIRED`를 유지했다. 출발점이 없으므로 Kakao 이동 API 호출은 0이며 **Kakao 실제 이동 조회 성공은 이번 smoke로 검증하지 않았다**.

각 추천의 기존 service 계측: Tour list 1 / detail evaluation 10 / Kakao transit 0. 현재 BIG-02의 `detail-limit=6`은 채택된 후보 수를 제한하고 실제 상세 평가 횟수는 `prefilter-limit=10`까지 가능하다. 이 기존 동작을 변경하지 않았으며 상세 호출이 항상 6회 이내라고 주장하지 않는다. detail evaluation 수는 개별 HTTP 요청 총수와도 다르다.

Smoke 종료 후 DB 확인: `trips`, `trip_days`, `trip_meal_slots`, `meal_anchors`, `tourism_places`, `restaurants` 모두 0행. 영양 기준 테이블은 19,534행. Live 결과가 관광 원문 테이블로 저장되지 않음을 확인했다.

## Known Issues / 미검증 범위

- BIG-03A 필수 기능 및 자동 검증의 미해결 실패는 없다.
- 기존 영양 XLSX import는 메모리 사용량이 크다. 로컬 적재에는 위에서 검증한 4GB 힙을 사용한다. 파서 최적화는 이번 변경에 포함하지 않았다.
- 기존 BIG-02 상세 호출 계측/상한 의미는 위 Live Smoke 설명과 같다. 추천 엔진 재설계는 하지 않았다.
- Kakao 실제 이동 조회, Frontend E2E, 학원 DB, 운영 배포는 이번 검증 대상이 아니다. 실 API smoke는 실행 시점의 외부 서비스 응답 증거다.
- V4 legacy trip은 원본 보존을 위해 새 일정 API에서 숨긴다. legacy를 신규 일정으로 변환하는 기능은 이번 범위가 아니다.

## 범위 보존 및 검수

- Frontend / Dockerfile / GitHub Actions / K8s / ApplicationSet / ArgoCD / Vercel / 운영 Secret / HTTPRoute / DNS / TLS 변경: 0.
- 새 테이블의 ownership은 Trip module이다. 다른 모듈 테이블에 새 mapper로 직접 쓰지 않는다.
- Anchor 외부 호출 중 활성 DB transaction이 없음을 통합 테스트로 확인한다.
- 공개 저장소 전환 및 원격 커밋/푸시를 수행하지 않았다.
- 일정 날짜/슬롯 수정과 관광지/숙소/Dynamic Planner는 명시적으로 후속 범위다.

최종 검사: 저장소의 기존 줄바꿈 설정으로 `git diff --check` 통과. 변경/신규 파일 21개에서 로컬 DB 비밀번호와 실제 API 키 값의 일치 검출 0. `.env.local` ignore 확인. Frontend/GitOps working tree clean. Backend local HEAD 및 다시 조회한 remote main은 모두 `e26f3132e4205d6890b2bab3f6c882b19f3d34be`이며 이번 21개 파일은 미커밋 상태다.
