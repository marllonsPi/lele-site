import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import App from '../App.vue'
import { criarRoteador } from '../router'
import { useSessao } from '../composables/useSessao'

const fetchMock = vi.fn()

describe('layout', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
    useSessao().reiniciar()
    document.cookie = 'XSRF-TOKEN=t; path=/'
  })

  it('mostra nome e perfil, menu e sai voltando ao login', async () => {
    fetchMock.mockImplementation(async (url: string) =>
      url === '/api/me'
        ? new Response(JSON.stringify({ id: 1, nome: 'Socia A', email: 'a@b.test', perfil: 'SOCIA' }), { status: 200 })
        : new Response(null, { status: 204 }),
    )
    const r = criarRoteador(createMemoryHistory())
    await r.push('/')
    const w = mount(App, { global: { plugins: [r] } })
    await flushPromises()
    expect(w.text()).toContain('Socia A · Sócia')
    expect(w.find('nav[aria-label=Principal]').text()).toContain('Início')
    await w.findAll('button').find((b) => b.text() === 'Sair')!.trigger('click')
    await flushPromises()
    expect(r.currentRoute.value.name).toBe('login')
    expect(useSessao().eu.value).toBeNull()
  })

  it('botao de tema alterna e grava o atributo', async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ id: 1, nome: 'A', email: 'a@b.test', perfil: 'SOCIA' }), { status: 200 }),
    )
    const r = criarRoteador(createMemoryHistory())
    await r.push('/')
    const w = mount(App, { global: { plugins: [r] } })
    await flushPromises()
    await w.find('button[title^="Tema"]').trigger('click')
    expect(document.documentElement.dataset.tema).toBe('claro')
  })
})
