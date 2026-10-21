import { flushPromises, mount, config } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import QuadroSugestao from '../components/QuadroSugestao.vue'
import HonorariosView from '../views/HonorariosView.vue'
import type { Proposta } from '../api/honorarios'
import { logarComo } from './helpers'

config.global.stubs = { RouterLink: true, teleport: true }

const m = vi.hoisted(() => ({
  carregarCatalogo: vi.fn(),
  propostaDoAtendimento: vi.fn(),
  sugerirNoAtendimento: vi.fn(),
  sugerirParaCliente: vi.fn(),
  recalcularProposta: vi.fn(),
  fecharProposta: vi.fn(),
  propostaNaoFechada: vi.fn(),
  assinarProposta: vi.fn(),
  listarVersoes: vi.fn(),
  listarItensTabela: vi.fn(),
  criarItemTabela: vi.fn(),
  atualizarItemTabela: vi.fn(),
  excluirItemTabela: vi.fn(),
  restaurarItemTabela: vi.fn(),
  atualizarUrh: vi.fn(),
  atualizarPolitica: vi.fn(),
  importarTabela: vi.fn(),
  carregarRelatorioHonorarios: vi.fn(),
  listarPropostas: vi.fn(),
  carregarModeloContrato: vi.fn(),
  salvarModeloContrato: vi.fn(),
}))
vi.mock('../api/honorarios', async (original) => ({ ...(await original<typeof import('../api/honorarios')>()), ...m }))
vi.mock('../api/cadastros', async (original) => ({ ...(await original<typeof import('../api/cadastros')>()), listarClientes: vi.fn(async () => []) }))

const SOCIA = { id: 1, nome: 'Sócia A', email: 'a@t.test', perfil: 'SOCIA' as const }
const CHAVE = 'abc'

const proposta = (p: Partial<Proposta> = {}): Proposta => ({
  id: 5, clienteId: 1, clienteNome: 'Cliente', agendamentoId: 9, advogadoId: 3, seccional: 'DF', status: 'ABERTA', valorMinimoOab: 15245.2, valorSugerido: 16464.82,
  descontoPacote: 0, valorFechado: null, justificativaTipo: null, justificativa: null, entrada: null, parcelas: null, primeiroVencimento: null, formaPagamento: null,
  dataAssinatura: null, contratoLancado: false, criadoEm: '2026-10-21T10:00:00Z', fechadoEm: null,
  itens: [{ id: 1, chave: CHAVE, area: 'FAMILIA', tipoServico: 'Divórcio consensual', fase: null, minimoOab: 15245.2, valorTabela: 18294.24, valorSugerido: 16464.82 }],
  quadro: {
    linhas: [{ servico: { chave: CHAVE, area: 'FAMILIA', tipo: 'Divórcio consensual', fase: null, exitoTexto: null }, minimoOab: 15245.2, valorTabela: 18294.24, valorSugerido: 16464.82, avisos: [] }],
    minimoOabTotal: 15245.2, valorTabelaTotal: 18294.24, valorSugeridoTotal: 16464.82, descontoPacote: 0, descontoPacoteAplicado: 0, exito: ['Divórcio: 5% sobre os bens'],
    opcoes: [
      { rotulo: 'A vista (com o desconto padrao)', total: 16464.82, entrada: 16464.82, parcelas: 0, valorParcela: 0, ultimaParcela: 0 },
      { rotulo: 'Entrada de 30% + 6 parcelas', total: 18294.24, entrada: 5488.27, parcelas: 6, valorParcela: 2134.32, ultimaParcela: 2134.35 },
    ],
    avisos: [],
  },
  abaixoDaOab: false, diferencaAbaixoOab: 0, tabelaOabVigente: true, ...p,
})

beforeEach(async () => {
  Object.values(m).forEach((f) => f.mockReset())
  m.carregarCatalogo.mockResolvedValue([{ chave: CHAVE, area: 'FAMILIA', tipoServico: 'Divórcio consensual', fase: null }])
  m.propostaDoAtendimento.mockResolvedValue(undefined)
  await logarComo(SOCIA, [])
  await flushPromises()
})


