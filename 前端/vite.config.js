import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  // HBuilderX 5+App 壳（`npm run build:app` → .env.app）：页面以 file:// 加载，
  // 绝对路径资源全部解析失败 → base 切 './'，产物另出 dist-app/ 不覆盖待部署的 dist/。
  const isAppShell = env.VITE_APP_SHELL === 'true'
  const digitalHumanTarget = env.DIGITAL_HUMAN_PROXY_TARGET || 'http://127.0.0.1:8010'
  const digitalHumanApiProxy = {
    target: digitalHumanTarget,
    changeOrigin: true,
  }

  return {
    plugins: [vue()],
    // 桌面/nginx：'/'（与原行为完全一致）；App 壳：'./'
    base: isAppShell ? './' : '/',
    build: isAppShell ? { outDir: 'dist-app' } : {},
    server: {
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
        // 会议信令 WebSocket：不配 ws:true 时 Vite 会按普通 HTTP 代理回 200
        // （而不是 101），排查时会误判成后端问题。
        '/ws': {
          target: 'http://localhost:8080',
          ws: true,
          changeOrigin: true,
        },
        '/digital-human': {
          ...digitalHumanApiProxy,
          rewrite: path => path.replace(/^\/digital-human/, ''),
        },
        '/offer': digitalHumanApiProxy,
        '/human': digitalHumanApiProxy,
        '/humanaudio': digitalHumanApiProxy,
        '/interrupt_talk': digitalHumanApiProxy,
        '/close_session': digitalHumanApiProxy,
        '/is_speaking': digitalHumanApiProxy,
        '/set_audiotype': digitalHumanApiProxy,
        '/record': digitalHumanApiProxy,
      },
    },
  }
})
