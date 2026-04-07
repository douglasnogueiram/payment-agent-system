export type VoiceOption = 'alloy' | 'echo' | 'fable' | 'onyx' | 'nova' | 'shimmer'
export type AudioFormat = 'mp3' | 'opus' | 'aac' | 'flac' | 'wav'

export interface VoiceConfig {
  voice: VoiceOption
  speed: number
  responseFormat: AudioFormat
  instructions: string
}

export const DEFAULT_VOICE_CONFIG: VoiceConfig = {
  voice: 'nova',
  speed: 1.0,
  responseFormat: 'mp3',
  instructions: '',
}

export interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  imageUrl?: string   // data URL shown in the bubble when user sends an image
}

export type TransactionType = 'PIX' | 'BOLETO' | 'SALDO' | 'TRANSFERENCIA' | 'PAGAMENTO'
export type TransactionStatus = 'COMPLETED' | 'PENDING' | 'FAILED' | 'CANCELLED'

export interface Transaction {
  id: string
  accountNumber: string
  type: TransactionType
  amount: number
  description: string
  status: TransactionStatus
  createdAt: string
  completedAt?: string
  pixKey?: string
  boletoBarcode?: string
  recipientName?: string
}

export interface Account {
  accountNumber: string
  ownerName: string
  balance: number
  branch: string
  updatedAt: string
}
