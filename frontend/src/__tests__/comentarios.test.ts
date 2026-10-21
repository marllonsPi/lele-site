import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ComentariosView from '../views/ComentariosView.vue'
import type { ComentarioSite } from '../api/comentarios'

const m = vi.hoisted(() => ({ listarComentarios: vi.fn(), aprovarComentario: vi.fn(), rejeitarComentario: vi.fn() }))
vi.mock('../api/comentarios', async (orig) => ({ ...(await orig<typeof import('../api/comentarios')>()), ...m }))

const c = (p: Partial<ComentarioSite> = {}): ComentarioSite => ({
  id: 1, nome: 'Ana', email: 'ana@t.test', mensagem: 'Muito bom o atendimento', estado: 'PENDENTE',
  criadoEm: '2026-10-21T12:00:00Z', decididoEm: null, decisaoVia: null, avisoEnviado: true, ...p,
})

describe('ComentariosView', () => {
  beforeEach(() => {
    Object.values(m).forEach((f) => f.mockReset())
    m.listarComentarios.mockResolvedValue([c(), c({ id: 2, nome: 'Bia', avisoEnviado: false })])
  })

  it('lista pendentes e aprova pelo botao, recarregando a fila', async () => {
    m.aprovarComentario.mockResolvedValue({})
    const w = mount(ComentariosView)
    await flushPromises()
    expect(w.text()).toContain('Muito bom o atendimento')
    expect(w.text()).toContain('SMTP não configurado') // aviso so no comentario sem e-mail enviado
    await w.findAll('button').find((b) => b.text() === 'Aprovar e publicar')!.trigger('click')
    await flushPromises()
    expect(m.aprovarComentario).toHaveBeenCalledWith(1)
    expect(m.listarComentarios).toHaveBeenCalledTimes(2)
    expect(w.find('[role=status]').text()).toContain('já aparece no site')
  })

  it('rejeitar chama a rejeicao', async () => {
    m.rejeitarComentario.mockResolvedValue({})
    const w = mount(ComentariosView)
    await flushPromises()
    await w.findAll('button').find((b) => b.text() === 'Rejeitar')!.trigger('click')
    await flushPromises()
    expect(m.rejeitarComentario).toHaveBeenCalledWith(1)
  })
})
