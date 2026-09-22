import { beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useTripsStore } from './trips'

beforeEach(() => { setActivePinia(createPinia()); vi.stubGlobal('fetch', vi.fn()); vi.stubGlobal('localStorage', { length: 0 }) })

it('guards duplicate recommendation requests and exposes errors', async () => {
  let resolve
  fetch.mockReturnValueOnce(new Promise(done => { resolve = done }))
  const store = useTripsStore()
  const first = store.recommendMeal('guest', 'trip', 'slot')
  const second = await store.recommendMeal('guest', 'trip', 'slot')
  expect(second).toBeNull()
  expect(fetch).toHaveBeenCalledTimes(1)
  resolve({ ok: true, status: 200, json: async () => ({ perspectives: [] }) })
  await first
  expect(store.actionLoading).toBe('')
})

it('updates restaurant anchor in memory without persisting API results', async () => {
  fetch.mockResolvedValue({ ok: true, status: 200, json: async () => ({ provider: 'KTO', contentId: '123', contentType: '39' }) })
  const store = useTripsStore()
  store.detail = { days: [{ mealSlots: [{ mealSlotPublicId: 'slot', anchor: null }] }] }
  await store.selectMeal('guest', 'trip', 'slot', '123')
  expect(store.detail.days[0].mealSlots[0].anchor.contentId).toBe('123')
  expect(localStorage.length).toBe(0)
})

it('keeps DAY_FOCUS live detail only in memory', async () => {
  fetch.mockResolvedValue({ ok: true, status: 200, json: async () => ({ publicId: 'focus', slotType: 'DAY_FOCUS', contentId: '456' }) })
  const store = useTripsStore()
  await store.selectFocus('guest', 'trip', 1, { contentId: '456', title: '성산일출봉' })
  expect(store.selectedFocus['trip:1'].title).toBe('성산일출봉')
  expect(localStorage.length).toBe(0)
})

it('surfaces a tourism outage and clears loading state', async () => {
  fetch.mockResolvedValue({ ok: false, status: 503, json: async () => ({ code: 'CURRENT_TOURISM_DATA_UNAVAILABLE' }) })
  const store = useTripsStore()
  await expect(store.recommendMeal('guest', 'trip', 'slot')).rejects.toThrow('현재 관광정보')
  expect(store.actionLoading).toBe('')
  expect(store.error).toContain('현재 관광정보')
})

it('keeps post-meal dessert recommendations in memory only', async () => {
  fetch.mockResolvedValue({ ok: true, status: 200, json: async () => ({ slotType: 'POST_LUNCH_DESSERT', candidates: [{ contentId: '789' }] }) })
  const store = useTripsStore()
  await store.recommendDessert('guest', 'trip', 1, 'LUNCH')
  expect(fetch.mock.calls[0][0]).toContain('mealType=LUNCH')
  expect(store.dessertResults['trip:1:LUNCH'].candidates[0].contentId).toBe('789')
  expect(localStorage.length).toBe(0)
})

it.each([
  ['관광지', (store) => store.recommendPlace('guest', 'trip', 1, 'AFTERNOON_ACTIVITY'), 'trip:1:AFTERNOON_ACTIVITY'],
  ['숙소', (store) => store.recommendStay('guest', 'trip', 1), 'trip:1:STAY'],
])('%s 추천 실패를 해당 섹션에만 격리하고 기존 결과를 유지한다', async (label, request, key) => {
  fetch.mockResolvedValue({ ok: false, status: 502, json: async () => null })
  const store = useTripsStore()
  const previous = { candidates: [{ contentId: 'saved' }] }
  store.placeResults[key] = previous
  store.planners['trip:1'] = { items: [{ contentId: 'saved' }] }

  await expect(request(store)).rejects.toThrow()

  expect(store.recommendationErrors[key]).toContain(`${label} 추천`)
  expect(store.placeResults[key]).toStrictEqual(previous)
  expect(store.planners['trip:1'].items[0].contentId).toBe('saved')
  expect(store.error).toBeNull()
})

it('keeps restaurant and place alternative searches transient and uses bounded submit requests', async () => {
  fetch.mockResolvedValue({ ok: true, status: 200, json: async () => ({ candidates: [{ contentId: '777' }] }) })
  const store = useTripsStore()

  await store.otherRestaurants('guest', 'trip', 1, ['111', '222'])
  expect(fetch.mock.calls[0][0]).toContain('/restaurant-alternatives')
  expect(JSON.parse(fetch.mock.calls[0][1].body).exclude).toBe('111,222')
  expect(store.alternativeResults['trip:1:RESTAURANT'].candidates[0].contentId).toBe('777')

  await store.searchPlaces('guest', 'trip', 1, 'STAY', '바다 숙소')
  expect(fetch.mock.calls[1][0]).toContain('slotType=STAY')
  expect(fetch.mock.calls[1][0]).toContain(encodeURIComponent('바다 숙소'))
  expect(store.alternativeResults['trip:1:STAY'].candidates[0].contentId).toBe('777')
  expect(localStorage.length).toBe(0)
})

it('edit re-entry clears only transient trip state and keeps backend detail anchors', () => {
  const store = useTripsStore()
  store.detail = { tripPublicId: 'trip', days: [{ mealSlots: [{ anchor: { contentId: '123' } }] }] }
  store.restaurantResults['trip:slot'] = { candidates: [{ contentId: 'old' }] }
  store.alternativeResults['trip:1:RESTAURANT'] = { candidates: [{ contentId: 'old' }] }
  store.planners['trip:1'] = { items: [{ contentId: 'old' }] }
  store.planners['another:1'] = { items: [{ contentId: 'keep' }] }
  store.actionLoading = 'meal:slot'

  store.resetTransient('trip')

  expect(store.detail.days[0].mealSlots[0].anchor.contentId).toBe('123')
  expect(store.restaurantResults['trip:slot']).toBeUndefined()
  expect(store.alternativeResults['trip:1:RESTAURANT']).toBeUndefined()
  expect(store.planners['trip:1']).toBeUndefined()
  expect(store.planners['another:1'].items[0].contentId).toBe('keep')
  expect(store.actionLoading).toBe('')
})
