import { authHeaders } from '../auth/keycloak'

// ─── Chat (SSE via fetch) ────────────────────────────────────────────────────

export async function sendChatMessage(
  chatId: string,
  message: string,
  onToken: (token: string) => void,
  onDone: () => Promise<void>,
  onError: (msg: string) => void,
  image?: { base64: string; mimeType: string },
): Promise<void> {
  let response: Response
  try {
    response = await fetch('/api/chat/message', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify({
        chatId,
        message,
        imageBase64: image?.base64,
        imageMimeType: image?.mimeType,
      }),
    })
  } catch {
    onError('Falha na conexão com o servidor.')
    return
  }

  if (!response.ok || !response.body) {
    onError(`Erro HTTP ${response.status}`)
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''

    for (const line of lines) {
      if (!line.startsWith('data:')) continue
      const raw = line.slice(5).trim()
      if (!raw) continue
      try {
        const event = JSON.parse(raw)
        if (event.type === 'token') {
          onToken(event.content as string)
        } else if (event.type === 'done') {
          await onDone()
        } else if (event.type === 'error') {
          onError(event.message as string)
        }
      } catch {
        // malformed JSON line — skip
      }
    }
  }
}
