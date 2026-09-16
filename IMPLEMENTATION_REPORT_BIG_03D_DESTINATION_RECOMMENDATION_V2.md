# BIG-03D Destination Recommendation V2 구현 보고서

검증일: 2026-09-17  
대상: `travel-back`, `hankki-travel`  
인프라 변경: 0

## Destination First

- `POST /api/guests/{guestPublicId}/trips/{tripPublicId}/days/{dayNumber}/focus-recommendations`로 여행 지역의 TourAPI Live 중심 장소를 추천한다.
- `GET .../focus-search?keyword=`로 사용자가 원하는 장소를 TourAPI Live keyword search로 찾는다.
- 선택과 해제는 기존 `trip_day_place_anchors`에 `DAY_FOCUS` 슬롯을 추가하는 방식으로 구현했다.
- DAY_FOCUS와 다른 일정 슬롯의 `contentId`가 같으면 플래너에는 한 번만 표시한다.
- 식당 추천은 같은 Day의 DAY_FOCUS를 조회하고, Live 상세 좌표를 얻은 경우 `PLACE_FIRST`로 추천한다. 중심 장소가 없거나 현재 좌표를 얻지 못하면 `MEAL_FIRST`로 안전하게 저하한다.
- 사용자가 조회한 TourAPI 중심 장소 후보와 식당 추천 원본 응답은 DB에 저장하지 않는다.

## Profile V2

- 가족 구성원별 `bloodSugarCare`, `allergenRestrictions`, `avoidedFoods`를 additive DTO로 추가했다.
- 혈당 관리 선택은 기존 식사 주의사항의 `SUGAR`, `CARBOHYDRATE`로 매핑한다. 의료 진단이나 질환 판정으로 표현하지 않는다.
- 알레르기와 기피 식재료는 신규 `family_member_food_restrictions` 테이블에 각각 `ALLERGEN`, `AVOID` 유형으로 저장한다.
- Flyway V13이 DAY_FOCUS 슬롯 제약과 가족 식재료 제한 스키마를 함께 추가한다.
- 기존 요청이 `bloodSugarCare`를 보내지 않아도 동작하도록 nullable 입력 호환성을 유지했다.

## Scoring

- 설정 가능한 기본 가중치는 가족 식사 적합도 40, 여행 동선 20, 이동 편의 10, 지역 방문 수요 10, 후기/평판 10, 지역 메뉴 적합도 10으로 합계 100이다.
- 시작 시 합계 100을 검증한다.
- 명시적으로 확인된 알레르기·기피 식재료 충돌은 hard constraint로 제외한다. 메뉴 정보가 없다는 이유만으로 안전하다고 판정하지 않으며 방문 전 전화 확인 안내를 낸다.
- 데이터가 없는 차원은 0점으로 벌점 처리하지 않고 `NOT_EVALUATED`로 제외한 뒤, 평가된 가중치 안에서 점수를 정규화한다.
- `overallScore`와 `evidenceCoverage`를 분리해 제공한다.
- 동점 정렬은 점수, 근거 커버리지, 가족 식사 적합도, 여행 동선, 안정적인 contentId 순으로 결정한다.

## Data Providers

| Provider | 상태 | 실제 출처 | fallback 정책 |
| --- | --- | --- | --- |
| Nutrition | ACTIVE | 로컬에 import된 식품영양성분 표준 음식 참고 데이터 | HIGH/MEDIUM 매칭만 참고 근거로 노출하고 실제 식당 영양값으로 표현하지 않는다. |
| Transit | ACTIVE | Kakao Mobility 대중교통 API | 대중교통 프로필과 유효한 중심 장소 좌표가 있을 때 후보 상위 3개만 호출한다. 실패하거나 미호출이면 해당 근거를 과장하지 않는다. 차량은 직선거리 기반 PARTIAL 근거를 사용한다. |
| Contact | ACTIVE | TourAPI `detailCommon2.tel` | 전화번호가 없으면 전화 CTA를 숨기고 공개 정보가 없음을 표시한다. |
| Area Demand | NOT_EVALUATED | 연결된 공식 Provider 없음 | 0점으로 처리하지 않고 분모에서 제외한다. 가짜 혼잡도나 수요를 만들지 않는다. |
| Review | NOT_EVALUATED | 연결된 공식 Provider 없음 | 0점으로 처리하지 않고 분모에서 제외한다. 후기 scraping을 하지 않는다. |
| Map/Review Link | NOT_EVALUATED | 안전하게 일치시킬 Kakao Local 장소 Provider 없음 | 이름·주소 매칭이 불명확하므로 `placeUrl=null`을 유지해 잘못된 링크를 만들지 않는다. |

## Restaurant Card

