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

it('surfaces a tourism outage and clears loading state', async () => {
  fetch.mockResolvedValue({ ok: false, status: 503, json: async () => ({ code: 'CURRENT_TOURISM_DATA_UNAVAILABLE' }) })
  const store = useTripsStore()
  await expect(store.recommendMeal('guest', 'trip', 'slot')).rejects.toThrow('현재 관광정보')
  expect(store.actionLoading).toBe('')
  expect(store.error).toContain('현재 관광정보')
})
