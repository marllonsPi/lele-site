import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, definirAoExpirar, enviar } from '../api/http'

function resposta(status: number, corpo?: unknown, cabecalhos: Record<string, string> = {}) {
  return new Response(corpo === undefined ? null : JSON.stringify(corpo), { status, headers: cabecalhos })
}

describe('http', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
    definirAoExpirar(() => {})
  })

  afterEach(() => vi.unstubAllGlobals())

  it('GET nao pede CSRF', async () => {
    fetchMock.mockResolvedValue(resposta(200, { ok: 1 }))
    expect(await api('GET', '/api/x')).toEqual({ ok: 1 })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('POST busca o CSRF e envia o cabecalho', async () => {
    fetchMock.mockImplementation(async (url: string) => {
      if (url === '/api/csrf') {
        document.cookie = 'XSRF-TOKEN=abc; path=/'
        return resposta(204)
      }
      return resposta(204)
    })
    await api('POST', '/api/x', { json: { a: 1 } })
    const [, init] = fetchMock.mock.calls[1]
    expect(init.headers['X-XSRF-TOKEN']).toBe('abc')
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(init.body).toBe('{"a":1}')
  })

  it('formulario vai como urlencoded', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    fetchMock.mockResolvedValue(resposta(204))
    await enviar('POST', '/api/login', { form: { email: 'a@b.test', senha: 's' } })
    const [, init] = fetchMock.mock.calls[0]
    expect(String(init.body)).toBe('email=a%40b.test&senha=s')
  })

  it('erro vira ApiError com o detail do ProblemDetail', async () => {
    fetchMock.mockResolvedValue(resposta(409, { detail: 'Conflito de horario' }))
    await expect(api('GET', '/api/x')).rejects.toMatchObject({ status: 409, detalhe: 'Conflito de horario' })
    await expect(api('GET', '/api/x')).rejects.toBeInstanceOf(ApiError)
  })

  it('401 avisa a sessao expirada, exceto em /api/me', async () => {
    const expirou = vi.fn()
    definirAoExpirar(expirou)
    fetchMock.mockResolvedValue(resposta(401))
    await expect(api('GET', '/api/x')).rejects.toMatchObject({ status: 401 })
    expect(expirou).toHaveBeenCalledTimes(1)
    await expect(api('GET', '/api/me')).rejects.toMatchObject({ status: 401 })
    expect(expirou).toHaveBeenCalledTimes(1)
  })
})
