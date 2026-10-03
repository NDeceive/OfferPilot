<template><LogoIcon :size="size" /></template>
<script setup>
import LogoIcon from './LogoIcon.vue'
defineProps({ size: { type: Number, default: 120 } })
</script>
