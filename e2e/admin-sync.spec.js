import { test, expect } from '@playwright/test'

const scopes = [
  ['JEJU_CITY_ATTRACTION', '제주시', '관광지'], ['JEJU_CITY_LODGING', '제주시', '숙박'], ['JEJU_CITY_RESTAURANT', '제주시', '음식점'],
  ['SEOGWIPO_ATTRACTION', '서귀포시', '관광지'], ['SEOGWIPO_LODGING', '서귀포시', '숙박'], ['SEOGWIPO_RESTAURANT', '서귀포시', '음식점'],
  ['GYEONGJU_ATTRACTION', '경주시', '관광지'], ['GYEONGJU_LODGING', '경주시', '숙박'], ['GYEONGJU_RESTAURANT', '경주시', '음식점'],
]
const overview = {
  operatorEnabled: true, operationZone: 'Asia/Seoul', dailyUsedCalls: 12, dailyCallBudget: 100,
  remainingCalls: 88, budgetStatus: 'AVAILABLE', activeScopeKey: null, lastFullSyncStatus: 'PARTIAL_SUCCESS', lastSyncAt: '2026-09-09T01:00:00Z',
  scopes: scopes.map(([scopeKey, regionName, contentTypeName], index) => ({
    scopeKey, regionName, contentTypeName, latestStatus: index === 4 ? 'SUSPICIOUS' : index === 5 ? 'FAILED' : 'SUCCESS',
    lastAttemptAt: '2026-09-09T01:00:00Z', lastSuccessfulSyncAt: '2026-09-09T00:59:00Z',
    latestRemoteCallCount: index + 1, failureCategory: index === 4 ? 'SUSPICIOUS_SNAPSHOT_SHRINK' : index === 5 ? 'TIMEOUT' : null,
  })),
  recentFailures: [{ scopeKey: 'SEOGWIPO_LODGING', startedAt: '2026-09-09T01:00:00Z', failureCategory: 'SUSPICIOUS_SNAPSHOT_SHRINK', remoteCallCount: 5 }],
}

for (const width of [360, 390]) {
  test(`Admin Sync 운영 UX ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 })
    const errors = []
    const requestedScopes = []
    let statusRequests = 0
    page.on('pageerror', error => errors.push(error.message))
    page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
    await page.route('**/api/admin/tourism-sync/**', async route => {
      if (route.request().url().endsWith('/status')) {
        statusRequests++
        return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(overview) })
      }
      if (route.request().method() === 'POST') {
        requestedScopes.push(JSON.parse(route.request().postData()).scopeKey)
        return route.fulfill({ status: 202, contentType: 'application/json', body: JSON.stringify({ accepted: true, code: 'QUEUED', scopeKey: requestedScopes.at(-1) }) })
      }
      return route.abort()
    })
    await page.goto('/admin/sync')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('관광 데이터 동기화 운영')
    expect(statusRequests).toBe(0)
    await page.getByLabel('운영자 아이디').fill('operator-test-user')
    await page.getByLabel('운영자 비밀번호').fill('operator-test-password')
    await page.getByRole('button', { name: '운영 상태 불러오기' }).click()
    await expect(page.getByText('9개 동기화 Scope')).toBeVisible()
    await expect(page.locator('.admin-scope-card')).toHaveCount(9)
    await expect(page.getByText('SUSPICIOUS · 이상 스냅샷 (캐시 유지)')).toBeVisible()
    await expect(page.getByText('PARTIAL_SUCCESS · 일부 Scope 실패')).toBeVisible()
    await expect(page.getByText('최근 실패·이상 스냅샷 요약')).toBeVisible()
    await page.getByRole('button', { name: '9개 Scope 전체 동기화' }).click()
    await expect.poll(() => requestedScopes.length).toBe(1)
    expect(requestedScopes[0]).toBe('ALL_MVP_SCOPES')
    await page.getByRole('button', { name: '이 Scope 동기화' }).first().click()
    await expect.poll(() => requestedScopes.length).toBe(2)
    expect(scopes.map(scope => scope[0])).toContain(requestedScopes[1])
    await expect(page.getByText('동기화 요청 접수')).toBeVisible()
    expect(statusRequests).toBeGreaterThanOrEqual(2)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    expect(await page.evaluate(() => Object.keys(localStorage))).toEqual([])
    expect(errors).toEqual([])
  })
}
