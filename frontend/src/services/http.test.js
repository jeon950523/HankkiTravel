import { beforeEach, expect, it, vi } from 'vitest'
import { requestJson } from './http'

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn())
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

it('브라우저 원문 네트워크 오류를 사용자 화면에 노출하지 않는다', async () => {
  fetch.mockRejectedValue(new TypeError('NetworkError when attempting to fetch resource.'))
  await expect(requestJson('/api/example')).rejects.toThrow('현재 요청을 마치지 못했어요')
  await expect(requestJson('/api/example')).rejects.not.toThrow('NetworkError')
})
