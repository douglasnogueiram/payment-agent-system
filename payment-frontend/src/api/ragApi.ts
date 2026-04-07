import { authHeaders } from '../auth/keycloak'

// ─── RAG Loader API (/rag/* → payment-rag:8090/api/*) ────────────────────────

export interface LoadResult {
  name: string
  originalFilename: string
  contentType: string
  sizeBytes: number
  sha256Hash: string
  version: number
  chunks: number
  action: 'created' | 'updated' | 'unchanged'
}

export interface DocumentRecord {
  id: string
  name: string
  originalFilename: string
  contentType: string
  sizeBytes: number
  sha256Hash: string
  version: number
  chunks: number
  status: string
  uploadedAt: string
}

export interface DocumentVersion {
  id: string
  name: string
  originalFilename: string
  sha256Hash: string
  version: number
  chunks: number
  action: 'CREATED' | 'UPDATED' | 'DELETED'
  uploadedAt: string
}

export async function uploadDocument(name: string, file: File): Promise<LoadResult> {
  const form = new FormData()
  form.append('file', file)

  const r = await fetch(`/rag/documents/upload?name=${encodeURIComponent(name)}`, {
    method: 'POST',
    headers: authHeaders(),
    body: form,
  })

  if (!r.ok) {
    const text = await r.text()
    throw new Error(text || `HTTP ${r.status}`)
  }

  return r.json()
}

export async function listDocuments(): Promise<DocumentRecord[]> {
  const r = await fetch('/rag/documents', { headers: authHeaders() })
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return r.json()
}

export async function getDocumentStatus(name: string): Promise<DocumentRecord | null> {
  const r = await fetch(`/rag/documents/${encodeURIComponent(name)}`, { headers: authHeaders() })
  if (r.status === 404) return null
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return r.json()
}

export async function getDocumentVersions(name: string): Promise<DocumentVersion[]> {
  const r = await fetch(`/rag/documents/${encodeURIComponent(name)}/versions`, {
    headers: authHeaders(),
  })
  if (r.status === 404) return []
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return r.json()
}

export async function deleteDocument(name: string): Promise<number> {
  const r = await fetch(`/rag/documents/${encodeURIComponent(name)}`, {
    method: 'DELETE',
    headers: authHeaders(),
  })
  if (r.status === 404) return 0
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  const data = await r.json()
  return data.deletedChunks as number
}
