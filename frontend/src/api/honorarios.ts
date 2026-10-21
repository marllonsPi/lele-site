import { api } from './http'
import type { AreaAtuacao } from './cadastros'

export type Seccional = 'DF' | 'SP'
export const SECCIONAIS: Seccional[] = ['DF', 'SP']
export type Fase = 'JUDICIAL' | 'EXTRAJUDICIAL' | 'CONHECIMENTO' | 'RECURSO' | 'CUMPRIMENTO'
export const ROTULO_FASE: Record<Fase, string> = {
  JUDICIAL: 'Judicial',
  EXTRAJUDICIAL: 'Extrajudicial',
  CONHECIMENTO: 'Conhecimento',
  RECURSO: 'Recurso',
  CUMPRIMENTO: 'Cumprimento de sentença',
}

export interface Versao {
  id: number
  tabela: 'OAB' | 'ESCRITORIO'
  seccional: Seccional | null
  referencia: string | null
  valorUrh: number | null
  fatorEscritorio: number | null
  descontoPadrao: number | null
  descontoTrabalhista: number | null
  descontoPacote: number
  vigente: boolean
  arquivoNome: string | null
  criadoEm: string
  substituidaEm: string | null
  itens: number
}

export interface ItemTabela {
  id: number
  versaoId: number
  tabela: 'OAB' | 'ESCRITORIO'
  area: AreaAtuacao | null
  tipoServico: string
  fase: Fase | null
  chaveServico: string
  itemOab: string | null
  valorUrh: number | null
  valor: number | null
  derivado: boolean
  descontoPercentual: number | null
  valorSugerido: number | null
  exitoTexto: string | null
  exitoMin: number | null
  exitoMax: number | null
  parcelamento: string | null
  observacoes: string | null
  ativo: boolean
}

export type DadosItemTabela = Omit<ItemTabela, 'id' | 'versaoId' | 'tabela' | 'chaveServico' | 'derivado' | 'ativo'>

export interface ServicoCatalogo {
  chave: string
  area: AreaAtuacao | null
  tipoServico: string
  fase: Fase | null
}

export interface ResultadoImportacaoTabela {
  versaoOabId: number
  versaoEscritorioId: number | null
  servicos: number
  semArea: number
  semValor: number
  avisos: string[]
}

export interface LinhaQuadro {
  servico: { chave: string; area: string | null; tipo: string; fase: string | null; exitoTexto: string | null }
  minimoOab: number | null
  valorTabela: number | null
  valorSugerido: number | null
  avisos: string[]
}
export interface OpcaoParcelamento {
  rotulo: string
  total: number
  entrada: number
  parcelas: number
  valorParcela: number
  ultimaParcela: number
}
export interface Quadro {
  linhas: LinhaQuadro[]
  minimoOabTotal: number
  valorTabelaTotal: number
  valorSugeridoTotal: number
  descontoPacote: number
  descontoPacoteAplicado: number
  exito: string[]
  opcoes: OpcaoParcelamento[]
  avisos: string[]
}

export type StatusProposta = 'ABERTA' | 'FECHADA' | 'NAO_FECHADA'
export type MotivoJustificativa = 'PERMUTA' | 'HIPOSSUFICIENTE' | 'PRO_BONO' | 'PACOTE' | 'OUTRO'
export const ROTULO_MOTIVO: Record<MotivoJustificativa, string> = {
  PERMUTA: 'Permuta',
  HIPOSSUFICIENTE: 'Cliente hipossuficiente',
  PRO_BONO: 'Pro bono',
  PACOTE: 'Pacote de serviços',
  OUTRO: 'Outro',
}

export interface Proposta {
  id: number
  clienteId: number
  clienteNome: string
  agendamentoId: number | null
  advogadoId: number
  seccional: Seccional
  status: StatusProposta
  valorMinimoOab: number
  valorSugerido: number
  descontoPacote: number
  valorFechado: number | null
  justificativaTipo: MotivoJustificativa | null
  justificativa: string | null
  entrada: number | null
  parcelas: number | null
  primeiroVencimento: string | null
  formaPagamento: string | null
  dataAssinatura: string | null
  contratoLancado: boolean
  criadoEm: string
  fechadoEm: string | null
  itens: { id: number; chave: string; area: string | null; tipoServico: string; fase: string | null; minimoOab: number | null; valorTabela: number | null; valorSugerido: number | null }[]
  quadro: Quadro | null
  abaixoDaOab: boolean
  diferencaAbaixoOab: number
  tabelaOabVigente: boolean
}

export interface Fechamento {
  valorFechado: number
  entrada: number | null
  parcelas: number | null
  primeiroVencimento: string | null
  formaPagamento: string | null
  justificativaTipo: MotivoJustificativa | null
  justificativa: string | null
}

export interface ResumoProposta {
  id: number
  cliente: string
  advogado: string
  status: StatusProposta
  valorSugerido: number
  valorFechado: number | null
  valorMinimoOab: number
  abaixoDaOab: boolean
  contratoLancado: boolean
  criadoEm: string
}

