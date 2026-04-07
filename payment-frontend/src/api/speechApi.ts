import { VoiceConfig, DEFAULT_VOICE_CONFIG } from '../types'
import { authHeaders } from '../auth/keycloak'

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

/** Converts any audio Blob to WAV (PCM 16-bit mono 16kHz) using Web Audio API. */
async function toWav(blob: Blob): Promise<Blob> {
  const arrayBuf = await blob.arrayBuffer()
  const ctx = new AudioContext({ sampleRate: 16000 })
  const decoded = await ctx.decodeAudioData(arrayBuf)
  await ctx.close()

  // Mix down to mono
  const numFrames = decoded.length
  const mono = new Float32Array(numFrames)
  for (let c = 0; c < decoded.numberOfChannels; c++) {
    const ch = decoded.getChannelData(c)
    for (let i = 0; i < numFrames; i++) mono[i] += ch[i]
  }
  if (decoded.numberOfChannels > 1) {
    for (let i = 0; i < numFrames; i++) mono[i] /= decoded.numberOfChannels
  }

  // PCM 16-bit
  const pcm = new Int16Array(numFrames)
  for (let i = 0; i < numFrames; i++) {
    const s = Math.max(-1, Math.min(1, mono[i]))
    pcm[i] = s < 0 ? s * 0x8000 : s * 0x7fff
  }

  // WAV header
  const wavBuf = new ArrayBuffer(44 + pcm.byteLength)
  const view = new DataView(wavBuf)
  const write = (off: number, val: string) => { for (let i = 0; i < val.length; i++) view.setUint8(off + i, val.charCodeAt(i)) }
  write(0, 'RIFF'); view.setUint32(4, 36 + pcm.byteLength, true)
  write(8, 'WAVE'); write(12, 'fmt ')
  view.setUint32(16, 16, true); view.setUint16(20, 1, true); view.setUint16(22, 1, true)
  view.setUint32(24, 16000, true); view.setUint32(28, 32000, true)
  view.setUint16(32, 2, true); view.setUint16(34, 16, true)
  write(36, 'data'); view.setUint32(40, pcm.byteLength, true)
  new Int16Array(wavBuf, 44).set(pcm)
  return new Blob([wavBuf], { type: 'audio/wav' })
}

export async function transcribeAudio(blob: Blob): Promise<string> {
  const wav = await toWav(blob)
  const form = new FormData()
  form.append('audio', wav, 'audio.wav')
  const r = await fetch('/api/stt/transcribe', {
    method: 'POST',
    headers: { ...authHeaders() },
    body: form,
  })
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  const { text } = await r.json()
  return text as string
}

export async function synthesizeSpeech(text: string, config: VoiceConfig = DEFAULT_VOICE_CONFIG): Promise<Blob> {
  const r = await fetch('/api/tts', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
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
