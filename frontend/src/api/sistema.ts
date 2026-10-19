import { api } from './http'
import type { DadosProfissionais, Usuario } from './cadastros'

export interface CampoConfiguracao {
  chave: string
  rotulo: string
  grupo: string
  ajuda: string
  maximo: number
  longo: boolean
}
export interface Configuracao {
  campos: CampoConfiguracao[]
  valores: Record<string, string>
}

export type SituacaoItem = 'OK' | 'ATENCAO' | 'PENDENTE'
export interface ItemProntidao {
  id: string
  titulo: string
  situacao: SituacaoItem
  detalhe: string
  rota: string | null
}

export const carregarConfiguracao = () => api<Configuracao>('GET', '/api/sistema/configuracao')
export const salvarConfiguracao = (valores: Record<string, string>) =>
  api<Configuracao>('PUT', '/api/sistema/configuracao', { json: valores })
export const carregarProntidao = () => api<ItemProntidao[]>('GET', '/api/sistema/prontidao')

export const carregarMeuPerfil = () => api<Usuario>('GET', '/api/me/perfil')
export const salvarMeuPerfil = (d: DadosProfissionais) => api<Usuario>('PUT', '/api/me/perfil', { json: d })
export const trocarMinhaSenha = (senhaAtual: string, novaSenha: string) =>
  api('PUT', '/api/me/senha', { json: { senhaAtual, novaSenha } })
