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
    expect(transitSummary({ dataAvailability: 'UNAVAILABLE' })).toContain('현재 확인하지 못했어요')
    const label = transitSummary({ dataAvailability: 'CURRENT_DATA', durationMinutes: 23.4, transferCount: 1, explicitWalkingDistanceMeters: 120, unaccountedDistanceMeters: 9999 })
    expect(label).toBe('대중교통 약 23분 · 환승 1회 · 명시 도보 120m')
    expect(label).not.toContain('9999')
    const car = transitSummary({ mode: 'CAR', dataAvailability: 'STRAIGHT_LINE_REFERENCE', straightDistanceMeters: 4200 })
    expect(car).toContain('직선거리 약 4.2km')
    expect(car).toContain('실제 도로 경로')
    expect(car).not.toMatch(/예상 \d+분/)
  })
})
