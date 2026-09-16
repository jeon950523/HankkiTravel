# BIG-03D.1 Evidence Coverage 구현 보고서

작성일: 2026-09-17

## 범위와 결론

- 기존 커밋의 원격 반영을 먼저 확인한 뒤 작업했다.
- TourAPI Live First를 유지했다.
- 공식 근거가 없는 값은 `NOT_EVALUATED`로 남긴다.
- DB migration, Dockerfile, GitHub Actions, K8s, ArgoCD, HTTPRoute, DNS, TLS, production secret은 수정하지 않았다.
- 모든 신규 provider 응답은 추천 요청 메모리 안에서만 사용하며 저장하지 않는다.

## Area Demand

### Demand Strength Provider

- 공식 base URL: `AreaTarDemDsService`
- operations: `areaTarSjrnDsList`, `areaTarExpDsList`
- 전체 지표 코드: `21`, `22`
- 필드: `tarSjrnDsIxVal`, `tarExpDsIxVal`

### Resource Demand Provider

- 공식 base URL: `AreaTarResDemService`
- operations: `areaTarSvcDemList`, `areaCulResDemList`
- 전체 지표 코드: `11`, `12`
- 필드: `tarSvcDemIxVal`, `culResDemIxVal`

### 지역 단위와 기준 시점

- 지역 단위는 공식 코드 파일과 실제 응답에 맞춘 광역 2자리 `areaCd`와 5자리 `signguCd`다.
- TourAPI의 3자리 법정동 시군구 코드는 광역 코드를 앞에 붙여 공식 수요 API의 5자리 코드로 변환한다.
- 실제 공식 응답으로 확인된 기준 월은 `202509`이며 `DEMAND_REFERENCE_PERIOD`로 교체할 수 있다.
- 서비스 데이터 갱신 후 환경변수만 새 기준 월로 바꾸면 된다.

### 정규화와 합성

- 공식 관광데이터랩은 지수가 0~100 고정 범위가 아니며 월별 지역 최소·최대가 새로 계산된다고 설명한다.
- 각 operation을 광역 지역 단위로 한 번 호출해 같은 월의 시군구 값을 받고, 광역 집계행(`signguCd=0`)을 제외한 최소값을 0, 최대값을 100으로 min-max 정규화한다.
- 체류·소비의 가용 값을 평균해 Demand Strength를 만들고, 관광 서비스·문화 자원의 가용 값을 평균해 Resource Demand를 만든다.
- 두 family가 있으면 50:50, 한 family만 있으면 해당 값만 사용한다. 누락값을 0점으로 넣지 않는다.

### Call budget

- 추천 요청 안에서 `areaCd:signguCd`별 결과를 재사용한다.
- 한 unique region당 Demand Strength 2 operation, Resource Demand 2 operation이다.
- 후보마다 수요 API를 반복 호출하지 않고 provider 원문이나 정규화 결과를 저장하지 않는다.

## Nutrition

- 로컬 `nutrition_foods` 19,534행을 우선 사용한다.
- `HIGH`는 자동 평가 가능하다.
- `MEDIUM`은 `APPROVED` 상태만 평가 가능하다.
- `LOW`와 `NONE`은 영양 점수에 넣지 않는다.
- 메뉴가 없으면 공개 메뉴 부족과 전화 확인 안내를 표시한다.
- 메뉴는 있으나 신뢰 가능한 매치가 없으면 표준 영양 기준과 충분히 일치하지 않아 점수에 반영하지 않았다고 표시한다.
- 화면 근거명은 `표준 음식 기준`, `유사 음식 기준`이며 특정 식당의 실제 영양값으로 표현하지 않는다.

## Kakao Local

- keyword search 결과에서 허용된 장소 필드만 request scope에서 읽는다.
- strict match 조건은 정규화 이름 완전 일치, 실제 주소 일치 또는 설정 거리 이내 좌표, 음식점 카테고리다.
- 최대 거리는 `CONTACT_MATCH_MAX_DISTANCE_METERS`이며 기본 120m다.
- 신뢰 가능한 후보가 복수면 `AMBIGUOUS`, 먼 좌표·다른 지점명·비음식점은 `REJECTED`다.
- 전화 우선순위는 `KTO_DIRECT`, KTO 전화가 없을 때만 `KAKAO_STRICT_MATCH`다.
- strict match일 때만 `placeUrl`을 반환하고 후기 점수는 만들지 않는다.

## Scoring

- 6개 dimension과 기존 가중치 합계 100을 유지했다.
- `AREA_DEMAND_SIGNAL`만 공식 provider 성공 시 평가한다.
- `REVIEW_SIGNAL`은 계속 `NOT_EVALUATED`다.
- `overallScore`는 평가된 dimension만 정규화하며 `evidenceCoverage`는 평가된 가중치 합이다.

## Live Smoke

실제 장소명과 인증키는 기록하지 않았다.

| 지역 | 후보 | Demand | Nutrition | Phone | placeUrl | overallScore | Before coverage | After coverage | 호출 요약 |
|---|---:|---:|---:|---:|---:|---|---|---|---|
| JEJU | TOP3 | 3/3 평가 | 0/3 | 2/3 | 2/3 | 75,75,75 | 0%,0%,0% | 10%,10%,10% | Tour list 1, detail 10, strength 2, resource 2, Kakao Local 4, transit 0 |
| GYEONGJU | TOP2 | 2/2 평가 | 0/2 | 1/2 | 1/2 | 92,92 | 0%,0% | 10%,10% | Tour list 1, detail 10, strength 2, resource 2, Kakao Local 2, transit 0 |

`Before coverage`는 03D 기준 동작에서 AREA와 REVIEW가 `NOT_EVALUATED`이고 동일 후보의 신뢰 가능한 영양 매치도 없는 상태를 적용한 기준값이다. `After coverage`는 03D1 실제 live smoke에서 공식 AREA dimension(가중치 10)만 평가된 결과다. 과거 버전을 다시 실행한 값과 이번 버전의 실제 실행값을 혼동하지 않도록 구분했다.

실제 후보에서 영양 자동 매치가 0인 것은 reference 미적재가 아니다. 로컬 reference 19,534행은 확인됐고, 해당 공개 메뉴가 HIGH 또는 승인 MEDIUM으로 매칭되지 않아 점수에 넣지 않은 결과다.

## Frontend

- 지역 방문 수요 평가/미평가, 후기 미평가, 전화 CTA, 외부 지도·후기 CTA, 표준/유사 영양 근거와 메뉴 부족 안내를 검증했다.
- 360/390/768px에서 문서 가로 overflow가 없음을 확인했다.
- localStorage는 guest public id 외 추천/provider 결과를 저장하지 않는다.

## Persistence

- KTO raw write = 0
- Demand raw write = 0
- Demand normalized result write = 0
- Kakao Local write = 0
- phone/placeUrl write = 0
- Nutrition runtime match write = 0
- Recommendation write = 0

## 검증

- Backend 핵심 14 tests PASS 후 정규화 parser test를 추가했다.
- Frontend unit 31 tests PASS.
- Frontend production build PASS.
- Frontend browser: development 4 tests PASS, production 10 tests PASS.
- 최초 전체 backend 132 tests 중 새 strict matcher test 1건이 짧은 placeholder 주소를 허용해 FAIL했고, 8자 미만 주소 근거를 거부하도록 수정했다. 이후 `mvn clean verify`에서 최종 133 tests, failures 0, errors 0, skipped 0으로 PASS했으며 실행 JAR 생성까지 확인했다.
