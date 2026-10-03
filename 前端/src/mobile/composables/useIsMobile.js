import { onMounted, onUnmounted, ref } from 'vue'

const QUERY = '(max-width: 767.98px)'

export function useIsMobile() {
  const media = window.matchMedia(QUERY)
  const isMobile = ref(media.matches)
  const update = event => { isMobile.value = event.matches }

  onMounted(() => media.addEventListener('change', update))
  onUnmounted(() => media.removeEventListener('change', update))

  return isMobile
}
