import { api } from './http'
import type { Perfil } from './sessao'

export interface Cliente {
  id: number
  nome: string
  documento: string | null
  email: string | null
  telefone: string | null
  endereco: string | null
  observacoes: string | null
  ativo: boolean
}
export type DadosCliente = Omit<Cliente, 'id' | 'ativo'>

export type PapelParte = 'CLIENTE' | 'CONTRARIA' | 'OUTRO'
export const ROTULO_PAPEL: Record<PapelParte, string> = { CLIENTE: 'Cliente', CONTRARIA: 'Parte contrária', OUTRO: 'Outro' }

export interface Parte {
  id: number
  nome: string
  papel: PapelParte
}

export interface Processo {
  id: number
  numero: string
  numeroFormatado: string
  tribunal: string
  varaComarca: string | null
  classe: string | null
  situacao: string | null
  valorCausa: number | null
  clienteId: number
  clienteNome: string
  advogadoId: number
  ativo: boolean
  partes: Parte[]
}
export interface DadosProcesso {
  numero: string
  tribunal: string
  varaComarca: string | null
  classe: string | null
  situacao: string | null
  valorCausa: number | null
  clienteId: number
  advogadoId: number
}

export interface Oab {
  id: number
  numero: string
  uf: string
  principal: boolean
  ativa: boolean
}
export interface Usuario {
  id: number
  nome: string
  email: string
  perfil: Perfil
  ativo: boolean
  supervisorId: number | null
  oabs: Oab[]
  mestre: boolean
  telefone: string | null
  whatsapp: string | null
  cargo: string | null
  emailAlertas: string | null
  alertasEmail: boolean
  assinatura: string | null
}
/** Contato e atuacao da conta; texto vazio e gravado como ausente. */
export interface DadosProfissionais {
  telefone: string | null
  whatsapp: string | null
  cargo: string | null
  emailAlertas: string | null
  alertasEmail: boolean
  assinatura: string | null
}
export interface DadosUsuario {
  nome: string
  email: string
  perfil: Perfil
  supervisorId: number | null
  profissionais?: DadosProfissionais
  mestre?: boolean
}

export type Escopo = 'PUBLICACAO' | 'AGENDAMENTO' | 'PROCESSO'
export const ESCOPOS: Escopo[] = ['PUBLICACAO', 'AGENDAMENTO', 'PROCESSO']
export const ROTULO_ESCOPO: Record<Escopo, string> = { PUBLICACAO: 'Publicações', AGENDAMENTO: 'Agenda', PROCESSO: 'Processos' }
export interface ParVisibilidade {
  observadorId: number
  titularId: number
  escopo: Escopo
}

export type EscopoFeriado = 'NACIONAL' | 'ESTADO' | 'MUNICIPIO' | 'TRIBUNAL'
export type TipoFeriado = 'FERIADO' | 'SUSPENSAO'
export interface Feriado {
  id: number
  escopo: EscopoFeriado
  referencia: string | null
  tipo: TipoFeriado
  dataInicial: string
  dataFinal: string | null
  descricao: string
  ativo: boolean
}
export interface DadosFeriado {
  escopo: EscopoFeriado
  referencia: string | null
  tipo: TipoFeriado
  dataInicial: string
  dataFinal: string | null
  descricao: string
}

export const listarClientes = () => api<Cliente[]>('GET', '/api/clientes')
export const criarCliente = (d: DadosCliente) => api<Cliente>('POST', '/api/clientes', { json: d })
export const atualizarCliente = (id: number, d: DadosCliente) => api<Cliente>('PUT', `/api/clientes/${id}`, { json: d })
export const desativarCliente = (id: number) => api('POST', `/api/clientes/${id}/desativar`)
export const reativarCliente = (id: number) => api('POST', `/api/clientes/${id}/reativar`)

export const listarProcessos = (clienteId?: number) =>
  api<Processo[]>('GET', '/api/processos' + (clienteId ? `?clienteId=${clienteId}` : ''))
export const criarProcesso = (d: DadosProcesso) => api<Processo>('POST', '/api/processos', { json: d })
export const atualizarProcesso = (id: number, d: DadosProcesso) => api<Processo>('PUT', `/api/processos/${id}`, { json: d })
export const desativarProcesso = (id: number) => api('POST', `/api/processos/${id}/desativar`)
export const adicionarParte = (id: number, nome: string, papel: PapelParte) =>
  api<Processo>('POST', `/api/processos/${id}/partes`, { json: { nome, papel } })

export const listarUsuarios = () => api<Usuario[]>('GET', '/api/usuarios')
export const criarUsuario = (d: DadosUsuario & { senha: string }) => api<Usuario>('POST', '/api/usuarios', { json: d })
export const atualizarUsuario = (id: number, d: DadosUsuario) => api<Usuario>('PUT', `/api/usuarios/${id}`, { json: d })
export const desativarUsuario = (id: number) => api('POST', `/api/usuarios/${id}/desativar`)
export const reativarUsuario = (id: number) => api('POST', `/api/usuarios/${id}/reativar`)
export const redefinirSenha = (id: number, senha: string) => api('PUT', `/api/usuarios/${id}/senha`, { json: { senha } })
export const adicionarOab = (id: number, numero: string, uf: string, principal: boolean) =>
  api<Oab>('POST', `/api/usuarios/${id}/oabs`, { json: { numero, uf, principal } })
export const desativarOab = (oabId: number) => api('DELETE', `/api/oabs/${oabId}`)
export const reativarOab = (oabId: number) => api('POST', `/api/oabs/${oabId}/reativar`)
export const tornarOabPrincipal = (oabId: number) => api('POST', `/api/oabs/${oabId}/principal`)

export const listarVisibilidade = () => api<ParVisibilidade[]>('GET', '/api/visibilidade')
export const definirVisibilidade = (observadorId: number, regras: ParVisibilidade[]) =>
  api('PUT', `/api/visibilidade/${observadorId}`, { json: regras })

export const listarCalendario = () => api<Feriado[]>('GET', '/api/calendario')
export const criarFeriado = (d: DadosFeriado) => api<Feriado>('POST', '/api/calendario', { json: d })
export const atualizarFeriado = (id: number, d: DadosFeriado) => api<Feriado>('PUT', `/api/calendario/${id}`, { json: d })
export const desativarFeriado = (id: number) => api('DELETE', `/api/calendario/${id}`)
