export const ATTRACTION_PERSPECTIVES = [
  { value: 'NEARBY_COURSE', label: '가까운 코스', description: '현재 일정에서 이동 부담이 적은 장소를 우선했어요.' },
  { value: 'SIGNATURE_COURSE', label: '대표 명소 코스', description: '조금 더 이동하더라도 지역 대표성을 함께 고려했어요.' },
]

export function perspectiveCandidates(result, perspective) {
  const group = result?.perspectives?.find(item => item.perspective === perspective)
  return group?.candidates || result?.candidates || []
}

export function movementEvidence(candidate) {
  const transit = candidate?.transitSummary
  if (transit?.durationMinutes != null) {
    return `대중교통 약 ${Math.round(transit.durationMinutes)}분 · 환승 ${transit.transferCount}회 · 명시 도보 ${transit.explicitWalkingDistanceMeters}m`
  }
  if (candidate?.distanceMeters != null) {
    const distance = candidate.distanceMeters < 1000 ? `${candidate.distanceMeters}m` : `${(candidate.distanceMeters / 1000).toFixed(1)}km`
    return `직선거리 약 ${distance} · 실제 도로 이동시간이 아님`
  }
  return '이동 근거 미평가 · 중심 장소나 식사 장소를 먼저 선택해 주세요.'
}

export const burdenLabel = value => ({ LOW: '낮음', MODERATE: '보통', HIGH: '높음', NOT_EVALUATED: '미평가' }[value] || '미평가')

export function dayBurdenSummary(dayBurden) {
  if (!dayBurden || dayBurden.state === 'NOT_EVALUATED') return null
  return `선택 장소 ${dayBurden.selectedPlaceCount}곳 · 대중교통 ${Math.round(dayBurden.transitMinutes)}분 · 환승 ${dayBurden.transferCount}회 · 명시 도보 ${dayBurden.explicitWalkingDistanceMeters}m`
}
