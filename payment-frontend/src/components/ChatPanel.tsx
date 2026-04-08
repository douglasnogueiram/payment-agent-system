import { useEffect, useRef, useState, KeyboardEvent, ChangeEvent } from 'react'
import Markdown from 'react-markdown'
import type { Components } from 'react-markdown'
import jsQR from 'jsqr'
import { Message, VoiceConfig, DEFAULT_VOICE_CONFIG } from '../types'
import { synthesizeSpeech, transcribeAudio } from '../api/speechApi'
import PinPad from './PinPad'
import './ChatPanel.css'

const PDF_URL_RE = /\/banking\/api\/(statements\/pdf|admin\/receipt\/pdf)\//

function PdfDownloadCard({ href, children }: { href: string; children: React.ReactNode }) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      className="pdf-download-card"
      download
    >
      <span className="pdf-download-card__icon">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6zm-1 7V3.5L18.5 9H13zM8 13h8v1.5H8V13zm0 3h5v1.5H8V16zm0-6h2v1.5H8V10z"/>
        </svg>
      </span>
      <span className="pdf-download-card__text">
        <span className="pdf-download-card__title">{children}</span>
        <span className="pdf-download-card__subtitle">PDF · Válido por 10 min</span>
      </span>
      <span className="pdf-download-card__arrow">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
          <path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/>
        </svg>
      </span>
    </a>
  )
}

const markdownComponents: Components = {
  a({ href, children }) {
    if (href && PDF_URL_RE.test(href)) {
      return <PdfDownloadCard href={href}>{children}</PdfDownloadCard>
    }
    return <a href={href} target="_blank" rel="noopener noreferrer">{children}</a>
  },
}

const PIN_TRIGGER_RE = /senha\s*(de\s*)?(transa[cç][aã]o|6\s*d[ií]gitos?|num[eé]rica)|6\s*d[ií]gitos?\s*num[eé]ricos?|informe\s*(sua\s*)?senha|digite\s*(sua\s*)?senha/i

interface ImageAttachment {
  base64: string
  mimeType: string
  dataUrl: string
}

interface Props {
  messages: Message[]
  isLoading: boolean
  onSend: (text: string, displayText?: string, image?: ImageAttachment) => void
  voiceConfig?: VoiceConfig
}

