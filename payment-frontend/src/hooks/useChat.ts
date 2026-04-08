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
    async (
      text: string,
      displayText?: string,
      image?: { base64: string; mimeType: string; dataUrl: string },
    ) => {
      if ((!text.trim() && !image) || isLoading) return

      const userMsg: Message = {
        id: uuid(),
        role: 'user',
        content: displayText ?? text,
        imageUrl: image?.dataUrl,
      }
      const assistantId = uuid()
      const assistantMsg: Message = { id: assistantId, role: 'assistant', content: '' }

      setMessages((prev) => [...prev, userMsg, assistantMsg])
      setIsLoading(true)

      await sendChatMessage(
        chatId,
        text,
        (token) => {
          setMessages((prev) =>
            prev.map((m) => (m.id === assistantId ? { ...m, content: m.content + token } : m)),
          )
        },
        async () => { setIsLoading(false) },
        (msg) => {
          setMessages((prev) =>
            prev.map((m) => (m.id === assistantId ? { ...m, content: `Erro: ${msg}` } : m)),
          )
          setIsLoading(false)
        },
        image ? { base64: image.base64, mimeType: image.mimeType } : undefined,
      )
    },
    [chatId, isLoading],
  )

  const injectMessage = useCallback((content: string) => {
    setMessages(prev => [...prev, { id: uuid(), role: 'assistant', content }])
  }, [])

  return { messages, isLoading, sendMessage, injectMessage }
}
