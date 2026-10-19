// Cliente HTTP da API. Sessao por cookie HttpOnly; CSRF via cookie XSRF-TOKEN -> cabecalho X-XSRF-TOKEN.

export class ApiError extends Error {
  status: number

  constructor(status: number) {
    super('Erro na API: ' + status)
    this.status = status
  }
}

function lerCookie(nome: string): string {
  const par = document.cookie.split('; ').find((c) => c.indexOf(nome + '=') === 0)
  return par ? decodeURIComponent(par.substring(nome.length + 1)) : ''
}

async function garantirCsrf(): Promise<string> {
  let token = lerCookie('XSRF-TOKEN')
  if (!token) {
    await fetch('/api/csrf', { credentials: 'same-origin' })
    token = lerCookie('XSRF-TOKEN')
  }
  return token
}

export async function requisitar(metodo: string, url: string, corpo?: URLSearchParams): Promise<Response> {
  const cabecalhos: Record<string, string> = {}
  if (metodo !== 'GET') {
    cabecalhos['X-XSRF-TOKEN'] = await garantirCsrf()
  }
  const resposta = await fetch(url, { method: metodo, body: corpo, headers: cabecalhos, credentials: 'same-origin' })
  if (!resposta.ok) {
    throw new ApiError(resposta.status)
  }
  return resposta
}
