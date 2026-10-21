import { config, flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PublicacoesView from '../views/PublicacoesView.vue'
import CapturaView from '../views/CapturaView.vue'
import PainelView from '../views/PainelView.vue'
import type { Publicacao } from '../api/publicacoes'
import type { Eu } from '../api/sessao'
import { item, logarComo } from './helpers'

config.global.stubs = { RouterLink: true, teleport: true }

const m = vi.hoisted(() => ({
  listarPublicacoes: vi.fn(),
  buscarPublicacao: vi.fn(),
  classificarPublicacao: vi.fn(),
  descartarPublicacao: vi.fn(),
  vincularProcesso: vi.fn(),
  executarCaptura: vi.fn(),
  listarExecucoes: vi.fn(),
  listarIgnorados: vi.fn(),
  listarFiltros: vi.fn(),
  criarFiltro: vi.fn(),
  atualizarFiltro: vi.fn(),
  excluirFiltro: vi.fn(),
  excluirExecucao: vi.fn(),
  cadastrarProcessoDePublicacao: vi.fn(),
  lerAgendamento: vi.fn(async () => ({ ativa: false, horario: '0 0 6 * * *', ultima: null })),
  ligarAgendamento: vi.fn(),
  limparExecucoes: vi.fn(),
  carregarPainel: vi.fn(),
  listarProcessos: vi.fn(),
}))
vi.mock('../api/publicacoes', async (original) => ({ ...(await original<typeof import('../api/publicacoes')>()), ...m }))
vi.mock('../api/cadastros', async (original) => ({ ...(await original<typeof import('../api/cadastros')>()), listarProcessos: m.listarProcessos }))

const SOCIA: Eu = { id: 1, nome: 'Sócia A', email: 'a@t.test', perfil: 'SOCIA' }
const ADVOGADA: Eu = { id: 3, nome: 'Advogada C', email: 'c@t.test', perfil: 'ADVOGADO' }
const USUARIOS = [
  { id: 1, nome: 'Sócia A', perfil: 'SOCIA', ativo: true },
  { id: 3, nome: 'Advogada C', perfil: 'ADVOGADO', ativo: true },
]

const pub = (p: Partial<Publicacao> = {}): Publicacao => ({
  id: 1, numeroProcesso: '00000013120258070001', numeroFormatado: '0000001-31.2025.8.07.0001', titularId: 3, dataPublicacao: '2026-10-19',
  tribunal: 'TJDFT', resumo: 'Intimação para contestar', fontes: ['DJEN'], somenteEmUmaFonte: true, estado: 'A_CLASSIFICAR',
  processoId: null, sugerirCadastro: true, agendamentoId: null, motivoDescarte: null, conteudo: null, metadados: null, ...p,
})

async function montar(componente: object, eu: Eu = SOCIA) {
  await logarComo(eu, USUARIOS)
  await flushPromises()
  const w = mount(componente)
  await flushPromises()
  return w
}

beforeEach(() => {
  Object.values(m).forEach((f) => f.mockReset())
  m.listarPublicacoes.mockResolvedValue([pub()])
  m.listarProcessos.mockResolvedValue([{ id: 5, numeroFormatado: '0000001-31.2025.8.07.0001', clienteNome: 'Cliente', ativo: true }])
})

describe('PublicacoesView', () => {
  it('sinaliza fonte unica e sugestao de cadastro', async () => {
    const w = await montar(PublicacoesView)
    expect(w.text()).toContain('única fonte')
    expect(w.text()).toContain('sugerido cadastrar')
  })

  it('so socia classifica e descarta', async () => {
    expect((await montar(PublicacoesView, SOCIA)).text()).toContain('Classificar')
    const w = await montar(PublicacoesView, ADVOGADA)
    expect(w.text()).not.toContain('Classificar')
    expect(w.text()).not.toContain('Descartar')
  })

  it('classifica criando prazo com responsavel e datas', async () => {
    m.classificarPublicacao.mockResolvedValue(pub())
    const w = await montar(PublicacoesView)
    await w.findAll('button').find((b) => b.text() === 'Classificar')!.trigger('click')
    await flushPromises()
    const f = w.find('form.formulario')
    await f.findAll('select')[1]!.setValue(5)
    await f.findAll('select')[2]!.setValue(3)
    await f.trigger('submit')
    await flushPromises()
    expect(m.classificarPublicacao).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ tipo: 'CON', processoId: 5, responsavelId: 3 }),
    )
    expect(w.find('[role=status]').text()).toContain('aguarda a conferência')
  })

  it('descarta com motivo obrigatorio', async () => {
    m.descartarPublicacao.mockResolvedValue(pub())
    const w = await montar(PublicacoesView)
    await w.findAll('button').find((b) => b.text() === 'Descartar')!.trigger('click')
    await w.find('textarea').setValue('Duplicada')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(m.descartarPublicacao).toHaveBeenCalledWith(1, 'Duplicada')
  })

  it('vincula a um processo e mostra o texto completo', async () => {
    m.vincularProcesso.mockResolvedValue(pub())
    m.buscarPublicacao.mockResolvedValue(pub({ conteudo: 'Texto integral da publicação' }))
    const w = await montar(PublicacoesView)
    await w.findAll('button').find((b) => b.text() === 'Ver texto completo')!.trigger('click')
    await flushPromises()
    expect(w.find('pre').text()).toBe('Texto integral da publicação')
    await w.findAll('button').find((b) => b.text() === 'Vincular processo')!.trigger('click')
    await flushPromises()
    await w.find('form select').setValue(5)
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(m.vincularProcesso).toHaveBeenCalledWith(1, 5)
  })
})

