import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  // 将 base 设置为相对路径 './' 
  // 这样打包后的 index.html 引用资源时会使用 ./assets/，兼容性最强
  base: './', 
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://129.211.187.179:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
      '/user-api': {
        target: 'http://129.211.187.179:8085',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/user-api/, ''),
      },
      '/trade-query': {
        target: 'http://129.211.187.179:8082',
        changeOrigin: true,
      },
      '/quotation-api': {
        target: 'http://129.211.187.179:8086',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/quotation-api/, ''),
      },
      '/ws': {
        target: 'http://129.211.187.179:8087',
        changeOrigin: true,
        ws: true,
      },
      '/export-data-api': {
        target: 'http://129.211.187.179:8065',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/export-data-api/, '/export-data'),
      },
    },
  },
})
