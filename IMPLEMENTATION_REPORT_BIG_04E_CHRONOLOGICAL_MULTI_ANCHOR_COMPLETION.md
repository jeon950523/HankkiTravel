# BIG-04E 구현 결과 보고서

작성일: 2026-09-18
범위: Chronological Multi-Anchor Planner + Day Completion + Final Trip Plan UX

## 결론

BIG-04E의 로컬 구현과 자동 회귀 검증을 완료했다. 추천 기준점은 `DAY_FOCUS` 고정 방식에서 대상 슬롯 직전의 가장 최근 유효 Anchor를 사용하는 방식으로 변경했고, 필수 Meal/Stay 기반 완료 상태와 최종 여행 플랜 화면을 추가했다.

운영 JEJU/GYEONGJU Live Acceptance는 이 변경이 아직 배포되지 않았으므로 수행하지 않았다. 운영 PASS는 백엔드와 프론트 배포 후 실제 여행을 만들고 삭제하는 별도 Smoke에서 확정해야 한다.

## Context Resolver

- `DayRecommendationContextResolver`는 DB에 저장된 Day Anchor의 chronology와 역할만 결정한다.
- TourAPI 상세 조회와 좌표 hydration은 `DayRecommendationOriginService`로 분리했다.
- 순서: 오늘의 중심 장소 → 아침 → 오전 관광 → 점심 → 점심 후 디저트 → 오후 관광 → 저녁 → 저녁 후 디저트 → 숙소.
- 대상 슬롯 직전의 Anchor를 최신순으로 평가하고, 좌표 조회가 실패하거나 좌표가 없으면 이전 Anchor로 후퇴한다.
- 유효 Anchor가 없으면 임의 좌표를 만들지 않고 지역 검색으로 degrade한다.
- 다음 날에는 전날 숙소와 현재 Day Focus를 별도 필드로 유지한다. 첫 Meal의 기본 origin은 현재 Day Focus이며 전날 숙소는 실제 출발 맥락으로 별도 제공된다.
- Meal 추천, 관광지 이동 맥락, 식당 Other/Search, 숙소 추천이 공통 origin 결과를 사용한다.

## Next Decision

- 사용자가 Trip 생성 시 선택한 Meal Slot만 필수 결정으로 평가한다.
- 필수 순서: 오늘의 중심 장소 → 아침/점심/저녁 chronology → 숙박일 숙소.
- 디저트와 관광지는 선택형이며 Day 완료를 막지 않는다.
- Day별 `requiredMealSlots`, `completedMealSlots`, `stayRequired`, `stayCompleted`, `requiredComplete`를 저장 Anchor에서 매번 파생한다.
- 완료 상태나 skip snapshot은 DB에 저장하지 않는다.
- Day 완료 시 다음 Day 준비 CTA, 마지막 Day 완료 시 여행 플랜 완성 CTA를 노출한다.
- Home과 여행 목록 CTA는 파생 완료 상태에 따라 `이어서 준비하기` 또는 `여행 플랜 보기`로 전환한다.

## Alternative/Search

- 식당 Other/Search에 `mealType`을 전달해 Meal별 chronology origin을 사용한다.
- 디저트 Other/Search API와 화면 흐름을 추가했다.
- 관광지와 숙소 Other/Search는 기존 Live 흐름을 유지하면서 동일한 탐색 결과 카드 표현을 사용한다.
- 탐색 결과는 추천 점수를 만들지 않고 `추천 점수 미산정 · 현재 확인 가능한 정보`로 표시한다.
- 카드에는 이미지, 이름, 주소, origin 거리/이동 근거, 가족 확인 문구, 주의사항, 제공 시 전화번호, 지도·후기 링크, 출처를 표시한다.
- 후보가 1개 또는 0개일 때 실제 수량을 안내하며, 0개이면 카카오맵 외부 검색을 제공한다.
- 외부 검색 결과는 자동 저장하거나 KTO Anchor로 간주하지 않는다.
- 추천/Search 실패 시 기존 Anchor 및 다른 Day 상태는 유지된다.

