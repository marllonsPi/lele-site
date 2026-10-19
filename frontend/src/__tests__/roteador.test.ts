import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { criarRoteador } from '../router'
import { useSessao } from '../composables/useSessao'
import { itensPara, ITENS_MENU } from '../router/navegacao'

const fetchMock = vi.fn()

function comSessao(autenticado: boolean) {
  fetchMock.mockResolvedValue(
    autenticado
      ? new Response(JSON.stringify({ id: 1, nome: 'Socia A', email: 'a@b.test', perfil: 'SOCIA' }), { status: 200 })
      : new Response(null, { status: 401 }),
  )
}

describe('roteador', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
    useSessao().reiniciar()
  })

  it('sem sessao, redireciona ao login guardando a pagina pedida', async () => {
    comSessao(false)
    const r = criarRoteador(createMemoryHistory())
    await r.push('/prazos')
    expect(r.currentRoute.value.name).toBe('login')
    expect(r.currentRoute.value.query.volta).toBe('/prazos')
  })

  it('com sessao, entra no inicio e o login redireciona para ele', async () => {
    comSessao(true)
    const r = criarRoteador(createMemoryHistory())
    await r.push('/')
    expect(r.currentRoute.value.name).toBe('inicio')
    await r.push('/login')
    expect(r.currentRoute.value.name).toBe('inicio')
  })

  it('com sessao, rota desconhecida cai na pagina nao encontrada', async () => {
    comSessao(true)
    const r = criarRoteador(createMemoryHistory())
    await r.push('/nao-existe')
    expect(r.currentRoute.value.name).toBe('nao-encontrada')
  })

  it('menu filtra itens pelo perfil', () => {
    expect(itensPara('ESTAGIARIO').length).toBe(ITENS_MENU.filter((i) => !i.perfis || i.perfis.includes('ESTAGIARIO')).length)
  })
})
