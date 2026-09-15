# 한끼여행 배포 기반

이 문서는 GitHub Actions, GHCR, Kubernetes, Argo CD, 운영 DB 마이그레이션의 연결 계약을 설명한다. 비밀값·개인키·PAT를 이 저장소에 넣지 않는다.

## 구성 결과

- 프런트 저장소의 `.github/workflows/frontend-ci.yml`은 Node 22.12, 단위 테스트, production build, Playwright E2E를 검증한다.
- 백엔드의 `backend-ci-ghcr.yml`은 Java 21 검증 후 `ghcr.io/jeon950523/hankki_travel_back`에 `sha-<commit>` 및 `main` 이미지를 발행한다.
- 같은 워크플로는 검증·이미지 발행 성공 후 `k8s/base/kustomization.yaml`의 이미지 태그만 커밋 SHA로 갱신한다. Argo CD는 이 Git 변경을 감지해 배포한다.
- `production-db-migrate.yml`은 자동 실행되지 않으며, GitHub `production` 환경 승인과 `MIGRATE_PRODUCTION` 확인 입력이 모두 있을 때 Flyway를 실행한다.

## GitHub 준비

1. 백엔드 저장소의 Actions 기본 권한에서 `GITHUB_TOKEN`에 packages write와 contents write를 허용한다. 워크플로도 최소 권한을 명시한다.
2. GitHub `production` Environment를 만들고 승인자를 설정한다.
3. 이 Environment에 다음 secrets를 등록한다. 값은 대화나 Git에 기록하지 않는다.

   - `PROD_DB_HOST`
   - `PROD_DB_PORT`
   - `PROD_DB_NAME`
   - `PROD_DB_USERNAME`
   - `PROD_DB_PASSWORD`

4. GHCR 패키지는 클러스터가 pull할 수 있어야 한다. 패키지를 public으로 만들거나, 아래와 같이 클러스터 관리자가 별도 image pull secret을 만든다. 실제 PAT는 명령 기록에 남기지 않는다.

   ```sh
   kubectl -n hankki create secret docker-registry ghcr-pull-secret \
     --docker-server=ghcr.io \
     --docker-username=GITHUB_USER \
     --docker-password=GITHUB_PAT
   ```

   private GHCR를 사용할 때는 `k8s/base/deployment.yaml`의 Pod spec에 `imagePullSecrets`로 `ghcr-pull-secret`을 추가한다. public 패키지는 추가 설정 없이 pull할 수 있다.

## Kubernetes와 Argo CD

- Namespace는 `hankki`다.
- 적용 전 `k8s/base/secrets.example.yaml`을 저장소 밖에 복사해 애플리케이션 Secret을 생성한다. 이 Secret이 없으면 의도적으로 Pod가 시작되지 않는다.
- Ingress host는 현재 Authority 정본의 `api.hankki.kro.kr`다. Ingress class와 TLS는 강사가 도메인·인증서를 준비한 뒤 클러스터 표준에 맞춰 추가한다.
- Argo CD에는 GitHub deploy key의 public key를 저장소 deploy key로 등록하고, private key는 Argo CD repository credential에만 등록한다. 키는 Git에 넣지 않는다.
- ApplicationSet CRD가 있는 클러스터 관리자가 `argocd/applicationset.yaml`을 적용한다. ApplicationSet은 `main`의 `k8s/base`를 `hankki` namespace에 자동 동기화한다.

## 운영 DB 실행

Actions 탭에서 `Production DB Migration`을 수동 실행하고 confirmation에 정확히 `MIGRATE_PRODUCTION`을 입력한다. 이 작업은 `info`, `validate`, `migrate` 순으로 실행하며 Flyway의 clean은 비활성 상태다.

## 강사 전달 사항

- Namespace: `hankki`
- Backend host: `api.hankki.kro.kr`
- Frontend host: `hankki.kro.kr`
- 전달 파일: `argocd/applicationset.yaml`
- 강사 측 필요 작업: ApplicationSet CRD 권한 확인, Argo CD repository credential 등록, Ingress class/TLS·DNS 적용