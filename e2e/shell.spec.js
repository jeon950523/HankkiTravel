import { test, expect, chromium } from '@playwright/test'

const guestId = '9d4f2c3a-6d29-4c12-8a70-2c6a2f87b111'
const tripId = 'c4b44ec1-ae62-4568-a7a9-25e4469d5d11'
const slotId = '75a9d2bb-379f-42fc-bb36-047cbd34b853'
const profile = { profileId: 42, name: '부모님과 제주', transportMode: 'CAR', parkingPreference: 'REQUIRED', walkingBurdenPreference: 'NORMAL', transferPreference: 'AVOID', stairsAvoidance: true, members: [{ memberId: 7, nickname: '엄마', continuousWalkingMinutes: 20, stairsPreference: 'AVOID', mealCautions: ['SODIUM'] }] }
const candidate = { contentId: '123', title: '현재 식당', areaLabel: '제주시 현재로', address: '제주시 현재로', coordinates: { longitude: 126.53, latitude: 33.49 }, imageUrl: null, compatibilityScore: 82, overallScore: 82, evidenceCoverage: 70, informationEvidence: 'REFERENCE', distanceMeters: 1800, phone: '064-123-4567', placeUrl: 'https://place.map.kakao.com/123', contactEvidence: 'KTO_DIRECT', menuSummary: ['갈치조림', '성게미역국'], ourFamilyFitReasons: ['공개 정보에서 확인한 근거예요.'], ourFamilyCautions: ['원재료는 직접 확인해 주세요.'], checkBeforeVisit: ['공개된 메뉴 정보가 부족해 식사 조건을 충분히 비교하기 어려워요. 방문 전에 전화로 재료와 조리법을 확인해 주세요.'], nutritionEvidence: [{ menuName: '비빔밥', matchLevel: 'HIGH', standardFood: '비빔밥', referenceLabel: '표준 음식 기준' }, { menuName: '돌솥밥', matchLevel: 'MEDIUM', standardFood: '비빔밥', referenceLabel: '유사 음식 기준' }], evaluatedDimensions: [{ code: 'FAMILY_MEAL_FIT', label: '식사 조건', weight: 40, evaluated: true, awardedPoints: 34, maxPoints: 40, evidenceState: 'EVALUATED' }, { code: 'AREA_DEMAND_SIGNAL', label: '지역 방문 수요', weight: 10, evaluated: true, awardedPoints: 8, maxPoints: 10, evidenceState: 'EVALUATED' }, { code: 'REVIEW_SIGNAL', label: '후기/평판', weight: 10, evaluated: false, awardedPoints: 0, maxPoints: 0, evidenceState: 'NOT_EVALUATED' }] }
const replacementCandidate = { ...candidate, contentId: '124', title: '새 식당', address: '제주시 새로', coordinates: { longitude: 126.55, latitude: 33.48 }, phone: null, placeUrl: null, menuSummary: [], overallScore: 74, compatibilityScore: 74, evidenceCoverage: 30 }
const place = { contentId: '456', contentType: '12', title: '현재 관광지', areaLabel: '제주시', imageUrl: null, address: '제주시 여행로', coordinates: { longitude: 126.5, latitude: 33.5 }, informationEvidence: 'TOUR_API_LIVE', perspective: 'NEARBY_COURSE', overallScore: 88, evidenceCoverage: 100, routeBurden: { state: 'REFERENCE', level: 'LOW', score: 90 }, distanceMeters: 4200, transitSummary: null, familyMobilityEvidence: ['자동차는 직선거리 기준 참고값입니다.'], reasons: ['현재 일정에서 이동 부담이 적어요.'], cautions: ['실제 도로 이동시간이 아닙니다. 자동차 이동시간은 제공하지 않아요.'], fitReasons: ['현재 일정에서 이동 부담이 적어요.'], checkBeforeVisit: ['실제 도로 이동시간이 아닙니다.'], sourceAttribution: '출처: ⓒ한국관광공사' }
const recommendation = { topCandidates: [candidate], perspectives: [{ perspective: 'BALANCED', status: 'READY', message: null, candidates: [candidate] }], candidateCount: 1, dataAvailability: 'CURRENT_DATA', sourceAttribution: '출처: ⓒ한국관광공사', nutritionNotice: '표준 음식 기준 또는 유사 음식 기준 참고정보입니다.', recommendationContext: { originSlotType: 'DAY_FOCUS', originTitle: '현재 관광지', originSource: 'CHRONOLOGICAL_ANCHOR' } }

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
  let anchored = false; let focused = false; let selectedRestaurant = ''
  await page.route('**/api/**', async route => {
    const request = route.request(); const pathname = new URL(request.url()).pathname
    const json = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (pathname === '/api/guests' && request.method() === 'POST') return json({ publicId: guestId }, 201)
    if (pathname === `/api/guests/${guestId}/profiles` && request.method() === 'GET') return json([profile])
    if (pathname === `/api/guests/${guestId}/trips` && request.method() === 'POST') return json({ tripPublicId: tripId, profileId: 42, regionKey: 'JEJU', startDate: '2026-09-16', endDate: '2026-09-16', durationDays: 1, days: [{ dayNumber: 1, travelDate: '2026-09-16', mealSlots: [{ mealSlotPublicId: slotId, mealType: 'LUNCH', anchor: null }] }] }, 201)
    if (pathname === `/api/guests/${guestId}/trips/${tripId}` && request.method() === 'GET') return json({ tripPublicId: tripId, profileId: 42, regionKey: 'JEJU', startDate: '2026-09-16', endDate: '2026-09-16', durationDays: 1, days: [{ dayNumber: 1, travelDate: '2026-09-16', mealSlots: [{ mealSlotPublicId: slotId, mealType: 'LUNCH', anchor: anchored ? { provider: 'KTO', contentId: selectedRestaurant || '123', contentType: '39' } : null }] }] })
    if (pathname.endsWith(`/meal-slots/${slotId}/recommendations`) && request.method() === 'POST') return json(anchored ? { ...recommendation, topCandidates: [replacementCandidate], perspectives: [{ perspective: 'BALANCED', status: 'READY', message: null, candidates: [replacementCandidate] }] } : recommendation)
    if (pathname.endsWith('/days/1/restaurant-search') && request.method() === 'GET') return json({ slotType: 'LUNCH', candidates: [replacementCandidate], dataAvailability: 'CURRENT_DATA', sourceAttribution: '출처: ⓒ한국관광공사', recommendationContext: { originSlotType: 'DAY_FOCUS', originTitle: '현재 관광지', originSource: 'CHRONOLOGICAL_ANCHOR' } })
    if (pathname.endsWith('/days/1/dessert-recommendations') && request.method() === 'POST') return json({ slotType: 'POST_LUNCH_DESSERT', candidates: [], dataAvailability: 'CURRENT_DATA', sourceAttribution: '출처: ⓒ한국관광공사', recommendationContext: { originSlotType: 'LUNCH', originTitle: '현재 식당', originSource: 'CHRONOLOGICAL_ANCHOR' } })
    if (pathname.endsWith('/days/1/focus-recommendations') && request.method() === 'POST') return json({ slotType: 'DAY_FOCUS', candidates: [place], dataAvailability: 'CURRENT_DATA', sourceAttribution: '출처: ⓒ한국관광공사' })
    if (pathname.endsWith('/days/1/place-anchors/DAY_FOCUS') && request.method() === 'PUT') { focused = true; return json({ publicId: 'focus-ref', slotType: 'DAY_FOCUS', provider: 'KTO', contentId: '456', contentType: '12' }) }
    if (pathname.endsWith(`/meal-slots/${slotId}/anchor`) && request.method() === 'PUT') { anchored = true; selectedRestaurant = JSON.parse(request.postData() || '{}').contentId; return json({ provider: 'KTO', contentId: selectedRestaurant, contentType: '39' }) }
    if (pathname.endsWith('/days/1/place-recommendations') && request.method() === 'POST') return json({ slotType: 'AFTERNOON_ACTIVITY', candidates: [place], perspectives: [{ perspective: 'NEARBY_COURSE', status: 'READY', message: '현재 일정에서 이동 부담이 적은 장소를 우선했어요.', candidates: [place] }, { perspective: 'SIGNATURE_COURSE', status: 'READY', message: '조금 더 이동하더라도 공식 지역 대표성을 함께 고려했어요.', candidates: [{ ...place, perspective: 'SIGNATURE_COURSE', overallScore: 91 }] }], movementContext: 'READY', recommendationContext: { originSlotType: 'LUNCH', originTitle: '현재 식당', originSource: 'CHRONOLOGICAL_ANCHOR' }, dataAvailability: 'CURRENT_DATA', sourceAttribution: '출처: ⓒ한국관광공사' })
    if (pathname.endsWith('/days/1/place-anchors/AFTERNOON_ACTIVITY') && request.method() === 'PUT') return json({ publicId: 'ref', slotType: 'AFTERNOON_ACTIVITY', provider: 'KTO', contentId: '456', contentType: '12' })
    if (pathname.endsWith('/days/1/planner') && request.method() === 'GET') {
      const items = []
      if (focused) items.push({ slotType: 'DAY_FOCUS', provider: 'KTO', contentId: '456', contentType: '12', title: '현재 관광지', address: '제주시 여행로', imageUrl: null, coordinates: { longitude: 126.5, latitude: 33.5 }, sourceAttribution: '출처: ⓒ한국관광공사', dataAvailability: 'CURRENT_DATA' })
      if (anchored) { const selected = selectedRestaurant === '124' ? replacementCandidate : candidate; items.push({ slotType: 'LUNCH', provider: 'KTO', contentId: selected.contentId, contentType: '39', title: selected.title, address: selected.address, imageUrl: null, coordinates: selected.coordinates, sourceAttribution: '출처: ⓒ한국관광공사', dataAvailability: 'CURRENT_DATA', telephone: selected.phone, placeUrl: selected.placeUrl, menuSummary: selected.menuSummary }) }
      const legs = items.length > 1 ? [{ fromSlotType: 'DAY_FOCUS', toSlotType: 'LUNCH', mode: 'PUBLIC_TRANSIT', durationMinutes: 195, transferCount: 3, explicitWalkingDistanceMeters: 180, unaccountedDistanceMeters: 0, dataAvailability: 'CURRENT_DATA', burdenSeverity: 'HIGH', burdenReasons: ['대중교통 이동 시간이 매우 긴 구간이에요.'] }] : []
      const routeSanity = legs.length ? { state: 'EVALUATED', severity: 'HIGH', evidenceCoverage: 100, evaluatedLegCount: 1, totalLegCount: 1, longestLeg: { legIndex: 0, fromSlotType: 'DAY_FOCUS', toSlotType: 'LUNCH', mode: 'PUBLIC_TRANSIT', durationMinutes: 195, straightDistanceMeters: null, severity: 'HIGH' }, totalTransitMinutes: 195, totalStraightDistanceMeters: null, totalTransfers: 3, totalExplicitWalkingDistanceMeters: 180, reasons: ['확인 가능한 이동 구간 중 긴 이동이 1개 있어요.'], suggestedActions: ['NEAR_RESTAURANT', 'KEEP_ITINERARY'] } : { state: 'NOT_EVALUATED', severity: 'NOT_EVALUATED', evidenceCoverage: 0, evaluatedLegCount: 0, totalLegCount: 0, longestLeg: null, reasons: [], suggestedActions: [] }
      return json({ tripPublicId: tripId, dayNumber: 1, travelDate: '2026-09-16', items, legs, routeSanity })
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
  await page.getByRole('button', { name: '이곳을 시작 장소로 선택' }).click()
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

async function mockProfileSaveApi(page, { fail = false, delay = 0 } = {}) {
  await page.route('**/api/**', async route => {
    const request = route.request(); const pathname = new URL(request.url()).pathname
    const json = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (pathname === '/api/guests' && request.method() === 'POST') return json({ publicId: guestId }, 201)
    if (pathname === `/api/guests/${guestId}/profiles` && request.method() === 'POST') {
      if (delay) await new Promise(resolve => setTimeout(resolve, delay))
      if (fail) return json({ message: '저장 서버를 확인해 주세요.' }, 500)
      return json(profile, 201)
    }
    if (pathname === `/api/guests/${guestId}/profiles` && request.method() === 'GET') return json([profile])
    return json({ message: 'not found' }, 404)
  })
}

for (const width of [360, 390, 768]) {
  test(`프로필 저장 성공과 선택 handoff가 ${width}px에서 유지된다`, async ({ page }) => {
    await mockProfileSaveApi(page, { delay: 200 })
    await page.setViewportSize({ width, height: 844 })
    await page.goto('/profiles/new')
    await page.getByLabel('프로필 이름').fill(profile.name)
    await page.getByLabel('부르는 이름').fill('엄마')
    const saveButton = page.getByRole('button', { name: '프로필 저장' })
    await saveButton.click()
    const activeSaveButton = page.locator('.profile-form button[type="submit"]')
    await expect(activeSaveButton).toBeDisabled()
    await expect(activeSaveButton).toHaveText('저장 중...')
    await expect(page.getByText('✓ 가족 프로필이 저장됐어요.')).toBeVisible()
    await expect(page).toHaveURL(`/profiles/${profile.profileId}/edit`)
    await expect(page.getByText('이제 이 조건으로 여행을 시작해볼까요?')).toBeVisible()
    await expect(page.getByRole('button', { name: '저장됨 ✓' })).toBeVisible()
    await expect(page.getByLabel('알레르기 주의 재료')).toHaveCSS('height', '48px')
    await expect(page.getByLabel('피하고 싶은 음식/재료')).toHaveCSS('height', '48px')
    await page.getByRole('link', { name: '이 프로필로 여행 시작하기' }).click()
    await expect(page).toHaveURL(`/travel/new?profileId=${profile.profileId}`)
    await expect(page.getByRole('radio', { name: new RegExp(profile.name) })).toBeChecked()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  })
}

test('프로필 저장 실패는 입력을 보존하고 다시 시도 상태를 제공한다', async ({ page }) => {
  await mockProfileSaveApi(page, { fail: true })
  await page.goto('/profiles/new')
  await page.getByLabel('프로필 이름').fill('보존할 가족 프로필')
  await page.getByLabel('부르는 이름').fill('보존할 이름')
  await page.getByRole('button', { name: '프로필 저장' }).click()
  await expect(page.getByRole('alert')).toContainText('입력 내용은 그대로 유지했어요.')
  await expect(page.getByLabel('프로필 이름')).toHaveValue('보존할 가족 프로필')
  await expect(page.getByLabel('부르는 이름')).toHaveValue('보존할 이름')
  await expect(page.getByRole('button', { name: '다시 시도' })).toBeVisible()
})

for (const width of [360, 390, 768]) {
  test(`여행 Wizard와 라이브 추천 카드가 ${width}px에서 잘리지 않는다`, async ({ page }) => {
    await mockGoldenApi(page); await page.setViewportSize({ width, height: 844 })
    const errors = []; page.on('pageerror', error => errors.push(error.message))
    await createDayTrip(page)
    await selectDayFocus(page)
    await page.getByRole('button', { name: '점심 TOP3 보기' }).click()
    await expect(page.getByRole('heading', { name: '식당 추천' })).toBeVisible()
    await expect(page.getByText('오늘 여행의 시작 장소로 선택한 현재 관광지 주변에서 찾았어요.')).toBeVisible()
    await expect(page.locator('.decision-results .live-card').filter({ hasText: '현재 식당' }).getByText('출처: ⓒ한국관광공사')).toBeVisible()
    await expect(page.getByText('지역 방문 수요')).toBeVisible()
    await expect(page.getByText('후기/평판')).toBeVisible()
    await expect(page.getByText('미평가', { exact: true })).toBeVisible()
    await expect(page.getByText('확인된 정보 기준 적합도')).toBeVisible()
    await expect(page.getByText('갈치조림 · 성게미역국')).toBeVisible()
    await expect(page.getByText(/미평가 항목은 점수를 깎지 않았어요/)).toBeVisible()
    await expect(page.getByText('중심 장소에서 직선거리 약 1.8km')).toBeVisible()
    await expect(page.getByRole('link', { name: '카카오맵에서 실제 경로 보기' })).toHaveAttribute('href', /\/link\/by\/car\/.*33\.5,126\.5\/.*33\.49,126\.53/)
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
  await expect(page.getByText('점심 식당으로 선택한 현재 식당 주변에서 찾았어요.')).toBeVisible()
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
  await expect(page.getByText(/대중교통 약 195분/)).toBeVisible()
  await expect(page.getByText('오늘 이동 부담 · 높음')).toBeVisible()
  await page.getByRole('button', { name: '문제 구간 보기' }).click()
  await expect(page.locator('.planner-leg')).toHaveClass(/active/)
  await page.getByRole('button', { name: '가까운 식당 다시 보기' }).click()
  await expect(page.getByText('현재 식당').last()).toBeVisible()
  await page.getByRole('button', { name: '그래도 이 일정 유지' }).click()
  await expect(page.getByText(/현재 선택을 유지했어요/)).toBeVisible()
  await expect(page.locator('.planner-card h3')).toHaveText(['현재 관광지', '현재 식당'])
  await expect(page.locator('.planner-card').getByText('출처: ⓒ한국관광공사')).toHaveCount(2)
  await page.getByRole('button', { name: '2번 현재 식당' }).click()
  await expect(page.locator('.planner-card').nth(1)).toHaveClass(/active/)
  await page.locator('.planner-card').first().click()
  await expect(page.getByRole('button', { name: '1번 현재 관광지' })).toHaveClass(/active/)
  const keys = await page.evaluate(() => Object.keys(localStorage))
  expect(keys.filter(key => key !== 'hankki.guest-public-id')).toEqual([])
})

test('미완료 여행이 Final Plan URL로 직접 진입하면 Journey 안내를 보여준다', async ({ page }) => {
  await mockGoldenApi(page)
  await page.goto(`/travel/${tripId}/plan`)
  await expect(page.getByRole('heading', { name: '아직 여행 준비가 끝나지 않았어요.' })).toBeVisible()
  await expect(page.getByRole('link', { name: '이어서 준비하기' })).toHaveAttribute('href', `/travel/${tripId}`)
  await expect(page.getByText('추천 식당 보기')).toHaveCount(0)
})

test('완료된 여행의 Final Plan은 결과만 보이고 수정은 Journey로 돌아간다', async ({ page }) => {
  await mockGoldenApi(page)
  await createDayTrip(page)
  await selectDayFocus(page)
  await page.getByRole('button', { name: '점심 TOP3 보기' }).click()
  await page.getByRole('button', { name: '이 식당 선택' }).click()
  await page.goto(`/travel/${tripId}/plan`)
  await expect(page.getByRole('heading', { name: /나의 제주 0박 1일 여행 플랜/ })).toBeVisible()
  await expect(page.getByText('현재 관광지')).toBeVisible()
  await expect(page.getByText('현재 식당')).toBeVisible()
  await expect(page.getByText('이 Day의 첫 장소예요.')).toBeVisible()
  await expect(page.getByText(/이전 장소에서 대중교통 약 195분/)).toBeVisible()
  await expect(page.getByText('갈치조림 · 성게미역국')).toBeVisible()
  await expect(page.getByRole('link', { name: '카카오맵에서 보기' })).toBeVisible()
  await expect(page.getByRole('link', { name: '지도·후기 보기' })).toBeVisible()
  await expect(page.getByRole('link', { name: '길찾기' })).toHaveAttribute('href', /\/link\/by\/traffic\//)
  await expect(page.getByRole('link', { name: '카카오맵에서 경로 보기' })).toHaveAttribute('href', /\/link\/by\/traffic\//)
  await expect(page.getByRole('link', { name: '전화하기' })).toHaveAttribute('href', 'tel:0641234567')
  const strictMap = page.getByRole('link', { name: '지도·후기 보기' })
  await expect(strictMap).toHaveAttribute('href', 'https://place.map.kakao.com/123')
  await expect(strictMap).toHaveAttribute('target', '_blank')
  await expect(strictMap).toHaveAttribute('rel', 'noopener noreferrer')
  await expect(page.getByText(/추천 식당 보기|다른 식당 보기|직접 찾기|선택 해제/)).toHaveCount(0)
  await page.getByRole('link', { name: '여행 수정하기' }).click()
  await expect(page).toHaveURL(new RegExp(`/travel/${tripId}\\?edit=1$`))
})

test('Final Plan에서 수정 진입 후 추천·검색·교체와 fresh hydrate가 이어진다', async ({ page }) => {
  await mockGoldenApi(page)
  await createDayTrip(page)
  await selectDayFocus(page)
  await page.getByRole('button', { name: '점심 TOP3 보기' }).click()
  await page.getByRole('button', { name: '이 식당 선택' }).click()
  await page.goto(`/travel/${tripId}/plan`)

  await page.getByRole('link', { name: 'DAY 1 수정' }).click()
  await expect(page).toHaveURL(new RegExp(`/travel/${tripId}\\?day=1&edit=1$`))
  await expect(page.getByRole('heading', { name: '완료된 선택을 변경할 수 있어요.' }).first()).toBeVisible()
  await expect(page.getByText('점심 · 현재 식당')).toBeVisible()
  await expect(page.getByText('현재 정보를 확인할 수 없는 장소')).toHaveCount(0)

  const mealSummary = page.getByRole('region', { name: '완료한 선택' }).getByText(/점심 · 현재 식당/).locator('..')
  await mealSummary.getByRole('button', { name: '변경' }).click()
  await expect(page.getByRole('heading', { name: 'DAY 1 식당 선택' })).toBeFocused()
  await expect(page.getByRole('heading', { name: '새 식당' })).toBeVisible()
  await expect(page.getByText('정보 근거 적음')).toBeVisible()
  await expect(page.getByText('확인된 정보가 아직 적어 참고용으로 봐주세요.')).toBeVisible()
  await expect(page.getByText(/공개된 대표메뉴 정보가 없어요/)).toBeVisible()

  await page.getByLabel('식당 직접 찾기').fill('새 식당')
  await page.getByRole('button', { name: '찾기', exact: true }).last().click()
  await expect(page.getByRole('heading', { name: '직접 찾은 결과' })).toBeVisible()
  await page.getByRole('button', { name: '이곳 선택' }).click()
  await expect(page.locator('.planner-card h3')).toHaveText(['현재 관광지', '새 식당'])
  await page.reload()
  await expect(page.locator('.planner-card h3')).toHaveText(['현재 관광지', '새 식당'])
  await expect(page.getByRole('button', { name: '수정 완료하고 플랜 보기' })).toBeVisible()
  await page.getByRole('button', { name: '수정 완료하고 플랜 보기' }).click()

  await expect(page).toHaveURL(new RegExp(`/travel/${tripId}/plan$`))
  await expect(page.getByRole('heading', { name: '새 식당' })).toBeVisible()
  await expect(page.getByText('현재 식당')).toHaveCount(0)
  await expect(page.getByText('현재 정보를 확인할 수 없는 장소')).toHaveCount(0)
  await expect(page.getByText('좌표가 없어 지도 마커만 생략했어요.')).toHaveCount(0)
  await page.reload()
  await expect(page.getByRole('heading', { name: '새 식당' })).toBeVisible()
  await page.getByRole('link', { name: 'DAY 1 수정' }).click()
  await page.goBack()
  await expect(page).toHaveURL(new RegExp(`/travel/${tripId}/plan$`))
  await expect(page.getByRole('heading', { name: '새 식당' })).toBeVisible()
})

test('선택형 디저트를 건너뛰면 compact 상태로 접고 다시 추가할 수 있다', async ({ page }) => {
  await mockGoldenApi(page)
  await createDayTrip(page)
  await selectDayFocus(page)
  await page.getByRole('button', { name: '점심 TOP3 보기' }).click()
  await page.getByRole('button', { name: '이 식당 선택' }).click()

  const dessertSection = page.getByRole('region', { name: '식후 디저트' })
  await dessertSection.getByRole('button', { name: '건너뛰기' }).click()
  await expect(dessertSection.getByText('점심 후 디저트 · 건너뜀')).toBeVisible()
  await expect(dessertSection.getByRole('button', { name: '추가하기' })).toBeVisible()
  await expect(dessertSection.getByRole('button', { name: '디저트 추천 보기' })).toHaveCount(0)

  await dessertSection.getByRole('button', { name: '추가하기' }).click()
  await expect(dessertSection.getByRole('heading', { name: '식후에 잠깐 쉬어갈까요?' })).toBeFocused()
  await expect(dessertSection.getByRole('button', { name: '디저트 추천 보기' })).toBeVisible()
})

test('디저트 후보가 없으면 인코딩된 Kakao 외부 검색으로 안전하게 연결한다', async ({ page }) => {
  await mockGoldenApi(page)
  await createDayTrip(page)
  await selectDayFocus(page)
  await page.getByRole('button', { name: '점심 TOP3 보기' }).click()
  await page.getByRole('button', { name: '이 식당 선택' }).click()
  await page.getByRole('button', { name: '디저트 추천 보기' }).click()

  const fallback = page.getByRole('link', { name: '카카오맵에서 주변 디저트 찾아보기' })
  await expect(fallback).toHaveAttribute('href', /https:\/\/map\.kakao\.com\/link\/search\/%ED%98%84%EC%9E%AC%20%EC%8B%9D%EB%8B%B9/)
  await expect(fallback).toHaveAttribute('target', '_blank')
  await expect(fallback).toHaveAttribute('rel', 'noopener noreferrer')
  await expect(page).toHaveURL(new RegExp(`/travel/${tripId}$`))
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
  await page.locator('.day-summary-switcher').getByRole('button', { name: /DAY 2/ }).click()
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
