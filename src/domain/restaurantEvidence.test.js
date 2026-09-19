import { describe, expect, it } from 'vitest'
import { coveragePresentation, finalPlanExternalActions, hasUnscoredDimensions, kakaoSearchUrl, restaurantExternalActions, restaurantMovementEvidence } from './restaurantEvidence'

describe('식당 외부 확인 CTA', () => {
  it('strict match가 없으면 Kakao link/search 계약과 URL 인코딩을 지킨다', () => {
    const url = kakaoSearchUrl({ title: '한끼 국수', address: '제주시 애월읍 1-1' })
    expect(url).toBe('https://map.kakao.com/link/search/%ED%95%9C%EB%81%BC%20%EA%B5%AD%EC%88%98%20%EC%A0%9C%EC%A3%BC%EC%8B%9C%20%EC%95%A0%EC%9B%94%EC%9D%8D%201-1')
    expect(url).not.toContain('?q=')
  })

  it.each([
    [{ phone: null, placeUrl: 'https://place.map.kakao.com/1' }, ['지도·후기 보기']],
    [{ phone: '064-123-4567', placeUrl: 'https://place.map.kakao.com/1' }, ['전화하기', '지도·후기 보기']],
    [{ phone: '064-123-4567', placeUrl: null, title: '식당', address: '제주시' }, ['전화하기', '카카오맵에서 검색']],
    [{ phone: null, placeUrl: null, title: '식당', address: '제주시' }, ['카카오맵에서 검색']],
  ])('전화와 strict match 조합에 맞는 CTA만 노출한다', (candidate, labels) => {
    expect(restaurantExternalActions(candidate).map(item => item.label)).toEqual(labels)
  })

  it('이전 장소와 현재 장소 좌표가 있으면 Final Plan 길찾기를 함께 제공한다', () => {
    const actions = finalPlanExternalActions({
      previous: { title: '출발', coordinates: { longitude: 126.5, latitude: 33.5 } },
      item: { phone: null, placeUrl: null, title: '식당', coordinates: { longitude: 126.53, latitude: 33.49 } },
      transportMode: 'CAR',
    })
    expect(actions.map(item => item.label)).toEqual(['카카오맵에서 보기', '길찾기'])
    expect(actions[1].href).toContain('/link/by/car/')
  })
})

describe('점수와 정보 근거 표현', () => {
  it('낮은 coverage는 점수보다 근거 부족을 먼저 설명한다', () => {
    expect(coveragePresentation(30)).toEqual({ label: '정보 근거 적음', level: 'LOW', notice: '확인된 정보가 아직 적어 참고용으로 봐주세요.' })
  })

  it('미평가 dimension을 점수 차감으로 오해하지 않게 감지한다', () => {
    expect(hasUnscoredDimensions({ evaluatedDimensions: [{ evaluated: false }] })).toBe(true)
  })
})

describe('식당 이동 근거', () => {
  it('자동차는 직선거리만 표시하고 주행시간을 만들지 않는다', () => {
    const evidence = restaurantMovementEvidence({ distanceMeters: 1800 }, 'CAR')
    expect(evidence.primary).toBe('중심 장소에서 직선거리 약 1.8km')
    expect(`${evidence.primary} ${evidence.secondary}`).not.toContain('분')
  })

  it('대중교통은 시간·환승·명시 도보만 표시한다', () => {
    const evidence = restaurantMovementEvidence({ transportEvidence: { totalTimeMinutes: 18, transferCount: 1, explicitWalkingDistanceMeters: 180, unaccountedDistanceMeters: 9999 } }, 'PUBLIC_TRANSIT')
    expect(evidence).toEqual({ primary: '중심 장소에서 대중교통 약 18분 · 환승 1회', secondary: '명시 도보 180m' })
  })

  it('대중교통 경로 없음은 이용 불가로 단정하지 않는다', () => {
    const evidence = restaurantMovementEvidence({}, 'PUBLIC_TRANSIT')
    expect(evidence.primary).toContain('현재 확인하지 못했어요')
    expect(evidence.primary).not.toContain('이용 불가')
  })
})