describe('CapturaView', () => {
  beforeEach(() => {
    m.listarExecucoes.mockResolvedValue([{ id: 1, fonte: 'DJEN', inicio: '2026-10-19T10:00:00Z', fim: null, resultado: 'ERRO', itensLidos: 0, itensNovos: 0, erro: 'fora do ar' }])
    m.listarIgnorados.mockResolvedValue([{ momento: '2026-10-19T10:00:00Z', fonte: 'DJEN', tribunal: 'TRT-1', motivo: 'Filtro TRT' }])
    m.listarFiltros.mockResolvedValue([{ id: 1, usuarioId: 1, fonte: 'EMAIL', tribunalPrefixo: 'TRT', excecoes: 'TRT-10', ativo: true }])
  })

  it('mostra filtros, ignorados e execucoes', async () => {
    const w = await montar(CapturaView)
    expect(w.text()).toContain('Filtro TRT')
    expect(w.text()).toContain('TRT-10')
    expect(w.text()).toContain('fora do ar')
  })

  it('executa a captura', async () => {
    m.executarCaptura.mockResolvedValue([])
    const w = await montar(CapturaView)
    await w.findAll('button').find((b) => b.text() === 'Executar captura agora')!.trigger('click')
    await flushPromises()
    expect(m.executarCaptura).toHaveBeenCalled()
  })
})

describe('PainelView', () => {
  it('mostra a carga por pessoa e as secoes', async () => {
    m.carregarPainel.mockResolvedValue({
      porPessoa: [{ usuarioId: 3, abertos: 4, vencidos: 1, urgentes: 0 }],
      atrasos: [item({ id: 1, descricao: 'Atrasado X' })],
      prazosDaSemana: [],
      compromissosProximos: [],
      aguardandoConferenciaSocia: [],
      aguardandoConferenciaPrazo: [],
      aClassificar: 2,
    })
    const w = await montar(PainelView)
    expect(w.text()).toContain('Advogada C')
    expect(w.text()).toContain('Atrasado X')
    expect(w.text()).toContain('2')
    expect(w.text()).toContain('Nada aqui.')
  })
})
