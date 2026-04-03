import './Header.css'

export default function Header() {
  return (
    <header className="header">
      <svg width="28" height="28" viewBox="0 0 24 24" fill="white" aria-hidden="true">
        <path d="M20 4H4c-1.11 0-2 .89-2 2v12c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V6c0-1.11-.89-2-2-2zm0 14H4v-6h16v6zm0-10H4V6h16v2z" />
      </svg>
      <span className="header-title">PaymentAgent</span>
      <span className="header-sub">Assistente de Pagamentos</span>
    </header>
  )
}
