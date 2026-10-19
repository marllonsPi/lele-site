import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ConferenciaView from '../views/ConferenciaView.vue'
import { item, logarComo } from './helpers'

const m = vi.hoisted(() => ({
  carregarPendentesConferencia: vi.fn(),
  aprovarSupervisor: vi.fn(),
  devolverSupervisor: vi.fn(),
  conferirSocia: vi.fn(),
  devolverSocia: vi.fn(),
}))
vi.mock('../api/agenda', async (original) => ({ ...(await original<typeof import('../api/agenda')>()), ...m }))

async function montar() {
  await logarComo({ id: 1, nome: 'Sócia A', email: 'a@t.test', perfil: 'SOCIA' })
  await flushPromises()
  const w = mount(ConferenciaView)
  await flushPromises()
  return w
}

const botao = (w: Awaited<ReturnType<typeof montar>>, texto: string) => w.findAll('button').find((b) => b.text() === texto)!

describe('ConferenciaView', () => {
  beforeEach(() => {
    Object.values(m).forEach((f) => f.mockReset())
    m.carregarPendentesConferencia.mockResolvedValue([
      item({ id: 1, status: 'EM_CONFERENCIA_SUPERVISOR' }),
      item({ id: 2, status: 'EM_CONFERENCIA_SOCIA' }),
    ])
  })

  it('mostra a acao certa para cada etapa', async () => {
    const w = await montar()
    const cartoes = w.findAll('article')
    expect(cartoes[0]!.text()).toContain('Aprovar (supervisor)')
    expect(cartoes[1]!.text()).toContain('Conferido')
  })

  it('aprova como supervisor e confere como socia', async () => {
    m.aprovarSupervisor.mockResolvedValue({})
    m.conferirSocia.mockResolvedValue({})
    const w = await montar()
    await botao(w, 'Aprovar (supervisor)').trigger('click')
    await flushPromises()
    expect(m.aprovarSupervisor).toHaveBeenCalledWith(1)
    await botao(w, 'Conferido').trigger('click')
    await flushPromises()
    expect(m.conferirSocia).toHaveBeenCalledWith(2)
  })

  it('devolver exige motivo e usa o endpoint da etapa', async () => {
    m.devolverSocia.mockResolvedValue({})
    m.devolverSupervisor.mockResolvedValue({})
    const w = await montar()
    await w.findAll('button').filter((b) => b.text() === 'Devolver')[1]!.trigger('click')
    await w.find('textarea').setValue('Faltou anexo')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(m.devolverSocia).toHaveBeenCalledWith(2, 'Faltou anexo')
    expect(m.devolverSupervisor).not.toHaveBeenCalled()

    await w.findAll('button').filter((b) => b.text() === 'Devolver')[0]!.trigger('click')
    await w.find('textarea').setValue('Refazer')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(m.devolverSupervisor).toHaveBeenCalledWith(1, 'Refazer')
  })

  it('motivo em branco nao envia', async () => {
    const w = await montar()
    await w.findAll('button').filter((b) => b.text() === 'Devolver')[0]!.trigger('click')
    await w.find('textarea').setValue('   ')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(m.devolverSupervisor).not.toHaveBeenCalled()
  })

  it('mostra o erro do servidor', async () => {
    const { ApiError } = await import('../api/http')
    m.conferirSocia.mockRejectedValue(new ApiError(403, 'Sem permissao'))
    const w = await montar()
    await botao(w, 'Conferido').trigger('click')
    await flushPromises()
    expect(w.find('[role=alert]').text()).toBe('Sem permissao')
  })
})
