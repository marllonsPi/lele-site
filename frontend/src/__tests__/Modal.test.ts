import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import Modal from '../components/Modal.vue'

const montar = (aberto: boolean) =>
  mount(Modal, { props: { aberto, titulo: 'Editar cliente', formId: 'f' }, slots: { default: '<form id="f"><input /></form>' }, global: { stubs: { teleport: true } } })

describe('Modal', () => {
  it('so renderiza aberto, com titulo e botoes de salvar e cancelar', () => {
    expect(montar(false).find('[role=dialog]').exists()).toBe(false)
    const w = montar(true)
    expect(w.find('[role=dialog]').attributes('aria-label')).toBe('Editar cliente')
    expect(w.find('button[type=submit]').attributes('form')).toBe('f')
    expect(w.text()).toContain('Cancelar')
  })

  it('fecha com Esc, Cancelar, X e clique no fundo', async () => {
    const w = montar(true)
    await w.find('[role=dialog]').trigger('keydown', { key: 'Escape' })
    await w.findAll('button').find((b) => b.text() === 'Cancelar')!.trigger('click')
    await w.find('.modal-fechar').trigger('click')
    await w.find('.modal-fundo').trigger('mousedown')
    expect(w.emitted('fechar')).toHaveLength(4)
  })
})
