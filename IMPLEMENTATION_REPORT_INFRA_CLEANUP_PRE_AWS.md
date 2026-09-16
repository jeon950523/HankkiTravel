# 한끼여행 INFRA CLEANUP Pre-AWS 결과 보고서

- 작업일: 2026-09-17
- 대상 저장소: `jeon950523/hankki_travel_back`
- 시작 HEAD: `9498ded49e407fa5d2c2b070acf8d1e2beeadb68`
- 브랜치: `main`
- 목적: Backend 내부 레거시 K8s/Argo CD 복사본을 제거하고 현재 배포 정본을 명확히 한다.

## Inventory

### ACTIVE

| 경로/대상 | 역할 | 판정 근거 |
|---|---|---|
| `.github/workflows/backend-ci-ghcr.yml` | Backend 검증, GHCR 발행, 별도 GitOps image 갱신 | `k8s-manifests-hankki/backend/deployment.yaml`을 갱신하는 현재 CI |
| `.github/workflows/production-db-migrate.yml` | 운영 DB 수동 Flyway migration | 수동 확인 입력과 GitHub production secret을 사용하는 현재 workflow |
| `Dockerfile` | Backend 운영 이미지 빌드 | 실제 Docker build 성공 |
| `README.md` | Backend 현재 실행·배포 경계 | 현재 도메인과 SSOT로 갱신 |
| `docs/deployment/README.md` | 현재 운영 배포 문서 | 분리 GitOps 구조와 CI 흐름으로 갱신 |
| `jeon950523/k8s-manifests-hankki/backend/*` | 강사 환경 K8s manifest 정본 | 최신 `origin/main`에 deployment/service/configmap/httproute/secret 존재 |
| Backend 저장소 밖 `argocd-appset-hankki.yaml` | 강사 전달용 ApplicationSet | source repo가 `k8s-manifests-hankki`, path가 root child directory |

### LEGACY

| 경로/표현 | 판정 | 처리 |
|---|---|---|
| `deploy/k8s/configmap.yaml` | 별도 GitOps 정본과 내용 불일치 | 제거 |
| `deploy/k8s/deployment.yaml` | `main` image tag를 사용하며 최신 SHA 정본과 불일치 | 제거 |
| `deploy/k8s/ingress.example.yaml` | 현재 Gateway HTTPRoute 정본과 불일치 | 제거 |
| `deploy/k8s/kustomization.yaml` | 강사 가이드의 plain YAML 정본에는 없음 | 제거 |
| `deploy/k8s/secret.example.yaml` | Backend 내부 레거시 manifest 복사본 | 제거 |
| `deploy/k8s/service.yaml` | 별도 GitOps 정본과 내용 불일치 | 제거 |
| `deploy/argocd/hankki-backend-applicationset.yaml` | Backend repo와 `deploy/k8s`를 바라보는 구 ApplicationSet | 제거 |
| workflow `paths-ignore: deploy/**` | 삭제된 Backend 경로용 재귀 실행 방지 규칙 | 제거 |
| `api.hankki.kro.kr` | 현재 HTTPRoute와 다른 구 도메인 | 현재 README에서 `api.hankki.r-e.kr`로 교정 |
| Jenkins 관련 현재 운영 문구 | 현재 GitHub Actions 전제와 불일치 | 현재 README에서 제거 |

### HISTORICAL_DOC

다음 문서는 과거 시점의 구현·검증 증거이므로 당시 경로, 도메인, 테스트 수를 소급 수정하지 않고 보존했다.

- `docs/deployment/IMPLEMENTATION_REPORT_BIG_INFRA_02_BACKEND_ARGO_DEPLOYMENT.md`
- `docs/deployment/PRODUCTION_DB_MIGRATION_REPORT_20260915.md`
- `IMPLEMENTATION_REPORT_BIG_INFRA_*.md` 패턴에 해당하는 기존 보고서
- 기존 BIG 03A/03B/03D/03D1 구현 보고서

### UNKNOWN

- Backend 저장소 내부의 영향 불명확한 배포 파일: 없음
- AWS infra SSOT: `TBD`

AWS 정본은 아직 결정되지 않았으며 이번 작업에서 새 저장소, Terraform, CloudFormation 또는 AWS 리소스를 만들지 않았다.

## Removed

삭제한 Backend tracked path는 총 7개 파일이다.

```text
deploy/argocd/hankki-backend-applicationset.yaml
deploy/k8s/configmap.yaml
deploy/k8s/deployment.yaml
deploy/k8s/ingress.example.yaml
deploy/k8s/kustomization.yaml
deploy/k8s/secret.example.yaml
deploy/k8s/service.yaml
```

삭제 조건 확인 결과:

