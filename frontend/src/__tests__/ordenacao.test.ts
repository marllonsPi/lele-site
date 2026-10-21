import { describe, expect, it } from 'vitest'
import { ordenar, porNome } from '../utils/ordenacao'

describe('ordenar', () => {
  it('ignora acento e caixa e usa ordem natural de numeros', () => {
    const nomes = ['Álvaro', 'beatriz', 'Zélia', 'Caso 10', 'Caso 2']
    expect(porNome(nomes, (n) => n)).toEqual(['Álvaro', 'beatriz', 'Caso 2', 'Caso 10', 'Zélia'])
  })

  it('deixa valores ausentes por ultimo nas duas direcoes e nao altera a lista original', () => {
    const lista = [{ c: null }, { c: 'b' }, { c: 'a' }]
    expect(ordenar(lista, (x) => x.c, 'asc').map((x) => x.c)).toEqual(['a', 'b', null])
    expect(ordenar(lista, (x) => x.c, 'desc').map((x) => x.c)).toEqual(['b', 'a', null])
    expect(lista[0]!.c).toBeNull()
  })
})

import { az, azPor, azRotulos } from '../utils/ordenacao'
import { useOrdenacao } from '../composables/useOrdenacao'

describe('combos e organizacao de listas', () => {
  it('combos saem de A a Z pelo texto exibido', () => {
    expect(az([{ n: 'Zeca' }, { n: 'ana' }, { n: 'Álvaro' }], (x) => x.n).map((x) => x.n)).toEqual(['Álvaro', 'ana', 'Zeca'])
    expect(azPor(['B', 'A'] as const, { A: 'Zebra', B: 'Abelha' })).toEqual(['B', 'A'])
    expect(azRotulos({ X: 'Gama', Y: 'Alfa' }).map(([, r]) => r)).toEqual(['Alfa', 'Gama'])
  })

  it('ordena, inverte ao clicar de novo e agrupa; "Sem informacao" vai por ultimo', () => {
    const o = useOrdenacao<{ n: string; g: string | null }>('teste-ord', [{ chave: 'n', rotulo: 'Nome', valor: (x) => x.n }], [
      { chave: 'g', rotulo: 'Grupo', valor: (x) => x.g },
    ])
    const lista = [{ n: 'b', g: 'Y' }, { n: 'a', g: null }, { n: 'c', g: 'X' }, { n: 'd', g: 'Y' }]
    expect(o.aplicar(lista).map((x) => x.n)).toEqual(['a', 'b', 'c', 'd'])
    o.definir('n')
    expect(o.direcao.value).toBe('desc')
    o.aplicarEscolha('n', 'asc', 'g')
    expect(o.grupos(lista).map((g) => [g.titulo, g.itens.map((x) => x.n)])).toEqual([['X', ['c']], ['Y', ['b', 'd']], ['Sem informação', ['a']]])
    o.restaurar()
    expect(o.grupos(lista)).toHaveLength(1)
  })
})
