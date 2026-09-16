import { describe, expect, it } from 'vitest'
import { addDays, buildTripPayload, durationDays, showStayForDay, transitSummary, validateTripDraft } from './trip'

const draft = (overrides = {}) => ({
  profileId: '7', regionKey: 'JEJU', startDate: '2026-09-16', endDate: '2026-09-16',
  days: [{ mealTypes: ['LUNCH'] }, { mealTypes: [] }, { mealTypes: [] }, { mealTypes: [] }], ...overrides,
})

describe('Trip Wizard validation', () => {
  it('requires at least one meal slot', () => expect(validateTripDraft(draft({ days: Array.from({ length: 4 }, () => ({ mealTypes: [] })) }))).toContain('하나 이상'))
  it('rejects more than four days', () => expect(validateTripDraft(draft({ endDate: addDays('2026-09-16', 4) }))).toContain('최대 3박 4일'))
  it('builds only days inside the selected period', () => expect(buildTripPayload(draft()).days).toEqual([{ dayNumber: 1, mealTypes: ['LUNCH'] }]))
  it('calculates inclusive duration and stay visibility', () => {
    expect(durationDays('2026-09-16', '2026-09-17')).toBe(2)
    expect(showStayForDay(1, 2)).toBe(true)
    expect(showStayForDay(2, 2)).toBe(false)
  })
  it('degrades unavailable transit and never labels unaccounted distance as walking', () => {
    expect(transitSummary({ dataAvailability: 'UNAVAILABLE' })).toContain('현재 확인이 어려워요')
    const label = transitSummary({ dataAvailability: 'CURRENT_DATA', durationMinutes: 23.4, transferCount: 1, explicitWalkingDistanceMeters: 120, unaccountedDistanceMeters: 9999 })
    expect(label).toBe('대중교통 예상 23분 · 환승 1회 · 명시 도보 120m')
    expect(label).not.toContain('9999')
  })
})
