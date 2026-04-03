import { useState, useRef, useEffect } from 'react'
import { VoiceConfig, VoiceOption, AudioFormat, DEFAULT_VOICE_CONFIG } from '../types'
import { synthesizeSpeech } from '../api/speechApi'
import { saveVoiceConfig } from '../api/voiceConfigApi'
import './VoiceConfigPanel.css'

interface Props {
  config: VoiceConfig
  onChange: (config: VoiceConfig) => void
}

const VOICES: { id: VoiceOption; label: string; description: string }[] = [
  { id: 'alloy',   label: 'Alloy',   description: 'Neutra e equilibrada' },
  { id: 'echo',    label: 'Echo',    description: 'Masculina, suave' },
  { id: 'fable',   label: 'Fable',   description: 'Expressiva, sotaque britanico' },
  { id: 'onyx',    label: 'Onyx',    description: 'Masculina, grave' },
  { id: 'nova',    label: 'Nova',    description: 'Feminina, amigavel' },
  { id: 'shimmer', label: 'Shimmer', description: 'Feminina, suave' },
]

const FORMATS: { id: AudioFormat; label: string; description: string }[] = [
  { id: 'mp3',  label: 'MP3',  description: 'Compativel com todos os browsers' },
  { id: 'opus', label: 'Opus', description: 'Menor tamanho, ideal para streaming' },
  { id: 'aac',  label: 'AAC',  description: 'Boa compressao, ideal para iOS/macOS' },
  { id: 'flac', label: 'FLAC', description: 'Sem perda, maior fidelidade' },
  { id: 'wav',  label: 'WAV',  description: 'Sem compressao' },
]

const PREVIEW_TEXT = 'Ola! Sou o assistente de pagamentos. Como posso ajudar com sua transacao hoje?'

