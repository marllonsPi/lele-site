import { api } from './http'

export type TipoItem = 'CLIENTE' | 'PROCESSO' | 'CONTRATO' | 'PARCELA' | 'DESPESA' | 'DOCUMENTO'
export type AcaoItem = 'CRIAR' | 'CONFLITO' | 'SEM_MUDANCA' | 'IGNORAR'
export type DecisaoItem = 'APLICAR' | 'MANTER'
export type ResultadoItem = 'APLICADO' | 'IGNORADO' | 'ERRO'

export const ROTULO_TIPO_ITEM: Record<TipoItem, string> = {
  CLIENTE: 'Clientes',
  PROCESSO: 'Processos',
  CONTRATO: 'Contratos',
  PARCELA: 'Parcelas',
  DESPESA: 'Despesas',
  DOCUMENTO: 'Documentos',
}

export const ROTULO_ACAO_ITEM: Record<AcaoItem, string> = {
  CRIAR: 'Novo',
  CONFLITO: 'Conflito',
  SEM_MUDANCA: 'Sem mudança',
  IGNORAR: 'Ignorado',
}

export interface ItemPrevia {
  id: number
  ordem: number
  tipo: TipoItem
  chave: string
  rotulo: string
  acao: AcaoItem
  divergencias: string[]
  importado: Record<string, unknown>
  existente: Record<string, unknown> | null
  decisao: DecisaoItem | null
  ajustes: Record<string, unknown>
  resultado: ResultadoItem | null
  motivo: string | null
}

export interface RelatorioImportacao {
  totais: Record<string, Record<string, number>>
  ignorados: { item: string; tipo: string; motivo: string }[]
  erros: { item: string; tipo: string; motivo: string }[]
}

export interface Importacao {
  id: number
  origem: 'PLANILHA' | 'DRIVE'
  modo: 'MANUAL' | 'DIARIA'
  arquivoNome: string | null
  status: 'PREVIA' | 'CONCLUIDA' | 'CANCELADA'
  criadoEm: string
  confirmadoEm: string | null
  confirmadoPor: number | null
  contagens: Record<string, Record<string, number>>
  comDivergencia: number
  pendencias: string[]
  relatorio: RelatorioImportacao | null
  itens: ItemPrevia[]
}

export interface StatusDrive {
  pastaClientesConfigurada: boolean
  planilhaConfigurada: boolean
  contaConectadaAoDrive: boolean
}

export interface StatusGoogle {
  disponivel: boolean
  conectado: boolean
  estado: string | null
  email: string | null
  drive: boolean
}

export const listarImportacoes = () => api<Importacao[]>('GET', '/api/importacoes')
export const detalheImportacao = (id: number) => api<Importacao>('GET', `/api/importacoes/${id}`)
export const enviarPlanilha = (arquivo: File, responsavelId?: number) => {
  const f = new FormData()
  f.append('arquivo', arquivo)
  if (responsavelId) {
    f.append('responsavelId', String(responsavelId))
  }
  return api<Importacao>('POST', '/api/importacoes/planilha', { multipart: f })
}
export const previaPlanilhaDoDrive = () => api<Importacao>('POST', '/api/importacoes/planilha-drive')
export const previaDoDrive = () => api<Importacao>('POST', '/api/importacoes/drive')
export const statusDrive = () => api<StatusDrive>('GET', '/api/importacoes/drive/status')
export const decidirItem = (id: number, itemId: number, corpo: { decisao?: DecisaoItem | null; ajustes?: Record<string, unknown> }) =>
  api<Importacao>('PUT', `/api/importacoes/${id}/itens/${itemId}`, { json: corpo })
export const decidirLote = (id: number, acao: AcaoItem, tipo: TipoItem | null, decisao: DecisaoItem) =>
  api<Importacao>('POST', `/api/importacoes/${id}/decidir-lote`, { json: { acao, tipo, decisao } })
export const confirmarImportacao = (id: number) => api<Importacao>('POST', `/api/importacoes/${id}/confirmar`)
export const cancelarImportacao = (id: number) => api<Importacao>('POST', `/api/importacoes/${id}/cancelar`)

export const statusGoogle = () => api<StatusGoogle>('GET', '/api/google/status')
export const conectarGoogle = (drive: boolean) => api<{ url: string }>('POST', `/api/google/conectar?drive=${drive}`)

export interface DocumentoCliente {
  id: number
  clienteId: number
  tipo: 'CONTRATO_HONORARIOS' | 'PROCURACAO' | 'DOCUMENTO_PESSOAL' | 'OUTRO'
  nome: string
  tamanho: number
  restritoSocias: boolean
  origem: string
  criadoEm: string
}
export const ROTULO_TIPO_DOCUMENTO: Record<DocumentoCliente['tipo'], string> = {
  CONTRATO_HONORARIOS: 'Contrato de honorários',
  PROCURACAO: 'Procuração',
  DOCUMENTO_PESSOAL: 'Documento pessoal',
  OUTRO: 'Outro',
}
export const listarDocumentos = (clienteId: number) => api<DocumentoCliente[]>('GET', `/api/clientes/${clienteId}/documentos`)
export const urlDownloadDocumento = (id: number) => `/api/documentos/${id}/download`
export const enviarDocumento = (clienteId: number, arquivo: File) => {
  const f = new FormData()
  f.append('arquivo', arquivo)
  return api<DocumentoCliente>('POST', `/api/clientes/${clienteId}/documentos`, { multipart: f })
}

export interface MesFinanceiro {
  mes: string
  recebido: number
  emAberto: number
}
export interface ParcelaLinha {
  parcelaId: number
  contratoId: number
  clienteId: number
  cliente: string
  contrato: string
  area: string | null
  rotulo: string
  vencimento: string
  valorEmAberto: number
  diasAtraso: number
}
export interface PeriodoFinanceiro {
  quantidade: number
  total: number
  parcelas: ParcelaLinha[]
}
export interface ReceitaArea {
  area: string | null
  recebido: number
  emAberto: number
}
export interface PainelFinanceiroDados {
  meses: MesFinanceiro[]
  quantidadeVencidas: number
  totalVencido: number
  vencidas: ParcelaLinha[]
  aVencer7Dias: PeriodoFinanceiro
  aVencer30Dias: PeriodoFinanceiro
  porArea: ReceitaArea[]
}
export const carregarPainelFinanceiro = (area?: string | null) =>
  api<PainelFinanceiroDados>('GET', '/api/financeiro/painel' + (area ? `?area=${area}` : ''))
