import { api } from './http'
import type { AreaAtuacao } from './cadastros'

export interface ResumoFinanceiro {
  contratado: number
  parcelasPagas: number
  parcelasAVencer: number
  parcelasEmAtraso: number
  quantidadeEmAtraso: number
  custasEDespesas: number
  reembolsavel: number
  exitoPrevisto: number
  exitoRecebido: number
  sucumbenciaPrevista: number
  sucumbenciaRecebida: number
}

export interface Contrato {
  id: number
  cliente_id: number
  processo_id: number | null
  descricao: string
  valor_total: number
  area: AreaAtuacao | null
  ativo: boolean
}

export type SituacaoParcela = 'PAGA' | 'A_VENCER' | 'EM_ATRASO' | 'CANCELADA'
export interface Parcela {
  id: number
  numero: number
  vencimento: string
  valor: number
  pago_em: string | null
  valor_pago: number | null
  situacao: SituacaoParcela
}

export const resumoDoCliente = (id: number) => api<ResumoFinanceiro>('GET', `/api/financeiro/clientes/${id}/resumo`)
export const listarContratos = (clienteId: number) => api<Contrato[]>('GET', `/api/financeiro/contratos?clienteId=${clienteId}`)
export const listarParcelas = (contratoId: number) => api<Parcela[]>('GET', `/api/financeiro/contratos/${contratoId}/parcelas`)
export const criarContrato = (d: {
  clienteId: number
  processoId: number | null
  descricao: string
  valorTotal: number
  parcelas: number
  primeiroVencimento: string
  area: AreaAtuacao
}) => api<{ id: number }>('POST', '/api/financeiro/contratos', { json: d })
export const pagarParcela = (id: number, data: string, valor: number, forma?: string) =>
  api('POST', `/api/financeiro/parcelas/${id}/pagar`, { json: forma ? { data, valor, forma } : { data, valor } })
export const registrarDespesa = (d: {
  clienteId: number
  processoId: number | null
  tipo: 'CUSTAS' | 'DESPESA'
  descricao: string
  valor: number
  data: string | null
  reembolsavel: boolean
}) => api<{ id: number }>('POST', '/api/financeiro/despesas', { json: d })
export const registrarHonorarioVariavel = (d: {
  clienteId: number
  processoId: number | null
  tipo: 'EXITO' | 'SUCUMBENCIA'
  descricao: string
  valorPrevisto: number
}) => api<{ id: number }>('POST', '/api/financeiro/honorarios', { json: d })