- 지역 라벨, 주소, 전체 점수, evidence coverage, 6개 차원별 평가 여부·획득점수·최대점수·근거를 표시한다.
- TourAPI 전화번호가 존재할 때만 `tel:` CTA를 표시한다.
- 정확히 일치한 지도/후기 URL이 있을 때만 링크를 표시하도록 계약을 만들었으며 현재 Provider 부재 시 숨긴다.
- 모든 Live 관광·식당 카드에 한국관광공사 출처를 유지한다.
- 긴 주소와 설명은 모바일에서 줄바꿈되며, 360/390/768px에서 가로 overflow가 없음을 확인했다.

## Tests

- Backend: `clean verify` 122 tests, 실패 0, 오류 0, skipped 0, JAR 패키징 성공.
- Flyway: H2와 MySQL 8.4에서 V1~V13 fresh migration 성공, V10→V11→V13 upgrade 및 재실행 0건 확인.
- Frontend unit: 31 tests 통과.
- Frontend production build: 성공.
- Frontend Playwright: development 4, production 10, 합계 14 tests 통과.
- 실제 Live 브라우저: Edge headless에서 JEJU와 GYEONGJU Golden Flow 통과.
- Mobile: 실제 Live 카드로 360/390/768px 모두 horizontal overflow 0.

## Live Smoke

### JEJU

- DAY_FOCUS 추천 6건, 검색 6건, 중심 장소 선택 성공.
- 중심 장소를 사용한 식당 추천 `PLACE_FIRST`, TOP3 3건.
- 차량 프로필: TourAPI list 1회, detail 10회, Kakao transit 0회, 최고 점수 53, coverage 30.
- 대중교통 프로필 추가 검증: Kakao transit 3회, 이동 근거 생성, 최고 점수 30, coverage 30.
- 실제 브라우저: 중심 장소, 식당 카드 3건, 점수, coverage, 차원 표 렌더링 성공.

### GYEONGJU

- DAY_FOCUS 추천 6건, 검색 1건, 중심 장소 선택 성공.
- 중심 장소를 사용한 식당 추천 `PLACE_FIRST`, TOP3 2건.
- 차량 프로필: TourAPI list 1회, detail 10회, Kakao transit 0회, 최고 점수 85, coverage 30.
- 실제 브라우저: 중심 장소, 식당 카드 2건, 점수, coverage, 차원 표 렌더링 성공.

### Persistence audit

- Live Smoke 전후 `tourism_places` row delta: 0.
- Live Smoke 전후 `restaurants` row delta: 0.
- Live Smoke 전후 `tourism_sync_runs` row delta: 0.
- 사용자 경로에서 KTO raw persistence, historical sync, stale cache fallback을 호출하지 않는다.

## 종료 조건

| 조건 | 결과 |
| --- | --- |
| DAY_FOCUS Live Search/Recommend | PASS |
| DAY_FOCUS Select/Clear | PASS |
| Meal Recommendation uses DAY_FOCUS | PASS |
| Profile V2 Allergens | PASS |
| Profile V2 Avoid Foods | PASS |
| Blood Sugar Care mapping | PASS |
| Hard Constraint | PASS |
| 100-point Compatibility | PASS |
| Evidence Coverage | PASS |
| Route Score | PASS |
| Mobility Score | PASS |
| Demand Provider | NOT_EVALUATED correctly |
| Review Provider | NOT_EVALUATED correctly |
| Restaurant Area Label | PASS |
| Phone CTA | PASS when evidence exists |
| Map/Review CTA | PASS when evidence exists; current provider absent |
| KTO Raw Persistence | 0 |
| Review Scraping | 0 |
| Fake Congestion | 0 |
| Local Live E2E JEJU | PASS |
| Local Live E2E GYEONGJU | PASS |
| Mobile 360/390/768 | PASS |
| Infra Changes | 0 |

## 남은 기획 Inventory와 우선순위

1. 공식적이고 이용 약관에 맞는 지도/후기 Provider를 정한 뒤, 이름·주소·좌표를 모두 사용한 엄격한 장소 일치와 URL 근거를 추가한다.
2. 공식 지역 수요 지표의 출처·갱신 주기·지역 단위를 확정한 뒤 Area Demand Provider를 연결한다.
3. 배포 후 `api.hankki.re.kr`에서 DAY_FOCUS → 식당 추천 → 선택 → 일정 보기 운영 Smoke를 수행하고 Argo CD Sync/Healthy, Pod readiness, 실제 도메인 CORS/TLS를 별도 확인한다.

현재 1~2번은 공식 출처가 정해지기 전까지 `NOT_EVALUATED`가 정답이며, 임의 점수·scraping·오매칭 링크로 대체하지 않는다.
