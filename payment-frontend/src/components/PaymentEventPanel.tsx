import { useEffect, useRef, useState } from 'react'
import { fetchMyAccount, fetchUserTransactions, fetchTransactionEvents, fetchReceiptUrl, TxRow, TxEvent } from '../api/cockpitApi'
import './PaymentEventPanel.css'

// ── Step model ──────────────────────────────────────────────────────
type StepState = 'idle' | 'active' | 'done' | 'failed'

interface Step {
  label: string
  sublabel: string
  activeLabel: string
  state: StepState
}

function deriveSteps(events: TxEvent[], tx: TxRow): Step[] {
  const types = new Set(events.map(e => e.eventType))

  const step1State: StepState =
    types.has('BALANCE_DEBITED') || types.has('CELCOIN_SUBMITTED') ? 'done'
    : types.has('DICT_LOOKUP') || types.has('DICT_FOUND') ? 'active'
    : 'idle'

  const step2State: StepState =
    types.has('WEBHOOK_RECEIVED') ? 'done'
    : types.has('AWAITING_WEBHOOK') ? 'active'
    : 'idle'

  const step3State: StepState =
    types.has('ERROR') ? 'failed'
    : types.has('FINALIZED')
      ? (tx.status === 'SUCCESS' ? 'done' : 'failed')
    : 'idle'

  return [
    {
      label: 'Pagamento solicitado',
      sublabel: 'Dados validados e débito efetuado',
      activeLabel: 'Validando dados e efetuando débito…',
      state: step1State,
    },
    {
      label: 'Aguardando o banco',
      sublabel: 'Transferência enviada ao banco do destinatário',
      activeLabel: 'Aguardando resposta do banco do destinatário…',
      state: step2State,
    },
    {
      label: tx.status === 'FAILED' ? 'Pagamento rejeitado' : 'Pagamento confirmado',
      sublabel: tx.status === 'FAILED'
        ? 'O valor foi estornado para sua conta'
        : 'O destinatário recebeu o valor',
      activeLabel: '',
      state: step3State,
    },
  ]
}

