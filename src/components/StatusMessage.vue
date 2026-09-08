<script setup>
defineProps({ kind: { type: String, default: 'info' }, title: String })
</script>
<template><div :class="['status-message', 'status-' + kind]" :role="kind === 'error' ? 'alert' : 'status'" :aria-busy="kind === 'loading' ? true : undefined"><strong v-if="title">{{ title }}</strong><p><slot /></p></div></template>
