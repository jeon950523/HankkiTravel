<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import KakaoDayMap from '../components/KakaoDayMap.vue'
import KtoImage from '../components/KtoImage.vue'
import RecommendationContextBanner from '../components/RecommendationContextBanner.vue'
import StatusMessage from '../components/StatusMessage.vue'
import { activitySlotForMeal, dessertSlotForMeal, MEAL_LABELS, showStayForDay, SLOT_LABELS, transitSummary } from '../domain/trip'
import { buildPlannerDisplayItems, buildPlannerLegs, carRouteNotice } from '../domain/plannerMap'
import { evaluateDayCompletion, resolveNextDecision } from '../domain/progressivePlanner'
import { ATTRACTION_PERSPECTIVES, burdenLabel, dayBurdenSummary, movementEvidence, perspectiveCandidates } from '../domain/routeAwareAttraction'
import { kakaoDirectionsAction } from '../domain/kakaoLinks'
import { coveragePresentation, hasUnscoredDimensions, kakaoSearchUrl as buildKakaoSearchUrl, restaurantExternalActions, restaurantMovementEvidence } from '../domain/restaurantEvidence'
import { problemLegId, reentryTarget, routeSanityActionLabel, routeSanityActions, routeSanityLabel, routeSanitySummary, slotHasRouteWarning } from '../domain/routeSanity'
import { useGuestStore } from '../stores/guest'
import { useProfilesStore } from '../stores/profiles'
import { useTripsStore } from '../stores/trips'

