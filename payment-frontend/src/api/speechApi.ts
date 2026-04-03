import { VoiceConfig, DEFAULT_VOICE_CONFIG } from '../types'

function stripMarkdown(text: string): string {
  return text
    .replace(/#{1,6}\s+/g, '')
    .replace(/\*\*(.+?)\*\*/g, '$1')
    .replace(/\*(.+?)\*/g, '$1')
    .replace(/`(.+?)`/g, '$1')
    .replace(/\[(.+?)\]\(.+?\)/g, '$1')
    .replace(/^\s*[-*+]\s+/gm, '')
    .trim()
}

export async function synthesizeSpeech(text: string, config: VoiceConfig = DEFAULT_VOICE_CONFIG): Promise<Blob> {
  const r = await fetch('/api/tts/speech', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      text: stripMarkdown(text),
      voice: config.voice,
      speed: config.speed,
      responseFormat: config.responseFormat,
      instructions: config.instructions,
    }),
  })
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return r.blob()
}
