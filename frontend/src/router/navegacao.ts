import type { Perfil } from '../api/sessao'

export interface ItemMenu {
  rota: string
  rotulo: string
  /** Vazio = todos os perfis. A UI so esconde; o servidor decide. */
  perfis?: Perfil[]
}

/** Menu principal; cada parte do frontend acrescenta suas telas aqui. */
export const ITENS_MENU: ItemMenu[] = [{ rota: '/', rotulo: 'Início' }]

export function itensPara(perfil: Perfil): ItemMenu[] {
  return ITENS_MENU.filter((i) => !i.perfis || i.perfis.includes(perfil))
}
