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
  { rota: '/painel', rotulo: 'Painel', perfis: ['SOCIA'] },
  { rota: '/agenda', rotulo: 'Agenda' },
  { rota: '/prazos', rotulo: 'Prazos' },
  // supervisores sao advogados; o servidor decide o que cada um ve na fila
  { rota: '/conferencia', rotulo: 'Conferência', perfis: ['SOCIA', 'ADVOGADO'] },
  { rota: '/publicacoes', rotulo: 'Publicações' },
  { rota: '/atendimento', rotulo: 'Atendimento' },
  { rota: '/clientes', rotulo: 'Clientes' },
  { rota: '/processos', rotulo: 'Processos' },
  { rota: '/relatorios', rotulo: 'Relatórios' },
  { rota: '/modelos', rotulo: 'Modelos' },
  { rota: '/financeiro', rotulo: 'Financeiro', perfis: ['SOCIA'] },
  { rota: '/calendario', rotulo: 'Calendário' },
  { rota: '/captura', rotulo: 'Captura', perfis: ['SOCIA'] },
  { rota: '/usuarios', rotulo: 'Usuários', perfis: ['SOCIA'] },
  { rota: '/visibilidade', rotulo: 'Visibilidade', perfis: ['SOCIA'] },
  { rota: '/seguranca', rotulo: 'Segurança' },
]

export function itensPara(perfil: Perfil): ItemMenu[] {
  return ITENS_MENU.filter((i) => !i.perfis || i.perfis.includes(perfil))
}
