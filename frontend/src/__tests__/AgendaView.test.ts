import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AgendaView from '../views/AgendaView.vue'
import { item, logarComo } from './helpers'

const m = vi.hoisted(() => ({
  carregarAgenda: vi.fn(),
  finalizar: vi.fn(),
  marcarCompromisso: vi.fn(),
  carregarProcessos: vi.fn(),
  carregarClientes: vi.fn(),
  carregarObservacoes: vi.fn(),
  carregarAnexos: vi.fn(),
  carregarHistoricoConferencia: vi.fn(),
}))
vi.mock('../api/agenda', async (original) => ({ ...(await original<typeof import('../api/agenda')>()), ...m }))

async function montar(perfil: 'SOCIA' | 'ADVOGADO' = 'ADVOGADO') {
  await logarComo({ id: 3, nome: 'Advogada C', email: 'c@t.test', perfil })
  await flushPromises()
  const w = mount(AgendaView)
  await flushPromises()
  return w
}

describe('AgendaView', () => {
  beforeEach(() => {
    Object.values(m).forEach((f) => f.mockReset())
    m.carregarAgenda.mockResolvedValue([
      item({ id: 1, tipo: 'CON', descricao: 'Contestação' }),
      item({ id: 2, tipo: 'ATD', descricao: 'Consulta', hora: '09:00:00', status: 'PENDENTE' }),
      item({ id: 3, tipo: 'INI', status: 'AGUARDANDO_CONFERENCIA_PRAZO', aguardandoConferenciaPrazo: true }),
    ])
    m.carregarProcessos.mockResolvedValue([])
    m.carregarClientes.mockResolvedValue([])
  })

  it('lista os itens; prazo aguardando conferencia nao tem Finalizar', async () => {
    const w = await montar()
    const cartoes = w.findAll('article')
    expect(cartoes).toHaveLength(3)
    expect(cartoes[2]!.text()).not.toContain('Finalizar')
    expect(cartoes[0]!.text()).toContain('Finalizar')
  })

  it('finaliza prazo direto e atendimento pede o resumo', async () => {
    m.finalizar.mockResolvedValue({})
    const w = await montar()
    const cartoes = w.findAll('article')
    await cartoes[0]!.findAll('button').find((b) => b.text() === 'Finalizar')!.trigger('click')
    await flushPromises()
    expect(m.finalizar).toHaveBeenCalledWith(1, undefined)

    await cartoes[1]!.findAll('button').find((b) => b.text() === 'Finalizar')!.trigger('click')
    expect(m.finalizar).toHaveBeenCalledTimes(1)
    await w.find('textarea').setValue('Atendimento realizado.')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(m.finalizar).toHaveBeenLastCalledWith(2, 'Atendimento realizado.')
  })

  it('so socia escolhe a agenda de outra pessoa', async () => {
    expect((await montar('ADVOGADO')).find('select').exists()).toBe(false)
    expect((await montar('SOCIA')).text()).toContain('Ver agenda de')
  })

  it('marca audiencia e exibe aviso de conflito sem bloquear', async () => {
    m.carregarProcessos.mockResolvedValue([{ id: 5, numeroFormatado: '0000001-31.2025.8.07.0001', clienteNome: 'Cliente' }])
    m.marcarCompromisso.mockResolvedValue({
      item: item(),
      avisos: [{ tipo: 'MESMO_ADVOGADO', comTipo: 'AUD', inicio: '', fim: '' }],
    })
    const w = await montar()
    await w.findAll('button').find((b) => b.text().startsWith('Marcar'))!.trigger('click')
    await flushPromises()
    const form = w.find('form.formulario')
    await form.findAll('select')[1]!.setValue(5)
    await form.find('input[type=date]').setValue('2026-10-21')
    await form.find('input[type=time]').setValue('10:15')
    await form.trigger('submit')
    await flushPromises()
    expect(m.marcarCompromisso).toHaveBeenCalledWith(
      expect.objectContaining({ tipo: 'AUD', processoId: 5, responsavelId: 3, inicio: '2026-10-21T10:15:00', clienteId: null }),
    )
    expect(w.find('ul.alerta').text()).toContain('Conflito')
    expect(w.find('[role=status]').text()).toContain('Compromisso marcado')
  })
})
