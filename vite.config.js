import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { readFileSync } from 'fs';
import { resolve } from 'path';

/** Classic sync head script — Vite skips non-module <script src>; emit it into dist. */
function emitOsJs() {
  return {
    name: 'emit-os-js',
    generateBundle() {
      this.emitFile({
        type: 'asset',
        fileName: 'os.js',
        source: readFileSync(resolve(__dirname, 'src/renderer/os.js'), 'utf8'),
      });
    },
  };
}

export default defineConfig({
  root: 'src/renderer',
  base: './',
  plugins: [react(), emitOsJs()],
  server: {
    port: 3344,
    strictPort: true,
  },
  build: {
    outDir: resolve(__dirname, 'dist-renderer'),
    emptyOutDir: true,
    commonjsOptions: {
      include: [/node_modules/, /src[\\/]shared/],
      defaultIsModuleExports: true,
    },
    rollupOptions: {
      input: resolve(__dirname, 'src/renderer/index.html'),
    },
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src/renderer'),
    },
  },
});
