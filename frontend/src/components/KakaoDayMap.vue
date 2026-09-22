<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { loadKakaoMapSdk } from '../services/kakaoMapLoader'
import { mapViewport } from '../domain/plannerMap'

const props = defineProps({
  items: { type: Array, default: () => [] },
  legs: { type: Array, default: () => [] },
  activeId: { type: String, default: '' },
  activeLegId: { type: String, default: '' },
})
const emit = defineEmits(['select', 'select-leg'])
const container = ref(null)
const status = ref('idle')
const activeLeg = computed(() => props.legs.find(leg => leg.plannerLegId === props.activeLegId))
let map
let overlays = []
let lineOverlays = []

function clearOverlays() {
  overlays.forEach(({ marker }) => marker.setMap(null))
  lineOverlays.forEach(({ line, hitArea }) => { line.setMap(null); hitArea.setMap(null) })
  overlays = []
  lineOverlays = []
}

function markerImage(maps, item, active = false) {
  const base = item.slotType === 'DAY_FOCUS' ? '#b85c39'
    : item.slotType === 'STAY' ? '#506b91'
      : item.slotType.includes('ACTIVITY') ? '#8a6b2f' : '#344c34'
  const color = active ? '#1f2e1f' : base
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="42" height="48" viewBox="0 0 42 48"><path fill="${color}" stroke="white" stroke-width="3" d="M21 1.5c10.8 0 19.5 8.7 19.5 19.5 0 13-19.5 25.5-19.5 25.5S1.5 34 1.5 21C1.5 10.2 10.2 1.5 21 1.5Z"/><text x="21" y="27" fill="white" text-anchor="middle" font-family="Arial,sans-serif" font-size="16" font-weight="700">${item.displayIndex}</text></svg>`
  const image = new maps.MarkerImage(`data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`, new maps.Size(42, 48), { offset: new maps.Point(21, 48) })
  image.hankkiActive = active
  return image
}

function styleActive() {
  overlays.forEach(({ id, marker, position, normalImage, activeImage }) => {
    marker.setImage(id === props.activeId ? activeImage : normalImage)
    marker.setZIndex(id === props.activeId ? 5 : 3)
    if (id === props.activeId && map) map.panTo(position)
  })
  lineOverlays.forEach(({ id, line, warning }) => line.setOptions({
    strokeWeight: id === props.activeLegId ? 7 : 4,
    strokeColor: id === props.activeLegId || warning ? '#b85c39' : '#506b91',
  }))
}

async function renderMap() {
  clearOverlays()
  const viewport = mapViewport(props.items)
  if (viewport.mode === 'EMPTY') { status.value = 'empty'; map = undefined; return }
  status.value = 'loading'
  try {
    const maps = await loadKakaoMapSdk()
    await nextTick()
    if (!container.value) return
    const first = viewport.points[0]
    map = new maps.Map(container.value, { center: new maps.LatLng(first.latitude, first.longitude), level: 6 })
    const bounds = viewport.mode === 'BOUNDS' ? new maps.LatLngBounds() : null
    for (const item of props.items.filter(value => value.mapPoint)) {
      const position = new maps.LatLng(item.mapPoint.latitude, item.mapPoint.longitude)
      const normalImage = markerImage(maps, item)
      const activeImage = markerImage(maps, item, true)
      const marker = new maps.Marker({ position, image: normalImage, title: `${item.displayIndex}번 ${item.title}`, zIndex: 3 })
      marker.setMap(map)
      maps.event.addListener(marker, 'click', () => emit('select', item.plannerItemId))
      overlays.push({ id: item.plannerItemId, marker, position, normalImage, activeImage })
      bounds?.extend(position)
    }
    for (const leg of props.legs.filter(value => value.renderReferenceLine)) {
      const path = [new maps.LatLng(leg.fromPoint.latitude, leg.fromPoint.longitude), new maps.LatLng(leg.toPoint.latitude, leg.toPoint.longitude)]
      const line = new maps.Polyline({ path, strokeWeight: 4, strokeColor: '#506b91', strokeOpacity: .85, strokeStyle: 'shortdash' })
      const hitArea = new maps.Polyline({ path, strokeWeight: 18, strokeColor: '#000000', strokeOpacity: .01 })
      line.setMap(map); hitArea.setMap(map)
      maps.event.addListener(line, 'click', () => emit('select-leg', leg.plannerLegId))
      maps.event.addListener(hitArea, 'click', () => emit('select-leg', leg.plannerLegId))
      maps.event.addListener(line, 'mouseover', () => emit('select-leg', leg.plannerLegId))
      maps.event.addListener(hitArea, 'mouseover', () => emit('select-leg', leg.plannerLegId))
      lineOverlays.push({ id: leg.plannerLegId, line, hitArea, warning: ['CAUTION', 'HIGH'].includes(leg.burdenSeverity) })
    }
    if (bounds) map.setBounds(bounds, 40, 40, 40, 40)
    else map.setCenter(new maps.LatLng(viewport.center.latitude, viewport.center.longitude))
    status.value = 'ready'
    styleActive()
  } catch {
    status.value = 'error'
    map = undefined
  }
}

watch(() => [props.items, props.legs], renderMap, { immediate: true, deep: true })
watch(() => props.activeId, styleActive)
watch(() => props.activeLegId, styleActive)
onBeforeUnmount(clearOverlays)
</script>

<template>
  <div class="day-map-shell">
    <div ref="container" class="day-map" data-testid="day-map" aria-label="현재 Day 일정 지도" />
    <div v-if="status === 'loading'" class="map-skeleton map-overlay" aria-label="지도를 불러오는 중" />
    <div v-if="activeLeg" class="map-leg-popover" aria-live="polite">
      <strong>{{ activeLeg.fromTitle }} → {{ activeLeg.toTitle }}</strong>
      <span v-if="activeLeg.mode === 'CAR'">직선거리 참고선 · 실제 도로 경로와 시간은 제공하지 않아요.</span>
      <span v-else>{{ activeLeg.transportModeLabel || '대중교통 이동 정보' }}</span>
      <small v-if="activeLeg.burdenReasons?.length">{{ activeLeg.burdenReasons.join(' ') }}</small>
    </div>
    <div v-if="status === 'empty'" class="map-state" data-testid="map-empty"><strong>아직 지도에 표시할 장소가 충분하지 않아요.</strong><span>식당이나 관광지를 선택하면 여기에 일정이 보여요.</span></div>
    <div v-if="status === 'error'" class="map-state" data-testid="map-error"><strong>지도를 불러오지 못했어요.</strong><span>일정 카드는 계속 확인할 수 있어요.</span></div>
  </div>
</template>
