<script setup>
import { computed, onMounted } from 'vue'
import { useHealthStore } from '../stores/health'
const health = useHealthStore()
const label = computed(() => ({
  idle: '연결을 준비하고 있어요.',
  loading: '서비스 연결을 확인하고 있어요.',
  up: '서비스가 정상적으로 연결되었어요.',
  error: '서비스에 연결하지 못했어요.',
})[health.status])
onMounted(() => health.check())
</script>

<template>
  <main class="shell">
    <header class="brand"><img src="/icon.svg" alt="" width="40" height="40" /><span>한끼여행</span></header>
    <p class="tagline">좋은 한 끼가,<br />좋은 여행을 만든다.</p>
    <section class="status-card" aria-labelledby="status-title">
      <p class="eyebrow">P0.1 · FOUNDATION</p>
      <h1 id="status-title">여행의 시작을 준비하고 있어요</h1>
      <p class="description">한끼여행의 기본 실행 상태를 확인하는 화면이에요.</p>
      <div class="connection" role="status" aria-live="polite" :aria-busy="health.status === 'loading'">
        <span class="status-mark" aria-hidden="true">{{ health.status === 'up' ? '✓' : '·' }}</span>
        <p>{{ label }}</p>
      </div>
      <p v-if="health.status === 'error'" class="error-help">잠시 후 다시 확인해 주세요.</p>
      <button type="button" :disabled="health.status === 'loading'" @click="health.check()">
        {{ health.status === 'loading' ? '확인 중…' : '연결 다시 확인하기' }}
      </button>
    </section>
    <footer>가족의 한 끼에서 시작하는 맞춤 여행 플래너</footer>
  </main>
</template>
