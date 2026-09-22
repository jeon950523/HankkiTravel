<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import StatusMessage from '../components/StatusMessage.vue'
import { useGuestStore } from '../stores/guest'
import { useProfilesStore } from '../stores/profiles'

const route = useRoute()
const router = useRouter()
const guest = useGuestStore()
const profiles = useProfilesStore()
const saving = ref(false)
const error = ref('')
const initialSavedProfileId = Number(profiles.lastSavedProfileId)
const saved = ref(Number.isSafeInteger(initialSavedProfileId) && initialSavedProfileId === Number(route.params.profileId))
const savedProfileId = ref(saved.value ? initialSavedProfileId : null)
if (saved.value) profiles.lastSavedProfileId = null
const profileId = computed(() => route.params.profileId ? Number(route.params.profileId) : null)
const isEdit = computed(() => Number.isSafeInteger(profileId.value) && profileId.value > 0)
const cautions = [
  ['SODIUM', '나트륨 참고'], ['SUGAR', '당류 참고'], ['CARBOHYDRATE', '탄수화물 참고'],
  ['SPICY', '매운맛 참고'], ['INGREDIENT_CHECK', '원재료 확인 필요'], ['NONE', '특별히 없음'],
]
const newMember = () => ({ nickname: '', continuousWalkingMinutes: 30, stairsPreference: 'NEUTRAL', mealCautions: ['NONE'],
  bloodSugarCare: false, allergenText: '', avoidedFoodText: '' })
const form = reactive({
  name: '', transportMode: 'CAR', parkingPreference: 'NO_PREFERENCE', walkingBurdenPreference: 'NORMAL',
  transferPreference: 'NO_PREFERENCE', stairsAvoidance: false, members: [newMember()],
})
const replaceForm = source => {
  form.name = source.name
  form.transportMode = source.transportMode
  form.parkingPreference = source.parkingPreference
  form.walkingBurdenPreference = source.walkingBurdenPreference
  form.transferPreference = source.transferPreference
  form.stairsAvoidance = source.stairsAvoidance
  form.members = source.members.map(member => ({ nickname: member.nickname, continuousWalkingMinutes: member.continuousWalkingMinutes,
    stairsPreference: member.stairsPreference, mealCautions: [...member.mealCautions], bloodSugarCare: Boolean(member.bloodSugarCare),
    allergenText: (member.allergenRestrictions || []).join(', '), avoidedFoodText: (member.avoidedFoods || []).join(', ') }))
}
const splitRestrictions = value => value.split(',').map(item => item.trim()).filter(Boolean)
const normalizeMember = member => ({ nickname: member.nickname, continuousWalkingMinutes: member.continuousWalkingMinutes,
  stairsPreference: member.stairsPreference, bloodSugarCare: member.bloodSugarCare,
  mealCautions: member.mealCautions.includes('NONE') ? ['NONE'] : member.mealCautions,
  allergenRestrictions: splitRestrictions(member.allergenText), avoidedFoods: splitRestrictions(member.avoidedFoodText) })
