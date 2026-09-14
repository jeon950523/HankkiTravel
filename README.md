# 한끼여행 Backend

한끼여행의 Spring Boot backend 저장소입니다. 프로젝트 제품 규칙의 단일 정본은 별도로 유지되는 Frontend / Authority 저장소의 [`docs/authority/current`](https://github.com/jeon950523/hankki-travel/tree/main/docs/authority/current)입니다. 이 저장소에는 Authority 정본을 복제하지 않습니다.

## 실행 환경

- Java 21
- Maven Wrapper
- 애플리케이션 포트: `8300`
- Production backend domain: `https://api.hankki.kro.kr`
- Production frontend origin (CORS): `https://hankki.kro.kr`

`WEB_BASE_URL`로 허용 origin을 설정합니다. 개발 기본값은 `http://localhost:5175`이며 Production에서는 반드시 `https://hankki.kro.kr`로 설정합니다.

## 로컬 실행

1. `.env.example`을 복사해 `.env.local`을 만들고, 필요한 값만 환경에 맞게 채웁니다. 실제 secret은 Git에 commit하지 않습니다.
2. Java 21을 선택합니다.
3. 아래 명령으로 실행합니다.

```powershell
.\mvnw.cmd spring-boot:run
```

정상 기동 후 health endpoint는 `http://localhost:8300/actuator/health`입니다.

## 검증

```powershell
.\mvnw.cmd -B -ntp clean verify
```

이 검증은 unit, architecture, Flyway migration 및 Testcontainers MySQL 기반 통합 테스트를 포함합니다.

## 환경변수

필요한 이름은 `.env.example`에만 정의합니다. 대표적으로 DB 연결 값, `DATA_GO_KR_SERVICE_KEY`, `KAKAO_REST_API_KEY`, `WEB_BASE_URL`을 사용합니다. Nutrition import 전용 실행에는 `NUTRITION_SOURCE_PATH`가 필요합니다.

Submission Production은 TourAPI Live First를 사용하며 KTO 관광 원본 payload를 persistent cache로 저장하지 않습니다. live 응답은 `no-store`이며 사용자 표기는 `출처: ⓒ한국관광공사`를 사용합니다.

## 배포 경계

- Backend repository: `https://github.com/jeon950523/hankki_travel_back`
- Branch: `main`
- Internal application port: `8300`
- External backend domain: `api.hankki.kro.kr`
- Allowed frontend origin: `https://hankki.kro.kr`

Jenkins, Argo, Kubernetes Ingress/TLS, DNS, Production DB credential은 제공된 인프라 템플릿과 운영 입력이 필요합니다. 현재 이 저장소에는 해당 템플릿을 추정해 추가하지 않습니다.