function fmtAmount(n: number) {
  return n.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function fmtTime(iso: string) {
  return new Date(iso).toLocaleTimeString('pt-BR', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

interface Props {
  onPaymentComplete?: (txId: number) => void
}

// ── Component ───────────────────────────────────────────────────────
export default function PaymentEventPanel({ onPaymentComplete }: Props) {
  const [accountNumber, setAccountNumber] = useState<string | null | undefined>(undefined) // undefined=loading, null=no account
  const [transactions, setTransactions] = useState<TxRow[]>([])
  const [trackedId, setTrackedId] = useState<number | null>(null)
  const prevStatusRef = useRef<string | null>(null)
  const [events, setEvents] = useState<TxEvent[]>([])
  const [collapsed, setCollapsed] = useState(false)
  const [receiptLoading, setReceiptLoading] = useState(false)
  const hasPending = transactions.some(t => t.status === 'PENDING')

  // ── Resolve account on mount ─────────────────────────────────────
  useEffect(() => {
    fetchMyAccount()
      .then(acct => setAccountNumber(acct?.accountNumber ?? null))
      .catch(() => setAccountNumber(null))
  }, [])

  // ── Poll transaction list (only when account is known) ───────────
  useEffect(() => {
    if (!accountNumber) return
    let alive = true
    async function refresh() {
      try {
        const txs = await fetchUserTransactions(accountNumber!)
        if (!alive) return
        setTransactions(txs)
        // Always track the most recent transaction
        if (txs.length > 0) setTrackedId(txs[0].id)
      } catch { /* ignore */ }
    }
    refresh()
    const id = setInterval(refresh, hasPending ? 1000 : 3000)
    return () => { alive = false; clearInterval(id) }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accountNumber, hasPending])

  // ── Poll events for tracked transaction ─────────────────────────
  useEffect(() => {
    if (trackedId == null) { setEvents([]); return }
    let alive = true
    async function load() {
      try {
        const evts = await fetchTransactionEvents(trackedId!)
        if (alive) setEvents(evts)
      } catch { /* ignore */ }
    }
    load()
    const tx = transactions.find(t => t.id === trackedId)
    if (tx?.status === 'PENDING') {
      const id = setInterval(load, 800)
      return () => { alive = false; clearInterval(id) }
    }
    return () => { alive = false }
  }, [trackedId, transactions])

  // ── Detect PENDING → SUCCESS transition and inject receipt in chat ─
  useEffect(() => {
    const tx = transactions.find(t => t.id === trackedId)
    const status = tx?.status ?? null
    if (prevStatusRef.current === 'PENDING' && status === 'SUCCESS' && trackedId) {
      onPaymentComplete?.(trackedId)
    }
    prevStatusRef.current = status
  }, [transactions, trackedId, onPaymentComplete])

  // ── Auto-expand when a payment is processing ─────────────────────
  useEffect(() => {
    if (hasPending) setCollapsed(false)
  }, [hasPending])

  const trackedTx = transactions.find(t => t.id === trackedId) ?? null
  const steps = trackedTx ? deriveSteps(events, trackedTx) : []
  const activeStep = steps.find(s => s.state === 'active')
  const isFailed = trackedTx?.status === 'FAILED'
  const isDone = trackedTx?.status === 'SUCCESS'

  async function downloadReceipt() {
    if (!trackedId) return
    setReceiptLoading(true)
    try {
      const url = await fetchReceiptUrl(trackedId)
      window.open(url, '_blank')
    } catch {
      alert('Não foi possível gerar o comprovante. Tente novamente.')
    } finally {
      setReceiptLoading(false)
    }
  }

  // ── Collapsed strip ──────────────────────────────────────────────
  if (collapsed) {
    return (
      <div
        className="pep pep--collapsed"
        onClick={() => setCollapsed(false)}
        title="Ver acompanhamento do Pix"
      >
        {hasPending && <span className="pep-dot-pulse" />}
        <span className="pep-label-vert">Pix</span>
        <span className="pep-expand-arrow">◀</span>
      </div>
    )
  }

  return (
    <div className="pep">
      {/* Header */}
      <div className="pep-header">
        <span className="pep-header-title">Acompanhamento do Pix</span>
        <div className="pep-header-right">
          {hasPending && <span className="pep-live">● ao vivo</span>}
          <button className="pep-collapse-btn" onClick={() => setCollapsed(true)} title="Recolher">▶</button>
        </div>
      </div>

      {/* Content */}
      {accountNumber === null ? (
        <div className="pep-empty">
          <div className="pep-empty-icon">🏦</div>
          <div className="pep-empty-text">Você não possui conta corrente ativa.</div>
        </div>
      ) : !trackedTx ? (
        <div className="pep-empty">
          <div className="pep-empty-icon">📲</div>
          <div className="pep-empty-text">Inicie um pagamento pelo chat para acompanhar aqui.</div>
        </div>
      ) : (
        <div className="pep-body">
          {/* Amount + recipient card */}
          <div className={`pep-card ${isFailed ? 'pep-card--failed' : isDone ? 'pep-card--done' : 'pep-card--pending'}`}>
            <div className="pep-card-amount">{fmtAmount(trackedTx.amount)}</div>
            {trackedTx.recipientName && (
              <div className="pep-card-recipient">
                <span className="pep-card-label">Para</span>
                <span className="pep-card-name">{trackedTx.recipientName}</span>
              </div>
            )}
            {trackedTx.reference && (
              <div className="pep-card-recipient">
                <span className="pep-card-label">Chave</span>
                <span className="pep-card-key">{trackedTx.reference}</span>
              </div>
            )}
            {trackedTx.endToEndId && (
              <div className="pep-card-recipient">
                <span className="pep-card-label">E2E</span>
                <span className="pep-card-e2e" title={trackedTx.endToEndId}>
                  {trackedTx.endToEndId.slice(0, 20)}…
                </span>
              </div>
            )}
            <div className="pep-card-time">{fmtTime(trackedTx.createdAt)}</div>
          </div>

          {/* Active step message */}
          {activeStep && (
            <div className="pep-active-msg">
              <span className="pep-active-spinner" />
              {activeStep.activeLabel}
            </div>
          )}
          {isFailed && (
            <div className="pep-result-msg pep-result-msg--failed">
              ✕ Pagamento não concluído. O valor foi devolvido à sua conta.
            </div>
          )}
          {isDone && (
            <div className="pep-result-msg pep-result-msg--done">
              ✓ Tudo certo! O pagamento foi confirmado.
            </div>
          )}

          {/* Receipt download button */}
          {(isDone || isFailed) && (
            <button
              className={`pep-receipt-btn ${isFailed ? 'pep-receipt-btn--failed' : ''}`}
              onClick={downloadReceipt}
              disabled={receiptLoading}
            >
              {receiptLoading ? 'Gerando…' : '⬇ Baixar Comprovante PDF'}
            </button>
          )}

          {/* Step tracker */}
          <div className="pep-steps">
            {steps.map((step, i) => (
              <div key={i} className={`pep-step pep-step--${step.state}`}>
                {/* connector line above (except first) */}
                <div className="pep-step-track">
                  <div className={`pep-step-line pep-step-line--${i === 0 ? 'none' : steps[i - 1].state}`} />
                  <div className="pep-step-circle">
                    {step.state === 'done'    && <span className="pep-step-icon">✓</span>}
                    {step.state === 'failed'  && <span className="pep-step-icon">✕</span>}
                    {step.state === 'active'  && <span className="pep-step-spinner" />}
                    {step.state === 'idle'    && <span className="pep-step-num">{i + 1}</span>}
                  </div>
                  <div className={`pep-step-line pep-step-line--${i === steps.length - 1 ? 'none' : step.state}`} />
                </div>
                {/* text */}
                <div className="pep-step-content">
                  <div className="pep-step-label">{step.label}</div>
                  {(step.state === 'done' || step.state === 'failed') && (
                    <div className="pep-step-sub">{step.sublabel}</div>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
