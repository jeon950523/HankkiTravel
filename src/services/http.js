import { apiUrl } from '../config/api'

export class ApiError extends Error {
  constructor(message, code = 'REQUEST_FAILED', status = 0) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

export async function requestJson(path, options = {}) {
  const response = await fetch(apiUrl(path), {
    cache: 'no-store',
    ...options,
    headers: { Accept: 'application/json', ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...options.headers },
  })
  const body = response.status === 204 ? null : await response.json().catch(() => null)
  if (!response.ok) {
    const message = response.status === 503
      ? '현재 관광정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'
      : body?.message || '요청을 처리하지 못했어요.'
    throw new ApiError(message, body?.code, response.status)
  }
  return body
}
