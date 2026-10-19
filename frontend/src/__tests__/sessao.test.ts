import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { carregarEu, entrar } from '../api/sessao'

function resposta(status: number, corpo?: unknown, cabecalhos: Record<string, string> = {}) {
  return new Response(corpo === undefined ? null : JSON.stringify(corpo), { status, headers: cabecalhos })
}

describe('api/sessao', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
    document.cookie = 'XSRF-TOKEN=t; path=/'
  })

  afterEach(() => vi.unstubAllGlobals())

  it('carregarEu devolve null sem sessao', async () => {
    fetchMock.mockResolvedValue(resposta(401))
    expect(await carregarEu()).toBeNull()
  })

  it('login ok renova o CSRF depois', async () => {
    fetchMock.mockResolvedValue(resposta(204))
    expect(await entrar('a@b.test', 's')).toBe('ok')
    expect(fetchMock.mock.calls.map((c) => c[0])).toEqual(['/api/login', '/api/csrf'])
  })

  it('envia o codigo quando informado', async () => {
    fetchMock.mockResolvedValue(resposta(204))
    await entrar('a@b.test', 's', '123456')
    expect(String(fetchMock.mock.calls[0][1].body)).toContain('codigo=123456')
  })

  it('401 com X-2FA-Obrigatorio pede o codigo', async () => {
    fetchMock.mockResolvedValue(resposta(401, undefined, { 'X-2FA-Obrigatorio': 'true' }))
    expect(await entrar('a@b.test', 's')).toBe('precisa-codigo')
  })

  it('401 simples e credencial invalida e 429 e bloqueio', async () => {
    fetchMock.mockResolvedValueOnce(resposta(401)).mockResolvedValueOnce(resposta(429))
    expect(await entrar('a@b.test', 'x')).toBe('invalido')
    expect(await entrar('a@b.test', 'x')).toBe('muitas-tentativas')
  })
})
