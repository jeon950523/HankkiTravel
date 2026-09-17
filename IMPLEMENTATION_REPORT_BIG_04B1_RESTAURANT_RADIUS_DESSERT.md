# BIG-04B.1 구현 보고서

## 완료 범위

- DAY_FOCUS 좌표가 있으면 TourAPI `locationBasedList2`와 `contentTypeId=39`으로 식당 후보를 반경 단계(1km, 3km, 5km)에서 조회한다.
- 반경 단계마다 contentId를 중복 제거하고, 최소 후보 수에 도달하면 다음 반경 호출을 중단한다.
- MEAL/MIXED 분류만 일반 식사 후보에 포함한다. 후보가 부족해도 CAFE_DESSERT/UNKNOWN으로 채우지 않는다.
- 실제 후보 수와 반경 후보 부족 상태를 응답으로 전달한다.
- 점심과 저녁의 식후 디저트를 각각 `POST_LUNCH_DESSERT`, `POST_DINNER_DESSERT`로 독립 선택·저장한다. 기존 `POST_MEAL_DESSERT`는 이미 생성된 일정의 호환성을 위해 유지한다.
- 디저트는 식사 앵커 기준으로 실시간 조회하고 CAFE_DESSERT 분류 및 명시 식품 제한을 통과한 후보만 제시한다.
- 식후 디저트 저장은 기존 day place anchor의 최소 참조 구조를 재사용하며, 중복 콘텐츠와 식사 앵커 중복은 거부한다.
- 일정 정렬에 점심 후·저녁 후 디저트를 각각 해당 식사 다음으로 반영했다.

## 비저장 원칙

- TourAPI 목록·상세·추천 후보·점수·원시 응답은 DB에 저장하지 않는다.
- 일정에는 provider, contentId, contentType 참조만 저장한다.
- 실제 제공량·레시피·알레르기 안전성·영양 섭취량을 확정적으로 표시하지 않는다.

## 검증

- Docker Desktop 및 JDK 21 기준 `./mvnw.cmd -B -ntp clean verify` 통과: 30개 테스트 묶음, 139개 테스트 PASS.
- V15는 새 MySQL 컨테이너와 V10 업그레이드 경로에서 검증하며, 점심·저녁 디저트 앵커 동시 저장을 통합 테스트로 확인한다.
- 프론트엔드 Vitest 44개 통과, production build 통과.
- 인프라, 배포, DNS, AWS, Vercel 설정은 변경하지 않았다.
