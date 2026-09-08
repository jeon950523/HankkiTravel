<script setup>
defineProps({ name: { type: String, required: true } })
const paths = {
  home: 'M3 10 12 3l9 7M5 9v12h5v-7h4v7h5V9',
  journey: 'M5 19c10 0 2-14 14-14M5 16a3 3 0 1 0 0 6 3 3 0 0 0 0-6M19 2a3 3 0 1 0 0 6 3 3 0 0 0 0-6',
  family: 'M8 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8M2 21v-2a6 6 0 0 1 12 0v2M16 5a3 3 0 0 1 0 6M17 15a5 5 0 0 1 5 5v1',
  meal: 'M3 11h18c0 6-4 9-9 9s-9-3-9-9ZM7 4v3m5-4v4m5-3v3M7 22h10',
  place: 'M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 0 1 16 0ZM15 10a3 3 0 1 0-6 0 3 3 0 0 0 6 0',
  arrow: 'M4 12h16m-6-6 6 6-6 6',
  back: 'M20 12H4m6-6-6 6 6 6',
  plus: 'M12 5v14M5 12h14',
  check: 'm5 12 4 4L19 6',
  note: 'M6 3h12v18H6ZM9 8h6M9 12h6M9 16h3',
}
</script>
<template><svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path :d="paths[name] || paths.note" /></svg></template>
