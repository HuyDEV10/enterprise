import { defineConfig } from 'vitest/config'
import { loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [react()],
    server: {
      proxy: {
        '/api': {
          target:
            process.env.API_PROXY_TARGET ||
            env.API_PROXY_TARGET ||
            'http://localhost:8080',
          changeOrigin: true,
          configure(proxy) {
            proxy.on('proxyReq', (proxyRequest, request) => {
              // A browser request to this Vite server is same-origin. Do not
              // make the backend treat the internal proxy hop as cross-origin.
              const origin = request.headers.origin
              if (
                origin &&
                URL.canParse(origin) &&
                new URL(origin).host === request.headers.host
              ) {
                proxyRequest.removeHeader('origin')
              }
            })
          },
        },
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: './src/test/setup.js',
      include: ['src/**/*.test.{js,jsx}'],
      clearMocks: true,
    },
  }
})
