<template>
  <div class="app">
    <OfflineDemoOverlay />
    <CameraConsentDialog />
    <router-view v-slot="{ Component }">
      <transition name="page" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </div>
</template>

<script setup>
import OfflineDemoOverlay from './components/ui/OfflineDemoOverlay.vue'
import CameraConsentDialog from './components/interview/CameraConsentDialog.vue'
</script>

<style>
.app {
  min-height: 100vh;
}
</style>
