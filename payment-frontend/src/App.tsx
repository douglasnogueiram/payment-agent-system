import { useState, useEffect } from 'react'
import Header from './components/Header'
import ChatPanel from './components/ChatPanel'
import PaymentEventPanel from './components/PaymentEventPanel'
import TransactionsPanel from './components/TransactionsPanel'
import RagPanel from './components/RagPanel'
import VoiceConfigPanel from './components/VoiceConfigPanel'
import AgentConfigPanel from './components/AgentConfigPanel'
import Cockpit from './components/Cockpit'
import { useChat } from './hooks/useChat'
import { VoiceConfig, DEFAULT_VOICE_CONFIG } from './types'
import { fetchVoiceConfig } from './api/voiceConfigApi'
import { hasRole } from './auth/keycloak'
import { fetchReceiptUrl } from './api/cockpitApi'
import './App.css'

type Tab = 'chat' | 'transactions' | 'cockpit' | 'rag' | 'voice' | 'agent'

export default function App() {
  const isAdmin = hasRole('ADMIN')
  const [activeTab, setActiveTab] = useState<Tab>('chat')
  const [voiceConfig, setVoiceConfig] = useState<VoiceConfig>(DEFAULT_VOICE_CONFIG)
  const { messages, isLoading, sendMessage, injectMessage } = useChat()

  async function handlePaymentComplete(txId: number) {
    try {
      const url = await fetchReceiptUrl(txId)
      injectMessage(`✅ Pix confirmado! Seu comprovante está pronto:\n\n[Baixar Comprovante PDF](${url})`)
    } catch { /* ignore */ }
  }

  useEffect(() => {
    fetchVoiceConfig().then(setVoiceConfig).catch(() => {})
  }, [])

  // If current tab is not allowed for this role, reset to chat
  useEffect(() => {
    if (!isAdmin && activeTab !== 'chat') setActiveTab('chat')
  }, [isAdmin, activeTab])

  return (
    <div className="app">
      <Header />

      <nav className="tab-bar">
        <button
          className={`tab${activeTab === 'chat' ? ' tab--active' : ''}`}
          onClick={() => setActiveTab('chat')}
        >
          <span className="tab-icon">💬</span> Chat
        </button>
        {isAdmin && (
          <>
            <button
              className={`tab${activeTab === 'transactions' ? ' tab--active' : ''}`}
              onClick={() => setActiveTab('transactions')}
            >
              <span className="tab-icon">📊</span> Transações
            </button>
            <button
              className={`tab${activeTab === 'cockpit' ? ' tab--active' : ''}`}
              onClick={() => setActiveTab('cockpit')}
            >
              <span className="tab-icon">🎛️</span> Cockpit
            </button>
            <button
              className={`tab${activeTab === 'rag' ? ' tab--active' : ''}`}
              onClick={() => setActiveTab('rag')}
            >
              <span className="tab-icon">📚</span> Conhecimento
            </button>
            <button
              className={`tab${activeTab === 'voice' ? ' tab--active' : ''}`}
              onClick={() => setActiveTab('voice')}
            >
              <span className="tab-icon">🔊</span> Voz
            </button>
            <button
              className={`tab${activeTab === 'agent' ? ' tab--active' : ''}`}
              onClick={() => setActiveTab('agent')}
            >
              <span className="tab-icon">🤖</span> Agente
            </button>
          </>
        )}
      </nav>

      <main className="main-content">
        {activeTab === 'chat' && (
          <div className="chat-layout">
            <ChatPanel
              messages={messages}
              isLoading={isLoading}
              onSend={sendMessage}
              voiceConfig={voiceConfig}
            />
            <PaymentEventPanel onPaymentComplete={handlePaymentComplete} />
          </div>
        )}
        {isAdmin && activeTab === 'transactions' && <TransactionsPanel />}
        {isAdmin && activeTab === 'cockpit' && <Cockpit />}
        {isAdmin && activeTab === 'rag' && <RagPanel />}
        {isAdmin && activeTab === 'voice' && (
          <VoiceConfigPanel config={voiceConfig} onChange={setVoiceConfig} />
        )}
        {isAdmin && activeTab === 'agent' && <AgentConfigPanel />}
      </main>
    </div>
  )
}
