import { useEffect, useRef, useState } from 'react'
import { fetchRecentTransactions, fetchTransactionEvents, TxRow, TxEvent } from '../api/cockpitApi'
import './Cockpit.css'

const EVENT_ICONS: Record<string, string> = {
  DICT_LOOKUP:       '🔍',
  DICT_FOUND:        '✅',
  BALANCE_DEBITED:   '💸',
  CELCOIN_SUBMITTED: '📡',
  AWAITING_WEBHOOK:  '⏳',
  WEBHOOK_RECEIVED:  '🔔',
  FINALIZED:         '🏁',
  ERROR:             '❌',
}

function fmtTime(iso: string) {
  const d = new Date(iso)
  return d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) +
    '.' + String(d.getMilliseconds()).padStart(3, '0')
}

function fmtAmount(n: number) {
  return n.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function shortKey(ref: string | null) {
  if (!ref) return '—'
  return ref.length > 30 ? ref.slice(0, 28) + '…' : ref
}

export default function Cockpit() {
  const [transactions, setTransactions] = useState<TxRow[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [events, setEvents] = useState<TxEvent[]>([])
  const [error, setError] = useState<string | null>(null)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const selectedTx = transactions.find(t => t.id === selectedId) ?? null
  const hasPending  = transactions.some(t => t.status === 'PENDING')

  // Poll transactions list
  useEffect(() => {
    let alive = true

    async function refresh() {
      try {
        const txs = await fetchRecentTransactions()
        if (alive) {
          setTransactions(txs)
          setError(null)
          // Auto-select the most recent if nothing selected
          if (txs.length > 0 && selectedId === null) {
            setSelectedId(txs[0].id)
          }
        }
      } catch (e: any) {
        if (alive) setError(e.message)
      }
    }

    refresh()
    // Poll faster when there are PENDING transactions
    intervalRef.current = setInterval(refresh, hasPending ? 1000 : 3000)

    return () => {
      alive = false
      if (intervalRef.current) clearInterval(intervalRef.current)
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hasPending])

  // Fetch events when selected transaction changes
  useEffect(() => {
    if (selectedId == null) { setEvents([]); return }

    let alive = true
    async function loadEvents() {
      try {
        const evts = await fetchTransactionEvents(selectedId!)
        if (alive) setEvents(evts)
      } catch { /* ignore */ }
    }

    loadEvents()
    // Keep refreshing events while the selected tx is PENDING
    const tx = transactions.find(t => t.id === selectedId)
    if (tx?.status === 'PENDING') {
      const id = setInterval(loadEvents, 1000)
      return () => { alive = false; clearInterval(id) }
    }
    return () => { alive = false }
  }, [selectedId, transactions])

  return (
    <div className="cockpit">
      {/* ── Left: transaction list ─────────────────────────────── */}
      <div className="cockpit-list">
        <div className="cockpit-list-header">
          <h3>Transações PIX</h3>
          {hasPending
            ? <span className="cockpit-refresh-badge">● LIVE</span>
            : <span className="cockpit-refresh-badge" style={{ animationDuration: '4s' }}>● auto</span>
          }
        </div>

        <div className="cockpit-list-body">
          {error && (
            <div style={{ padding: '12px 16px', color: '#f85149', fontSize: '0.75rem' }}>
              Erro: {error}
            </div>
          )}
          {transactions.length === 0 && !error && (
            <div className="cockpit-empty" style={{ paddingTop: 40 }}>Nenhuma transação ainda.</div>
          )}
          {transactions.map(tx => (
            <div
              key={tx.id}
              className={`cockpit-tx-row${selectedId === tx.id ? ' selected' : ''}`}
              onClick={() => setSelectedId(tx.id)}
            >
              <div className="cockpit-tx-top">
                <span className="cockpit-tx-amount">{fmtAmount(tx.amount)}</span>
                <span className={`badge badge--${tx.status}`}>{tx.status}</span>
              </div>
              <span className="cockpit-tx-key">{shortKey(tx.reference)}</span>
              <span className="cockpit-tx-time">#{tx.id} · {fmtTime(tx.createdAt)}</span>
            </div>
          ))}
        </div>
      </div>

      {/* ── Right: event timeline ──────────────────────────────── */}
      <div className="cockpit-detail">
        {selectedTx == null ? (
          <div className="cockpit-empty">Selecione uma transação para ver o fluxo.</div>
        ) : (
          <>
            <div className="cockpit-detail-header">
              <h3>Fluxo da Transação #{selectedTx.id}</h3>
              <div className="cockpit-tx-meta">
                <span><strong>{fmtAmount(selectedTx.amount)}</strong></span>
                <span>→ <strong>{selectedTx.reference}</strong></span>
                <span className={`badge badge--${selectedTx.status}`}>{selectedTx.status}</span>
                {selectedTx.endToEndId && (
                  <span title={selectedTx.endToEndId}>
                    E2E: <strong>{selectedTx.endToEndId.slice(0, 18)}…</strong>
                  </span>
                )}
                {selectedTx.celcoinTransactionId && (
                  <span title={selectedTx.celcoinTransactionId}>
                    CelcoinId: <strong>{selectedTx.celcoinTransactionId.slice(0, 12)}…</strong>
                  </span>
                )}
              </div>
            </div>

            <div className="cockpit-timeline">
              {events.length === 0 && (
                <div className="cockpit-empty">Carregando eventos…</div>
              )}
              {events.map(evt => (
                <div key={evt.id} className={`event-row event-row--${evt.eventType}`}>
                  <div className={`event-icon event-icon--${evt.eventType}`}>
                    {EVENT_ICONS[evt.eventType] ?? '·'}
                  </div>
                  <div className="event-body">
                    <div className="event-header">
                      <span className="event-type">{evt.eventType}</span>
                      <span className="event-time">{fmtTime(evt.createdAt)}</span>
                    </div>
                    <div className="event-message">{evt.message}</div>
                  </div>
                </div>
              ))}
              {selectedTx.status === 'PENDING' && (
                <div className="event-row event-row--AWAITING_WEBHOOK">
                  <div className="event-icon event-icon--AWAITING_WEBHOOK">⏳</div>
                  <div className="event-body">
                    <div className="event-header">
                      <span className="event-type">AGUARDANDO</span>
                    </div>
                    <div className="event-message">Aguardando confirmação da Celcoin…</div>
                  </div>
                </div>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  )
}
