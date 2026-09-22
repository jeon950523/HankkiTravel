import { defineStore } from 'pinia'
import { apiUrl } from '../config/api'

export const useRecommendationStore = defineStore('recommendation', {
  state: () => ({ result: null, loading: false, error: null }),
  actions: {
    async request(payload) {
      this.loading = true
      this.error = null
      this.result = null
      try {
        const response = await fetch(apiUrl('/api/recommendations/restaurants'), {
          method: 'POST', headers: { Accept: 'application/json', 'Content-Type': 'application/json' }, body: JSON.stringify(payload),
        })
        const body = await response.json().catch(() => null)
        if (!response.ok) throw new Error(body?.message || '추천을 준비하지 못했어요.')
        this.result = body
        return body
      } catch (error) {
        this.error = error.message || '추천을 준비하지 못했어요.'
        throw error
      } finally {
        this.loading = false
      }
    },
    clear() { this.result = null; this.error = null },
  },
})
