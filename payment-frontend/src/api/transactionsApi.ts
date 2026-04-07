import { Transaction, Account } from '../types'
import { authHeaders } from '../auth/keycloak'

export async function fetchTransactions(accountNumber?: string): Promise<Transaction[]> {
  const url = accountNumber
    ? `/api/transactions?accountNumber=${encodeURIComponent(accountNumber)}`
    : '/api/transactions'
  const r = await fetch(url, { headers: authHeaders() })
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return r.json()
}

export async function fetchAccount(accountNumber: string): Promise<Account | null> {
  const r = await fetch(`/api/accounts/${encodeURIComponent(accountNumber)}`, {
    headers: authHeaders(),
  })
  if (r.status === 404) return null
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return r.json()
}
