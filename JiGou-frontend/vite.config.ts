import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  // 开发环境把 /api 代理到后端，让接口与预览页（/api/static/...）与前端保持同源：
  // 1. 预览 iframe 与主站同源，才能向其中注入可视化编辑脚本（跨域时无法访问 contentDocument）；
  // 2. 请求天然携带登录 Cookie，无需依赖后端跨域配置
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8181',
        changeOrigin: true,
      },
    },
  },
})
