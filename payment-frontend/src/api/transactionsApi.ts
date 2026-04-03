import { Transaction, Account } from '../types'

// ─── Transactions API (/api/transactions) ────────────────────────────────────
// The payment-agent backend at port 8080 exposes this endpoint,
// which proxies or aggregates data from the banking service.

export async function fetchTransactions(accountNumber?: string): Promise<Transaction[]> {
  const url = accountNumber
    ? `/api/transactions?accountNumber=${encodeURIComponent(accountNumber)}`
    : '/api/transactions'
  const r = await fetch(url)
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return r.json()
}

export async function fetchAccount(accountNumber: string): Promise<Account | null> {
  const r = await fetch(`/api/accounts/${encodeURIComponent(accountNumber)}`)
  if (r.status === 404) return null
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return r.json()
}
