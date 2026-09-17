import { describe, expect, it } from 'vitest'
import { restaurantExternalActions, restaurantMovementEvidence } from './restaurantEvidence'

describe('식당 외부 확인 CTA', () => {
  it.each([
    [{ phone: null, placeUrl: 'https://place.map.kakao.com/1' }, ['지도·후기 보기']],
    [{ phone: '064-123-4567', placeUrl: 'https://place.map.kakao.com/1' }, ['전화하기', '지도·후기 보기']],
    [{ phone: '064-123-4567', placeUrl: null, title: '식당', address: '제주시' }, ['전화하기', '카카오맵에서 검색']],
    [{ phone: null, placeUrl: null, title: '식당', address: '제주시' }, ['카카오맵에서 검색']],
  ])('전화와 strict match 조합에 맞는 CTA만 노출한다', (candidate, labels) => {
    expect(restaurantExternalActions(candidate).map(item => item.label)).toEqual(labels)
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
