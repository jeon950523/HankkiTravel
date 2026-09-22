# 배포·운영 구조

## 운영 경계

```text
GitHub 원본 저장소
 ├─ Frontend → Vercel → https://hankki.kro.kr/
 └─ Backend  → GitHub Actions → GHCR → AWS EC2
                                      └→ MySQL

Prometheus → Grafana
```

현재 운영 배포는 원본 프론트·백엔드 저장소를 기준으로 유지됩니다. 이 통합 저장소는 포트폴리오용 코드와 문서를 제공할 뿐, Vercel·AWS·GHCR 배포 대상을 변경하지 않습니다.

## 비밀값 관리

| 보관 위치 | 대상 |
| --- | --- |
| Vercel 환경 변수 | 프런트 API 주소, 브라우저 공개 범위의 Kakao JavaScript 키 |
| AWS/서버 환경 변수 | DB 자격증명, TourAPI 서비스 키, Kakao REST 키, 관리자 인증 정보 |
| GitHub Actions Secrets | GHCR 토큰 및 배포 연결에 필요한 값 |
| Grafana 설정 | 관리자 비밀번호와 데이터 소스 연결 정보 |

이 저장소에는 위 값의 이름만 `.env.example`로 제공하며, 실제 값은 포함하지 않습니다.

## 관측과 안전한 운영

- Prometheus와 Grafana는 서비스 상태와 운영 지표를 관측하는 데 사용합니다.
- 관광데이터 동기화는 내부 호출 예산과 단일 실행 경계를 사용합니다.
- 비정상적으로 감소한 스냅샷은 기존 캐시를 덮어쓰지 않도록 방어합니다.
- 공개 저장소에는 운영 IP, 배포 토큰, 관리자 계정, 내부 대시보드 URL을 기록하지 않습니다.
