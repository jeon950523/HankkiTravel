<script setup>
import { computed, ref } from 'vue'
import StatusMessage from '../components/StatusMessage.vue'

const username = ref('')
const password = ref('')
const overview = ref(null)
const loading = ref(false)
const triggeringScope = ref('')
const error = ref('')
const notice = ref('')

const hasCredentials = computed(() => username.value.trim() && password.value)
const canTrigger = scope => Boolean(overview.value?.operatorEnabled && overview.value?.remainingCalls > 0
  && !overview.value?.activeScopeKey && !triggeringScope.value && scope.latestStatus !== 'RUNNING')

function basicAuthorization() {
  const bytes = new TextEncoder().encode(`${username.value}:${password.value}`)
  return `Basic ${btoa(String.fromCharCode(...bytes))}`
}

function messageForStatus(status) {
  return ({ AVAILABLE: '동기화를 실행할 수 있어요.', EXHAUSTED: '오늘 설정된 내부 호출 budget을 모두 사용했어요.',
    OPERATOR_CONFIGURATION_REQUIRED: '서버의 운영자 활성화와 내부 호출 budget 설정이 필요해요.' })[status]
    || '운영 상태를 다시 확인해 주세요.'
}

function formatTime(value) {
  if (!value) return '기록 없음'
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

async function loadStatus(preserveNotice = false) {
  error.value = ''
  if (!preserveNotice) notice.value = ''
  if (!hasCredentials.value) {
    error.value = '운영자 아이디와 비밀번호를 입력해 주세요.'
    return
  }
  loading.value = true
  try {
    const response = await fetch('/api/admin/tourism-sync/status', {
      headers: { Authorization: basicAuthorization(), Accept: 'application/json' },
    })
    if (!response.ok) throw new Error(response.status === 401 ? 'AUTH_REQUIRED' : 'STATUS_FAILED')
    overview.value = await response.json()
  } catch (exception) {
    overview.value = null
    error.value = exception.message === 'AUTH_REQUIRED'
      ? '운영자 인증에 실패했어요. 서버 설정과 입력값을 확인해 주세요.'
      : '운영 상태를 불러오지 못했어요. 네트워크와 서버 상태를 확인해 주세요.'
  } finally {
    loading.value = false
  }
}

async function trigger(scopeKey) {
  if (!canTrigger({ latestStatus: null })) return
  error.value = ''
  notice.value = ''
  triggeringScope.value = scopeKey
  try {
    const response = await fetch('/api/admin/tourism-sync/runs', {
      method: 'POST',
      headers: { Authorization: basicAuthorization(), Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ scopeKey }),
    })
    const result = await response.json().catch(() => ({}))
    if (response.status !== 202 || !result.accepted) {
      throw new Error(result.code || 'DISPATCH_FAILED')
    }
    notice.value = `${scopeKey} 동기화 요청을 접수했어요. 자동 재시도는 하지 않으며, 완료 결과는 운영 상태에서 다시 확인할 수 있어요.`
    await loadStatus(true)
  } catch (exception) {
    error.value = exception.message === 'CALL_BUDGET_EXHAUSTED'
      ? '남은 내부 호출 budget이 없어 동기화를 시작하지 않았어요.'
      : exception.message === 'SYNC_ALREADY_RUNNING'
        ? '다른 동기화가 실행 중이라 요청을 접수하지 않았어요.'
        : '동기화 요청을 접수하지 못했어요. 상태를 다시 확인해 주세요.'
  } finally {
    triggeringScope.value = ''
  }
}
</script>

