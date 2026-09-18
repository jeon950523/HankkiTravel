import { describe, expect, it } from 'vitest'
import { buildKakaoDirectionsUrl, kakaoDirectionsAction } from './kakaoLinks'

const start = { title: '출발 장소', coordinates: { longitude: 126.5, latitude: 33.5 } }
const end = { title: '도착/식당', coordinates: { longitude: 126.53, latitude: 33.49 }, address: '제주시 현재로' }

describe('Kakao 길찾기 링크', () => {
  it.each([
    ['CAR', 'car'],
    ['PUBLIC_TRANSIT', 'traffic'],
  ])('%s는 공식 이동수단과 이름·위도·경도 순서를 사용한다', (transportMode, mode) => {
    const url = buildKakaoDirectionsUrl({ start, end, transportMode })
    expect(url).toBe(`https://map.kakao.com/link/by/${mode}/%EC%B6%9C%EB%B0%9C%20%EC%9E%A5%EC%86%8C,33.5,126.5/%EB%8F%84%EC%B0%A9%2F%EC%8B%9D%EB%8B%B9,33.49,126.53`)
  })

  it('좌표가 빠지면 가짜 route 대신 장소 검색으로 degrade한다', () => {
    const action = kakaoDirectionsAction({ start: { title: '출발' }, end: { title: '도착 식당', address: '제주시' }, transportMode: 'CAR' })
    expect(action.precise).toBe(false)
    expect(action.label).toBe('카카오맵에서 검색')
    expect(action.href).toBe('https://map.kakao.com/link/search/%EB%8F%84%EC%B0%A9%20%EC%8B%9D%EB%8B%B9%20%EC%A0%9C%EC%A3%BC%EC%8B%9C')
  })
})
