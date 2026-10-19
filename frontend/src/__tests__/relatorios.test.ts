import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RelatoriosView from '../views/RelatoriosView.vue'
import ModelosView from '../views/ModelosView.vue'
import SegurancaView from '../views/SegurancaView.vue'
import type { Eu } from '../api/sessao'
import type { RelatorioCliente } from '../api/relatorios'
import { ApiError } from '../api/http'
import { logarComo } from './helpers'

const m = vi.hoisted(() => ({
  gerarInterno: vi.fn(),
  enviarInternoAsSocias: vi.fn(),
  criarRelatorioCliente: vi.fn(),
  buscarRelatorioCliente: vi.fn(),
  relatoriosDoCliente: vi.fn(),
  editarRelatorioCliente: vi.fn(),
  aprovarRelatorioCliente: vi.fn(),
  enviarRelatorioCliente: vi.fn(),
  listarModelos: vi.fn(),
  listarAreas: vi.fn(),
  criarModelo: vi.fn(),
  desativarModelo: vi.fn(),
  statusDoisFatores: vi.fn(),
  iniciarDoisFatores: vi.fn(),
  confirmarDoisFatores: vi.fn(),
  desativarDoisFatores: vi.fn(),
  listarClientes: vi.fn(),
}))
vi.mock('../api/relatorios', async (o) => ({ ...(await o<typeof import('../api/relatorios')>()), ...m }))
vi.mock('../api/cadastros', async (o) => ({ ...(await o<typeof import('../api/cadastros')>()), listarClientes: m.listarClientes }))

const SOCIA: Eu = { id: 1, nome: 'Sócia A', email: 'a@t.test', perfil: 'SOCIA' }
const ADVOGADA: Eu = { id: 3, nome: 'Advogada C', email: 'c@t.test', perfil: 'ADVOGADO' }

async function montar(componente: object, eu: Eu = SOCIA) {
  await logarComo(eu)
  await flushPromises()
  const w = mount(componente)
  await flushPromises()
  return w
}
const botao = (w: ReturnType<typeof mount>, texto: string) => w.findAll('button').find((b) => b.text() === texto)!

const rel = (p: Partial<RelatorioCliente> = {}): RelatorioCliente => ({
  id: 4, clienteId: 1, periodoInicio: null, periodoFim: '2026-10-19', estado: 'RASCUNHO', incluirFinanceiro: false,
  conteudo: {
    clienteNome: 'Maria', periodoInicio: null, periodoFim: '2026-10-19', comentarios: 'Texto inicial', financeiro: null,
    processos: [{ numero: '0000001-31.2025.8.07.0001', varaComarca: null, tribunal: 'TJDFT', tipoAcao: null, situacao: 'Em andamento', partes: ['Maria', 'Empresa'], movimentacoes: [{ data: '2026-10-10', texto: 'Sentença publicada' }], feitos: [], proximosPassos: [] }],
  },
  criadoPor: 1, criadoEm: '2026-10-19T10:00:00Z', aprovadoPor: null, aprovadoEm: null, enviadoPor: null, enviadoEm: null, canal: null, ...p,
})

beforeEach(() => {
  Object.values(m).forEach((f) => f.mockReset())
  m.listarClientes.mockResolvedValue([{ id: 1, nome: 'Maria Cliente', ativo: true }])
  m.relatoriosDoCliente.mockResolvedValue([])
})

