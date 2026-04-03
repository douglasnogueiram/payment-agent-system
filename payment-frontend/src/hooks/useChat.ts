import { useState, useCallback, useRef } from 'react'
import { Message } from '../types'
import { sendChatMessage } from '../api/chatApi'

function uuid(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID()
  const b = new Uint8Array(16)
  crypto.getRandomValues(b)
  b[6] = (b[6] & 0x0f) | 0x40
  b[8] = (b[8] & 0x3f) | 0x80
  return [...b].map((v, i) =>
    ([4, 6, 8, 10].includes(i) ? '-' : '') + v.toString(16).padStart(2, '0'),
  ).join('')
}

export function useChat() {
  const chatId = useRef<string>(uuid()).current
  const [messages, setMessages] = useState<Message[]>([])
  const [isLoading, setIsLoading] = useState(false)

  const sendMessage = useCallback(
    async (text: string) => {
      if (!text.trim() || isLoading) return

      const userMsg: Message = { id: uuid(), role: 'user', content: text }
      const assistantId = uuid()
      const assistantMsg: Message = { id: assistantId, role: 'assistant', content: '' }

      setMessages((prev) => [...prev, userMsg, assistantMsg])
      setIsLoading(true)

      await sendChatMessage(
        chatId,
        text,
        // onToken
        (token) => {
          setMessages((prev) =>
            prev.map((m) => (m.id === assistantId ? { ...m, content: m.content + token } : m)),
          )
        },
        // onDone
        async () => {
          setIsLoading(false)
        },
        // onError
        (msg) => {
          setMessages((prev) =>
            prev.map((m) => (m.id === assistantId ? { ...m, content: `Erro: ${msg}` } : m)),
          )
          setIsLoading(false)
        },
      )
    },
    [chatId, isLoading],
  )

  return { messages, isLoading, sendMessage }
}
