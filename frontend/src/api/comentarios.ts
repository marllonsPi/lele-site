import { api } from './http'

export type EstadoComentario = 'PENDENTE' | 'APROVADO' | 'REJEITADO'

export interface ComentarioSite {
  id: number
  nome: string
  email: string
  mensagem: string
  estado: EstadoComentario
  criadoEm: string
  decididoEm: string | null
  decisaoVia: 'EMAIL' | 'SISTEMA' | null
  avisoEnviado: boolean
}

export const ROTULO_ESTADO_COMENTARIO: Record<EstadoComentario, string> = {
  PENDENTE: 'Aguardando decisão',
  APROVADO: 'Publicado no site',
  REJEITADO: 'Rejeitado',
}

export const listarComentarios = (estado?: EstadoComentario) =>
  api<ComentarioSite[]>('GET', '/api/comentarios' + (estado ? `?estado=${estado}` : ''))
export const aprovarComentario = (id: number) => api('POST', `/api/comentarios/${id}/aprovar`)
export const rejeitarComentario = (id: number) => api('POST', `/api/comentarios/${id}/rejeitar`)