describe('RelatoriosView internos', () => {
  it('mostra a tabela e o link do PDF com os filtros', async () => {
    m.gerarInterno.mockResolvedValue({ titulo: 'Agenda do dia', subtitulo: '19/10/2026', colunas: ['Tipo', 'Descrição'], linhas: [['CON', 'Contestar']], geradoEm: '' })
    const w = await montar(RelatoriosView)
    await w.find('input[type=date]').setValue('2026-10-01')
    await botao(w, 'Ver').trigger('click')
    await flushPromises()
    expect(m.gerarInterno).toHaveBeenCalledWith('AGENDA_DIA', expect.objectContaining({ de: '2026-10-01' }))
    expect(w.find('table').text()).toContain('Contestar')
    expect(w.find('a[href^="/api/relatorios/internos/AGENDA_DIA/pdf?de=2026-10-01"]').exists()).toBe(true)
  })

  it('envia as socias e mostra a negativa do servidor', async () => {
    m.enviarInternoAsSocias.mockResolvedValueOnce({ enviados: 2 }).mockRejectedValueOnce(new ApiError(403, 'Somente sócias'))
    const w = await montar(RelatoriosView)
    await botao(w, 'Enviar às sócias').trigger('click')
    await flushPromises()
    expect(w.find('[role=status]').text()).toContain('2 sócia(s)')
    await botao(w, 'Enviar às sócias').trigger('click')
    await flushPromises()
    expect(w.find('[role=alert]').text()).toBe('Somente sócias')
  })
})

describe('RelatoriosView ao cliente (rascunho, aprovacao, PDF)', () => {
  async function comRascunho() {
    m.criarRelatorioCliente.mockResolvedValue(rel())
    const w = await montar(RelatoriosView)
    const selects = w.findAll('select')
    await selects[selects.length - 1]!.setValue(1)
    await flushPromises()
    await w.findAll('form.formulario')[0]!.trigger('submit')
    await flushPromises()
    return w
  }

  it('gera rascunho sem PDF; edita comentarios e aprova', async () => {
    m.editarRelatorioCliente.mockResolvedValue(rel({ conteudo: { ...rel().conteudo, comentarios: 'Revisado' } }))
    m.aprovarRelatorioCliente.mockResolvedValue(rel({ estado: 'APROVADO' }))
    m.buscarRelatorioCliente.mockResolvedValue(rel({ estado: 'APROVADO' }))
    const w = await comRascunho()
    expect(m.criarRelatorioCliente).toHaveBeenCalledWith(expect.objectContaining({ clienteId: 1, incluirFinanceiro: false }))
    expect(w.text()).toContain('Sentença publicada')
    expect(w.text()).toContain('O PDF só fica disponível depois de aprovado')
    expect(w.find('a[href="/api/relatorios/clientes/4/pdf"]').exists()).toBe(false)

    await w.find('article textarea').setValue('Revisado')
    await botao(w, 'Aprovar').trigger('click')
    await flushPromises()
    expect(m.editarRelatorioCliente).toHaveBeenCalledWith(4, expect.objectContaining({ comentarios: 'Revisado' }))
    expect(m.aprovarRelatorioCliente).toHaveBeenCalledWith(4)
    expect(w.find('a[href="/api/relatorios/clientes/4/pdf"]').exists()).toBe(true)
    expect(botao(w, 'Enviar ao cliente')).toBeTruthy()
  })

  it('envia ao cliente pelo canal escolhido', async () => {
    m.buscarRelatorioCliente.mockResolvedValue(rel({ estado: 'ENVIADO', canal: 'WHATSAPP' }))
    m.enviarRelatorioCliente.mockResolvedValue(rel({ estado: 'ENVIADO' }))
    m.criarRelatorioCliente.mockResolvedValue(rel({ estado: 'APROVADO' }))
    const w = await montar(RelatoriosView)
    const selects = w.findAll('select')
    await selects[selects.length - 1]!.setValue(1)
    await flushPromises()
    await w.findAll('form.formulario')[0]!.trigger('submit')
    await flushPromises()
    await w.find('article .cartao-acoes select').setValue('WHATSAPP')
    await botao(w, 'Enviar ao cliente').trigger('click')
    await flushPromises()
    expect(m.enviarRelatorioCliente).toHaveBeenCalledWith(4, 'WHATSAPP')
    expect(w.text()).toContain('Enviado por WHATSAPP')
    expect(w.find('article textarea').attributes('disabled')).toBeDefined()
  })

  it('so socia pode pedir financeiro no relatorio ao cliente', async () => {
    expect((await montar(RelatoriosView, SOCIA)).text()).not.toContain('Incluir financeiro')
    m.relatoriosDoCliente.mockResolvedValue([])
    const w = await montar(RelatoriosView, SOCIA)
    const selects = w.findAll('select')
    await selects[selects.length - 1]!.setValue(1)
    await flushPromises()
    expect(w.text()).toContain('Incluir financeiro')
    const adv = await montar(RelatoriosView, ADVOGADA)
    const sel2 = adv.findAll('select')
    await sel2[sel2.length - 1]!.setValue(1)
    await flushPromises()
    expect(adv.text()).not.toContain('Incluir financeiro')
  })
})