## Final Trip Plan

- 새로고침 가능한 `/travel/:tripPublicId/plan` 경로를 추가했다.
- 저장된 Trip/Anchor에서 지역, 기간, Day 수, 식사/숙소 완료 수, 이동 부담 경고를 계산한다.
- Day별 실제 Anchor만 chronology 순서로 표시한다.
- 기존 Kakao 지도, ordered marker, Timeline, Leg, Route Sanity 표현을 재사용한다.
- Day별 수정 CTA는 해당 Day 편집 화면으로 재진입한다.
- Planner hydrate는 Day별 순차 수행하며 모든 Day를 동시에 Live 조회하지 않는다.

## Enum Presentation

- 사용자 문구는 중앙 `SLOT_LABELS` 및 서버 label mapper를 사용한다.
- `LUNCH에서`, `POST_LUNCH_DESSERT` 같은 raw slot 문구를 사용자 화면에 표시하지 않는다.
- 코드·API·DOM 식별자 내부 enum 사용은 계약 유지 목적이며 사용자 표시가 아니다.

## Persistence / Data Boundary

- 신규 DB migration: 0
- completion write: 0
- optional skip write: 0
- candidate/search result write: 0
- route/planner snapshot write: 0
- KTO raw write: 0
- 기존 Anchor가 완료 상태와 최종 플랜의 정본이다.
- 로컬 관광 캐시를 사용자 플래너 응답으로 읽는 신규 경로: 0

## 검증 결과

- Backend `clean verify`: 158 tests, 실패 0, 오류 0, BUILD SUCCESS.
- MySQL/Testcontainers 및 Flyway v1→v15 업그레이드 회귀 통과.
- Frontend unit: 10 files, 66 tests PASS.
- Frontend production build: PASS.
- Browser E2E: development 4 + production 14 = 18 PASS.
- 360 / 390 / 768 overflow 및 주요 여행 흐름 회귀 PASS.
- 직접 새로고침, 지도/타임라인, Route Sanity, Resume/Edit, Dessert, Alternative/Search 회귀 포함.
- `git diff --check`: PASS.
- Infra/AWS/Caddy/K8s/Argo/Vercel/DNS/TLS 변경: 0.

## 운영 Live Acceptance

현재 상태: **배포 전 보류**

배포 후 반드시 수행할 항목:

1. JEJU에서 점심 → 관광 → 저녁을 서로 다른 권역으로 선택하고 저녁 origin 및 후보 pool 변화를 확인한다.
2. GYEONGJU 1박 2일에서 DAY1 완료 → DAY2 CTA → DAY2 완료 → 최종 여행 플랜을 확인한다.
3. 실제 후보 1개/0개 안내 및 카카오맵 fallback을 확인한다.
4. 생성한 테스트 Trip을 종료 후 DELETE한다.

운영 Live를 수행하지 않은 상태에서 `JEJU Live PASS`, `GYEONGJU Live PASS`로 보고하지 않는다.

## 종료 조건 현황

| 항목 | 결과 |
|---|---|
| DAY_FOCUS fixed-origin dependency | 제거 |
| Chronological origin resolver | PASS |
| Breakfast/Lunch/Dinner independent context | PASS |
| Previous stay/current focus separation | PASS |
| Required Meal priority / Stay rule | PASS |
| Optional blocking | 0 |
| Day/Trip derived completion | PASS |
| Dessert compact/Other/Search | PASS |
| Candidate scarcity/Kakao fallback | PASS |
| Raw slot enum user exposure | 0 |
| Final plan/summary/map/timeline/edit | PASS |
| 신규 상태·후보·경로 persistence | 0 |
| Mobile 360/390/768 자동 회귀 | PASS |
| Infra changes | 0 |
| JEJU/GYEONGJU 운영 Live | 배포 후 확인 필요 |
