import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { criarRoteador } from '../router'
import ClientesView from '../views/ClientesView.vue'
import ProcessosView from '../views/ProcessosView.vue'
import UsuariosView from '../views/UsuariosView.vue'
import VisibilidadeView from '../views/VisibilidadeView.vue'
import CalendarioView from '../views/CalendarioView.vue'
import { ApiError } from '../api/http'
import type { Cliente, Processo, Usuario } from '../api/cadastros'
import type { Eu } from '../api/sessao'
import { logarComo } from './helpers'
import { config } from '@vue/test-utils'

config.global.stubs = { RouterLink: true }

const m = vi.hoisted(() => ({
  listarClientes: vi.fn(),
  criarCliente: vi.fn(),
  atualizarCliente: vi.fn(),
  desativarCliente: vi.fn(),
  reativarCliente: vi.fn(),
  listarProcessos: vi.fn(),
  criarProcesso: vi.fn(),
  atualizarProcesso: vi.fn(),
  desativarProcesso: vi.fn(),
  adicionarParte: vi.fn(),
  listarUsuarios: vi.fn(),
  criarUsuario: vi.fn(),
  atualizarUsuario: vi.fn(),
  desativarUsuario: vi.fn(),
  reativarUsuario: vi.fn(),
  redefinirSenha: vi.fn(),
  adicionarOab: vi.fn(),
  desativarOab: vi.fn(),
  listarVisibilidade: vi.fn(),
  definirVisibilidade: vi.fn(),
  listarCalendario: vi.fn(),
  criarFeriado: vi.fn(),
  atualizarFeriado: vi.fn(),
  desativarFeriado: vi.fn(),
}))
vi.mock('../api/cadastros', async (original) => ({ ...(await original<typeof import('../api/cadastros')>()), ...m }))
vi.mock('../api/publicacoes', async (original) => ({
  ...(await original<typeof import('../api/publicacoes')>()),
  listarFiltros: vi.fn(async () => []),
  criarFiltro: vi.fn(),
  desativarFiltro: vi.fn(),
}))
vi.mock('vue-router', async (original) => ({ ...(await original<typeof import('vue-router')>()), useRoute: () => ({ query: {} }) }))

const cliente = (p: Partial<Cliente> = {}): Cliente => ({
  id: 1, nome: 'Maria Cliente', documento: '000.000.000-00', email: 'm@t.test', telefone: null, endereco: null, observacoes: null, ativo: true, ...p,
})
const processo = (p: Partial<Processo> = {}): Processo => ({
  id: 1, numero: '00000013120258070001', numeroFormatado: '0000001-31.2025.8.07.0001', tribunal: 'TJDFT', varaComarca: null, classe: null,
  situacao: null, valorCausa: null, clienteId: 1, clienteNome: 'Maria Cliente', advogadoId: 3, ativo: true, partes: [{ id: 1, nome: 'Fulano', papel: 'CONTRARIA' }], ...p,
})
const usuario = (p: Partial<Usuario> = {}): Usuario => ({
  id: 1, nome: 'Sócia A', email: 'a@t.test', perfil: 'SOCIA', ativo: true, supervisorId: null, oabs: [], mestre: false, telefone: null, whatsapp: null, cargo: null,
  emailAlertas: null, alertasEmail: true, assinatura: null, ...p,
})

const SOCIA = { id: 1, nome: 'Sócia A', email: 'a@t.test', perfil: 'SOCIA' as const }
const ADVOGADO = { id: 3, nome: 'Advogada C', email: 'c@t.test', perfil: 'ADVOGADO' as const }

async function montar(componente: object, eu: Eu = SOCIA) {
  await logarComo(eu, [usuario(), usuario({ id: 3, nome: 'Advogada C', perfil: 'ADVOGADO' })])
  await flushPromises()
  const w = mount(componente)
  await flushPromises()
  return w
}

const botao = (w: ReturnType<typeof mount>, texto: string) => w.findAll('button').find((b) => b.text() === texto)!