1. 최신 K8s manifest가 별도 GitOps `origin/main/backend`에 존재한다.
2. 현재 Backend CI는 Backend의 `deploy/k8s`를 갱신하지 않는다.
3. 현재 전달용 ApplicationSet은 별도 GitOps repo를 source로 사용한다.
4. Backend 내부 구 ApplicationSet은 현재 전달물이 아니다.
5. AWS infra SSOT는 `TBD`이며 이번 AWS 준비 작업은 구 manifest를 재사용하지 않는다.

## Retained

- Backend test/verify, GHCR publish, GitOps image update workflow
- Production DB migration workflow
- Dockerfile
- 기능 코드 전체
- Flyway V1~V13 전체
- 과거 구현·배포·마이그레이션 보고서 전체
- 별도 GitOps 최신 `origin/main/backend` 구조
- Backend 저장소 밖 강사 전달용 ApplicationSet

## Current SSOT

```text
Backend source: jeon950523/hankki_travel_back
K8s GitOps:     jeon950523/k8s-manifests-hankki/backend
AWS infra:      TBD
```

검증 시점 GitOps 최신 원격 구조:

```text
backend/configmap.yaml
backend/deployment.yaml
backend/httproute.yaml
backend/secret.yaml
backend/service.yaml
```

GitOps root는 `backend` 하나이며 `backend/kustomization.yaml`은 없다. 최신 deployment image는 Backend HEAD에 대응하는 full SHA tag를 사용하고, HTTPRoute host는 `api.hankki.r-e.kr`, Service port는 `8300`이다.

로컬 GitOps checkout은 최신 `origin/main`보다 4 commits 뒤였으므로 파일을 pull하거나 수정하지 않고 최신 `origin/main`을 직접 감사 기준으로 사용했다.

## CI

활성 workflow:

- `Backend CI and GHCR`
  - Maven `clean verify`
  - GHCR `sha-<full-sha>` 및 `main` tag 발행
  - `jeon950523/k8s-manifests-hankki` checkout
  - `backend/deployment.yaml` image 한 줄 갱신
  - 변경 시 GitOps `main` commit/push
- `Production DB Migration`
  - `MIGRATE_PRODUCTION` 수동 확인
  - `flyway:migrate`
  - `flyway:validate flyway:info`

활성 workflow의 구 경로 참조:

```text
deploy/k8s   0
deploy/argocd 0
```

현재 운영 README 두 곳의 구 경로·구 도메인·Jenkins 참조도 0이다. 역사 보고서에 남은 과거 경로는 이력 보존 대상이다.

## Security

Backend tracked working tree와 이번 diff에서 다음 형태를 검사했다.

```text
OpenSSH/RSA/EC private key  0
AWS access key pattern      0
GitHub PAT pattern          0
JWT literal pattern         0
장문 secret literal pattern 0
```

`.env.local`은 tracked file이 아니다. 실제 비밀번호, PAT, API key, DB password, SSH private key는 출력하거나 새로 커밋하지 않았다.

별도 Private GitOps 정본에는 기존 `backend/secret.yaml`이 tracked되어 있고 런타임 key 7개가 선언돼 있다. 값은 출력하거나 수정하지 않았다. 이번 Backend cleanup의 신규 노출은 0이지만, AWS 전환 시에는 현재 비밀값 관리 방식을 AWS Secrets Manager, External Secrets 또는 이에 준하는 방식으로 교체할지 별도 결정해야 한다.

## Validation

| 검증 | 결과 |
|---|---|
| Maven wrapper `clean verify` | PASS |
| Maven tests | 133 run, 0 failures, 0 errors, 0 skipped |
| Flyway V1~V13 validation | PASS |
| 실행 JAR package | PASS |
| Docker build | PASS (`hankki-travel-api:pre-aws-cleanup`) |
| Workflow current GitOps target | PASS |
| Active old-path reference | 0 |
| `git diff --check` | PASS |
| Feature code changes | 0 |
| Flyway changes | 0 |
| Frontend changes | 0 |
| GitOps repo changes | 0 |
| AWS changes | 0 |
| 신규 secret exposure | 0 |

Maven 실행 중 종료된 Testcontainers DB connection을 Hikari가 재검사하는 경고가 있었지만 테스트 결과와 패키징은 모두 성공했다.

## Exit Criteria

```text
Legacy deploy inventory             PASS
Backend deploy/k8s legacy copy      REMOVED
Backend deploy/argocd legacy copy   REMOVED
Current GitOps SSOT                 CLEAR
Backend CI old-path reference       0
Current docs stale-path reference   0
Historical reports preserved       PASS
Flyway changes                      0
Feature code changes                0
Secret exposure introduced          0
Backend regression                  PASS
Docker build                        PASS
AWS accidental mutation             0
```