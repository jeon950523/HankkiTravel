# API 개요

API의 상세 계약은 백엔드 소스와 테스트를 기준으로 합니다. 아래는 공개 포트폴리오에서 현재 사용자 경로를 이해하기 위한 핵심 경계입니다. 실제 운영 자격증명·관리자 주소·키 값은 공개하지 않습니다.

## 사용자 기능

| 범주 | 역할 |
| --- | --- |
| 여행·프로필 | 가족 구성원, 여행 조건, 식사 슬롯, 일정 상태를 다룹니다. |
| 관광·음식점 추천 | TourAPI Live 응답과 Nutrition reference를 조합해 관광·음식점 후보와 추천 근거를 제공합니다. |
| 지도·동선 | 좌표와 이동 근거를 이용해 일자별 계획과 이동 부담을 표시합니다. |

## TourAPI Live 경계

| 메서드 | 경로 | 용도 |
| --- | --- | --- |
| `GET` | `/api/tourism/live/restaurants` | 현재 지역의 음식점 후보를 TourAPI에서 요청 시점에 조회 |
| `GET` | `/api/tourism/live/restaurants/{contentId}/decision-data` | 특정 음식점의 현재 상세정보와 Nutrition reference 기반 판단 데이터 조회 |

Live 응답은 `Cache-Control: no-store`를 사용하며 사용자 경로에서 관광 원본 캐시를 최신 정보의 대체 수단으로 사용하지 않습니다.

## Historical / Inactive Sync 경계

초기 Cache First 구조에서 사용하던 관리자 동기화 API는 코드에 남아 있지만 현재 Production 사용자 추천·Planner의 데이터 기준이 아닙니다.

| 메서드 | 경로 | 용도 |
| --- | --- | --- |
| `GET` | `/api/admin/tourism-sync/status` | 비활성 Sync/Admin 상태 확인 |
| `POST` | `/api/admin/tourism-sync/runs` | 승인된 운영자만 과거 동기화 경로를 수동 실행 |

`ADMIN_SYNC_ENABLED=false`가 기본값입니다. 이 경로를 활성화하더라도 현재 사용자 추천이 로컬 관광 캐시를 읽도록 바뀌는 것은 아닙니다.

관리자 자격정보는 서버 환경에서만 관리하며 프런트엔드 코드나 공개 URL에 포함하지 않습니다.
