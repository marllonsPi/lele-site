import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AtendimentoView from '../views/AtendimentoView.vue'
import FinanceiroView from '../views/FinanceiroView.vue'
import { formatarMoeda } from '../utils/moeda'
import type { Eu } from '../api/sessao'
import { logarComo } from './helpers'

const m = vi.hoisted(() => ({
  listarAtendimentos: vi.fn(),
  registrarAtendimento: vi.fn(),
  listarAndamentosPendentes: vi.fn(),
  registrarAndamento: vi.fn(),
  marcarRepassado: vi.fn(),
  listarClientes: vi.fn(),
  listarProcessos: vi.fn(),
  resumoDoCliente: vi.fn(),
  listarContratos: vi.fn(),
  listarParcelas: vi.fn(),
  criarContrato: vi.fn(),
  pagarParcela: vi.fn(),
  registrarDespesa: vi.fn(),
  registrarHonorarioVariavel: vi.fn(),
}))
vi.mock('../api/atendimento', async (o) => ({ ...(await o<typeof import('../api/atendimento')>()), ...m }))
vi.mock('../api/financeiro', async (o) => ({ ...(await o<typeof import('../api/financeiro')>()), ...m }))
vi.mock('../api/cadastros', async (o) => ({ ...(await o<typeof import('../api/cadastros')>()), listarClientes: m.listarClientes, listarProcessos: m.listarProcessos }))

const SOCIA: Eu = { id: 1, nome: 'Sócia A', email: 'a@t.test', perfil: 'SOCIA' }

async function montar(componente: object) {
  await logarComo(SOCIA)
  await flushPromises()
  const w = mount(componente)
  await flushPromises()
  return w
}
const botao = (w: ReturnType<typeof mount>, texto: string) => w.findAll('button').find((b) => b.text() === texto)!

beforeEach(() => {
  Object.values(m).forEach((f) => f.mockReset())
  m.listarClientes.mockResolvedValue([{ id: 1, nome: 'Maria Cliente', ativo: true }])
  m.listarProcessos.mockResolvedValue([{ id: 5, numeroFormatado: '0000001-31.2025.8.07.0001', clienteId: 1, clienteNome: 'Maria Cliente', ativo: true }])
})

describe('AtendimentoView', () => {
  beforeEach(() => {
    m.listarAtendimentos.mockResolvedValue([
      { id: 1, clienteId: 1, processoId: null, momento: '2026-10-19T13:00:00Z', canal: 'TELEFONE', atendenteId: 1, resumo: 'Ligou pedindo notícias', origem: 'AVULSO', agendamentoId: null },
    ])
    m.listarAndamentosPendentes.mockResolvedValue([
      { id: 9, processoId: 5, data: '2026-10-18', descricao: 'Sentença publicada', origem: 'PUBLICACAO', repassado: false, repassadoEm: null, repassadoVia: null },
    ])
  })

  it('lista atendimentos e andamentos pendentes com nomes', async () => {
    const w = await montar(AtendimentoView)
    expect(w.text()).toContain('Ligou pedindo notícias')
    expect(w.text()).toContain('Maria Cliente')
    expect(w.text()).toContain('Sentença publicada')
    expect(w.text()).toContain('0000001-31.2025.8.07.0001')
  })

  it('canal de repasse comeca em e-mail', async () => {
    m.marcarRepassado.mockResolvedValue({})
    const w = await montar(AtendimentoView)
    expect((w.find('article select').element as HTMLSelectElement).value).toBe('EMAIL')
    await botao(w, 'Marcar repassado').trigger('click')
    await flushPromises()
    expect(m.marcarRepassado).toHaveBeenCalledWith(9, 'EMAIL')
  })

  it('marca andamento repassado pelo canal escolhido', async () => {
    m.marcarRepassado.mockResolvedValue({})
    const w = await montar(AtendimentoView)
    await w.find('article select').setValue('WHATSAPP')
    await botao(w, 'Marcar repassado').trigger('click')
    await flushPromises()
    expect(m.marcarRepassado).toHaveBeenCalledWith(9, 'WHATSAPP')
    expect(m.listarAndamentosPendentes).toHaveBeenCalledTimes(2)
  })

  it('registra atendimento avulso', async () => {
    m.registrarAtendimento.mockResolvedValue({})
    const w = await montar(AtendimentoView)
    await botao(w, 'Registrar atendimento').trigger('click')
    const f = w.find('form.formulario')
    await f.find('select').setValue(1)
    await f.find('textarea').setValue('Reunião rápida')
    await f.trigger('submit')
    await flushPromises()
    expect(m.registrarAtendimento).toHaveBeenCalledWith(
      expect.objectContaining({ clienteId: 1, processoId: null, canal: 'TELEFONE', resumo: 'Reunião rápida', atendenteId: 1 }),
    )
  })

  it('registra andamento manual', async () => {
    m.registrarAndamento.mockResolvedValue({})
    const w = await montar(AtendimentoView)
    await botao(w, 'Registrar andamento').trigger('click')
    const f = w.find('form.formulario')
    await f.find('select').setValue(5)
    await f.find('textarea').setValue('Juntada de petição')
    await f.trigger('submit')
    await flushPromises()
    expect(m.registrarAndamento).toHaveBeenCalledWith(expect.objectContaining({ processoId: 5, descricao: 'Juntada de petição' }))
  })
})

