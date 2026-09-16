import { describe, expect, it } from 'vitest'
import { burdenLabel, dayBurdenSummary, movementEvidence, perspectiveCandidates } from './routeAwareAttraction'

describe('route-aware attraction presentation', () => {
  it('switches nearby and signature candidates without discarding a duplicate candidate', () => {
    const shared = { contentId: '1' }
    const result = { perspectives: [
      { perspective: 'NEARBY_COURSE', candidates: [shared] },
      { perspective: 'SIGNATURE_COURSE', candidates: [{ ...shared, perspective: 'SIGNATURE_COURSE' }] },
    ] }
    expect(perspectiveCandidates(result, 'NEARBY_COURSE')).toHaveLength(1)
    expect(perspectiveCandidates(result, 'SIGNATURE_COURSE')).toEqual([{ contentId: '1', perspective: 'SIGNATURE_COURSE' }])
  })

  it('distinguishes actual transit evidence from car straight-line reference', () => {
    expect(movementEvidence({ transitSummary: { durationMinutes: 18.2, transferCount: 1, explicitWalkingDistanceMeters: 320 } }))
      .toBe('대중교통 약 18분 · 환승 1회 · 명시 도보 320m')
    expect(movementEvidence({ distanceMeters: 4200 })).toContain('직선거리 약 4.2km', '실제 도로 이동시간이 아님')
  })

  it('renders movement caution and cumulative day burden', () => {
    expect(burdenLabel('HIGH')).toBe('높음')
    expect(dayBurdenSummary({ state: 'EVALUATED', selectedPlaceCount: 3, transitMinutes: 95, transferCount: 2, explicitWalkingDistanceMeters: 700 }))
      .toContain('대중교통 95분', '명시 도보 700m')
  })
})
