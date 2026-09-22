<script setup>
import { computed } from 'vue'
import { recommendationContextCopy } from '../domain/recommendationContext'

const props = defineProps({ context: { type: Object, default: null } })
const copy = computed(() => recommendationContextCopy(props.context))
</script>

<template>
  <p class="recommendation-context" role="status">{{ copy }}</p>
</template>
