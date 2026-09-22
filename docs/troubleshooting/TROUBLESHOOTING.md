# Troubleshooting

한끼여행 개발 과정에서 실제로 발생했던 문제 중, 포트폴리오에서 기술적 깊이를 설명할 가치가 있는 사례만 정리했습니다.

단순 오타, 스타일 수정, 일회성 UI 깨짐은 제외하고 다음 기준을 우선했습니다.

- 아키텍처 변경이 필요했던 문제
- 외부 API/공공데이터의 실제 제약을 다룬 문제
- 운영 배포 환경에서만 드러난 문제
- 추천 품질/데이터 정합성에 직접 영향을 준 문제
- 테스트 환경과 운영 환경의 차이를 줄인 문제

> 공개 저장소용 문서이므로 API Key, 비밀번호, 내부 식별자, 운영 Guest/Trip ID, 로컬 절대경로 등 민감하거나 불필요한 값은 제거했습니다.

---

## Portfolio Highlights

포트폴리오와 면접에서 우선적으로 강조할 대표 사례입니다.

| 우선순위 | 사례 | 강조 포인트 |
|---|---|---|
| ⭐ 1 | TourAPI Cache First → Live First 전환 | 외부 데이터 정책 변화에 맞춘 아키텍처 재설계, 데이터 정합성/컴플라이언스 |
| ⭐ 2 | 운영 502를 CORS 문제로 오판하지 않고 Backend Restart로 분리 | Reverse Proxy·배포 로그 기반 장애 분석, 부분 장애 격리 |
| ⭐ 3 | 18.9km 관광지가 1순위가 되던 추천 문제 | 추천 품질, 최신 Anchor 문맥, bounded 외부 API 호출, route sanity |
| ⭐ 4 | Mock에서는 통과하지만 Production에서 깨지는 계약 차이 | Production parity, optional field, PWA stale bundle, 실운영 검증 |
| ⭐ 5 | KTO 장소와 Kakao Local을 strict entity matching으로 연결 | 외부 Provider 간 Entity Resolution, 오연결 방지, 데이터 신뢰성 |

---

# 1. 관광 원본 저장 정책 변경으로 Cache First 아키텍처를 Live First로 전환

**포트폴리오 대표 사례 ⭐**

## 문제

초기에는 TourAPI 호출 제한과 외부 장애에 대비하기 위해 관광 데이터를 로컬 DB에 적재하고 증분 동기화하는 `Cache First + Periodic Sync` 구조를 구현했습니다.

그러나 공모전 운영 과정에서 한국관광공사 원본 관광데이터를 DB에 저장하려면 별도 승인이 필요하다는 안내를 받았습니다.

기존 구조를 그대로 유지하면 기술적으로는 동작하더라도, 최종 제출 서비스의 데이터 활용 정책과 충돌할 수 있는 상황이었습니다.

## 증상

런타임 오류가 발생한 문제는 아니었지만 다음 리스크가 발생했습니다.

- 사용자 추천 경로가 로컬 관광 캐시를 Source of Truth로 사용
- TourAPI 원문/상세 데이터를 지속적으로 DB에 적재
- API 장애 시 stale 데이터를 현재 정보처럼 보여줄 가능성
- 이미 구현한 Sync/Admin 구조와 제출용 Production 구조가 서로 다른 방향을 요구

즉, **기능 구현은 성공했지만 운영 정책상 현재 아키텍처를 그대로 사용할 수 없는 문제**였습니다.

## 원인

초기 아키텍처는 아래 요구를 우선했습니다.

```text
TourAPI 호출량 절감
외부 장애 대응
일정한 응답속도
증분 동기화를 통한 데이터 최신성 확보
```

하지만 실제 운영 조건에서는 추가 제약이 생겼습니다.

```text
관광 원본 데이터 영속 저장에는 별도 승인 필요
```

초기 설계 시 기술적 제약만 고려했고, 최종 운영 단계에서 데이터 이용 정책이라는 새로운 제약이 추가된 것이 원인이었습니다.

## 분석 과정

기존 코드와 데이터 흐름을 다음 기준으로 다시 분류했습니다.

```text
1. 반드시 로컬에 저장해야 하는 데이터
2. 외부 provider reference만 저장하면 되는 데이터
3. 요청 시점에 다시 조회할 수 있는 데이터
4. 영속하면 안 되는 외부 원문/런타임 계산 결과
```

그 결과 사용자 여행 상태와 관광 원본을 분리했습니다.

저장 허용:

```text
Guest / Profile / Trip / Day / Meal Slot
사용자가 확정한 최소 KTO reference
Nutrition reference
```

저장하지 않음:

```text
TourAPI raw payload
실시간 상세정보
메뉴 원문 복제
추천 후보/점수
이동 결과
runtime match 결과
```

선택한 장소는 `provider + contentId + contentType`만 저장하고, 화면에 필요한 최신 상세정보는 다시 TourAPI에서 hydrate하는 구조로 변경했습니다.

## 해결

Production 계약을 `TourAPI Live First`로 전환했습니다.

```text
contentId
→ runtime TourAPI 조회
→ detailCommon/detailIntro 정규화
→ Nutrition reference 결합
→ 사용자 응답
```

추가로 다음 규칙을 적용했습니다.

- 사용자 경로에서 로컬 관광 캐시 조회 금지
- TourAPI 응답 `Cache-Control: no-store`
- PWA의 `/api/**` runtime cache 금지
- 외부 API 실패 시 오래된 데이터를 대신 보여주지 않고 `현재 정보 확인 불가`로 degrade
- 기존 Sync/Admin 기능은 삭제하지 않고 Historical/Inactive로 격리

