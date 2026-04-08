import { useEffect, useState } from 'react'
import keycloak, { getCurrentUser } from '../auth/keycloak'
import { fetchMyAccount, AccountInfo } from '../api/accountApi'
import './Header.css'

function getInitials(name?: string, email?: string): string {
  const source = name || email || '?'
  const parts = source.trim().split(/\s+/)
  if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase()
  return source.slice(0, 2).toUpperCase()
}

export default function Header() {
  const user = getCurrentUser()
  const [account, setAccount] = useState<AccountInfo | null>(null)

  useEffect(() => {
    fetchMyAccount().then(setAccount).catch(() => {})
  }, [])

  return (
    <header className="header">
      {/* Pix logo — stylized X with 4 rounded rhombus shapes */}
      <svg width="32" height="32" viewBox="0 0 32 32" fill="none" aria-hidden="true">
        <path d="M19.8 12.2L16 16l-3.8-3.8a5.4 5.4 0 0 0-7.6 0L2 14.8l3.6 3.6a5.4 5.4 0 0 0 7.6 0L16 16l2.8 2.8a5.4 5.4 0 0 0 7.6 0L30 15.2l-2.6-3a5.4 5.4 0 0 0-7.6 0z" fill="url(#pixGrad)"/>
        <defs>
          <linearGradient id="pixGrad" x1="0" y1="0" x2="32" y2="32" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="#00BDAE"/>
            <stop offset="100%" stopColor="#0094FF"/>
          </linearGradient>
        </defs>
      </svg>

      <div className="header-brand">
        <span className="header-title">Meu Agente Pix</span>
        <span className="header-sub">powered by IA</span>
      </div>

      <div className="header-user">
        {user && (
          <>
            <div className="header-avatar" title={user.email}>
              {getInitials(user.name, user.email)}
            </div>
            <div className="header-user-info">
              <span className="header-username">{user.name || user.email}</span>
              {account && (
                <span className="header-account">
                  Ag. {account.agency} · CC {account.accountNumber}
                </span>
              )}
            </div>
          </>
        )}
        <button className="header-logout-btn" onClick={() => keycloak.logout()}>
          Sair
        </button>
      </div>
    </header>
  )
}
