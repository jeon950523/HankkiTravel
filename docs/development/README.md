# 로컬 개발 및 검증

포트폴리오 README와 분리한 개발자용 실행·검증 메모입니다.

## 사전 준비

- Frontend: Node.js 22.12 이상
- Backend: Java 21, MySQL
- MySQL 통합 테스트: Docker Desktop 또는 Docker Engine

루트의 `.env.example`을 참고해 필요한 환경 변수를 로컬에만 설정합니다. 실제 API 키, 비밀번호, 토큰은 커밋하지 않습니다.

## 실행

### Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

기본 API 주소는 `http://localhost:8300`입니다.

### Frontend

```powershell
cd frontend
npm ci
npm run dev
```

기본 웹 주소는 `http://localhost:5173`입니다.

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

백엔드의 MySQL 통합 테스트는 Testcontainers를 사용하므로 Docker가 실행 중이어야 합니다.