## 검증

- 실제 TourAPI 목록/상세 조회 HTTP 200 확인
- 사용자 추천/Planner 경로의 `tourism_places`, `restaurants` 캐시 read 0 확인
- KTO raw/detail payload 영속 write 0 확인
- PWA `/api/**` 동적 캐시 0 확인
- 제주/경주 Production Smoke에서 Live 데이터 기반 추천/Planner 정상 동작 확인

## 배운 점

외부 데이터 기반 서비스에서는 API 명세뿐 아니라 **데이터 저장 권한과 이용 정책도 아키텍처의 일부**라는 점을 배웠습니다.

또한 외부 원본을 DB에 복제하지 않고도,

```text
최소 reference 영속
+
runtime hydration
```

구조로 사용자 상태의 지속성과 최신 외부 데이터 활용을 동시에 만족시킬 수 있었습니다.

---

# 2. 운영 502/CORS 오류의 실제 원인이 CORS가 아니라 Backend Restart였던 문제

**포트폴리오 대표 사례 ⭐**

## 문제

운영 환경에서 관광지/숙소/Planner API 요청이 간헐적으로 `502`와 함께 브라우저 CORS 오류처럼 보이는 현상이 발생했습니다.

표면적으로는 프론트와 백엔드 도메인이 분리된 구조였기 때문에 CORS 설정 오류로 보기 쉬웠습니다.

## 증상

브라우저에서는 다음 문제가 함께 나타났습니다.

```text
API 요청 실패
502 Bad Gateway
CORS 관련 메시지
```

문제 시점에는 일부 추천/Planner 요청이 실패했고, Proxy가 반환한 오류 응답에는 애플리케이션의 CORS Header가 존재하지 않았습니다.

처음 화면만 보면 `CORS 설정 문제`로 판단하기 쉬운 상황이었습니다.

## 원인

GitHub Actions와 배포 로그를 시간순으로 대조한 결과 실제 원인은 Backend container 교체 구간이었습니다.

배포 방식은 단일 Backend container를 다음 방식으로 교체하고 있었습니다.

```text
docker compose up -d --no-deps backend
```

기존 container 종료 후 새 container가 Ready 상태가 되기 전까지 약 18초 동안 Caddy가 연결할 upstream이 없는 구간이 생겼습니다.

실제 흐름은 다음과 같았습니다.

```text
Backend container restart
→ 새 container health check 대기
→ Caddy upstream 연결 실패
→ Caddy가 502 반환
→ Proxy 502 응답에는 앱 CORS Header 없음
→ Browser가 CORS 오류도 함께 표시
```

즉 CORS는 원인이 아니라 **502 응답에서 함께 보인 2차 증상**이었습니다.

## 분석 과정

다음 순서로 문제를 분리했습니다.

1. GitHub Actions 배포 시간 확인
2. 502 발생 시각과 Backend restart 구간 비교
3. 새 container health check 이력 확인
4. 정상 상태에서 Health API 반복 호출
5. 의도적으로 존재하지 않는 리소스를 호출해 Application 404와 Proxy 502 구분
6. 정상 Application 응답의 CORS/Caddy Header 확인

정상 시점에는:

```text
health 5/5 = HTTP 200
존재하지 않는 place/stay/planner = HTTP 404
```

가 안정적으로 응답했습니다.

따라서 애플리케이션 CORS 설정 자체는 정상이라고 판단했습니다.

## 해결

잘못된 방향인 `502에 CORS Header만 추가하는 수정`은 하지 않았습니다.

대신 애플리케이션에서는 외부 장애를 화면 전체 장애로 확산시키지 않도록 오류를 격리했습니다.

```text
관광지 추천 오류 → 해당 추천 영역만 오류 처리
숙소 추천 오류 → STAY 영역만 오류 처리
Planner 오류 → 기존 Planner 유지 + 재시도 CTA
Network reject → 사용자용 메시지와 console 기술 로그 분리
```

인프라 측 근본 개선 후보도 별도로 정의했습니다.

```text
문서-only commit이 배포를 재실행하지 않도록 workflow path filter
health-gated 또는 blue/green 배포
Caddy 502와 Backend log를 연결할 request correlation
```

## 검증

- 502 fixture E2E에서 기존 Planner 유지 확인
- 부분 API 장애 시 page fatal error 0
- Frontend Vitest 54 tests PASS
- Frontend Playwright 18 tests PASS
- Backend `clean verify` 142 tests PASS
- 운영 Health 5/5 HTTP 200
- place/stay/planner 테스트 경로 5/5 정상 Application 404
- 재검증 시 502 재현 0

## 배운 점

브라우저에 표시되는 오류 메시지가 반드시 Root Cause는 아니라는 점을 확인했습니다.

특히 Reverse Proxy 구조에서는:

```text
Application Error
Proxy Error
Browser Security Error
```

가 한 화면에 겹쳐 나타날 수 있으므로, 로그와 상태코드를 레이어별로 분리해서 봐야 합니다.

또한 장애를 완전히 없애는 것뿐 아니라 **한 기능의 외부 장애가 전체 사용자 흐름을 무너뜨리지 않도록 격리하는 설계**도 중요하다는 점을 배웠습니다.

---

# 3. 관광지 추천 1순위가 식당에서 18.9km 떨어진 장소였던 문제

**포트폴리오 대표 사례 ⭐**

## 문제

제주 실제 운영 흐름을 점검하던 중, 점심 식당 이후 추천된 오후 관광지 1순위가 식당에서 직선거리 약 `18.9km` 떨어진 장소로 나타났습니다.

