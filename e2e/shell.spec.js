import { test, expect, chromium } from '@playwright/test'

const guestId = '9d4f2c3a-6d29-4c12-8a70-2c6a2f87b111'
const tripId = 'c4b44ec1-ae62-4568-a7a9-25e4469d5d11'
const slotId = '75a9d2bb-379f-42fc-bb36-047cbd34b853'
const profile = { profileId: 42, name: '부모님과 제주', transportMode: 'CAR', parkingPreference: 'REQUIRED', walkingBurdenPreference: 'NORMAL', transferPreference: 'AVOID', stairsAvoidance: true, members: [{ memberId: 7, nickname: '엄마', continuousWalkingMinutes: 20, stairsPreference: 'AVOID', mealCautions: ['SODIUM'] }] }
const candidate = { contentId: '123', title: '현재 식당', areaLabel: '제주시 현재로', address: '제주시 현재로', imageUrl: null, compatibilityScore: 82, overallScore: 82, evidenceCoverage: 70, informationEvidence: 'REFERENCE', distanceMeters: 1800, phone: '064-123-4567', placeUrl: 'https://place.map.kakao.com/123', contactEvidence: 'KTO_DIRECT', ourFamilyFitReasons: ['공개 정보에서 확인한 근거예요.'], ourFamilyCautions: ['원재료는 직접 확인해 주세요.'], checkBeforeVisit: ['공개된 메뉴 정보가 부족해 식사 조건을 충분히 비교하기 어려워요. 방문 전에 전화로 재료와 조리법을 확인해 주세요.'], nutritionEvidence: [{ menuName: '비빔밥', matchLevel: 'HIGH', standardFood: '비빔밥', referenceLabel: '표준 음식 기준' }, { menuName: '돌솥밥', matchLevel: 'MEDIUM', standardFood: '비빔밥', referenceLabel: '유사 음식 기준' }], evaluatedDimensions: [{ code: 'FAMILY_MEAL_FIT', label: '식사 조건', weight: 40, evaluated: true, awardedPoints: 34, maxPoints: 40, evidenceState: 'EVALUATED' }, { code: 'AREA_DEMAND_SIGNAL', label: '지역 방문 수요', weight: 10, evaluated: true, awardedPoints: 8, maxPoints: 10, evidenceState: 'EVALUATED' }, { code: 'REVIEW_SIGNAL', label: '후기/평판', weight: 10, evaluated: false, awardedPoints: 0, maxPoints: 0, evidenceState: 'NOT_EVALUATED' }] }
const place = { contentId: '456', contentType: '12', title: '현재 관광지', areaLabel: '제주시', imageUrl: null, address: '제주시 여행로', coordinates: { longitude: 126.5, latitude: 33.5 }, informationEvidence: 'TOUR_API_LIVE', perspective: 'NEARBY_COURSE', overallScore: 88, evidenceCoverage: 100, routeBurden: { state: 'REFERENCE', level: 'LOW', score: 90 }, distanceMeters: 4200, transitSummary: null, familyMobilityEvidence: ['자동차는 직선거리 기준 참고값입니다.'], reasons: ['현재 일정에서 이동 부담이 적어요.'], cautions: ['실제 도로 이동시간이 아닙니다. 자동차 이동시간은 제공하지 않아요.'], fitReasons: ['현재 일정에서 이동 부담이 적어요.'], checkBeforeVisit: ['실제 도로 이동시간이 아닙니다.'], sourceAttribution: '출처: ⓒ한국관광공사' }
const recommendation = { topCandidates: [candidate], perspectives: [{ perspective: 'BALANCED', status: 'READY', message: null, candidates: [candidate] }], candidateCount: 1, dataAvailability: 'CURRENT_DATA', sourceAttribution: '출처: ⓒ한국관광공사', nutritionNotice: '표준 음식 기준 또는 유사 음식 기준 참고정보입니다.' }

async function mockKakaoMap(page) {
  await page.addInitScript(() => {
    class LatLng { constructor(latitude, longitude) { this.latitude = latitude; this.longitude = longitude } }
    class LatLngBounds { extend() {} }
    class MapView { constructor(container) { this.container = container } panTo() {} setBounds() {} setCenter() {} }
    class MarkerImage { constructor(src) { this.src = src; this.hankkiActive = false } }
    class Marker {
      constructor(options) { this.options = options; this.element = document.createElement('button'); this.element.type = 'button'; this.element.className = 'map-number-marker'; this.element.setAttribute('aria-label', options.title); this.element.textContent = options.title.split('번')[0] }
      setMap(map) { this.element.remove(); if (map) map.container.appendChild(this.element) }
      setImage(image) { this.element.classList.toggle('active', Boolean(image.hankkiActive)) }
      setZIndex() {}
    }
    class Size { constructor(width, height) { this.width = width; this.height = height } }
    class Point { constructor(x, y) { this.x = x; this.y = y } }
    window.kakao = { maps: { Map: MapView, LatLng, LatLngBounds, Marker, MarkerImage, Size, Point, event: { addListener: (marker, name, handler) => marker.element.addEventListener(name, handler) }, load: callback => callback() } }
  })
}