export interface RelatorioHonorarios {
  de: string
  ate: string
  sugeridoXFechado: { advogadoId: number; advogado: string; area: string | null; mes: string; propostas: number; sugerido: number; fechado: number }[]
  conversao: { advogadoId: number; advogado: string; reunioes: number; fechadas: number; taxa: number }[]
  conversaoGeral: { reunioes: number; fechadas: number; taxa: number }
  ticketMedio: { area: string | null; contratos: number; total: number; ticketMedio: number }[]
  abaixoDaTabela: { propostaId: number; cliente: string; advogado: string; data: string; minimoOab: number; fechado: number; diferenca: number; tipo: string; justificativa: string }[]
}

// tabelas (somente socias)
export const listarVersoes = () => api<Versao[]>('GET', '/api/honorarios/tabelas/versoes')
export const listarItensTabela = (versaoId: number, excluidos = false) =>
  api<ItemTabela[]>('GET', `/api/honorarios/tabelas/versoes/${versaoId}/itens${excluidos ? '?excluidos=true' : ''}`)
export const criarItemTabela = (versaoId: number, d: DadosItemTabela) => api<ItemTabela>('POST', `/api/honorarios/tabelas/versoes/${versaoId}/itens`, { json: d })
export const atualizarItemTabela = (id: number, d: DadosItemTabela) => api<ItemTabela>('PUT', `/api/honorarios/tabelas/itens/${id}`, { json: d })
export const excluirItemTabela = (id: number) => api('DELETE', `/api/honorarios/tabelas/itens/${id}`)
export const restaurarItemTabela = (id: number) => api('POST', `/api/honorarios/tabelas/itens/${id}/restaurar`)
export const atualizarUrh = (versaoOabId: number, valor: number) => api('PUT', `/api/honorarios/tabelas/oab/${versaoOabId}/urh`, { json: { valor } })
export const atualizarPolitica = (
  versaoId: number,
  d: { fator: number | null; descontoPadrao: number | null; descontoTrabalhista: number | null; descontoPacote: number | null },
) => api('PUT', `/api/honorarios/tabelas/escritorio/${versaoId}/politica`, { json: d })
export const importarTabela = (arquivo: File, seccional: Seccional, substituirEscritorio: boolean) => {
  const f = new FormData()
  f.append('arquivo', arquivo)
  f.append('seccional', seccional)
  f.append('substituirEscritorio', String(substituirEscritorio))
  return api<ResultadoImportacaoTabela>('POST', '/api/honorarios/tabelas/importar', { multipart: f })
}
export const carregarModeloContrato = () => api<{ texto: string }>('GET', '/api/honorarios/modelo-contrato')
export const salvarModeloContrato = (texto: string) => api('PUT', '/api/honorarios/modelo-contrato', { json: { texto } })
export const carregarRelatorioHonorarios = (de?: string, ate?: string) => {
  const q = [de ? `de=${de}` : '', ate ? `ate=${ate}` : ''].filter(Boolean).join('&')
  return api<RelatorioHonorarios>('GET', '/api/honorarios/relatorios' + (q ? `?${q}` : ''))
}
export const listarPropostas = () => api<ResumoProposta[]>('GET', '/api/honorarios/propostas')

// atendimento e proposta (socias e advogado do atendimento)
export const carregarCatalogo = () => api<ServicoCatalogo[]>('GET', '/api/honorarios/catalogo')
export const propostaDoAtendimento = (agendamentoId: number) => api<Proposta | undefined>('GET', `/api/atendimentos/${agendamentoId}/proposta`)
export const sugerirNoAtendimento = (agendamentoId: number, seccional: Seccional, servicos: string[]) =>
  api<Proposta>('POST', `/api/atendimentos/${agendamentoId}/proposta/sugestao`, { json: { seccional, servicos } })
export const sugerirParaCliente = (clienteId: number, seccional: Seccional, servicos: string[]) =>
  api<Proposta>('POST', `/api/clientes/${clienteId}/proposta/sugestao`, { json: { seccional, servicos } })
export const recalcularProposta = (id: number, seccional: Seccional, servicos: string[]) =>
  api<Proposta>('PUT', `/api/propostas/${id}/servicos`, { json: { seccional, servicos } })
export const fecharProposta = (id: number, f: Fechamento) => api<Proposta>('PUT', `/api/propostas/${id}/fechamento`, { json: f })
export const propostaNaoFechada = (id: number) => api<Proposta>('POST', `/api/propostas/${id}/nao-fechada`)
export const assinarProposta = (id: number, dataAssinatura: string | null) =>
  api<Proposta>('POST', `/api/propostas/${id}/assinar`, { json: dataAssinatura ? { dataAssinatura } : {} })
export const urlMinuta = (id: number) => `/api/propostas/${id}/minuta`
