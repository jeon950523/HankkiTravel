import { defineStore } from 'pinia'
import { requestJson } from '../services/http'

const pathFor = (guest, trip = '') => `/api/guests/${encodeURIComponent(guest)}/trips${trip ? `/${encodeURIComponent(trip)}` : ''}`
const dayKey = (trip, day) => `${trip}:${day}`
const slotKey = (trip, slot) => `${trip}:${slot}`

export const useTripsStore = defineStore('trips', {
  state: () => ({
    items: [], detail: null, restaurantResults: {}, dessertResults: {}, placeResults: {}, alternativeResults: {}, focusResults: {}, selectedFocus: {}, planners: {},
    plannerLoading: {}, plannerErrors: {}, recommendationErrors: {}, loading: false, actionLoading: '', error: null,
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
    async list(guest) {
      this.loading = true; this.error = null
      try { this.items = await requestJson(pathFor(guest)); return this.items }
      catch (error) { this.error = error.message || '여행 목록을 불러오지 못했어요.'; throw error }
      finally { this.loading = false }
    },
    async deleteTrip(guest, trip) {
      return this.run(`trip-delete:${trip}`, async () => {
        await requestJson(pathFor(guest, trip), { method: 'DELETE' })
        this.items = this.items.filter(item => item.tripPublicId !== trip)
        if (this.detail?.tripPublicId === trip) this.detail = null
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
    async otherRestaurants(guest, trip, dayNumber, mealType, excluded = []) {
      if (Array.isArray(mealType)) { excluded = mealType; mealType = '' }
      return this.run(`restaurant-other:${dayNumber}`, async () => {
        const result = await requestJson(`${pathFor(guest, trip)}/days/${dayNumber}/restaurant-alternatives?mealType=${encodeURIComponent(mealType || '')}`, {
          method: 'POST', body: JSON.stringify({ exclude: excluded.join(',') }),
        })
        this.alternativeResults[`${dayKey(trip, dayNumber)}:RESTAURANT`] = result
        return result
      })
    },
    async searchRestaurants(guest, trip, dayNumber, mealType, keyword) {
      if (keyword === undefined) { keyword = mealType; mealType = '' }
      return this.run(`restaurant-search:${dayNumber}`, async () => {
        const result = await requestJson(`${pathFor(guest, trip)}/days/${dayNumber}/restaurant-search?mealType=${encodeURIComponent(mealType || '')}&keyword=${encodeURIComponent(keyword)}`)
        this.alternativeResults[`${dayKey(trip, dayNumber)}:RESTAURANT`] = result
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
    async clearMeal(guest, trip, slot) {
      return this.run(`meal-clear:${slot}`, async () => {
        await requestJson(`${pathFor(guest, trip)}/meal-slots/${encodeURIComponent(slot)}/anchor`, { method: 'DELETE' })
        const target = this.detail?.days.flatMap(day => day.mealSlots).find(item => item.mealSlotPublicId === slot)
        if (target) target.anchor = null
      })
    },

    async recommendPlace(guest, trip, dayNumber, slotType) {
      const key = `${dayKey(trip, dayNumber)}:${slotType}`
      delete this.recommendationErrors[key]
      try { return await this.run(`place:${dayNumber}:${slotType}`, async () => {
        const result = await requestJson(`${pathFor(guest, trip)}/days/${dayNumber}/place-recommendations`, { method: 'POST', body: JSON.stringify({ slotType }) })
        this.placeResults[key] = result
        return result
      }) } catch (error) {
        this.error = null
        this.recommendationErrors[key] = '관광지 추천을 현재 불러오지 못했어요. 잠시 후 다시 확인해 주세요.'
        throw error
      }
    },
    async recommendStay(guest, trip, dayNumber) {
      const key = `${dayKey(trip, dayNumber)}:STAY`
      delete this.recommendationErrors[key]
      try { return await this.run(`stay:${dayNumber}`, async () => {
        const result = await requestJson(`${pathFor(guest, trip)}/days/${dayNumber}/stay-recommendations`, { method: 'POST' })
        this.placeResults[key] = result
        return result
      }) } catch (error) {
        this.error = null
        this.recommendationErrors[key] = '숙소 추천을 현재 불러오지 못했어요. 잠시 후 다시 확인해 주세요.'
        throw error
      }
    },
    async otherPlaces(guest, trip, dayNumber, slotType, excluded = []) {
      return this.run(`place-other:${dayNumber}:${slotType}`, async () => {
        const result = await requestJson(`${pathFor(guest, trip)}/days/${dayNumber}/place-alternatives`, {
          method: 'POST', body: JSON.stringify({ slotType, exclude: excluded.join(',') }),
        })
        this.alternativeResults[`${dayKey(trip, dayNumber)}:${slotType}`] = result
        return result
      })
    },
    async searchPlaces(guest, trip, dayNumber, slotType, keyword) {
      return this.run(`place-search:${dayNumber}:${slotType}`, async () => {
        const result = await requestJson(`${pathFor(guest, trip)}/days/${dayNumber}/place-search?slotType=${encodeURIComponent(slotType)}&keyword=${encodeURIComponent(keyword)}`)
        this.alternativeResults[`${dayKey(trip, dayNumber)}:${slotType}`] = result
        return result
      })
    },
    async recommendDessert(guest, trip, dayNumber, mealType) {
      return this.run(`dessert:${dayNumber}:${mealType}`, async () => {
        const result = await requestJson(`${pathFor(guest, trip)}/days/${dayNumber}/dessert-recommendations?mealType=${encodeURIComponent(mealType)}`, { method: 'POST' })
        this.dessertResults[`${dayKey(trip, dayNumber)}:${mealType}`] = result
        return result
      })
    },
    async otherDesserts(guest, trip, dayNumber, mealType, excluded = []) {
      return this.run(`dessert-other:${dayNumber}:${mealType}`, async () => {
        const result = await requestJson(`${pathFor(guest, trip)}/days/${dayNumber}/dessert-alternatives?mealType=${encodeURIComponent(mealType)}`, {
          method: 'POST', body: JSON.stringify({ exclude: excluded.join(',') }),
        })
        this.dessertResults[`${dayKey(trip, dayNumber)}:${mealType}`] = result
        return result
      })
    },
    async searchDesserts(guest, trip, dayNumber, mealType, keyword) {
      return this.run(`dessert-search:${dayNumber}:${mealType}`, async () => {
        const result = await requestJson(`${pathFor(guest, trip)}/days/${dayNumber}/dessert-search?mealType=${encodeURIComponent(mealType)}&keyword=${encodeURIComponent(keyword)}`)
        this.dessertResults[`${dayKey(trip, dayNumber)}:${mealType}`] = result
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
    async clearPlace(guest, trip, dayNumber, slotType) {
      return this.run(`place-clear:${dayNumber}:${slotType}`, async () => {
        await requestJson(`${pathFor(guest, trip)}/days/${dayNumber}/place-anchors/${slotType}`, { method: 'DELETE' })
        delete this.planners[dayKey(trip, dayNumber)]
      })
    },

    async loadPlanner(guest, trip, dayNumber) {
      const key = dayKey(trip, dayNumber)
      if (this.plannerLoading[key]) return null
      this.plannerLoading[key] = true
      delete this.plannerErrors[key]
      try {
        const result = await requestJson(`${pathFor(guest, trip)}/days/${dayNumber}/planner`)
        this.planners[key] = result
        const targetDay = this.detail?.days.find(day => day.dayNumber === dayNumber)
        if (targetDay && result.completion) targetDay.completion = { ...result.completion, requiredComplete: result.completion.requiredComplete ?? result.completion.isRequiredComplete }
        return result
      } catch (error) {
        this.plannerErrors[key] = error.message || '오늘 일정을 불러오지 못했어요.'
        throw error
      } finally {
        delete this.plannerLoading[key]
      }
    },
  },
})
