<template>
  <div class="mobile-state" :class="`is-${kind}`" :role="kind === 'error' ? 'alert' : 'status'">
    <span class="mobile-state-icon" aria-hidden="true">{{ icon }}</span>
    <strong>{{ title }}</strong>
    <p v-if="description">{{ description }}</p>
    <button v-if="action" type="button" class="mobile-secondary-button" @click="$emit('action')">{{ action }}</button>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  kind: { type: String, default: 'empty' },
  title: { type: String, required: true },
  description: { type: String, default: '' },
  action: { type: String, default: '' },
})
defineEmits(['action'])

const icon = computed(() => ({ loading: '···', error: '!', empty: '○' })[props.kind] || '○')
</script>
