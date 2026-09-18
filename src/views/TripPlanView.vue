<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import KakaoDayMap from '../components/KakaoDayMap.vue'
import KtoImage from '../components/KtoImage.vue'
import StatusMessage from '../components/StatusMessage.vue'
import { buildPlannerDisplayItems, buildPlannerLegs } from '../domain/plannerMap'
import { SLOT_LABELS, showStayForDay, transitSummary } from '../domain/trip'
import { evaluateDayCompletion } from '../domain/progressivePlanner'
import { restaurantExternalActions } from '../domain/restaurantEvidence'
import { routeSanityLabel, routeSanitySummary } from '../domain/routeSanity'
import { useGuestStore } from '../stores/guest'
import { useProfilesStore } from '../stores/profiles'
import { useTripsStore } from '../stores/trips'

const route = useRoute()
const guest = useGuestStore()
const profiles = useProfilesStore()
const trips = useTripsStore()
const guestId = ref('')
const loading = ref(true)
const planHeading = ref(null)
const tripId = computed(() => String(route.params.tripPublicId || ''))
const regionName = computed(() => ({ JEJU: '제주', GYEONGJU: '경주' }[trips.detail?.regionKey] || ''))
const profile = computed(() => profiles.items.find(item => item.profileId === trips.detail?.profileId))
const profileName = computed(() => profile.value?.name || '가족 프로필')
const mealCount = computed(() => trips.detail?.days.reduce((sum, day) => sum + day.mealSlots.length, 0) || 0)
const selectedMealCount = computed(() => trips.detail?.days.reduce((sum, day) => sum + day.mealSlots.filter(slot => slot.anchor).length, 0) || 0)
const stayRequired = computed(() => Math.max(0, (trips.detail?.durationDays || 1) - 1))
const stayCompleted = computed(() => tripDays.value.filter(day => day.items.some(item => item.slotType === 'STAY')).length)
const warningCount = computed(() => tripDays.value.filter(day => ['CAUTION', 'HIGH'].includes(day.routeSanity?.severity)).length)
const formatDate = value => value ? new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric' }).format(new Date(`${value}T12:00:00`)) : ''
const tripDays = computed(() => (trips.detail?.days || []).map(day => {
  const planner = trips.planners[`${tripId.value}:${day.dayNumber}`] || {}
  const items = buildPlannerDisplayItems(planner.items || [])
  const derivedCompletion = evaluateDayCompletion({
    day,
    plannerItems: items,
    stayRequired: showStayForDay(day.dayNumber, trips.detail?.durationDays || 0),
  })
  const completion = planner.completion || day.completion || derivedCompletion
  return {
    ...day,
    ...planner,
    completion: { ...derivedCompletion, ...completion, isRequiredComplete: completion.requiredComplete ?? completion.isRequiredComplete ?? derivedCompletion.isRequiredComplete },
    items,
    displayLegs: buildPlannerLegs(items, planner.legs || []),
    loadError: trips.plannerErrors[`${tripId.value}:${day.dayNumber}`],
  }
}))
const tripComplete = computed(() => tripDays.value.length > 0 && tripDays.value.every(day => day.completion.isRequiredComplete))
const finalMovementEvidence = (day, index) => index === 0
  ? '이 Day의 첫 장소예요.'
  : `이전 장소에서 ${transitSummary(day.displayLegs[index - 1])}`
const finalActions = item => restaurantExternalActions({
  ...item,
  phone: item.telephone,
})

onMounted(async () => {
  try {
    guestId.value = await guest.ensureGuest()
    await Promise.all([trips.load(guestId.value, tripId.value), profiles.load(guestId.value)])
    for (const day of trips.detail?.days || []) await trips.loadPlanner(guestId.value, tripId.value, day.dayNumber)
  } finally {
    loading.value = false
    await nextTick()
    planHeading.value?.focus()
  }
})
</script>

