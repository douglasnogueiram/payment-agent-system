export interface AgentPrompt {
  id?: number
  content: string
  description?: string
  active?: boolean
  createdAt?: string
}

export async function fetchActivePrompt(): Promise<AgentPrompt> {
  const r = await fetch('/api/agent-prompt/active')
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return r.json()
}

export async function fetchPromptHistory(): Promise<AgentPrompt[]> {
  const r = await fetch('/api/agent-prompt/history')
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return r.json()
}

export async function savePrompt(content: string, description: string): Promise<AgentPrompt> {
  const r = await fetch('/api/agent-prompt', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content, description }),
  })
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return r.json()
}

export async function activatePromptVersion(id: number): Promise<AgentPrompt> {
  const r = await fetch(`/api/agent-prompt/${id}/activate`, { method: 'POST' })
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return r.json()
}
