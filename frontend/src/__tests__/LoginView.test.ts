import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LoginView from '../views/LoginView.vue'

vi.mock('../api/sessao', () => ({ entrar: vi.fn() }))
import { entrar } from '../api/sessao'

async function preencherEEnviar(email: string, senha: string) {
  const w = mount(LoginView)
  await w.find('input[type=email]').setValue(email)
  await w.find('input[type=password]').setValue(senha)
  await w.find('form').trigger('submit')
  await flushPromises()
  return w
}

describe('LoginView', () => {
  beforeEach(() => {
    vi.mocked(entrar).mockReset()
  })

  it('emite autenticado quando o login e aceito', async () => {
    vi.mocked(entrar).mockResolvedValue(true)
    const w = await preencherEEnviar('a@b.test', 'segredo')
    expect(w.emitted('autenticado')).toHaveLength(1)
    expect(w.find('[role=alert]').exists()).toBe(false)
  })

  it('mostra erro e nao emite quando as credenciais sao invalidas', async () => {
    vi.mocked(entrar).mockResolvedValue(false)
    const w = await preencherEEnviar('a@b.test', 'errada')
    expect(w.emitted('autenticado')).toBeUndefined()
    expect(w.find('[role=alert]').text()).toContain('inválidos')
  })

  it('mostra erro generico quando a API falha', async () => {
    vi.mocked(entrar).mockRejectedValue(new Error('rede'))
    const w = await preencherEEnviar('a@b.test', 'x')
    expect(w.find('[role=alert]').text()).toContain('Tente novamente')
  })
})
