import { test, expect, chromium } from '@playwright/test'

const guestId = '9d4f2c3a-6d29-4c12-8a70-2c6a2f87b111'
const tripId = 'c4b44ec1-ae62-4568-a7a9-25e4469d5d11'
const slotId = '75a9d2bb-379f-42fc-bb36-047cbd34b853'
const profile = { profileId: 42, name: '부모님과 제주', transportMode: 'CAR', parkingPreference: 'REQUIRED', walkingBurdenPreference: 'NORMAL', transferPreference: 'AVOID', stairsAvoidance: true, members: [{ memberId: 7, nickname: '엄마', continuousWalkingMinutes: 20, stairsPreference: 'AVOID', mealCautions: ['SODIUM'] }] }
const candidate = { contentId: '123', title: '현재 식당', areaLabel: '제주시 현재로', address: '제주시 현재로', imageUrl: null, compatibilityScore: 82, overallScore: 82, evidenceCoverage: 60, informationEvidence: 'REFERENCE', phone: '064-123-4567', contactEvidence: 'TOUR_API_LIVE', ourFamilyFitReasons: ['공개 정보에서 확인한 근거예요.'], ourFamilyCautions: ['원재료는 직접 확인해 주세요.'], checkBeforeVisit: ['운영시간을 방문 전에 확인해 주세요.'], nutritionEvidence: [{ menuName: '비빔밥', matchLevel: 'HIGH', standardFood: '비빔밥', referenceLabel: '표준 음식 기준' }], evaluatedDimensions: [{ code: 'FAMILY_MEAL_FIT', label: '식사 조건', weight: 40, evaluated: true, awardedPoints: 34, maxPoints: 40, evidenceState: 'EVALUATED' }] }
const place = { contentId: '456', contentType: '12', title: '현재 관광지', imageUrl: null, address: '제주시 여행로', coordinates: { longitude: 126.5, latitude: 33.5 }, informationEvidence: 'CURRENT', fitReasons: ['현재 일정 흐름과 함께 볼 수 있어요.'], checkBeforeVisit: ['운영시간을 확인해 주세요.'], sourceAttribution: '출처: ⓒ한국관광공사' }
const recommendation = { topCandidates: [candidate], perspectives: [{ perspective: 'BALANCED', status: 'READY', message: null, candidates: [candidate] }], candidateCount: 1, dataAvailability: 'CURRENT_DATA', sourceAttribution: '출처: ⓒ한국관광공사', nutritionNotice: '표준 음식 기준 또는 유사 음식 기준 참고정보입니다.' }

