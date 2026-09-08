<script setup>
import ActionButton from '../components/ActionButton.vue'
import AppIcon from '../components/AppIcon.vue'
import StatusMessage from '../components/StatusMessage.vue'
</script>
<template>
  <div class="content-page">
    <RouterLink to="/profiles" class="back-link"><AppIcon name="back" />가족 프로필</RouterLink>
    <p class="eyebrow">새로운 여행의 동행</p>
    <h1 tabindex="-1">함께 떠날 가족을<br />담을 공간이에요.</h1>
    <p class="page-description">누구와 함께하는 여행인지 차근차근 담아갈 거예요.</p>
    <section class="surface editor-outline" aria-labelledby="profile-outline">
      <h2 id="profile-outline">가족 프로필</h2>
      <div class="outline-row"><AppIcon name="note" /><div><h3>가족을 부르는 이름</h3><p>우리 가족의 프로필을 알아볼 수 있도록.</p></div></div>
      <div class="outline-row"><AppIcon name="family" /><div><h3>함께 가는 사람들</h3><p>여행을 함께할 구성원을 담을 수 있도록.</p></div></div>
      <StatusMessage title="프로필 작성을 준비하고 있어요">지금은 입력하거나 저장할 수 없어요.</StatusMessage>
      <ActionButton disabled>프로필 저장 · 준비 중</ActionButton>
    </section>
  </div>
</template>
