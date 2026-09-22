let loaderPromise

const ready = () => globalThis.kakao?.maps?.Map && globalThis.kakao?.maps?.LatLng

export function loadKakaoMapSdk(key = import.meta.env.VITE_KAKAO_JAVASCRIPT_KEY) {
  if (ready()) return Promise.resolve(globalThis.kakao.maps)
  if (!key) {
    console.error('[Kakao Map] JavaScript SDK key is not configured.')
    return Promise.reject(new Error('KAKAO_MAP_KEY_MISSING'))
  }
  if (loaderPromise) return loaderPromise
  loaderPromise = new Promise((resolve, reject) => {
    const existing = document.querySelector('script[data-hankki-kakao-map]')
    const script = existing || document.createElement('script')
    const fail = () => {
      loaderPromise = undefined
      script.remove()
      console.error('[Kakao Map] JavaScript SDK failed to load.')
      reject(new Error('KAKAO_MAP_LOAD_FAILED'))
    }
    const finish = () => {
      if (!globalThis.kakao?.maps?.load) return fail()
      globalThis.kakao.maps.load(() => ready() ? resolve(globalThis.kakao.maps) : fail())
    }
    script.addEventListener('error', fail, { once: true })
    script.addEventListener('load', finish, { once: true })
    if (!existing) {
      script.dataset.hankkiKakaoMap = 'true'
      script.async = true
      script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(key)}&autoload=false`
      document.head.appendChild(script)
    }
  })
  return loaderPromise
}

export function resetKakaoMapLoaderForTest() {
  loaderPromise = undefined
}
