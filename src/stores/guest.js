import { defineStore } from 'pinia'
import { apiUrl } from '../config/api'

const STORAGE_KEY = 'hankki.guest-public-id'
const readStoredGuest = () => typeof localStorage === 'undefined' ? null : localStorage.getItem(STORAGE_KEY)

export const useGuestStore = defineStore('guest', {
  state: () => ({ publicId: readStoredGuest(), loading: false, error: null }),
  actions: {
    async ensureGuest() {
      if (this.publicId) return this.publicId
      this.loading = true
      this.error = null
      try {
        const response = await fetch(apiUrl('/api/guests'), { method: 'POST', headers: { Accept: 'application/json' } })
        if (!response.ok) throw new Error('게스트 시작 정보를 만들지 못했어요.')
        const body = await response.json()
        if (!body.publicId) throw new Error('게스트 시작 정보를 확인하지 못했어요.')
        this.publicId = body.publicId
        if (typeof localStorage !== 'undefined') localStorage.setItem(STORAGE_KEY, body.publicId)
        return body.publicId
      } catch (error) {
        this.error = error.message || '게스트 시작 정보를 만들지 못했어요.'
        throw error
      } finally {
        this.loading = false
      }
    },
  },
})
