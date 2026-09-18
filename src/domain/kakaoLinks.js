const pointFor = place => place?.coordinates || place?.mapPoint
const finite = value => value !== null && value !== '' && Number.isFinite(Number(value))
const titleFor = place => String(place?.title || '장소').trim()

const routePoint = place => {
  const point = pointFor(place)
  const latitude = point?.latitude
  const longitude = point?.longitude
  if (!finite(latitude) || !finite(longitude)) return null
  return `${encodeURIComponent(titleFor(place))},${Number(latitude)},${Number(longitude)}`
}

export function buildKakaoPlaceSearchUrl(place) {
  const query = [place?.title, place?.address].filter(Boolean).join(' ').trim()
  return `https://map.kakao.com/link/search/${encodeURIComponent(query || '장소')}`
}

export function buildKakaoPlaceUrl(place) {
  const point = routePoint(place)
  return point
    ? `https://map.kakao.com/link/map/${point}`
    : buildKakaoPlaceSearchUrl(place)
}

export function buildKakaoDirectionsUrl({ start, end, transportMode }) {
  const startPoint = routePoint(start)
  const endPoint = routePoint(end)
  if (!startPoint || !endPoint) return null
  const mode = transportMode === 'PUBLIC_TRANSIT' ? 'traffic' : 'car'
  return `https://map.kakao.com/link/by/${mode}/${startPoint}/${endPoint}`
}

export function kakaoDirectionsAction({ start, end, transportMode }) {
  const href = buildKakaoDirectionsUrl({ start, end, transportMode })
  if (href) return {
    kind: 'directions',
    label: transportMode === 'PUBLIC_TRANSIT' ? '카카오맵에서 경로 보기' : '카카오맵에서 실제 경로 보기',
    href,
    precise: true,
  }
  return {
    kind: 'place-fallback',
    label: pointFor(end) ? '카카오맵에서 장소 보기' : '카카오맵에서 검색',
    href: buildKakaoPlaceUrl(end),
    precise: false,
  }
}