beforeEach(() => {
  Object.values(m).forEach((f) => f.mockReset())
  m.listarClientes.mockResolvedValue([cliente(), cliente({ id: 2, nome: 'Outro', documento: '1', ativo: false })])
  m.listarProcessos.mockResolvedValue([processo()])
  m.listarUsuarios.mockResolvedValue([usuario(), usuario({ id: 3, nome: 'Advogada C', perfil: 'ADVOGADO' })])
  m.listarVisibilidade.mockResolvedValue([{ observadorId: 3, titularId: 1, escopo: 'AGENDAMENTO' }])
  m.listarCalendario.mockResolvedValue([
    { id: 1, escopo: 'NACIONAL', referencia: null, tipo: 'FERIADO', dataInicial: '2026-11-02', dataFinal: null, descricao: 'Finados', ativo: true },
  ])
})

describe('ClientesView', () => {
  it('socia ve acoes de escrita; advogado so le', async () => {
    expect((await montar(ClientesView, SOCIA)).text()).toContain('Novo cliente')
    const w = await montar(ClientesView, ADVOGADO)
    expect(w.text()).not.toContain('Novo cliente')
    expect(w.text()).not.toContain('Editar')
    expect(w.text()).toContain('Maria Cliente')
  })

  it('busca filtra por nome e documento', async () => {
    const w = await montar(ClientesView)
    await w.find('input[type=search]').setValue('outro')
    expect(w.findAll('article')).toHaveLength(1)
    expect(w.text()).toContain('Inativo')
  })

  it('cadastra cliente', async () => {
    m.criarCliente.mockResolvedValue(cliente())
    const w = await montar(ClientesView)
    await botao(w, 'Novo cliente').trigger('click')
    await w.find('form input').setValue('Novo Cliente')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(m.criarCliente).toHaveBeenCalledWith(expect.objectContaining({ nome: 'Novo Cliente' }))
    expect(w.find('[role=status]').text()).toContain('cadastrado')
  })

  it('mostra o erro do servidor', async () => {
    m.desativarCliente.mockRejectedValue(new ApiError(409, 'Cliente tem processos ativos'))
    const w = await montar(ClientesView)
    await botao(w, 'Desativar').trigger('click')
    await flushPromises()
    expect(w.find('[role=alert]').text()).toBe('Cliente tem processos ativos')
  })
})

describe('ProcessosView', () => {
  it('lista com numero formatado; so socia cadastra', async () => {
    const w = await montar(ProcessosView, ADVOGADO)
    expect(w.text()).toContain('0000001-31.2025.8.07.0001')
    expect(w.text()).not.toContain('Novo processo')
  })

  it('socia adiciona parte', async () => {
    m.adicionarParte.mockResolvedValue(processo())
    const w = await montar(ProcessosView)
    await botao(w, 'Partes e anexos').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('Fulano')
    await w.find('.detalhe form input').setValue('Beltrano')
    await w.find('.detalhe form').trigger('submit')
    await flushPromises()
    expect(m.adicionarParte).toHaveBeenCalledWith(1, 'Beltrano', 'CONTRARIA')
  })

  it('cria processo com cliente e responsavel', async () => {
    m.criarProcesso.mockResolvedValue(processo())
    const w = await montar(ProcessosView)
    await botao(w, 'Novo processo').trigger('click')
    await flushPromises()
    const f = w.find('form.formulario')
    await f.find('input').setValue('00000013120258070001')
    await f.findAll('input')[1]!.setValue('TJDFT')
    const selects = f.findAll('select')
    await selects[0]!.setValue(1)
    await selects[1]!.setValue(3)
    await f.trigger('submit')
    await flushPromises()
    expect(m.criarProcesso).toHaveBeenCalledWith(expect.objectContaining({ numero: '00000013120258070001', clienteId: 1, advogadoId: 3 }))
  })
})

