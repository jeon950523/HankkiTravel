import { createApp, nextTick } from 'vue'
import { createPinia } from 'pinia'
import { makeRouter } from './router'
import App from './App.vue'
import './style.css'

const router = makeRouter()
router.afterEach(async (to, from) => {
  document.title = to.meta.title + ' · 한끼여행'
  await nextTick()
  if (from.matched.length && to.path !== from.path) {
    document.querySelector('main h1')?.focus({ preventScroll: true })
  }
})
createApp(App).use(createPinia()).use(router).mount('#app')
