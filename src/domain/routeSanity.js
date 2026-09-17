const LABELS = { NORMAL: '보통', CAUTION: '살펴보기', HIGH: '높음', NOT_EVALUATED: '미평가' }
const ACTION_LABELS = { NEAR_RESTAURANT: '가까운 식당 다시 보기', NEAR_ATTRACTION: '가까운 관광지 다시 보기', NEAR_STAY: '가까운 숙소 다시 보기', KEEP_ITINERARY: '그래도 이 일정 유지' }
export const routeSanityLabel = severity => LABELS[severity] || LABELS.NOT_EVALUATED
export const routeSanityActionLabel = action => ACTION_LABELS[action] || action
export const routeSanityEvaluated = sanity => Boolean(sanity && sanity.state !== 'NOT_EVALUATED')
export function routeSanitySummary(sanity) {
  if (!routeSanityEvaluated(sanity)) return '현재 확인 가능한 이동 근거가 부족해요.'
  if (sanity.severity === 'HIGH') return '확인 가능한 이동 구간 중 긴 이동이 있어요.'
  if (sanity.severity === 'CAUTION') return '이동 부담이 다소 큰 구간을 확인해 주세요.'
  return '확인 가능한 이동 구간 기준으로 무리가 크지 않아요.'
}
export const problemLegId = (sanity, legs = []) => {
  const index = sanity?.longestLeg?.legIndex
  if (!Number.isInteger(index) || !legs[index] || !['CAUTION', 'HIGH'].includes(sanity?.severity)) return ''
  return legs[index].plannerLegId
}
export const routeSanityActions = sanity => sanity?.severity === 'NORMAL' || sanity?.state === 'NOT_EVALUATED' ? [] : (sanity?.suggestedActions || [])
export const slotHasRouteWarning = (slotType, legs = []) => legs.some(leg => leg.toSlotType === slotType && ['CAUTION', 'HIGH'].includes(leg.burdenSeverity))
export const reentryTarget = (action, longestLeg) => {
  if (action === 'NEAR_RESTAURANT') return { type: 'RESTAURANT', slotType: longestLeg?.toSlotType }
  if (action === 'NEAR_ATTRACTION') return { type: 'ATTRACTION', slotType: longestLeg?.toSlotType }
  if (action === 'NEAR_STAY') return { type: 'STAY', slotType: 'STAY' }
  return { type: 'KEEP', slotType: null }
}
