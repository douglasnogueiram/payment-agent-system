import { VoiceConfig } from '../types'
import { authHeaders } from '../auth/keycloak'

export async function fetchVoiceConfig(): Promise<VoiceConfig> {
  const r = await fetch('/api/voice-config', { headers: authHeaders() })
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  const data = await r.json()
  return {
    voice: data.voice,
    speed: data.speed,
    responseFormat: data.responseFormat,
    instructions: data.instructions ?? '',
  }
}

export async function saveVoiceConfig(config: VoiceConfig): Promise<VoiceConfig> {
  const r = await fetch('/api/voice-config', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(config),
  })
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  const data = await r.json()
  return {
    voice: data.voice,
    speed: data.speed,
    responseFormat: data.responseFormat,
    instructions: data.instructions ?? '',
  }
}
