const ORDER = ['DAY_FOCUS', 'BREAKFAST', 'MORNING_ACTIVITY', 'LUNCH', 'POST_LUNCH_DESSERT', 'AFTERNOON_ACTIVITY', 'DINNER', 'POST_DINNER_DESSERT', 'STAY']

export function resolveNextDecision({ day, plannerItems = [], stayRequired = false, skipped = [] }) {
  const complete = new Set(plannerItems.map(item => item.slotType))
  const skippedSet = new Set(skipped)
  const mealTypes = new Set((day?.mealSlots || []).map(slot => slot.mealType))
  const required = ORDER.filter(slot => {
    if (['BREAKFAST', 'LUNCH', 'DINNER'].includes(slot)) return mealTypes.has(slot)
    if (slot === 'MORNING_ACTIVITY') return mealTypes.has('BREAKFAST')
    if (slot === 'POST_LUNCH_DESSERT') return mealTypes.has('LUNCH') && complete.has('LUNCH')
    if (slot === 'AFTERNOON_ACTIVITY') return mealTypes.has('LUNCH') || mealTypes.has('DINNER')
    if (slot === 'POST_DINNER_DESSERT') return mealTypes.has('DINNER') && complete.has('DINNER')
    if (slot === 'STAY') return stayRequired
    return true
  })
  return required.find(slot => !complete.has(slot) && !skippedSet.has(slot)) || 'PLANNER'
}

export function decisionState(slotType, plannerItems = [], skipped = [], stayRequired = true) {
  if (slotType === 'STAY' && !stayRequired) return 'NOT_REQUIRED'
  if (skipped.includes(slotType)) return 'SKIPPED'
  return plannerItems.some(item => item.slotType === slotType) ? 'COMPLETE' : 'PENDING'
}
