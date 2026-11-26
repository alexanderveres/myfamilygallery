import { defineConfig } from 'vite';

export default defineConfig({
  root: 'src/main/ui',
  resolve: {
    alias: {
      vue: 'vue/dist/vue.esm-bundler.js'
    }
  },
  build: {
    outDir: '../resources/static/dist',
    emptyOutDir: true
  }
});