export default function VoiceConfigPanel({ config, onChange }: Props) {
  const [previewingVoice, setPreviewingVoice] = useState<VoiceOption | null>(null)
  const [isTesting, setIsTesting] = useState(false)
  const [saveState, setSaveState] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle')
  const [savedConfig, setSavedConfig] = useState<VoiceConfig>(config)
  const audioRef = useRef<HTMLAudioElement | null>(null)

  const isDirty = JSON.stringify(config) !== JSON.stringify(savedConfig)

  useEffect(() => {
    setSavedConfig(config)
  }, [])

  const handleSave = async () => {
    setSaveState('saving')
    try {
      const saved = await saveVoiceConfig(config)
      setSavedConfig(saved)
      onChange(saved)
      setSaveState('saved')
      setTimeout(() => setSaveState('idle'), 2000)
    } catch {
      setSaveState('error')
      setTimeout(() => setSaveState('idle'), 3000)
    }
  }

  const handleReset = () => {
    stopAudio()
    onChange(DEFAULT_VOICE_CONFIG)
  }

  const set = <K extends keyof VoiceConfig>(key: K, value: VoiceConfig[K]) => {
    onChange({ ...config, [key]: value })
  }

  const stopAudio = () => {
    if (audioRef.current) {
      audioRef.current.pause()
      audioRef.current = null
    }
    setPreviewingVoice(null)
    setIsTesting(false)
  }

  const previewVoice = async (voice: VoiceOption) => {
    stopAudio()
    setPreviewingVoice(voice)
    try {
      const blob = await synthesizeSpeech(PREVIEW_TEXT, { ...config, voice })
      const url = URL.createObjectURL(blob)
      const audio = new Audio(url)
      audioRef.current = audio
      audio.onended = () => { setPreviewingVoice(null); URL.revokeObjectURL(url) }
      audio.play()
    } catch {
      setPreviewingVoice(null)
    }
  }

  const testConfig = async () => {
    stopAudio()
    setIsTesting(true)
    try {
      const blob = await synthesizeSpeech(PREVIEW_TEXT, config)
      const url = URL.createObjectURL(blob)
      const audio = new Audio(url)
      audioRef.current = audio
      audio.onended = () => { setIsTesting(false); URL.revokeObjectURL(url) }
      audio.play()
    } catch {
      setIsTesting(false)
    }
  }

  return (
    <div className="voice-config">
      <div className="voice-config__header">
        <div>
          <h2 className="voice-config__title">Configuracao de Voz</h2>
          <p className="voice-config__subtitle">Modelo: <strong>gpt-4o-mini-tts-2025-03-20</strong></p>
        </div>
        <div className="voice-config__header-actions">
          <button className="vc-btn vc-btn--ghost" onClick={handleReset}>Restaurar padroes</button>
          <button
            className={`vc-btn vc-btn--primary${isTesting ? ' vc-btn--loading' : ''}`}
            onClick={isTesting ? stopAudio : testConfig}
          >
            {isTesting ? (
              <><span className="vc-spinner" /> Parar</>
            ) : (
              <><svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor"><path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02z"/></svg> Testar configuracao</>
            )}
          </button>
          <button
            className={`vc-btn${saveState === 'saving' ? ' vc-btn--loading' : saveState === 'saved' ? ' vc-btn--saved' : saveState === 'error' ? ' vc-btn--error' : isDirty ? ' vc-btn--primary' : ' vc-btn--ghost'}`}
            onClick={handleSave}
            disabled={saveState === 'saving' || saveState === 'saved' || !isDirty}
          >
            {saveState === 'saving' ? <><span className="vc-spinner" /> Salvando</> :
             saveState === 'saved' ? 'Salvo' :
             saveState === 'error' ? 'Erro ao salvar' :
             'Salvar'}
          </button>
        </div>
      </div>

      {/* Voz */}
      <section className="vc-section">
        <h3 className="vc-section__title">Voz</h3>
        <div className="vc-voices">
          {VOICES.map((v) => (
            <div
              key={v.id}
              className={`vc-voice-card${config.voice === v.id ? ' vc-voice-card--selected' : ''}`}
              onClick={() => set('voice', v.id)}
            >
              <div className="vc-voice-card__info">
                <span className="vc-voice-card__name">{v.label}</span>
                <span className="vc-voice-card__desc">{v.description}</span>
              </div>
              <button
                className={`vc-preview-btn${previewingVoice === v.id ? ' vc-preview-btn--active' : ''}`}
                onClick={(e) => { e.stopPropagation(); previewingVoice === v.id ? stopAudio() : previewVoice(v.id) }}
                title="Ouvir amostra"
              >
                {previewingVoice === v.id ? (
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M6 6h4v12H6zm8 0h4v12h-4z"/></svg>
                ) : (
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
                )}
              </button>
            </div>
          ))}
        </div>
      </section>

      {/* Velocidade */}
      <section className="vc-section">
        <h3 className="vc-section__title">
          Velocidade
          <span className="vc-section__value">{config.speed.toFixed(2)}x</span>
        </h3>
        <div className="vc-slider-row">
          <span className="vc-slider-label">0.25x</span>
          <input
            type="range"
            className="vc-slider"
            min={0.25}
            max={4.0}
            step={0.05}
            value={config.speed}
            onChange={(e) => set('speed', parseFloat(e.target.value))}
          />
          <span className="vc-slider-label">4.00x</span>
        </div>
        <div className="vc-speed-presets">
          {[0.75, 1.0, 1.25, 1.5, 2.0].map((s) => (
            <button
              key={s}
              className={`vc-preset${config.speed === s ? ' vc-preset--active' : ''}`}
              onClick={() => set('speed', s)}
            >
              {s}x
            </button>
          ))}
        </div>
      </section>

      {/* Instrucoes */}
      <section className="vc-section">
        <h3 className="vc-section__title">Instrucoes de estilo
          <span className="vc-section__badge">gpt-4o-mini-tts-2025-03-20</span>
        </h3>
        <p className="vc-section__hint">
          Descreva o tom, personalidade e estilo de fala. Deixe em branco para comportamento padrao.
        </p>
        <textarea
          className="vc-instructions"
          rows={4}
          placeholder="Ex: Fale de forma profissional e tranquilizadora, como um assistente bancario experiente. Use um tom calmo e preciso."
          value={config.instructions}
          onChange={(e) => set('instructions', e.target.value)}
        />
        <div className="vc-instructions-presets">
          <span className="vc-instructions-presets__label">Sugestoes:</span>
          {[
            { label: 'Formal', text: 'Fale de forma formal e profissional, como um gerente bancario de primeira classe.' },
            { label: 'Amigavel', text: 'Fale de forma amigavel e acessivel, com energia positiva e tom acolhedor.' },
            { label: 'Calmo', text: 'Fale de forma calma, pausada e tranquilizadora, transmitindo seguranca e confianca.' },
            { label: 'Objetivo', text: 'Fale de forma direta e objetiva, focando nas informacoes mais importantes sem rodeios.' },
          ].map((p) => (
            <button key={p.label} className="vc-preset" onClick={() => set('instructions', p.text)}>
              {p.label}
            </button>
          ))}
        </div>
      </section>

      {/* Formato */}
      <section className="vc-section">
        <h3 className="vc-section__title">Formato de audio</h3>
        <div className="vc-formats">
          {FORMATS.map((f) => (
            <label key={f.id} className={`vc-format-card${config.responseFormat === f.id ? ' vc-format-card--selected' : ''}`}>
              <input
                type="radio"
                name="format"
                value={f.id}
                checked={config.responseFormat === f.id}
                onChange={() => set('responseFormat', f.id)}
              />
              <span className="vc-format-card__name">{f.label}</span>
              <span className="vc-format-card__desc">{f.description}</span>
            </label>
          ))}
        </div>
      </section>
    </div>
  )
}