const route = useRoute()
const router = useRouter()
const guest = useGuestStore()
const profiles = useProfilesStore()
const trips = useTripsStore()
const guestId = ref('')
const dayNumber = ref(1)
const selectedSlotId = ref('')
const activePlaceType = ref('')
const activeAttractionPerspective = ref('NEARBY_COURSE')
const focusKeyword = ref('')
const selectedPlaces = ref({})
const plannerSection = ref(null)
const plannerHeading = ref(null)
const activePlannerItemId = ref('')
const activePlannerLegId = ref('')
const searchKeyword = ref('')
const dessertKeyword = ref('')
const explorationMode = ref('')
const dessertExplorationMode = ref('')
const skippedDecisions = ref([])
const expandedDecision = ref('')
const keptSanityKey = ref('')
const imageAlt = title => `${title} 관광정보 이미지`
const tripId = computed(() => String(route.params.tripPublicId || ''))
const isEditMode = computed(() => route.query.edit === '1')
const day = computed(() => trips.detail?.days.find(item => item.dayNumber === dayNumber.value))
const selectedSlot = computed(() => day.value?.mealSlots.find(item => item.mealSlotPublicId === selectedSlotId.value) || day.value?.mealSlots[0])
const mealResult = computed(() => selectedSlot.value ? trips.restaurantResults[`${tripId.value}:${selectedSlot.value.mealSlotPublicId}`] : null)
const dayKey = computed(() => `${tripId.value}:${dayNumber.value}`)
const focusResult = computed(() => trips.focusResults[dayKey.value])
const selectedFocus = computed(() => trips.selectedFocus[dayKey.value] || planner.value?.items?.find(item => item.slotType === 'DAY_FOCUS'))
const hasDayFocus = computed(() => Boolean(selectedFocus.value))
const dessertResult = computed(() => selectedSlot.value ? trips.dessertResults[`${dayKey.value}:${selectedSlot.value.mealType}`] : null)
const placeResult = computed(() => activePlaceType.value ? trips.placeResults[`${tripId.value}:${dayNumber.value}:${activePlaceType.value}`] : null)
const placeCandidates = computed(() => activePlaceType.value === 'STAY' ? (placeResult.value?.candidates || []) : perspectiveCandidates(placeResult.value, activeAttractionPerspective.value))
const activePerspective = computed(() => placeResult.value?.perspectives?.find(item => item.perspective === activeAttractionPerspective.value))
const planner = computed(() => trips.planners[`${tripId.value}:${dayNumber.value}`])
const plannerItems = computed(() => hasDayFocus.value ? buildPlannerDisplayItems(planner.value?.items || []) : [])
const plannerLegs = computed(() => buildPlannerLegs(plannerItems.value, planner.value?.legs || []))
const sanity = computed(() => planner.value?.routeSanity)
const sanityActions = computed(() => keptSanityKey.value === `${dayKey.value}:${sanity.value?.severity}` ? [] : routeSanityActions(sanity.value))
const activePlaceComplete = computed(() => plannerItems.value.some(item => item.slotType === activePlaceType.value))
const plannerLoading = computed(() => Boolean(trips.plannerLoading[dayKey.value]))
const plannerError = computed(() => trips.plannerErrors[dayKey.value])
const regionName = computed(() => ({ JEJU: '제주', GYEONGJU: '경주' }[trips.detail?.regionKey] || ''))
const profile = computed(() => profiles.items.find(item => item.profileId === trips.detail?.profileId))
const recommendationError = computed(() => activePlaceType.value ? trips.recommendationErrors[`${dayKey.value}:${activePlaceType.value}`] : null)
const profileName = computed(() => profile.value?.name || '가족 프로필')
const carNotice = computed(() => carRouteNotice(profile.value?.transportMode))
const mealSlotCount = computed(() => trips.detail?.days.reduce((sum, item) => sum + item.mealSlots.length, 0) || 0)
const selectedMealCount = computed(() => trips.detail?.days.reduce((sum, item) => sum + item.mealSlots.filter(slot => slot.anchor).length, 0) || 0)
const formatDate = value => value ? new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric' }).format(new Date(`${value}T12:00:00`)) : ''
const daySummaries = computed(() => (trips.detail?.days || []).map(item => {
  const key = `${tripId.value}:${item.dayNumber}`
  const hydratedFocus = trips.planners[key]?.items?.some(value => value.slotType === 'DAY_FOCUS')
  return { ...item, selectedMeals: item.mealSlots.filter(slot => slot.anchor).length, focusSelected: Boolean(trips.selectedFocus[key] || hydratedFocus) }
}))
const canStay = computed(() => showStayForDay(dayNumber.value, trips.detail?.durationDays || 0))
const candidates = computed(() => {
  const seen = new Set()
  if (mealResult.value?.topCandidates?.length) return mealResult.value.topCandidates
  return (mealResult.value?.perspectives || [])
    .flatMap(perspective => perspective.candidates.map(candidate => ({ ...candidate, perspective: perspective.perspective })))
    .filter(candidate => !seen.has(candidate.contentId) && seen.add(candidate.contentId))
})
const perspectiveLabel = value => ({ BALANCED: '균형 있게', MEAL_PRIORITY: '한 끼 우선', MOBILITY_PRIORITY: '이동 편의 우선' }[value] || value)
const selectionKey = type => `${dayNumber.value}:${type}`
const alternativeKey = computed(() => `${dayKey.value}:${activePlaceType.value || 'RESTAURANT'}`)
const alternativeResult = computed(() => trips.alternativeResults[alternativeKey.value])
const nextDecision = computed(() => resolveNextDecision({ day: day.value, plannerItems: plannerItems.value, stayRequired: canStay.value, skipped: skippedDecisions.value }))
const dayCompletion = computed(() => { const value = planner.value?.completion || evaluateDayCompletion({ day: day.value, plannerItems: plannerItems.value, stayRequired: canStay.value }); return { ...value, isRequiredComplete: value.isRequiredComplete ?? value.requiredComplete } })
const tripComplete = computed(() => (trips.detail?.days || []).every(item => {
  const currentPlanner = trips.planners[`${tripId.value}:${item.dayNumber}`]
  const completion = currentPlanner?.completion || item.completion || evaluateDayCompletion({
    day: item,
    plannerItems: buildPlannerDisplayItems(currentPlanner?.items || []),
    stayRequired: showStayForDay(item.dayNumber, trips.detail?.durationDays || 0),
  })
  return completion.requiredComplete ?? completion.isRequiredComplete
}))
const nextDayNumber = computed(() => dayNumber.value < (trips.detail?.durationDays || 0) ? dayNumber.value + 1 : null)
const selectedDessert = computed(() => plannerItems.value.find(item => item.slotType === dessertSlotForMeal(selectedSlot.value?.mealType)))
const dessertSlot = computed(() => selectedSlot.value?.mealType ? dessertSlotForMeal(selectedSlot.value.mealType) : '')
const skippedDessert = computed(() => Boolean(dessertSlot.value && skippedDecisions.value.includes(dessertSlot.value)))
const selectedMealItem = computed(() => plannerItems.value.find(item => item.slotType === selectedSlot.value?.mealType))
const kakaoSearchUrl = (kind = '식당') => buildKakaoSearchUrl({ title: `${selectedMealItem.value?.title || regionName.value} ${kind}` })
const scarcityMessage = (count, kind) => count === 1 ? `현재 범위에서 추가로 확인되는 ${kind} 1곳이 있어요.` : count === 0 ? `현재 범위에서 한국관광공사 데이터로 추가 확인되는 ${kind}은 없어요.` : `현재 범위에서 추가로 확인되는 ${kind} ${count}곳이 있어요.`
const originItem = result => plannerItems.value.find(item => item.slotType === result?.recommendationContext?.originSlotType)
const candidateDirections = (candidate, result = mealResult.value) => kakaoDirectionsAction({ start: originItem(result), end: candidate, transportMode: profile.value?.transportMode })
const plannerDirections = index => kakaoDirectionsAction({ start: plannerItems.value[index], end: plannerItems.value[index + 1], transportMode: plannerLegs.value[index]?.mode || profile.value?.transportMode })
const menuSummary = candidate => (candidate?.menuSummary || []).slice(0, 3)
watch(planner, (value, previous) => { if (previous && value !== previous) keptSanityKey.value = '' })
watch(day, async value => {
  selectedSlotId.value = value?.mealSlots[0]?.mealSlotPublicId || ''
  activePlaceType.value = ''
  activePlannerItemId.value = ''
  activePlannerLegId.value = ''
  skippedDecisions.value = []
  expandedDecision.value = ''
  keptSanityKey.value = ''
  if (guestId.value && value) await loadPlanner()
})
async function recommendFocus() { await trips.recommendFocus(guestId.value, tripId.value, dayNumber.value).catch(() => {}) }
async function searchFocus() { if (focusKeyword.value.trim()) await trips.searchFocus(guestId.value, tripId.value, dayNumber.value, focusKeyword.value).catch(() => {}) }
async function selectFocus(candidate) { const selected = await trips.selectFocus(guestId.value, tripId.value, dayNumber.value, candidate).catch(() => null); if (selected) { expandedDecision.value = ''; await loadPlanner(); await advanceToNext() } }
async function clearFocus() { await trips.clearFocus(guestId.value, tripId.value, dayNumber.value).catch(() => {}) }
async function recommendMeal() { if (selectedSlot.value) await trips.recommendMeal(guestId.value, tripId.value, selectedSlot.value.mealSlotPublicId).catch(() => {}) }
async function selectMeal(contentId) { const selected = await trips.selectMeal(guestId.value, tripId.value, selectedSlot.value.mealSlotPublicId, contentId).catch(() => null); if (selected) { expandedDecision.value = ''; await loadPlanner(); await advanceToNext() } }
async function recommendDessert() {
  if (selectedSlot.value?.mealType) await trips.recommendDessert(guestId.value, tripId.value, dayNumber.value, selectedSlot.value.mealType).catch(() => {})
}
async function selectDessert(candidate) {
  const type = dessertSlotForMeal(selectedSlot.value.mealType)
  const selected = await trips.selectPlace(guestId.value, tripId.value, dayNumber.value, type, candidate.contentId).catch(() => null)
  if (selected) { selectedPlaces.value[selectionKey(type)] = candidate; expandedDecision.value = ''; await loadPlanner(); await advanceToNext() }
}
async function recommendPlace(type) {
  activePlaceType.value = type
  if (type !== 'STAY') activeAttractionPerspective.value = 'NEARBY_COURSE'
  const action = type === 'STAY' ? trips.recommendStay(guestId.value, tripId.value, dayNumber.value) : trips.recommendPlace(guestId.value, tripId.value, dayNumber.value, type)
  await action.catch(() => {})
}
async function selectPlace(candidate) {
  const type = activePlaceType.value
  const selected = await trips.selectPlace(guestId.value, tripId.value, dayNumber.value, type, candidate.contentId).catch(() => null)
  if (selected) { selectedPlaces.value[selectionKey(type)] = candidate; expandedDecision.value = ''; await loadPlanner(); await advanceToNext() }
}
async function reselectPlanner(slotType) {
  expandedDecision.value = slotType
  searchKeyword.value = ''
  explorationMode.value = ''
  await nextTick()
  document.getElementById(`decision-${slotType}`)?.querySelector('h2')?.focus({ preventScroll: true })
  if (slotType === 'DAY_FOCUS') {
    await recommendFocus(); await nextTick(); document.getElementById('focus-result')?.focus({ preventScroll: true }); return
  }
  if (['BREAKFAST', 'LUNCH', 'DINNER'].includes(slotType)) {
    const slot = day.value?.mealSlots.find(item => item.mealType === slotType)
    if (slot) { selectedSlotId.value = slot.mealSlotPublicId; await recommendMeal(); return }
  }
  if (slotType === 'POST_LUNCH_DESSERT' || slotType === 'POST_DINNER_DESSERT') {
    const slot = day.value?.mealSlots.find(item => item.mealType === (slotType === 'POST_LUNCH_DESSERT' ? 'LUNCH' : 'DINNER'))
    if (slot) { selectedSlotId.value = slot.mealSlotPublicId; await recommendDessert(); return }
  }
  await recommendPlace(slotType)
  await nextTick()
  document.getElementById('place-result')?.focus({ preventScroll: true })
}
async function clearPlanner(slotType) {
  if (slotType === 'DAY_FOCUS') await trips.clearFocus(guestId.value, tripId.value, dayNumber.value).catch(() => {})
  else if (['BREAKFAST', 'LUNCH', 'DINNER'].includes(slotType)) {
    const slot = day.value?.mealSlots.find(item => item.mealType === slotType)
    if (slot) await trips.clearMeal(guestId.value, tripId.value, slot.mealSlotPublicId).catch(() => {})
  } else await trips.clearPlace(guestId.value, tripId.value, dayNumber.value, slotType).catch(() => {})
  await loadPlanner()
}
async function loadPlanner() { await trips.loadPlanner(guestId.value, tripId.value, dayNumber.value).catch(() => {}) }
async function advanceToNext() {
  await nextTick()
  const target = nextDecision.value
  if (['BREAKFAST', 'LUNCH', 'DINNER'].includes(target)) {
    const slot = day.value?.mealSlots.find(item => item.mealType === target)
    if (slot) selectedSlotId.value = slot.mealSlotPublicId
  }
  await nextTick()
  const id = target === 'PLANNER' ? 'day-planner' : `decision-${target}`
  const element = document.getElementById(id)
  element?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  const section = element?.closest('section') || element
  section?.querySelector('h2')?.focus({ preventScroll: true })
}
async function skipDessert() {
  const slot = dessertSlotForMeal(selectedSlot.value.mealType)
  if (!skippedDecisions.value.includes(slot)) skippedDecisions.value = [...skippedDecisions.value, slot]
  await advanceToNext()
}
async function addDessertAfterSkip() {
  skippedDecisions.value = skippedDecisions.value.filter(item => item !== dessertSlot.value)
  dessertExplorationMode.value = ''
  await nextTick()
  document.getElementById(`decision-${dessertSlot.value}`)?.querySelector('h2')?.focus()
}
async function otherRestaurants() {
  activePlaceType.value = ''
  explorationMode.value = 'OTHER'
  await trips.otherRestaurants(guestId.value, tripId.value, dayNumber.value, selectedSlot.value?.mealType, candidates.value.map(item => item.contentId)).catch(() => {})
}
async function searchRestaurants() {
  if (!searchKeyword.value.trim()) return
  activePlaceType.value = ''
  explorationMode.value = 'SEARCH'
  await trips.searchRestaurants(guestId.value, tripId.value, dayNumber.value, selectedSlot.value?.mealType, searchKeyword.value.trim()).catch(() => {})
}
async function otherDesserts() {
  dessertExplorationMode.value = 'OTHER'
  if (selectedSlot.value?.mealType) await trips.otherDesserts(guestId.value, tripId.value, dayNumber.value, selectedSlot.value.mealType, (dessertResult.value?.candidates || []).map(item => item.contentId)).catch(() => {})
}
async function searchDesserts() {
  dessertExplorationMode.value = 'SEARCH'
  if (selectedSlot.value?.mealType && dessertKeyword.value.trim()) await trips.searchDesserts(guestId.value, tripId.value, dayNumber.value, selectedSlot.value.mealType, dessertKeyword.value.trim()).catch(() => {})
}
async function openNextDay() {
  if (!nextDayNumber.value) return
  dayNumber.value = nextDayNumber.value
  await nextTick()
  document.getElementById('focus-decision')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  document.getElementById('focus-decision')?.focus({ preventScroll: true })
}
function openFinalPlan() { router.push(`/travel/${tripId.value}/plan`) }
async function startNextDecision() {
  if (['BREAKFAST', 'LUNCH', 'DINNER'].includes(nextDecision.value)) {
    const slot = day.value?.mealSlots.find(item => item.mealType === nextDecision.value)
    if (slot) selectedSlotId.value = slot.mealSlotPublicId
    await nextTick(); return recommendMeal()
  }
  if (nextDecision.value === 'STAY') return recommendPlace('STAY')
  if (nextDecision.value === 'DAY_FOCUS') return recommendFocus()
}
async function otherPlaces() {
  explorationMode.value = 'OTHER'
  await trips.otherPlaces(guestId.value, tripId.value, dayNumber.value, activePlaceType.value, placeCandidates.value.map(item => item.contentId)).catch(() => {})
}
async function searchPlaces() {
  if (!searchKeyword.value.trim()) return
  explorationMode.value = 'SEARCH'
  await trips.searchPlaces(guestId.value, tripId.value, dayNumber.value, activePlaceType.value, searchKeyword.value.trim()).catch(() => {})
}
async function selectAlternative(candidate) {
  if (!activePlaceType.value) return selectMeal(candidate.contentId)
  return selectPlace(candidate)
}
async function activatePlannerItem(id, scroll = false) {
  activePlannerLegId.value = ''
  activePlannerItemId.value = id
  if (scroll) {
    await nextTick()
    document.getElementById(`planner-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }
}
async function activatePlannerLeg(id, scroll = false) {
  activePlannerItemId.value = ''
  activePlannerLegId.value = id
  if (scroll) { await nextTick(); document.getElementById(`planner-leg-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' }) }
}
async function focusProblemLeg() {
  const id = problemLegId(sanity.value, plannerLegs.value)
  if (id) await activatePlannerLeg(id, true)
}
async function handleSanityAction(action) {
  const target = reentryTarget(action, sanity.value?.longestLeg)
  if (target.type === 'KEEP') { keptSanityKey.value = `${dayKey.value}:${sanity.value?.severity}`; return }
  if (target.type === 'RESTAURANT') {
    const mealType = ['BREAKFAST', 'LUNCH', 'DINNER'].includes(target.slotType) ? target.slotType : target.slotType === 'POST_LUNCH_DESSERT' ? 'LUNCH' : target.slotType === 'POST_DINNER_DESSERT' ? 'DINNER' : day.value?.mealSlots[0]?.mealType
    const slot = day.value?.mealSlots.find(item => item.mealType === mealType)
    if (slot) { selectedSlotId.value = slot.mealSlotPublicId; expandedDecision.value = mealType; await recommendMeal() }
    return
  }
  activeAttractionPerspective.value = 'NEARBY_COURSE'
  await recommendPlace(target.slotType)
}
async function showPlanner() {
  if (!hasDayFocus.value) return
  if (!planner.value) await loadPlanner()
  await nextTick()
  plannerSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  plannerHeading.value?.focus({ preventScroll: true })
}
onMounted(async () => {
  try {
    guestId.value = await guest.ensureGuest()
    trips.resetTransient(tripId.value)
    await Promise.all([trips.load(guestId.value, tripId.value), profiles.load(guestId.value)])
    const requestedDay = Number(route.query.day)
    if (Number.isInteger(requestedDay) && requestedDay >= 1 && requestedDay <= (trips.detail?.durationDays || 0)) dayNumber.value = requestedDay
    selectedSlotId.value = day.value?.mealSlots[0]?.mealSlotPublicId || ''
    await loadPlanner()
  } catch { /* store error is rendered */ }
})
</script>

<template>
  <div class="content-page journey-page">
    <StatusMessage v-if="trips.loading" kind="loading" title="여행을 불러오고 있어요">저장한 일정 구조만 먼저 확인하고 있어요.</StatusMessage>
    <StatusMessage v-else-if="!trips.detail" kind="error" title="여행을 불러오지 못했어요">{{ trips.error }}</StatusMessage>
    <template v-else>
      <header class="trip-overview surface">
        <div><p class="eyebrow">전체 여행</p><h1>{{ regionName }} {{ trips.detail.durationDays - 1 }}박 {{ trips.detail.durationDays }}일</h1><p class="page-description">{{ formatDate(trips.detail.startDate) }} ~ {{ formatDate(trips.detail.endDate) }} · 가족 프로필: {{ profileName }}</p></div>
        <dl class="trip-overview-facts"><div><dt>DAY</dt><dd>{{ trips.detail.durationDays }}일</dd></div><div><dt>식사</dt><dd>{{ selectedMealCount }}/{{ mealSlotCount }} 선택</dd></div></dl>
      </header>
      <nav class="day-switcher day-summary-switcher" aria-label="여행 날짜"><button v-for="item in daySummaries" :key="item.dayNumber" type="button" :class="{ active: item.dayNumber === dayNumber }" @click="dayNumber = item.dayNumber"><strong>DAY {{ item.dayNumber }} · {{ item.completion?.requiredComplete ? '완료' : '준비 중' }}</strong><span>{{ formatDate(item.travelDate) }}</span><small>{{ item.focusSelected ? '시작 장소 선택됨' : '시작 장소 미선택' }} · 식사 {{ item.selectedMeals }}/{{ item.mealSlots.length }}</small></button></nav>

      <section v-if="nextDecision !== 'DAY_FOCUS' && nextDecision !== 'DAY_COMPLETE'" class="surface next-decision-primary" aria-labelledby="primary-next-title">
        <p class="step-label">다음 결정</p><h2 id="primary-next-title" tabindex="-1">{{ SLOT_LABELS[nextDecision] }}을(를) 골라볼까요?</h2>
        <button class="action-button button-primary" type="button" :disabled="Boolean(trips.actionLoading)" @click="startNextDecision">{{ SLOT_LABELS[nextDecision] }} 추천 보기</button>
        <div class="optional-inline"><span>원하면 추가할 수 있어요</span><button class="text-button" type="button" @click="recommendPlace('AFTERNOON_ACTIVITY')">관광지 추천 보기</button><button v-if="selectedSlot?.anchor && ['LUNCH', 'DINNER'].includes(selectedSlot.mealType)" class="text-button" type="button" @click="recommendDessert">디저트 추가</button><button v-if="hasDayFocus" class="text-button" type="button" @click="showPlanner">오늘 일정 보기</button></div>
      </section>

      <section v-if="dayCompletion.isRequiredComplete" class="surface day-complete-card" aria-live="polite">
        <p class="step-label">{{ isEditMode ? `DAY ${dayNumber} 수정 중` : `DAY ${dayNumber} 완료` }}</p><h2>{{ isEditMode ? '완료된 선택을 변경할 수 있어요.' : `DAY ${dayNumber} 준비가 끝났어요.` }}</h2>
        <div class="card-actions"><button v-if="nextDayNumber && !isEditMode" class="action-button button-primary" type="button" @click="openNextDay">DAY {{ nextDayNumber }} 준비하기</button><button v-if="tripComplete" class="action-button button-primary" type="button" @click="openFinalPlan">{{ isEditMode ? '수정 완료하고 플랜 보기' : '여행 플랜 완성하기' }}</button><button class="action-button button-secondary" type="button" @click="showPlanner">DAY {{ dayNumber }} 일정 보기</button></div>
        <div class="optional-inline"><span>원하면 추가할 수 있어요</span><button class="text-button" type="button" @click="recommendPlace('AFTERNOON_ACTIVITY')">관광지 추천 보기</button><button v-if="day?.mealSlots.some(item => ['LUNCH', 'DINNER'].includes(item.mealType) && item.anchor)" class="text-button" type="button" @click="recommendDessert">+ 디저트 추가</button><button v-if="hasDayFocus" class="text-button" type="button" @click="showPlanner">오늘 일정 보기</button></div>
      </section>

      <section id="day-planner" ref="plannerSection" class="planner-section trip-planner" aria-labelledby="planner-title"><div class="planner-heading"><div><p class="eyebrow">DAY {{ dayNumber }} · {{ formatDate(day?.travelDate) }}</p><h2 id="planner-title" ref="plannerHeading" tabindex="-1">지도와 오늘의 일정</h2></div><button class="text-button" type="button" :disabled="plannerLoading" @click="loadPlanner">일정 새로고침</button></div>
        <p v-if="carNotice" class="car-route-notice">{{ carNotice }}</p>
        <aside v-if="sanity" class="route-sanity" :data-severity="sanity.severity"><div><strong>오늘 이동 부담 · {{ routeSanityLabel(sanity.severity) }}</strong><span>{{ routeSanitySummary(sanity) }}</span><small>이동 근거 {{ sanity.evaluatedLegCount }}/{{ sanity.totalLegCount }}구간 · {{ sanity.evidenceCoverage }}%</small></div><button v-if="problemLegId(sanity, plannerLegs)" class="text-button" type="button" @click="focusProblemLeg">문제 구간 보기</button><div v-if="sanityActions.length" class="route-sanity-actions"><button v-for="action in sanityActions" :key="action" class="text-button" type="button" @click="handleSanityAction(action)">{{ routeSanityActionLabel(action) }}</button></div><p v-else-if="keptSanityKey" class="route-sanity-kept">현재 선택을 유지했어요. 장소를 바꾸면 다시 확인합니다.</p></aside>
        <aside v-else-if="planner?.dayBurden" class="day-burden" :data-level="planner.dayBurden.level"><strong>오늘 이동 부담 · {{ burdenLabel(planner.dayBurden.level) }}</strong><span v-if="dayBurdenSummary(planner.dayBurden)">{{ dayBurdenSummary(planner.dayBurden) }}</span><span v-else>현재 확인 가능한 이동 근거가 부족해요.</span><p v-if="planner.dayBurden.caution">{{ planner.dayBurden.caution }}</p></aside>
        <StatusMessage v-if="plannerError" kind="error" title="오늘 일정을 다시 불러오지 못했어요">현재 저장된 장소 선택은 그대로 유지돼요. 잠시 후 일정 새로고침을 눌러주세요.</StatusMessage>
        <div v-if="plannerLoading && !planner" class="planner-loading-grid"><div class="map-skeleton" /><div class="timeline-skeleton" /></div>
        <div v-else-if="hasDayFocus" class="planner-layout">
          <KakaoDayMap :key="dayNumber" :items="plannerItems" :legs="plannerLegs" :active-id="activePlannerItemId" :active-leg-id="activePlannerLegId" @select="activatePlannerItem($event, true)" @select-leg="activatePlannerLeg($event, true)" />
          <div class="timeline-panel"><p v-if="!plannerItems.length" class="empty-inline">아직 선택한 장소가 없어요. 식당이나 관광지를 먼저 골라주세요.</p><ol v-else class="timeline"><li v-for="(item, index) in plannerItems" :id="`planner-${item.plannerItemId}`" :key="item.plannerItemId"><article class="surface planner-card" :class="{ active: item.plannerItemId === activePlannerItemId }" role="button" tabindex="0" @click="activatePlannerItem(item.plannerItemId)" @keydown.enter="activatePlannerItem(item.plannerItemId)" @keydown.space.prevent="activatePlannerItem(item.plannerItemId)"><span class="timeline-number">{{ item.displayIndex }}</span><KtoImage :src="item.imageUrl" :alt="imageAlt(item.title)" /><div><p class="card-kicker">{{ SLOT_LABELS[item.slotType] }}</p><h3>{{ item.title || '현재 정보를 확인할 수 없는 장소' }}</h3><p class="address">{{ item.address }}</p><p class="source-line">{{ item.sourceAttribution }}</p><p v-if="!item.mapPoint && item.dataAvailability === 'CURRENT_DATA'" class="map-missing-note">현재 관광정보에 좌표가 없어 지도 마커만 생략했어요.</p></div></article><button v-if="plannerLegs[index]" :id="`planner-leg-${plannerLegs[index].plannerLegId}`" class="planner-leg" :class="{ active: plannerLegs[index].plannerLegId === activePlannerLegId, warning: ['CAUTION', 'HIGH'].includes(plannerLegs[index].burdenSeverity) }" type="button" @click="activatePlannerLeg(plannerLegs[index].plannerLegId)"><strong>{{ plannerLegs[index].displayFrom }} → {{ plannerLegs[index].displayTo }}</strong><span>{{ transitSummary(plannerLegs[index]) }}</span><small v-if="plannerLegs[index].burdenReasons?.length">{{ plannerLegs[index].burdenReasons.join(' ') }}</small></button><a v-if="plannerLegs[index]" class="text-button planner-directions-link" :href="plannerDirections(index).href" target="_blank" rel="noopener noreferrer">{{ plannerDirections(index).label }}</a></li></ol></div>
        </div>
        <p v-else class="empty-inline">오늘 여행의 시작 장소를 고르면 지도와 일정이 나타나요.</p>
      </section>

      <section id="decision-DAY_FOCUS" class="surface decision-section" aria-labelledby="focus-decision"><p class="step-label">{{ isEditMode && dayCompletion.isRequiredComplete ? `DAY ${dayNumber} 수정 중` : '첫 번째 결정' }}</p><h2 id="focus-decision" tabindex="-1">{{ isEditMode && dayCompletion.isRequiredComplete ? '완료된 선택을 변경할 수 있어요.' : '오늘 여행을 어디에서 시작할까요?' }}</h2><p v-if="!(isEditMode && dayCompletion.isRequiredComplete)" class="result-notice">하루 전체의 고정 기준점이 아니라, DAY {{ dayNumber }} 여행의 방향을 잡는 첫 장소예요.</p>
      <section v-if="plannerItems.length" class="compact-decisions" aria-label="완료한 선택"><p class="step-label">완료한 선택</p><div v-for="item in plannerItems" :key="`edit-${item.plannerItemId}`" class="compact-decision"><span aria-hidden="true">✓</span><strong>{{ SLOT_LABELS[item.slotType] }} · {{ item.title || '현재 선택' }} <small v-if="slotHasRouteWarning(item.slotType, plannerLegs)" class="route-warning-badge">이동 부담 큼</small></strong><button class="text-button" type="button" :disabled="Boolean(trips.actionLoading)" @click="reselectPlanner(item.slotType)">변경</button><button class="text-button" type="button" :disabled="Boolean(trips.actionLoading)" @click="clearPlanner(item.slotType)">선택 해제</button></div></section>


        <div v-if="selectedFocus" class="focus-selection"><strong>{{ selectedFocus.title }}</strong><span>{{ selectedFocus.areaLabel || selectedFocus.address }}</span><button type="button" class="text-button" @click="clearFocus">다시 고르기</button></div>
        <template v-if="!selectedFocus || expandedDecision === 'DAY_FOCUS'"><div class="focus-actions"><button class="action-button button-secondary" type="button" :disabled="Boolean(trips.actionLoading)" @click="recommendFocus">추천받기</button><form class="focus-search" @submit.prevent="searchFocus"><label class="wide-field">가고 싶은 곳 찾기<input v-model.trim="focusKeyword" maxlength="100" placeholder="예: 성산일출봉, 첨성대" /></label><button class="action-button button-primary" type="submit" :disabled="!focusKeyword.trim() || Boolean(trips.actionLoading)">찾기</button></form></div></template>
      </section>

      <section v-if="focusResult && (!selectedFocus || expandedDecision === 'DAY_FOCUS')" class="decision-results" aria-labelledby="focus-result"><p class="eyebrow">현재 TourAPI Live</p><h2 id="focus-result" tabindex="-1">오늘 여행의 시작 장소</h2><p v-if="!focusResult.candidates.length" class="empty-inline">현재 확인할 수 있는 장소가 없어요.</p><article v-for="candidate in focusResult.candidates" :key="candidate.contentId" class="surface live-card"><KtoImage :src="candidate.imageUrl" :alt="imageAlt(candidate.title)" /><div class="live-card-body"><p class="card-kicker">{{ candidate.areaLabel || regionName }}</p><h3>{{ candidate.title }}</h3><p class="address">{{ candidate.address }}</p><p>관광 유형 {{ candidate.contentType }}</p><p class="source-line">{{ candidate.sourceAttribution }}</p><button class="action-button button-primary" type="button" @click="selectFocus(candidate)">이곳을 시작 장소로 선택</button></div></article></section>

      <section :id="`decision-${selectedSlot?.mealType || 'MEAL'}`" class="surface decision-section" aria-labelledby="meal-decision"><p class="step-label">두 번째 결정</p><h2 id="meal-decision" tabindex="-1">DAY {{ dayNumber }} 식당 선택</h2>
        <div v-if="day?.mealSlots?.length" class="slot-switcher"><button v-for="slot in day.mealSlots" :key="slot.mealSlotPublicId" type="button" :class="{ active: slot.mealSlotPublicId === selectedSlot?.mealSlotPublicId }" @click="selectedSlotId = slot.mealSlotPublicId">{{ MEAL_LABELS[slot.mealType] }}<span>{{ slot.anchor ? '선택 완료' : '선택 전' }}</span></button></div><p v-else class="empty-inline">이 Day에는 선택한 식사 시간이 없어요.</p>
        <div v-if="selectedSlot?.anchor && expandedDecision !== selectedSlot.mealType" class="compact-decision"><span aria-hidden="true">✓</span><strong>{{ MEAL_LABELS[selectedSlot.mealType] }} 선택 완료</strong><button class="text-button" type="button" @click="reselectPlanner(selectedSlot.mealType)">변경</button></div><template v-else><p v-if="!selectedFocus" class="empty-inline">오늘 여행의 시작 장소를 먼저 골라주세요.</p><button v-if="selectedSlot" class="action-button button-primary" type="button" :disabled="Boolean(trips.actionLoading) || !selectedFocus" @click="recommendMeal">{{ trips.actionLoading.startsWith('meal:') ? '가족 조건과 현재 관광정보를 함께 확인하고 있어요' : `${MEAL_LABELS[selectedSlot.mealType]} TOP3 보기` }}</button></template>
      </section>

      <StatusMessage v-if="trips.error" kind="error" title="현재 요청을 마치지 못했어요">{{ trips.error }}</StatusMessage>
      <section v-if="mealResult && (!selectedSlot?.anchor || expandedDecision === selectedSlot?.mealType)" class="decision-results" aria-labelledby="meal-result"><p class="eyebrow">현재 TourAPI Live</p><h2 id="meal-result">식당 추천</h2><RecommendationContextBanner :context="mealResult.recommendationContext" /><p class="result-notice">{{ mealResult.nutritionNotice }}</p><p v-if="!candidates.length" class="empty-inline">현재 조건으로 비교할 추천 후보가 없어요.</p>
        <p class="result-notice">추천 식당 {{ candidates.length }}곳을 현재 조건에서 확인했어요.</p>
        <article v-for="candidate in candidates" :key="candidate.contentId" class="surface live-card"><KtoImage :src="candidate.imageUrl" :alt="imageAlt(candidate.title)" /><div class="live-card-body"><p class="card-kicker">{{ candidate.areaLabel || perspectiveLabel(candidate.perspective) }}</p><h3>{{ candidate.title }}</h3><p class="address">{{ candidate.address }}</p><dl class="facts evidence-facts"><div><dt>정보 근거</dt><dd>{{ candidate.evidenceCoverage ?? candidate.evaluatedWeight }}% · {{ coveragePresentation(candidate.evidenceCoverage ?? candidate.evaluatedWeight).label }}</dd></div><div><dt>확인된 정보 기준 적합도</dt><dd>{{ candidate.overallScore ?? candidate.compatibilityScore }}점</dd></div></dl><p v-if="coveragePresentation(candidate.evidenceCoverage ?? candidate.evaluatedWeight).notice" class="evidence-notice">{{ coveragePresentation(candidate.evidenceCoverage ?? candidate.evaluatedWeight).notice }}</p><p v-if="hasUnscoredDimensions(candidate)" class="evidence-notice">확인 가능한 정보만으로 적합도를 계산했어요. 미평가 항목은 점수를 깎지 않았어요.</p><div class="menu-evidence"><strong>대표 메뉴</strong><p v-if="menuSummary(candidate).length">{{ menuSummary(candidate).join(' · ') }}</p><p v-else>공개된 대표메뉴 정보가 없어요. 방문 전에 메뉴를 확인해 주세요.</p></div><div class="movement-evidence"><strong>최근 확정 장소에서의 이동</strong><p>{{ restaurantMovementEvidence(candidate, profile?.transportMode).primary }}</p><span>{{ restaurantMovementEvidence(candidate, profile?.transportMode).secondary }}</span><a class="text-button" :href="candidateDirections(candidate).href" target="_blank" rel="noopener noreferrer">{{ candidateDirections(candidate).label }}</a></div><ul class="dimension-list"><li v-for="dimension in candidate.evaluatedDimensions" :key="dimension.code || dimension.dimension"><span>{{ dimension.label || dimension.dimension }}</span><strong>{{ dimension.evaluated === false || dimension.evidenceState === 'NOT_EVALUATED' ? '미평가' : `${dimension.awardedPoints ?? dimension.score} / ${dimension.maxPoints ?? dimension.weight}` }}</strong></li></ul><div class="reason-grid"><div><h4>우리 가족과 잘 맞는 점</h4><p>{{ candidate.ourFamilyFitReasons.join(' ') || '현재 확인된 근거를 살펴봐 주세요.' }}</p></div><div><h4>우리 가족이 주의할 점</h4><p>{{ candidate.ourFamilyCautions.join(' ') || '공개 정보에서 별도 근거를 확인하지 못했어요.' }}</p></div><div><h4>방문 전에 같이 확인하면 좋은 점</h4><p>{{ candidate.checkBeforeVisit.join(' ') }}</p></div></div><p v-if="candidate.nutritionEvidence.length" class="nutrition-line">{{ candidate.nutritionEvidence.map(item => `${item.menuName} · ${item.referenceLabel}`).join(' / ') }}</p><div class="contact-actions"><a v-for="action in restaurantExternalActions(candidate)" :key="action.kind" class="action-button button-secondary" :href="action.href" :target="action.kind === 'phone' ? undefined : '_blank'" :rel="action.kind === 'phone' ? undefined : 'noopener noreferrer'">{{ action.label }}</a></div><p class="source-line">{{ mealResult.sourceAttribution }}</p><button class="action-button button-primary" type="button" :disabled="Boolean(trips.actionLoading)" @click="selectMeal(candidate.contentId)">{{ selectedSlot.anchor?.contentId === candidate.contentId ? '선택 완료' : '이 식당 선택' }}</button></div></article>
        <div class="alternative-actions"><button class="action-button button-secondary" type="button" @click="otherRestaurants">다른 식당 보기</button><form class="focus-search" @submit.prevent="searchRestaurants"><label class="wide-field">식당 직접 찾기<input v-model.trim="searchKeyword" maxlength="100" placeholder="찾을 식당 이름" /></label><button class="action-button button-primary" type="submit" :disabled="!searchKeyword.trim()">찾기</button></form></div>
      </section>

      <span v-if="selectedSlot?.anchor" :id="`decision-${activitySlotForMeal(selectedSlot.mealType)}`" class="decision-anchor" /><span id="decision-STAY" class="decision-anchor" />

      <section v-if="(placeResult || recommendationError) && (!activePlaceComplete || expandedDecision === activePlaceType)" :id="`decision-${activePlaceType}`" class="decision-results attraction-results" aria-labelledby="place-result">
        <p class="eyebrow">{{ SLOT_LABELS[activePlaceType] }} · TourAPI Live</p><h2 id="place-result" tabindex="-1">{{ activePlaceType === 'STAY' ? '숙소' : '관광지' }} 추천</h2>
        <RecommendationContextBanner v-if="placeResult" :context="placeResult.recommendationContext" />
        <StatusMessage v-if="recommendationError" kind="error" :title="activePlaceType === 'STAY' ? '숙소 추천을 현재 불러오지 못했어요.' : '관광지 추천을 현재 불러오지 못했어요.'">잠시 후 다시 확인해 주세요. 선택한 식당과 오늘 일정은 그대로 유지돼요.</StatusMessage>
        <template v-if="activePlaceType !== 'STAY'"><div class="perspective-tabs" role="tablist" aria-label="관광지 추천 관점"><button v-for="item in ATTRACTION_PERSPECTIVES" :key="item.value" type="button" role="tab" :aria-selected="activeAttractionPerspective === item.value" :class="{ active: activeAttractionPerspective === item.value }" @click="activeAttractionPerspective = item.value">{{ item.label }}</button></div><p class="result-notice">{{ activePerspective?.message || ATTRACTION_PERSPECTIVES.find(item => item.value === activeAttractionPerspective)?.description }}</p><p v-if="activePerspective?.status === 'MOVEMENT_CONTEXT_REQUIRED'" class="empty-inline">{{ activePerspective.message }}</p></template>
        <p v-if="!placeCandidates.length" class="empty-inline">현재 확인할 수 있는 후보가 없어요.</p>
        <article v-for="candidate in placeCandidates" :key="`${candidate.perspective || activePlaceType}:${candidate.contentId}`" class="surface live-card attraction-card"><KtoImage :src="candidate.imageUrl" :alt="imageAlt(candidate.title)" /><div class="live-card-body"><p class="card-kicker">{{ candidate.areaLabel || regionName }} · {{ activePlaceType === 'STAY' ? '현재 일정 연결' : ATTRACTION_PERSPECTIVES.find(item => item.value === candidate.perspective)?.label }}</p><h3>{{ candidate.title }}</h3><p class="address">{{ candidate.address }}</p><dl v-if="activePlaceType !== 'STAY'" class="facts"><div><dt>일정 적합도</dt><dd>{{ candidate.overallScore }}점</dd></div><div><dt>근거 커버리지</dt><dd>{{ candidate.evidenceCoverage }}%</dd></div><div><dt>이동 근거</dt><dd>{{ movementEvidence(candidate) }}</dd></div><div><dt>이동 부담</dt><dd>{{ burdenLabel(candidate.routeBurden?.level) }}</dd></div></dl><div class="reason-grid"><div><h4>{{ activePlaceType === 'STAY' ? '현재 일정과의 연결 근거' : '이 일정과 잘 맞는 이유' }}</h4><p>{{ (candidate.reasons || candidate.fitReasons || []).join(' ') }}</p></div><div v-if="candidate.familyMobilityEvidence?.length"><h4>가족 이동 조건</h4><p>{{ candidate.familyMobilityEvidence.join(' ') }}</p></div><div><h4>확인할 점</h4><p>{{ (candidate.cautions || candidate.checkBeforeVisit || []).join(' ') }}</p></div></div><p class="source-line">{{ candidate.sourceAttribution }}</p><div v-if="activePlaceType !== 'STAY'" class="recommendation-actions"><a class="action-button button-secondary" :href="kakaoSearchUrl(candidate.title)" target="_blank" rel="noopener noreferrer">카카오맵에서 검색</a><a class="action-button button-secondary" :href="candidateDirections(candidate, placeResult).href" target="_blank" rel="noopener noreferrer">{{ candidateDirections(candidate, placeResult).label }}</a></div><button class="action-button button-primary" type="button" :disabled="Boolean(trips.actionLoading)" @click="selectPlace(candidate)">{{ selectedPlaces[selectionKey(activePlaceType)]?.contentId === candidate.contentId ? '선택 완료' : `이 ${activePlaceType === 'STAY' ? '숙소' : '관광지'} 선택` }}</button></div></article>
        <div class="alternative-actions"><button class="action-button button-secondary" type="button" @click="otherPlaces">다른 {{ activePlaceType === 'STAY' ? '숙소' : '관광지' }} 보기</button><form class="focus-search" @submit.prevent="searchPlaces"><label class="wide-field">{{ activePlaceType === 'STAY' ? '숙소' : '관광지' }} 직접 찾기<input v-model.trim="searchKeyword" maxlength="100" :placeholder="`${activePlaceType === 'STAY' ? '숙소' : '관광지'} 이름`" /></label><button class="action-button button-primary" type="submit" :disabled="!searchKeyword.trim()">찾기</button></form></div>
      </section>

      <section v-if="alternativeResult" class="decision-results" :class="{ 'restaurant-alternative-results': !activePlaceType, 'attraction-alternative-results': activePlaceType && activePlaceType !== 'STAY' }" aria-live="polite"><p class="eyebrow">TourAPI Live · {{ explorationMode === 'SEARCH' ? '직접 검색' : '주변 탐색' }}</p><h2>{{ explorationMode === 'SEARCH' ? '직접 찾은 결과' : activePlaceType === 'STAY' ? '주변의 다른 숙소' : activePlaceType ? '주변의 다른 관광지' : '주변의 다른 식당' }}</h2><RecommendationContextBanner :context="alternativeResult.recommendationContext" /><p class="result-notice">{{ explorationMode === 'SEARCH' ? '검색어와 일치하는 결과를 먼저 보여드려요. 추천 점수는 실제 평가된 경우에만 표시해요.' : '현재 추천 기준 장소에서 가까운 순서로 더 둘러보세요.' }}</p><p class="result-notice">{{ scarcityMessage(alternativeResult.candidates.length, activePlaceType === 'STAY' ? '숙소' : activePlaceType ? '관광지' : '식당') }}</p><article v-for="candidate in alternativeResult.candidates" :key="`alternative-${candidate.contentId}`" class="surface live-card" :class="{ 'attraction-card': activePlaceType && activePlaceType !== 'STAY' }"><KtoImage :src="candidate.imageUrl" :alt="imageAlt(candidate.title)" /><div class="live-card-body"><p class="card-kicker">{{ candidate.overallScore > 0 ? `추천 적합도 ${candidate.overallScore}점 · 근거 ${candidate.evidenceCoverage}%` : '추천 점수 미산정 · 현재 확인 가능한 정보' }}</p><h3>{{ candidate.title }}</h3><p class="address">{{ candidate.address }}</p><p>{{ (candidate.reasons || candidate.fitReasons || []).join(' ') }}</p><dl class="facts"><div><dt>이동 근거</dt><dd>{{ candidate.transitSummary ? movementEvidence(candidate) : candidate.distanceMeters != null ? '현재 기준 장소의 직선거리' : 'TourAPI 위치 정보' }}</dd></div><div><dt>가족 확인</dt><dd>{{ candidate.familyMobilityEvidence?.length ? candidate.familyMobilityEvidence.join(' ') : '운영시간·접근성은 방문 전 확인해 주세요.' }}</dd></div></dl><p v-if="candidate.distanceMeters != null">기준 장소에서 직선거리 {{ candidate.distanceMeters < 1000 ? `${candidate.distanceMeters}m` : `${(candidate.distanceMeters / 1000).toFixed(1)}km` }}</p><p v-if="candidate.telephone">전화 {{ candidate.telephone }}</p><p>{{ (candidate.cautions || candidate.checkBeforeVisit || []).join(' ') }}</p><div class="recommendation-actions"><a class="action-button button-secondary" :href="kakaoSearchUrl(candidate.title)" target="_blank" rel="noopener noreferrer">카카오맵에서 검색</a><a class="action-button button-secondary" :href="candidateDirections(candidate, alternativeResult).href" target="_blank" rel="noopener noreferrer">{{ candidateDirections(candidate, alternativeResult).label }}</a></div><p class="source-line">{{ candidate.sourceAttribution }}</p><button class="action-button button-primary" type="button" @click="selectAlternative(candidate)">이곳 선택</button></div></article><a v-if="!alternativeResult.candidates.length" class="action-button button-secondary" :href="kakaoSearchUrl(activePlaceType === 'STAY' ? '주변 숙소' : activePlaceType ? '주변 관광지' : '주변 식당')" target="_blank" rel="noopener noreferrer">카카오맵에서 주변 {{ activePlaceType === 'STAY' ? '숙소' : activePlaceType ? '관광지' : '식당' }} 찾아보기</a></section>

      <section v-if="selectedSlot?.anchor && ['LUNCH', 'DINNER'].includes(selectedSlot.mealType)" :id="`decision-${dessertSlotForMeal(selectedSlot.mealType)}`" class="surface next-decisions" aria-label="식후 디저트">
        <p class="step-label">선택형 식후 디저트</p>
        <div v-if="skippedDessert" class="compact-decision"><span aria-hidden="true">↷</span><strong>{{ SLOT_LABELS[dessertSlot] }} · 건너뜀</strong><button class="text-button" type="button" @click="addDessertAfterSkip">추가하기</button></div>
        <div v-else-if="selectedDessert && expandedDecision !== dessertSlotForMeal(selectedSlot.mealType)" class="compact-decision"><span aria-hidden="true">✓</span><strong>{{ SLOT_LABELS[selectedDessert.slotType] }} · {{ selectedDessert.title }}</strong><button class="text-button" type="button" @click="reselectPlanner(selectedDessert.slotType)">변경</button><button class="text-button" type="button" @click="clearPlanner(selectedDessert.slotType)">삭제</button></div>
        <template v-else><h2 tabindex="-1">식후에 잠깐 쉬어갈까요?</h2><div class="next-actions"><button class="action-button button-secondary" type="button" :disabled="Boolean(trips.actionLoading)" @click="recommendDessert">디저트 추천 보기</button><button class="action-button button-secondary" type="button" @click="skipDessert">건너뛰기</button></div></template>
        <template v-if="dessertResult && !skippedDessert"><RecommendationContextBanner :context="dessertResult.recommendationContext" /><p v-if="dessertExplorationMode" class="result-notice">{{ dessertExplorationMode === 'SEARCH' ? '검색어와 일치하는 디저트를 먼저 보여드려요.' : '선택한 식사 장소에서 가까운 순서로 더 둘러보세요.' }}</p><p class="result-notice">{{ scarcityMessage(dessertResult.candidates.length, '디저트') }} 재료와 교차조리는 방문 전 확인해 주세요.</p>
        <div class="candidate-grid"><article v-for="candidate in dessertResult.candidates || []" :key="candidate.contentId" class="surface live-card compact-live-card"><KtoImage :src="candidate.imageUrl" :alt="imageAlt(candidate.title)" /><div class="live-card-body"><h3>{{ candidate.title }}</h3><p class="address">{{ candidate.address }}</p><p v-if="candidate.distanceMeters != null">식사 장소에서 직선거리 {{ candidate.distanceMeters < 1000 ? `${candidate.distanceMeters}m` : `${(candidate.distanceMeters / 1000).toFixed(1)}km` }}</p><p>{{ (candidate.reasons || candidate.fitReasons || []).join(' ') }}</p><p v-if="candidate.telephone">전화 {{ candidate.telephone }}</p><p>{{ (candidate.cautions || candidate.checkBeforeVisit || []).join(' ') }}</p><a class="text-button" :href="candidateDirections(candidate, dessertResult).href" target="_blank" rel="noopener noreferrer">{{ candidateDirections(candidate, dessertResult).label }}</a><a class="text-button" :href="kakaoSearchUrl(candidate.title)" target="_blank" rel="noopener noreferrer">카카오맵에서 검색</a><p class="source-line">{{ candidate.sourceAttribution }}</p><button class="action-button button-primary" type="button" :disabled="Boolean(trips.actionLoading)" @click="selectDessert(candidate)">이 디저트 선택</button></div></article></div>
        <div class="alternative-actions"><button class="action-button button-secondary" type="button" @click="otherDesserts">다른 디저트 보기</button><form class="focus-search" @submit.prevent="searchDesserts"><label class="wide-field">디저트 직접 찾기<input v-model.trim="dessertKeyword" maxlength="100" placeholder="카페나 디저트 이름" /></label><button class="action-button button-primary" type="submit" :disabled="!dessertKeyword.trim()">찾기</button></form><a v-if="!dessertResult.candidates.length" class="action-button button-secondary" :href="kakaoSearchUrl('카페 디저트')" target="_blank" rel="noopener noreferrer">카카오맵에서 주변 디저트 찾아보기</a></div></template>
      </section>

    </template>
  </div>
</template>
