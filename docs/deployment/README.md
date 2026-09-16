# 한끼여행 백엔드 운영 배포

이 저장소는 Backend 소스, Flyway, Dockerfile, Backend CI와 Production DB migration workflow의 정본이다. Kubernetes manifest와 Argo CD source는 별도 Private GitOps 저장소에서 관리한다. 실제 비밀값·PAT·개인키는 이 Backend 저장소에 기록하지 않는다.

## 현재 배포 정본

```text
Backend main push
  → GitHub Actions JDK 21 검증
  → GHCR ghcr.io/jeon950523/hankki_travel_back:sha-<full-sha> 발행
  → jeon950523/k8s-manifests-hankki checkout
  → backend/deployment.yaml의 image 한 줄 갱신
  → Argo CD가 별도 GitOps 저장소를 동기화
  → namespace hankki의 hankki-travel-api 배포
```

- Backend source SSOT: `jeon950523/hankki_travel_back`
- K8s GitOps SSOT: `jeon950523/k8s-manifests-hankki/backend`
- Argo CD ApplicationSet: Backend 저장소 밖의 강사 전달물
- AWS infra SSOT: `TBD`

Backend 저장소 내부에는 K8s 또는 Argo CD manifest 복사본을 두지 않는다.

## Kubernetes 계약

- Namespace: `hankki`
- Deployment / Service: `hankki-travel-api`
- Service type / port / targetPort: `ClusterIP` / `8300` / `8300`
- Backend host: `api.hankki.r-e.kr`
- Frontend origin: `https://hankki.kro.kr`
- Routing: 강사 관리 Gateway에 연결되는 GitOps `backend/httproute.yaml`
- Image: `ghcr.io/jeon950523/hankki_travel_back:sha-<full-sha>`

Kubernetes 런타임 Secret 계약은 `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DATA_GO_KR_SERVICE_KEY`, `KAKAO_REST_API_KEY`다. 값은 이 Backend 저장소에 기록하지 않는다.

## CI와 GHCR

`.github/workflows/backend-ci-ghcr.yml`은 다음 순서로 동작한다.

1. Maven `clean verify`
2. 검증된 Backend 이미지를 GHCR에 `sha-<full-sha>`와 `main` 태그로 발행
3. `K8S_MANIFESTS_TOKEN`으로 별도 GitOps 저장소 checkout
4. `backend/deployment.yaml`에서 정확히 한 개의 Backend image 줄을 새 SHA 태그로 교체
5. 변경이 있을 때만 GitOps `main`에 커밋·푸시

## 운영 DB 수동 마이그레이션

GitHub `production` Environment의 `Production DB Migration` 워크플로는 `MIGRATE_PRODUCTION` 확인 입력이 있어야 실행된다. 실행 순서는 `flyway:migrate` 후 `flyway:validate flyway:info`다. 실제 DB 자격증명은 GitHub Environment secret으로만 주입한다.

## 경계

AWS 신규 리소스, Terraform, CloudFormation, ECS, EKS, EC2, ALB, Route53, ACM은 이 운영 문서에서 생성하거나 소유하지 않는다. AWS 인프라 정본이 결정되기 전까지 `AWS_INFRA_SSOT = TBD`로 유지한다.