import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { criarRoteador } from '../router'
import { useSessao } from '../composables/useSessao'
import { itensPara } from '../router/navegacao'

const fetchMock = vi.fn()

function comSessao(mestre: boolean, perfil = 'SOCIA') {
  fetchMock.mockResolvedValue(new Response(JSON.stringify({ id: 1, nome: 'Marllons', email: 'm@t.test', perfil, mestre }), { status: 200 }))
}

describe('acesso mestre na interface', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
    useSessao().reiniciar()
  })

  it('o menu Sistema aparece so para o mestre', () => {
    expect(itensPara('SOCIA', false).map((i) => i.rota)).not.toContain('/sistema')
    expect(itensPara('SOCIA', true).map((i) => i.rota)).toContain('/sistema')
    expect(itensPara('ADVOGADO', true).map((i) => i.rota)).not.toContain('/sistema')
  })

  it('socia comum e barrada em /sistema; mestre entra', async () => {
    comSessao(false)
    const r = criarRoteador(createMemoryHistory())
    await r.push('/sistema')
    expect(r.currentRoute.value.name).toBe('inicio')

    useSessao().reiniciar()
    comSessao(true)
    const r2 = criarRoteador(createMemoryHistory())
    await r2.push('/sistema')
    expect(r2.currentRoute.value.name).toBe('sistema')
  })

  it('ehMestre reflete a sessao', async () => {
    comSessao(true)
    await useSessao().carregar()
    expect(useSessao().ehMestre()).toBe(true)
  })
})
