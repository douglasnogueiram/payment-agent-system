import { useEffect, useRef, useState } from 'react'
import {
  deleteDocument,
  getDocumentVersions,
  listDocuments,
  uploadDocument,
} from '../api/ragApi'
import type { DocumentRecord, DocumentVersion, LoadResult } from '../api/ragApi'
import './RagPanel.css'

type UploadState = 'idle' | 'loading' | 'success' | 'error'

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

function actionLabel(action: string): string {
  switch (action) {
    case 'created': case 'CREATED': return 'Criado'
    case 'updated': case 'UPDATED': return 'Atualizado'
    case 'unchanged': return 'Sem alteracao'
    case 'DELETED': return 'Excluido'
    default: return action
  }
}

function contentTypeLabel(ct: string): string {
  if (ct?.includes('pdf')) return 'PDF'
  if (ct?.includes('plain')) return 'TXT'
  return ct ?? '—'
}

export default function RagPanel() {
  // ── Upload ────────────────────────────────────────────────────────────────
  const [uploadName, setUploadName] = useState('')
  const [uploadFile, setUploadFile] = useState<File | null>(null)
  const [uploadState, setUploadState] = useState<UploadState>('idle')
  const [uploadResult, setUploadResult] = useState<LoadResult | null>(null)
  const [uploadError, setUploadError] = useState('')
  const fileInputRef = useRef<HTMLInputElement>(null)

  const NAME_REGEX = /^[a-zA-Z0-9_-]+$/

  function isValidName(name: string): boolean {
    return NAME_REGEX.test(name.trim())
  }

  async function handleUpload(e: React.FormEvent) {
    e.preventDefault()
    if (!uploadName.trim() || !uploadFile) return

    if (!isValidName(uploadName)) {
      setUploadState('error')
      setUploadError('Nome invalido. Use apenas letras, numeros, hifen (-) e underscore (_). Sem espacos ou acentos.')
      return
    }

    setUploadState('loading')
    setUploadResult(null)
    setUploadError('')

    try {
      const result = await uploadDocument(uploadName.trim(), uploadFile)
      setUploadResult(result)
      setUploadState('success')
      loadDocumentList()
    } catch (err) {
      setUploadError(err instanceof Error ? err.message : 'Erro desconhecido')
      setUploadState('error')
    }
  }

  function resetUpload() {
    setUploadName('')
    setUploadFile(null)
    setUploadState('idle')
    setUploadResult(null)
    setUploadError('')
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  // ── Document list ─────────────────────────────────────────────────────────
  const [docs, setDocs] = useState<DocumentRecord[]>([])
  const [listLoading, setListLoading] = useState(false)
  const [expandedVersions, setExpandedVersions] = useState<Record<string, DocumentVersion[]>>({})
  const [versionsLoading, setVersionsLoading] = useState<Record<string, boolean>>({})
  const [deletingName, setDeletingName] = useState<string | null>(null)

  async function loadDocumentList() {
    setListLoading(true)
    try {
      const list = await listDocuments()
      setDocs(list)
    } catch {
      // silently ignore
    } finally {
      setListLoading(false)
    }
  }

  useEffect(() => { loadDocumentList() }, [])

  async function toggleVersions(name: string) {
    if (expandedVersions[name]) {
      setExpandedVersions(prev => { const n = { ...prev }; delete n[name]; return n })
      return
    }
    setVersionsLoading(prev => ({ ...prev, [name]: true }))
    try {
      const versions = await getDocumentVersions(name)
      setExpandedVersions(prev => ({ ...prev, [name]: versions }))
    } finally {
      setVersionsLoading(prev => { const n = { ...prev }; delete n[name]; return n })
    }
  }

  async function handleDelete(name: string) {
    if (!confirm(`Excluir o documento "${name}"? Esta acao nao pode ser desfeita.`)) return
    setDeletingName(name)
    try {
      await deleteDocument(name)
      setDocs(prev => prev.filter(d => d.name !== name))
      setExpandedVersions(prev => { const n = { ...prev }; delete n[name]; return n })
    } finally {
      setDeletingName(null)
    }
  }

  return (
    <div className="rag-panel">

      {/* ── Upload section ───────────────────────────────────────────────── */}
      <section className="rag-card">
        <h3 className="rag-card__title">Carregar documento</h3>
        <p className="rag-card__desc">
          Envie um arquivo <code>.txt</code> ou <code>.pdf</code> para indexar na base de conhecimento.
          Se o arquivo for identico a versao atual, o reprocessamento e ignorado automaticamente.
        </p>

        <form className="rag-form" onSubmit={handleUpload}>
          <div className="rag-form__row">
            <label htmlFor="upload-name" className="rag-label">Nome do documento</label>
            <input
              id="upload-name"
              type="text"
              className={`rag-input${uploadName && !isValidName(uploadName) ? ' rag-input--error' : ''}`}
              placeholder="ex: politica-de-pagamentos"
              value={uploadName}
              onChange={e => setUploadName(e.target.value)}
              required
              disabled={uploadState === 'loading'}
            />
            {uploadName && !isValidName(uploadName) && (
              <span className="rag-field-error">
                Use apenas letras, numeros, hifen (-) e underscore (_). Sem espacos ou acentos.
              </span>
            )}
          </div>

          <div className="rag-form__row">
            <label htmlFor="upload-file" className="rag-label">Arquivo (.txt ou .pdf)</label>
            <input
              id="upload-file"
              ref={fileInputRef}
              type="file"
              accept=".txt,.pdf,text/plain,application/pdf"
              className="rag-file-input"
              onChange={e => setUploadFile(e.target.files?.[0] ?? null)}
              required
              disabled={uploadState === 'loading'}
            />
          </div>

          <div className="rag-form__actions">
            <button
              type="submit"
              className="rag-btn rag-btn--primary"
              disabled={uploadState === 'loading' || !uploadName.trim() || !uploadFile || !isValidName(uploadName)}
            >
              {uploadState === 'loading' ? 'Enviando…' : 'Carregar'}
            </button>
            {uploadState !== 'idle' && (
              <button type="button" className="rag-btn rag-btn--ghost" onClick={resetUpload}>
                Limpar
              </button>
            )}
          </div>
        </form>

        {uploadState === 'success' && uploadResult && (
          <div className={`rag-feedback rag-feedback--${uploadResult.action === 'unchanged' ? 'warning' : 'success'}`}>
            {uploadResult.action === 'unchanged' ? (
              <>Arquivo identico a versao atual — nenhuma alteracao necessaria.</>
            ) : (
              <>
                Documento <strong>{uploadResult.name}</strong>{' '}
                {uploadResult.action === 'updated' ? 'atualizado' : 'criado'} — v{uploadResult.version},{' '}
                <strong>{uploadResult.chunks}</strong> chunks · {formatBytes(uploadResult.sizeBytes)}
              </>
            )}
          </div>
        )}
        {uploadState === 'error' && (
          <div className="rag-feedback rag-feedback--error">Erro: {uploadError}</div>
        )}
      </section>

      {/* ── Document list ────────────────────────────────────────────────── */}
      <section className="rag-card">
        <div className="rag-card__header">
          <h3 className="rag-card__title">Documentos indexados</h3>
          <button
            className="rag-btn rag-btn--ghost rag-btn--sm"
            onClick={loadDocumentList}
            disabled={listLoading}
          >
            {listLoading ? 'Atualizando…' : 'Atualizar'}
          </button>
        </div>

        {docs.length === 0 && !listLoading && (
          <p className="rag-empty">Nenhum documento indexado ainda.</p>
        )}

        {docs.length > 0 && (
          <div className="rag-table-wrap">
            <table className="rag-table">
              <thead>
                <tr>
                  <th>Nome</th>
                  <th>Tipo</th>
                  <th>Tamanho</th>
                  <th>Versao</th>
                  <th>Chunks</th>
                  <th>Atualizado em</th>
                  <th>Hash (SHA-256)</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {docs.map(doc => (
                  <>
                    <tr key={doc.name} className="rag-table__row">
                      <td className="rag-table__name">{doc.name}</td>
                      <td><span className="rag-badge">{contentTypeLabel(doc.contentType)}</span></td>
                      <td>{formatBytes(doc.sizeBytes)}</td>
                      <td>v{doc.version}</td>
                      <td>{doc.chunks}</td>
                      <td className="rag-table__date">{formatDate(doc.uploadedAt)}</td>
                      <td className="rag-table__hash" title={doc.sha256Hash}>
                        {doc.sha256Hash.slice(0, 8)}…
                      </td>
                      <td className="rag-table__actions">
                        <button
                          className="rag-btn rag-btn--ghost rag-btn--sm"
                          onClick={() => toggleVersions(doc.name)}
                          disabled={!!versionsLoading[doc.name]}
                        >
                          {versionsLoading[doc.name] ? '…' : expandedVersions[doc.name] ? 'Ocultar' : 'Historico'}
                        </button>
                        <button
                          className="rag-btn rag-btn--danger rag-btn--sm"
                          onClick={() => handleDelete(doc.name)}
                          disabled={deletingName === doc.name}
                        >
                          {deletingName === doc.name ? '…' : 'Excluir'}
                        </button>
                      </td>
                    </tr>

                    {expandedVersions[doc.name] && (
                      <tr key={`${doc.name}-versions`} className="rag-table__versions-row">
                        <td colSpan={8}>
                          <div className="rag-versions">
                            <strong>Historico de versoes — {doc.name}</strong>
                            <table className="rag-versions-table">
                              <thead>
                                <tr>
                                  <th>Versao</th>
                                  <th>Acao</th>
                                  <th>Arquivo</th>
                                  <th>Chunks</th>
                                  <th>Hash</th>
                                  <th>Data</th>
                                </tr>
                              </thead>
                              <tbody>
                                {expandedVersions[doc.name].map(v => (
                                  <tr key={v.id}>
                                    <td>v{v.version}</td>
                                    <td>
                                      <span className={`rag-badge rag-badge--${v.action.toLowerCase()}`}>
                                        {actionLabel(v.action)}
                                      </span>
                                    </td>
                                    <td>{v.originalFilename ?? '—'}</td>
                                    <td>{v.chunks}</td>
                                    <td className="rag-table__hash" title={v.sha256Hash}>
                                      {v.sha256Hash?.slice(0, 8)}…
                                    </td>
                                    <td>{formatDate(v.uploadedAt)}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        </td>
                      </tr>
                    )}
                  </>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

    </div>
  )
}