async function mockGoldenApi(page) {
  let anchored = false
  await page.route('**/api/**', async route => {
    const request = route.request(); const pathname = new URL(request.url()).pathname
    const json = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (pathname === '/api/guests' && request.method() === 'POST') return json({ publicId: guestId }, 201)
    if (pathname === `/api/guests/${guestId}/profiles` && request.method() === 'GET') return json([profile])
    if (pathname === `/api/guests/${guestId}/trips` && request.method() === 'POST') return json({ tripPublicId: tripId, profileId: 42, regionKey: 'JEJU', startDate: '2026-09-16', endDate: '2026-09-16', durationDays: 1, days: [{ dayNumber: 1, travelDate: '2026-09-16', mealSlots: [{ mealSlotPublicId: slotId, mealType: 'LUNCH', anchor: null }] }] }, 201)
    if (pathname === `/api/guests/${guestId}/trips/${tripId}` && request.method() === 'GET') return json({ tripPublicId: tripId, profileId: 42, regionKey: 'JEJU', startDate: '2026-09-16', endDate: '2026-09-16', durationDays: 1, days: [{ dayNumber: 1, travelDate: '2026-09-16', mealSlots: [{ mealSlotPublicId: slotId, mealType: 'LUNCH', anchor: anchored ? { provider: 'KTO', contentId: '123', contentType: '39' } : null }] }] })
    if (pathname.endsWith(`/meal-slots/${slotId}/recommendations`) && request.method() === 'POST') return json(recommendation)
    if (pathname.endsWith('/days/1/focus-recommendations') && request.method() === 'POST') return json({ slotType: 'DAY_FOCUS', candidates: [place], dataAvailability: 'CURRENT_DATA', sourceAttribution: '출처: ⓒ한국관광공사' })
    if (pathname.endsWith('/days/1/place-anchors/DAY_FOCUS') && request.method() === 'PUT') return json({ publicId: 'focus-ref', slotType: 'DAY_FOCUS', provider: 'KTO', contentId: '456', contentType: '12' })
    if (pathname.endsWith(`/meal-slots/${slotId}/anchor`) && request.method() === 'PUT') { anchored = true; return json({ provider: 'KTO', contentId: '123', contentType: '39' }) }
    if (pathname.endsWith('/days/1/place-recommendations') && request.method() === 'POST') return json({ slotType: 'AFTERNOON_ACTIVITY', candidates: [place], dataAvailability: 'CURRENT_DATA', sourceAttribution: '출처: ⓒ한국관광공사' })
    if (pathname.endsWith('/days/1/place-anchors/AFTERNOON_ACTIVITY') && request.method() === 'PUT') return json({ publicId: 'ref', slotType: 'AFTERNOON_ACTIVITY', provider: 'KTO', contentId: '456', contentType: '12' })
    if (pathname.endsWith('/days/1/planner') && request.method() === 'GET') return json({ tripPublicId: tripId, dayNumber: 1, travelDate: '2026-09-16', items: [{ slotType: 'LUNCH', provider: 'KTO', contentId: '123', contentType: '39', title: '현재 식당', address: '제주시 현재로', imageUrl: null, sourceAttribution: '출처: ⓒ한국관광공사', dataAvailability: 'CURRENT_DATA' }, { slotType: 'AFTERNOON_ACTIVITY', provider: 'KTO', contentId: '456', contentType: '12', title: '현재 관광지', address: '제주시 여행로', imageUrl: null, sourceAttribution: '출처: ⓒ한국관광공사', dataAvailability: 'CURRENT_DATA' }], legs: [{ fromSlotType: 'LUNCH', toSlotType: 'AFTERNOON_ACTIVITY', mode: 'PUBLIC_TRANSIT', durationMinutes: 24, transferCount: 1, explicitWalkingDistanceMeters: 180, unaccountedDistanceMeters: 0, dataAvailability: 'CURRENT_DATA' }] })
    return json({ message: 'not found' }, 404)
  })
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
    await expect(page.getByText('출처: ⓒ한국관광공사')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    expect(errors).toEqual([])
  })
}

test('식당과 관광지를 선택하고 Kakao 이동정보가 포함된 Planner를 렌더한다', async ({ page }) => {
  await mockGoldenApi(page); await page.setViewportSize({ width: 390, height: 844 })
  await createDayTrip(page)
  await selectDayFocus(page)
  await page.getByRole('button', { name: '점심 TOP3 보기' }).click()
  await page.getByRole('button', { name: '이 식당 선택' }).click()
  await page.getByRole('button', { name: '관광지 추천 보기' }).click()
  await page.getByRole('button', { name: '이 관광지 선택' }).click()
  await page.getByRole('button', { name: '오늘 일정 보기' }).click()
  await expect(page.getByRole('heading', { name: '오늘의 일정' })).toBeVisible()
  await expect(page.getByText(/대중교통 예상 24분/)).toBeVisible()
  await expect(page.locator('.planner-card h3')).toHaveText(['현재 식당', '현재 관광지'])
  await expect(page.locator('.planner-card').getByText('출처: ⓒ한국관광공사')).toHaveCount(2)
  const keys = await page.evaluate(() => Object.keys(localStorage))
  expect(keys.filter(key => key !== 'hankki.guest-public-id')).toEqual([])
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
