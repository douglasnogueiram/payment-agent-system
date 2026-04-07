/**
 * Lightweight OAuth2 Authorization Code client — no keycloak-js dependency.
 * Uses Math.random() for state (no Web Crypto required), so it works over HTTP+IP.
 */

// In production, VITE_KC_URL is set to https://meuagentepix.com.br/kc (see .env.production)
// In dev it falls back to the LAN Keycloak on port 8180
const KC_URL = ((import.meta.env.VITE_KC_URL as string | undefined) ?? '').replace(/\/$/, '') ||
  `http://${window.location.hostname}:8180`

// Redirect URI must match the protocol/port the user is actually on
function currentOrigin(): string {
  const port = window.location.port
  return `${window.location.protocol}//${window.location.hostname}${port ? ':' + port : ''}`
}
const REALM  = 'payment'
const CLIENT = 'payment-frontend'

// ── Internal state ────────────────────────────────────────────────────────────

type TokenData = {
  access_token:      string
  refresh_token:     string
  expires_in:        number
  refresh_expires_in: number
  timestamp:         number   // Date.now() when token was stored
}

let _tokens: TokenData | null = null
let _parsed: Record<string, unknown> | null = null

// ── Helpers ───────────────────────────────────────────────────────────────────

function randHex(len = 32) {
  let s = ''
  for (let i = 0; i < len; i++) s += Math.floor(Math.random() * 16).toString(16)
  return s
}

function parseJwt(token: string): Record<string, unknown> | null {
  try {
    const b64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    return JSON.parse(atob(b64))
  } catch { return null }
}

function isExpired(t: TokenData, margin = 30): boolean {
  return (Date.now() - t.timestamp) / 1000 >= t.expires_in - margin
}

function store(raw: Omit<TokenData, 'timestamp'>) {
  _tokens = { ...raw, timestamp: Date.now() }
  _parsed = parseJwt(raw.access_token)
  localStorage.setItem('kc_token', JSON.stringify(_tokens))
}

function clear() {
  _tokens = null
  _parsed = null
  localStorage.removeItem('kc_token')
  sessionStorage.removeItem('kc_state')
  sessionStorage.removeItem('kc_redir')
}

async function callToken(params: Record<string, string>): Promise<TokenData | null> {
  // In dev (HTTP+IP): KC_URL = http://<lan-ip>:8180 → direct Keycloak call
  // In prod (HTTPS):  KC_URL = https://meuagentepix.com.br/kc → same-origin, no mixed content
  const url = `${KC_URL}/realms/${REALM}/protocol/openid-connect/token`
  try {
    const res = await fetch(url, {
      method:  'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body:    new URLSearchParams({ client_id: CLIENT, ...params }).toString(),
    })
    return res.ok ? res.json() : null
  } catch { return null }
}

// ── Keycloak-compatible object ─────────────────────────────────────────────────

const keycloak = {
  get token()       { return _tokens?.access_token ?? '' },
  get tokenParsed() { return _parsed },

  /** Initialise: handles callback code OR restores/refreshes stored token. */
  async init(_opts?: unknown): Promise<boolean> {
    const sp   = new URLSearchParams(window.location.search)
    const code = sp.get('code')
    const state = sp.get('state')

    // ── Auth-code callback ─────────────────────────────────────────────────
    if (code && state) {
      const savedState = sessionStorage.getItem('kc_state')
      const redir      = sessionStorage.getItem('kc_redir') ?? (window.location.origin + '/')

      if (state === savedState) {
        const data = await callToken({ grant_type: 'authorization_code', code, redirect_uri: redir ?? (currentOrigin() + '/') })
        if (data) {
          store(data)
          window.history.replaceState({}, '', window.location.pathname)
          return true
        }
      }
      clear()
      return false
    }

    // ── Try to restore from localStorage ──────────────────────────────────
    try {
      const raw = localStorage.getItem('kc_token')
      if (raw) {
        const saved = JSON.parse(raw) as TokenData
        if (!isExpired(saved)) {
          _tokens = saved
          _parsed = parseJwt(saved.access_token)
          return true
        }
        // Expired — try refresh
        const refreshed = await callToken({ grant_type: 'refresh_token', refresh_token: saved.refresh_token })
        if (refreshed) { store(refreshed); return true }
        clear()
      }
    } catch { clear() }

    return false  // must login
  },

  login() {
    const state = randHex(24)
    const redir = currentOrigin() + '/'
    sessionStorage.setItem('kc_state', state)
    sessionStorage.setItem('kc_redir', redir)
    window.location.href =
      `${KC_URL}/realms/${REALM}/protocol/openid-connect/auth` +
      `?client_id=${encodeURIComponent(CLIENT)}` +
      `&redirect_uri=${encodeURIComponent(redir)}` +
      `&response_type=code` +
      `&scope=openid%20profile%20email` +
      `&state=${state}`
  },

  logout() {
    const redir = currentOrigin() + '/'
    clear()
    window.location.href =
      `${KC_URL}/realms/${REALM}/protocol/openid-connect/logout` +
      `?client_id=${encodeURIComponent(CLIENT)}` +
      `&post_logout_redirect_uri=${encodeURIComponent(redir)}`
  },

  /** Returns true if token was refreshed, false if still valid, throws if failed. */
  async updateToken(minValidity: number): Promise<boolean> {
    if (!_tokens) throw new Error('not authenticated')
    if (!isExpired(_tokens, minValidity)) return false
    const data = await callToken({ grant_type: 'refresh_token', refresh_token: _tokens.refresh_token })
    if (!data) { clear(); throw new Error('refresh failed') }
    store(data)
    return true
  },
}

export default keycloak

export function getToken(): string {
  return keycloak.token
}

export function authHeaders(): Record<string, string> {
  const t = getToken()
  return t ? { Authorization: `Bearer ${t}` } : {}
}

export function getCurrentUser(): { sub: string; name: string; email: string } | null {
  if (!_parsed) return null
  return {
    sub:   (_parsed.sub as string)                ?? '',
    name:  ((_parsed.name ?? _parsed.preferred_username) as string) ?? '',
    email: (_parsed.email as string)              ?? '',
  }
}

export function getRoles(): string[] {
  const realmAccess = _parsed?.realm_access as { roles?: string[] } | undefined
  return realmAccess?.roles ?? []
}

export function hasRole(role: string): boolean {
  return getRoles().includes(role)
}
