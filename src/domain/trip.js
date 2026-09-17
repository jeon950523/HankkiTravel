export const MEAL_TYPES = ['BREAKFAST', 'LUNCH', 'DINNER']
export const MEAL_LABELS = { BREAKFAST: '아침', LUNCH: '점심', DINNER: '저녁' }
export const SLOT_LABELS = {
  DAY_FOCUS: '오늘의 중심 장소', BREAKFAST: '아침', MORNING_ACTIVITY: '오전 관광', LUNCH: '점심',
  AFTERNOON_ACTIVITY: '오후 관광', DINNER: '저녁', POST_MEAL_DESSERT: '식후 디저트', POST_LUNCH_DESSERT: '점심 후 디저트', POST_DINNER_DESSERT: '저녁 후 디저트', STAY: '숙소',
}

export function addDays(date, amount) {
  const result = new Date(`${date}T12:00:00`)
  result.setDate(result.getDate() + amount)
  return result.toISOString().slice(0, 10)
}

export function durationDays(startDate, endDate) {
  if (!startDate || !endDate) return 0
  return Math.round((new Date(`${endDate}T12:00:00`) - new Date(`${startDate}T12:00:00`)) / 86400000) + 1
}

export function validateTripDraft(draft) {
  if (!Number(draft.profileId)) return '가족 프로필을 골라주세요.'
  if (!['JEJU', 'GYEONGJU'].includes(draft.regionKey)) return '여행 지역을 골라주세요.'
  const duration = durationDays(draft.startDate, draft.endDate)
  if (duration < 1 || duration > 4) return '여행 기간은 최대 3박 4일까지 선택할 수 있어요.'
  const selected = draft.days.slice(0, duration).reduce((count, day) => count + day.mealTypes.length, 0)
  if (selected < 1) return '여행 전체에서 식사 시간을 하나 이상 골라주세요.'
  if (selected > 12) return '식사 시간은 최대 12개까지 선택할 수 있어요.'
  return ''
}

export function buildTripPayload(draft) {
  const error = validateTripDraft(draft)
  if (error) throw new Error(error)
  const duration = durationDays(draft.startDate, draft.endDate)
  return {
    profileId: Number(draft.profileId), regionKey: draft.regionKey,
    startDate: draft.startDate, endDate: draft.endDate,
    days: draft.days.slice(0, duration).map((day, index) => ({ dayNumber: index + 1, mealTypes: [...day.mealTypes] })),
  }
}

export const showStayForDay = (dayNumber, duration) => duration > 1 && dayNumber < duration
export const activitySlotForMeal = mealType => mealType === 'BREAKFAST' ? 'MORNING_ACTIVITY' : 'AFTERNOON_ACTIVITY'
export const dessertSlotForMeal = mealType => mealType === 'LUNCH' ? 'POST_LUNCH_DESSERT' : 'POST_DINNER_DESSERT'
export const transitSummary = leg => {
  if (leg?.mode === 'CAR' && leg?.dataAvailability === 'STRAIGHT_LINE_REFERENCE') {
    const distance = leg.straightDistanceMeters < 1000 ? `${leg.straightDistanceMeters}m` : `${(leg.straightDistanceMeters / 1000).toFixed(1)}km`
    return `직선거리 약 ${distance} · 실제 도로 경로와 소요시간은 카카오맵에서 확인해 주세요.`
  }
  return leg?.dataAvailability === 'CURRENT_DATA'
    ? `대중교통 약 ${Math.round(leg.durationMinutes)}분 · 환승 ${leg.transferCount}회 · 명시 도보 ${leg.explicitWalkingDistanceMeters}m`
    : '대중교통 경로를 현재 확인하지 못했어요. 카카오맵에서 다시 확인해 주세요.'
}
