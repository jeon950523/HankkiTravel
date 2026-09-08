<script setup>
import ActionButton from '../components/ActionButton.vue'
import EmptyState from '../components/EmptyState.vue'
import AppIcon from '../components/AppIcon.vue'
</script>
<template>
  <div class="content-page">
    <p class="eyebrow">함께 가는 사람들</p>
    <h1 tabindex="-1">우리 가족을 담는<br class="mobile-break" /> 여행 프로필</h1>
    <p class="page-description">함께 먹고, 걷고, 쉬는 취향을 한곳에.<br />우리 가족과 잘 맞는 여행을 준비하는 첫걸음이에요.</p>
    <EmptyState title="가족의 여행 이야기를 기다려요">
      가족 프로필을 담을 공간이에요.<br />지금은 새 프로필 화면을 미리 둘러볼 수 있어요.
      <template #action><ActionButton to="/profiles/new"><AppIcon name="plus" />새 프로필 만들기</ActionButton></template>
    </EmptyState>
    <p class="scope-note"><AppIcon name="note" />프로필 입력과 저장은 준비 중이에요.</p>
  </div>
</template>
