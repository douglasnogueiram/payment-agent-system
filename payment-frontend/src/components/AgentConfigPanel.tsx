import { useEffect, useState } from 'react'
import {
  AgentPrompt,
  activatePromptVersion,
  fetchActivePrompt,
  fetchPromptHistory,
  savePrompt,
} from '../api/agentPromptApi'
import './AgentConfigPanel.css'

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

export default function AgentConfigPanel() {
  const [content, setContent] = useState('')
  const [description, setDescription] = useState('')
  const [originalContent, setOriginalContent] = useState('')
  const [history, setHistory] = useState<AgentPrompt[]>([])
  const [saveState, setSaveState] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle')
  const [activatingId, setActivatingId] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)

  const isDirty = content !== originalContent

  useEffect(() => {
    Promise.all([fetchActivePrompt(), fetchPromptHistory()])
      .then(([active, hist]) => {
        setContent(active.content)
        setOriginalContent(active.content)
        setHistory(hist)
      })
      .finally(() => setLoading(false))
  }, [])

  const handleSave = async () => {
    if (!content.trim()) return
    setSaveState('saving')
    try {
      await savePrompt(content, description)
      setOriginalContent(content)
      setDescription('')
      setSaveState('saved')
      const hist = await fetchPromptHistory()
      setHistory(hist)
      setTimeout(() => setSaveState('idle'), 2000)
    } catch {
      setSaveState('error')
      setTimeout(() => setSaveState('idle'), 3000)
    }
  }

  const handleActivate = async (id: number) => {
    setActivatingId(id)
    try {
      const activated = await activatePromptVersion(id)
      setContent(activated.content)
      setOriginalContent(activated.content)
      const hist = await fetchPromptHistory()
      setHistory(hist)
    } finally {
      setActivatingId(null)
    }
  }

  const handleReset = () => {
    setContent(originalContent)
    setDescription('')
  }

  if (loading) return <div className="agent-config__loading">Carregando configuracao…</div>

  return (
    <div className="agent-config">
      <div className="agent-config__header">
        <div>
          <h2 className="agent-config__title">Configuracao do Agente</h2>
          <p className="agent-config__subtitle">
            Edite o prompt do sistema. Cada salvamento cria uma nova versao. Alteracoes tem efeito imediato.
          </p>
        </div>
        <div className="agent-config__actions">
          <button className="ac-btn ac-btn--ghost" onClick={handleReset} disabled={!isDirty}>
            Descartar
          </button>
          <button
            className={`ac-btn${saveState === 'saving' ? ' ac-btn--loading' : saveState === 'saved' ? ' ac-btn--saved' : saveState === 'error' ? ' ac-btn--error' : isDirty ? ' ac-btn--primary' : ' ac-btn--ghost'}`}
            onClick={handleSave}
            disabled={saveState === 'saving' || saveState === 'saved' || !isDirty}
          >
            {saveState === 'saving' ? <><span className="ac-spinner" /> Salvando</> :
             saveState === 'saved' ? 'Salvo' :
             saveState === 'error' ? 'Erro ao salvar' :
             'Salvar nova versao'}
          </button>
        </div>
      </div>

      <section className="agent-config__editor-section">
        <textarea
          className={`agent-config__textarea${isDirty ? ' agent-config__textarea--dirty' : ''}`}
          value={content}
          onChange={e => setContent(e.target.value)}
          spellCheck={false}
        />
        {isDirty && (
          <div className="agent-config__desc-row">
            <input
              type="text"
              className="agent-config__desc-input"
              placeholder="Descreva brevemente a alteracao (opcional)"
              value={description}
              onChange={e => setDescription(e.target.value)}
            />
          </div>
        )}
      </section>

      <section className="agent-config__history">
        <h3 className="agent-config__history-title">Historico de versoes</h3>
        {history.length === 0 ? (
          <p className="agent-config__empty">Nenhuma versao salva ainda.</p>
        ) : (
          <div className="agent-config__history-list">
            {history.map(p => (
              <div key={p.id} className={`ac-version${p.active ? ' ac-version--active' : ''}`}>
                <div className="ac-version__meta">
                  <span className="ac-version__id">v{p.id}</span>
                  {p.active && <span className="ac-version__badge">Ativo</span>}
                  <span className="ac-version__date">{p.createdAt ? formatDate(p.createdAt) : '—'}</span>
                  {p.description && <span className="ac-version__desc">{p.description}</span>}
                </div>
                <div className="ac-version__preview">{p.content?.slice(0, 120)}…</div>
                {!p.active && (
                  <button
                    className="ac-btn ac-btn--ghost ac-btn--sm"
                    onClick={() => handleActivate(p.id!)}
                    disabled={activatingId === p.id}
                  >
                    {activatingId === p.id ? 'Ativando…' : 'Restaurar esta versao'}
                  </button>
                )}
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
