import { computed, ref } from 'vue'
import { compararTexto, ordenar, type Direcao } from '../utils/ordenacao'

export interface CriterioOrdem<T> {
  chave: string
  rotulo: string
  valor: (item: T) => string | number | null | undefined
}

export interface Agrupador<T> {
  chave: string
  rotulo: string
  /** Nome do grupo em que o item cai; vazio vai para "Sem informacao". */
  valor: (item: T) => string | null | undefined
}

export interface Grupo<T> {
  titulo: string
  itens: T[]
}

/**
 * Organizacao escolhida pela pessoa para uma lista: ordenar por um criterio (crescente/decrescente) e,
 * opcionalmente, agrupar. Lembrada neste navegador por tela. O primeiro criterio e o padrao.
 * Sem armazenamento disponivel, apenas nao lembra.
 */
export function useOrdenacao<T>(tela: string, criterios: CriterioOrdem<T>[], agrupadores: Agrupador<T>[] = []) {
  const guardado = ler(`ordem:${tela}`)
  const chave = ref(criterios.some((c) => c.chave === guardado.chave) ? guardado.chave : criterios[0]!.chave)
  const direcao = ref<Direcao>(guardado.direcao)
  const agrupar = ref(agrupadores.some((a) => a.chave === guardado.grupo) ? guardado.grupo : '')

  function ler(k: string): { chave: string; direcao: Direcao; grupo: string } {
    try {
      const v = JSON.parse(localStorage.getItem(k) ?? 'null') as { chave?: string; direcao?: string; grupo?: string } | null
      return { chave: v?.chave ?? '', direcao: v?.direcao === 'desc' ? 'desc' : 'asc', grupo: v?.grupo ?? '' }
    } catch {
      return { chave: '', direcao: 'asc', grupo: '' }
    }
  }

  function lembrar() {
    try {
      localStorage.setItem(`ordem:${tela}`, JSON.stringify({ chave: chave.value, direcao: direcao.value, grupo: agrupar.value }))
    } catch {
      /* sem armazenamento: a organizacao vale so nesta visita */
    }
  }

  function definir(novaChave: string) {
    if (novaChave === chave.value) {
      direcao.value = direcao.value === 'asc' ? 'desc' : 'asc'
    } else {
      chave.value = novaChave
      direcao.value = 'asc'
    }
    lembrar()
  }

  function inverter() {
    direcao.value = direcao.value === 'asc' ? 'desc' : 'asc'
    lembrar()
  }

  function definirGrupo(novo: string) {
    agrupar.value = novo
    lembrar()
  }

  function aplicarEscolha(novaChave: string, novaDirecao: Direcao, novoGrupo: string) {
    chave.value = criterios.some((c) => c.chave === novaChave) ? novaChave : criterios[0]!.chave
    direcao.value = novaDirecao
    agrupar.value = agrupadores.some((a) => a.chave === novoGrupo) ? novoGrupo : ''
    lembrar()
  }

  function restaurar() {
    chave.value = criterios[0]!.chave
    direcao.value = 'asc'
    agrupar.value = ''
    lembrar()
  }

  const criterio = computed(() => criterios.find((c) => c.chave === chave.value) ?? criterios[0]!)
  const agrupadorAtual = computed(() => agrupadores.find((a) => a.chave === agrupar.value) ?? null)
  const personalizada = computed(() => chave.value !== criterios[0]!.chave || direcao.value !== 'asc' || agrupar.value !== '')

  function aplicar(lista: readonly T[]): T[] {
    return ordenar(lista, criterio.value.valor, direcao.value)
  }

  /** Lista ja ordenada, repartida em grupos (um unico grupo sem titulo quando nao ha agrupamento). */
  function grupos(lista: readonly T[]): Grupo<T>[] {
    const ordenada = aplicar(lista)
    const ag = agrupadorAtual.value
    if (!ag) {
      return [{ titulo: '', itens: ordenada }]
    }
    const mapa = new Map<string, T[]>()
    for (const item of ordenada) {
      const nome = (ag.valor(item) ?? '').trim() || 'Sem informação'
      mapa.set(nome, [...(mapa.get(nome) ?? []), item])
    }
    return [...mapa.entries()]
      .sort(([a], [b]) => (a === 'Sem informação' ? 1 : b === 'Sem informação' ? -1 : compararTexto(a, b)))
      .map(([titulo, itens]) => ({ titulo, itens }))
  }

  return { chave, direcao, agrupar, criterios, agrupadores, definir, inverter, definirGrupo, aplicarEscolha, restaurar, aplicar, grupos, personalizada }
}

export type Ordenacao<T = never> = ReturnType<typeof useOrdenacao<T>>
