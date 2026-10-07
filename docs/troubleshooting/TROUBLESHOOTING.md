# HankkiTravel Troubleshooting

한끼여행 개발과 운영 과정에서 실제로 확인했던 장애, 데이터 차이, 추천 품질 문제를 정리했습니다.

단순 오타나 설정 실수보다는 원인을 여러 경계에서 확인해야 했던 사례를 남겼습니다. 정책 변화나 저장소 분리처럼 장애라기보다 구조를 바꾼 작업은 아래의 **관련 설계 결정**로 따로 구분했습니다.

공개 문서이므로 운영 계정, Secret, 내부 식별자는 제외했습니다.

---

## 주요 장애 기록

### 502와 CORS가 같이 보였지만 실제 원인은 Backend 재시작 구간이었다

운영에서 관광지, 숙소, Planner 요청이 간헐적으로 502로 실패했고 브라우저에는 CORS 관련 메시지도 같이 나타났습니다.

프론트와 백엔드 도메인이 분리되어 있어 처음 화면만 보면 CORS 설정 문제처럼 보일 수 있었습니다. 하지만 정상 응답의 CORS 설정을 바꾸기 전에 502가 나온 시각과 배포 로그를 먼저 맞춰봤습니다.

당시 Backend는 다음 명령으로 단일 컨테이너를 교체하고 있었습니다.

```text
docker compose up -d --no-deps backend
```

기존 컨테이너가 내려간 뒤 새 컨테이너가 Ready가 되기 전까지 약 18초 동안 Caddy가 연결할 upstream이 없었습니다.

```text
Backend container restart
→ 새 container health check 대기
→ Caddy upstream 연결 실패
→ Caddy 502
→ Proxy 오류 응답에는 앱 CORS header가 없음
→ 브라우저에서 CORS 메시지도 함께 표시
```

정상 상태에서는 health 요청이 반복해서 200을 반환했고, 존재하지 않는 place/stay/planner 리소스는 애플리케이션의 404로 응답했습니다. 이 차이로 애플리케이션 CORS와 Proxy 502를 분리했습니다.

502에 CORS header를 억지로 추가하지는 않았습니다. 대신 한 외부 요청의 실패가 전체 화면 오류로 번지지 않도록 추천 영역과 Planner 오류를 분리하고 기존 Planner를 유지한 채 재시도할 수 있도록 했습니다.

기존 기록의 검증 결과:

- 502 fixture에서도 기존 Planner 유지
- 부분 API 장애에서 page fatal error 0
- Frontend Vitest 54 tests PASS
- Frontend Playwright 18 tests PASS
- Backend `clean verify` 142 tests PASS
- 운영 health 5/5 HTTP 200
- place/stay/planner 테스트 경로 5/5 애플리케이션 404
- 재검증 시 502 재현 0

무중단 배포 자체는 이 수정에서 완료한 것으로 적지 않았고, health-gated 또는 blue/green 방식은 별도 개선 후보로 남겼습니다.

---

### 점심 식당 다음 관광지 1순위가 18.9km 떨어져 있던 문제

제주 운영 흐름을 확인하던 중 점심 식당 이후 추천된 오후 관광지 1순위가 식당에서 직선거리 약 18.9km 떨어진 곳으로 나왔습니다.

장소 자체는 유효했지만 현재 일정에서는 이동 부담이 컸습니다. 기존 추천은 장소의 지역/유형 적합성을 평가하고 있었지만, 일정이 진행된 뒤의 **현재 위치 문맥**을 충분히 사용하지 못했습니다.

오후 관광 추천의 기준점을 하루 시작 장소가 아니라 대상 Slot 직전의 실제 Anchor로 바꿨습니다.

```text
DAY_FOCUS
→ 점심 식당
→ 오후 관광 추천

오후 관광 origin = 점심 식당
```

추천 관점도 하나로 합치지 않았습니다.

- NEARBY_COURSE: 현재 동선과 이동 부담을 더 크게 반영
- SIGNATURE_COURSE: 관광지 적합성과 지역 대표성을 더 크게 반영

대중교통 경로를 모든 후보에 호출하지 않도록 후보를 먼저 줄였습니다.

```text
TourAPI 후보
→ 지역/유형 prefilter
→ 상세 후보 제한
→ 직선거리 shortlist
→ 상위 후보만 Kakao Transit
```

이동 근거를 얻지 못한 후보는 0점이나 가짜 이동시간으로 바꾸지 않고 NOT_EVALUATED, PARTIAL, MOVEMENT_CONTEXT_REQUIRED 상태를 유지했습니다.

