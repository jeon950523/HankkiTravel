import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useHealthStore } from './health'

beforeEach(() => { setActivePinia(createPinia()) })
afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers() })
it('reflects UP only after a successful backend response', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ status: 'UP' }) }))
  const store = useHealthStore()
  const pending = store.check()
  expect(store.status).toBe('loading')
  await pending
  expect(store.status).toBe('up')
  expect(fetch).toHaveBeenCalledWith('/actuator/health', expect.objectContaining({ cache: 'no-store' }))
})
it.each([
  { ok: false }, { ok: true, json: async () => ({ status: 'DOWN' }) },
  { ok: true, json: async () => { throw new Error('invalid JSON') } },
])('does not report unhealthy or malformed responses as success', async response => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response))
  const store = useHealthStore()
  await store.check()
  expect(store.status).toBe('error')
})
it('recovers after network failure', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValueOnce(new TypeError('offline'))
    .mockResolvedValueOnce({ ok: true, json: async () => ({ status: 'UP' }) }))
  const store = useHealthStore()
  await store.check()
  expect(store.status).toBe('error')
  await store.check()
  expect(store.status).toBe('up')
})
it('aborts slow requests and avoids duplicate concurrent requests', async () => {
  vi.useFakeTimers()
  vi.stubGlobal('fetch', vi.fn((_url, { signal }) => new Promise((_resolve, reject) => {
    signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))
  })))
  const store = useHealthStore()
  const pending = store.check()
  await store.check()
  expect(fetch).toHaveBeenCalledTimes(1)
  await vi.advanceTimersByTimeAsync(8000)
  await pending
  expect(store.status).toBe('error')
})
