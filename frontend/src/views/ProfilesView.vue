<script setup>
import { onMounted } from 'vue'
import ActionButton from '../components/ActionButton.vue'
import EmptyState from '../components/EmptyState.vue'
import AppIcon from '../components/AppIcon.vue'
import StatusMessage from '../components/StatusMessage.vue'
import { useGuestStore } from '../stores/guest'
import { useProfilesStore } from '../stores/profiles'

const guest = useGuestStore()
const profiles = useProfilesStore()
onMounted(async () => {
  try { await profiles.load(await guest.ensureGuest()) } catch { /* 화면은 안내 상태로 유지 */ }
})
</script>
<template>
  <div class="content-page">
    <p class="eyebrow">함께 가는 사람들</p>
    <h1 tabindex="-1">우리 가족을 담는<br class="mobile-break" /> 여행 프로필</h1>
    <p class="page-description">함께 먹고, 걷고, 쉬는 조건을 한곳에 담아<br />다음 한 끼를 함께 살펴봐요.</p>
    <StatusMessage v-if="profiles.loading" kind="loading" title="가족 프로필을 준비하고 있어요">처음이라면 여행을 위한 Guest 정보를 만들고 있어요.</StatusMessage>
    <StatusMessage v-else-if="profiles.error" kind="error" title="프로필을 불러오지 못했어요">{{ profiles.error }} 잠시 후 다시 시도해 주세요.</StatusMessage>
    <template v-else-if="profiles.items.length">
      <section class="profile-list" aria-label="저장한 가족 프로필">
        <article v-for="profile in profiles.items" :key="profile.profileId" class="surface profile-card">
          <div><p class="card-kicker">{{ profile.transportMode === 'CAR' ? '자동차 이동' : '대중교통 이동' }}</p><h2>{{ profile.name }}</h2><p>{{ profile.members.map(member => member.nickname).join(' · ') }}</p></div>
          <RouterLink :to="`/profiles/${profile.profileId}/edit`" class="text-link">수정하기<AppIcon name="arrow" /></RouterLink>
        </article>
      </section>
      <ActionButton to="/profiles/new" variant="secondary"><AppIcon name="plus" />새 프로필 만들기</ActionButton>
    </template>
    <EmptyState v-else title="가족의 여행 이야기를 기다려요">
      함께 걷고 식사할 가족의 조건을 먼저 담아볼까요?
      <template #action><ActionButton to="/profiles/new"><AppIcon name="plus" />새 프로필 만들기</ActionButton></template>
    </EmptyState>
  </div>
</template>
