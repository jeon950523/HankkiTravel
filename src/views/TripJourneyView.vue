<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import KtoImage from '../components/KtoImage.vue'
import StatusMessage from '../components/StatusMessage.vue'
import { activitySlotForMeal, MEAL_LABELS, showStayForDay, SLOT_LABELS, transitSummary } from '../domain/trip'
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
const selectedPlaces = ref({})
const imageAlt = title => `${title} 관광정보 이미지`
const tripId = computed(() => String(route.params.tripPublicId || ''))
const day = computed(() => trips.detail?.days.find(item => item.dayNumber === dayNumber.value))
const selectedSlot = computed(() => day.value?.mealSlots.find(item => item.mealSlotPublicId === selectedSlotId.value) || day.value?.mealSlots[0])
const mealResult = computed(() => selectedSlot.value ? trips.restaurantResults[`${tripId.value}:${selectedSlot.value.mealSlotPublicId}`] : null)
const placeResult = computed(() => activePlaceType.value ? trips.placeResults[`${tripId.value}:${dayNumber.value}:${activePlaceType.value}`] : null)
const planner = computed(() => trips.planners[`${tripId.value}:${dayNumber.value}`])
const regionName = computed(() => ({ JEJU: '제주', GYEONGJU: '경주' }[trips.detail?.regionKey] || ''))
const profileName = computed(() => profiles.items.find(item => item.profileId === trips.detail?.profileId)?.name || '가족 프로필')
const canStay = computed(() => showStayForDay(dayNumber.value, trips.detail?.durationDays || 0))
const candidates = computed(() => {
  const seen = new Set()
  return (mealResult.value?.perspectives || [])
    .flatMap(perspective => perspective.candidates.map(candidate => ({ ...candidate, perspective: perspective.perspective })))
    .filter(candidate => !seen.has(candidate.contentId) && seen.add(candidate.contentId))
})
const perspectiveLabel = value => ({ BALANCED: '균형 있게', MEAL_PRIORITY: '한 끼 우선', MOBILITY_PRIORITY: '이동 편의 우선' }[value] || value)
const selectionKey = type => `${dayNumber.value}:${type}`
watch(day, value => { selectedSlotId.value = value?.mealSlots[0]?.mealSlotPublicId || ''; activePlaceType.value = '' })
async function recommendMeal() { if (selectedSlot.value) await trips.recommendMeal(guestId.value, tripId.value, selectedSlot.value.mealSlotPublicId).catch(() => {}) }
async function selectMeal(contentId) { await trips.selectMeal(guestId.value, tripId.value, selectedSlot.value.mealSlotPublicId, contentId).catch(() => {}) }
async function recommendPlace(type) {
  activePlaceType.value = type
  const action = type === 'STAY' ? trips.recommendStay(guestId.value, tripId.value, dayNumber.value) : trips.recommendPlace(guestId.value, tripId.value, dayNumber.value, type)
  await action.catch(() => {})
}
async function selectPlace(candidate) {
  const type = activePlaceType.value
  const selected = await trips.selectPlace(guestId.value, tripId.value, dayNumber.value, type, candidate.contentId).catch(() => null)
  if (selected) selectedPlaces.value[selectionKey(type)] = candidate
}
async function loadPlanner() { await trips.loadPlanner(guestId.value, tripId.value, dayNumber.value).catch(() => {}) }
onMounted(async () => {
  try {
    guestId.value = await guest.ensureGuest()
    await Promise.all([trips.load(guestId.value, tripId.value), profiles.load(guestId.value)])
    selectedSlotId.value = trips.detail?.days[0]?.mealSlots[0]?.mealSlotPublicId || ''
  } catch { /* store error is rendered */ }
})
</script>