관광지 자체는 유효한 데이터였지만, 당일 여행 플랜에서는 이동 부담이 큰 추천이었습니다.

## 증상

운영 일정:

```text
중심 장소
→ 점심 식당
→ 오후 관광지
```

에서 오후 관광지 추천이 여행 동선보다 관광지 자체 적합도에 치우쳐 있었습니다.

즉 추천 결과는 데이터상 틀리지 않았지만 사용자 관점에서는 `왜 지금 위치에서 이렇게 먼 곳을 1순위로 추천하지?`라는 문제가 생겼습니다.

## 원인

기존 추천은 관광지 자체의 지역/유형 적합성은 평가했지만, 현재 일정의 최신 Anchor 기준 이동 부담을 충분히 반영하지 못했습니다.

또한 추천 기준점을 하루 시작 장소 하나로 고정하면 실제 일정이 진행된 이후의 위치 문맥을 잃을 수 있었습니다.

## 분석 과정

먼저 추천 문제를 하나의 점수로 덮지 않고 두 관점으로 분리했습니다.

```text
NEARBY_COURSE
- 현재 동선과 이동 부담 중심

SIGNATURE_COURSE
- 관광지 정합성과 공식 지역 수요 중심
```

그리고 관광지 추천의 기준점을 대상 Slot 직전의 실제 Anchor로 정의했습니다.

예:

```text
DAY_FOCUS
→ 점심 식당
→ 오후 관광 추천

오후 관광 origin = 점심 식당
```

대중교통 Profile에서는 모든 후보에 외부 API를 호출하지 않고:

```text
TourAPI 후보
→ 지역/유형 prefilter
→ 상세 후보 제한
→ 직선거리 shortlist
→ 상위 후보만 Kakao Transit 호출
```

순서로 bounded call budget을 적용했습니다.

이동 정보가 없을 때는 `0점`으로 처리하지 않고:

```text
NOT_EVALUATED
PARTIAL
MOVEMENT_CONTEXT_REQUIRED
```

로 남겼습니다.

## 해결

관광지 추천을 두 관점으로 분리하고 이동 문맥을 점수에 반영했습니다.

`가까운 코스`는 이동 부담 비중을 가장 크게 두고, `대표 명소 코스`는 관광지 정합성과 공식 지역 수요 비중을 높였습니다.

추가로 전체 Planner에는 Route Sanity Guard를 두어 이미 선택된 일정도 다음 조건으로 다시 검증했습니다.

```text
PUBLIC_TRANSIT
- 긴 이동시간
- 많은 환승
- Day 누적 이동시간

CAR
- 긴 직선거리
```

근거가 없는 구간은 0으로 계산하지 않았습니다.

## 검증

Before:

```text
운영 감사 1순위 관광지
→ 식당 기준 직선거리 약 18.9km
```

After 자동화 시나리오:

```text
가까운 코스 1순위
→ 약 0.9km 후보
```

추가 검증:

- Backend `clean verify` 138 tests PASS
- Route-aware 신규 tests PASS
- TourAPI/Kakao LiveIntegration PASS
- Frontend unit/build/E2E PASS
- 모바일 360/390/768 overflow 0
- Kakao Transit 호출 budget 테스트에서 설정값 초과 0
- 추천 결과/거리/route persistence 0
- 이후 Global Route Sanity Guard 운영 검증에서 제주 장거리 경로 HIGH, 경주 정상 근거리 NORMAL 확인
- CAR 가짜 이동시간 0
- PUBLIC_TRANSIT 가짜 geometry 0

## 배운 점

추천 시스템에서 **정확한 후보를 찾는 것과 좋은 순서로 추천하는 것은 다른 문제**라는 점을 배웠습니다.

또한 외부 이동 API는 후보 수만큼 무제한 호출하기보다:

```text
저비용 필터링
→ shortlist
→ 고비용 외부 API
```

구조로 호출 예산을 통제해야 한다는 점을 적용했습니다.

---

# 4. Mock E2E는 통과했지만 Production 데이터 조합과 PWA 상태가 달랐던 문제

**포트폴리오 대표 사례 ⭐**

## 문제

로컬 Unit/E2E 테스트와 배포 자체는 성공했지만 실제 Production 브라우저에서 일부 CTA와 프로필 저장 후 UX가 기대대로 보이지 않는 문제가 발생했습니다.

단일 원인이 아니라 두 종류의 Production Parity 문제가 동시에 존재했습니다.

```text
1. Mock fixture가 실제 데이터보다 지나치게 완전함
2. 기존 PWA client가 이전 frontend bundle을 계속 사용
```

## 증상

### 데이터 계약 차이

기존 happy-path fixture는 식당 후보에 다음 값이 항상 존재했습니다.

```text
phone
placeUrl
menuSummary
coordinates
```

하지만 실제 Production에서는 다음 조합이 존재했습니다.

```text
전화번호 없음
strict Kakao place URL 없음
둘 다 없음
관광지는 좌표가 있어도 strict place URL 없음
```

따라서 Mock 환경에서 보이지 않던 fallback 경로가 운영에서 발생했습니다.

### PWA 번들 차이

배포 후 서버에는 최신 bundle이 올라갔지만 이미 열려 있던 PWA client는 이전 Service Worker가 관리하는 구 bundle을 계속 실행했습니다.

그 결과 최신 코드가 배포됐는데도 `수정이 운영에 반영되지 않았다`처럼 보였습니다.

## 원인

### Fixture 문제

