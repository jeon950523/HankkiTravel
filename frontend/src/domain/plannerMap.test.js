import { describe, expect, it } from 'vitest'
import { activatePlannerItem, buildPlannerDisplayItems, buildPlannerLegs, carRouteNotice, mapViewport, switchPlannerDay } from './plannerMap'

const item = (slotType, contentId, coordinates = { longitude: 126.5, latitude: 33.5 }, dataAvailability = 'CURRENT_DATA') => ({ slotType, contentId, coordinates, dataAvailability })

describe('planner map projection', () => {
  it('keeps chronology and assigns continuous marker numbers', () => {
    const items = buildPlannerDisplayItems([item('DAY_FOCUS', '1'), item('LUNCH', '2'), item('STAY', '3')])
    expect(items.map(value => [value.slotType, value.displayIndex])).toEqual([['DAY_FOCUS', 1], ['LUNCH', 2], ['STAY', 3]])
  })

  it('keeps a missing-coordinate card but skips its marker', () => {
    const [value] = buildPlannerDisplayItems([item('LUNCH', '1', null)])
    expect(value.mapPoint).toBeNull()
    expect(value.displayIndex).toBe(1)
  })

  it('deduplicates a DAY_FOCUS content repeated later in chronology', () => {
    const items = buildPlannerDisplayItems([item('DAY_FOCUS', '1'), item('MORNING_ACTIVITY', '1'), item('LUNCH', '2')])
    expect(items.map(value => value.contentId)).toEqual(['1', '2'])
  })

  it.each([[[], 'EMPTY'], [[item('LUNCH', '1')], 'SINGLE'], [[item('LUNCH', '1'), item('STAY', '2')], 'BOUNDS']])('selects the correct viewport for 0/1/N points', (source, mode) => {
    expect(mapViewport(buildPlannerDisplayItems(source)).mode).toBe(mode)
  })

  it('uses one transient active id for card and marker selection', () => {
    expect(activatePlannerItem({ activePlannerItemId: '' }, 'LUNCH:2').activePlannerItemId).toBe('LUNCH:2')
    expect(activatePlannerItem({ activePlannerItemId: 'LUNCH:2' }, 'STAY:3').activePlannerItemId).toBe('STAY:3')
  })

  it('clears previous map selection when the day changes', () => {
    const state = switchPlannerDay({ activePlannerItemId: 'LUNCH:2' }, 2, [item('DINNER', '4')])
    expect(state.activePlannerItemId).toBe('')
    expect(state.dayNumber).toBe(2)
    expect(state.items[0].contentId).toBe('4')
  })

  it('states the car route limitation without inventing a duration', () => {
    expect(carRouteNotice('CAR')).toContain('이동시간은 현재 제공하지 않아요')
    expect(carRouteNotice('CAR')).not.toMatch(/\d+분/)
    expect(carRouteNotice('PUBLIC_TRANSIT')).toBe('')
  })

  it('builds only honest car reference lines between adjacent markers', () => {
    const items = buildPlannerDisplayItems([item('DAY_FOCUS', '1'), item('LUNCH', '2'), item('STAY', '3')])
    const legs = buildPlannerLegs(items, [
      { fromSlotType: 'DAY_FOCUS', toSlotType: 'LUNCH', mode: 'CAR', dataAvailability: 'STRAIGHT_LINE_REFERENCE' },
      { fromSlotType: 'LUNCH', toSlotType: 'STAY', mode: 'PUBLIC_TRANSIT', dataAvailability: 'CURRENT_DATA' },
    ])
    expect(legs[0].renderReferenceLine).toBe(true)
    expect(legs[1].renderReferenceLine).toBe(false)
    expect(legs.map(value => value.plannerLegId)).toEqual(['DAY_FOCUS->LUNCH:0', 'LUNCH->STAY:1'])
  })
})
