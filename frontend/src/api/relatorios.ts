import { api } from './http'

export type TipoInterno =
  | 'AGENDA_DIA'
  | 'AGENDA_USUARIO'
  | 'PENDENTES'
  | 'VENCIDOS'
  | 'FINALIZADOS'
  | 'ATENDIMENTOS'
  | 'FINANCEIRO_CLIENTE'

export const ROTULO_TIPO_INTERNO: Record<TipoInterno, string> = {
  AGENDA_DIA: 'Agenda do dia',
  AGENDA_USUARIO: 'Agenda de uma pessoa',
  PENDENTES: 'Pendentes',
  VENCIDOS: 'Vencidos',
  FINALIZADOS: 'Finalizados',
  ATENDIMENTOS: 'Atendimentos',
  FINANCEIRO_CLIENTE: 'Financeiro de um cliente',
}

export interface RelatorioDados {
  titulo: string
  subtitulo: string
  colunas: string[]
  linhas: string[][]
  geradoEm: string
}

export interface FiltrosInternos {
  de?: string
  ate?: string
  usuarioId?: number | null
  clienteId?: number | null
}

function consulta(f: FiltrosInternos): string {
  const q = new URLSearchParams()
  if (f.de) q.set('de', f.de)
  if (f.ate) q.set('ate', f.ate)
  if (f.usuarioId) q.set('usuarioId', String(f.usuarioId))
  if (f.clienteId) q.set('clienteId', String(f.clienteId))
  const s = q.toString()
  return s ? '?' + s : ''
}

export const gerarInterno = (tipo: TipoInterno, f: FiltrosInternos) =>
  api<RelatorioDados>('GET', `/api/relatorios/internos/${tipo}${consulta(f)}`)
export const urlPdfInterno = (tipo: TipoInterno, f: FiltrosInternos) => `/api/relatorios/internos/${tipo}/pdf${consulta(f)}`
export const enviarInternoAsSocias = (tipo: TipoInterno, f: FiltrosInternos) =>
  api<{ enviados: number }>('POST', `/api/relatorios/internos/${tipo}/enviar${consulta(f)}`)

export interface ItemRelatorio {
  data: string
  texto: string
}
export interface ProcessoRelatorio {
  numero: string
  varaComarca: string | null
  tribunal: string
  tipoAcao: string | null
  situacao: string | null
  partes: string[]
  movimentacoes: ItemRelatorio[]
  feitos: ItemRelatorio[]
  proximosPassos: ItemRelatorio[]
}
export interface ConteudoCliente {
  clienteNome: string
  periodoInicio: string | null
  periodoFim: string
  processos: ProcessoRelatorio[]
  comentarios: string | null
  financeiro: { parcelasPagas: number; parcelasAVencer: number } | null
}
export type EstadoRelatorio = 'RASCUNHO' | 'EM_REVISAO' | 'APROVADO' | 'ENVIADO'
export const ROTULO_ESTADO_RELATORIO: Record<EstadoRelatorio, string> = {
  RASCUNHO: 'Rascunho',
  EM_REVISAO: 'Em revisão',
  APROVADO: 'Aprovado',
  ENVIADO: 'Enviado',
}
export interface RelatorioCliente {
  id: number
  clienteId: number
  periodoInicio: string | null
  periodoFim: string
  estado: EstadoRelatorio
  incluirFinanceiro: boolean
  conteudo: ConteudoCliente
  criadoPor: number
  criadoEm: string
  aprovadoPor: number | null
  aprovadoEm: string | null
  enviadoPor: number | null
  enviadoEm: string | null
  canal: string | null
}

export const criarRelatorioCliente = (d: {
  clienteId: number
  processoIds: number[] | null
  de: string | null
  ate: string | null
  desdeUltimoRelatorio: boolean
  incluirFinanceiro: boolean
}) => api<RelatorioCliente>('POST', '/api/relatorios/clientes', { json: d })
export const buscarRelatorioCliente = (id: number) => api<RelatorioCliente>('GET', `/api/relatorios/clientes/${id}`)
export const relatoriosDoCliente = (clienteId: number) => api<RelatorioCliente[]>('GET', `/api/clientes/${clienteId}/relatorios`)
export const editarRelatorioCliente = (id: number, c: ConteudoCliente) =>
  api<RelatorioCliente>('PUT', `/api/relatorios/clientes/${id}`, { json: c })
export const aprovarRelatorioCliente = (id: number) => api<RelatorioCliente>('POST', `/api/relatorios/clientes/${id}/aprovar`)
export const enviarRelatorioCliente = (id: number, canal: string) =>
  api<RelatorioCliente>('POST', `/api/relatorios/clientes/${id}/enviar`, { json: { canal } })
export const urlPdfCliente = (id: number) => `/api/relatorios/clientes/${id}/pdf`

export interface Modelo {
  id: number
  tipo: string
  areaDireito: string
  titulo: string
  nomeArquivo: string
  tamanho: number
  ativo: boolean
  criadoPor: number
  criadoEm: string
}
export const listarModelos = (tipo?: string, area?: string) => {
  const q = new URLSearchParams()
  if (tipo) q.set('tipo', tipo)
  if (area) q.set('area', area)
  const s = q.toString()
  return api<Modelo[]>('GET', '/api/modelos' + (s ? '?' + s : ''))
}
export const listarAreas = () => api<string[]>('GET', '/api/modelos/areas')
export function criarModelo(tipo: string, area: string, titulo: string, arquivo: File) {
  const dados = new FormData()
  dados.append('tipo', tipo)
  dados.append('area', area)
  dados.append('titulo', titulo)
  dados.append('arquivo', arquivo)
  return api<Modelo>('POST', '/api/modelos', { multipart: dados })
}
export const desativarModelo = (id: number) => api('DELETE', `/api/modelos/${id}`)
export const urlDownloadModelo = (id: number) => `/api/modelos/${id}/download`

export interface CadastroDoisFatores {
  segredo: string
  uri: string
}
export const statusDoisFatores = () => api<{ ativo: boolean }>('GET', '/api/me/2fa')
export const iniciarDoisFatores = () => api<CadastroDoisFatores>('POST', '/api/me/2fa/iniciar')
export const confirmarDoisFatores = (codigo: string) => api('POST', '/api/me/2fa/confirmar', { json: { codigo } })
export const desativarDoisFatores = (codigo: string) => api('POST', '/api/me/2fa/desativar', { json: { codigo } })
