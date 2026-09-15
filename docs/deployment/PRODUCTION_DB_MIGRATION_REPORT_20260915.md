# 운영 DB 마이그레이션 결과 보고서

- 작업 일시: 2026-09-15
- 대상: 한끼여행 백엔드 운영 MySQL
- 결과: 완료

## 수행 내역

1. 애플리케이션 기본 스키마인 `hankki_planb`를 UTF-8(`utf8mb4`) 설정으로 생성했습니다.
2. Flyway SQL 마이그레이션 V1부터 V10까지 총 10건을 순서대로 적용했습니다.
3. 적용 직후 `flyway:validate`와 `flyway:info`를 실행해 스키마 이력과 파일 무결성을 확인했습니다.
4. GitHub `production` 환경을 생성하고, 수동 DB 마이그레이션 워크플로에 필요한 DB 환경 비밀값 5개를 등록했습니다. 값 자체는 이 문서와 저장소에 기록하지 않습니다.
5. GitHub Actions에서 수동 운영 DB 마이그레이션을 실행해 성공을 확인했습니다.

## 검증 결과

| 항목 | 결과 |
| --- | --- |
| 현재 Flyway 스키마 버전 | V10 |
| 적용된 마이그레이션 | V1~V10, 총 10건 |
| Flyway 무결성 검증 | 성공 |
| GitHub Actions 운영 DB 마이그레이션 | 성공 |

- [GitHub Actions 실행 결과](https://github.com/jeon950523/hankki_travel_back/actions/runs/34923910404)

## 워크플로 개선

빈 스키마에서는 적용 전 `flyway:validate`가 미적용 마이그레이션을 오류로 처리하는 것을 확인했습니다. 이에 운영 워크플로의 순서를 다음과 같이 수정했습니다.

```text
기존: 현황 확인·검증 → 마이그레이션
변경: 마이그레이션 → 검증·현황 확인
```

- 관련 커밋: `07a4a7d fix: 빈 운영 DB 마이그레이션 순서 수정`

## 보류 항목

ArgoCD ApplicationSet은 요청에 따라 아직 생성하거나 적용하지 않았습니다.
