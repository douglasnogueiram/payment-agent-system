import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App'
import keycloak from './auth/keycloak'

keycloak
  .init({
    onLoad: 'login-required',
    checkLoginIframe: false,
  })
  .then((authenticated) => {
    if (!authenticated) {
      keycloak.login()
      return
    }

    // Auto-refresh token when it expires
    setInterval(() => {
      keycloak.updateToken(60).catch(() => keycloak.logout())
    }, 30_000)

    createRoot(document.getElementById('root')!).render(
      <StrictMode>
        <App />
      </StrictMode>,
    )
  })
  .catch(() => {
    document.body.innerHTML =
      '<div style="padding:2rem;color:red">Falha ao conectar ao servidor de autenticação.</div>'
  })
