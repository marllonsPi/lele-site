import { api } from './http'
import type { ItemAgenda, TipoAgendamento } from './agenda'

export type Fonte = 'DJEN' | 'DOMICILIO' | 'EMAIL'
export type EstadoPublicacao = 'A_CLASSIFICAR' | 'CLASSIFICADA' | 'DESCARTADA_COM_REGISTRO'

export const ROTULO_ESTADO: Record<EstadoPublicacao, string> = {
  A_CLASSIFICAR: 'A classificar',
  CLASSIFICADA: 'Classificada',
  DESCARTADA_COM_REGISTRO: 'Descartada (com registro)',
}
export const ROTULO_FONTE: Record<Fonte, string> = { DJEN: 'DJEN', DOMICILIO: 'Domicílio Judicial', EMAIL: 'E-mail' }

export interface Publicacao {
  id: number
  numeroProcesso: string
  numeroFormatado: string | null
  titularId: number
  dataPublicacao: string
  tribunal: string | null
  resumo: string | null
  fontes: Fonte[]
  somenteEmUmaFonte: boolean
  estado: EstadoPublicacao
  processoId: number | null
  sugerirCadastro: boolean
  agendamentoId: number | null
  motivoDescarte: string | null
  conteudo: string | null
}

export interface Classificacao {
  tipo: TipoAgendamento
  processoId: number | null
  responsavelId: number
  urgente: boolean
  descricao: string
  inicioPrazo: string
  prazoFatal: string
}

export interface ExecucaoCaptura {
  id: number
  fonte: string
  inicio: string
  fim: string | null
  resultado: string
  itensLidos: number
  itensNovos: number
  erro: string | null
}

export interface Ignorado {
  momento: string
  fonte: string
  tribunal: string | null
  motivo: string
}

export interface FiltroCaptura {
  id: number
  usuarioId: number
  fonte: Fonte
  tribunalPrefixo: string
  excecoes: string | null
  ativo: boolean
}

export interface Painel {
  porPessoa: { usuarioId: number; abertos: number; vencidos: number; urgentes: number }[]
  atrasos: ItemAgenda[]
  prazosDaSemana: ItemAgenda[]
  compromissosProximos: ItemAgenda[]
  aguardandoConferenciaSocia: ItemAgenda[]
  aguardandoConferenciaPrazo: ItemAgenda[]
  aClassificar: number
}

export const listarPublicacoes = (estado?: EstadoPublicacao) =>
  api<Publicacao[]>('GET', '/api/publicacoes' + (estado ? `?estado=${estado}` : ''))
export const buscarPublicacao = (id: number) => api<Publicacao>('GET', `/api/publicacoes/${id}`)
export const classificarPublicacao = (id: number, c: Classificacao) =>
  api<Publicacao>('POST', `/api/publicacoes/${id}/classificar`, { json: c })
export const descartarPublicacao = (id: number, motivo: string) =>
  api<Publicacao>('POST', `/api/publicacoes/${id}/descartar`, { json: { motivo } })
export const vincularProcesso = (id: number, processoId: number) =>
  api<Publicacao>('PUT', `/api/publicacoes/${id}/processo`, { json: { processoId } })

export const executarCaptura = () => api<ExecucaoCaptura[]>('POST', '/api/captura/executar')
export const listarExecucoes = () => api<ExecucaoCaptura[]>('GET', '/api/captura/execucoes')
export const listarIgnorados = () => api<Ignorado[]>('GET', '/api/captura/ignorados')
export const listarFiltros = () => api<FiltroCaptura[]>('GET', '/api/captura/filtros')
export const criarFiltro = (d: { usuarioId: number; fonte: Fonte; tribunalPrefixo: string; excecoes: string }) =>
  api<FiltroCaptura>('POST', '/api/captura/filtros', { json: d })
export const desativarFiltro = (id: number) => api('DELETE', `/api/captura/filtros/${id}`)

export const carregarPainel = () => api<Painel>('GET', '/api/painel')
