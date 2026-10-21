import { api } from './http'
import type { DocumentoCliente } from './importacao'

type TipoDocumento = DocumentoCliente['tipo']

export interface StatusDrive {
  googleDisponivel: boolean
  minhaContaComDrive: boolean
  contaDoDriveDefinida: boolean
  euSouAContaDoDrive: boolean
  contaDoDriveConectada: boolean
  pastaRaizId: string
  ativo: boolean
}

export interface SugestaoDrive {
  driveId: string
  nome: string
  mime: string | null
  tamanho: number
  modificadoEm: string | null
  caminho: string[]
  tipo: TipoDocumento
  processoId: number | null
  processoNumero: string | null
}

export interface SugestoesDrive {
  driveAtivo: boolean
  pastaEncontrada: boolean
  pastaNome: string | null
  itens: SugestaoDrive[]
}

export interface EscolhaDrive {
  driveId: string
  tipo: TipoDocumento | null
  processoId: number | null
}

export const statusDrive = () => api<StatusDrive>('GET', '/api/drive/status')
export const salvarConfigDrive = (usarMinhaConta: boolean, pastaRaiz: string) =>
  api<StatusDrive>('PUT', '/api/drive/config', { json: { usarMinhaConta, pastaRaiz } })
export const locaisNoDisco = () => api<{ pendentes: number }>('GET', '/api/drive/locais')
export const moverLocaisParaDrive = () => api<{ movidos: number; falhas: number; restantes: number }>('POST', '/api/drive/mover-locais')
export const sugestoesDoDrive = (clienteId: number) => api<SugestoesDrive>('GET', `/api/clientes/${clienteId}/drive/sugestoes`)
export const associarDoDrive = (clienteId: number, itens: EscolhaDrive[]) =>
  api<{ associados: number }>('POST', `/api/clientes/${clienteId}/drive/associar`, { json: { itens } })
