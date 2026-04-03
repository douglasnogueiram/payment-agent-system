import { useEffect, useRef, useState, KeyboardEvent } from 'react'
import Markdown from 'react-markdown'
import { Message, VoiceConfig, DEFAULT_VOICE_CONFIG } from '../types'
import { synthesizeSpeech } from '../api/speechApi'
import './ChatPanel.css'

interface Props {
  messages: Message[]
  isLoading: boolean
  onSend: (text: string) => void
  voiceConfig?: VoiceConfig
}

export default function ChatPanel({ messages, isLoading, onSend, voiceConfig = DEFAULT_VOICE_CONFIG }: Props) {
  const [input, setInput] = useState('')
  const [speakingId, setSpeakingId] = useState<string | null>(null)
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const bottomRef = useRef<HTMLDivElement>(null)

  const speak = async (id: string, text: string) => {
    if (audioRef.current) {
      audioRef.current.pause()
      audioRef.current = null
    }
    if (speakingId === id) {
      setSpeakingId(null)
      return
    }
    setSpeakingId(id)
    try {
      const blob = await synthesizeSpeech(text, voiceConfig)
      const url = URL.createObjectURL(blob)
      const audio = new Audio(url)
      audioRef.current = audio
      audio.onended = () => {
        setSpeakingId(null)
        URL.revokeObjectURL(url)
      }
      audio.play()
    } catch {
      setSpeakingId(null)
    }
  }

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const submit = () => {
    const text = input.trim()
    if (!text || isLoading) return
    setInput('')
    onSend(text)
  }

  const onKey = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      submit()
    }
  }

  return (
    <div className="chat-panel">
      <div className="message-list">
        {messages.length === 0 && (
          <div className="empty-chat">
            <p>Ola! Sou o assistente de pagamentos.</p>
            <p>Posso ajudar com PIX, boletos, consultas de saldo e transferencias.</p>
          </div>
        )}
        {messages.map((msg) => (
          <div key={msg.id} className={`bubble-wrap bubble-wrap--${msg.role}`}>
            <div className={`bubble bubble--${msg.role}`}>
              {msg.role === 'assistant' ? (
                <Markdown>{msg.content || (isLoading ? '…' : '')}</Markdown>
              ) : (
                msg.content
              )}
            </div>
            {msg.role === 'assistant' && msg.content && (
              <button
                className={`speak-btn${speakingId === msg.id ? ' speak-btn--active' : ''}`}
                onClick={() => speak(msg.id, msg.content)}
                title={speakingId === msg.id ? 'Parar audio' : 'Ouvir resposta'}
              >
                {speakingId === msg.id ? (
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M6 6h4v12H6zm8 0h4v12h-4z" />
                  </svg>
                ) : (
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02z"/>
                  </svg>
                )}
              </button>
            )}
          </div>
        ))}
        {isLoading && messages[messages.length - 1]?.role !== 'assistant' && (
          <div className="bubble-wrap bubble-wrap--assistant">
            <div className="bubble bubble--assistant bubble--typing">
              <span /><span /><span />
            </div>
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      <div className="chat-input-bar">
        <textarea
          className="chat-input"
          rows={1}
          placeholder="Digite sua mensagem… (Enter para enviar)"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={onKey}
          disabled={isLoading}
        />
        <button className="send-btn" onClick={submit} disabled={isLoading || !input.trim()}>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
            <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z" />
          </svg>
        </button>
      </div>
    </div>
  )
}
