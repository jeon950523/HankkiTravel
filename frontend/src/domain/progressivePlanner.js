const MEAL_ORDER = ['BREAKFAST', 'LUNCH', 'DINNER']

export function evaluateDayCompletion({ day, plannerItems = [], stayRequired = false }) {
  const complete = new Set(plannerItems.map(item => item.slotType))
  const requiredMealTypes = (day?.mealSlots || []).map(slot => slot.mealType)
  const completedMealSlots = requiredMealTypes.filter(type => complete.has(type) || day.mealSlots.find(slot => slot.mealType === type)?.anchor).length
  const stayCompleted = complete.has('STAY')
  return { requiredMealSlots: requiredMealTypes.length, completedMealSlots, stayRequired, stayCompleted,
    isRequiredComplete: completedMealSlots === requiredMealTypes.length && (!stayRequired || stayCompleted) }
}

export function resolveNextDecision({ day, plannerItems = [], stayRequired = false }) {
  const complete = new Set(plannerItems.map(item => item.slotType))
  if (!complete.has('DAY_FOCUS')) return 'DAY_FOCUS'
  const mealTypes = new Set((day?.mealSlots || []).map(slot => slot.mealType))
  const pendingMeal = MEAL_ORDER.find(slot => mealTypes.has(slot) && !complete.has(slot) && !day.mealSlots.find(item => item.mealType === slot)?.anchor)
  if (pendingMeal) return pendingMeal
  if (stayRequired && !complete.has('STAY')) return 'STAY'
  return 'DAY_COMPLETE'
}

export function decisionState(slotType, plannerItems = [], skipped = [], stayRequired = true) {
  if (slotType === 'STAY' && !stayRequired) return 'NOT_REQUIRED'
  if (skipped.includes(slotType)) return 'SKIPPED'
  return plannerItems.some(item => item.slotType === slotType) ? 'COMPLETE' : 'PENDING'
}
