import type { Perfil } from '../api/sessao'

export interface ItemMenu {
  rota: string
  rotulo: string
  /** Secao do menu lateral. */
  grupo: string
  /** Vazio = todos os perfis. A UI so esconde; o servidor decide. */
  perfis?: Perfil[]
  /** So o acesso mestre. */
  mestre?: boolean
}

/** Menu principal; cada parte do frontend acrescenta suas telas aqui. */
export const ITENS_MENU: ItemMenu[] = [
  { rota: '/', rotulo: 'Início', grupo: 'Dia a dia' },
  { rota: '/painel', rotulo: 'Painel', grupo: 'Dia a dia', perfis: ['SOCIA'] },
  { rota: '/agenda', rotulo: 'Agenda', grupo: 'Dia a dia' },
  { rota: '/prazos', rotulo: 'Prazos', grupo: 'Dia a dia' },
  // supervisores sao advogados; o servidor decide o que cada um ve na fila
  { rota: '/conferencia', rotulo: 'Conferência', grupo: 'Dia a dia', perfis: ['SOCIA', 'ADVOGADO'] },
  { rota: '/publicacoes', rotulo: 'Publicações', grupo: 'Dia a dia' },
  { rota: '/atendimento', rotulo: 'Atendimento', grupo: 'Dia a dia' },
  { rota: '/clientes', rotulo: 'Clientes', grupo: 'Cadastros' },
  { rota: '/processos', rotulo: 'Processos', grupo: 'Cadastros' },
  { rota: '/modelos', rotulo: 'Modelos', grupo: 'Cadastros' },
  { rota: '/calendario', rotulo: 'Calendário', grupo: 'Cadastros' },
  { rota: '/relatorios', rotulo: 'Relatórios', grupo: 'Gestão' },
  { rota: '/financeiro', rotulo: 'Financeiro', grupo: 'Gestão', perfis: ['SOCIA'] },
  { rota: '/honorarios', rotulo: 'Honorários', grupo: 'Gestão', perfis: ['SOCIA'] },
  { rota: '/captura', rotulo: 'Captura', grupo: 'Gestão', perfis: ['SOCIA'] },
  { rota: '/comentarios', rotulo: 'Comentários do site', grupo: 'Gestão', perfis: ['SOCIA'] },
  { rota: '/drive', rotulo: 'Drive do escritório', grupo: 'Gestão', perfis: ['SOCIA'] },
  { rota: '/importacao', rotulo: 'Importação (suporte)', grupo: 'Administração', perfis: ['SOCIA'], mestre: true },
  { rota: '/usuarios', rotulo: 'Contas e OABs', grupo: 'Administração', perfis: ['SOCIA'] },
  { rota: '/visibilidade', rotulo: 'Visibilidade', grupo: 'Administração', perfis: ['SOCIA'] },
  { rota: '/sistema', rotulo: 'Sistema', grupo: 'Administração', perfis: ['SOCIA'], mestre: true },
  { rota: '/seguranca', rotulo: 'Minha conta', grupo: 'Conta' },
]

export function itensPara(perfil: Perfil, mestre = false): ItemMenu[] {
  return ITENS_MENU.filter((i) => (!i.perfis || i.perfis.includes(perfil)) && (!i.mestre || mestre))
}

/** Agrupa mantendo a ordem do menu. */
export function porGrupo(itens: ItemMenu[]): { grupo: string; itens: ItemMenu[] }[] {
  const saida: { grupo: string; itens: ItemMenu[] }[] = []
  for (const i of itens) {
    const g = saida.find((x) => x.grupo === i.grupo)
    if (g) {
      g.itens.push(i)
    } else {
      saida.push({ grupo: i.grupo, itens: [i] })
    }
  }
  return saida
}
