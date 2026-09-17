const transitFor = candidate => candidate?.transitSummary || candidate?.transportEvidence

const distanceLabel = meters => meters < 1000
  ? `${Math.round(meters)}m`
  : `${(meters / 1000).toFixed(1)}km`

export function restaurantMovementEvidence(candidate, transportMode) {
  const transit = transitFor(candidate)
  const duration = transit?.durationMinutes ?? transit?.totalTimeMinutes
  if (transportMode === 'PUBLIC_TRANSIT') {
    if (duration != null) {
      return {
        primary: `중심 장소에서 대중교통 약 ${Math.round(duration)}분 · 환승 ${transit.transferCount ?? 0}회`,
        secondary: `명시 도보 ${transit.explicitWalkingDistanceMeters ?? 0}m`,
      }
    }
    return {
      primary: '대중교통 경로를 현재 확인하지 못했어요.',
      secondary: '카카오맵에서 자세히 확인해 주세요.',
    }
  }

  const meters = candidate?.distanceMeters ?? (candidate?.distanceFromAnchorKm == null ? null : Number(candidate.distanceFromAnchorKm) * 1000)
  return meters == null
    ? { primary: '중심 장소와의 직선거리를 현재 확인하지 못했어요.', secondary: '실제 도로 이동시간은 지도에서 확인해 주세요.' }
    : { primary: `중심 장소에서 직선거리 약 ${distanceLabel(Number(meters))}`, secondary: '실제 도로 이동시간은 지도에서 확인해 주세요.' }
}

export const telephoneHref = value => `tel:${String(value || '').replace(/[^0-9+]/g, '')}`

export function kakaoSearchUrl(candidate) {
  const query = [candidate?.title, candidate?.address].filter(Boolean).join(' ').trim()
  return `https://map.kakao.com/link/search/${encodeURIComponent(query)}`
}

export function restaurantExternalActions(candidate) {
  const actions = []
  if (candidate?.phone) actions.push({ kind: 'phone', label: '전화하기', href: telephoneHref(candidate.phone) })
  if (candidate?.placeUrl) actions.push({ kind: 'strict-map', label: '지도·후기 보기', href: candidate.placeUrl })
  else actions.push({ kind: 'search', label: '카카오맵에서 검색', href: kakaoSearchUrl(candidate) })
  return actions
}
