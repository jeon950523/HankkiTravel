# 한끼여행 BIG-04C.2 구현·검수 보고서

- 작성일: 2026-09-17
- 범위: Production UX & Integration Hotfix
- 인프라 변경: 0
- 사용자 GPS 사용: 0
- KTO/추천/Planner 신규 영구 저장: 0

## Restaurant Evidence

식당 추천 내부 모델의 `distanceFromAnchorKm`를 API에서 `distanceMeters`로 additive 노출했다.
기존 `transportEvidence`는 그대로 유지하고 `routeDataAvailability`를 추가했다.

| Profile / 상태 | 사용자 표시 |
|---|---|
| CAR + 거리 있음 | `중심 장소에서 직선거리 약 1.8km` / `실제 도로 이동시간은 지도에서 확인해 주세요.` |
| PUBLIC_TRANSIT + route 있음 | `대중교통 약 N분 · 환승 N회` / `명시 도보 Nm` |
| PUBLIC_TRANSIT + route 없음 | `대중교통 경로를 현재 확인하지 못했어요.` / 카카오맵 확인 안내 |

- 자동차 주행시간·도로거리 추정 생성: 0
- `unaccountedDistanceMeters`를 도보거리로 표시: 0
- 이동 근거가 점수 근처에서 직접 노출되도록 식당 카드 우선순위를 보완했다.

## External CTA Matrix

| phone | strict placeUrl | 노출 CTA |
|---|---|---|
| X | O | 지도·후기 보기 |
| O | O | 전화하기 / 지도·후기 보기 |
| O | X | 전화하기 / 카카오맵에서 검색 |
| X | X | 카카오맵에서 검색 |

검색 fallback은 `식당명 + 주소`를 카카오맵 검색 URL에 전달할 뿐 strict match로 취급하지 않는다.
후기 점수·전화번호를 검색 결과에서 생성하거나 저장하지 않는다.

## Planner CTA

- Planner section anchor: `#day-planner`
- `오늘 일정 보기`: `scrollIntoView({ behavior: 'smooth', block: 'start' })`
- 이동 후 `지도와 오늘의 일정` heading에 programmatic focus
- heading은 `tabindex=-1`
- DAY_FOCUS가 없으면 CTA를 렌더링하지 않는다.

Playwright에서 CTA 클릭 후 Planner heading visible + focused를 검증했다.

## Error Isolation

| 대상 | 처리 |
|---|---|
| 관광지 추천 | `recommendationErrors[trip:day:slot]`에 격리; 기존 카드·Planner 유지 |
| 숙소 추천 | STAY 전용 오류에 격리; 기존 카드·Planner 유지 |
| Planner | 기존 Planner를 삭제하지 않고 오류 안내와 재시도 CTA 유지 |
| fetch network reject | 브라우저 원문 대신 사용자용 재시도 문구; 기술 원인은 console error로 분리 |

502 fixture E2E에서 관광지/숙소 영역 오류, 기존 Planner 유지, Planner CTA 유지, page fatal error 0을 확인했다.

## 502 Root Cause

분류: **BACKEND_RESTART**

근거:

1. GitHub Actions run `35197725343`이 08:10:29Z~08:11:05Z AWS 배포를 수행했다.
2. 문서 보고서 커밋도 backend workflow를 촉발해 run `35199084315`가 08:25:19Z~08:25:48Z AWS 배포를 다시 수행했다.
3. 두 번째 배포 로그에서 기존 container image를 새 image로 교체한 뒤 health check가 `1/45`부터 `9/45`까지 실패하고 성공했다. 약 18초 동안 backend가 ready가 아니었다.
4. 배포 방식은 `docker compose up -d --no-deps backend` 단일 container 교체라, 교체 중 Caddy upstream이 연결되지 않는 구간이 생길 수 있다.
5. 현재 반복 측정은 health 5/5 HTTP 200, place/stay/planner ownership miss 5/5 HTTP 404이며 모든 응답은 약 0.048~0.243초였다.
6. 현재 애플리케이션 404에는 `Access-Control-Allow-Origin: https://hankki.kro.kr`, `Via: 1.1 Caddy`가 존재한다.

판정:

```text
backend container restart window
→ Caddy upstream 502
→ proxy-generated 502 response에 CORS header가 없어서 브라우저가 CORS도 함께 표기
```

따라서 `CORS_ONLY`가 아니다. CORS header를 덧붙여 502를 숨기는 수정은 하지 않았다.

별도 Infra Task 권장 최소 범위:

- Markdown-only commit이 backend image build/deploy를 일으키지 않도록 workflow path filter 보완
- 단일 container 교체 시 upstream 공백을 없애는 health-gated/blue-green 방식 검토
- Caddy 502 응답에 관측용 request id와 로그 상관관계 확보

이번 Task에서는 workflow, AWS, Caddy, Compose를 수정하지 않았다.

## Live

| 항목 | 결과 |
|---|---|
| 운영 Home / Trips | PASS: BIG-04C 배포 및 `내 여행` 메뉴 확인 |
| 운영 health | PASS: 5/5 HTTP 200 |
| JEJU 실제 추천 | PENDING: 읽기 전용 브라우저 Guest에 저장 Trip/Profile이 없어 운영 데이터 생성 없이 재현 불가 |
| GYEONGJU 실제 추천 | PENDING: 동일 |
| place/stay/planner proxy 경로 | PASS: 존재하지 않는 테스트 식별자에서 각각 5/5 HTTP 404, 502 재현 0 |

## Mobile

- 360px: PASS
- 390px: PASS
- 768px: PASS
- 식당 이동 근거와 외부 CTA overflow 0
- 관광지/숙소 section error 시 기존 Planner 유지 PASS

## Regression

- Frontend Vitest: 54 tests PASS
- Frontend production build: PASS
- Frontend Playwright: development 4 + production 14 = 18 tests PASS
- Backend clean verify: 142 tests PASS, failures 0, errors 0

## Gate 상태

코드·로컬 자동 검증·502 원인 분리는 PASS다. 최신 hotfix 배포 후 실제 JEJU/GYEONGJU 추천 smoke만 남아 있으므로 BIG-04C.2의 최종 운영 Gate는 `PENDING LIVE SMOKE`로 기록한다.