테스트 데이터가 `모든 외부 정보가 정상적으로 존재하는 경우`에 편향되어 있었습니다.

즉 테스트가 코드 분기를 충분히 검증한 것이 아니라, fixture가 happy path만 제공하고 있었습니다.

### PWA 문제

기존 열린 client가 활성 Service Worker를 계속 붙잡고 있었고 새 Worker가 즉시 현재 탭을 교체하지 못했습니다.

배포 성공과 사용자가 실제 실행하는 frontend version이 항상 같지는 않았습니다.

## 분석 과정

Production API 응답과 Fixture를 필드 단위로 비교했습니다.

```text
Fixture has / Production missing
Production has / Fixture missing
```

형태로 차이를 정리하고, CTA를 다음 Matrix로 분해했습니다.

```text
phone O / placeUrl O
phone O / placeUrl X
phone X / placeUrl O
phone X / placeUrl X
```

또한 Production 브라우저를 다음 상태로 나눠 비교했습니다.

```text
기존 열린 PWA client
Service Worker client 종료 후 새 탭
독립 브라우저 프로필
```

이 과정을 통해 Backend 배포 문제와 Frontend stale bundle 문제를 분리했습니다.

## 해결

### 데이터 계약

필드가 없으면 값을 만들지 않는 원칙을 강화했습니다.

```text
phone 없음 → 전화 CTA 숨김
strict placeUrl 있음 → 지도/후기 CTA
strict placeUrl 없음 → Kakao 검색 fallback
좌표 있음 → 길찾기 가능
메뉴 없음 → missing-menu 안내
```

저장된 Anchor를 Final Plan에서 다시 hydrate할 때도 현재 TourAPI/Kakao 결과를 기준으로 phone/place/menu를 재구성했습니다.

Fixture 역시 optional field 조합을 포함하도록 현실화했습니다.

### Frontend/PWA 검증

오래된 Service Worker client와 새 bundle을 구분해 Production Browser Smoke 절차를 추가했습니다.

최신 client에서 다음 Golden Flow를 직접 검증했습니다.

```text
Profile 저장
→ 새 여행 시작
→ 제주/경주 신규 여행
→ Final Plan
→ 완료 여행 수정
→ 직접 검색으로 후보 교체
→ Final Plan 재진입
```

## 검증

- Frontend Unit 83 tests PASS
- Mock E2E: development 4 + production 23 PASS
- Backend `clean verify` 162 tests PASS
- 실제 Backend Trip/Recommendation/Planner 응답 HTTP 200 확인
- JEJU/GYEONGJU 신규 여행 Production Smoke PASS
- 완료 여행 Edit Re-entry Production Smoke PASS
- `phone/placeUrl` 4가지 CTA 계약 Unit 검증
- CTA 0개인 Final Plan Meal item 0
- 독립 브라우저 프로필에서 최신 bundle 확인
- Service Worker client 교체 후 stale bundle confusion 재현 0

## 배운 점

Mock E2E가 Green이라고 해서 Production 계약까지 검증된 것은 아니라는 점을 배웠습니다.

특히 외부 API를 사용할 때는:

```text
값 존재
값 부재
부분 존재
provider match 실패
```

가 모두 정상적인 입력일 수 있으므로, optional field를 실패가 아니라 **계약의 일부**로 테스트해야 합니다.

또한 PWA에서는:

```text
배포 성공
≠
사용자가 최신 bundle 실행
```

이라는 점 때문에 Production Browser Smoke가 별도 Gate로 필요했습니다.

---

# 5. Monorepo의 암묵적 의존성을 제거하고 Front/Back 독립 배포 구조로 분리

## 문제

서비스 배포 구조가:

```text
Frontend = Vercel
Backend = 별도 서버/AWS
```

로 분리되면서 하나의 Monorepo 안에 Frontend와 Backend를 함께 두는 구조가 실제 배포 경계와 맞지 않게 됐습니다.

Backend가 독립 저장소로 이동하면 빌드가 깨질 가능성도 있었습니다.

## 증상

사전 감사에서 Backend가 Monorepo 바깥 환경에 암묵적으로 의존하는 부분이 확인됐습니다.

예:

```text
상위 디렉터리의 .env.local 의존
repository 밖 Nutrition fixture 의존
Frontend/Authority와 Backend 배포 책임 혼재
```

이 상태에서 단순히 `backend/` 디렉터리만 새 저장소로 복사하면:

```text
로컬에서는 동작
CI/새 Clone에서는 실패
```

하는 구조가 될 수 있었습니다.

## 원인

초기 개발 단계에서는 하나의 저장소가 편했지만,
실제 운영 단계에서는 각 컴포넌트의 생명주기가 달라졌습니다.

```text
Frontend
- Vercel
- SPA/PWA
- Frontend CI

Backend
- Maven/JDK
- Docker/GHCR
- DB/Flyway
- AWS/K8s 배포
```

그럼에도 파일 경로와 테스트 fixture 일부는 Monorepo 구조를 전제로 남아 있었습니다.

## 분석 과정

분리 전에 Root Dependency Audit을 수행해 파일을 다음으로 나눴습니다.

```text
Frontend/Authority에 남을 것
Backend에 필요한 것
공유 참고 문서
분리 후 불필요한 것
```

Git history를 잃지 않기 위해 단순 copy가 아니라 `git subtree split`을 사용했습니다.

또한 분리 전 Recovery tag를 만들고, Backend를 새 저장소에서 fresh clone한 뒤 독립 검증하기 전에는 원본 Backend를 삭제하지 않았습니다.

## 해결

