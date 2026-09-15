import { test, expect, chromium } from '@playwright/test'

const guestId = '9d4f2c3a-6d29-4c12-8a70-2c6a2f87b111'
const profile = {
 profileId: 42, name: '부모님과 제주', transportMode: 'CAR', parkingPreference: 'REQUIRED',
 walkingBurdenPreference: 'NORMAL', transferPreference: 'AVOID', stairsAvoidance: true,
 members: [{ memberId: 7, nickname: '엄마', continuousWalkingMinutes: 20, stairsPreference: 'AVOID', mealCautions: ['SODIUM'] }],
}
const candidate = {
 contentId: '123', title: '현재 비빔밥', address: '제주시 테스트로', imageUrl: null, compatibilityScore: 82,
 evaluatedWeight: 55, informationEvidence: 'REFERENCE', ourFamilyFitReasons: ['표준 음식 참고값이 비교 가능한 메뉴 근거가 있어요.'],
 ourFamilyCautions: ['원재료 정보는 공개 데이터만으로 확인하기 어려워요.'], checkBeforeVisit: ['원재료와 조리 방식은 방문 전에 직접 확인해 주세요.'],
 nutritionEvidence: [{ menuName: '비빔밥', matchLevel: 'HIGH', standardFood: '비빔밥', referenceLabel: '표준 음식 기준' }],
 transportEvidence: null, evaluatedDimensions: [],
}
const recommendation = {
 context: { region: 'jeju-city', tripDate: '2026-09-15', mealType: 'LUNCH', startMode: 'MEAL_FIRST' },
 perspectives: [
  { perspective: 'BALANCED', status: 'READY', message: null, candidates: [candidate] },
  { perspective: 'MEAL_PRIORITY', status: 'READY', message: null, candidates: [candidate] },
  { perspective: 'MOBILITY_PRIORITY', status: 'MOVEMENT_CONTEXT_REQUIRED', message: '이동편의 비교를 위해 기준 장소를 추가해 주세요.', candidates: [] },
 ], candidateCount: 1, callSummary: { tourListCalls: 1, tourDetailCalls: 1, kakaoTransitCalls: 0, elapsedMillis: 12 },
 dataAvailability: 'CURRENT_DATA', sourceAttribution: '출처: ⓒ한국관광공사', nutritionNotice: '표준 음식 기준 또는 유사 음식 기준 참고정보입니다.',
}
async function mockBig2Api(page) {
 await page.route('**/api/**', async route => {
  const request = route.request()
  const pathname = new URL(request.url()).pathname
  const json = body => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
  if (pathname === '/api/guests' && request.method() === 'POST') return json({ publicId: guestId, createdAt: '2026-09-15T00:00:00Z' })
  if (pathname.endsWith(`/api/guests/${guestId}/profiles`) && request.method() === 'GET') return json([profile])
  if (pathname.endsWith(`/api/guests/${guestId}/profiles`) && request.method() === 'POST') return route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(profile) })
  if (pathname === '/api/recommendations/restaurants' && request.method() === 'POST') return json(recommendation)
  return route.fulfill({ status: 404, contentType: 'application/json', body: '{"message":"not found"}' })
 })
}

for (const width of [360, 390, 768]) {
 test('BIG-2 입력과 추천 카드가 좁은 화면에서도 보인다 '+width+'px', async ({ page }) => {
  await mockBig2Api(page)
  await page.setViewportSize({ width, height: 844 })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto('/travel/new')
  await page.getByRole('radio', { name: /제주/ }).check()
  await page.getByRole('button', { name: '추천 확인' }).click()
  await expect(page.getByRole('heading', { name: '우리 가족의 추천' })).toBeVisible()
  await expect(page.getByText('동행 적합도').first()).toBeVisible()
  await expect(page.getByText('정보 근거').first()).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  expect(errors).toEqual([])
 })
}

test('Guest 시작, 실제 프로필 입력, Meal Slot 추천 흐름', async ({ page }) => {
 await mockBig2Api(page)
 await page.setViewportSize({ width: 390, height: 844 })
 await page.goto('/')
 await page.getByRole('button', { name: '한 끼부터 찾기' }).click()
 await expect(page).toHaveURL(/travel\/new\?start=meal$/)
 await page.getByRole('link', { name: '가족 프로필' }).click()
 await page.getByRole('link', { name: '새 프로필 만들기' }).click()
 await page.getByLabel('프로필 이름').fill('부모님과 제주')
 await page.getByLabel('부르는 이름').fill('엄마')
 await page.getByLabel('나트륨 참고').check()
 await page.getByRole('button', { name: '프로필 저장' }).click()
 await expect(page).toHaveURL(/profiles\/42\/edit$/)
 await page.getByRole('link', { name: '여행 시작' }).click()
 await page.getByRole('radio', { name: /제주/ }).check()
 await page.getByRole('button', { name: '추천 확인' }).click()
 await expect(page.getByText('현재 비빔밥').first()).toBeVisible()
 await expect(page.getByText('우리 가족과 잘 맞는 점').first()).toBeVisible()
 await expect(page.getByText('방문 전에 같이 확인하면 좋은 점').first()).toBeVisible()
 await expect(page.getByText('출처: ⓒ한국관광공사')).toBeVisible()
})

test('장소 기준점이 없으면 이동편의 점수를 만들지 않는다', async ({ page }) => {
 await mockBig2Api(page)
 await page.goto('/travel/new?start=place')
 await page.getByRole('radio', { name: /제주/ }).check()
 await page.getByRole('button', { name: '추천 확인' }).click()
 await expect(page.getByText('장소부터 시작할 때는 기준 장소 좌표를 입력해 주세요.')).toBeVisible()
})

test('PWA는 API 응답을 장기 캐시하지 않는다', async ({}, testInfo) => {
 const context = await chromium.launchPersistentContext(testInfo.outputPath('pwa-profile'), { channel: 'msedge', headless: true, viewport: { width: 390, height: 844 } })
 const page = await context.newPage()
 try {
  await mockBig2Api(page)
  await page.goto('/')
  await page.evaluate(async () => { await navigator.serviceWorker.ready })
  await page.reload()
  await page.waitForFunction(() => Boolean(navigator.serviceWorker.controller))
  const cachedUrls = await page.evaluate(async () => { const urls = []; for (const key of await caches.keys()) for (const request of await (await caches.open(key)).keys()) urls.push(new URL(request.url).pathname); return urls })
  expect(cachedUrls.filter(url => /^\/(api|actuator)/.test(url))).toEqual([])
 } finally { await context.close() }
})
