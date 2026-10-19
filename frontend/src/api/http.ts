// Cliente HTTP da API. Sessao por cookie HttpOnly; CSRF via cookie XSRF-TOKEN -> cabecalho X-XSRF-TOKEN.
// Erros vem como ProblemDetail (campo detail); recurso fora da visibilidade responde 404.

export class ApiError extends Error {
  status: number
  detalhe: string
  doisFatores: boolean

  constructor(status: number, detalhe = '', doisFatores = false) {
    super(detalhe || 'Erro na API: ' + status)
    this.status = status
    this.detalhe = detalhe
    this.doisFatores = doisFatores
  }
}

export interface Opcoes {
  json?: unknown
  form?: Record<string, string>
}

let aoExpirar: () => void = () => {}

/** Registra o que fazer quando uma chamada autenticada volta 401 (sessao expirada). */
export function definirAoExpirar(acao: () => void): void {
  aoExpirar = acao
}

function lerCookie(nome: string): string {
  const par = document.cookie.split('; ').find((c) => c.indexOf(nome + '=') === 0)
  return par ? decodeURIComponent(par.substring(nome.length + 1)) : ''
}

/** Pede um token novo; obrigatorio depois do login (a sessao troca o token). */
export async function renovarCsrf(): Promise<string> {
  await fetch('/api/csrf', { credentials: 'same-origin' })
  return lerCookie('XSRF-TOKEN')
}

async function garantirCsrf(): Promise<string> {
  return lerCookie('XSRF-TOKEN') || (await renovarCsrf())
}

/** Faz a chamada e devolve a resposta crua, sem lancar por status. */
export async function enviar(metodo: string, url: string, opcoes: Opcoes = {}): Promise<Response> {
  const cabecalhos: Record<string, string> = {}
  let corpo: BodyInit | undefined
  if (metodo !== 'GET') {
    cabecalhos['X-XSRF-TOKEN'] = await garantirCsrf()
  }
  if (opcoes.form) {
    corpo = new URLSearchParams(opcoes.form)
  } else if (opcoes.json !== undefined) {
    cabecalhos['Content-Type'] = 'application/json'
    corpo = JSON.stringify(opcoes.json)
  }
  return fetch(url, { method: metodo, body: corpo, headers: cabecalhos, credentials: 'same-origin' })
}

async function detalheDe(resposta: Response): Promise<string> {
  try {
    const dados = await resposta.json()
    return typeof dados?.detail === 'string' ? dados.detail : ''
  } catch {
    return ''
  }
}

/** Chamada que lanca ApiError quando o status nao e 2xx e devolve o JSON (ou undefined em 204). */
export async function api<T = void>(metodo: string, url: string, opcoes: Opcoes = {}): Promise<T> {
  const resposta = await enviar(metodo, url, opcoes)
  if (!resposta.ok) {
    if (resposta.status === 401 && url !== '/api/me') {
      aoExpirar()
    }
    throw new ApiError(resposta.status, await detalheDe(resposta), resposta.headers.get('X-2FA-Obrigatorio') !== null)
  }
  if (resposta.status === 204) {
    return undefined as T
  }
  return (await resposta.json()) as T
}
