import { api } from './http'

export type TipoAgendamento = 'CTR' | 'INI' | 'CON' | 'MAN' | 'AUD' | 'REC' | 'ATD'
export type StatusAgendamento =
  | 'AGUARDANDO_CONFERENCIA_PRAZO'
  | 'PENDENTE'
  | 'FINALIZADO'
  | 'EM_CONFERENCIA_SUPERVISOR'
  | 'EM_CONFERENCIA_SOCIA'
  | 'CONFERIDO'
  | 'DEVOLVIDO'
export type StatusVisual = 'VERMELHO' | 'AMARELO' | 'VERDE' | 'NENHUM'
export type Modalidade = 'PRESENCIAL' | 'ONLINE' | 'TELEFONE'

export const ROTULO_TIPO: Record<TipoAgendamento, string> = {
  CTR: 'Controladoria',
  INI: 'Petição inicial',
  CON: 'Contestação',
  MAN: 'Manifestação',
  AUD: 'Audiência',
  REC: 'Recurso',
  ATD: 'Atendimento',
}

export const ROTULO_STATUS: Record<StatusAgendamento, string> = {
  AGUARDANDO_CONFERENCIA_PRAZO: 'Aguardando conferência do prazo',
  PENDENTE: 'Pendente',
  FINALIZADO: 'Finalizado',
  EM_CONFERENCIA_SUPERVISOR: 'Em conferência do supervisor',
  EM_CONFERENCIA_SOCIA: 'Em conferência das sócias',
  CONFERIDO: 'Conferido',
  DEVOLVIDO: 'Devolvido',
}

export const ROTULO_MODALIDADE: Record<Modalidade, string> = {
  PRESENCIAL: 'Presencial',
  ONLINE: 'Online',
  TELEFONE: 'Telefone',
}

export const TIPOS_PRAZO: TipoAgendamento[] = ['CTR', 'INI', 'CON', 'MAN', 'REC']

export interface ItemAgenda {
  id: number
  processoId: number | null
  clienteId: number | null
  tipo: TipoAgendamento
  corTipo: string
  descricao: string | null
  responsavelId: number
  criadoPor: number
  urgente: boolean
  status: StatusAgendamento
  data: string
  hora: string | null
  duracaoMin: number | null
  modalidade: Modalidade | null
  localOuLink: string | null
  inicioPrazo: string | null
  prazoInterno: string | null
  prazoFatal: string | null
  statusVisual: StatusVisual
  alertaAudiencia: boolean
  aguardandoConferenciaPrazo: boolean
}

export interface Prazo {
  id: number
  processoId: number
  tipo: TipoAgendamento
  cor: string
  descricao: string | null
  responsavelId: number
  lancadoPor: number
  urgente: boolean
  status: StatusAgendamento
  inicioPrazo: string
  prazoInterno: string
  prazoFatal: string
  conferidoPor: number | null
  conferidoEm: string | null
}

export interface NovoPrazo {
  processoId: number
  tipo: TipoAgendamento
  descricao: string
  responsavelId: number
  urgente: boolean
  inicioPrazo: string
  prazoFatal: string
}

export interface NovoCompromisso {
  tipo: 'AUD' | 'ATD'
  processoId: number | null
  clienteId: number | null
  descricao: string
  responsavelId: number
  urgente: boolean
  inicio: string
  duracaoMin: number | null
  modalidade: Modalidade
  localOuLink: string
}

export interface Observacao {
  id: number
  autorId: number
  texto: string
  criadoEm: string
}

export interface Anexo {
  id: number
  nome: string
  tamanho: number
  autorId: number
  criadoEm: string
}

export interface Conferencia {
  id: number
  etapa: string
  resultado: string
  motivo: string | null
  usuarioId: number
  criadoEm: string
}

export interface ProcessoResumo {
  id: number
  numeroFormatado: string
  tribunal: string
  clienteId: number
  clienteNome: string
  advogadoId: number
  ativo: boolean
}

export const carregarAgenda = (usuarioId?: number) =>
  api<ItemAgenda[]>('GET', '/api/agenda' + (usuarioId ? `?usuarioId=${usuarioId}` : ''))
export const carregarPrazos = () => api<Prazo[]>('GET', '/api/prazos')
export const lancarPrazo = (d: NovoPrazo) => api<Prazo>('POST', '/api/prazos', { json: d })
export const conferirPrazo = (id: number) => api<Prazo>('POST', `/api/prazos/${id}/conferir`)
export interface AvisoConflito {
  tipo: 'MESMO_ADVOGADO' | 'AUDIENCIAS_NO_ESCRITORIO'
  comTipo: TipoAgendamento
  inicio: string
  fim: string
}

export const marcarCompromisso = (d: NovoCompromisso) =>
  api<{ item: ItemAgenda; avisos: AvisoConflito[] }>('POST', '/api/compromissos', { json: d })
export const carregarProcessos = () => api<ProcessoResumo[]>('GET', '/api/processos')

export const finalizar = (id: number, resumo?: string) =>
  api<ItemAgenda>('POST', `/api/agendamentos/${id}/finalizar`, resumo ? { json: { resumo } } : {})
export const aprovarSupervisor = (id: number) => api<ItemAgenda>('POST', `/api/agendamentos/${id}/aprovar-supervisor`)
export const devolverSupervisor = (id: number, motivo: string) =>
  api<ItemAgenda>('POST', `/api/agendamentos/${id}/devolver-supervisor`, { json: { motivo } })
export const conferirSocia = (id: number) => api<ItemAgenda>('POST', `/api/agendamentos/${id}/conferir-socia`)
export const devolverSocia = (id: number, motivo: string) =>
  api<ItemAgenda>('POST', `/api/agendamentos/${id}/devolver`, { json: { motivo } })
export const carregarPendentesConferencia = () => api<ItemAgenda[]>('GET', '/api/conferencias/pendentes')
export const carregarHistoricoConferencia = (id: number) =>
  api<Conferencia[]>('GET', `/api/agendamentos/${id}/conferencias`)

export const carregarObservacoes = (id: number) => api<Observacao[]>('GET', `/api/agendamentos/${id}/observacoes`)
export const registrarObservacao = (id: number, texto: string) =>
  api<Observacao>('POST', `/api/agendamentos/${id}/observacoes`, { json: { texto } })
export const carregarAnexos = (id: number) => api<Anexo[]>('GET', `/api/agendamentos/${id}/anexos`)
export function anexarArquivo(id: number, arquivo: File) {
  const dados = new FormData()
  dados.append('arquivo', arquivo)
  return api<Anexo>('POST', `/api/agendamentos/${id}/anexos`, { multipart: dados })
}
export const urlDownload = (anexoId: number) => `/api/anexos/${anexoId}/download`

export interface ClienteResumo {
  id: number
  nome: string
}
export const carregarClientes = () => api<ClienteResumo[]>('GET', '/api/clientes')
