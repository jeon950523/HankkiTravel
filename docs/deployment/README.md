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

4. 백엔드 저장소가 private이므로 GHCR image pull secret은 필수다. 클러스터 관리자가 아래와 같이 `ghcr-pull-secret`을 만들고, 실제 PAT는 명령 기록에 남기지 않는다.

   ```sh
   kubectl -n hankki create secret docker-registry ghcr-pull-secret \
     --docker-server=ghcr.io \
     --docker-username=GITHUB_USER \
     --docker-password=GITHUB_PAT
   ```

   `k8s/base/deployment.yaml`은 이 Secret을 `imagePullSecrets`로 참조한다. 이 Secret이 없으면 Pod는 의도적으로 image pull 단계에서 시작되지 않는다.

## Kubernetes와 Argo CD

- Namespace는 `hankki`다.
- 적용 전 `k8s/base/secrets.example.yaml`을 저장소 밖에 복사해 애플리케이션 Secret을 생성한다. 이 Secret이 없으면 의도적으로 Pod가 시작되지 않는다.
- Ingress host는 현재 Authority 정본의 `api.hankki.kro.kr`다. Ingress class와 TLS는 강사가 도메인·인증서를 준비한 뒤 클러스터 표준에 맞춰 추가한다.
- Argo CD에는 GitHub deploy key의 public key를 저장소 deploy key로 등록하고, private key는 Argo CD repository credential에만 등록한다. 키는 Git에 넣지 않는다.
- ApplicationSet CRD가 있는 클러스터 관리자가 `argocd/applicationset.yaml`을 적용한다. ApplicationSet은 `main`의 `k8s/base`를 `hankki` namespace에 자동 동기화한다.


## GitHub → Argo CD 웹훅

Argo CD 공개 endpoint에는 GitHub webhook을 한 개만 등록한다. URL은 `https://argocd.meerkat.p-e.kr/api/webhook`, Content type은 `application/json`이며 이벤트는 `Pushes`와 `Packages`를 선택한다.

등록 전에 클러스터 관리자가 `argocd` namespace의 `argocd-secret`에 `webhook.github.secret`을 설정해야 한다. 이 값은 GitHub webhook secret과 정확히 같아야 하며, 저장소나 ApplicationSet 파일에는 넣지 않는다. ApplicationSet과 GitHub webhook을 관리자가 반영하기 전에는 Argo CD의 3분 폴링만 동기화 경로로 사용된다.
## 운영 DB 실행

Actions 탭에서 `Production DB Migration`을 수동 실행하고 confirmation에 정확히 `MIGRATE_PRODUCTION`을 입력한다. 이 작업은 `info`, `validate`, `migrate` 순으로 실행하며 Flyway의 clean은 비활성 상태다.

## 강사 전달 사항

- Namespace: `hankki`
- Backend host: `api.hankki.kro.kr`
- Frontend host: `hankki.kro.kr`
- 전달 파일: `argocd/applicationset.yaml`
- 강사 측 필요 작업: ApplicationSet CRD 권한 확인, Argo CD repository credential 등록, Ingress class/TLS·DNS 적용