import { onUnmounted, ref, watch } from 'vue'

/** 数字从 0 走到目标值；系统减动画或 data-motion="off" 时直接给结果 */
export function useCountUp(source, { duration = 700 } = {}) {
  const shown = ref(null)
  let raf = 0

  function run(target) {
    cancelAnimationFrame(raf)
    if (target == null) { shown.value = null; return }
    const reduce = typeof window !== 'undefined'
      && (window.matchMedia('(prefers-reduced-motion: reduce)').matches
        || document.documentElement.dataset.motion === 'off')
    if (reduce) { shown.value = target; return }
    const start = performance.now()
    const step = now => {
      const p = Math.min(1, (now - start) / duration)
      const eased = 1 - Math.pow(1 - p, 3)
      shown.value = Math.round(target * eased)
      if (p < 1) raf = requestAnimationFrame(step)
    }
    raf = requestAnimationFrame(step)
  }

  watch(source, value => run(value), { immediate: true })
  onUnmounted(() => cancelAnimationFrame(raf))
  return shown
}
