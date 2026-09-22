import { test, expect } from '@playwright/test'

for (const width of [360, 390, 768]) {
 test(`실제 backend 연결 및 overflow ${width}px`, async ({ page }) => {
  await page.route('**/actuator/health', route => route.fulfill({ status: 200, contentType: 'application/json', body: '{"status":"UP"}' }))
  await page.setViewportSize({ width, height: 844 })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto('/dev/diagnostics')
  await expect(page.getByRole('heading', { name: '여행의 시작을 준비하고 있어요' })).toBeVisible()
  await expect(page.getByRole('status')).toContainText('서비스가 정상적으로 연결되었어요.')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  expect(errors).toEqual([])
 })
}

test('loading, failure, retry and slow network', async ({ page }) => {
 let release
 await page.route('**/actuator/health', async route => {
  await new Promise(resolve => { release = resolve })
  await route.fulfill({ status: 503, body: '{"status":"DOWN"}', contentType: 'application/json' })
 })
 await page.setViewportSize({ width: 360, height: 844 })
 await page.goto('/dev/diagnostics')
 await expect(page.getByRole('status')).toContainText('서비스 연결을 확인하고 있어요.')
 release()
 await expect(page.getByRole('status')).toContainText('서비스에 연결하지 못했어요.')
 await page.unroute('**/actuator/health')
 await page.route('**/actuator/health', route => route.fulfill({ status: 200, body: '{"status":"UP"}', contentType: 'application/json' }))
 await page.getByRole('button', { name: '연결 다시 확인하기' }).click()
 await expect(page.getByRole('status')).toContainText('서비스가 정상적으로 연결되었어요.')
})
