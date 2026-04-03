import { useEffect, useState } from 'react'
import { Transaction, TransactionStatus, TransactionType } from '../types'
import { fetchTransactions } from '../api/transactionsApi'
import './TransactionsPanel.css'

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

function formatCurrency(value: number): string {
  return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function typeLabel(type: TransactionType): string {
  switch (type) {
    case 'PIX':           return 'PIX'
    case 'BOLETO':        return 'Boleto'
    case 'SALDO':         return 'Consulta Saldo'
    case 'TRANSFERENCIA': return 'Transferencia'
    case 'PAGAMENTO':     return 'Pagamento'
    default:              return type
  }
}

function statusLabel(status: TransactionStatus): string {
  switch (status) {
    case 'COMPLETED': return 'Concluida'
    case 'PENDING':   return 'Pendente'
    case 'FAILED':    return 'Falhou'
    case 'CANCELLED': return 'Cancelada'
    default:          return status
  }
}

function statusClass(status: TransactionStatus): string {
  switch (status) {
    case 'COMPLETED': return 'tx-badge--completed'
    case 'PENDING':   return 'tx-badge--pending'
    case 'FAILED':    return 'tx-badge--failed'
    case 'CANCELLED': return 'tx-badge--cancelled'
    default:          return ''
  }
}

function typeClass(type: TransactionType): string {
  switch (type) {
    case 'PIX':           return 'tx-type--pix'
    case 'BOLETO':        return 'tx-type--boleto'
    case 'SALDO':         return 'tx-type--saldo'
    case 'TRANSFERENCIA': return 'tx-type--transferencia'
    case 'PAGAMENTO':     return 'tx-type--pagamento'
    default:              return ''
  }
}

export default function TransactionsPanel() {
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [accountFilter, setAccountFilter] = useState('')
  const [filterInput, setFilterInput] = useState('')

  const load = async (account?: string) => {
    setLoading(true)
    setError('')
    try {
      const data = await fetchTransactions(account || undefined)
      setTransactions(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao carregar transacoes.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const handleFilter = (e: React.FormEvent) => {
    e.preventDefault()
    setAccountFilter(filterInput.trim())
    load(filterInput.trim() || undefined)
  }

  const handleClearFilter = () => {
    setFilterInput('')
    setAccountFilter('')
    load()
  }

  return (
    <div className="tx-panel">
      <div className="tx-panel__header">
        <div>
          <h2 className="tx-panel__title">Transacoes</h2>
          <p className="tx-panel__subtitle">
            Historico de transacoes recentes: PIX, boletos e consultas de saldo.
          </p>
        </div>
        <button
          className="tx-btn tx-btn--ghost tx-btn--sm"
          onClick={() => load(accountFilter || undefined)}
          disabled={loading}
        >
          {loading ? 'Atualizando…' : 'Atualizar'}
        </button>
      </div>

      <form className="tx-filter" onSubmit={handleFilter}>
        <input
          className="tx-filter__input"
          type="text"
          placeholder="Filtrar por numero da conta…"
          value={filterInput}
          onChange={(e) => setFilterInput(e.target.value)}
        />
        <button type="submit" className="tx-btn tx-btn--primary tx-btn--sm">
          Filtrar
        </button>
        {accountFilter && (
          <button type="button" className="tx-btn tx-btn--ghost tx-btn--sm" onClick={handleClearFilter}>
            Limpar filtro
          </button>
        )}
      </form>

      {accountFilter && (
        <div className="tx-filter-badge">
          Exibindo transacoes da conta: <strong>{accountFilter}</strong>
        </div>
      )}

      {error && (
        <div className="tx-feedback tx-feedback--error">{error}</div>
      )}

      {!loading && !error && transactions.length === 0 && (
        <div className="tx-empty">
          Nenhuma transacao encontrada{accountFilter ? ` para a conta ${accountFilter}` : ''}.
        </div>
      )}

      {loading && (
        <div className="tx-empty">Carregando transacoes…</div>
      )}

      {!loading && transactions.length > 0 && (
        <div className="tx-table-wrap">
          <table className="tx-table">
            <thead>
              <tr>
                <th>Tipo</th>
                <th>Descricao</th>
                <th>Conta</th>
                <th>Valor</th>
                <th>Status</th>
                <th>Data</th>
                <th>Destinatario</th>
              </tr>
            </thead>
            <tbody>
              {transactions.map((tx) => (
                <tr key={tx.id} className="tx-table__row">
                  <td>
                    <span className={`tx-type ${typeClass(tx.type)}`}>
                      {typeLabel(tx.type)}
                    </span>
                  </td>
                  <td className="tx-table__desc">{tx.description || '—'}</td>
                  <td className="tx-table__account">{tx.accountNumber}</td>
                  <td className={`tx-table__amount${tx.amount < 0 ? ' tx-table__amount--debit' : ' tx-table__amount--credit'}`}>
                    {tx.type === 'SALDO' ? '—' : formatCurrency(tx.amount)}
                  </td>
                  <td>
                    <span className={`tx-badge ${statusClass(tx.status)}`}>
                      {statusLabel(tx.status)}
                    </span>
                  </td>
                  <td className="tx-table__date">{formatDate(tx.createdAt)}</td>
                  <td className="tx-table__recipient">{tx.recipientName || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