```text
git subtree split
→ Backend history 보존
→ 새 Backend repository 생성
→ 독립 clone
→ 외부 경로 의존 제거
→ 테스트 fixture repository 내부 이동
→ Backend 독립 검증
→ 기존 Front repository에서 backend 제거
```

Backend 설정도 상위 Monorepo 경로가 아닌 Backend root 기준으로 변경했습니다.

배포 계약을 명확히 분리했습니다.

```text
Frontend = Vercel
Backend = Docker/GHCR + 별도 배포
CORS = 실제 Front origin 기준
```

## 검증

Backend 독립 검증:

- JDK 21
- Maven Wrapper 독립 실행
- `clean verify` 83 tests PASS
- Testcontainers MySQL PASS
- Fresh Flyway migration PASS
- packaged JAR 독립 기동
- `/actuator/health = UP`

Frontend 회귀:

- Unit 20 tests PASS
- Production build PASS
- E2E 20 tests PASS

추가 확인:

- Backend/Frontend tracked secret finding 0
- Backend가 Frontend filesystem path에 의존하지 않음
- Frontend가 Backend source path에 의존하지 않음
- Force push/history rewrite 없이 기존 history 보존

## 배운 점

Repository 분리는 단순히 폴더를 옮기는 작업이 아니라 **실제 런타임 의존성을 드러내는 테스트**라는 점을 배웠습니다.

특히:

```text
새 저장소 fresh clone
→ build
→ test
→ migration
→ runtime health
```

까지 통과해야 비로소 독립 배포 가능한 서비스라고 볼 수 있었습니다.

---

---

# 6. 빈 운영 DB에서 Flyway `validate`가 먼저 실패해 마이그레이션 워크플로 순서를 바꾼 문제

## 문제

AWS 운영 DB를 처음 준비할 때 수동 Production Migration Workflow가 기존 DB 검증 흐름을 그대로 사용하고 있었습니다.

기존 순서는 다음과 같았습니다.

```text
현황 확인 / validate
→ migrate
```

이미 마이그레이션이 적용된 DB에서는 자연스러운 순서였지만, **완전히 빈 운영 스키마를 최초 bootstrap하는 경우**에는 이 흐름이 맞지 않았습니다.

## 증상

운영 DB를 새로 만든 뒤 Flyway 검증을 먼저 실행하자 아직 적용되지 않은 migration들이 존재하는 상태라 validation 단계에서 작업이 중단됐습니다.

즉:

```text
DB 연결 정상
migration 파일 정상
schema는 비어 있음
```

인데도 첫 운영 배포가 실패했습니다.

애플리케이션 SQL 자체가 잘못된 것처럼 보일 수 있었지만, 실제로는 **신규 DB bootstrap과 기존 DB 무결성 검증을 같은 실행 순서로 처리한 것**이 문제였습니다.

## 원인

`validate`의 목적은 현재 DB에 적용된 schema history와 로컬 migration 파일의 일치 여부를 검증하는 것입니다.

하지만 첫 운영 DB에는 아직 적용된 migration history가 없었습니다.

따라서:

```text
기존 DB
validate → migrate 가능

신규 빈 DB
migrate → validate가 적절
```

한데, 두 상황을 구분하지 않은 워크플로가 원인이었습니다.

## 분석 과정

문제를 DB 접속 실패, SQL 오류, Flyway checksum 문제로 단정하지 않고 다음을 분리했습니다.

1. DB 자체 생성 여부 확인
2. 애플리케이션 계정 연결 확인
3. Flyway migration 파일 존재 여부 확인
4. 현재 schema history 확인
5. `validate`와 `migrate`의 실행 의미 비교
6. 빈 DB에서의 최초 실행 순서를 별도 검증

그 결과 migration 파일이나 DB 권한 문제가 아니라 **실행 순서 문제**임을 확인했습니다.

## 해결

운영 Workflow를 다음과 같이 변경했습니다.

```text
기존
현황 확인·검증
→ migration

변경
migration
→ validate
→ info
```

또한 Production DB Migration은 자동 배포와 분리해 수동 확인 입력을 요구하도록 유지했습니다.

DB를 drop/recreate하거나 `flyway clean`, `repair`로 우회하지 않았습니다.

## 검증

- 운영 스키마 생성 성공
- Flyway V1부터 당시 최신 버전까지 순차 적용
- migration 이후 `flyway:validate` PASS
- `flyway:info`에서 현재 schema version 확인
- GitHub Actions Production DB Migration 성공
- 재실행 시 이미 적용된 migration이 다시 실행되지 않음

## 배운 점

Migration 도구의 명령 자체보다 **DB lifecycle에 맞는 실행 순서**가 중요하다는 점을 배웠습니다.

특히:

```text
신규 환경 bootstrap
기존 환경 upgrade
기존 환경 validation
```

은 비슷해 보여도 서로 다른 운영 시나리오이므로, CI/CD 단계에서 의미를 분리해야 했습니다.

---

# 7. TourAPI 일부 호출 실패가 전체 추천 실패로 번지지 않도록 Live Fallback과 부분 장애 격리를 추가

## 문제

`TourAPI Live First` 구조로 전환한 뒤에는 사용자 요청이 외부 API 상태에 직접 영향을 받습니다.

운영 수용 과정에서 다음과 같은 **부분 장애**가 실제로 확인됐습니다.

```text
위치 기반 식당 조회 실패
제주 복수 권역 검색 중 한 권역 실패
식당/관광지/숙소 직접 검색의 일부 upstream 실패
```