describe('UsuariosView', () => {
  it('estagiario exige supervisor e envia senha inicial', async () => {
    m.criarUsuario.mockResolvedValue(usuario())
    const w = await montar(UsuariosView)
    await botao(w, 'Nova conta').trigger('click')
    const f = w.find('form.formulario')
    expect(f.text()).not.toContain('Supervisor')
    await f.find('input').setValue('Estag Novo')
    await f.find('input[type=email]').setValue('e@t.test')
    await f.find('select').setValue('ESTAGIARIO')
    await flushPromises()
    await f.findAll('select')[1]!.setValue(3)
    await f.find('input[type=password]').setValue('segredo-123')
    await f.trigger('submit')
    await flushPromises()
    expect(m.criarUsuario).toHaveBeenCalledWith(
      expect.objectContaining({ nome: 'Estag Novo', perfil: 'ESTAGIARIO', supervisorId: 3, senha: 'segredo-123' }),
    )
  })

  it('cadastra OAB em maiusculas', async () => {
    m.adicionarOab.mockResolvedValue({})
    const w = await montar(UsuariosView)
    await w.findAll('button').filter((b) => b.text() === 'OABs')[0]!.trigger('click')
    const f = w.find('article form')
    await f.find('input').setValue('12345')
    await f.findAll('input')[1]!.setValue('df')
    await f.trigger('submit')
    await flushPromises()
    expect(m.adicionarOab).toHaveBeenCalledWith(3, '12345', 'DF', false)
  })
})

describe('VisibilidadeView', () => {
  it('reflete a matriz, alterna e salva so a linha do observador', async () => {
    m.definirVisibilidade.mockResolvedValue(undefined)
    const w = await montar(VisibilidadeView)
    const cartaoC = w.findAll('article')[1]!
    const caixas = cartaoC.findAll('input[type=checkbox]')
    // tabela de C: linha da Socia A, colunas Publicacoes/Agenda/Processos
    expect((caixas[1]!.element as HTMLInputElement).checked).toBe(true)
    await caixas[2]!.setValue(true)
    await cartaoC.find('button').trigger('click')
    await flushPromises()
    const [observador, regras] = m.definirVisibilidade.mock.calls[0]!
    expect(observador).toBe(3)
    expect(regras).toEqual(
      expect.arrayContaining([
        { observadorId: 3, titularId: 1, escopo: 'AGENDAMENTO' },
        { observadorId: 3, titularId: 1, escopo: 'PROCESSO' },
      ]),
    )
    expect(regras).toHaveLength(2)
  })
})

describe('CalendarioView', () => {
  it('todos leem; so socia edita', async () => {
    expect((await montar(CalendarioView, ADVOGADO)).text()).toContain('Finados')
    expect((await montar(CalendarioView, ADVOGADO)).text()).not.toContain('Novo feriado')
    expect((await montar(CalendarioView, SOCIA)).text()).toContain('Novo feriado')
  })

  it('cria feriado nacional', async () => {
    m.criarFeriado.mockResolvedValue({})
    const w = await montar(CalendarioView)
    await botao(w, 'Novo feriado ou suspensão').trigger('click')
    const f = w.find('form.formulario')
    await f.find('input[type=date]').setValue('2026-12-25')
    await f.find('input[maxlength="200"]').setValue('Natal')
    await f.trigger('submit')
    await flushPromises()
    expect(m.criarFeriado).toHaveBeenCalledWith(
      expect.objectContaining({ escopo: 'NACIONAL', dataInicial: '2026-12-25', dataFinal: null, referencia: null, descricao: 'Natal' }),
    )
  })
})

describe('roteador e perfis', () => {
  it('rotas de socia redirecionam outros perfis ao inicio', async () => {
    await logarComo(ADVOGADO)
    await flushPromises()
    const r = criarRoteador(createMemoryHistory())
    await r.push('/usuarios')
    expect(r.currentRoute.value.name).toBe('inicio')
    await r.push('/clientes')
    expect(r.currentRoute.value.name).toBe('clientes')
  })
})
