<template>
  <img class="logo-icon" :src="source" :width="size" :height="size" alt="" aria-hidden="true" />
</template>
<script setup>
import { computed } from 'vue'
import standard from '../../assets/brand/symbol-green.svg'
import micro from '../../assets/brand/micro-green.svg'
import white from '../../assets/brand/symbol-white.svg'
import microWhite from '../../assets/brand/micro-white.svg'
const props = defineProps({ size: { type: Number, default: 32 }, inverse: { type: Boolean, default: false } })
const source = computed(() => props.inverse ? (props.size < 32 ? microWhite : white) : (props.size < 32 ? micro : standard))
</script>
<style scoped>
.logo-icon { display: inline-block; flex-shrink: 0; object-fit: contain; }
</style>