이때 한 번의 외부 호출 실패 때문에 전체 추천이나 Planner가 실패하면 Live First 구조의 사용자 경험이 지나치게 취약해집니다.

## 증상

예를 들어 `locationBasedList2` 기반 근거리 식당 조회가 실패하면:

```text
식당 후보 전체 없음
```

으로 끝날 수 있었습니다.

또 제주처럼 복수 권역을 조합해서 검색하는 흐름에서는 한 권역의 실패가 정상적으로 조회된 다른 권역 결과까지 버릴 가능성이 있었습니다.

직접 검색도 일부 TourAPI 호출이 실패하면 검색 전체가 실패하는 문제가 있었습니다.

## 원인

초기 구현은 개별 외부 호출을 하나의 성공/실패 단위로 보는 경향이 있었습니다.

하지만 실제 사용자 관점에서 중요한 것은:

```text
"모든 upstream이 성공했는가?"
```

가 아니라:

```text
"현재 확보 가능한 신뢰 가능한 결과를 계속 제공할 수 있는가?"
```

였습니다.

또한 Live First 정책상 stale DB cache로 대체하는 것은 의도적으로 금지되어 있었기 때문에, **Live 데이터 안에서의 fallback 전략**이 필요했습니다.

## 분석 과정

외부 연동을 다음 세 종류로 분리했습니다.

```text
1. 대체 가능한 조회
   - 위치 기반 식당 조회 → 동일 지역 Live 목록 조회

2. 병렬/복수 범위 조회
   - 제주 A/B 권역 중 정상 응답은 유지

3. 독립 검색 요청
   - 식당/관광지/숙소 검색 실패를 서로 격리
```

그 다음 각 실패가 반드시 전체 요청을 실패시켜야 하는지 검토했습니다.

핵심 기준은:

```text
가짜 데이터 생성 금지
stale local cache 사용 금지
성공한 Live 응답은 버리지 않기
실패한 근거를 성공처럼 표현하지 않기
```

였습니다.

## 해결

다음 fallback과 격리 로직을 추가했습니다.

```text
locationBasedList2 실패
→ TourAPI Live 지역 목록 fallback

제주 두 권역 중 한 권역 실패
→ 정상 권역 결과 유지

식당 Search 실패
→ 식당 Search 영역만 실패

관광지 Search 실패
→ 관광지 Search 영역만 실패

숙소 Search 실패
→ 숙소 Search 영역만 실패
```

Fallback에서도 Local Tourism Cache는 사용하지 않았습니다.

또한 실패한 외부 근거를 `0점`이나 성공 값으로 변환하지 않고 unavailable/partial 상태를 유지했습니다.

## 검증

운영에서 관측된 부분 장애를 재현하는 회귀 테스트를 추가했습니다.

- 위치 기반 식당 조회 실패 → Live 지역 목록 fallback PASS
- 제주 한 권역 검색 실패 → 정상 권역 결과 유지 PASS
- 식당/관광지/숙소 검색 부분 장애 격리 PASS
- Backend `clean verify`: 146 tests, failure/error 0
- Frontend unit/build PASS
- Frontend E2E 18 tests PASS
- 제주/경주 Live Flow PASS
- KTO raw write 0
- candidate/route persistence 0

## 배운 점

외부 API 연동에서는 `Retry`만이 장애 대응이 아니라는 점을 배웠습니다.

상황에 따라 더 중요한 것은:

```text
대체 가능한 Live 경로
부분 성공 보존
기능 단위 장애 격리
정직한 unavailable 표현
```

이었습니다.

특히 Live First 아키텍처에서는 stale cache에 기대지 않고도 **정상 응답의 가치를 최대한 보존하는 degrade 전략**이 필요했습니다.

---

# 8. KTO 장소와 Kakao Local 검색 결과의 잘못된 연결을 strict entity matching으로 방지

**포트폴리오 대표 사례 ⭐**

## 문제

TourAPI 장소 데이터에는 전화번호가 없거나, 사용자가 바로 열 수 있는 Kakao 장소 URL이 없는 경우가 있었습니다.

이를 보완하기 위해 Kakao Local 검색 결과를 결합하려 했지만, 단순히 `장소명 검색 결과 1위`를 연결하면 다른 지점이나 이름이 비슷한 다른 장소를 잘못 연결할 위험이 있었습니다.

잘못 연결하면:

```text
잘못된 전화번호
다른 지점의 지도/후기 링크
사용자가 선택하지 않은 장소 정보
```

를 제공하게 됩니다.

## 증상

실제 관광/음식점 이름은 다음과 같은 특성이 있었습니다.

```text
같은 상호의 여러 지점
띄어쓰기/특수문자 차이
주소 표기 방식 차이
검색 결과에 비음식점 카테고리 포함
같은 이름이지만 좌표가 먼 장소
```

따라서 이름 일부가 비슷하다는 이유만으로 Kakao 결과를 붙이는 것은 데이터 신뢰성 측면에서 위험했습니다.

## 원인

KTO의 `contentId`와 Kakao Local의 `place id` 사이에는 공통 Primary Key가 없습니다.

즉 두 Provider의 장소를 연결하려면 Entity Resolution이 필요했습니다.

단순 이름 매칭은 recall은 높지만 precision이 낮고, 이 서비스는 잘못된 연락처/후기 링크를 보여주는 비용이 더 컸습니다.

## 분석 과정

Kakao 검색 결과 중 어떤 근거를 신뢰할 수 있는지 분리했습니다.

검토한 근거:

```text
정규화된 장소명
실제 주소
KTO/Kakao 좌표 거리
Kakao category
동일 이름 후보 개수
```

