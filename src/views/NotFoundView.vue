<script setup>
import ActionButton from '../components/ActionButton.vue'
import AppIcon from '../components/AppIcon.vue'
</script>
<template><div class="focused-page not-found"><span class="outlined-symbol"><AppIcon name="journey" /></span><p class="eyebrow">길을 다시 찾아볼까요?</p><h1 tabindex="-1">이 페이지를<br />찾을 수 없어요.</h1><p class="page-description">주소가 바뀌었거나 없는 페이지예요.<br />홈에서 여행의 시작을 다시 찾아보세요.</p><ActionButton to="/">홈으로 돌아가기<AppIcon name="arrow" /></ActionButton></div></template>
