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
    expect(style).toContain('.decision-results[aria-labelledby="meal-result"] > .live-card { display: block; }')
    expect(style).toContain('aspect-ratio: 16 / 9')
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