운영 감사 당시 18.9km였던 1순위는 자동화 시나리오에서 약 0.9km 후보로 바뀌었습니다. 추가로 Route Sanity Guard를 통해 장거리 이동, 많은 환승, 하루 누적 이동 부담을 다시 확인하도록 했습니다.

기존 기록의 검증 결과:

- Backend `clean verify` 138 tests PASS
- Route-aware 신규 tests PASS
- TourAPI/Kakao LiveIntegration PASS
- Frontend unit/build/E2E PASS
- 모바일 360/390/768 horizontal overflow 0
- Kakao Transit 호출 budget 초과 0
- 추천 결과/거리/route persistence 0
- 제주 장거리 경로 HIGH, 경주 정상 근거리 NORMAL 확인
- CAR 가짜 이동시간 0
- PUBLIC_TRANSIT 가짜 geometry 0

---

### Mock E2E는 통과했지만 실제 데이터와 PWA가 다른 상태를 보여준 문제

로컬 Unit/E2E와 배포는 성공했는데 Production 브라우저에서 일부 CTA와 프로필 저장 이후 흐름이 기대와 다르게 보였습니다.

원인은 하나가 아니었습니다.

첫 번째는 fixture가 실제 데이터보다 너무 완전했던 문제였습니다. Mock 식당 후보에는 phone, placeUrl, menuSummary, coordinates가 항상 있었지만 실제 응답에서는 일부 필드가 없거나 조합이 달랐습니다.

실제 조합을 다음처럼 나눠 확인했습니다.

```text
phone O / placeUrl O
phone O / placeUrl X
phone X / placeUrl O
phone X / placeUrl X
```

필드가 없을 때 값을 만들어 채우지 않고 CTA도 해당 근거에 맞게 바꿨습니다.

- phone 없음 → 전화 CTA 숨김
- strict placeUrl 있음 → 지도/후기 CTA
- strict placeUrl 없음 → Kakao 검색 fallback
- 좌표 있음 → 길찾기 가능
- 메뉴 없음 → missing-menu 안내

Fixture도 optional field 조합을 포함하도록 수정했습니다.

두 번째는 PWA 상태였습니다. 서버에는 최신 bundle이 배포됐는데 이미 열려 있던 PWA client는 이전 Service Worker가 관리하는 bundle을 계속 실행할 수 있었습니다.

그래서 다음 환경을 따로 비교했습니다.

- 기존 열린 PWA client
- Service Worker client를 종료한 뒤 연 새 탭
- 독립 브라우저 프로필

최신 client에서는 Profile 저장부터 신규 여행, Final Plan, 완료 여행 수정, 직접 검색 후보 교체, Final Plan 재진입까지 Golden Flow를 다시 확인했습니다.

기존 기록의 검증 결과:

- Frontend Unit 83 tests PASS
- Mock E2E development 4 + production 23 PASS
- Backend `clean verify` 162 tests PASS
- 실제 Backend Trip/Recommendation/Planner HTTP 200
- JEJU/GYEONGJU 신규 여행 Production Smoke PASS
- 완료 여행 Edit Re-entry Production Smoke PASS
- phone/placeUrl 4가지 CTA 계약 검증
- CTA 0개인 Final Plan Meal item 0
- 독립 브라우저 프로필에서 최신 bundle 확인
- Service Worker client 교체 후 stale bundle confusion 재현 0

---

### 빈 운영 DB에서 Flyway validate를 먼저 실행해 초기 마이그레이션이 막힌 문제

AWS 운영 DB를 처음 준비할 때 수동 Production Migration Workflow가 기존 DB를 점검하던 순서를 그대로 사용하고 있었습니다.

```text
현황 확인 / validate
→ migrate
```

이미 schema history가 있는 DB에서는 사용할 수 있는 흐름이었지만, 완전히 빈 운영 스키마에서는 validate 단계가 먼저 실패했습니다.

DB 연결과 migration 파일은 정상이었고 schema만 비어 있었습니다. DB 접속, SQL 오류, checksum 문제를 각각 확인한 뒤 신규 bootstrap과 기존 DB 검증의 실행 순서가 같았던 것이 원인이라고 판단했습니다.

운영 Workflow를 다음과 같이 바꿨습니다.

```text
migrate
→ validate
→ info
```

Production DB Migration은 자동 배포와 분리된 수동 확인 작업으로 유지했고, 문제를 피하려고 DB를 drop/recreate하거나 `flyway clean`, `repair`를 사용하지 않았습니다.

