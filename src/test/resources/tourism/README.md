# 좌표 정밀도 회귀 자료

evidence-coordinates.csv는 사용자 제공 Authority Pack v0.11의 evidence/transit 아래 다음 4개 공개 TourAPI 샘플에서 mapx/mapy 문자열만 추출한 자료다.

- tourapi_gyeongju_attractions_sample.json
- tourapi_gyeongju_restaurants_sample.json
- tourapi_jeju_attractions_sample.json
- tourapi_jeju_restaurants_sample.json

파일명 순서, 각 원본 item 순서대로 400쌍(800개 값)을 보존했다. 문자열에서 직접 복사하여 부동소수점 변환을 하지 않았다. 최대 소수점 자릿수는 15다. DatabaseFoundationTest가 모든 값을 BigDecimal로 읽어 실제 MySQL insert/read 값과 비교한다. 전체 원본 응답은 저장소에 포함하지 않는다.