describe('ModelosView', () => {
  beforeEach(() => {
    m.listarModelos.mockResolvedValue([{ id: 1, tipo: 'CON', areaDireito: 'Cível', titulo: 'Contestação padrão', nomeArquivo: 'cont.docx', tamanho: 2048, ativo: true, criadoPor: 1, criadoEm: '' }])
    m.listarAreas.mockResolvedValue(['Cível', 'Trabalhista'])
  })

  it('todos baixam; so socia cadastra e desativa', async () => {
    const adv = await montar(ModelosView, ADVOGADA)
    expect(adv.find('a[href="/api/modelos/1/download"]').exists()).toBe(true)
    expect(adv.text()).not.toContain('Novo modelo')
    expect(adv.text()).not.toContain('Desativar')
    expect((await montar(ModelosView, SOCIA)).text()).toContain('Desativar')
  })

  it('filtra por tipo e area', async () => {
    const w = await montar(ModelosView)
    await w.findAll('select')[0]!.setValue('REC')
    await flushPromises()
    expect(m.listarModelos).toHaveBeenLastCalledWith('REC', undefined)
  })

  it('cadastra modelo com arquivo', async () => {
    m.criarModelo.mockResolvedValue({})
    const w = await montar(ModelosView)
    await botao(w, 'Novo modelo').trigger('click')
    const f = w.find('form.formulario')
    await f.findAll('input')[0]!.setValue('Trabalhista')
    await f.findAll('input')[1]!.setValue('Recurso ordinário')
    const arquivo = new File(['x'], 'modelo.docx')
    const campo = f.find('input[type=file]')
    Object.defineProperty(campo.element, 'files', { value: [arquivo], configurable: true })
    await campo.trigger('change')
    await f.trigger('submit')
    await flushPromises()
    expect(m.criarModelo).toHaveBeenCalledWith('CON', 'Trabalhista', 'Recurso ordinário', arquivo)
  })
})

describe('SegurancaView (2FA)', () => {
  it('ativa: mostra o segredo e confirma com o codigo', async () => {
    m.statusDoisFatores.mockResolvedValueOnce({ ativo: false }).mockResolvedValueOnce({ ativo: true })
    m.iniciarDoisFatores.mockResolvedValue({ segredo: 'JBSWY3DPEHPK3PXP', uri: 'otpauth://totp/x?secret=JBSWY3DPEHPK3PXP' })
    m.confirmarDoisFatores.mockResolvedValue(undefined)
    const w = await montar(SegurancaView)
    expect(w.text()).toContain('desativada')
    await botao(w, 'Ativar').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('JBSWY3DPEHPK3PXP')
    await w.find('input').setValue('123456')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(m.confirmarDoisFatores).toHaveBeenCalledWith('123456')
    expect(w.text()).toContain('está ativa')
  })

  it('desativa com o codigo e mostra erro de codigo invalido', async () => {
    m.statusDoisFatores.mockResolvedValue({ ativo: true })
    m.desativarDoisFatores.mockRejectedValue(new ApiError(422, 'Codigo invalido'))
    const w = await montar(SegurancaView)
    await w.find('input').setValue('000000')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(m.desativarDoisFatores).toHaveBeenCalledWith('000000')
    expect(w.find('[role=alert]').text()).toBe('Codigo invalido')
  })
})