async function mockGoldenApi(page, { map = true } = {}) {
  if (map) await mockKakaoMap(page)
  else await page.route('https://dapi.kakao.com/**', route => route.abort())
  let anchored = false; let focused = false
  await page.route('**/api/**', async route => {
    const request = route.request(); const pathname = new URL(request.url()).pathname
    const json = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (pathname === '/api/guests' && request.method() === 'POST') return json({ publicId: guestId }, 201)
    if (pathname === `/api/guests/${guestId}/profiles` && request.method() === 'GET') return json([profile])
    if (pathname === `/api/guests/${guestId}/trips` && request.method() === 'POST') return json({ tripPublicId: tripId, profileId: 42, regionKey: 'JEJU', startDate: '2026-09-16', endDate: '2026-09-16', durationDays: 1, days: [{ dayNumber: 1, travelDate: '2026-09-16', mealSlots: [{ mealSlotPublicId: slotId, mealType: 'LUNCH', anchor: null }] }] }, 201)
    if (pathname === `/api/guests/${guestId}/trips/${tripId}` && request.method() === 'GET') return json({ tripPublicId: tripId, profileId: 42, regionKey: 'JEJU', startDate: '2026-09-16', endDate: '2026-09-16', durationDays: 1, days: [{ dayNumber: 1, travelDate: '2026-09-16', mealSlots: [{ mealSlotPublicId: slotId, mealType: 'LUNCH', anchor: anchored ? { provider: 'KTO', contentId: '123', contentType: '39' } : null }] }] })
    if (pathname.endsWith(`/meal-slots/${slotId}/recommendations`) && request.method() === 'POST') return json(recommendation)
    if (pathname.endsWith('/days/1/focus-recommendations') && request.method() === 'POST') return json({ slotType: 'DAY_FOCUS', candidates: [place], dataAvailability: 'CURRENT_DATA', sourceAttribution: '출처: ⓒ한국관광공사' })
    if (pathname.endsWith('/days/1/place-anchors/DAY_FOCUS') && request.method() === 'PUT') { focused = true; return json({ publicId: 'focus-ref', slotType: 'DAY_FOCUS', provider: 'KTO', contentId: '456', contentType: '12' }) }
    if (pathname.endsWith(`/meal-slots/${slotId}/anchor`) && request.method() === 'PUT') { anchored = true; return json({ provider: 'KTO', contentId: '123', contentType: '39' }) }
    if (pathname.endsWith('/days/1/place-recommendations') && request.method() === 'POST') return json({ slotType: 'AFTERNOON_ACTIVITY', candidates: [place], perspectives: [{ perspective: 'NEARBY_COURSE', status: 'READY', message: '현재 일정에서 이동 부담이 적은 장소를 우선했어요.', candidates: [place] }, { perspective: 'SIGNATURE_COURSE', status: 'READY', message: '조금 더 이동하더라도 공식 지역 대표성을 함께 고려했어요.', candidates: [{ ...place, perspective: 'SIGNATURE_COURSE', overallScore: 91 }] }], movementContext: 'READY', dataAvailability: 'CURRENT_DATA', sourceAttribution: '출처: ⓒ한국관광공사' })
    if (pathname.endsWith('/days/1/place-anchors/AFTERNOON_ACTIVITY') && request.method() === 'PUT') return json({ publicId: 'ref', slotType: 'AFTERNOON_ACTIVITY', provider: 'KTO', contentId: '456', contentType: '12' })
    if (pathname.endsWith('/days/1/planner') && request.method() === 'GET') {
      const items = []
      if (focused) items.push({ slotType: 'DAY_FOCUS', provider: 'KTO', contentId: '456', contentType: '12', title: '현재 관광지', address: '제주시 여행로', imageUrl: null, coordinates: { longitude: 126.5, latitude: 33.5 }, sourceAttribution: '출처: ⓒ한국관광공사', dataAvailability: 'CURRENT_DATA' })
      if (anchored) items.push({ slotType: 'LUNCH', provider: 'KTO', contentId: '123', contentType: '39', title: '현재 식당', address: '제주시 현재로', imageUrl: null, coordinates: { longitude: 126.53, latitude: 33.49 }, sourceAttribution: '출처: ⓒ한국관광공사', dataAvailability: 'CURRENT_DATA' })
      return json({ tripPublicId: tripId, dayNumber: 1, travelDate: '2026-09-16', items, legs: items.length > 1 ? [{ fromSlotType: 'DAY_FOCUS', toSlotType: 'LUNCH', mode: 'PUBLIC_TRANSIT', durationMinutes: 24, transferCount: 1, explicitWalkingDistanceMeters: 180, unaccountedDistanceMeters: 0, dataAvailability: 'CURRENT_DATA' }] : [] })
    }
    return json({ message: 'not found' }, 404)
  })
}

