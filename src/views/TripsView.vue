<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import StatusMessage from '../components/StatusMessage.vue'
import { useGuestStore } from '../stores/guest'
import { useProfilesStore } from '../stores/profiles'
import { useTripsStore } from '../stores/trips'
const guest = useGuestStore(); const profiles = useProfilesStore(); const trips = useTripsStore(); const deleting = ref(null); const cancelButton = ref(null)
let deleteTrigger = null
const region = key => key === 'JEJU' ? '제주' : '경주'
const profile = id => profiles.items.find(item => item.profileId === id)?.name || '가족 프로필'
async function load() { const id = await guest.ensureGuest(); await Promise.all([trips.list(id), profiles.load(id)]) }
function openDelete(id, event) { deleteTrigger = event.currentTarget; deleting.value = id }
function closeDelete() { deleting.value = null; nextTick(() => deleteTrigger?.focus()) }
async function remove() { const id = deleting.value; if (!id) return; await trips.deleteTrip(guest.publicId, id).catch(() => {}); if (!trips.error) closeDelete() }
function onDialogKeydown(event) {
  if (!deleting.value) return
  if (event.key === 'Escape') { event.preventDefault(); closeDelete(); return }
  if (event.key !== 'Tab') return
  const focusable = [...document.querySelector('.confirm-dialog')?.querySelectorAll('button:not(:disabled), [href], [tabindex]:not([tabindex="-1"])') || []]
  if (!focusable.length) return
  const first = focusable[0]; const last = focusable.at(-1)
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
}
watch(deleting, async active => { if (active) { await nextTick(); cancelButton.value?.focus() } })
onMounted(() => { load().catch(() => {}); window.addEventListener('keydown', onDialogKeydown) })
onBeforeUnmount(() => window.removeEventListener('keydown', onDialogKeydown))
</script>
<template><main class="content-page trips-page"><header class="section-heading"><p class="eyebrow">저장된 여행</p><h1>내 여행</h1><p>만들어 둔 여행을 이어서 준비하거나 정리할 수 있어요.</p></header><StatusMessage v-if="trips.loading" kind="loading" title="여행을 불러오고 있어요" /><p v-else-if="!trips.items.length" class="empty-inline">아직 저장된 여행이 없어요.</p><section v-else class="trip-list"><article v-for="trip in trips.items" :key="trip.tripPublicId" class="surface trip-list-card"><p class="eyebrow">{{ region(trip.regionKey) }} · {{ trip.durationDays - 1 }}박 {{ trip.durationDays }}일</p><h2>{{ trip.startDate }} ~ {{ trip.endDate }}</h2><p>{{ profile(trip.profileId) }} · 식사 {{ trip.selectedAnchorCount }}/{{ trip.mealSlotCount }} 선택</p><div class="card-actions"><RouterLink class="action-button button-primary" :to="`/travel/${trip.tripPublicId}`">이어서 준비하기</RouterLink><button class="action-button button-secondary" type="button" @click="openDelete(trip.tripPublicId, $event)">이 여행 삭제</button></div></article></section><div v-if="deleting" class="confirm-backdrop"><section class="surface confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="trip-delete-title" tabindex="-1"><h2 id="trip-delete-title">이 여행을 삭제할까요?</h2><p>선택한 식당, 관광지, 숙소, 디저트 일정이 함께 삭제돼요. 삭제 후 되돌릴 수 없어요.</p><div class="card-actions"><button ref="cancelButton" class="action-button button-secondary" type="button" @click="closeDelete">취소</button><button class="action-button button-primary" type="button" :disabled="Boolean(trips.actionLoading)" @click="remove">{{ trips.actionLoading ? '삭제 중…' : '여행 삭제' }}</button></div></section></div></main></template>
