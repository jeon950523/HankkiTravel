import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useHealthStore = defineStore('health', () => {
  const status = ref('idle')
  async function check() {
    if (status.value === 'loading') return
    status.value = 'loading'
    const controller = new AbortController()
    const timeout = setTimeout(() => controller.abort(), 8000)
    try {
      const response = await fetch('/actuator/health', { cache: 'no-store', signal: controller.signal })
      if (!response.ok) throw new Error('Health request failed')
      const body = await response.json()
      status.value = body.status === 'UP' ? 'up' : 'error'
    } catch {
      status.value = 'error'
    } finally {
      clearTimeout(timeout)
    }
  }
  return { status, check }
})
