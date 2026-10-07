# 배포·운영 구조

## 현재 Production 경계

```text
Frontend repository
  → Vercel
  → https://hankki.kro.kr/

Backend repository
  → GitHub Actions verify
  → GHCR SHA image
  → AWS OIDC
  → SSM
  → EC2 /opt/hankki Docker Compose
  → backend health check

EC2
  ├─ Backend
  ├─ MySQL
  └─ Prometheus / Grafana monitoring
```

현재 사용자에게 제공되는 Backend Production runtime은 AWS EC2입니다. 이 통합 저장소는 공개 포트폴리오용 코드와 문서를 제공하며 실제 Vercel/AWS 연결 자체를 변경하지 않습니다.

## Backend 배포 판정

Backend workflow는 `main`의 검증이 끝난 뒤 source SHA에 대응하는 GHCR 이미지를 발행합니다. 이후 GitHub OIDC로 AWS 자격을 얻고 SSM으로 EC2에 배포합니다.

EC2에서는 `/opt/hankki/.env`의 `BACKEND_IMAGE`를 새 SHA 이미지로 바꾼 뒤 Docker Compose로 Backend 컨테이너만 교체합니다. 새 컨테이너가 `/actuator/health`에서 `UP`이 되지 않으면 이전 이미지로 rollback하도록 구성되어 있습니다.

SSM command 전송만으로 배포 성공을 판단하지 않고 command 결과와 Backend health까지 확인합니다.

## GitOps 기록과 현재 runtime 구분

Backend CI에는 private `k8s-manifests-hankki` 저장소의 image tag를 갱신하는 단계도 남아 있습니다. 이는 과거 Kubernetes/GitOps 경로의 추적용 기록이며, 현재 Production Backend가 Kubernetes/Argo CD에서 실행된다는 의미는 아닙니다.

현재 Production 배포 기준은 AWS SSM → EC2 Docker Compose 경로입니다.

## 비밀값 관리

| 보관 위치 | 대상 |
| --- | --- |
| Vercel 환경 변수 | Frontend API 주소, 브라우저 공개 범위의 Kakao JavaScript 키 |
| AWS/EC2 서버 환경 | DB 자격정보, TourAPI 서비스 키, Kakao REST 키, Backend runtime 환경값 |
| GitHub Actions Secrets / OIDC | GHCR 및 배포에 필요한 GitHub/AWS 연결 정보 |
| Grafana 설정 | 관리자 비밀번호와 데이터 소스 연결 정보 |

이 저장소에는 실제 비밀값을 기록하지 않습니다.

## 관광 데이터 운영 경계

현재 사용자 추천·Planner는 TourAPI Live First를 사용합니다. 과거 Cache First용 Sync/Admin 구현은 기본 비활성화되어 있으며 현재 사용자 경로의 데이터 정본이 아닙니다.

- Live TourAPI 응답은 `no-store`
- 사용자 추천에서 stale 관광 캐시를 최신 정보처럼 대체 표시하지 않음
- 추천 후보·route 결과를 관광 원본 DB에 영속하지 않음
- 기존 Sync/Admin은 `ADMIN_SYNC_ENABLED=false`가 기본값

## 관측

Prometheus와 Grafana 구성은 서비스 상태와 운영 지표를 확인하기 위한 용도로 유지합니다. 공개 저장소에는 운영 IP, 관리자 계정, 내부 대시보드 URL이나 자격정보를 기록하지 않습니다.
