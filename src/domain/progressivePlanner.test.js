import { describe, expect, it } from 'vitest'
import { decisionState, resolveNextDecision } from './progressivePlanner'

const day = { mealSlots: [{ mealType: 'LUNCH' }, { mealType: 'DINNER' }] }

describe('progressive planner decisions', () => {
  it('moves through incomplete decisions and honors dessert skip', () => {
    expect(resolveNextDecision({ day, plannerItems: [], stayRequired: true })).toBe('DAY_FOCUS')
    const items = [{ slotType: 'DAY_FOCUS' }, { slotType: 'LUNCH' }]
    expect(resolveNextDecision({ day, plannerItems: items, stayRequired: true })).toBe('POST_LUNCH_DESSERT')
    expect(resolveNextDecision({ day, plannerItems: items, stayRequired: true, skipped: ['POST_LUNCH_DESSERT'] })).toBe('AFTERNOON_ACTIVITY')
  })

  it('reports complete, skipped and not-required without persistence', () => {
    expect(decisionState('LUNCH', [{ slotType: 'LUNCH' }])).toBe('COMPLETE')
    expect(decisionState('POST_LUNCH_DESSERT', [], ['POST_LUNCH_DESSERT'])).toBe('SKIPPED')
    expect(decisionState('STAY', [], [], false)).toBe('NOT_REQUIRED')
  })
})
