# 한끼여행 BIG-04C.1 검수 보고서

- 작성일: 2026-09-17
- 범위: Resume/Edit Production Gate
- 원칙: 인프라·AWS·Vercel·DNS 설정은 변경하지 않았다.

## 배포 및 회귀 결과

| 항목 | 결과 | 근거 |
|---|---|---|
| 백엔드 원격 반영 | PASS | `2813724 fix: 식사 해제와 디저트 일정 정합성 보완`을 `hankki_travel_back/main`에 push |
| 프론트 원격 반영 | PASS | `b375e5c fix: 여행 삭제 확인 접근성 보완`을 `hankki-travel/main`에 push |
| 백엔드 전체 검증 | PASS | Docker Desktop/Testcontainers 포함 `mvnw.cmd -B -ntp clean verify`: 88 tests, failure 0, error 0 |
| 프론트 단위 테스트 | PASS | Vitest 44 tests PASS |
| 프론트 production build | PASS | Vite/PWA build PASS |
| 프론트 E2E | PASS | 개발 4 + 운영 12, 총 16 PASS |
| AWS health | PASS | `https://api.hankki.r-e.kr/actuator/health`가 HTTP 200 및 UP 응답 |
| 공개 Guest GET 차단 | PASS | `GET /api/guests`가 HTTP 403; 생성 전용 endpoint가 읽기로 노출되지 않음 |

## Browser Recovery

| 항목 | 결과 | 비고 |
|---|---|---|
| Home Recent Trip | BLOCKED | 최신 원격 커밋은 있으나 운영 `https://hankki.kro.kr/`가 아직 이전 번들을 응답한다. `내 여행` 메뉴와 Home recent card가 DOM에 없다. |
| `/trips` 목록/빈 상태 | BLOCKED | 위 운영 프론트 배포 반영 후 테스트 전용 Guest로 확인 필요 |
| Direct URL / F5 / 새 탭 | BLOCKED | 이전 운영 번들로 최신 route를 검증하면 의미가 없으므로 실행하지 않음 |
| 삭제 Confirm 접근성 | PASS (local) | `role=dialog`, `aria-modal`, title 연결, 취소 버튼 default focus, Tab focus trap, Escape 닫기, trigger focus 복귀 보완 |

## Mutation Matrix

운영 프론트가 최신 번들로 갱신되지 않아 Live mutation을 보류했다. 사용자 데이터를 이전 코드로 수정하지 않기 위한 판단이다.

| 대상 | Re-recommend | Replace | Clear | 실패 시 기존 유지 |
|---|---|---|---|---|
| DAY_FOCUS | BLOCKED | BLOCKED | BLOCKED | BLOCKED |
| Meal | BLOCKED | BLOCKED | BLOCKED | LOCAL/API contract PASS |
| Lunch Dessert | BLOCKED | BLOCKED | BLOCKED | LOCAL/API contract PASS |
| Dinner Dessert | BLOCKED | BLOCKED | BLOCKED | LOCAL/API contract PASS |
| Morning Activity | BLOCKED | BLOCKED | BLOCKED | BLOCKED |
| Afternoon Activity | BLOCKED | BLOCKED | BLOCKED | BLOCKED |
| Stay | BLOCKED | BLOCKED | BLOCKED | BLOCKED |

## Atomic Clear / Marker Audit

- Meal clear → 연결된 `POST_LUNCH_DESSERT` 또는 `POST_DINNER_DESSERT` clear는 같은 transaction으로 구현되어 있다.
- `TripPlannerMysqlIntegrationTest`를 포함한 전체 `clean verify`가 PASS했다.
- 운영 브라우저의 old marker residual, current-day-only network, 실제 API failure injection은 최신 프론트 배포 후에만 판정한다.

## Ownership / Stale Recovery

| 검증 | 결과 |
|---|---|
| Cross-guest trip/meal/day mutation | BLOCKED: 최신 UI를 통한 실제 공격성 조합 대기 |
| Missing trip에서 Guest 재발급 0 | BLOCKED: 최신 UI Direct URL 대기 |
| Stale guest bootstrap | BLOCKED: 최신 UI Home bootstrap 대기 |

## Live / Mobile / Persistence

| 항목 | 결과 |
|---|---|
| JEJU Live E2E | BLOCKED: 최신 운영 프론트 배포 대기 |
| GYEONGJU Live E2E | BLOCKED: 최신 운영 프론트 배포 대기 |
| 360 / 390 / 768 | PASS (Playwright E2E overflow 및 핵심 Wizard/Live 카드 회귀) |
| Home/Trips/Confirm 실제 운영 모바일 | BLOCKED: 최신 운영 프론트 배포 대기 |
| API runtime cache | PASS (운영 E2E: PWA는 API 응답을 장기 캐시하지 않음) |
| browser business persistence | BLOCKED: 최신 운영 프론트 배포 후 storage audit 필요 |
| KTO/recommendation/planner 영구 저장 | 기존 Authority 유지; 이번 변경에서 추가 write 0 |

## 배포 판정

백엔드는 `api.hankki.r-e.kr`에서 정상 응답하고 원격 main 반영도 완료됐다. 프론트 원격 main은 `b375e5c`까지 반영됐지만 운영 `hankki.kro.kr`는 이전 번들을 제공한다. 저장소의 `.github/workflows/frontend-ci.yml`은 CI 검증만 수행하며 배포 단계가 없다. 따라서 Vercel 프로젝트의 Git 연결/배포 상태가 최신 `main`을 가리키는지 확인되고 새 번들이 노출된 뒤에만 남은 Live Gate를 재개한다.
