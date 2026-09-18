import { describe, expect, it } from 'vitest'
import { decisionState, evaluateDayCompletion, resolveNextDecision } from './progressivePlanner'

const day = { mealSlots: [{ mealType: 'LUNCH' }, { mealType: 'DINNER' }] }

describe('progressive planner decisions', () => {
  it('prioritizes pending required meals over optional decisions', () => {
    expect(resolveNextDecision({ day, plannerItems: [] })).toBe('DAY_FOCUS')
    const items = [{ slotType: 'DAY_FOCUS' }, { slotType: 'LUNCH' }]
    expect(resolveNextDecision({ day, plannerItems: items })).toBe('DINNER')
  })

  it('requires stay only on lodging days and then completes the day', () => {
    const meals = [{ slotType: 'DAY_FOCUS' }, { slotType: 'LUNCH' }, { slotType: 'DINNER' }]
    expect(resolveNextDecision({ day, plannerItems: meals, stayRequired: true })).toBe('STAY')
    expect(resolveNextDecision({ day, plannerItems: [...meals, { slotType: 'STAY' }], stayRequired: true })).toBe('DAY_COMPLETE')
  })

  it('does not let unselected dessert or attraction block completion', () => {
    const items = [{ slotType: 'DAY_FOCUS' }, { slotType: 'LUNCH' }, { slotType: 'DINNER' }]
    expect(evaluateDayCompletion({ day, plannerItems: items })).toMatchObject({ isRequiredComplete: true, completedMealSlots: 2 })
  })

  it('reports complete, skipped and not-required without persistence', () => {
    expect(decisionState('LUNCH', [{ slotType: 'LUNCH' }])).toBe('COMPLETE')
    expect(decisionState('POST_LUNCH_DESSERT', [], ['POST_LUNCH_DESSERT'])).toBe('SKIPPED')
    expect(decisionState('STAY', [], [], false)).toBe('NOT_REQUIRED')
  })
})
