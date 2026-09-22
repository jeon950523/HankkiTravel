<script setup>
import { ref, watch } from 'vue'
const props = defineProps({ src: String, alt: { type: String, default: '' } })
const failed = ref(false)
watch(() => props.src, () => { failed.value = false })
</script>
<template>
  <div class="kto-image">
    <img v-if="src && !failed" :src="src" :alt="alt" loading="lazy" referrerpolicy="no-referrer" @error="failed = true" />
    <span v-else aria-hidden="true">한끼여행</span>
  </div>
</template>
