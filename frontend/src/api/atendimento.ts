import { api } from './http'

export type Canal = 'PRESENCIAL' | 'ONLINE' | 'TELEFONE' | 'WHATSAPP' | 'EMAIL' | 'OUTRO'
export const ROTULO_CANAL: Record<Canal, string> = {
  PRESENCIAL: 'Presencial',
  ONLINE: 'Online',
  TELEFONE: 'Telefone',
  WHATSAPP: 'WhatsApp',
  EMAIL: 'E-mail',
  OUTRO: 'Outro',
}

export interface Atendimento {
  id: number
  clienteId: number
  processoId: number | null
  momento: string
  canal: string
  atendenteId: number
  resumo: string
  origem: string
  agendamentoId: number | null
}

export interface Andamento {
  id: number
  processoId: number
  data: string
  descricao: string
  origem: string
  repassado: boolean
  repassadoEm: string | null
  repassadoVia: string | null
}

export const listarAtendimentos = (clienteId?: number) =>
  api<Atendimento[]>('GET', '/api/atendimentos' + (clienteId ? `?clienteId=${clienteId}` : ''))
export const registrarAtendimento = (d: {
  clienteId: number
  processoId: number | null
  canal: Canal
  momento: string | null
  resumo: string
  atendenteId: number | null
}) => api<Atendimento>('POST', '/api/atendimentos', { json: d })
export const listarAndamentosPendentes = () => api<Andamento[]>('GET', '/api/andamentos/pendentes')
export const registrarAndamento = (d: { processoId: number; data: string | null; descricao: string }) =>
  api<Andamento>('POST', '/api/andamentos', { json: d })
export const marcarRepassado = (id: number, via: string) => api<Andamento>('POST', `/api/andamentos/${id}/repassar`, { json: { via } })
