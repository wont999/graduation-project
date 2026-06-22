import { createApp } from 'vue'
import App from './App.vue'
import keycloak from './keycloak'
import './index.css'

keycloak.init({
    onLoad: 'login-required',
    checkLoginIframe: false,
    pkceMethod: 'S256',
    redirectUri: window.location.origin,
})
    .then((authenticated) => {
        if (authenticated) {
            window.__keycloak = keycloak
        }
        createApp(App).mount('#app')
    })
    .catch((error) => {
        console.error('Keycloak init failed:', error)
        createApp(App).mount('#app')
    })
