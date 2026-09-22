export const RECOMMENDATION_ORIGIN_LABELS = {
  DAY_FOCUS: '오늘 여행의 시작 장소',
  BREAKFAST: '아침 식당',
  MORNING_ACTIVITY: '오전 관광',
  LUNCH: '점심 식당',
  POST_LUNCH_DESSERT: '점심 후 디저트',
  AFTERNOON_ACTIVITY: '오후 관광',
  DINNER: '저녁 식당',
  POST_DINNER_DESSERT: '저녁 후 디저트',
  POST_MEAL_DESSERT: '식후 디저트',
  STAY: '숙소',
}

const ORIGIN_SELECTION_PHRASES = {
  DAY_FOCUS: '오늘 여행의 시작 장소로',
  BREAKFAST: '아침 식당으로',
  MORNING_ACTIVITY: '오전 관광으로',
  LUNCH: '점심 식당으로',
  POST_LUNCH_DESSERT: '점심 후 디저트로',
  AFTERNOON_ACTIVITY: '오후 관광으로',
  DINNER: '저녁 식당으로',
  POST_DINNER_DESSERT: '저녁 후 디저트로',
  POST_MEAL_DESSERT: '식후 디저트로',
  STAY: '숙소로',
}

export function recommendationContextCopy(context) {
  const label = RECOMMENDATION_ORIGIN_LABELS[context?.originSlotType]
  if (!label) return '현재 이동 기준점을 충분히 확인하지 못했어요. 지역 내 후보를 우선 보여드려요.'
  return context.originTitle
    ? `${ORIGIN_SELECTION_PHRASES[context.originSlotType]} 선택한 ${context.originTitle} 주변에서 찾았어요.`
    : `${label} 주변에서 찾았어요.`
}