describe('QuadroSugestao', () => {
  it('pede area e servico, calcula e mostra minimo da OAB, valor do escritorio, exito e parcelamento', async () => {
    m.sugerirNoAtendimento.mockResolvedValue(proposta())
    const w = mount(QuadroSugestao, { props: { agendamentoId: 9, podeAssinar: true } })
    await flushPromises()
    const selects = w.findAll('select')
    await selects[1]!.setValue('FAMILIA')
    await selects[2]!.setValue(CHAVE)
    expect(w.text()).toContain('Divórcio consensual')
    await w.find('form.formulario').trigger('submit')
    await flushPromises()
    expect(m.sugerirNoAtendimento).toHaveBeenCalledWith(9, 'DF', [CHAVE])
    const t = w.text()
    expect(t).toContain('Mínimo da OAB')
    expect(t).toContain('Tabela do escritório')
    expect(t).toContain('R$')
    expect(t).toContain('Divórcio: 5% sobre os bens')
    expect(t).toContain('Entrada de 30% + 6 parcelas')
  })

  it('valor abaixo do minimo da OAB mostra alerta com a diferenca e exige motivo e justificativa', async () => {
    m.propostaDoAtendimento.mockResolvedValue(proposta())
    m.fecharProposta.mockResolvedValue(proposta({ status: 'FECHADA', valorFechado: 12000, abaixoDaOab: true, diferencaAbaixoOab: 3245.2 }))
    const w = mount(QuadroSugestao, { props: { agendamentoId: 9, podeAssinar: false } })
    await flushPromises()
    expect(w.text()).not.toContain('Valor abaixo do mínimo da tabela da OAB')
    await w.find('input[type=number]').setValue(12000)
    expect(w.find('[role=alert]').text()).toContain('Valor abaixo do mínimo da tabela da OAB')
    expect(w.find('[role=alert]').text()).toContain('3.245,20')
    const motivo = w.findAll('select').find((s) => s.text().includes('Pro bono'))!
    await motivo.setValue('HIPOSSUFICIENTE')
    await w.find('textarea').setValue('Cliente sem renda')
    await w.find('form.formulario:last-of-type').trigger('submit')
    await flushPromises()
    expect(m.fecharProposta).toHaveBeenCalledWith(5, expect.objectContaining({ valorFechado: 12000, justificativaTipo: 'HIPOSSUFICIENTE', justificativa: 'Cliente sem renda' }))
  })

  it('valor igual ou acima do minimo nao pede justificativa e ja fechada mostra sugerido x fechado, minuta e assinatura so para socia', async () => {
    m.propostaDoAtendimento.mockResolvedValue(proposta({ status: 'FECHADA', valorFechado: 17000 }))
    const socia = mount(QuadroSugestao, { props: { agendamentoId: 9, podeAssinar: true } })
    await flushPromises()
    expect(socia.text()).toContain('Sugerido')
    expect(socia.text()).toContain('fechado')
    expect(socia.text()).toContain('Gerar minuta do contrato')
    expect(socia.text()).toContain('Contrato assinado: lançar parcelas')
    const advogado = mount(QuadroSugestao, { props: { agendamentoId: 9, podeAssinar: false } })
    await flushPromises()
    expect(advogado.text()).toContain('Gerar minuta do contrato')
    expect(advogado.text()).not.toContain('Contrato assinado: lançar parcelas')
  })
})

describe('HonorariosView', () => {
  it('lista versoes das duas tabelas separadas e os itens da vigente da OAB', async () => {
    m.listarVersoes.mockResolvedValue([
      { id: 1, tabela: 'OAB', seccional: 'DF', referencia: 'Julho/2026', valorUrh: 381.13, fatorEscritorio: null, descontoPadrao: null, descontoTrabalhista: null, descontoPacote: 0, vigente: true, arquivoNome: 'x.xlsx', criadoEm: '2026-10-21T10:00:00Z', substituidaEm: null, itens: 1 },
      { id: 2, tabela: 'ESCRITORIO', seccional: null, referencia: 'Julho/2026', valorUrh: null, fatorEscritorio: 1.2, descontoPadrao: 0.1, descontoTrabalhista: 0.05, descontoPacote: 0, vigente: true, arquivoNome: 'x.xlsx', criadoEm: '2026-10-21T10:00:00Z', substituidaEm: null, itens: 1 },
      { id: 0, tabela: 'OAB', seccional: 'DF', referencia: 'Janeiro/2026', valorUrh: 350, fatorEscritorio: null, descontoPadrao: null, descontoTrabalhista: null, descontoPacote: 0, vigente: false, arquivoNome: 'v0.xlsx', criadoEm: '2026-01-10T10:00:00Z', substituidaEm: '2026-10-21T10:00:00Z', itens: 1 },
    ])
    m.listarItensTabela.mockResolvedValue([
      { id: 10, versaoId: 1, tabela: 'OAB', area: 'FAMILIA', tipoServico: 'Divórcio consensual', fase: null, chaveServico: CHAVE, itemOab: '40.1-a', valorUrh: 40, valor: 15245.2, derivado: false, descontoPercentual: null, valorSugerido: null, exitoTexto: null, exitoMin: null, exitoMax: null, parcelamento: null, observacoes: null, ativo: true },
    ])
    const w = mount(HonorariosView)
    await flushPromises()
    const opcoes = w.findAll('select')[1]!.findAll('option').map((o) => o.text())
    expect(opcoes.some((o) => o.startsWith('OAB/DF — Julho/2026 (vigente)'))).toBe(true)
    expect(opcoes.some((o) => o.startsWith('Escritório'))).toBe(true)
    expect(opcoes.some((o) => o.includes('(histórico)'))).toBe(true)
    expect(w.text()).toContain('Divórcio consensual')
    expect(w.text()).toContain('40.1-a')
    expect(w.text()).toContain('Atualizar URH')
    expect(w.text()).toContain('editar')
  })
})