export default function ChatPanel({ messages, isLoading, onSend, voiceConfig = DEFAULT_VOICE_CONFIG }: Props) {
  const [input, setInput] = useState('')
  const [speakingId, setSpeakingId] = useState<string | null>(null)
  const [showPinPad, setShowPinPad] = useState(false)
  const [imageAttachment, setImageAttachment] = useState<ImageAttachment | null>(null)
  const [micState, setMicState] = useState<'idle' | 'recording' | 'transcribing'>('idle')
  const [micError, setMicError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const qrInputRef = useRef<HTMLInputElement>(null)
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const recorderRef = useRef<MediaRecorder | null>(null)
  const chunksRef = useRef<BlobPart[]>([])
  const bottomRef = useRef<HTMLDivElement>(null)

  // ID of the message that has the "Inserir senha" button pending action
  const [pendingPinMsgId, setPendingPinMsgId] = useState<string | null>(null)
  // IDs of messages where the PIN has already been submitted (button disappears)
  const submittedPinMsgIds = useRef<Set<string>>(new Set())

  // Detect when the agent asks for the password — show button, don't auto-open
  const lastAssistantMsg = [...messages].reverse().find((m) => m.role === 'assistant')
  useEffect(() => {
    if (
      !isLoading &&
      lastAssistantMsg?.content &&
      !submittedPinMsgIds.current.has(lastAssistantMsg.id) &&
      pendingPinMsgId !== lastAssistantMsg.id &&
      PIN_TRIGGER_RE.test(lastAssistantMsg.content)
    ) {
      setPendingPinMsgId(lastAssistantMsg.id)
    }
  }, [lastAssistantMsg?.id, isLoading])

  const handlePinConfirm = (pin: string) => {
    setShowPinPad(false)
    if (pendingPinMsgId) {
      submittedPinMsgIds.current.add(pendingPinMsgId)
      setPendingPinMsgId(null)
    }
    onSend(pin, '••••••')
  }

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

  const processImageFile = (file: File): Promise<ImageAttachment> =>
    new Promise((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = (e) => {
        const img = new Image()
        img.onload = () => {
          const MAX = 1024
          let { width, height } = img
          if (width > MAX || height > MAX) {
            if (width > height) { height = Math.round(height * MAX / width); width = MAX }
            else { width = Math.round(width * MAX / height); height = MAX }
          }
          const canvas = document.createElement('canvas')
          canvas.width = width; canvas.height = height
          canvas.getContext('2d')!.drawImage(img, 0, 0, width, height)
          const dataUrl = canvas.toDataURL('image/jpeg', 0.85)
          const base64 = dataUrl.split(',')[1]
          resolve({ base64, mimeType: 'image/jpeg', dataUrl })
        }
        img.onerror = reject
        img.src = e.target!.result as string
      }
      reader.onerror = reject
      reader.readAsDataURL(file)
    })

  const handleQrSelect = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    e.target.value = ''
    const reader = new FileReader()
    reader.onload = (ev) => {
      const img = new Image()
      img.onload = () => {
        const canvas = document.createElement('canvas')
        canvas.width = img.width
        canvas.height = img.height
        const ctx = canvas.getContext('2d')!
        ctx.drawImage(img, 0, 0)
        const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height)
        const code = jsQR(imageData.data, imageData.width, imageData.height)
        if (code?.data) {
          // Send EMV directly as a message so the agent decodes and presents it
          onSend(`Leia este QR Code Pix e me mostre os detalhes do pagamento: ${code.data}`)
        } else {
          setMicError('QR Code não encontrado na imagem. Tente uma foto mais nítida.')
        }
      }
      img.src = ev.target!.result as string
    }
    reader.readAsDataURL(file)
  }

  const handleImageSelect = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    try {
      const attachment = await processImageFile(file)
      setImageAttachment(attachment)
    } catch { /* ignore */ }
    e.target.value = ''
  }

  const startMic = async (e: React.MouseEvent | React.TouchEvent) => {
    e.preventDefault()
    setMicError(null)
    if (micState !== 'idle' || isLoading) return

    if (!navigator.mediaDevices?.getUserMedia) {
      setMicError('Microfone não disponível. Acesse via HTTPS.')
      return
    }
    let stream: MediaStream
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    } catch {
      setMicError('Permissão de microfone negada.')
      return
    }

    const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
      ? 'audio/webm;codecs=opus'
      : MediaRecorder.isTypeSupported('audio/webm') ? 'audio/webm' : 'audio/mp4'

    const recorder = new MediaRecorder(stream, { mimeType })
    chunksRef.current = []
    recorder.ondataavailable = (ev) => { if (ev.data.size > 0) chunksRef.current.push(ev.data) }
    recorder.onstop = async () => {
      stream.getTracks().forEach((t) => t.stop())
      setMicState('transcribing')
      try {
        const blob = new Blob(chunksRef.current, { type: mimeType })
        const text = await transcribeAudio(blob)
        if (text) onSend(text)
        else setMicError('Nenhum áudio detectado. Tente novamente.')
      } catch {
        setMicError('Falha na transcrição. Tente novamente.')
      }
      setMicState('idle')
    }
    recorderRef.current = recorder
    recorder.start()
    setMicState('recording')
  }

  const stopMic = (e: React.MouseEvent | React.TouchEvent) => {
    e.preventDefault()
    if (micState === 'recording') recorderRef.current?.stop()
  }

  const submit = () => {
    const text = input.trim()
    if (!text && !imageAttachment || isLoading) return
    setInput('')
    const img = imageAttachment
    setImageAttachment(null)
    onSend(text, undefined, img ?? undefined)
  }

  const onKey = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      submit()
    }
  }

  return (
    <div className="chat-panel">
      {showPinPad && (
        <PinPad
          onConfirm={handlePinConfirm}
          onCancel={() => setShowPinPad(false)}
        />
      )}
      <div className="message-list">
        {messages.length === 0 && (
          <div className="empty-chat">
            <p>Ola! Sou o assistente de pagamentos.</p>
            <p>Posso ajudar com PIX, boletos, consultas de saldo e transferencias.</p>
          </div>
        )}
        {messages.map((msg) => (
          <div key={msg.id} className={`bubble-wrap bubble-wrap--${msg.role}`}>
            <div className="bubble-col">
              <div className={`bubble bubble--${msg.role}`}>
                {msg.imageUrl && (
                  <img src={msg.imageUrl} alt="imagem enviada" className="bubble-image" />
                )}
                {msg.role === 'assistant' ? (
                  <Markdown components={markdownComponents}>{msg.content || (isLoading ? '…' : '')}</Markdown>
                ) : (
                  msg.content
                )}
              </div>
              {/* PIN button — shown inline below the message that requests the password */}
              {msg.role === 'assistant' && msg.id === pendingPinMsgId && (
                <button
                  className="pin-trigger-btn"
                  onClick={() => setShowPinPad(true)}
                >
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 1C8.676 1 6 3.676 6 7v1H4v15h16V8h-2V7c0-3.324-2.676-6-6-6zm0 2c2.276 0 4 1.724 4 4v1H8V7c0-2.276 1.724-4 4-4zm0 9a2 2 0 1 1 0 4 2 2 0 0 1 0-4z"/>
                  </svg>
                  Inserir senha de transação
                </button>
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

      {/* Image preview above input */}
      {imageAttachment && (
        <div className="image-preview-bar">
          <img src={imageAttachment.dataUrl} alt="preview" className="image-preview-thumb" />
          <button className="image-preview-remove" onClick={() => setImageAttachment(null)} title="Remover imagem">✕</button>
        </div>
      )}

      {micError && (
        <div className="mic-error-bar">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/></svg>
          {micError}
          <button onClick={() => setMicError(null)}>✕</button>
        </div>
      )}
      {micState === 'recording' && (
        <div className="mic-recording-bar">
          <span className="mic-recording-dot" />
          Gravando… solte para enviar
        </div>
      )}
      {micState === 'transcribing' && (
        <div className="mic-recording-bar mic-recording-bar--processing">
          <span className="mic-recording-dot mic-recording-dot--processing" />
          Processando áudio…
        </div>
      )}
      <div className="chat-input-bar">
        {/* Hidden file input — accepts gallery + camera */}
        <input
          ref={fileInputRef}
          type="file"
          accept="image/*"
          style={{ display: 'none' }}
          onChange={handleImageSelect}
        />
        {/* Hidden file input for QR Code reading */}
        <input
          ref={qrInputRef}
          type="file"
          accept="image/*"
          style={{ display: 'none' }}
          onChange={handleQrSelect}
        />
        <button
          className="attach-btn"
          onClick={() => fileInputRef.current?.click()}
          disabled={isLoading}
          title="Enviar imagem ou foto"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
            <path d="M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z"/>
          </svg>
        </button>
        <button
          className="attach-btn"
          onClick={() => qrInputRef.current?.click()}
          disabled={isLoading}
          title="Ler QR Code Pix"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
            <path d="M3 3h6v6H3V3zm2 2v2h2V5H5zm8-2h6v6h-6V3zm2 2v2h2V5h-2zM3 13h6v6H3v-6zm2 2v2h2v-2H5zm13-2h2v2h-2v-2zm-4 0h2v2h-2v-2zm4 4h2v2h-2v-2zm-4 0h2v4h-4v-2h2v-2zm-2-4h2v2h-2v-2z"/>
          </svg>
        </button>
        <button
          className={`mic-btn${micState === 'recording' ? ' mic-btn--recording' : ''}${micState === 'transcribing' ? ' mic-btn--transcribing' : ''}`}
          onMouseDown={startMic}
          onMouseUp={stopMic}
          onMouseLeave={micState === 'recording' ? stopMic : undefined}
          onTouchStart={startMic}
          onTouchEnd={stopMic}
          onTouchCancel={stopMic}
          disabled={isLoading || micState === 'transcribing'}
          title="Segure para gravar"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm-1-9c0-.55.45-1 1-1s1 .45 1 1v6c0 .55-.45 1-1 1s-1-.45-1-1V5zm6 6c0 2.76-2.24 5-5 5s-5-2.24-5-5H5c0 3.53 2.61 6.43 6 6.92V21h2v-3.08c3.39-.49 6-3.39 6-6.92h-2z"/>
          </svg>
        </button>
        <textarea
          className="chat-input"
          rows={1}
          placeholder={imageAttachment ? 'Adicione um comentário (opcional)…' : 'Digite sua mensagem… (Enter para enviar)'}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={onKey}
          disabled={isLoading}
        />
        <button className="send-btn" onClick={submit} disabled={isLoading || (!input.trim() && !imageAttachment)}>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
            <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z" />
          </svg>
        </button>
      </div>
    </div>
  )
}