<template>
  <div class="admin-sync-page">
    <p class="eyebrow">운영자 전용 · P1.2</p>
    <h1 tabindex="-1">관광 데이터 동기화 운영</h1>
    <p class="page-description">사용자 검색과 분리된 내부 관광 데이터 동기화 상태예요. 운영자 인증과 서버 설정이 확인된 경우에만 Scope별 실행을 요청할 수 있어요.</p>

    <section class="surface admin-access" aria-labelledby="admin-access-title">
      <h2 id="admin-access-title">운영자 연결</h2>
      <p class="body-copy">자격 증명은 이 화면과 브라우저 저장소에 남기지 않아요.</p>
      <div class="admin-access-fields">
        <label>운영자 아이디<input v-model="username" autocomplete="username" inputmode="text" /></label>
        <label>운영자 비밀번호<input v-model="password" type="password" autocomplete="current-password" /></label>
      </div>
      <button class="action-button button-primary" :disabled="loading" @click="loadStatus">
        {{ loading ? '운영 상태를 확인하고 있어요' : '운영 상태 불러오기' }}
      </button>
    </section>

    <StatusMessage v-if="error" kind="error" title="운영 요청을 완료하지 못했어요">{{ error }}</StatusMessage>
    <StatusMessage v-if="notice" title="동기화 요청 접수">{{ notice }}</StatusMessage>

    <template v-if="overview">
      <section class="admin-summary" aria-label="TourAPI 운영 요약">
        <article class="surface"><span>오늘 호출량</span><strong>{{ overview.dailyUsedCalls }}회</strong><small>{{ overview.operationZone }} 기준</small></article>
        <article class="surface"><span>내부 budget</span><strong>{{ overview.dailyCallBudget }}회</strong><small>남은 {{ overview.remainingCalls }}회</small></article>
        <article class="surface"><span>마지막 Sync</span><strong class="admin-date">{{ formatTime(overview.lastSyncAt) }}</strong><small>{{ messageForStatus(overview.budgetStatus) }}</small></article>
      </section>

      <StatusMessage v-if="overview.activeScopeKey" kind="loading" title="동기화 실행 중">{{ overview.activeScopeKey }} Scope가 실행 중이에요. 다른 요청은 접수하지 않아요.</StatusMessage>
      <StatusMessage v-else-if="overview.budgetStatus !== 'AVAILABLE'" title="실행 전 확인">{{ messageForStatus(overview.budgetStatus) }}</StatusMessage>

      <section class="admin-scope-section" aria-labelledby="scope-status-title">
        <div class="section-heading"><p class="eyebrow">지역 · 유형별 상태</p><h2 id="scope-status-title">9개 동기화 Scope</h2></div>
        <div class="admin-scope-grid">
          <article v-for="scope in overview.scopes" :key="scope.scopeKey" class="surface admin-scope-card">
            <div><h3>{{ scope.regionName }} · {{ scope.contentTypeName }}</h3><p class="admin-key">{{ scope.scopeKey }}</p></div>
            <dl><div><dt>최근 상태</dt><dd>{{ scope.latestStatus || '기록 없음' }}</dd></div><div><dt>마지막 성공</dt><dd>{{ formatTime(scope.lastSuccessfulSyncAt) }}</dd></div><div><dt>최근 호출</dt><dd>{{ scope.latestRemoteCallCount }}회</dd></div></dl>
            <p v-if="scope.failureCategory" class="admin-failure">실패 분류: {{ scope.failureCategory }}</p>
            <button class="action-button button-secondary" :disabled="!canTrigger(scope)" @click="trigger(scope.scopeKey)">
              {{ triggeringScope === scope.scopeKey ? '요청 중' : '이 Scope 동기화' }}
            </button>
          </article>
        </div>
      </section>

      <section v-if="overview.recentFailures.length" class="surface admin-failure-list" aria-labelledby="failure-summary-title">
        <h2 id="failure-summary-title">최근 실패 요약</h2>
        <ul><li v-for="failure in overview.recentFailures" :key="`${failure.scopeKey}-${failure.startedAt}`"><strong>{{ failure.scopeKey }}</strong><span>{{ failure.failureCategory }} · {{ failure.remoteCallCount }}회 · {{ formatTime(failure.startedAt) }}</span></li></ul>
      </section>
    </template>
  </div>
</template>
