import { describe, it, expect, vi } from 'vitest'
import { createSSRApp } from 'vue'
import { createPinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'
import { renderToString } from '@vue/server-renderer'
import { makeRouter } from './index'
import App from '../App.vue'

async function render(path) {
 const router = makeRouter(createMemoryHistory())
 await router.push(path)
 await router.isReady()
 const html = await renderToString(createSSRApp(App).use(createPinia()).use(router))
 return { html, router }
}
describe('제품 경로와 실제 상태를 구분하는 Shell', () => {
 it.each([
  ['/', '좋은 한 끼가,'], ['/login', '카카오로 계속하기'],
  ['/profiles', '가족의 여행 이야기를 기다려요'], ['/profiles/new', '프로필 저장'],
  ['/travel/new', '이번 한 끼를'], ['/auth/callback', '로그인 연결을'],
  ['/admin/sync', '관광 데이터 동기화 운영'], ['/unknown', '이 페이지를'],
 ])('%s 렌더와 제목 계약', async (path, text) => {
  const { html, router } = await render(path)
  expect(html).toContain(text)
  if (path === '/admin/sync') expect(html).not.toContain('주요 메뉴')
  else expect(html).toContain('주요 메뉴')
  expect(router.currentRoute.value.meta.standalone === true).toBe(path === '/admin/sync')
  expect(html).not.toContain('P0.1')
  expect(html).not.toContain('actuator')
  expect(router.currentRoute.value.meta.title).toBeTruthy()
 })
 it('Home의 두 CTA는 Guest 시작을 거쳐 서로 다른 여행 시작 의도를 전달한다', async () => {
  const {html} = await render('/')
  expect(html).toContain('한 끼부터 찾기')
  expect(html).toContain('장소부터 찾기')
  expect(html).toContain('로그인 없이 가족 프로필')
 })
 it('프로필 수정 경로는 편집 화면을 재사용한다', async () => {
  const {html, router} = await render('/profiles/42/edit')
  expect(router.currentRoute.value.name).toBe('profile-edit')
  expect(html).toContain('우리 가족의 조건을')
 })
 it('OAuth 버튼은 disabled이며 외부 인증 링크가 없다', async () => {
  const {html} = await render('/login')
  expect((html.match(/<button\b[^>]*\bdisabled\b/g) || []).length).toBe(2)
  expect(html).not.toMatch(/href="https?:/)
  expect(html).not.toContain('Google')
 })
 it('콜백 query 내용을 사용자 화면에 반사하지 않는다', async () => {
  const {html} = await render('/auth/callback?code=synthetic-callback-marker&error=synthetic-error')
  expect(html).not.toContain('synthetic-callback-marker')
  expect(html).not.toContain('synthetic-error')
 })
 it('미지원 시작 query는 기본 식사 흐름만 표시한다', async () => {
  const {html} = await render('/travel/new?start=untrusted-marker')
  expect(html).toContain('먹고 싶은 한 끼에서 시작해요')
  expect(html).not.toContain('untrusted-marker')
 })
 it('진단 화면은 제품 내비게이션과 분리한다', async () => {
  const {html} = await render('/dev/diagnostics')
  expect(html).toContain('P0.1')
  expect(html).not.toContain('주요 메뉴')
 })
})

it('production 환경에서는 진단 경로를 등록하지 않고 일반 404를 렌더한다', async () => {
 vi.stubEnv('DEV', false)
 vi.resetModules()
 try {
  const { makeRouter: makeProductionRouter } = await import('./index')
  const router = makeProductionRouter(createMemoryHistory())
  expect(router.hasRoute('diagnostics')).toBe(false)
  await router.push('/dev/diagnostics')
  await router.isReady()
  expect(router.currentRoute.value.name).toBe('not-found')
  const html=await renderToString(createSSRApp(App).use(createPinia()).use(router))
  expect(html).toContain('이 페이지를')
  expect(html).not.toContain('P0.1')
 } finally {
  vi.unstubAllEnvs()
  vi.resetModules()
 }
})
