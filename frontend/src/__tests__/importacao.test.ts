import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ImportacaoView from '../views/ImportacaoView.vue'
import PainelFinanceiro from '../components/PainelFinanceiro.vue'
import type { Importacao, ItemPrevia } from '../api/importacao'
import { logarComo } from './helpers'
import { config } from '@vue/test-utils'

config.global.stubs = { RouterLink: true, teleport: true }

const m = vi.hoisted(() => ({
  listarImportacoes: vi.fn(),
  detalheImportacao: vi.fn(),
  enviarPlanilha: vi.fn(),
  previaPlanilhaDoDrive: vi.fn(),
  previaDoDrive: vi.fn(),
  statusDrive: vi.fn(),
  statusGoogle: vi.fn(),
  conectarGoogle: vi.fn(),
  decidirItem: vi.fn(),
  decidirLote: vi.fn(),
  confirmarImportacao: vi.fn(),
  cancelarImportacao: vi.fn(),
  carregarPainelFinanceiro: vi.fn(),
}))
vi.mock('../api/importacao', async (original) => ({ ...(await original<typeof import('../api/importacao')>()), ...m }))

const SOCIA = { id: 1, nome: 'Sócia A', email: 'a@t.test', perfil: 'SOCIA' as const }

const item = (p: Partial<ItemPrevia> = {}): ItemPrevia => ({
  id: 1, ordem: 0, tipo: 'CLIENTE', chave: 'CLIENTE|ANA', rotulo: 'ANA EXEMPLO', acao: 'CRIAR', divergencias: ['Planilha nao traz CPF/CNPJ'],
  importado: { nome: 'ANA EXEMPLO', areas: [] }, existente: null, decisao: null, ajustes: {}, resultado: null, motivo: null, ...p,
})

const importacao = (p: Partial<Importacao> = {}): Importacao => ({
  id: 7, origem: 'PLANILHA', modo: 'MANUAL', arquivoNome: 'planilha-ficticia.xlsx', status: 'PREVIA', criadoEm: '2026-10-21T10:00:00Z',
  confirmadoEm: null, confirmadoPor: null, contagens: { CLIENTE: { CRIAR: 1 } }, comDivergencia: 1, pendencias: ['Cliente "ANA EXEMPLO": escolha a area de atuacao'],
  relatorio: null, itens: [item()], ...p,
})

beforeEach(async () => {
  Object.values(m).forEach((f) => f.mockReset())
  m.listarImportacoes.mockResolvedValue([importacao({ itens: [] })])
  m.detalheImportacao.mockResolvedValue(importacao())
  m.statusDrive.mockResolvedValue({ pastaClientesConfigurada: true, planilhaConfigurada: true, contaConectadaAoDrive: true })
  m.statusGoogle.mockResolvedValue({ disponivel: true, conectado: true, estado: 'ATIVA', email: 'a@t.test', drive: true })
  await logarComo(SOCIA, [])
  await flushPromises()
})

const botao = (w: ReturnType<typeof mount>, texto: string) => w.findAll('button').find((b) => b.text() === texto)!

describe('ImportacaoView', () => {
  it('mostra a previa com divergencias e pendencias e bloqueia a confirmacao', async () => {
    const w = mount(ImportacaoView)
    await flushPromises()
    await botao(w, 'Abrir').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('Planilha nao traz CPF/CNPJ')
    expect(w.text()).toContain('Pendências para poder confirmar (1)')
    expect(botao(w, 'Confirmar importação').attributes('disabled')).toBeDefined()
  })

  it('escolher a area do cliente na previa chama a API com o ajuste', async () => {
    m.decidirItem.mockResolvedValue(importacao({ pendencias: [], itens: [item({ ajustes: { areas: ['CIVEL'] } })] }))
    const w = mount(ImportacaoView)
    await flushPromises()
    await botao(w, 'Abrir').trigger('click')
    await flushPromises()
    const caixas = w.findAll('article fieldset input[type=checkbox]')
    expect(caixas).toHaveLength(10)
    await caixas[9]!.setValue(true) // Cível
    await flushPromises()
    expect(m.decidirItem).toHaveBeenCalledWith(7, 1, { decisao: null, ajustes: { areas: ['CIVEL'] } })
    expect(botao(w, 'Confirmar importação').attributes('disabled')).toBeUndefined()
  })

  it('confirma e mostra o relatorio', async () => {
    m.detalheImportacao.mockResolvedValue(importacao({ pendencias: [] }))
    m.confirmarImportacao.mockResolvedValue(
      importacao({
        status: 'CONCLUIDA', pendencias: [], itens: [],
        relatorio: { totais: { CLIENTE: { criados: 1 } }, ignorados: [{ item: 'x', tipo: 'PARCELA', motivo: 'Linha de total' }], erros: [] },
      }),
    )
    const w = mount(ImportacaoView)
    await flushPromises()
    await botao(w, 'Abrir').trigger('click')
    await flushPromises()
    await botao(w, 'Confirmar importação').trigger('click')
    await flushPromises()
    expect(m.confirmarImportacao).toHaveBeenCalledWith(7)
    expect(w.text()).toContain('Relatório da importação')
    expect(w.text()).toContain('Importação concluída.')
  })

  it('mostra os conflitos lado a lado', async () => {
    m.detalheImportacao.mockResolvedValue(
      importacao({
        pendencias: [],
        itens: [item({ tipo: 'PARCELA', acao: 'CONFLITO', rotulo: 'ANA — PARCELA 1/24', divergencias: [], importado: { situacao: 'PAGA', valor: 200 }, existente: { situacao: 'ABERTA', valor: 200 } })],
      }),
    )
    const w = mount(ImportacaoView)
    await flushPromises()
    await botao(w, 'Abrir').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('No sistema')
    expect(w.text()).toContain('PAGA')
    expect(w.text()).toContain('ABERTA')
  })
})

describe('PainelFinanceiro', () => {
  it('lista vencidas com dias de atraso e receita por area', async () => {
    m.carregarPainelFinanceiro.mockResolvedValue({
      meses: [{ mes: '2026-04', recebido: 800, emAberto: 400 }],
      quantidadeVencidas: 1, totalVencido: 400,
      vencidas: [{ parcelaId: 1, contratoId: 1, clienteId: 1, cliente: 'ANA EXEMPLO', contrato: 'Honorários', area: 'FAMILIA', rotulo: 'PARCELA 1/24', vencimento: '2026-04-10', valorEmAberto: 400, diasAtraso: 193 }],
      aVencer7Dias: { quantidade: 0, total: 0, parcelas: [] },
      aVencer30Dias: { quantidade: 0, total: 0, parcelas: [] },
      porArea: [{ area: 'FAMILIA', recebido: 800, emAberto: 400 }, { area: null, recebido: 0, emAberto: 10 }],
    })
    const w = mount(PainelFinanceiro, { props: { area: '' } })
    await flushPromises()
    expect(w.find('[role=alert]').text()).toContain('1 parcela(s) vencida(s)')
    expect(w.text()).toContain('193')
    expect(w.text()).toContain('04/2026')
    expect(w.text()).toContain('Família')
    expect(w.text()).toContain('Sem área')
    await w.setProps({ area: 'FAMILIA' })
    await flushPromises()
    expect(m.carregarPainelFinanceiro).toHaveBeenLastCalledWith('FAMILIA')
  })
})
