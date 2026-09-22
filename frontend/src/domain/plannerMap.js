const finite = value => Number.isFinite(Number(value))

export function plannerItemId(item) {
  return `${item.slotType}:${item.contentId}`
}

export function buildPlannerDisplayItems(items = []) {
  const seenContent = new Set()
  return items.reduce((result, item) => {
    if (!item?.contentId || seenContent.has(item.contentId)) return result
    seenContent.add(item.contentId)
    const longitude = item.coordinates?.longitude
    const latitude = item.coordinates?.latitude
    result.push({
      ...item,
      plannerItemId: plannerItemId(item),
      displayIndex: result.length + 1,
      mapPoint: item.dataAvailability === 'CURRENT_DATA' && finite(longitude) && finite(latitude)
        ? { longitude: Number(longitude), latitude: Number(latitude) }
        : null,
    })
    return result
  }, [])
}

export function mapViewport(items = []) {
  const points = items.map(item => item.mapPoint).filter(Boolean)
  if (!points.length) return { mode: 'EMPTY', points }
  if (points.length === 1) return { mode: 'SINGLE', points, center: points[0] }
  return { mode: 'BOUNDS', points }
}

export function activatePlannerItem(state, plannerItemId) {
  return { ...state, activePlannerItemId: plannerItemId }
}

export function switchPlannerDay(state, dayNumber, items = []) {
  return { ...state, dayNumber, items: buildPlannerDisplayItems(items), activePlannerItemId: '' }
}

export function plannerLegId(leg, index) {
  return `${leg.fromSlotType}->${leg.toSlotType}:${index}`
}

export function buildPlannerLegs(items = [], legs = []) {
  return legs.map((leg, index) => ({
    ...leg,
    plannerLegId: plannerLegId(leg, index),
    displayFrom: index + 1,
    displayTo: index + 2,
    fromPoint: items[index]?.mapPoint || null,
    toPoint: items[index + 1]?.mapPoint || null,
    renderReferenceLine: leg.mode === 'CAR' && leg.dataAvailability === 'STRAIGHT_LINE_REFERENCE'
      && Boolean(items[index]?.mapPoint && items[index + 1]?.mapPoint),
  }))
}

export const carRouteNotice = transportMode => transportMode === 'CAR'
  ? '자동차 이동시간은 현재 제공하지 않아요. 장소 위치와 공개 주차정보를 참고하고, 상세 경로는 지도에서 확인해 주세요.'
  : ''
