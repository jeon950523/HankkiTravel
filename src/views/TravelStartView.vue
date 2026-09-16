<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import StatusMessage from '../components/StatusMessage.vue'
import { useGuestStore } from '../stores/guest'
import { useProfilesStore } from '../stores/profiles'
import { useTripsStore } from '../stores/trips'
import { addDays, buildTripPayload, durationDays, MEAL_LABELS, MEAL_TYPES, validateTripDraft } from '../domain/trip'

const router = useRouter()
const guest = useGuestStore()
const profiles = useProfilesStore()
const trips = useTripsStore()
const step = ref(1)
const setupError = ref('')
const today = new Date().toLocaleDateString('sv-SE')
const form = reactive({
  profileId: '', regionKey: '', startDate: today, endDate: today, durationMode: 'DAY',
  days: Array.from({ length: 4 }, (_, index) => ({ dayNumber: index + 1, mealTypes: index === 0 ? ['LUNCH'] : [] })),
})
const duration = computed(() => durationDays(form.startDate, form.endDate))
const selectedProfile = computed(() => profiles.items.find(item => item.profileId === Number(form.profileId)))
const regionName = computed(() => ({ JEJU: '제주', GYEONGJU: '경주' }[form.regionKey] || '미선택'))
const totalMeals = computed(() => form.days.slice(0, duration.value).reduce((sum, day) => sum + day.mealTypes.length, 0))

watch(() => form.durationMode, mode => {
  if (mode === 'DAY') form.endDate = form.startDate
  if (mode === 'NIGHT') form.endDate = addDays(form.startDate, 1)
})
watch(() => form.startDate, value => {
  if (form.durationMode === 'DAY') form.endDate = value
  if (form.durationMode === 'NIGHT') form.endDate = addDays(value, 1)
})

function stepError() {
  if (step.value === 1 && !form.profileId) return '여행에 사용할 가족 프로필을 골라주세요.'
  if (step.value === 2 && !form.regionKey) return '제주 또는 경주를 골라주세요.'
  if (step.value === 3 && (duration.value < 1 || duration.value > 4)) return '여행 기간은 최대 3박 4일까지 선택할 수 있어요.'
  if (step.value === 4 && totalMeals.value < 1) return '여행 전체에서 식사 시간을 하나 이상 골라주세요.'
  return ''
}
function next() { setupError.value = stepError(); if (!setupError.value) step.value = Math.min(5, step.value + 1) }
const previous = () => { setupError.value = ''; step.value = Math.max(1, step.value - 1) }
async function submit() {
  setupError.value = validateTripDraft(form)
  if (setupError.value) return
  try {
    const created = await trips.create(await guest.ensureGuest(), buildTripPayload(form))
    await router.push(`/travel/${created.tripPublicId}`)
  } catch (error) { setupError.value = error.message || '여행을 만들지 못했어요.' }
}
onMounted(async () => {
  try {
    let guestPublicId = await guest.ensureGuest()
    let loaded
    try { loaded = await profiles.load(guestPublicId) }
    catch (error) {
      if (error.status !== 404) throw error
      guest.reset()
      guestPublicId = await guest.ensureGuest()
      loaded = await profiles.load(guestPublicId)
    }
    if (loaded[0]) form.profileId = String(profiles.selectedProfileId || loaded[0].profileId)
  } catch { setupError.value = '가족 프로필을 불러오지 못했어요. 프로필 화면에서 다시 시도해 주세요.' }
})
</script>

