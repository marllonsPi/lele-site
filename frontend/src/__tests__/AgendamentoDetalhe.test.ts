import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AgendamentoDetalhe from '../components/AgendamentoDetalhe.vue'
import { ApiError } from '../api/http'

const m = vi.hoisted(() => ({
  carregarObservacoes: vi.fn(),
  registrarObservacao: vi.fn(),
  carregarAnexos: vi.fn(),
  anexarArquivo: vi.fn(),
  carregarHistoricoConferencia: vi.fn(),
}))
vi.mock('../api/agenda', async (original) => ({ ...(await original<typeof import('../api/agenda')>()), ...m }))

describe('AgendamentoDetalhe', () => {
  beforeEach(() => {
    Object.values(m).forEach((f) => f.mockReset())
    m.carregarObservacoes.mockResolvedValue([{ id: 1, autorId: 3, texto: 'Primeira nota', criadoEm: '2026-10-19T12:00:00Z' }])
    m.carregarAnexos.mockResolvedValue([{ id: 7, nome: 'peca.pdf', tamanho: 2048, autorId: 3, criadoEm: '2026-10-19T12:00:00Z' }])
    m.carregarHistoricoConferencia.mockResolvedValue([
      { id: 1, etapa: 'SOCIA', resultado: 'DEVOLVIDO', motivo: 'Faltou a procuracao', usuarioId: 1, criadoEm: '2026-10-19T12:00:00Z' },
    ])
  })

  it('lista observacoes, anexos com link de download e historico com motivo', async () => {
    const w = mount(AgendamentoDetalhe, { props: { id: 10 } })
    await flushPromises()
    expect(w.text()).toContain('Primeira nota')
    expect(w.find('a[href="/api/anexos/7/download"]').text()).toBe('peca.pdf')
    expect(w.text()).toContain('Motivo: Faltou a procuracao')
  })

  it('registra observacao e recarrega a lista', async () => {
    m.registrarObservacao.mockResolvedValue({})
    const w = mount(AgendamentoDetalhe, { props: { id: 10 } })
    await flushPromises()
    await w.find('textarea').setValue('  Nova nota  ')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(m.registrarObservacao).toHaveBeenCalledWith(10, 'Nova nota')
    expect(m.carregarObservacoes).toHaveBeenCalledTimes(2)
    expect((w.find('textarea').element as HTMLTextAreaElement).value).toBe('')
  })

  it('envia o arquivo escolhido e mostra o erro do servidor', async () => {
    m.anexarArquivo.mockRejectedValue(new ApiError(400, 'Tipo de arquivo nao aceito'))
    const w = mount(AgendamentoDetalhe, { props: { id: 10 } })
    await flushPromises()
    const arquivo = new File(['x'], 'virus.exe')
    const campo = w.find('input[type=file]')
    Object.defineProperty(campo.element, 'files', { value: [arquivo], configurable: true })
    await campo.trigger('change')
    await flushPromises()
    expect(m.anexarArquivo).toHaveBeenCalledWith(10, arquivo)
    expect(w.find('[role=alert]').text()).toBe('Tipo de arquivo nao aceito')
  })
})
