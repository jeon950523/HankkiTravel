import { describe, expect, it } from 'vitest'
import { recommendationContextCopy } from './recommendationContext'

describe('추천 기준 위치 사용자 문구', () => {
  it.each([
    ['DAY_FOCUS', '오늘 여행의 시작 장소'],
    ['LUNCH', '점심 식당'],
    ['AFTERNOON_ACTIVITY', '오후 관광'],
    ['DINNER', '저녁 식당'],
  ])('%s를 사용자 라벨로 표시한다', (slotType, label) => {
    const copy = recommendationContextCopy({ originSlotType: slotType, originTitle: '테스트 장소' })
    expect(copy).toContain(label)
    expect(copy).toContain('테스트 장소')
    expect(copy).not.toContain(slotType)
  })

  it('기준점이 없으면 지역 후보 안내를 표시한다', () => {
    expect(recommendationContextCopy(null)).toContain('지역 내 후보')
  })
})