<template>
  <div class="content-page journey-page">
    <StatusMessage v-if="trips.loading" kind="loading" title="여행을 불러오고 있어요">저장한 일정 구조만 먼저 확인하고 있어요.</StatusMessage>
    <StatusMessage v-else-if="!trips.detail" kind="error" title="여행을 불러오지 못했어요">{{ trips.error }}</StatusMessage>
    <template v-else>
      <p class="eyebrow">{{ regionName }} · {{ trips.detail.durationDays - 1 }}박 {{ trips.detail.durationDays }}일</p><h1>{{ profileName }}의<br />한 끼 여행</h1>
      <p class="page-description">{{ trips.detail.startDate }} ~ {{ trips.detail.endDate }} · 현재 DAY {{ dayNumber }}만 살펴보고 있어요.</p>
      <nav class="day-switcher" aria-label="여행 날짜"><button v-for="item in trips.detail.days" :key="item.dayNumber" type="button" :class="{ active: item.dayNumber === dayNumber }" @click="dayNumber = item.dayNumber">DAY {{ item.dayNumber }}</button></nav>

      <section class="surface decision-section" aria-labelledby="meal-decision"><p class="step-label">현재 결정</p><h2 id="meal-decision">DAY {{ dayNumber }} 식당 선택</h2>
        <div v-if="day?.mealSlots?.length" class="slot-switcher"><button v-for="slot in day.mealSlots" :key="slot.mealSlotPublicId" type="button" :class="{ active: slot.mealSlotPublicId === selectedSlot?.mealSlotPublicId }" @click="selectedSlotId = slot.mealSlotPublicId">{{ MEAL_LABELS[slot.mealType] }}<span>{{ slot.anchor ? '선택 완료' : '선택 전' }}</span></button></div><p v-else class="empty-inline">이 Day에는 선택한 식사 시간이 없어요.</p>
        <button v-if="selectedSlot" class="action-button button-primary" type="button" :disabled="Boolean(trips.actionLoading)" @click="recommendMeal">{{ trips.actionLoading.startsWith('meal:') ? '가족 조건과 현재 관광정보를 함께 확인하고 있어요' : `${MEAL_LABELS[selectedSlot.mealType]} TOP3 보기` }}</button>
      </section>

      <StatusMessage v-if="trips.error" kind="error" title="현재 요청을 마치지 못했어요">{{ trips.error }}</StatusMessage>
      <section v-if="mealResult" class="decision-results" aria-labelledby="meal-result"><p class="eyebrow">현재 TourAPI Live</p><h2 id="meal-result">식당 추천</h2><p class="result-notice">{{ mealResult.nutritionNotice }}</p><p v-if="!candidates.length" class="empty-inline">현재 조건으로 비교할 추천 후보가 없어요.</p>
        <article v-for="candidate in candidates" :key="candidate.contentId" class="surface live-card"><KtoImage :src="candidate.imageUrl" :alt="imageAlt(candidate.title)" /><div class="live-card-body"><p class="card-kicker">{{ perspectiveLabel(candidate.perspective) }}</p><h3>{{ candidate.title }}</h3><p class="address">{{ candidate.address }}</p><dl class="facts"><div><dt>동행 적합도</dt><dd>{{ candidate.compatibilityScore }}</dd></div><div><dt>정보 근거</dt><dd>{{ candidate.informationEvidence }}</dd></div></dl><div class="reason-grid"><div><h4>우리 가족과 잘 맞는 점</h4><p>{{ candidate.ourFamilyFitReasons.join(' ') || '현재 확인된 근거를 살펴봐 주세요.' }}</p></div><div><h4>우리 가족이 주의할 점</h4><p>{{ candidate.ourFamilyCautions.join(' ') || '공개 정보에서 별도 근거를 확인하지 못했어요.' }}</p></div><div><h4>방문 전에 같이 확인하면 좋은 점</h4><p>{{ candidate.checkBeforeVisit.join(' ') }}</p></div></div><p v-if="candidate.nutritionEvidence.length" class="nutrition-line">{{ candidate.nutritionEvidence.map(item => `${item.menuName} · ${item.referenceLabel}`).join(' / ') }}</p><p class="source-line">{{ mealResult.sourceAttribution }}</p><button class="action-button button-primary" type="button" :disabled="Boolean(trips.actionLoading)" @click="selectMeal(candidate.contentId)">{{ selectedSlot.anchor?.contentId === candidate.contentId ? '선택 완료' : '이 식당 선택' }}</button></div></article>
      </section>

      <section v-if="selectedSlot?.anchor" class="surface next-decisions" aria-labelledby="next-title"><p class="step-label">다음 결정</p><h2 id="next-title">식사와 하루를 이어볼까요?</h2><div class="next-actions"><button class="action-button button-secondary" type="button" :disabled="Boolean(trips.actionLoading)" @click="recommendPlace(activitySlotForMeal(selectedSlot.mealType))">관광지 추천 보기</button><button v-if="canStay" class="action-button button-secondary" type="button" :disabled="Boolean(trips.actionLoading)" @click="recommendPlace('STAY')">숙소 추천 보기</button><button class="action-button button-primary" type="button" :disabled="Boolean(trips.actionLoading)" @click="loadPlanner">오늘 일정 보기</button></div></section>

      <section v-if="placeResult" class="decision-results" aria-labelledby="place-result"><p class="eyebrow">{{ SLOT_LABELS[activePlaceType] }} · TourAPI Live</p><h2 id="place-result">{{ activePlaceType === 'STAY' ? '숙소' : '관광지' }} 추천</h2><p v-if="!placeResult.candidates.length" class="empty-inline">현재 확인할 수 있는 후보가 없어요.</p><article v-for="candidate in placeResult.candidates" :key="candidate.contentId" class="surface live-card"><KtoImage :src="candidate.imageUrl" :alt="imageAlt(candidate.title)" /><div class="live-card-body"><h3>{{ candidate.title }}</h3><p class="address">{{ candidate.address }}</p><div class="reason-grid"><div><h4>{{ activePlaceType === 'STAY' ? '현재 일정과의 연결 근거' : '일정과 잘 맞는 점' }}</h4><p>{{ candidate.fitReasons.join(' ') }}</p></div><div><h4>확인할 점</h4><p>{{ candidate.checkBeforeVisit.join(' ') }}</p></div></div><p class="source-line">{{ candidate.sourceAttribution }}</p><button class="action-button button-primary" type="button" :disabled="Boolean(trips.actionLoading)" @click="selectPlace(candidate)">{{ selectedPlaces[selectionKey(activePlaceType)]?.contentId === candidate.contentId ? '선택 완료' : `이 ${activePlaceType === 'STAY' ? '숙소' : '관광지'} 선택` }}</button></div></article></section>

      <section v-if="planner" class="planner-section" aria-labelledby="planner-title"><p class="eyebrow">DAY {{ dayNumber }}</p><h2 id="planner-title">오늘의 일정</h2><p v-if="!planner.items.length" class="empty-inline">아직 선택한 장소가 없어요. 식당이나 관광지를 먼저 골라주세요.</p><ol class="timeline"><li v-for="(item, index) in planner.items" :key="`${item.slotType}:${item.contentId}`"><article class="surface planner-card"><KtoImage :src="item.imageUrl" :alt="imageAlt(item.title)" /><div><p class="card-kicker">{{ SLOT_LABELS[item.slotType] }}</p><h3>{{ item.title }}</h3><p class="address">{{ item.address }}</p><p class="source-line">{{ item.sourceAttribution }}</p></div></article><div v-if="planner.legs[index]" class="planner-leg">{{ transitSummary(planner.legs[index]) }}</div></li></ol></section>
    </template>
  </div>
</template>
