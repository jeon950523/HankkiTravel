const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()
const defaultBaseUrl = import.meta.env.PROD ? 'https://api.hankki.kro.kr' : ''

export const API_BASE_URL = (configuredBaseUrl || defaultBaseUrl).replace(/\/+$/, '')

export function apiUrl(path) {
  if (!path.startsWith('/')) throw new Error('API path must start with /.')
  return `${API_BASE_URL}${path}`
}
