<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import StatusMessage from '../components/StatusMessage.vue'
import { useGuestStore } from '../stores/guest'
import { useProfilesStore } from '../stores/profiles'
import { useRecommendationStore } from '../stores/recommendation'

const route = useRoute()
const guest = useGuestStore()
const profiles = useProfilesStore()
const recommendations = useRecommendationStore()
const startingPoint = computed(() => route.query.start === 'place' ? '가보고 싶은 곳에서 시작해요' : '먹고 싶은 한 끼에서 시작해요')
const startMode = computed(() => route.query.start === 'place' ? 'PLACE_FIRST' : 'MEAL_FIRST')
const today = new Date().toISOString().slice(0, 10)
const form = reactive({ region: '', tripDate: today, mealType: 'LUNCH', profileId: '', desiredLocalFood: '', strictExclusions: '', longitude: '', latitude: '' })
const setupError = ref('')
const perspectiveName = value => ({ BALANCED: '균형 잡힌 선택', MEAL_PRIORITY: '식사조건 우선', MOBILITY_PRIORITY: '이동편의 우선' }[value] || value)
const evidenceName = value => ({ SUFFICIENT: '충분', REFERENCE: '참고', LIMITED: '부족', CHECK_REQUIRED: '확인 필요' }[value] || value)
const submit = async () => {
  setupError.value = ''
  if (!form.profileId) { setupError.value = '추천에 사용할 가족 프로필을 골라주세요.'; return }
  if (startMode.value === 'PLACE_FIRST' && (!form.longitude || !form.latitude)) { setupError.value = '장소부터 시작할 때는 기준 장소 좌표를 입력해 주세요.'; return }
  try {
    await recommendations.request({
      guestPublicId: await guest.ensureGuest(), profileId: Number(form.profileId), region: form.region, tripDate: form.tripDate,
      mealType: form.mealType, startMode: startMode.value, desiredLocalFood: form.desiredLocalFood || null,
      strictExclusions: form.strictExclusions.split(',').map(value => value.trim()).filter(Boolean),
      anchor: startMode.value === 'PLACE_FIRST' ? { longitude: Number(form.longitude), latitude: Number(form.latitude) } : null,
    })
  } catch (error) { setupError.value = error.message || '추천을 준비하지 못했어요.' }
}
onMounted(async () => {
  try {
    const loaded = await profiles.load(await guest.ensureGuest())
    if (loaded[0]) form.profileId = String(profiles.selectedProfileId || loaded[0].profileId)
  } catch (error) { setupError.value = '가족 프로필을 불러오지 못했어요. 프로필에서 다시 시도해 주세요.' }
})
</script>
<template>
  <div class="content-page travel-page">
    <p class="eyebrow">{{ startingPoint }}</p>
    <h1 tabindex="-1">이번 한 끼를<br />어디에서 살펴볼까요?</h1>
    <p class="page-description">현재 검증을 위한 한 Meal Slot의 추천이에요. 날짜와 식사 유형을 임의 시간으로 바꾸지 않아요.</p>
    <form class="travel-grid" @submit.prevent="submit">
      <section class="surface travel-options" aria-label="추천 조건 입력">
        <fieldset><legend><span class="step-label">01</span>어디로 떠날까요?</legend><div class="option-pair">
          <label class="choice-card" :class="{ selected: form.region === 'JEJU' }"><input v-model="form.region" required type="radio" name="region" value="JEJU" /><span class="choice-top"><span class="place-drawing" aria-hidden="true">∿</span><span class="choice-indicator" aria-hidden="true"><AppIcon v-if="form.region === 'JEJU'" name="check" /></span></span><strong>제주</strong><span class="choice-description">바다 곁에서 만나는 한 끼</span></label>
          <label class="choice-card" :class="{ selected: form.region === 'GYEONGJU' }"><input v-model="form.region" required type="radio" name="region" value="GYEONGJU" /><span class="choice-top"><span class="place-drawing" aria-hidden="true">⌁</span><span class="choice-indicator" aria-hidden="true"><AppIcon v-if="form.region === 'GYEONGJU'" name="check" /></span></span><strong>경주</strong><span class="choice-description">오래된 풍경 곁의 한 끼</span></label>
        </div></fieldset>
        <div class="form-grid travel-fields"><label>식사 날짜<input v-model="form.tripDate" required type="date" /></label><label>Meal Slot<select v-model="form.mealType"><option value="BREAKFAST">아침</option><option value="LUNCH">점심</option><option value="DINNER">저녁</option></select></label><label>가족 프로필<select v-model="form.profileId" required><option disabled value="">프로필을 골라주세요</option><option v-for="profile in profiles.items" :key="profile.profileId" :value="String(profile.profileId)">{{ profile.name }}</option></select></label><label>먹고 싶은 지역음식 <input v-model.trim="form.desiredLocalFood" placeholder="선택 입력" /></label></div>
        <label class="wide-field">엄격히 제외할 재료·메뉴 <input v-model.trim="form.strictExclusions" placeholder="쉼표로 구분해 입력" /></label>
        <template v-if="startMode === 'PLACE_FIRST'"><p class="input-note">기준 장소 좌표가 있을 때만 대중교통 이동 근거를 함께 살펴봐요.</p><div class="form-grid"><label>경도<input v-model="form.longitude" type="number" step="any" placeholder="126.5" /></label><label>위도<input v-model="form.latitude" type="number" step="any" placeholder="33.5" /></label></div></template>
      </section>
      <aside class="travel-preview" aria-labelledby="travel-preview-title"><AppIcon name="journey" /><h2 id="travel-preview-title">우리 가족의 한 끼</h2><p class="selection-summary" role="status">{{ form.region === 'JEJU' ? '제주' : form.region === 'GYEONGJU' ? '경주' : '지역을 골라주세요' }}<span aria-hidden="true"> · </span>{{ form.mealType === 'BREAKFAST' ? '아침' : form.mealType === 'DINNER' ? '저녁' : '점심' }}</p><p class="quiet-note">실시간 관광정보와 표준 음식 참고정보를 요청할 때만 결합해요.</p><RouterLink v-if="!profiles.items.length && !profiles.loading" to="/profiles/new" class="action-button button-secondary">가족 프로필 만들기</RouterLink><button v-else class="action-button button-primary" type="submit" :disabled="recommendations.loading || profiles.loading">{{ recommendations.loading ? '추천을 살피는 중…' : '추천 확인' }}</button></aside>
    </form>
    <StatusMessage v-if="setupError" kind="error" title="추천을 준비하지 못했어요">{{ setupError }}</StatusMessage>
    <StatusMessage v-else-if="recommendations.loading" kind="loading" title="음식점을 찾고 있어요">실시간 후보와 메뉴 근거를 확인하고 있어요.</StatusMessage>
    <section v-if="recommendations.result" class="recommendation-results" aria-labelledby="recommendation-title"><p class="eyebrow">현재 한 끼의 비교</p><h2 id="recommendation-title">우리 가족의 추천</h2><p class="result-notice">{{ recommendations.result.nutritionNotice }}</p>
      <article v-for="perspective in recommendations.result.perspectives" :key="perspective.perspective" class="surface perspective-card"><div class="section-row"><h3>{{ perspectiveName(perspective.perspective) }}</h3><span class="evidence-badge">{{ perspective.status === 'READY' ? '현재 근거로 비교' : '추가 정보 필요' }}</span></div><p v-if="perspective.message" class="input-note">{{ perspective.message }}</p><p v-else-if="!perspective.candidates.length" class="input-note">조건을 모두 충족하는 후보가 없어요. 조건을 조금 조정해 비교해볼까요?</p>
        <article v-for="candidate in perspective.candidates" :key="candidate.contentId" class="recommendation-card"><h4>{{ candidate.title }}</h4><p class="address">{{ candidate.address }}</p><dl><div><dt>동행 적합도</dt><dd>{{ candidate.compatibilityScore || '—' }}</dd></div><div><dt>정보 근거</dt><dd>{{ evidenceName(candidate.informationEvidence) }}</dd></div></dl><div class="reason-grid"><div><h5>우리 가족과 잘 맞는 점</h5><p>{{ candidate.ourFamilyFitReasons.join(' ') || '현재 근거를 더 확인하고 있어요.' }}</p></div><div><h5>우리 가족이 주의할 점</h5><p>{{ candidate.ourFamilyCautions.join(' ') || '공개 정보에서 특별한 주의 근거는 찾지 못했어요.' }}</p></div><div><h5>방문 전에 같이 확인하면 좋은 점</h5><p>{{ candidate.checkBeforeVisit.join(' ') || '영업·주차 정보는 방문 전에 한 번 더 확인해 주세요.' }}</p></div></div><p v-if="candidate.nutritionEvidence.length" class="nutrition-line">{{ candidate.nutritionEvidence.map(item => `${item.menuName} · ${item.referenceLabel}`).join(' / ') }}</p><p v-if="candidate.transportEvidence" class="transport-line">예상 {{ Math.round(candidate.transportEvidence.totalTimeMinutes) }}분 · 환승 {{ candidate.transportEvidence.transferCount }}회 · 명시 도보 {{ candidate.transportEvidence.explicitWalkingDistanceMeters }}m</p></article>
      </article>
      <p class="source-line">{{ recommendations.result.sourceAttribution }}</p>
    </section>
  </div>
</template>