describe('FinanceiroView', () => {
  beforeEach(() => {
    m.resumoDoCliente.mockResolvedValue({
      contratado: 3000, parcelasPagas: 1000, parcelasAVencer: 1500, parcelasEmAtraso: 500, quantidadeEmAtraso: 1,
      custasEDespesas: 200, reembolsavel: 100, exitoPrevisto: 0, exitoRecebido: 0, sucumbenciaPrevista: 0, sucumbenciaRecebida: 0,
    })
    m.listarContratos.mockResolvedValue([{ id: 2, cliente_id: 1, processo_id: null, descricao: 'Honorários iniciais', valor_total: 3000, ativo: true }])
    m.listarParcelas.mockResolvedValue([
      { id: 11, numero: 1, vencimento: '2026-09-01', valor: 1000, pago_em: '2026-09-02', valor_pago: 1000, situacao: 'PAGA' },
      { id: 12, numero: 2, vencimento: '2026-10-01', valor: 500, pago_em: null, valor_pago: null, situacao: 'EM_ATRASO' },
    ])
  })

  async function escolherCliente(w: ReturnType<typeof mount>) {
    await w.find('select').setValue(1)
    await flushPromises()
  }

  it('mostra resumo, contratos e parcelas em reais', async () => {
    const w = await montar(FinanceiroView)
    await escolherCliente(w)
    expect(m.resumoDoCliente).toHaveBeenCalledWith(1)
    expect(w.text()).toContain('Parcelas em atraso (1)')
    expect(w.text()).toContain(formatarMoeda(500))
    expect(w.text()).toContain('Honorários iniciais')
    expect(w.text()).toContain('Em atraso')
  })

  it('so parcela nao paga oferece pagamento; confirma com data e valor', async () => {
    m.pagarParcela.mockResolvedValue(undefined)
    const w = await montar(FinanceiroView)
    await escolherCliente(w)
    const links = w.findAll('button').filter((b) => b.text() === 'registrar pagamento')
    expect(links).toHaveLength(1)
    await links[0]!.trigger('click')
    await w.find('td form input[type=date]').setValue('2026-10-19')
    await w.find('td form').trigger('submit')
    await flushPromises()
    expect(m.pagarParcela).toHaveBeenCalledWith(12, '2026-10-19', 500)
    expect(m.resumoDoCliente).toHaveBeenCalledTimes(2)
  })

  it('cria contrato parcelado', async () => {
    m.criarContrato.mockResolvedValue({ id: 3 })
    const w = await montar(FinanceiroView)
    await escolherCliente(w)
    await botao(w, 'Novo contrato').trigger('click')
    const f = w.find('form.formulario')
    await f.find('input').setValue('Contrato novo')
    await f.find('input[type=number]').setValue(1200)
    await f.findAll('select')[1]!.setValue('CIVEL')
    await f.trigger('submit')
    await flushPromises()
    expect(m.criarContrato).toHaveBeenCalledWith(expect.objectContaining({ clienteId: 1, descricao: 'Contrato novo', valorTotal: 1200, parcelas: 1, area: 'CIVEL' }))
  })

  it('registra despesa e honorario variavel', async () => {
    m.registrarDespesa.mockResolvedValue({ id: 1 })
    m.registrarHonorarioVariavel.mockResolvedValue({ id: 1 })
    const w = await montar(FinanceiroView)
    await escolherCliente(w)
    await botao(w, 'Registrar despesa').trigger('click')
    let f = w.find('form.formulario')
    await f.findAll('input')[0]!.setValue('Taxa judiciária')
    await f.find('input[type=number]').setValue(80)
    await f.trigger('submit')
    await flushPromises()
    expect(m.registrarDespesa).toHaveBeenCalledWith(expect.objectContaining({ tipo: 'CUSTAS', descricao: 'Taxa judiciária', valor: 80, reembolsavel: false }))

    await botao(w, 'Honorário de êxito/sucumbência').trigger('click')
    f = w.find('form.formulario')
    await f.find('input').setValue('Êxito na ação')
    await f.find('input[type=number]').setValue(5000)
    await f.trigger('submit')
    await flushPromises()
    expect(m.registrarHonorarioVariavel).toHaveBeenCalledWith(expect.objectContaining({ tipo: 'EXITO', valorPrevisto: 5000 }))
  })
})