const toggleCaution = (member, value) => {
  if (value === 'NONE' && member.mealCautions.includes('NONE')) member.mealCautions = ['NONE']
  else if (value !== 'NONE') member.mealCautions = member.mealCautions.filter(item => item !== 'NONE')
}
const addMember = () => form.members.push(newMember())
const removeMember = index => { if (form.members.length > 1) form.members.splice(index, 1) }
const saveButtonLabel = computed(() => saving.value ? '저장 중...' : saved.value ? '저장됨 ✓' : error.value ? '다시 시도' : '프로필 저장')
watch(form, () => { if (!saving.value) saved.value = false }, { deep: true })
const submit = async () => {
  saving.value = true
  error.value = ''
  saved.value = false
  try {
    const guestPublicId = await guest.ensureGuest()
    const savedProfile = await profiles.save(guestPublicId, { ...form, members: form.members.map(normalizeMember) }, profileId.value)
    savedProfileId.value = savedProfile.profileId
    await router.replace(`/profiles/${savedProfile.profileId}/edit`)
    saved.value = true
    profiles.lastSavedProfileId = null
  } catch (cause) {
    error.value = cause.message || '프로필을 저장하지 못했어요.'
  } finally { saving.value = false }
}
onMounted(async () => {
  if (!isEdit.value) return
  try {
    const items = await profiles.load(await guest.ensureGuest())
    const profile = items.find(item => item.profileId === profileId.value)
    if (!profile) error.value = '요청한 프로필을 찾을 수 없어요.'
    else replaceForm(profile)
  } catch (cause) { error.value = cause.message || '프로필을 불러오지 못했어요.' }
})
</script>
<template>
  <div class="content-page editor-page">
    <RouterLink to="/profiles" class="back-link"><AppIcon name="back" />가족 프로필</RouterLink>
    <p class="eyebrow">{{ isEdit ? '여행의 동행을 다듬기' : '새로운 여행의 동행' }}</p>
    <h1 tabindex="-1">{{ isEdit ? '우리 가족의 조건을\n다시 살펴봐요.' : '함께 떠날 가족을\n담아볼까요?' }}</h1>
    <p class="page-description">의료 판단이 아닌 여행 중 식사와 이동을 함께 살피기 위한 정보예요.</p>
    <form class="surface profile-form" @submit.prevent="submit">
      <label>프로필 이름<input v-model.trim="form.name" required maxlength="100" placeholder="예: 부모님과 제주 여행" /></label>
      <fieldset><legend>이동 방식</legend><div class="compact-choices">
        <label><input v-model="form.transportMode" type="radio" value="CAR" />자동차</label>
        <label><input v-model="form.transportMode" type="radio" value="PUBLIC_TRANSIT" />대중교통</label>
      </div></fieldset>
      <div class="form-grid">
        <label>주차 선호<select v-model="form.parkingPreference"><option value="NO_PREFERENCE">상관없음</option><option value="PREFERRED">있으면 좋아요</option><option value="REQUIRED">필수예요</option></select></label>
        <label>환승 선호<select v-model="form.transferPreference"><option value="NO_PREFERENCE">상관없음</option><option value="AVOID">적을수록 좋아요</option><option value="PREFERRED">상관없어요</option></select></label>
        <label>도보 부담<select v-model="form.walkingBurdenPreference"><option value="LOW">낮게</option><option value="NORMAL">보통</option><option value="HIGH">여유 있게</option></select></label>
        <label class="check-label"><input v-model="form.stairsAvoidance" type="checkbox" />계단을 가급적 피하고 싶어요</label>
      </div>
      <section class="members-section" aria-labelledby="members-title"><div class="section-row"><h2 id="members-title">함께 가는 사람</h2><button type="button" class="small-button" @click="addMember">구성원 추가</button></div>
        <article v-for="(member, index) in form.members" :key="index" class="member-form">
          <div class="section-row"><h3>구성원 {{ index + 1 }}</h3><button v-if="form.members.length > 1" type="button" class="text-button" @click="removeMember(index)">삭제</button></div>
          <div class="form-grid"><label>부르는 이름<input v-model.trim="member.nickname" required maxlength="100" placeholder="예: 엄마" /></label><label>연속 보행(분)<input v-model.number="member.continuousWalkingMinutes" type="number" required min="0" max="480" /></label><label>계단 선호<select v-model="member.stairsPreference"><option value="NEUTRAL">상관없음</option><option value="AVOID">피하고 싶어요</option></select></label></div>
          <fieldset><legend>식사할 때 참고할 점</legend><div class="caution-grid"><label v-for="[value, label] in cautions" :key="value"><input v-model="member.mealCautions" type="checkbox" :value="value" @change="toggleCaution(member, value)" />{{ label }}</label></div></fieldset>
          <label class="check-label"><input v-model="member.bloodSugarCare" type="checkbox" />혈당 관리를 고려하고 있어요</label>
          <div class="form-grid profile-restriction-grid"><label>알레르기 주의 재료<input v-model.trim="member.allergenText" maxlength="500" placeholder="예: 땅콩, 새우" /><span class="input-note">쉼표로 구분해 주세요. 공개 정보가 없으면 안전하다고 판단하지 않아요.</span></label><label>피하고 싶은 음식/재료<input v-model.trim="member.avoidedFoodText" maxlength="500" placeholder="예: 고수, 내장" /><span class="input-note">명시적 메뉴 충돌이 확인될 때만 제외에 사용해요.</span></label></div>
        </article>
      </section>
      <StatusMessage v-if="error" kind="error" title="저장하지 못했어요">{{ error }} 입력 내용은 그대로 유지했어요.</StatusMessage>
      <StatusMessage v-if="saved" title="✓ 가족 프로필이 저장됐어요.">이제 이 조건으로 여행을 시작해볼까요?</StatusMessage>
      <RouterLink v-if="saved && savedProfileId" class="action-button button-primary" :to="{ path: '/travel/new', query: { profileId: savedProfileId } }">이 프로필로 여행 시작하기</RouterLink>
      <button class="action-button" :class="saved ? 'button-secondary' : 'button-primary'" type="submit" :disabled="saving">{{ saveButtonLabel }}</button>
    </form>
  </div>
</template>
