import { defineStore } from 'pinia'
import { requestJson } from '../services/http'

const pathFor = (guest, trip = '') => `/api/guests/${encodeURIComponent(guest)}/trips${trip ? `/${encodeURIComponent(trip)}` : ''}`
const dayKey = (trip, day) => `${trip}:${day}`
const slotKey = (trip, slot) => `${trip}:${slot}`

export const useTripsStore = defineStore('trips', {
  state: () => ({
    detail: null, restaurantResults: {}, placeResults: {}, focusResults: {}, selectedFocus: {}, planners: {},
    loading: false, actionLoading: '', error: null,
  }),
  actions: {
    async run(name, task) {
      if (this.actionLoading) return null
      this.actionLoading = name
      this.error = null
      try { return await task() }
      catch (error) { this.error = error.message || '요청을 처리하지 못했어요.'; throw error }
      finally { this.actionLoading = '' }
    },
    async create(guest, payload) {
      return this.run('create', async () => {
        this.detail = await requestJson(pathFor(guest), { method: 'POST', body: JSON.stringify(payload) })
        return this.detail
      })
    },
    async load(guest, trip) {
      this.loading = true; this.error = null
      try { this.detail = await requestJson(pathFor(guest, trip)); return this.detail }
      catch (error) { this.error = error.message || '여행을 불러오지 못했어요.'; throw error }
      finally { this.loading = false }
    },
    async recommendMeal(guest, trip, slot) {
      return this.run(`meal:${slot}`, async () => {
        const result = await requestJson(`${pathFor(guest, trip)}/meal-slots/${encodeURIComponent(slot)}/recommendations`, { method: 'POST' })
        this.restaurantResults[slotKey(trip, slot)] = result
        return result
      })
    },
    async recommendFocus(guest, trip, dayNumber) {
      return this.run(`focus:${dayNumber}`, async () => {
        const result = await requestJson(`${pathFor(guest, trip)}/days/${dayNumber}/focus-recommendations`, { method: 'POST' })
        this.focusResults[dayKey(trip, dayNumber)] = result
        return result
      })
    },
    async searchFocus(guest, trip, dayNumber, keyword) {
      return this.run(`focus-search:${dayNumber}`, async () => {
        const result = await requestJson(`${pathFor(guest, trip)}/days/${dayNumber}/focus-search?keyword=${encodeURIComponent(keyword)}`)
        this.focusResults[dayKey(trip, dayNumber)] = result
        return result
      })
    },
    async selectFocus(guest, trip, dayNumber, candidate) {
      const selected = await this.selectPlace(guest, trip, dayNumber, 'DAY_FOCUS', candidate.contentId)
      if (selected) this.selectedFocus[dayKey(trip, dayNumber)] = candidate
      return selected
    },
    async clearFocus(guest, trip, dayNumber) {
      return this.run(`focus-clear:${dayNumber}`, async () => {
        await requestJson(`${pathFor(guest, trip)}/days/${dayNumber}/place-anchors/DAY_FOCUS`, { method: 'DELETE' })
        delete this.selectedFocus[dayKey(trip, dayNumber)]
        delete this.planners[dayKey(trip, dayNumber)]
      })
    },
    async selectMeal(guest, trip, slot, contentId) {
      return this.run(`meal-anchor:${slot}`, async () => {
        const anchor = await requestJson(`${pathFor(guest, trip)}/meal-slots/${encodeURIComponent(slot)}/anchor`, { method: 'PUT', body: JSON.stringify({ contentId }) })
        const target = this.detail?.days.flatMap(day => day.mealSlots).find(item => item.mealSlotPublicId === slot)
        if (target) target.anchor = anchor
        return anchor
      })
    },
    async recommendPlace(guest, trip, dayNumber, slotType) {
      return this.run(`place:${dayNumber}:${slotType}`, async () => {
        const result = await requestJson(`${pathFor(guest, trip)}/days/${dayNumber}/place-recommendations`, { method: 'POST', body: JSON.stringify({ slotType }) })
        this.placeResults[`${dayKey(trip, dayNumber)}:${slotType}`] = result
        return result
      })
    },
    async recommendStay(guest, trip, dayNumber) {
      return this.run(`stay:${dayNumber}`, async () => {
        const result = await requestJson(`${pathFor(guest, trip)}/days/${dayNumber}/stay-recommendations`, { method: 'POST' })
        this.placeResults[`${dayKey(trip, dayNumber)}:STAY`] = result
        return result
      })
    },
    async selectPlace(guest, trip, dayNumber, slotType, contentId) {
      return this.run(`place-anchor:${dayNumber}:${slotType}`, async () => {
        const result = await requestJson(`${pathFor(guest, trip)}/days/${dayNumber}/place-anchors/${slotType}`, { method: 'PUT', body: JSON.stringify({ contentId }) })
        delete this.planners[dayKey(trip, dayNumber)]
        return result
      })
    },
    async loadPlanner(guest, trip, dayNumber) {
      return this.run(`planner:${dayNumber}`, async () => {
        const result = await requestJson(`${pathFor(guest, trip)}/days/${dayNumber}/planner`)
        this.planners[dayKey(trip, dayNumber)] = result
        return result
      })
    },
  },
})
