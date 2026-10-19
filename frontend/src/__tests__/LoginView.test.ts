import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import LoginView from '../views/LoginView.vue'

const entrar = vi.fn()
vi.mock('../composables/useSessao', () => ({ useSessao: () => ({ entrar }) }))

async function montar(query: Record<string, string> = {}) {
  const roteador = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', component: LoginView },
      { path: '/', component: { template: '<p>inicio</p>' } },
      { path: '/prazos', component: { template: '<p>prazos</p>' } },
    ],
  })
  await roteador.push({ path: '/login', query })
  await roteador.isReady()
  const w = mount(LoginView, { global: { plugins: [roteador] } })
  return { w, roteador }
}

async function enviarCredenciais(w: Awaited<ReturnType<typeof montar>>['w']) {
  await w.find('input[type=email]').setValue('a@b.test')
  await w.find('input[type=password]').setValue('segredo')
  await w.find('form').trigger('submit')
  await flushPromises()
}

describe('LoginView', () => {
  beforeEach(() => {
    entrar.mockReset()
  })

  it('vai ao inicio quando o login e aceito', async () => {
    entrar.mockResolvedValue('ok')
    const { w, roteador } = await montar()
    await enviarCredenciais(w)
    expect(entrar).toHaveBeenCalledWith('a@b.test', 'segredo', undefined)
    expect(roteador.currentRoute.value.path).toBe('/')
  })

  it('volta para o caminho interno de origem', async () => {
    entrar.mockResolvedValue('ok')
    const { w, roteador } = await montar({ volta: '/prazos' })
    await enviarCredenciais(w)
    expect(roteador.currentRoute.value.path).toBe('/prazos')
  })

  it('ignora destino externo', async () => {
    entrar.mockResolvedValue('ok')
    const { w, roteador } = await montar({ volta: '//mal.test' })
    await enviarCredenciais(w)
    expect(roteador.currentRoute.value.path).toBe('/')
  })

  it('mostra erro quando as credenciais sao invalidas', async () => {
    entrar.mockResolvedValue('invalido')
    const { w, roteador } = await montar()
    await enviarCredenciais(w)
    expect(roteador.currentRoute.value.path).toBe('/login')
    expect(w.find('[role=alert]').text()).toContain('inválidos')
  })

  it('avisa sobre muitas tentativas', async () => {
    entrar.mockResolvedValue('muitas-tentativas')
    const { w } = await montar()
    await enviarCredenciais(w)
    expect(w.find('[role=alert]').text()).toContain('Muitas tentativas')
  })

  it('mostra erro generico quando a API falha', async () => {
    entrar.mockImplementation(async () => {
      throw new Error('rede')
    })
    const { w } = await montar()
    await enviarCredenciais(w)
    expect(w.find('[role=alert]').text()).toContain('Tente novamente')
  })

  it('pede o codigo do 2FA e reenvia com ele', async () => {
    entrar.mockResolvedValueOnce('precisa-codigo').mockResolvedValueOnce('ok')
    const { w, roteador } = await montar()
    await enviarCredenciais(w)
    expect(w.find('input[autocomplete=one-time-code]').exists()).toBe(true)
    expect(w.find('input[type=email]').exists()).toBe(false)
    await w.find('input[autocomplete=one-time-code]').setValue('123456')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(entrar).toHaveBeenLastCalledWith('a@b.test', 'segredo', '123456')
    expect(roteador.currentRoute.value.path).toBe('/')
  })

  it('codigo errado mostra erro e continua pedindo o codigo', async () => {
    entrar.mockResolvedValueOnce('precisa-codigo').mockResolvedValueOnce('invalido')
    const { w } = await montar()
    await enviarCredenciais(w)
    await w.find('input[autocomplete=one-time-code]').setValue('000000')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(w.find('[role=alert]').text()).toContain('Código inválido')
    expect(w.find('input[autocomplete=one-time-code]').exists()).toBe(true)
  })
})