기존 기록의 검증 결과:

- 운영 스키마 생성 성공
- Flyway V1부터 당시 최신 migration까지 순차 적용
- migration 이후 `flyway:validate` PASS
- `flyway:info`에서 현재 schema version 확인
- GitHub Actions Production DB Migration 성공
- 재실행 시 적용 완료 migration 재실행 없음

---

### KTO 장소에 다른 Kakao 지점 정보가 붙을 수 있었던 문제

TourAPI 장소에 전화번호나 바로 열 수 있는 장소 URL이 없는 경우 Kakao Local 결과로 보완하려고 했습니다.

문제는 KTO의 contentId와 Kakao Local의 place id 사이에 공통 ID가 없다는 점이었습니다. 장소명 검색 결과 1위를 그대로 연결하면 같은 상호의 다른 지점, 비슷한 이름의 장소, 멀리 떨어진 동명 장소가 연결될 수 있었습니다.

실제 매칭에서 확인한 근거는 다음과 같습니다.

- 정규화한 장소명
- 주소
- KTO/Kakao 좌표 거리
- Kakao category
- 동일 이름 후보 수

단순히 이름이 비슷한 것보다 잘못된 전화번호와 후기 링크를 사용자에게 보여주는 쪽을 더 위험하게 봤습니다.

strict match는 다음 조건을 사용했습니다.

```text
정규화 이름 완전 일치
AND
(주소 일치 OR 설정 거리 이내 좌표)
AND
음식점 카테고리
```

후보가 여러 개이거나 좌표가 멀고, 지점명이 다르거나 음식점이 아니면 연결하지 않았습니다.

전화번호는 KTO_DIRECT를 우선하고 값이 없을 때만 KAKAO_STRICT_MATCH를 사용했습니다. placeUrl도 strict match가 성공한 경우에만 반환했고, 실패하면 잘못된 URL을 만드는 대신 Kakao 검색 fallback을 제공했습니다.

후기 점수는 신뢰할 수 있는 별도 Provider가 없어 NOT_EVALUATED로 남겼습니다.

기존 기록의 검증 결과:

- 제주/경주 TOP 후보 전화/placeUrl 보강 확인
- strict match 성공/거절 테스트 PASS
- 불충분한 주소를 허용하던 테스트 1건을 발견해 matcher를 더 보수적으로 수정
- Backend 전체 133 tests PASS
- Kakao Local 결과 persistence 0
- phone/placeUrl persistence 0
- Review score 생성 0

---

## 짧은 장애 기록

### TourAPI 일부 실패가 전체 추천 실패로 번지던 문제

Live First 전환 뒤 위치 기반 식당 조회나 제주 복수 권역 검색 중 일부 upstream 호출이 실패하는 경우가 있었습니다.

한 호출이 실패했다고 이미 받은 정상 결과까지 버리지 않도록 실패 유형을 나눴습니다.

```text
locationBasedList2 실패
→ 같은 지역의 TourAPI Live 목록으로 fallback

제주 두 권역 중 한 권역 실패
→ 정상 권역 결과 유지

식당/관광지/숙소 Search 실패
→ 해당 검색 영역만 실패
```

fallback에서도 로컬 관광 캐시는 사용하지 않았고, 실패한 근거를 0점이나 성공 값으로 바꾸지 않았습니다.

기존 회귀 기록에서는 위치 기반 조회 fallback, 한 권역 실패 시 부분 성공 보존, 검색 기능별 장애 격리를 확인했고 Backend `clean verify` 146 tests와 Frontend E2E 18 tests가 통과했습니다.

---

### GHCR 이미지는 만들어졌지만 SSM 배포가 끝까지 이어지지 않았던 문제

AWS EC2로 Backend 운영 환경을 옮긴 초기에는 GitHub Actions가 성공해 보여도 실제 EC2 반영 여부가 불분명한 경우가 있었습니다. 첫 자동 배포에서는 SSM 단계가 정상적으로 끝까지 이어지지 않아 수동 배포로 먼저 서비스를 복구했습니다.

배포를 한 덩어리로 보지 않고 다음 단계로 나눠 확인했습니다.

```text
Maven verify
→ GHCR SHA image
→ AWS 인증
→ SSM command
→ EC2 image pull
→ Docker Compose 교체
→ /actuator/health
→ 외부 API
```

운영 이미지는 source commit에 대응하는 SHA tag를 기준으로 관리했고, SSM command 전송만으로 완료 처리하지 않고 health가 UP인지까지 확인하도록 했습니다.

