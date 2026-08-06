import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const digitalHumanTarget = env.DIGITAL_HUMAN_PROXY_TARGET || 'http://127.0.0.1:8010'
  const digitalHumanApiProxy = {
    target: digitalHumanTarget,
    changeOrigin: true,
  }

  return {
    plugins: [vue()],
    server: {
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
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
