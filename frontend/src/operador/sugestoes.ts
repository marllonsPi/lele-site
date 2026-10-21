import type { Comando } from './comandos'

export interface Contexto {
  rota: string
  /** Publicacoes aguardando classificacao (so quem enxerga a fila). */
  aClassificar: number
  paraConferir?: number
  fataisProximos?: number
}

export interface Sugestao {
  texto: string
  comando: string
}

/** O que o assistente oferece agora, pelo lugar em que a pessoa esta e pelo que esta pendente. */
export function sugestoesPara(ctx: Contexto, comandos: Comando[]): Sugestao[] {
  const tem = (id: string) => comandos.some((c) => c.id === id)
  const lista: Sugestao[] = []
  if (ctx.aClassificar > 0 && ctx.rota !== '/publicacoes' && tem('ir:/publicacoes')) {
    lista.push({ texto: `Há ${ctx.aClassificar} publicação(ões) a classificar. Quer ver?`, comando: 'ir:/publicacoes' })
  }
  if ((ctx.paraConferir ?? 0) > 0 && ctx.rota !== '/conferencia' && tem('ir:/conferencia')) {
    lista.push({ texto: `${ctx.paraConferir} prazo(s) lançado(s) por outra pessoa aguardam sua conferência. Quer conferir?`, comando: 'ir:/conferencia' })
  }
  if ((ctx.fataisProximos ?? 0) > 0 && ctx.rota !== '/prazos' && tem('ir:/prazos')) {
    lista.push({ texto: `${ctx.fataisProximos} prazo(s) fatal(is) vencem hoje ou amanhã. Quer ver?`, comando: 'ir:/prazos' })
  }
  if (ctx.rota === '/publicacoes' && tem('captura-agora')) {
    lista.push({ texto: 'Quer que eu busque publicações novas agora?', comando: 'captura-agora' })
  }
  if (ctx.rota === '/captura' && tem('captura-ligar')) {
    lista.push({ texto: 'Posso ligar a captura diária para você?', comando: 'captura-ligar' })
  }
  if (ctx.rota.startsWith('/processos') && tem('atalho:Lançar prazo')) {
    lista.push({ texto: 'Quer lançar um prazo para um processo?', comando: 'atalho:Lançar prazo' })
  }
  if (ctx.rota.startsWith('/prazos') && tem('ir:/conferencia')) {
    lista.push({ texto: 'Quer conferir os prazos que aguardam conferência?', comando: 'ir:/conferencia' })
  }
  if (ctx.rota === '/' && tem('ir:/agenda')) {
    lista.push({ texto: 'Quer ver a agenda de hoje?', comando: 'ir:/agenda' })
  }
  return lista
}
