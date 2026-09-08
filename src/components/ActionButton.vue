<script setup>
defineProps({
  to: { type: [String, Object], default: undefined },
  variant: { type: String, default: 'primary' },
  disabled: Boolean,
})
</script>
<template>
  <RouterLink v-if="to && !disabled" :to="to" :class="['action-button', 'button-' + variant]"><slot /></RouterLink>
  <button v-else type="button" :disabled="disabled" :class="['action-button', 'button-' + variant]"><slot /></button>
</template>
