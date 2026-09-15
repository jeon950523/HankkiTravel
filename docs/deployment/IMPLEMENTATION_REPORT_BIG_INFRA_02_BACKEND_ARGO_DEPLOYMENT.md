# 한끼여행 BIG-INFRA-02 Backend Argo Deployment 결과 보고서

- 작업 일시: 2026-09-15
- 작업 시작 HEAD: `3a5219f3136ac4a68b091e2ac3b7b523f9f379bd`
- 작업 브랜치: `main`
- 범위: 백엔드 컨테이너, GHCR GitOps, Kubernetes 배포 전달물, ArgoCD ApplicationSet

## 결과 요약

| 영역 | 상태 | 근거 |
| --- | --- | --- |
| Backend test | PASS | JDK 21, 86 tests, failures 0, errors 0 |
| Docker build | PASS | `hankki-travel-api:infra-02` 로컬 빌드 성공 |
| Local runtime health | PASS | 임시 MySQL에서 Flyway 후 health/readiness/liveness 모두 `UP` |
| GHCR / CI | PREPARED | 현재 변경 푸시 후 GitHub Actions 검증 대기 |
| Kubernetes manifests | PASS | `kubectl kustomize deploy/k8s` 및 계약 검사 성공 |
| Production DB contract | PASS | `hankki_planb`, Flyway V10 success 확인 |
| ApplicationSet | READY | 전달 파일 생성, 실제 적용은 Argo 선행 조건 대기 |
| Argo Sync / Health | WAITING_ARGO | 저장소 credential·대상 cluster server 미확정 |
| External HTTPS health | WAITING_TLS | 강사 Ingress/TLS·DNS 작업 대기 |
| Nutrition reference | IMPORT_REQUIRED | active rows 0, import runs 0 |
| Secret audit | PASS | 실제 비밀값·개인키·PAT 미검출 |

## 배포 구성

- Docker: Java 21 multi-stage build, 고정된 Spring Boot JAR 선택, non-root `spring`(UID 10001), exec-form entrypoint, port 8300
- GitHub Actions: main 검증 후 GHCR에 full SHA tag를 발행하고 `deploy/k8s/kustomization.yaml`만 갱신
- GitOps loop: `deploy/**`는 push trigger에서 제외되어 image tag self-commit이 CI를 재귀 호출하지 않음
- Kubernetes: `hankki` namespace, replica 1, ClusterIP 8300→8300, ConfigMap·Secret 참조, startup/readiness/liveness probe, graceful termination 30초
- CORS: `WEB_BASE_URL=https://hankki.kro.kr`이며 wildcard를 사용하지 않음
- Ingress: `api.hankki.re.kr` 예시만 제공하고 실제 Ingress/TLS는 강사 소유

## Runtime Secret Contract

Kubernetes runtime Secret은 아래 애플리케이션 환경변수 계약과 일치한다.

- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- `DATA_GO_KR_SERVICE_KEY`, `KAKAO_REST_API_KEY`
- 선택 항목: `ADMIN_SYNC_USERNAME`, `ADMIN_SYNC_PASSWORD`

운영 DB 이름은 `hankki_planb`로 고정했다. 실제 secret 값, PAT, Argo password, webhook secret, SSH private key는 Git tracked file에 기록하지 않았다.

## ArgoCD 상태

- 전달 파일: `deploy/argocd/hankki-backend-applicationset.yaml`
- source repository: `git@github.com:jeon950523/hankki_travel_back.git`
- source revision/path: `main` / `deploy/k8s`
- destination namespace: `hankki`
- automated sync: `prune`, `selfHeal` enabled
- `CreateNamespace=true`: 사용하지 않음

한끼여행 백엔드 private repository의 읽기 전용 GitHub deploy key와 ArgoCD SSH repository credential을 등록했고, ArgoCD connection status가 `Successful`인 것을 확인했다. 다만 이 작업 계정으로 확인 가능한 대상 cluster server 값이 없으므로, ApplicationSet destination에는 `<INSTRUCTOR_CONFIRM_DESTINATION_SERVER>` placeholder를 남겼다. 따라서 ApplicationSet 파일은 전달 준비 상태이나 실제 적용·Sync·Health는 `WAITING_ARGO`다.

## DB / Flyway / Nutrition

운영 DB read-only 점검 결과 Flyway 최신 이력은 V10 success다. V1~V10은 수정하지 않았고 `clean`, `repair`, drop/recreate를 실행하지 않았다.

`nutrition_foods`의 active reference row는 0건이고 `nutrition_import_runs`도 0건이다. 기존 `nutrition-import` 프로필과 `NUTRITION_SOURCE_PATH` one-shot 경로를 문서화했으며, backend startup import는 추가하지 않았다.

## 남은 외부 작업

1. 강사가 실제 Argo destination server를 확인해 ApplicationSet placeholder를 교체한다.
2. 클러스터에 `hankki-travel-api-secrets`, `ghcr-pull-secret`을 실제 값으로 생성한다.
3. ApplicationSet 적용 후 Argo `Synced` / `Healthy`, Deployment AVAILABLE 1, Pod READY 1/1, Service endpoint를 확인한다.
4. 강사가 Ingress/TLS·DNS를 설정한 뒤 `https://api.hankki.re.kr/actuator/health`의 HTTP 200 / `UP`을 확인한다.
5. 운영 smoke 전 Nutrition one-shot import를 실행한다.


## 금지사항 준수

- 프런트엔드 배포 방식(Vercel)을 변경하지 않음
- Jenkins를 추가하지 않음
- 기능 코드를 수정하지 않음
- 운영 DB 이름을 변경하거나 재생성하지 않음
- 기존 Flyway V1~V10을 수정하지 않음
- TourAPI payload persistence를 추가하지 않음

## 검증 한계 / Known Issues

- 로컬 `kubectl` context가 설정돼 있지 않아 실제 cluster apply, Deployment AVAILABLE, Pod READY, Service endpoint는 검증할 수 없었다.
- 현재 GitHub 인증에는 `read:packages` scope가 없어 GHCR package visibility를 API로 확인하지 못했다. 백엔드 저장소가 private이므로 `ghcr-pull-secret` 참조는 유지했다.
- ArgoCD repository credential은 연결 성공 상태이나 destination server가 확정되지 않아 actual ApplicationSet apply는 수행하지 않았다.