그리고 `못 찾는 것`보다 `잘못 연결하는 것`을 더 위험하게 봤습니다.

따라서 모호한 후보는 강제로 선택하지 않는 방향으로 설계했습니다.

## 해결

Kakao Local 보강에 strict match 규칙을 적용했습니다.

기본 조건:

```text
정규화 이름 완전 일치
AND
(주소 일치 OR 설정 거리 이내 좌표)
AND
음식점 카테고리
```

추가 정책:

```text
신뢰 가능한 후보가 복수 → AMBIGUOUS
먼 좌표 → REJECTED
다른 지점명 → REJECTED
비음식점 → REJECTED
```

전화번호 우선순위도 정했습니다.

```text
KTO_DIRECT
→ 없을 때만 KAKAO_STRICT_MATCH
```

`placeUrl`은 strict match가 성공한 경우에만 반환했습니다.

strict match가 실패하면 잘못된 URL을 만들지 않고 Kakao 검색 fallback을 제공했습니다.

후기 점수는 별도 신뢰 가능한 Provider가 없으므로 생성하지 않고 `NOT_EVALUATED`를 유지했습니다.

## 검증

Live Smoke에서:

- 제주 TOP 후보들의 전화/placeUrl 보강 여부 확인
- 경주 TOP 후보들의 전화/placeUrl 보강 여부 확인
- strict match 성공/거절 테스트 PASS
- 짧거나 불충분한 주소 근거를 허용하던 테스트 1건을 발견해 matcher를 보수적으로 수정
- 최종 Backend 전체 테스트 133 tests PASS
- Kakao Local 결과 persistence 0
- phone/placeUrl persistence 0
- Review score 생성 0

## 배운 점

외부 Provider를 여러 개 결합할 때 가장 어려운 부분은 API 호출 자체보다 **같은 현실 세계의 Entity인지 안전하게 판단하는 것**이었습니다.

특히 사용자에게 직접 노출되는 연락처/후기 링크는:

```text
"아마 같은 곳일 것"
```

보다:

```text
"확실하지 않으면 연결하지 않는다"
```

가 더 적절한 제품 정책이었습니다.

---

# 9. AWS 전환 초기에 GHCR → SSM 자동 배포가 안정적으로 이어지지 않아 배포 단계를 분리해 검증

## 문제

학원 Kubernetes 환경의 외부 접근 제약 때문에 Backend 운영 환경을 AWS EC2로 전환했습니다.

목표 배포 흐름은 다음과 같았습니다.

```text
main push
→ Maven Verify
→ Docker image build
→ GHCR push
→ AWS 인증
→ SSM으로 EC2 배포
→ health check
```

하지만 초기 전환 시 첫 자동 배포에서 SSM 단계가 정상적으로 끝까지 이어지지 않아, 코드가 GitHub에 반영돼도 운영 Backend가 자동 갱신된다고 확신할 수 없는 상태가 있었습니다.

## 증상

초기에는:

```text
CI는 성공한 것처럼 보이지만 운영 반영 여부가 불명확
SSM 배포 실패
수동 배포 후에야 서비스 정상화
```

가 발생했습니다.

즉 문제는 애플리케이션 코드 자체보다 **CI 성공과 Production 반영 사이의 경계**였습니다.

## 원인

AWS 전환 직후에는 다음 요소가 동시에 새로 생겼습니다.

```text
GHCR image
immutable source SHA
AWS 인증
SSM command
EC2 Docker Compose
runtime env
Caddy reverse proxy
health check
```

이 여러 단계가 하나의 `배포 실패`로 보였고, 어느 경계에서 실패했는지 명확히 분리돼 있지 않았습니다.

## 분석 과정

배포를 하나의 작업으로 보지 않고 단계별 Gate로 나눴습니다.

```text
1. Maven clean verify
2. GHCR SHA image 존재
3. AWS 인증 성공
4. SSM command 실행
5. EC2가 새 SHA image pull
6. Docker Compose backend 교체
7. /actuator/health = UP
8. 외부 API domain HTTP 200
```

초기에는 수동 배포로 서비스 상태를 우선 복구하고,
그 상태와 자동배포 결과를 비교하면서 파이프라인의 각 경계를 검증했습니다.

## 해결

운영 이미지는 mutable한 `latest`만 신뢰하지 않고 source commit에 대응하는 SHA tag를 기준으로 관리했습니다.

배포 계약도 다음 순서로 고정했습니다.

```text
Verify
→ GHCR SHA image
→ AWS 인증
→ SSM deploy
→ health check
```

배포 완료 판정은 SSM command 전송 자체가 아니라 외부 health가 `UP`인지까지 확인하도록 했습니다.

이후 기능 작업에서도 같은 Backend workflow가:

```text
전체 검증
GHCR SHA build/push
AWS 인증
SSM EC2 배포
health check
```

를 반복해서 성공하는 것을 확인했습니다.

## 검증

후속 배포에서:

- Backend 전체 테스트 PASS
- GHCR full SHA image 발행 PASS
- AWS 인증 PASS
- SSM deploy PASS
- Production `/actuator/health` HTTP 200 / `UP`
- Frontend → Production API 실제 호출 PASS
- 제주/경주 Production Smoke PASS

최종 Production 감사에서도 동일 Backend HEAD에 대해 CI, GHCR, AWS SSM deploy, health 상태가 일치함을 다시 확인했습니다.

## 배운 점

CI가 성공했다고 Production 배포가 성공한 것은 아니라는 점을 다시 확인했습니다.

배포 파이프라인은:

