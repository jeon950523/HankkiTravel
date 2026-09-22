<script setup>
import ActionButton from '../components/ActionButton.vue'
import AppIcon from '../components/AppIcon.vue'
</script>
<template>
  <div class="focused-page">
    <p class="eyebrow">함께 이어가는 여행</p>
    <h1 tabindex="-1">우리 가족의 여행,<br />다음에도 이어서.</h1>
    <p class="page-description">가족 프로필과 여행을 간편하게 이어갈 수 있도록<br class="desktop-break" /> 로그인 기능을 준비하고 있어요.</p>
    <section class="surface login-panel" aria-labelledby="login-options">
      <span class="outlined-symbol"><AppIcon name="journey" /></span>
      <h2 id="login-options">편한 방법으로 만나요</h2>
      <p class="body-copy">소셜 로그인은 아직 이용할 수 없어요.</p>
      <div class="login-actions">
        <ActionButton variant="kakao" disabled aria-describedby="login-readiness"><span class="provider-symbol" aria-hidden="true">●</span>카카오로 계속하기<span class="button-note">준비 중</span></ActionButton>
        <ActionButton variant="naver" disabled aria-describedby="login-readiness"><span class="provider-symbol" aria-hidden="true">N</span>네이버로 계속하기<span class="button-note">준비 중</span></ActionButton>
      </div>
      <p id="login-readiness" class="quiet-note">로그인 연결 없이 화면만 둘러볼 수 있어요.</p>
      <ActionButton to="/" variant="text">로그인 없이 둘러보기<AppIcon name="arrow" /></ActionButton>
    </section>
  </div>
</template>
