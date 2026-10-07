# 한끼여행 Backend

이 디렉터리는 한끼여행 Spring Boot Backend의 포트폴리오 통합본입니다. 실제 Backend 개발·배포 원본은 `jeon950523/hankki_travel_back`의 `main`을 기준으로 합니다.

제품 규칙과 사용자 흐름은 Frontend / Authority 저장소의 `docs/authority/current`를 기준으로 관리하며, 이 통합본에서 별도 정본을 새로 만들지 않습니다.

## 실행 환경

- Java 21
- Maven Wrapper
- 애플리케이션 포트: `8300`
- Production Backend: `https://api.hankki.r-e.kr`
- Production Frontend origin: `https://hankki.kro.kr`

`WEB_BASE_URL`로 허용 origin을 설정합니다. 개발 기본값은 `http://localhost:5175`입니다.

## 로컬 실행

1. `.env.example`을 참고해 필요한 환경 변수를 로컬에 설정합니다.
2. Java 21을 사용합니다.
3. 아래 명령으로 실행합니다.

```powershell
.\mvnw.cmd spring-boot:run
```

기본 health endpoint는 `http://localhost:8300/actuator/health`입니다.

## 검증

```powershell
.\mvnw.cmd -B -ntp clean verify
```

unit, architecture, Flyway migration, Testcontainers MySQL 기반 통합 테스트를 포함합니다.

## 사용자 데이터 경로

현재 Production 사용자 경로는 TourAPI Live First를 사용합니다.

- TourAPI 목록·상세는 요청 시점에 조회
- Live 응답은 `Cache-Control: no-store`
- 사용자 추천·Planner에서 KTO 관광 원본 캐시를 현재 정보의 기준으로 사용하지 않음
- 사용자/Trip/선택 장소의 최소 provider reference와 Nutrition reference는 MySQL에 영속
- 추천 후보·점수·route 결과는 런타임 계산

과거 Cache First용 Sync/Admin 코드는 Historical/Inactive 경로로 남아 있으며 `ADMIN_SYNC_ENABLED=false`가 기본값입니다.

## 환경 변수

필요한 변수 이름은 `.env.example`에 정의합니다. 대표적으로 DB 연결 값, `DATA_GO_KR_SERVICE_KEY`, `KAKAO_REST_API_KEY`, `WEB_BASE_URL`을 사용합니다. 실제 Secret은 Git에 커밋하지 않습니다.

## 현재 Production 배포

Backend의 현재 운영 경로는 AWS EC2입니다.

```text
main push
→ GitHub Actions clean verify
→ GHCR full SHA image
→ AWS OIDC
→ SSM
→ EC2 Docker Compose backend 교체
→ /actuator/health 확인
```

EC2 배포 스크립트는 새 이미지의 health가 `UP`이 되지 않으면 이전 이미지로 rollback하도록 구성되어 있습니다.

CI에는 private `k8s-manifests-hankki`의 Backend image tag를 기록하는 단계도 남아 있습니다. 이 단계는 과거 Kubernetes/GitOps 경로의 추적을 위한 것이며 현재 Production runtime이 Kubernetes/Argo CD라는 뜻은 아닙니다.

Frontend Production은 Vercel에서 운영합니다.
