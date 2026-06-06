import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
    plugins: [vue()],
    resolve: {
        alias: {
            'blockly/msg/ru': '/node_modules/blockly/msg/ru.mjs'
        }
    },
    server: {
        proxy: {
            '/api': {
                target: 'http://localhost:8180/routing',
                changeOrigin: true,
            },
            '/realms': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            },
        },
    },
})
