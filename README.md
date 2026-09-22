# 한끼여행 (HankkiTravel)

> 관광데이터를 바탕으로 가족의 식사 조건과 이동 부담을 함께 고려하는 여행·식당 플래너

[서비스 데모](https://hankki.kro.kr/) · [아키텍처](docs/architecture/README.md) · [API](docs/api/README.md) · [운영 구조](docs/infra/README.md)

## 문제 정의

가족 여행에서 식사는 단순한 "맛집 검색" 문제가 아닙니다. 연령대, 알레르기·식단 조건, 동행 인원, 식사 시간, 이동 거리, 관광 일정이 동시에 맞아야 합니다. 한끼여행은 이 조건들을 여행 프로필과 일정으로 연결해, 가족이 실제로 선택 가능한 식당과 관광 동선을 제안합니다.

## 핵심 기능

- 관광공사 TourAPI 기반으로 제주·서귀포·경주의 관광지·숙박·음식점 데이터를 동기화하고, 비정상 스냅샷을 방어합니다.
- 가족 구성원별 식사 조건과 여행 선호를 반영해 식당 후보와 추천 근거를 제공합니다.
- 여행 일정 중 이동 부담을 계산해 무리한 동선을 경고하고, 단계적으로 플랜을 구성합니다.
- Kakao Map으로 일자별 방문 후보와 경로를 시각화합니다.
- 관리자 관광데이터 동기화는 기본 비활성화하고, 단일 실행·호출 예산·이상 스냅샷 방어를 둡니다.

## 주요 화면

테스트용 여행·장소 데이터로 구성한 포트폴리오 캡처입니다. 실제 사용자 정보와 운영 비밀값은 포함하지 않습니다.

### 지도 기반 일정 플래너

![제주 여행의 지도와 방문 순서를 함께 보여주는 일정 플래너](docs/screenshots/map-planner-desktop.png)

### 식당 추천 및 선택 흐름

![가족 조건과 이동 부담을 바탕으로 식당을 추천하고 선택하는 화면](docs/screenshots/restaurant-recommendation-desktop.png)

### 완성된 여행 플랜

![선택한 관광지와 식당, 이동 부담을 정리한 여행 플랜 화면](docs/screenshots/final-plan-desktop.png)

## 서비스 구조

```text
사용자 조건·여행 일정
        ↓
Vue 3 / Vite Frontend ── Kakao Map
        ↓
Spring Boot Backend
   ├── TourAPI / 관광데이터
   ├── 추천·일정·이동 부담 로직
   └── MySQL
        ↓
운영 배포: Vercel / AWS EC2
운영 자동화: GitHub Actions → GHCR → AWS
관측: Prometheus / Grafana
```

자세한 모듈 경계와 데이터 흐름은 [아키텍처 문서](docs/architecture/README.md)에 정리했습니다.

## 기술 스택

| 영역 | 구성 |
| --- | --- |
| Frontend | Vue 3, Vite, Pinia, Vue Router, Vitest, Playwright |
| Backend | Java 21, Spring Boot, Spring Security, MyBatis, Flyway |
| 데이터 | MySQL, 한국관광공사 TourAPI, 식품영양 데이터 |
| 지도·교통 | Kakao Map, Kakao Mobility/대중교통 연동 |
| 배포·운영 | Vercel, AWS EC2, GitHub Actions, GHCR, Prometheus, Grafana |

## 저장소 구성

```text
HankkiTravel/
├── frontend/             # Vue/Vite 웹 애플리케이션
├── backend/              # Spring Boot API 및 데이터 동기화
├── docs/
│   ├── architecture/     # 시스템·모듈 경계
│   ├── api/              # 공개·운영 API 개요
│   ├── infra/            # 배포·관측·보안 경계
│   └── screenshots/      # 포트폴리오용 캡처 안내
└── .env.example          # 값 없는 환경 변수 템플릿
```

이 저장소는 포트폴리오용 통합본입니다. 운영 중인 프론트·백엔드 원본 저장소와 Vercel/AWS 배포 연결은 수정하지 않았습니다.

## 로컬 실행

### 1. 환경 변수 준비

루트의 `.env.example`을 참고해 필요한 값을 로컬 환경에만 설정합니다. 실제 API 키·비밀번호·토큰은 커밋하지 말고 Vercel, AWS, GitHub Actions 등의 비밀 저장소에서 관리합니다.

필수 비밀값은 다음과 같습니다.

- `DATA_GO_KR_SERVICE_KEY`
- `KAKAO_REST_API_KEY`
- `VITE_KAKAO_JAVASCRIPT_KEY`
- `DB_USERNAME`, `DB_PASSWORD`
- 운영 시 `ADMIN_SYNC_USERNAME`, `ADMIN_SYNC_PASSWORD`

### 2. Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Java 21과 MySQL이 필요합니다. 로컬 기본 웹 주소는 `http://localhost:5173`입니다.

### 3. Frontend

```powershell
cd frontend
npm ci
npm run dev
```

Node.js 22.12 이상을 사용합니다. 개발 프록시 기준 API 주소는 `http://localhost:8300`입니다.

## 검증

```powershell
# Frontend
cd frontend
npm test
npm run build

# Backend
cd ..\backend
.\mvnw.cmd verify
```

백엔드의 MySQL 통합 테스트는 Testcontainers를 사용하므로 Docker Desktop(또는 Docker Engine)이 실행 중이어야 합니다.

## 보안·공개 범위

- 실제 `.env`, 키 파일, 인증서, 운영 자격증명은 Git 추적 대상에서 제외합니다.
- 예시 파일에는 값이 없는 변수 이름만 제공합니다.
- 브라우저에 노출 가능한 Kakao JavaScript 키와 서버 전용 REST 키를 분리합니다.
- 운영용 관리자 동기화는 기본 비활성화하며, 운영 자격증명은 프런트엔드에 전달하지 않습니다.
- 배포용 Secret, AWS 자격증명, GHCR 토큰, Grafana 관리자 비밀번호는 이 저장소에 포함하지 않습니다.

## 이력 통합 방식

프론트엔드와 백엔드의 기존 Git 이력을 각각 보존한 뒤 `frontend/`, `backend/` 경로로 병합했습니다. 이 포트폴리오 저장소의 루트 구조를 만들기 위해 경로가 재작성된 커밋은 있지만, 각 코드 변경의 기존 커밋 메시지와 선후 관계는 유지됩니다.