<template>
  <div class="content-page wizard-page">
    <p class="eyebrow">여행 만들기 · STEP {{ step }} / 5</p>
    <h1 tabindex="-1">한 끼를 중심으로<br />여행을 시작해요.</h1>
    <ol class="wizard-progress" aria-label="여행 만들기 진행 단계"><li v-for="item in 5" :key="item" :class="{ active: item === step, done: item < step }"><span>{{ item }}</span></li></ol>
    <form class="surface wizard-panel" @submit.prevent="step === 5 ? submit() : next()">
      <section v-if="step === 1" aria-labelledby="wizard-profile"><h2 id="wizard-profile">가족 프로필</h2><p class="input-note">누구와 떠나는지 먼저 골라주세요.</p><div v-if="profiles.items.length" class="wizard-options"><label v-for="profile in profiles.items" :key="profile.profileId" class="wizard-choice" :class="{ selected: Number(form.profileId) === profile.profileId }"><input v-model="form.profileId" type="radio" :value="String(profile.profileId)" /><strong>{{ profile.name }}</strong><span>{{ profile.members.map(member => member.nickname).join(' · ') }}</span></label></div><RouterLink v-else to="/profiles/new" class="action-button button-secondary">가족 프로필 만들기</RouterLink></section>
      <section v-else-if="step === 2" aria-labelledby="wizard-region"><h2 id="wizard-region">여행 지역</h2><p class="input-note">현재 라이브 추천을 제공하는 지역이에요.</p><div class="wizard-options two"><label class="wizard-choice" :class="{ selected: form.regionKey === 'JEJU' }"><input v-model="form.regionKey" type="radio" value="JEJU" /><strong>제주</strong><span>바다 곁에서 만나는 한 끼</span></label><label class="wizard-choice" :class="{ selected: form.regionKey === 'GYEONGJU' }"><input v-model="form.regionKey" type="radio" value="GYEONGJU" /><strong>경주</strong><span>오래된 풍경 곁의 한 끼</span></label></div></section>
      <section v-else-if="step === 3" aria-labelledby="wizard-date"><h2 id="wizard-date">여행 날짜</h2><div class="compact-choices date-modes"><label><input v-model="form.durationMode" type="radio" value="DAY" />당일</label><label><input v-model="form.durationMode" type="radio" value="NIGHT" />1박 2일</label><label><input v-model="form.durationMode" type="radio" value="CUSTOM" />직접 선택</label></div><div class="form-grid travel-fields"><label>시작일<input v-model="form.startDate" required type="date" /></label><label>종료일<input v-model="form.endDate" required type="date" :min="form.startDate" :max="addDays(form.startDate, 3)" :disabled="form.durationMode !== 'CUSTOM'" /></label></div><p class="selection-summary">{{ duration > 0 ? `${duration - 1}박 ${duration}일` : '날짜를 확인해 주세요' }}</p></section>
      <section v-else-if="step === 4" aria-labelledby="wizard-meals"><h2 id="wizard-meals">식사 시간</h2><p class="input-note">Day별로 0~3개, 여행 전체에서 하나 이상 골라주세요.</p><div class="meal-day-list"><article v-for="day in form.days.slice(0, duration)" :key="day.dayNumber"><h3>DAY {{ day.dayNumber }}</h3><div class="compact-choices"><label v-for="meal in MEAL_TYPES" :key="meal"><input v-model="day.mealTypes" type="checkbox" :value="meal" />{{ MEAL_LABELS[meal] }}</label></div></article></div></section>
      <section v-else aria-labelledby="wizard-confirm"><h2 id="wizard-confirm">확인 후 생성</h2><dl class="trip-summary"><div><dt>가족 프로필</dt><dd>{{ selectedProfile?.name }}</dd></div><div><dt>지역</dt><dd>{{ regionName }}</dd></div><div><dt>기간</dt><dd>{{ form.startDate }} ~ {{ form.endDate }}</dd></div><div><dt>식사 시간</dt><dd>{{ totalMeals }}개</dd></div></dl><p class="input-note">관광정보는 선택하는 순간 Local Backend가 TourAPI Live로 확인해요.</p></section>
      <StatusMessage v-if="setupError" kind="error" title="입력 내용을 확인해 주세요">{{ setupError }}</StatusMessage>
      <div class="wizard-actions"><button v-if="step > 1" class="action-button button-secondary" type="button" @click="previous">이전</button><button class="action-button button-primary" type="submit" :disabled="Boolean(trips.actionLoading) || profiles.loading">{{ trips.actionLoading === 'create' ? '여행 생성 중…' : step === 5 ? '여행 만들기' : '다음' }}</button></div>
    </form>
  </div>
</template>
