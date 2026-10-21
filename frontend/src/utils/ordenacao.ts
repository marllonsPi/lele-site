export type Direcao = 'asc' | 'desc'

/** Compara textos sem diferenciar maiusculas nem acentos, com numeros em ordem natural (processo 2 antes de 10). */
export function compararTexto(a: string | null | undefined, b: string | null | undefined): number {
  return (a ?? '').localeCompare(b ?? '', 'pt-BR', { sensitivity: 'base', numeric: true })
}

/** Ordena sem alterar a lista original; itens com valor ausente ficam sempre por ultimo. */
export function ordenar<T>(lista: readonly T[], valor: (item: T) => string | number | null | undefined, direcao: Direcao = 'asc'): T[] {
  const sinal = direcao === 'asc' ? 1 : -1
  return [...lista].sort((a, b) => {
    const va = valor(a)
    const vb = valor(b)
    if (va == null || va === '') {
      return vb == null || vb === '' ? 0 : 1
    }
    if (vb == null || vb === '') {
      return -1
    }
    const c = typeof va === 'number' && typeof vb === 'number' ? va - vb : compararTexto(String(va), String(vb))
    return c * sinal
  })
}

/** Ordem alfabetica para combos e listas de apoio. */
export const porNome = <T>(lista: readonly T[], nome: (item: T) => string): T[] => ordenar(lista, nome)

/** Opcoes de combo em ordem alfabetica (A a Z) pelo texto exibido. */
export const az = <T>(lista: readonly T[], rotulo: (item: T) => string): T[] => ordenar(lista, rotulo)

/** Registro codigo -> rotulo como pares [codigo, rotulo] em ordem alfabetica do rotulo. */
export const azRotulos = <V extends string>(registro: Record<V, string>): [V, string][] =>
  (Object.entries(registro) as [V, string][]).sort(([, a], [, b]) => compararTexto(a, b))

/** Codigos em ordem alfabetica do rotulo que o registro lhes da. */
export const azPor = <V extends string>(codigos: readonly V[], registro: Record<V, string>): V[] => ordenar(codigos, (c) => registro[c])