async function mockTwoDayTrip(page) {
  await mockKakaoMap(page)
  const plannerCalls = []
  const dayItems = {
    1: [{ slotType: 'DAY_FOCUS', provider: 'KTO', contentId: '1001', contentType: '12', title: '첨성대', address: '경주시 인왕동', imageUrl: null, coordinates: { longitude: 129.219, latitude: 35.835 }, sourceAttribution: '출처: ⓒ한국관광공사', dataAvailability: 'CURRENT_DATA' }, { slotType: 'STAY', provider: 'KTO', contentId: '1002', contentType: '32', title: '경주 숙소', address: '경주시 보문로', imageUrl: null, coordinates: { longitude: 129.28, latitude: 35.85 }, sourceAttribution: '출처: ⓒ한국관광공사', dataAvailability: 'CURRENT_DATA' }],
    2: [{ slotType: 'DAY_FOCUS', provider: 'KTO', contentId: '2001', contentType: '12', title: '불국사', address: '경주시 불국로', imageUrl: null, coordinates: { longitude: 129.331, latitude: 35.79 }, sourceAttribution: '출처: ⓒ한국관광공사', dataAvailability: 'CURRENT_DATA' }],
  }
  await page.route('**/api/**', async route => {
    const request = route.request(); const pathname = new URL(request.url()).pathname
    const json = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (pathname === '/api/guests' && request.method() === 'POST') return json({ publicId: guestId }, 201)
    if (pathname === `/api/guests/${guestId}/profiles`) return json([{ ...profile, name: '경주 가족', transportMode: 'PUBLIC_TRANSIT' }])
    if (pathname === `/api/guests/${guestId}/trips/${tripId}`) return json({ tripPublicId: tripId, profileId: 42, regionKey: 'GYEONGJU', startDate: '2026-09-20', endDate: '2026-09-21', durationDays: 2, days: [{ dayNumber: 1, travelDate: '2026-09-20', mealSlots: [{ mealSlotPublicId: 'day1-slot', mealType: 'LUNCH', anchor: { provider: 'KTO', contentId: '11', contentType: '39' } }] }, { dayNumber: 2, travelDate: '2026-09-21', mealSlots: [{ mealSlotPublicId: 'day2-slot', mealType: 'DINNER', anchor: { provider: 'KTO', contentId: '22', contentType: '39' } }] }] })
    const match = pathname.match(/\/days\/(\d+)\/planner$/)
    if (match) { const number = Number(match[1]); plannerCalls.push(number); return json({ tripPublicId: tripId, dayNumber: number, travelDate: number === 1 ? '2026-09-20' : '2026-09-21', items: dayItems[number], legs: [] }) }
    return json({ message: 'not found' }, 404)
  })
  return plannerCalls
}

async function selectDayFocus(page) {
  await page.getByRole('button', { name: '추천받기' }).click()
  await page.getByRole('button', { name: '이곳을 중심 장소로 선택' }).click()
}

async function createDayTrip(page) {
  await page.goto('/travel/new')
  await page.getByRole('button', { name: '다음' }).click()
  await page.getByRole('radio', { name: /제주/ }).check()
  await page.getByRole('button', { name: '다음' }).click()
  await page.getByRole('button', { name: '다음' }).click()
  await page.getByRole('button', { name: '다음' }).click()
  await page.getByRole('button', { name: '여행 만들기' }).click()
  await expect(page).toHaveURL(new RegExp(`/travel/${tripId}$`))
}

