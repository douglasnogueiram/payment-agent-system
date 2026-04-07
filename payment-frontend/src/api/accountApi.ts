import { authHeaders } from '../auth/keycloak'

export interface AccountInfo {
  accountNumber: string
  agency: string
  name: string
}

export async function fetchMyAccount(): Promise<AccountInfo | null> {
  const res = await fetch('/api/me/account', { headers: authHeaders() })
  if (res.status === 404) return null
  if (!res.ok) throw new Error('Erro ao buscar conta')
  return res.json()
}