<template>
  <main class="content-page final-plan-page">
    <StatusMessage v-if="loading" kind="loading" title="완성된 여행 플랜을 정리하고 있어요">DAY 순서대로 저장된 장소와 이동 근거를 확인하고 있어요.</StatusMessage>
    <StatusMessage v-else-if="!trips.detail" kind="error" title="여행 플랜을 불러오지 못했어요">{{ trips.error }}</StatusMessage>
    <section v-else-if="!tripComplete" class="surface plan-guard" aria-labelledby="plan-guard-title">
      <p class="eyebrow">여행 준비 중</p>
      <h1 id="plan-guard-title" ref="planHeading" tabindex="-1">아직 여행 준비가 끝나지 않았어요.</h1>
      <p>필수 식사와 숙소를 모두 고르면 완성된 여행 플랜을 볼 수 있어요.</p>
      <RouterLink class="action-button button-primary" :to="`/travel/${tripId}`">이어서 준비하기</RouterLink>
    </section>
    <template v-else>
      <header class="surface final-plan-header"><div><p class="eyebrow">완성된 여행 플랜</p><h1 ref="planHeading" tabindex="-1">나의 {{ regionName }} {{ trips.detail.durationDays - 1 }}박 {{ trips.detail.durationDays }}일 여행 플랜</h1><p>{{ formatDate(trips.detail.startDate) }} ~ {{ formatDate(trips.detail.endDate) }} · 가족 프로필 · {{ profileName }}</p></div><dl class="trip-overview-facts"><div><dt>DAY</dt><dd>{{ trips.detail.durationDays }}일</dd></div><div><dt>식사</dt><dd>{{ selectedMealCount }}/{{ mealCount }}</dd></div><div><dt>숙소</dt><dd>{{ stayCompleted }}/{{ stayRequired }}</dd></div><div><dt>이동 부담 경고</dt><dd>{{ warningCount }}</dd></div></dl><RouterLink class="action-button button-secondary" :to="`/travel/${tripId}`">여행 수정하기</RouterLink></header>

      <section v-for="day in tripDays" :key="day.dayNumber" class="surface final-plan-day" :aria-labelledby="`final-day-${day.dayNumber}`">
        <div class="planner-heading"><div><p class="eyebrow">{{ formatDate(day.travelDate) }}</p><h2 :id="`final-day-${day.dayNumber}`">DAY {{ day.dayNumber }}</h2></div><RouterLink class="text-button" :to="`/travel/${tripId}?day=${day.dayNumber}`">DAY {{ day.dayNumber }} 수정</RouterLink></div>
        <StatusMessage v-if="day.loadError" kind="error" :title="`DAY ${day.dayNumber} 일정 정보를 현재 다시 확인하지 못했어요.`">다른 Day의 일정은 그대로 볼 수 있어요. <button class="text-button" type="button" @click="trips.loadPlanner(guestId, tripId, day.dayNumber)">다시 시도</button></StatusMessage>
        <aside v-if="day.routeSanity" class="route-sanity" :data-severity="day.routeSanity.severity"><strong>이동 부담 · {{ routeSanityLabel(day.routeSanity.severity) }}</strong><span>{{ routeSanitySummary(day.routeSanity) }}</span></aside>
        <div class="planner-layout"><KakaoDayMap :items="day.items" :legs="day.displayLegs" /><div class="timeline-panel"><ol class="timeline"><li v-for="(item, index) in day.items" :key="item.plannerItemId"><article class="surface planner-card final-result-card"><span class="timeline-number">{{ item.displayIndex }}</span><KtoImage :src="item.imageUrl" :alt="`${item.title} 관광정보 이미지`" /><div><p class="card-kicker">{{ SLOT_LABELS[item.slotType] }}</p><h3>{{ item.title }}</h3><p class="address">{{ item.address }}</p><p class="final-movement-evidence">{{ finalMovementEvidence(day, index) }}</p><div class="contact-actions"><a v-for="action in finalActions(item)" :key="action.kind" class="action-button button-secondary" :href="action.href" :target="action.kind === 'phone' ? undefined : '_blank'" :rel="action.kind === 'phone' ? undefined : 'noopener noreferrer'">{{ action.label }}</a></div><p class="source-line">{{ item.sourceAttribution }}</p></div></article><div v-if="day.displayLegs[index]" class="planner-leg final-plan-leg"><strong>{{ day.displayLegs[index].displayFrom }} → {{ day.displayLegs[index].displayTo }}</strong><span>{{ transitSummary(day.displayLegs[index]) }}</span></div></li></ol></div></div>
      </section>
    </template>
  </main>
</template>
