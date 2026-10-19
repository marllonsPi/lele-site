import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import CartaoAgenda from '../components/CartaoAgenda.vue'
import { item } from './helpers'

vi.mock('../api/agenda', async (original) => ({
  ...(await original<typeof import('../api/agenda')>()),
  carregarObservacoes: vi.fn().mockResolvedValue([]),
  carregarAnexos: vi.fn().mockResolvedValue([]),
  carregarHistoricoConferencia: vi.fn().mockResolvedValue([]),
}))

describe('CartaoAgenda', () => {
  it('cor do cartao vem do status e a bolinha do tipo', () => {
    const w = mount(CartaoAgenda, { props: { item: item({ statusVisual: 'VERMELHO', tipo: 'AUD' }) } })
    expect(w.find('article').classes()).toContain('status-VERMELHO')
    expect(w.find('.bolinha').classes()).toContain('tipo-AUD')
  })

  it('mostra URGENTE, hora e duracao dos compromissos', () => {
    const w = mount(CartaoAgenda, {
      props: { item: item({ tipo: 'ATD', urgente: true, hora: '10:30:00', duracaoMin: 60, data: '2026-10-20' }) },
    })
    expect(w.text()).toContain('URGENTE')
    expect(w.text()).toContain('20/10/2026 10:30 (60 min)')
  })

  it('avisa audiencia proxima e prazo aguardando conferencia', () => {
    const w = mount(CartaoAgenda, {
      props: { item: item({ alertaAudiencia: true, aguardandoConferenciaPrazo: true }) },
    })
    expect(w.text()).toContain('Audiência em até 10 dias')
    expect(w.text()).toContain('Aguardando conferência da contagem')
  })

  it('abre e fecha os detalhes', async () => {
    const w = mount(CartaoAgenda, { props: { item: item() } })
    expect(w.find('.detalhe').exists()).toBe(false)
    await w.find('button[aria-expanded]').trigger('click')
    expect(w.find('.detalhe').exists()).toBe(true)
  })
})
