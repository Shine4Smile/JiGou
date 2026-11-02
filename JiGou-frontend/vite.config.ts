import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

export default defineConfig(({ mode }) => {
  // 第三个参数传 '' 表示加载所有环境变量（不局限于 VITE_ 开头）
  const env = loadEnv(mode, process.cwd(), '')

  // 优雅的兜底逻辑：优先读取环境变量，如果没有则默认单体 8181
  const backendTarget = env.VITE_API_TARGET || 'http://localhost:8181'

  // 在终端里高亮打印当前连的后端，方便排查问题
  console.log(`\n🎯 [Vite] 当前环境: \x1b[36m${mode}\x1b[0m`)
  console.log(`🔗 [Vite] API 代理目标: \x1b[32m${backendTarget}\x1b[0m\n`)

  return {
    plugins: [vue(), vueDevTools()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      proxy: {
        '/api': {
          target: backendTarget,
          changeOrigin: true,
        },
      },
    },
  }
})
