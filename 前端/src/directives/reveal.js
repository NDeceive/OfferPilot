// 滚动出现：一个统一的滚动监听，按元素在视口中的位置决定显示/收起。
// 容器没传 stagger 时整块淡入；传了 stagger 时逐行算，行到位才亮，离开就收回。
// 用 rAF 节流，整页只挂一个 scroll 监听。
const registry = new Set()
let raf = 0

function tick() {
  raf = 0
  const vh = window.innerHeight
  const rowLine = vh * 0.89
  for (const item of registry) {
    if (!item.el.isConnected) { registry.delete(item); continue }
    if (item.selector) {
      item.el.querySelectorAll(item.selector).forEach(row => {
        const rect = row.getBoundingClientRect()
        const inView = rect.top < rowLine && rect.bottom > 0
        if (inView) {
          row.classList.remove('row-pending')
          row.classList.add('is-row')
        } else {
          row.classList.remove('is-row')
          row.classList.add('row-pending')
        }
      })
    } else {
      const rect = item.el.getBoundingClientRect()
      const inView = rect.top < vh * 0.96 && rect.bottom > 0
      item.el.classList.toggle('is-revealed', inView)
    }
  }
}

function schedule() {
  if (!raf) raf = requestAnimationFrame(tick)
}

if (typeof window !== 'undefined') {
  window.addEventListener('scroll', schedule, { passive: true })
  window.addEventListener('resize', schedule)
  // 内容晚到时也要补一次，半秒一次足够
  setInterval(schedule, 500)
}

export const reveal = {
  mounted(el, binding) {
    const options = binding?.value && typeof binding.value === 'object' ? binding.value : {}
    el.dataset.reveal = ''
    if (options.stagger) el.dataset.stagger = ''
    registry.add({ el, selector: options.stagger || '' })
    schedule()
  },
  unmounted(el) {
    for (const item of registry) if (item.el === el) registry.delete(item)
  },
}
