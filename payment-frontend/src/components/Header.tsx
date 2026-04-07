import { useEffect, useState } from 'react'
import keycloak, { getCurrentUser } from '../auth/keycloak'
import { fetchMyAccount, AccountInfo } from '../api/accountApi'
import './Header.css'

export default function Header() {
  const user = getCurrentUser()
  const [account, setAccount] = useState<AccountInfo | null>(null)

  useEffect(() => {
    fetchMyAccount().then(setAccount).catch(() => {})
  }, [])

  return (
    <header className="header">
      <svg width="28" height="28" viewBox="0 0 24 24" fill="white" aria-hidden="true">
        <path d="M20 4H4c-1.11 0-2 .89-2 2v12c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V6c0-1.11-.89-2-2-2zm0 14H4v-6h16v6zm0-10H4V6h16v2z" />
      </svg>
      <span className="header-title">PaymentAgent</span>
      <span className="header-sub">Assistente de Pagamentos</span>

      <div className="header-user">
        {user && (
          <span className="header-username" title={user.email}>
            {user.name || user.email}
            {account && (
              <span className="header-account">
                &nbsp;· Ag. {account.agency} / CC {account.accountNumber}
              </span>
            )}
          </span>
        )}
        <button className="header-logout-btn" onClick={() => keycloak.logout()}>
          Sair
        </button>
      </div>
    </header>
  )
}
