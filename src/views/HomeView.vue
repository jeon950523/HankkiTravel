<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import JourneyArtwork from '../components/JourneyArtwork.vue'
import StatusMessage from '../components/StatusMessage.vue'
import { useGuestStore } from '../stores/guest'

const router = useRouter()
const guest = useGuestStore()
const startError = ref('')
const start = async mode => {
  startError.value = ''
  try { await guest.ensureGuest() } catch (error) { startError.value = 'Guest 정보를 준비하지 못했어요. 여행 화면에서 다시 시도해 주세요.' }
  await router.push({ path: '/travel/new', query: { start: mode } })
}
</script>
<template>
  <section class="home-hero">
    <div class="hero-copy">
      <p class="eyebrow"><span class="small-line"></span>한 끼에서 시작하는 가족여행</p>
      <h1 tabindex="-1">좋은 한 끼가,<br />좋은 여행을 만든다.</h1>
      <p class="hero-description">먹고 싶은 한 끼, 가보고 싶은 곳.<br />우리 가족의 여행을 거기서 시작해요.</p>
      <div class="hero-actions">
        <button class="action-button button-primary" type="button" :disabled="guest.loading" @click="start('meal')"><AppIcon name="meal" />여행 시작하기<AppIcon name="arrow" /></button>
        <button class="action-button button-secondary" type="button" :disabled="guest.loading" @click="start('place')"><AppIcon name="place" />장소부터 찾기<AppIcon name="arrow" /></button>
      </div>
      <StatusMessage v-if="startError" kind="error" title="여행 시작 정보를 확인해 주세요">{{ startError }}</StatusMessage>
      <p v-else class="quiet-note">로그인 없이 가족 프로필과 한 끼 추천을 시작할 수 있어요.</p>
    </div>
    <div class="hero-visual"><JourneyArtwork /><p>한 끼를 중심으로, 함께 이어가는 여행</p></div>
  </section>
  <section class="journey-intro" aria-labelledby="journey-heading">
    <div class="section-heading"><p class="eyebrow">우리의 여행 방식</p><h2 id="journey-heading">일정을 채우기 전에,<br class="mobile-break" /> 한 끼부터.</h2></div>
    <div class="journey-principles">
      <article><span class="principle-number">01</span><div><h3>먹고 싶은 마음에서</h3><p>그 지역의 한 끼를 여행의 시작점으로.</p></div></article>
      <article><span class="principle-number">02</span><div><h3>함께 가는 사람을 생각하며</h3><p>우리 가족과 잘 맞는 점을 살펴보고.</p></div></article>
      <article><span class="principle-number">03</span><div><h3>우리의 속도로 고르기</h3><p>마지막 선택은 언제나 우리 가족이.</p></div></article>
    </div>
  </section>
  <section class="family-invitation"><span class="invitation-icon"><AppIcon name="family" /></span><div><h2>누구와 함께 떠나세요?</h2><p>가족 프로필에 식사와 이동 조건을 담아볼 수 있어요.</p></div><RouterLink to="/profiles" class="text-link">가족 프로필 알아보기<AppIcon name="arrow" /></RouterLink></section>
</template>
