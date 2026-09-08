<script setup>
import { useRoute } from 'vue-router'
import AppHeader from './components/AppHeader.vue'
const route = useRoute()
</script>

<template>
  <RouterView v-if="route.meta.standalone" />
  <div v-else class="app-shell">
    <a href="#main-content" class="skip-link">본문으로 바로가기</a>
    <AppHeader />
    <main id="main-content" class="page-container" tabindex="-1">
      <RouterView />
    </main>
    <footer class="app-footer"><span>한 끼의 선택이, 여행의 시작이 되도록.</span><span>한끼여행</span></footer>
  </div>
</template>
