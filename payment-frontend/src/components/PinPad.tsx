import { useState } from 'react'
import './PinPad.css'

interface Props {
  onConfirm: (pin: string) => void
  onCancel: () => void
}

const KEYS = ['1','2','3','4','5','6','7','8','9','','0','⌫']

export default function PinPad({ onConfirm, onCancel }: Props) {
  const [digits, setDigits] = useState<string[]>([])

  const press = (key: string) => {
    if (key === '⌫') {
      setDigits(d => d.slice(0, -1))
    } else if (digits.length < 6) {
      const next = [...digits, key]
      setDigits(next)
      if (next.length === 6) {
        // auto-confirm after short delay so user sees 6th dot fill
        setTimeout(() => onConfirm(next.join('')), 120)
      }
    }
  }

  return (
    <div className="pinpad-overlay">
      <div className="pinpad">
        <p className="pinpad-title">Digite sua senha de transação</p>
        <p className="pinpad-sub">6 dígitos numéricos</p>

        <div className="pinpad-dots">
          {Array.from({ length: 6 }).map((_, i) => (
            <span key={i} className={`pinpad-dot${i < digits.length ? ' pinpad-dot--filled' : ''}`} />
          ))}
        </div>

        <div className="pinpad-grid">
          {KEYS.map((key, i) => (
            key === '' ? (
              <span key={i} />
            ) : (
              <button
                key={i}
                className={`pinpad-key${key === '⌫' ? ' pinpad-key--back' : ''}`}
                onClick={() => press(key)}
                disabled={key !== '⌫' && digits.length === 6}
              >
                {key}
              </button>
            )
          ))}
        </div>

        <button className="pinpad-cancel" onClick={onCancel}>
          Cancelar
        </button>
      </div>
    </div>
  )
}
