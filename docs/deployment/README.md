# 한끼여행 백엔드 운영 배포

이 저장소는 GitHub Actions로 검증·GHCR 발행을 수행하고, `deploy/k8s`의 Git 변경을 Argo CD가 동기화하는 방식으로 운영한다. 비밀값·PAT·개인키는 Git에 기록하지 않는다.

## 배포 정본

```text
소스 main push
  → GitHub Actions JDK 21 검증
  → GHCR ghcr.io/jeon950523/hankki_travel_back:sha-<full-sha> 발행
  → deploy/k8s/kustomization.yaml 이미지 태그 self-commit
  → Argo CD Git 감지
  → namespace hankki의 hankki-travel-api 배포
```

`backend-ci-ghcr.yml`은 `deploy/**` 변경을 제외한다. 따라서 이미지 태그 self-commit은 검증·발행 작업을 다시 실행하지 않는다.

## Kubernetes 계약

- Namespace: `hankki` (이 저장소는 Namespace를 생성·소유하지 않음)
- Deployment / Service: `hankki-travel-api`
- Service type / port / targetPort: `ClusterIP` / `8300` / `8300`
- Backend host handoff: `api.hankki.re.kr`
- Frontend origin: `https://hankki.kro.kr`
- Ingress/TLS: 강사 관리 영역. `deploy/k8s/ingress.example.yaml`은 참고용이며 Kustomize 대상이 아님

`deploy/k8s/secret.example.yaml`을 저장소 밖으로 복사해 `hankki-travel-api-secrets`를 만든다. 실제 Secret에는 다음 런타임 환경변수가 필요하다.

- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- `DATA_GO_KR_SERVICE_KEY`, `KAKAO_REST_API_KEY`
- 필요 시 `ADMIN_SYNC_USERNAME`, `ADMIN_SYNC_PASSWORD`

운영 DB 이름은 반드시 `hankki_planb`다. 현재 제공된 root 계정은 마이그레이션 용도이며, 런타임에는 전용 최소 권한 DB 계정을 생성해 사용해야 한다.

Private GitHub 저장소이므로 GHCR package visibility 확인 전까지 `ghcr-pull-secret`을 필수 전제로 둔다. 클러스터 관리자는 실제 PAT를 기록하지 않도록 안전한 방식으로 아래 Secret을 만든다.

```sh
kubectl -n hankki create secret docker-registry ghcr-pull-secret \
  --docker-server=ghcr.io \
  --docker-username=GITHUB_USER \
  --docker-password=GITHUB_PAT
```

## Argo CD 전달 절차

전달 파일은 `deploy/argocd/hankki-backend-applicationset.yaml`이다.

1. Argo CD에 `git@github.com:jeon950523/hankki_travel_back.git` 저장소를 SSH repository credential로 등록한다.
2. deploy key의 private key는 Argo CD credential에만 저장하고, 이 저장소나 ApplicationSet에 넣지 않는다.
3. ApplicationSet의 `<INSTRUCTOR_CONFIRM_DESTINATION_SERVER>`를 강사가 확인한 실제 대상 클러스터 서버 값으로 교체한다.
4. ApplicationSet을 `argocd` namespace에 적용한다.
5. Application이 `Synced`, `Healthy`가 된 뒤 Deployment AVAILABLE 1, Pod READY 1/1, Service endpoint를 확인한다.

`CreateNamespace=true`는 사용하지 않는다. Namespace `hankki`는 강사 측에서 제공한다.

## DB와 Nutrition

운영 `hankki_planb`는 Flyway V10까지 적용·검증됐다. 운영 기동 시 Flyway는 schema history를 인식하며 pending migration은 없어야 한다. `clean`, `repair`, V1~V10 수정은 금지다.

Nutrition reference는 현재 0건이므로 다음 배포 smoke 전 one-shot import가 필요하다. 자동 기동 import는 하지 않는다. XLSX를 안전한 외부 경로로 준비하고 동일한 DB Secret을 주입한 one-off Job에서 아래 기존 프로필을 사용한다.

```sh
SPRING_PROFILES_ACTIVE=nutrition-import \
NUTRITION_SOURCE_PATH=/secure/path/20260828_음식DB_19617건.xlsx \
java -jar app.jar
```

## 운영 DB 수동 마이그레이션

GitHub `production` Environment의 `Production DB Migration` 워크플로는 `MIGRATE_PRODUCTION` 확인 입력이 있어야 실행된다. 빈 스키마와 기존 스키마 모두에서 안전하도록 실행 순서는 `flyway:migrate` 후 `flyway:validate flyway:info`다.
