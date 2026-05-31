import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id || id.indexOf("node_modules") === -1) {
            return undefined;
          }
          if (/node_modules[\\/](react|react-dom|react-router-dom)[\\/]/.test(id)) {
            return "vendor-react";
          }
          if (/node_modules[\\/]lightweight-charts[\\/]/.test(id)) {
            return "vendor-lightweight-charts";
          }
          if (/node_modules[\\/]recharts[\\/]/.test(id)) {
            return "vendor-recharts";
          }
          if (/node_modules[\\/]@tanstack[\\/]react-query[\\/]/.test(id)) {
            return "vendor-query";
          }
          if (/node_modules[\\/](i18next|react-i18next)[\\/]/.test(id)) {
            return "vendor-i18n";
          }
          return undefined;
        },
      },
    },
  },
})
