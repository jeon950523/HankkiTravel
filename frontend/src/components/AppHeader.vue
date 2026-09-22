<script setup>
import { useRoute } from 'vue-router'
import AppIcon from './AppIcon.vue'
const route = useRoute()
const navigation = [
  { to: '/', label: '홈', icon: 'home', active: path => path === '/' },
  { to: '/trips', label: '내 여행', icon: 'journey', active: path => path === '/trips' },
  { to: '/travel/new', label: '여행 시작', icon: 'journey', active: path => path.startsWith('/travel') },
  { to: '/profiles', label: '가족 프로필', icon: 'family', active: path => path.startsWith('/profiles') },
]
</script>
<template>
  <header class="app-header">
    <div class="header-inner">
      <RouterLink to="/" class="brand" aria-label="한끼여행 홈"><img src="/icon.svg" alt="" width="36" height="36" /><span>한끼여행</span></RouterLink>
      <nav class="app-nav" aria-label="주요 메뉴">
        <RouterLink v-for="item in navigation" :key="item.to" :to="item.to" :class="{ 'nav-active': item.active(route.path) }" :aria-current="item.active(route.path) ? 'page' : undefined">
          <AppIcon :name="item.icon" /><span>{{ item.label }}</span>
        </RouterLink>
      </nav>
      <RouterLink to="/login" class="login-link">로그인</RouterLink>
    </div>
  </header>
</template>
