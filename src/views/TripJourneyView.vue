<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import KakaoDayMap from '../components/KakaoDayMap.vue'
import KtoImage from '../components/KtoImage.vue'
import StatusMessage from '../components/StatusMessage.vue'
import { activitySlotForMeal, MEAL_LABELS, showStayForDay, SLOT_LABELS, transitSummary } from '../domain/trip'
import { buildPlannerDisplayItems, carRouteNotice } from '../domain/plannerMap'
import { useGuestStore } from '../stores/guest'
import { useProfilesStore } from '../stores/profiles'
import { useTripsStore } from '../stores/trips'

const route = useRoute()
const guest = useGuestStore()
const profiles = useProfilesStore()
const trips = useTripsStore()
const guestId = ref('')
const dayNumber = ref(1)
const selectedSlotId = ref('')
const activePlaceType = ref('')
const focusKeyword = ref('')
const selectedPlaces = ref({})
const activePlannerItemId = ref('')
const imageAlt = title => `${title} 관광정보 이미지`
const tripId = computed(() => String(route.params.tripPublicId || ''))
const day = computed(() => trips.detail?.days.find(item => item.dayNumber === dayNumber.value))
const selectedSlot = computed(() => day.value?.mealSlots.find(item => item.mealSlotPublicId === selectedSlotId.value) || day.value?.mealSlots[0])
const mealResult = computed(() => selectedSlot.value ? trips.restaurantResults[`${tripId.value}:${selectedSlot.value.mealSlotPublicId}`] : null)
const dayKey = computed(() => `${tripId.value}:${dayNumber.value}`)
const focusResult = computed(() => trips.focusResults[dayKey.value])
const selectedFocus = computed(() => trips.selectedFocus[dayKey.value] || planner.value?.items?.find(item => item.slotType === 'DAY_FOCUS'))
const placeResult = computed(() => activePlaceType.value ? trips.placeResults[`${tripId.value}:${dayNumber.value}:${activePlaceType.value}`] : null)
const planner = computed(() => trips.planners[`${tripId.value}:${dayNumber.value}`])
const plannerItems = computed(() => buildPlannerDisplayItems(planner.value?.items || []))
const plannerLoading = computed(() => Boolean(trips.plannerLoading[dayKey.value]))
const plannerError = computed(() => trips.plannerErrors[dayKey.value])
const regionName = computed(() => ({ JEJU: '제주', GYEONGJU: '경주' }[trips.detail?.regionKey] || ''))
const profile = computed(() => profiles.items.find(item => item.profileId === trips.detail?.profileId))
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
const telephoneHref = value => `tel:${String(value || '').replace(/[^0-9+]/g, '')}`
const perspectiveLabel = value => ({ BALANCED: '균형 있게', MEAL_PRIORITY: '한 끼 우선', MOBILITY_PRIORITY: '이동 편의 우선' }[value] || value)
const selectionKey = type => `${dayNumber.value}:${type}`
watch(day, async value => {
  selectedSlotId.value = value?.mealSlots[0]?.mealSlotPublicId || ''
  activePlaceType.value = ''
  activePlannerItemId.value = ''
  if (guestId.value && value) await loadPlanner()
})
async function recommendFocus() { await trips.recommendFocus(guestId.value, tripId.value, dayNumber.value).catch(() => {}) }
async function searchFocus() { if (focusKeyword.value.trim()) await trips.searchFocus(guestId.value, tripId.value, dayNumber.value, focusKeyword.value).catch(() => {}) }
async function selectFocus(candidate) { const selected = await trips.selectFocus(guestId.value, tripId.value, dayNumber.value, candidate).catch(() => null); if (selected) await loadPlanner() }
async function clearFocus() { await trips.clearFocus(guestId.value, tripId.value, dayNumber.value).catch(() => {}) }
async function recommendMeal() { if (selectedSlot.value) await trips.recommendMeal(guestId.value, tripId.value, selectedSlot.value.mealSlotPublicId).catch(() => {}) }
async function selectMeal(contentId) { const selected = await trips.selectMeal(guestId.value, tripId.value, selectedSlot.value.mealSlotPublicId, contentId).catch(() => null); if (selected) await loadPlanner() }
async function recommendPlace(type) {
  activePlaceType.value = type
  const action = type === 'STAY' ? trips.recommendStay(guestId.value, tripId.value, dayNumber.value) : trips.recommendPlace(guestId.value, tripId.value, dayNumber.value, type)
  await action.catch(() => {})
}
async function selectPlace(candidate) {
  const type = activePlaceType.value
  const selected = await trips.selectPlace(guestId.value, tripId.value, dayNumber.value, type, candidate.contentId).catch(() => null)
  if (selected) { selectedPlaces.value[selectionKey(type)] = candidate; await loadPlanner() }
}
async function loadPlanner() { await trips.loadPlanner(guestId.value, tripId.value, dayNumber.value).catch(() => {}) }
async function activatePlannerItem(id, scroll = false) {
  activePlannerItemId.value = id
  if (scroll) {
    await nextTick()
    document.getElementById(`planner-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }
}
onMounted(async () => {
  try {
    guestId.value = await guest.ensureGuest()
    await Promise.all([trips.load(guestId.value, tripId.value), profiles.load(guestId.value)])
    selectedSlotId.value = trips.detail?.days[0]?.mealSlots[0]?.mealSlotPublicId || ''
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
      <nav class="day-switcher day-summary-switcher" aria-label="여행 날짜"><button v-for="item in daySummaries" :key="item.dayNumber" type="button" :class="{ active: item.dayNumber === dayNumber }" @click="dayNumber = item.dayNumber"><strong>DAY {{ item.dayNumber }}</strong><span>{{ formatDate(item.travelDate) }}</span><small>{{ item.focusSelected ? '중심 장소 선택됨' : '중심 장소 미선택' }} · 식사 {{ item.selectedMeals }}/{{ item.mealSlots.length }}</small></button></nav>

      <section class="planner-section trip-planner" aria-labelledby="planner-title"><div class="planner-heading"><div><p class="eyebrow">DAY {{ dayNumber }} · {{ formatDate(day?.travelDate) }}</p><h2 id="planner-title">지도와 오늘의 일정</h2></div><button class="text-button" type="button" :disabled="plannerLoading" @click="loadPlanner">일정 새로고침</button></div>
        <p v-if="carNotice" class="car-route-notice">{{ carNotice }}</p>
        <div v-if="plannerLoading && !planner" class="planner-loading-grid"><div class="map-skeleton" /><div class="timeline-skeleton" /></div>
        <StatusMessage v-else-if="plannerError && !planner" kind="error" title="오늘 일정을 불러오지 못했어요">장소 선택은 그대로 유지돼요. 잠시 후 일정 새로고침을 눌러주세요.</StatusMessage>
        <div v-else class="planner-layout">
          <KakaoDayMap :key="dayNumber" :items="plannerItems" :active-id="activePlannerItemId" @select="activatePlannerItem($event, true)" />
          <div class="timeline-panel"><p v-if="!plannerItems.length" class="empty-inline">아직 선택한 장소가 없어요. 식당이나 관광지를 먼저 골라주세요.</p><ol v-else class="timeline"><li v-for="(item, index) in plannerItems" :id="`planner-${item.plannerItemId}`" :key="item.plannerItemId"><article class="surface planner-card" :class="{ active: item.plannerItemId === activePlannerItemId }" role="button" tabindex="0" @click="activatePlannerItem(item.plannerItemId)" @keydown.enter="activatePlannerItem(item.plannerItemId)" @keydown.space.prevent="activatePlannerItem(item.plannerItemId)"><span class="timeline-number">{{ item.displayIndex }}</span><KtoImage :src="item.imageUrl" :alt="imageAlt(item.title)" /><div><p class="card-kicker">{{ SLOT_LABELS[item.slotType] }}</p><h3>{{ item.title || '현재 정보를 확인할 수 없는 장소' }}</h3><p class="address">{{ item.address }}</p><p class="source-line">{{ item.sourceAttribution }}</p><p v-if="!item.mapPoint" class="map-missing-note">좌표가 없어 지도 마커만 생략했어요.</p></div></article><div v-if="planner?.legs[index]" class="planner-leg">{{ transitSummary(planner.legs[index]) }}</div></li></ol></div>
        </div>
      </section>

      <section class="surface decision-section" aria-labelledby="focus-decision"><p class="step-label">첫 번째 결정</p><h2 id="focus-decision">오늘 어디를 중심으로 여행할까요?</h2><p class="result-notice">DAY {{ dayNumber }} 식당과 일정을 고를 때 가장 먼저 참고할 장소예요.</p>
        <div v-if="selectedFocus" class="focus-selection"><strong>{{ selectedFocus.title }}</strong><span>{{ selectedFocus.areaLabel || selectedFocus.address }}</span><button type="button" class="text-button" @click="clearFocus">다시 고르기</button></div>
        <template v-else><div class="focus-actions"><button class="action-button button-secondary" type="button" :disabled="Boolean(trips.actionLoading)" @click="recommendFocus">추천받기</button><form class="focus-search" @submit.prevent="searchFocus"><label class="wide-field">가고 싶은 곳 찾기<input v-model.trim="focusKeyword" maxlength="100" placeholder="예: 성산일출봉, 첨성대" /></label><button class="action-button button-primary" type="submit" :disabled="!focusKeyword.trim() || Boolean(trips.actionLoading)">찾기</button></form></div></template>
      </section>

      <section v-if="focusResult && !selectedFocus" class="decision-results" aria-labelledby="focus-result"><p class="eyebrow">현재 TourAPI Live</p><h2 id="focus-result">오늘의 중심 장소</h2><p v-if="!focusResult.candidates.length" class="empty-inline">현재 확인할 수 있는 장소가 없어요.</p><article v-for="candidate in focusResult.candidates" :key="candidate.contentId" class="surface live-card"><KtoImage :src="candidate.imageUrl" :alt="imageAlt(candidate.title)" /><div class="live-card-body"><p class="card-kicker">{{ candidate.areaLabel || regionName }}</p><h3>{{ candidate.title }}</h3><p class="address">{{ candidate.address }}</p><p>관광 유형 {{ candidate.contentType }}</p><p class="source-line">{{ candidate.sourceAttribution }}</p><button class="action-button button-primary" type="button" @click="selectFocus(candidate)">이곳을 중심 장소로 선택</button></div></article></section>

      <section class="surface decision-section" aria-labelledby="meal-decision"><p class="step-label">두 번째 결정</p><h2 id="meal-decision">DAY {{ dayNumber }} 식당 선택</h2>
        <div v-if="day?.mealSlots?.length" class="slot-switcher"><button v-for="slot in day.mealSlots" :key="slot.mealSlotPublicId" type="button" :class="{ active: slot.mealSlotPublicId === selectedSlot?.mealSlotPublicId }" @click="selectedSlotId = slot.mealSlotPublicId">{{ MEAL_LABELS[slot.mealType] }}<span>{{ slot.anchor ? '선택 완료' : '선택 전' }}</span></button></div><p v-else class="empty-inline">이 Day에는 선택한 식사 시간이 없어요.</p>
        <p v-if="!selectedFocus" class="empty-inline">오늘의 중심 장소를 먼저 골라주세요.</p><button v-if="selectedSlot" class="action-button button-primary" type="button" :disabled="Boolean(trips.actionLoading) || !selectedFocus" @click="recommendMeal">{{ trips.actionLoading.startsWith('meal:') ? '가족 조건과 현재 관광정보를 함께 확인하고 있어요' : `${MEAL_LABELS[selectedSlot.mealType]} TOP3 보기` }}</button>
      </section>

      <StatusMessage v-if="trips.error" kind="error" title="현재 요청을 마치지 못했어요">{{ trips.error }}</StatusMessage>
      <section v-if="mealResult" class="decision-results" aria-labelledby="meal-result"><p class="eyebrow">현재 TourAPI Live</p><h2 id="meal-result">식당 추천</h2><p class="result-notice">{{ mealResult.nutritionNotice }}</p><p v-if="!candidates.length" class="empty-inline">현재 조건으로 비교할 추천 후보가 없어요.</p>
        <article v-for="candidate in candidates" :key="candidate.contentId" class="surface live-card"><KtoImage :src="candidate.imageUrl" :alt="imageAlt(candidate.title)" /><div class="live-card-body"><p class="card-kicker">{{ candidate.areaLabel || perspectiveLabel(candidate.perspective) }}</p><h3>{{ candidate.title }}</h3><p class="address">{{ candidate.address }}</p><dl class="facts"><div><dt>여행·식사 적합도</dt><dd>{{ candidate.overallScore ?? candidate.compatibilityScore }}점</dd></div><div><dt>정보 근거 커버리지</dt><dd>{{ candidate.evidenceCoverage ?? candidate.evaluatedWeight }}%</dd></div></dl><ul class="dimension-list"><li v-for="dimension in candidate.evaluatedDimensions" :key="dimension.code || dimension.dimension"><span>{{ dimension.label || dimension.dimension }}</span><strong>{{ dimension.evaluated === false || dimension.evidenceState === 'NOT_EVALUATED' ? '미평가' : `${dimension.awardedPoints ?? dimension.score} / ${dimension.maxPoints ?? dimension.weight}` }}</strong></li></ul><div class="reason-grid"><div><h4>우리 가족과 잘 맞는 점</h4><p>{{ candidate.ourFamilyFitReasons.join(' ') || '현재 확인된 근거를 살펴봐 주세요.' }}</p></div><div><h4>우리 가족이 주의할 점</h4><p>{{ candidate.ourFamilyCautions.join(' ') || '공개 정보에서 별도 근거를 확인하지 못했어요.' }}</p></div><div><h4>방문 전에 같이 확인하면 좋은 점</h4><p>{{ candidate.checkBeforeVisit.join(' ') }}</p></div></div><p v-if="candidate.nutritionEvidence.length" class="nutrition-line">{{ candidate.nutritionEvidence.map(item => `${item.menuName} · ${item.referenceLabel}`).join(' / ') }}</p><div class="contact-actions"><a v-if="candidate.phone" class="action-button button-secondary" :href="telephoneHref(candidate.phone)">전화로 확인하기</a><span v-else class="empty-inline">전화 정보가 공개되어 있지 않아요</span><a v-if="candidate.placeUrl" class="action-button button-secondary" :href="candidate.placeUrl" target="_blank" rel="noopener noreferrer">지도·후기 보기</a></div><p class="source-line">{{ mealResult.sourceAttribution }}</p><button class="action-button button-primary" type="button" :disabled="Boolean(trips.actionLoading)" @click="selectMeal(candidate.contentId)">{{ selectedSlot.anchor?.contentId === candidate.contentId ? '선택 완료' : '이 식당 선택' }}</button></div></article>
      </section>

      <section v-if="selectedSlot?.anchor" class="surface next-decisions" aria-labelledby="next-title"><p class="step-label">다음 결정</p><h2 id="next-title">식사와 하루를 이어볼까요?</h2><div class="next-actions"><button class="action-button button-secondary" type="button" :disabled="Boolean(trips.actionLoading)" @click="recommendPlace(activitySlotForMeal(selectedSlot.mealType))">관광지 추천 보기</button><button v-if="canStay" class="action-button button-secondary" type="button" :disabled="Boolean(trips.actionLoading)" @click="recommendPlace('STAY')">숙소 추천 보기</button><button class="action-button button-primary" type="button" :disabled="Boolean(trips.actionLoading)" @click="loadPlanner">오늘 일정 보기</button></div></section>

      <section v-if="placeResult" class="decision-results" aria-labelledby="place-result"><p class="eyebrow">{{ SLOT_LABELS[activePlaceType] }} · TourAPI Live</p><h2 id="place-result">{{ activePlaceType === 'STAY' ? '숙소' : '관광지' }} 추천</h2><p v-if="!placeResult.candidates.length" class="empty-inline">현재 확인할 수 있는 후보가 없어요.</p><article v-for="candidate in placeResult.candidates" :key="candidate.contentId" class="surface live-card"><KtoImage :src="candidate.imageUrl" :alt="imageAlt(candidate.title)" /><div class="live-card-body"><h3>{{ candidate.title }}</h3><p class="address">{{ candidate.address }}</p><div class="reason-grid"><div><h4>{{ activePlaceType === 'STAY' ? '현재 일정과의 연결 근거' : '일정과 잘 맞는 점' }}</h4><p>{{ candidate.fitReasons.join(' ') }}</p></div><div><h4>확인할 점</h4><p>{{ candidate.checkBeforeVisit.join(' ') }}</p></div></div><p class="source-line">{{ candidate.sourceAttribution }}</p><button class="action-button button-primary" type="button" :disabled="Boolean(trips.actionLoading)" @click="selectPlace(candidate)">{{ selectedPlaces[selectionKey(activePlaceType)]?.contentId === candidate.contentId ? '선택 완료' : `이 ${activePlaceType === 'STAY' ? '숙소' : '관광지'} 선택` }}</button></div></article></section>

    </template>
  </div>
</template>
