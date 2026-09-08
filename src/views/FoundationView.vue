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

<style scoped>
* { box-sizing: border-box; }
:root { font-family: Pretendard, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: #272A27; background: #F7F3EB; font-synthesis: none; }
body { margin: 0; min-width: 320px; }
button { font: inherit; }
.shell { width: 100%; max-width: 560px; margin: 0 auto; padding: max(28px, env(safe-area-inset-top)) 20px max(28px, env(safe-area-inset-bottom)); }
.brand { display: flex; align-items: center; gap: 9px; font-size: 21px; font-weight: 700; color: #414A3C; }
.tagline { margin: 40px 0 28px; font-size: 28px; font-weight: 700; line-height: 1.5; letter-spacing: -0.04em; }
.status-card { padding: 24px 20px; background: #FFFDFA; border: 1px solid #DED2BE; border-radius: 20px; }
.eyebrow { margin: 0 0 14px; font-size: 12px; letter-spacing: 0.08em; color: #6A564B; }
h1 { margin: 0; font-size: 22px; line-height: 1.45; letter-spacing: -0.045em; word-break: keep-all; overflow-wrap: anywhere; }
.description, .error-help { color: #62655F; font-size: 15px; line-height: 1.7; word-break: keep-all; }
.connection { display: flex; align-items: flex-start; gap: 10px; margin: 24px 0 18px; padding: 16px 12px; border-radius: 12px; background: #F0F1EA; color: #414A3C; }
.connection p { margin: 0; font-size: 15px; line-height: 1.6; word-break: keep-all; }
.status-mark { flex: 0 0 18px; line-height: 24px; font-weight: 700; }
button { width: 100%; min-height: 48px; padding: 12px; border: 0; border-radius: 10px; background: #414A3C; color: white; cursor: pointer; }
button:disabled { opacity: 0.65; cursor: wait; }
button:focus-visible { outline: 3px solid #C86B4A; outline-offset: 4px; }
footer { margin-top: 26px; color: #62655F; font-size: 13px; line-height: 1.7; text-align: center; word-break: keep-all; }
@media (min-width: 768px) { .shell { padding-top: 64px; } .status-card { padding: 30px; } }

</style>
