import { computed, ref } from 'vue'

/**
 * 外观主题：浅色 / 深色 / 跟随系统。
 *
 * 约定：
 * - 只改 <html data-theme="dark">，样式表里用 :root[data-theme='dark'] 覆盖变量；
 * - 选择存在 localStorage（键 app-theme），冷启动时先应用再挂载，避免白闪；
 * - 「跟随系统」监听 prefers-color-scheme，系统切换时实时跟随。
 */

export const THEME_STORAGE_KEY = 'app-theme'
export const THEME_MODES = ['light', 'dark', 'system']
const SYSTEM_QUERY = '(prefers-color-scheme: dark)'

/** 纯函数，便于单测：给定选择与系统偏好，得出实际主题 */
export function resolveTheme(mode, prefersDark) {
  if (mode === 'dark') return 'dark'
  if (mode === 'light') return 'light'
  return prefersDark ? 'dark' : 'light'
}

export function normalizeMode(value) {
  return THEME_MODES.includes(value) ? value : 'system'
}

function readStoredMode() {
  try {
    return normalizeMode(localStorage.getItem(THEME_STORAGE_KEY))
  } catch {
    return 'system'
  }
}

const mode = ref(readStoredMode())
const prefersDark = ref(false)
let mediaQuery = null
let started = false

const resolved = computed(() => resolveTheme(mode.value, prefersDark.value))

function paint() {
  if (typeof document === 'undefined') return
  const theme = resolved.value
  const root = document.documentElement
  root.dataset.theme = theme
  root.style.colorScheme = theme
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) meta.setAttribute('content', theme === 'dark' ? '#0e1013' : '#10b981')
}

function onSystemChange(event) {
  prefersDark.value = event.matches
  if (mode.value === 'system') paint()
}

function start() {
  if (started || typeof window === 'undefined') return
  started = true
  const mq = window.matchMedia ? window.matchMedia(SYSTEM_QUERY) : null
  mediaQuery = mq
  prefersDark.value = mq ? mq.matches : false
  if (mq) {
    if (mq.addEventListener) mq.addEventListener('change', onSystemChange)
    else if (mq.addListener) mq.addListener(onSystemChange)
  }
  paint()
}

export function setThemeMode(next) {
  const value = normalizeMode(next)
  mode.value = value
  try {
    localStorage.setItem(THEME_STORAGE_KEY, value)
  } catch {
    /* 存储被禁用时只在本次会话生效 */
  }
  paint()
  return value
}

/** 挂载前调用一次：读取偏好、注册系统监听、落到 <html> */
export function initTheme() {
  start()
  paint()
  return resolved.value
}

export function useTheme() {
  start()
  return { mode, resolved, prefersDark, setThemeMode }
}
