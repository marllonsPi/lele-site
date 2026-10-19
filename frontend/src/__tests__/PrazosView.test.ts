import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PrazosView from '../views/PrazosView.vue'
import { logarComo, prazo } from './helpers'
import type { Eu } from '../api/sessao'

const m = vi.hoisted(() => ({
  carregarPrazos: vi.fn(),
  carregarProcessos: vi.fn(),
  conferirPrazo: vi.fn(),
  lancarPrazo: vi.fn(),
}))
vi.mock('../api/agenda', async (original) => ({ ...(await original<typeof import('../api/agenda')>()), ...m }))

const socia: Eu = { id: 1, nome: 'Sócia A', email: 'a@t.test', perfil: 'SOCIA' }

async function montar(eu: Eu) {
  await logarComo(eu)
  await flushPromises()
  const w = mount(PrazosView)
  await flushPromises()
  return w
}

describe('PrazosView (dupla conferencia)', () => {
  beforeEach(() => {
    Object.values(m).forEach((f) => f.mockReset())
    m.carregarPrazos.mockResolvedValue([prazo({ lancadoPor: 1 }), prazo({ id: 21, lancadoPor: 2 })])
    m.carregarProcessos.mockResolvedValue([{ id: 1, numeroFormatado: '0000001-31.2025.8.07.0001', clienteNome: 'Cliente', tribunal: 'TJDFT', clienteId: 1, advogadoId: 3, ativo: true }])
  })

  it('quem lancou nao ve o botao Conferir; outro usuario ve', async () => {
    const w = await montar(socia)
    const botoes = w.findAll('article').map((a) => a.text().includes('Conferir contagem'))
    expect(botoes).toEqual([false, true])
  })

  it('estagiario nunca ve Conferir', async () => {
    const w = await montar({ id: 6, nome: 'Estagiário', email: 'f@t.test', perfil: 'ESTAGIARIO' })
    expect(w.text()).not.toContain('Conferir contagem')
  })

  it('confere e recarrega', async () => {
    m.conferirPrazo.mockResolvedValue({})
    const w = await montar(socia)
    await w.findAll('button').find((b) => b.text() === 'Conferir contagem')!.trigger('click')
    await flushPromises()
    expect(m.conferirPrazo).toHaveBeenCalledWith(21)
    expect(m.carregarPrazos).toHaveBeenCalledTimes(2)
  })

  it('lanca prazo e mostra o interno calculado pelo servidor', async () => {
    m.lancarPrazo.mockResolvedValue(prazo({ prazoInterno: '2026-10-20' }))
    const w = await montar(socia)
    await w.findAll('button').find((b) => b.text() === 'Lançar prazo')!.trigger('click')
    await flushPromises()
    await w.find('form select').setValue(1)
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(m.lancarPrazo).toHaveBeenCalledWith(expect.objectContaining({ processoId: 1, tipo: 'CON', responsavelId: 1 }))
    expect(w.find('[role=status]').text()).toContain('interno em 20/10/2026')
  })
})
