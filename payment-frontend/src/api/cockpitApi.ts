export interface TxRow {
  id: number
  accountNumber: string
  type: string
  amount: number
  status: 'PENDING' | 'SUCCESS' | 'FAILED'
  reference: string
  description: string
  balanceAfter: number
  endToEndId: string | null
  celcoinTransactionId: string | null
  recipientName: string | null
  createdAt: string
}

export interface TxEvent {
  id: number
  transactionId: number
  eventType: string
  serviceName: string
  message: string
  createdAt: string
}

export async function fetchRecentTransactions(): Promise<TxRow[]> {
  const r = await fetch('/banking/api/admin/transactions')
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return r.json()
}

export async function fetchMyAccount(): Promise<{ accountNumber: string; agency: string; name: string } | null> {
  const { authHeaders } = await import('../auth/keycloak')
  const r = await fetch('/api/me/account', { headers: authHeaders() })
  if (r.status === 404) return null
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return r.json()
}

export async function fetchUserTransactions(accountNumber: string): Promise<TxRow[]> {
  const { authHeaders } = await import('../auth/keycloak')
  const r = await fetch(`/api/transactions?accountNumber=${encodeURIComponent(accountNumber)}`, {
    headers: authHeaders(),
  })
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return r.json()
}

export async function fetchTransactionEvents(txId: number): Promise<TxEvent[]> {
  const r = await fetch(`/banking/api/admin/transactions/${txId}/events`)
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return r.json()
}

export async function fetchReceiptUrl(txId: number): Promise<string> {
  const r = await fetch(`/banking/api/admin/transactions/${txId}/receipt/pdf`)
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  const data = await r.json()
  return data.downloadUrl as string
}
