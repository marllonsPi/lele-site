import type { Perfil } from '../api/sessao'

export interface ItemMenu {
  rota: string
  rotulo: string
  /** Vazio = todos os perfis. A UI so esconde; o servidor decide. */
  perfis?: Perfil[]
}

/** Menu principal; cada parte do frontend acrescenta suas telas aqui. */
export const ITENS_MENU: ItemMenu[] = [
  { rota: '/', rotulo: 'Início' },
  { rota: '/agenda', rotulo: 'Agenda' },
  { rota: '/prazos', rotulo: 'Prazos' },
  // supervisores sao advogados; o servidor decide o que cada um ve na fila
  { rota: '/painel', rotulo: 'Painel', perfis: ['SOCIA'] },
  { rota: '/publicacoes', rotulo: 'Publicações' },
  { rota: '/captura', rotulo: 'Captura', perfis: ['SOCIA'] },
  { rota: '/clientes', rotulo: 'Clientes' },
  { rota: '/processos', rotulo: 'Processos' },
  { rota: '/atendimento', rotulo: 'Atendimento' },
  { rota: '/financeiro', rotulo: 'Financeiro', perfis: ['SOCIA'] },
  { rota: '/calendario', rotulo: 'Calendário' },
  { rota: '/usuarios', rotulo: 'Usuários', perfis: ['SOCIA'] },
  { rota: '/visibilidade', rotulo: 'Visibilidade', perfis: ['SOCIA'] },
  { rota: '/conferencia', rotulo: 'Conferência', perfis: ['SOCIA', 'ADVOGADO'] },
]

export function itensPara(perfil: Perfil): ItemMenu[] {
  return ITENS_MENU.filter((i) => !i.perfis || i.perfis.includes(perfil))
}
