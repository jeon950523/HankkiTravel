import { buildKakaoDirectionsUrl, buildKakaoPlaceSearchUrl, kakaoPlaceAction } from './kakaoLinks'

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
  return buildKakaoPlaceSearchUrl(candidate)
}

export function coveragePresentation(value) {
  const coverage = Number(value)
  if (!Number.isFinite(coverage)) return { label: '정보 근거 미확인', level: 'UNKNOWN', notice: '' }
  if (coverage >= 70) return { label: '정보 근거 충분', level: 'SUFFICIENT', notice: '' }
  if (coverage >= 40) return { label: '정보 근거 일부', level: 'PARTIAL', notice: '확인 가능한 정보만으로 적합도를 계산했어요.' }
  return { label: '정보 근거 적음', level: 'LOW', notice: '확인된 정보가 아직 적어 참고용으로 봐주세요.' }
}

export const hasUnscoredDimensions = candidate => (candidate?.evaluatedDimensions || [])
  .some(item => item.evaluated === false || item.evidenceState === 'NOT_EVALUATED')

export function restaurantExternalActions(candidate) {
  const actions = []
  if (candidate?.phone) actions.push({ kind: 'phone', label: '전화하기', href: telephoneHref(candidate.phone) })
  actions.push(kakaoPlaceAction(candidate))
  return actions
}

export function finalPlanExternalActions({ item, previous, transportMode }) {
  const actions = restaurantExternalActions(item)
  const directions = previous ? buildKakaoDirectionsUrl({ start: previous, end: item, transportMode }) : null
  if (directions) actions.push({ kind: 'directions', label: '길찾기', href: directions })
  return actions
}
