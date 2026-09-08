<script setup>
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import ActionButton from '../components/ActionButton.vue'
import AppIcon from '../components/AppIcon.vue'
import StatusMessage from '../components/StatusMessage.vue'
const route = useRoute()
const startingPoint = computed(() => route.query.start === 'place' ? '가보고 싶은 곳에서 시작해요' : '먹고 싶은 한 끼에서 시작해요')
const region = ref('')
const dayType = ref('')
</script>
<template>
  <div class="content-page travel-page">
    <p class="eyebrow">{{ startingPoint }}</p>
    <h1 tabindex="-1">어디로, 얼마 동안<br />떠나고 싶으세요?</h1>
    <p class="page-description">가까운 마음이 드는 쪽을 골라보세요.<br />지금은 여행의 시작만 가볍게 살펴볼 수 있어요.</p>
    <div class="travel-grid">
      <section class="surface travel-options" aria-label="여행 시작 선택">
        <fieldset><legend><span class="step-label">01</span>어디로 떠날까요?</legend>
          <div class="option-pair">
            <label class="choice-card" :class="{ selected: region === '제주' }"><input v-model="region" type="radio" name="region" value="제주" /><span class="choice-top"><span class="place-drawing" aria-hidden="true">∿</span><span class="choice-indicator" aria-hidden="true"><AppIcon v-if="region === '제주'" name="check" /></span></span><strong>제주</strong><span class="choice-description">바다 곁에서 만나는 한 끼</span></label>
            <label class="choice-card" :class="{ selected: region === '경주' }"><input v-model="region" type="radio" name="region" value="경주" /><span class="choice-top"><span class="place-drawing" aria-hidden="true">⌁</span><span class="choice-indicator" aria-hidden="true"><AppIcon v-if="region === '경주'" name="check" /></span></span><strong>경주</strong><span class="choice-description">오래된 풍경 곁의 한 끼</span></label>
          </div>
        </fieldset>
        <fieldset><legend><span class="step-label">02</span>얼마 동안 함께할까요?</legend>
          <div class="option-pair day-options"><label :class="{ selected: dayType === '당일' }"><input v-model="dayType" type="radio" name="dayType" value="당일" /><span>당일</span></label><label :class="{ selected: dayType === '1박 2일' }"><input v-model="dayType" type="radio" name="dayType" value="1박 2일" /><span>1박 2일</span></label></div>
        </fieldset>
      </section>
      <aside class="travel-preview" aria-labelledby="travel-preview-title">
        <AppIcon name="journey" /><h2 id="travel-preview-title">여행의 첫 마음</h2>
        <p class="selection-summary" role="status">{{ region || '지역을 골라주세요' }}<span aria-hidden="true"> · </span>{{ dayType || '머무를 시간을 골라주세요' }}</p>
        <StatusMessage title="다음 여정은 준비 중이에요">선택은 이 화면에서만 유지돼요.<br />추천이나 여행 저장은 아직 시작되지 않아요.</StatusMessage>
        <ActionButton disabled>다음으로 · 준비 중</ActionButton>
      </aside>
    </div>
  </div>
</template>
