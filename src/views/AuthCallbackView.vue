<script setup>
import ActionButton from '../components/ActionButton.vue'
import StatusMessage from '../components/StatusMessage.vue'
</script>
<template><div class="focused-page"><p class="eyebrow">로그인 연결 안내</p><h1 tabindex="-1">로그인 연결을<br />준비하고 있어요.</h1><p class="page-description">아직 로그인 결과를 확인하거나 계정을 연결할 수 없어요.</p><StatusMessage>로그인되지 않은 상태로 화면을 둘러볼 수 있어요.</StatusMessage><ActionButton to="/" class="callback-action">홈으로 돌아가기</ActionButton></div></template>