for (const width of [360, 390, 768]) {
  test(`여행 Wizard와 라이브 추천 카드가 ${width}px에서 잘리지 않는다`, async ({ page }) => {
    await mockGoldenApi(page); await page.setViewportSize({ width, height: 844 })
    const errors = []; page.on('pageerror', error => errors.push(error.message))
    await createDayTrip(page)
    await selectDayFocus(page)
    await page.getByRole('button', { name: '점심 TOP3 보기' }).click()
    await expect(page.getByRole('heading', { name: '식당 추천' })).toBeVisible()
    await expect(page.locator('.decision-results .live-card').filter({ hasText: '현재 식당' }).getByText('출처: ⓒ한국관광공사')).toBeVisible()
    await expect(page.getByText('지역 방문 수요')).toBeVisible()
    await expect(page.getByText('후기/평판')).toBeVisible()
    await expect(page.getByText('미평가')).toBeVisible()
    await expect(page.getByText('중심 장소에서 직선거리 약 1.8km')).toBeVisible()
    await expect(page.getByRole('link', { name: '전화하기' })).toHaveAttribute('href', 'tel:0641234567')
    await expect(page.getByRole('link', { name: '지도·후기 보기' })).toHaveAttribute('target', '_blank')
    await expect(page.locator('.nutrition-line')).toContainText('표준 음식 기준')
    await expect(page.locator('.nutrition-line')).toContainText('유사 음식 기준')
    await expect(page.getByText(/공개된 메뉴 정보가 부족해/)).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    expect(errors).toEqual([])
  })
}

test('식당과 관광지를 선택하고 지도·카드 focus와 Kakao 이동정보를 렌더한다', async ({ page }) => {
  await mockGoldenApi(page); await page.setViewportSize({ width: 390, height: 844 })
  await createDayTrip(page)
  await selectDayFocus(page)
  await page.getByRole('button', { name: '점심 TOP3 보기' }).click()
  await page.getByRole('button', { name: '이 식당 선택' }).click()
  await page.getByRole('button', { name: '관광지 추천 보기' }).click()
  await expect(page.getByRole('tab', { name: '가까운 코스' })).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByText(/직선거리 약 4.2km/)).toBeVisible()
  await expect(page.getByText(/실제 도로 이동시간이 아님/).first()).toBeVisible()
  await page.getByRole('tab', { name: '대표 명소 코스' }).click()
  await expect(page.getByRole('tab', { name: '대표 명소 코스' })).toHaveAttribute('aria-selected', 'true')
  await page.getByRole('button', { name: '이 관광지 선택' }).click()
  await page.getByRole('button', { name: '오늘 일정 보기' }).click()
  await expect(page.getByRole('heading', { name: '지도와 오늘의 일정' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '지도와 오늘의 일정' })).toBeFocused()
  await expect(page.getByTestId('day-map')).toBeVisible()
  await expect(page.getByText(/대중교통 약 24분/)).toBeVisible()
  await expect(page.locator('.planner-card h3')).toHaveText(['현재 관광지', '현재 식당'])
  await expect(page.locator('.planner-card').getByText('출처: ⓒ한국관광공사')).toHaveCount(2)
  await page.getByRole('button', { name: '2번 현재 식당' }).click()
  await expect(page.locator('.planner-card').nth(1)).toHaveClass(/active/)
  await page.locator('.planner-card').first().click()
  await expect(page.getByRole('button', { name: '1번 현재 관광지' })).toHaveClass(/active/)
  const keys = await page.evaluate(() => Object.keys(localStorage))
  expect(keys.filter(key => key !== 'hankki.guest-public-id')).toEqual([])
})

test('관광지 추천 502는 해당 영역에만 표시하고 기존 Planner를 유지한다', async ({ page }) => {
  await mockGoldenApi(page)
  await page.route('**/api/**/place-recommendations', route => route.fulfill({ status: 502, contentType: 'application/json', body: '{}' }))
  await createDayTrip(page)
  await selectDayFocus(page)
  await page.getByRole('button', { name: '점심 TOP3 보기' }).click()
  await page.getByRole('button', { name: '이 식당 선택' }).click()
  await page.getByRole('button', { name: '관광지 추천 보기' }).click()

  await expect(page.getByRole('heading', { name: '관광지 추천' })).toBeVisible()
  await expect(page.getByText('관광지 추천을 현재 불러오지 못했어요.')).toBeVisible()
  await expect(page.locator('.planner-card h3')).toHaveText(['현재 관광지', '현재 식당'])
  await expect(page.getByRole('button', { name: '오늘 일정 보기' })).toBeVisible()
  await expect(page.getByText('NetworkError when attempting to fetch resource.')).toHaveCount(0)
})

