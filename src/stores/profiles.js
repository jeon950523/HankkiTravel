import { defineStore } from 'pinia'
import { apiUrl } from '../config/api'

async function jsonOrThrow(response, fallback) {
  const body = await response.json().catch(() => null)
  if (!response.ok) {
    const error = new Error(body?.message || fallback)
    error.status = response.status
    error.code = body?.code
    throw error
  }
  return body
}

export const useProfilesStore = defineStore('profiles', {
  state: () => ({ items: [], selectedProfileId: null, loading: false, error: null }),
  actions: {
    async load(guestPublicId) {
      this.loading = true
      this.error = null
      try {
        this.items = await jsonOrThrow(await fetch(apiUrl(`/api/guests/${encodeURIComponent(guestPublicId)}/profiles`), {
          headers: { Accept: 'application/json' },
        }), '가족 프로필을 불러오지 못했어요.')
        if (!this.selectedProfileId && this.items[0]) this.selectedProfileId = this.items[0].profileId
        return this.items
      } catch (error) {
        this.error = error.message || '가족 프로필을 불러오지 못했어요.'
        throw error
      } finally {
        this.loading = false
      }
    },
    async save(guestPublicId, payload, profileId = null) {
      this.loading = true
      this.error = null
      try {
        const suffix = profileId ? `/${profileId}` : ''
        const body = await jsonOrThrow(await fetch(apiUrl(`/api/guests/${encodeURIComponent(guestPublicId)}/profiles${suffix}`), {
          method: profileId ? 'PUT' : 'POST',
          headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
        }), '가족 프로필을 저장하지 못했어요.')
        const index = this.items.findIndex(item => item.profileId === body.profileId)
        if (index >= 0) this.items.splice(index, 1, body)
        else this.items.unshift(body)
        this.selectedProfileId = body.profileId
        return body
      } catch (error) {
        this.error = error.message || '가족 프로필을 저장하지 못했어요.'
        throw error
      } finally {
        this.loading = false
      }
    },
  },
})