```text
build success
image publish
remote command
runtime replacement
health acceptance
```

를 각각 독립된 Gate로 봐야 했습니다.

또한 immutable SHA image를 사용하면 `현재 운영 서버가 어떤 소스 버전을 실행 중인지`를 추적하기 쉬워졌습니다.

---

# 10. SSH 터널은 정상인데 MySQL `Access denied`가 발생해 네트워크 문제와 DB 인증 문제를 분리

## 문제

AWS EC2 내부 Docker MySQL을 로컬 DB 도구에서 관리하기 위해 SSH Tunnel을 구성했습니다.

보안을 위해 MySQL을 인터넷에 직접 노출하지 않고:

```text
Local DB Tool
→ SSH
→ EC2
→ Docker MySQL
```

구조를 사용했습니다.

그런데 연결 시도가 실패해 처음에는 Security Group, SSH Tunnel, Docker port binding 중 어디가 문제인지 불명확했습니다.

## 증상

접속 결과는 timeout이나 connection refused가 아니라:

```text
Access denied for user ...
```

였습니다.

이 메시지를 단순 연결 실패로 보면 SSH/network 설정을 계속 수정하게 될 수 있었습니다.

## 원인

`Access denied`가 MySQL에서 직접 반환됐다는 것은 이미 다음 경로가 성공했다는 뜻이었습니다.

```text
로컬
→ SSH 연결
→ EC2
→ Docker published port
→ MySQL server 도달
```

즉 문제는 네트워크가 아니라 **MySQL 계정 인증/host 권한 경계**였습니다.

애플리케이션용 DB 계정을 관리 도구에서도 그대로 쓰려 한 것도 운영상 적절하지 않았습니다.

## 분석 과정

오류를 레이어별로 분리했습니다.

```text
SSH 실패
→ SSH key / Security Group 문제

Connection refused
→ port binding / container / service 문제

Access denied
→ MySQL까지 도달, 계정/비밀번호/host grant 문제
```

현재 오류가 세 번째 유형임을 확인하고 네트워크 수정은 중단했습니다.

추가로 MySQL은 외부 전체 공개 대신 loopback에만 bind하고 SSH Tunnel을 유지하는 방향으로 보안 경계를 보존했습니다.

## 해결

애플리케이션 Runtime 계정과 운영 관리 계정을 분리했습니다.

```text
App account
- 애플리케이션 실행에 필요한 권한만

Admin account
- SSH Tunnel을 통한 관리 작업용
- 필요한 host/grant를 명시
```

DB Tool은 SSH Tunnel을 통해서만 접속하고, MySQL port를 인터넷에 직접 개방하지 않았습니다.

## 검증

- SSH Tunnel 수립 성공
- MySQL server까지 연결 성공
- 별도 관리 계정으로 인증 성공
- DB 조회 정상
- 애플리케이션 Backend의 DB health 유지
- MySQL public internet 직접 노출 없이 운영

## 배운 점

네트워크 장애를 분석할 때 **에러 메시지가 어느 레이어에서 생성됐는지**가 중요하다는 점을 배웠습니다.

특히 `Access denied`는 실패 메시지이면서 동시에:

```text
"네트워크 경로는 여기까지 정상이다"
```

라는 강한 진단 정보였습니다.

또한 운영 편의를 위해 애플리케이션 계정에 과도한 권한을 주기보다 **Runtime 계정과 Admin 계정을 분리하는 것이 더 안전한 해결**이었습니다.

---

# 추가 후보 검토 후 제외한 사례

## Prometheus / Grafana 구축

Prometheus와 Grafana를 AWS EC2에 추가하면서 scrape interval, retention, JVM/DB/모니터링 컨테이너의 메모리 한계를 검토한 작업도 있었습니다.

다만 현재 기록에서는 이것이 하나의 명확한 장애 원인→분석→해결 사건이라기보다 **운영 리소스 설계와 모니터링 구축 작업**에 더 가깝습니다.

따라서 사례 수를 늘리기 위해 억지로 Troubleshooting으로 분류하지 않았습니다.

## 단순 포트 충돌 / 도구 설정

DB Tool의 로컬 포트 충돌, 개별 Grafana Panel Query 조정, 단순 환경변수 오기입처럼 해결 범위가 작은 문제는 포트폴리오용 사례에서 제외했습니다.

---

# 최종 포트폴리오 활용 추천

10개를 전부 면접에서 같은 비중으로 이야기하기보다 아래 5개를 대표 사례로 사용하는 것이 좋습니다.

```text
1. TourAPI Cache First → Live First
   → 외부 데이터 정책과 아키텍처 경계

2. 502 + CORS처럼 보인 장애의 Backend Restart Root Cause 분석
   → 운영 장애 / Reverse Proxy / 배포

3. 18.9km 관광지 추천 개선
   → 추천 품질 / 이동 문맥 / 외부 API call budget

4. Mock ↔ Production Parity + PWA stale bundle
   → 테스트 전략 / 운영 환경 차이

5. KTO ↔ Kakao Local Strict Entity Matching
   → 외부 데이터 결합 / 데이터 신뢰성
```

보조 사례로는 다음을 상황에 맞춰 선택합니다.

```text
Flyway 운영 DB bootstrap
→ DB/배포 질문

TourAPI 부분 장애 격리
→ 외부 API 장애 대응 질문

AWS GHCR/SSM 배포 안정화
→ DevOps/Cloud 질문

SSH Tunnel/MySQL 인증 분리
→ 네트워크/DB 보안 질문

Repository Split
→ 배포 경계/프로젝트 구조 질문
```
