import { describe, expect, it } from 'vitest'
import { problemLegId, reentryTarget, routeSanityActions, routeSanityLabel, routeSanitySummary, slotHasRouteWarning } from './routeSanity'

const sanity = (severity, state = 'EVALUATED') => ({ state, severity, suggestedActions: ['NEAR_RESTAURANT', 'NEAR_ATTRACTION', 'NEAR_STAY', 'KEEP_ITINERARY'], longestLeg: { legIndex: 1, toSlotType: 'LUNCH' } })
const legs = [{ plannerLegId: 'a:0', toSlotType: 'MORNING_ACTIVITY', burdenSeverity: 'NORMAL' }, { plannerLegId: 'b:1', toSlotType: 'LUNCH', burdenSeverity: 'HIGH' }]

describe('global route sanity presentation', () => {
  it.each([['NORMAL', '보통'], ['CAUTION', '살펴보기'], ['HIGH', '높음'], ['NOT_EVALUATED', '미평가']])('renders %s severity with text', (severity, label) => {
    expect(routeSanityLabel(severity)).toBe(label)
    expect(routeSanitySummary(sanity(severity, severity === 'NOT_EVALUATED' ? 'NOT_EVALUATED' : 'EVALUATED'))).toBeTruthy()
  })
  it('focuses the same problem leg used by timeline and map', () => {
    expect(problemLegId(sanity('HIGH'), legs)).toBe('b:1')
    expect(slotHasRouteWarning('LUNCH', legs)).toBe(true)
    expect(slotHasRouteWarning('MORNING_ACTIVITY', legs)).toBe(false)
  })
  it('provides restaurant, attraction, stay and keep actions', () => {
    expect(routeSanityActions(sanity('CAUTION'))).toEqual(['NEAR_RESTAURANT', 'NEAR_ATTRACTION', 'NEAR_STAY', 'KEEP_ITINERARY'])
    expect(reentryTarget('NEAR_RESTAURANT', { toSlotType: 'LUNCH' })).toEqual({ type: 'RESTAURANT', slotType: 'LUNCH' })
    expect(reentryTarget('NEAR_ATTRACTION', { toSlotType: 'AFTERNOON_ACTIVITY' })).toEqual({ type: 'ATTRACTION', slotType: 'AFTERNOON_ACTIVITY' })
    expect(reentryTarget('NEAR_STAY')).toEqual({ type: 'STAY', slotType: 'STAY' })
    expect(reentryTarget('KEEP_ITINERARY')).toEqual({ type: 'KEEP', slotType: null })
  })
})