test('숙소 추천 502는 해당 영역에만 표시하고 저장된 선택을 유지한다', async ({ page }) => {
  await mockGoldenApi(page)
  await page.route('**/api/**', async route => {
    const request = route.request(); const pathname = new URL(request.url()).pathname
    const json = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (pathname === `/api/guests/${guestId}/trips/${tripId}` && request.method() === 'GET') {
      return json({ tripPublicId: tripId, profileId: 42, regionKey: 'JEJU', startDate: '2026-09-16', endDate: '2026-09-17', durationDays: 2, days: [{ dayNumber: 1, travelDate: '2026-09-16', mealSlots: [{ mealSlotPublicId: slotId, mealType: 'LUNCH', anchor: { provider: 'KTO', contentId: '123', contentType: '39' } }] }, { dayNumber: 2, travelDate: '2026-09-17', mealSlots: [] }] })
    }
    if (pathname.endsWith('/days/1/planner') && request.method() === 'GET') {
      return json({ tripPublicId: tripId, dayNumber: 1, travelDate: '2026-09-16', items: [{ slotType: 'DAY_FOCUS', provider: 'KTO', contentId: '456', contentType: '12', title: '현재 관광지', address: '제주시 여행로', imageUrl: null, coordinates: { longitude: 126.5, latitude: 33.5 }, sourceAttribution: '출처: ⓒ한국관광공사', dataAvailability: 'CURRENT_DATA' }, { slotType: 'LUNCH', provider: 'KTO', contentId: '123', contentType: '39', title: '현재 식당', address: '제주시 현재로', imageUrl: null, coordinates: { longitude: 126.53, latitude: 33.49 }, sourceAttribution: '출처: ⓒ한국관광공사', dataAvailability: 'CURRENT_DATA' }], legs: [] })
    }
    if (pathname.endsWith('/days/1/stay-recommendations') && request.method() === 'POST') return json({}, 502)
    return route.fallback()
  })

  await page.goto(`/travel/${tripId}`)
  await expect(page.locator('.planner-card h3')).toHaveText(['현재 관광지', '현재 식당'])
  await page.getByRole('button', { name: '숙소 추천 보기' }).click()

  await expect(page.getByRole('heading', { name: '숙소 추천' })).toBeVisible()
  await expect(page.getByText('숙소 추천을 현재 불러오지 못했어요.')).toBeVisible()
  await expect(page.locator('.planner-card h3')).toHaveText(['현재 관광지', '현재 식당'])
  await expect(page.getByRole('button', { name: '오늘 일정 보기' })).toBeVisible()
})

test('GYEONGJU 1박2일은 선택 Day만 hydrate하고 marker를 완전히 교체한다', async ({ page }) => {
  const plannerCalls = await mockTwoDayTrip(page)
  await page.setViewportSize({ width: 768, height: 900 })
  await page.goto(`/travel/${tripId}`)
  await expect(page.getByRole('heading', { name: '경주 1박 2일' })).toBeVisible()
  await expect(page.getByRole('button', { name: '1번 첨성대' })).toBeVisible()
  expect(plannerCalls).toEqual([1])
  await page.getByRole('button', { name: /DAY 2/ }).click()
  await expect(page.getByRole('button', { name: '1번 불국사' })).toBeVisible()
  await expect(page.getByRole('button', { name: '1번 첨성대' })).toHaveCount(0)
  expect(plannerCalls).toEqual([1, 2])
})

test('Kakao SDK 설정이 없어도 timeline은 계속 사용할 수 있다', async ({ page }) => {
  await mockGoldenApi(page, { map: false })
  await createDayTrip(page)
  await selectDayFocus(page)
  await expect(page.getByTestId('map-error')).toBeVisible()
  await expect(page.locator('.planner-card h3')).toHaveText(['현재 관광지'])
})

test('PWA는 API 응답을 장기 캐시하지 않는다', async ({}, testInfo) => {
  const context = await chromium.launchPersistentContext(testInfo.outputPath('pwa-profile'), { channel: 'msedge', headless: true, viewport: { width: 390, height: 844 } })
  const page = await context.newPage()
  try {
    await mockGoldenApi(page); await page.goto('/'); await page.evaluate(async () => { await navigator.serviceWorker.ready }); await page.reload(); await page.waitForFunction(() => Boolean(navigator.serviceWorker.controller))
    const cachedUrls = await page.evaluate(async () => { const urls = []; for (const key of await caches.keys()) for (const request of await (await caches.open(key)).keys()) urls.push(new URL(request.url).pathname); return urls })
    expect(cachedUrls.filter(url => /^\/(api|actuator)/.test(url))).toEqual([])
  } finally { await context.close() }
})
