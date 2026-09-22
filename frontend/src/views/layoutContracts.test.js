import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const read = relativePath => readFileSync(new URL(relativePath, import.meta.url), 'utf8')

describe('BIG-04F layout contracts', () => {
  it('keeps restaurant image and details in vertical order with menu and CTA contracts', () => {
    const view = read('./TripJourneyView.vue')
    const style = read('../style.css')
    const recommendation = view.slice(view.indexOf('aria-labelledby="meal-result"'), view.indexOf('class="alternative-actions"'))

    expect(recommendation.indexOf('<KtoImage')).toBeLessThan(recommendation.indexOf('class="live-card-body"'))
    expect(recommendation).toContain('대표 메뉴')
    expect(recommendation).toContain('restaurantExternalActions(candidate)')
    expect(recommendation).toContain('이 식당 선택')
    expect(recommendation).toContain('class="surface live-card candidate-card"')
    expect(style).toContain('.candidate-card { display: block; }')
    expect(style).toContain('.candidate-card > .kto-image { height: auto; aspect-ratio: 16 / 9; }')
    expect(style).toContain('aspect-ratio: 16 / 9')
  })

  it('keeps restaurant and attraction alternatives on the same vertical media contract', () => {
    const view = read('./TripJourneyView.vue')
    const style = read('../style.css')

    expect(view).toContain("'restaurant-alternative-results': !activePlaceType")
    expect(view).toContain('class="surface live-card candidate-card" :class=')
    expect(view).toContain("'attraction-alternative-results': activePlaceType && activePlaceType !== 'STAY'")
    expect(style).toContain('.candidate-card { display: block; }')
  })

  it('keeps attraction media above full-width evidence and actions', () => {
    const view = read('./TripJourneyView.vue')
    const style = read('../style.css')
    const attraction = view.slice(view.indexOf('class="decision-results attraction-results"'), view.indexOf('class="alternative-actions"', view.indexOf('class="decision-results attraction-results"')))

    expect(attraction.indexOf('<KtoImage')).toBeLessThan(attraction.indexOf('class="live-card-body"'))
    expect(attraction).toContain('class="surface live-card candidate-card attraction-card"')
    expect(attraction).toContain('일정 적합도')
    expect(attraction).toContain('근거 커버리지')
    expect(attraction).toContain('이 일정과 잘 맞는 이유')
    expect(attraction).toContain('가족 이동 조건')
    expect(attraction).toContain('확인할 점')
    expect(attraction).toContain('카카오맵에서 검색')
    expect(attraction).toContain("`이 ${activePlaceType === 'STAY' ? '숙소' : '관광지'} 선택`")
    expect(style).toContain('.candidate-card { display: block; }')
    expect(style).toContain('.candidate-card > .kto-image { height: auto; aspect-ratio: 16 / 9; }')
  })

  it('aligns dessert, stay, and day focus candidates while excluding Final Plan cards', () => {
    const journey = read('./TripJourneyView.vue')
    const plan = read('./TripPlanView.vue')
    const dessert = journey.slice(journey.indexOf('class="candidate-grid"'), journey.indexOf('class="alternative-actions"', journey.indexOf('class="candidate-grid"')))

    expect(dessert).toContain('class="surface live-card candidate-card"')
    expect(dessert.indexOf('<KtoImage')).toBeLessThan(dessert.indexOf('class="live-card-body"'))
    expect(dessert).toContain('선택형 식후 디저트')
    expect(dessert).toContain('카카오맵에서 검색')
    expect(dessert).toContain('candidateDirections(candidate, dessertResult)')
    expect(dessert).toContain('이 디저트 선택')
    expect(journey).toContain('class="surface live-card candidate-card attraction-card"')
    expect(journey).toContain('class="surface live-card candidate-card" :class=')
    expect(journey).not.toContain('compact-live-card')
    expect(plan).not.toContain('candidate-card')
  })

  it('keeps the Final Plan map before a full-width timeline without changing chronology rendering', () => {
    const view = read('./TripPlanView.vue')
    const style = read('../style.css')
    const plan = view.slice(view.indexOf('class="surface final-plan-day"'))

    expect(plan.indexOf('<KakaoDayMap')).toBeLessThan(plan.indexOf('class="timeline-panel"'))
    expect(plan).toContain('v-for="(item, index) in day.items"')
    expect(plan).toContain('finalActions(day, index, item)')
    expect(plan).toContain('day.displayLegs[index]')
    expect(style).toContain('.final-plan-day .planner-layout { grid-template-columns: minmax(0, 1fr);')
    expect(style).toContain('.final-plan-day .timeline-panel { width: 100%; }')
  })
})