후속 배포 기록에서는 GHCR full SHA 발행, AWS 인증, SSM deploy, Production health HTTP 200/UP, Frontend의 Production API 호출과 제주/경주 Smoke를 확인했습니다.

---

### SSH Tunnel은 열렸는데 MySQL Access denied가 난 문제

EC2 내부 Docker MySQL을 외부에 직접 공개하지 않고 SSH Tunnel을 통해 관리하려 했는데 DB 도구에서 연결이 실패했습니다.

오류는 timeout이나 connection refused가 아니라 `Access denied for user ...`였습니다. 이 응답이 MySQL에서 나온다는 점을 기준으로 네트워크 경로와 DB 인증을 분리했습니다.

```text
Local
→ SSH
→ EC2
→ Docker published port
→ MySQL
→ Access denied
```

SSH와 포트 경로는 이미 MySQL까지 도달하고 있었고, 문제는 계정/비밀번호/host grant 쪽이었습니다.

애플리케이션 Runtime 계정과 관리용 계정을 분리하고 DB 도구는 SSH Tunnel에서만 관리 계정을 사용하도록 했습니다. MySQL port는 인터넷에 직접 열지 않았습니다.

기존 기록에서는 Tunnel 수립, 별도 관리 계정 인증, DB 조회, Backend DB health 유지와 public internet 직접 노출이 없는 상태를 확인했습니다.

---

## 관련 설계 결정

아래 두 항목은 장애를 고친 기록보다 운영 조건이 바뀌면서 구조를 조정한 작업에 가깝습니다.

### 관광 데이터 Cache First에서 Live First로 전환

초기에는 TourAPI 호출량과 외부 장애를 고려해 관광 원본을 로컬 DB에 적재하고 증분 동기화하는 Cache First + Periodic Sync 구조를 구현했습니다.

공모전 운영 과정에서 한국관광공사 원본 관광데이터의 DB 저장에 별도 승인이 필요하다는 안내가 추가되면서, 최종 제출 서비스에서는 기존 저장 정책을 그대로 사용할 수 없게 됐습니다.

데이터를 다음처럼 다시 나눴습니다.

저장하는 데이터:

- Guest / Profile / Trip / Day / Meal Slot
- 사용자가 선택한 최소 KTO reference
- Nutrition reference

런타임에서 조회하고 영속하지 않는 데이터:

- TourAPI raw payload와 상세 원문
- 메뉴 원문 복제
- 추천 후보와 점수
- 이동 결과
- runtime match 결과

선택한 장소에는 provider + contentId + contentType을 보관하고 화면에 필요한 정보는 TourAPI에서 다시 조회하도록 바꿨습니다.

사용자 경로에서 로컬 관광 캐시를 읽지 않고, TourAPI 응답은 Cache-Control: no-store로 두며 PWA의 /api/** runtime cache에서도 제외했습니다. 기존 Sync/Admin 구현은 삭제하지 않고 Historical/Inactive로 격리했습니다.

기존 검증 기록에서는 사용자 추천/Planner의 관광 캐시 read 0, KTO raw/detail payload write 0, PWA 동적 API cache 0과 제주/경주 Production Smoke를 확인했습니다.

---

### Monorepo에서 Frontend와 Backend 배포 경계를 분리

Frontend는 Vercel, Backend는 별도 서버/AWS로 배포되면서 한 Monorepo 안의 디렉터리 구조와 실제 배포 생명주기가 달라졌습니다.

분리 전에는 Backend가 상위 .env.local이나 repository 바깥 Nutrition fixture에 의존하는 부분이 있어 backend 디렉터리만 복사하면 fresh clone에서 깨질 수 있었습니다.

Root Dependency Audit으로 남길 파일과 옮길 파일을 구분하고 `git subtree split`을 사용해 Backend history를 보존했습니다. 새 Backend repository를 fresh clone한 뒤 상위 경로 의존을 제거하고 테스트 fixture도 Backend repository 안으로 옮겼습니다.

기존 기록의 독립 검증 결과:

- JDK 21 / Maven Wrapper
- `clean verify` 83 tests PASS
- Testcontainers MySQL PASS
- Fresh Flyway migration PASS
- packaged JAR 독립 기동
- `/actuator/health = UP`
- Frontend Unit 20 tests / Production build / E2E 20 tests PASS
- Front/Back 양쪽의 filesystem source path 의존 없음
- Force push/history rewrite 없이 기존 history 보존
