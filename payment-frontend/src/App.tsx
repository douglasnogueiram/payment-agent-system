import { useState, useEffect } from 'react'
import Header from './components/Header'
import ChatPanel from './components/ChatPanel'
import TransactionsPanel from './components/TransactionsPanel'
import RagPanel from './components/RagPanel'
import VoiceConfigPanel from './components/VoiceConfigPanel'
import AgentConfigPanel from './components/AgentConfigPanel'
import { useChat } from './hooks/useChat'
import { VoiceConfig, DEFAULT_VOICE_CONFIG } from './types'
import { fetchVoiceConfig } from './api/voiceConfigApi'
import './App.css'

type Tab = 'chat' | 'transactions' | 'rag' | 'voice' | 'agent'

export default function App() {
  const [activeTab, setActiveTab] = useState<Tab>('chat')
  const [voiceConfig, setVoiceConfig] = useState<VoiceConfig>(DEFAULT_VOICE_CONFIG)
  const { messages, isLoading, sendMessage } = useChat()

  useEffect(() => {
    fetchVoiceConfig().then(setVoiceConfig).catch(() => {})
  }, [])

  return (
    <div className="app">
      <Header />

      <nav className="tab-bar">
        <button
          className={`tab${activeTab === 'chat' ? ' tab--active' : ''}`}
          onClick={() => setActiveTab('chat')}
        >
          Chat
        </button>
        <button
          className={`tab${activeTab === 'transactions' ? ' tab--active' : ''}`}
          onClick={() => setActiveTab('transactions')}
        >
          Transacoes
        </button>
        <button
          className={`tab${activeTab === 'rag' ? ' tab--active' : ''}`}
          onClick={() => setActiveTab('rag')}
        >
          Base de Conhecimento
        </button>
        <button
          className={`tab${activeTab === 'voice' ? ' tab--active' : ''}`}
          onClick={() => setActiveTab('voice')}
        >
          Configuracao de Voz
        </button>
        <button
          className={`tab${activeTab === 'agent' ? ' tab--active' : ''}`}
          onClick={() => setActiveTab('agent')}
        >
          Configuracao do Agente
        </button>
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
          </div>
        )}
        {activeTab === 'transactions' && <TransactionsPanel />}
        {activeTab === 'rag' && <RagPanel />}
        {activeTab === 'voice' && (
          <VoiceConfigPanel config={voiceConfig} onChange={setVoiceConfig} />
        )}
        {activeTab === 'agent' && <AgentConfigPanel />}
      </main>
    </div>
  )
}
