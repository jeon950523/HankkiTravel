<script setup>
import AppIcon from './AppIcon.vue'
defineProps({ title: String, icon: { type: String, default: 'family' } })
</script>
<template><section class="empty-state"><span class="empty-symbol"><AppIcon :name="icon" /></span><h2>{{ title }}</h2><p class="body-copy"><slot /></p><div v-if="$slots.action" class="empty-action"><slot name="action" /></div></section></template>
