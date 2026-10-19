import { api, enviar, renovarCsrf } from './http'

export type Perfil = 'SOCIA' | 'ADVOGADO' | 'ASSISTENTE' | 'ESTAGIARIO'

export interface Eu {
  id: number
  nome: string
  email: string
  perfil: Perfil
}

export const ROTULO_PERFIL: Record<Perfil, string> = {
  SOCIA: 'Sócia',
  ADVOGADO: 'Advogado(a)',
  ASSISTENTE: 'Assistente jurídica',
  ESTAGIARIO: 'Estagiário(a)',
}

/** Resultado de uma tentativa de login. */
export type ResultadoLogin = 'ok' | 'invalido' | 'precisa-codigo' | 'muitas-tentativas'

/** Retorna o usuario logado ou null quando nao ha sessao (401). */
export async function carregarEu(): Promise<Eu | null> {
  const resposta = await enviar('GET', '/api/me')
  if (resposta.status === 401) {
    return null
  }
  if (!resposta.ok) {
    throw new Error('Falha ao carregar a sessão: ' + resposta.status)
  }
  return (await resposta.json()) as Eu
}

/** Login por formulario. 401 com X-2FA-Obrigatorio pede o codigo; 429 e bloqueio por tentativas. */
export async function entrar(email: string, senha: string, codigo?: string): Promise<ResultadoLogin> {
  const form: Record<string, string> = { email, senha }
  if (codigo) {
    form.codigo = codigo
  }
  const resposta = await enviar('POST', '/api/login', { form })
  if (resposta.ok) {
    await renovarCsrf()
    return 'ok'
  }
  if (resposta.status === 429) {
    return 'muitas-tentativas'
  }
  if (resposta.status === 401) {
    return resposta.headers.get('X-2FA-Obrigatorio') !== null ? 'precisa-codigo' : 'invalido'
  }
  throw new Error('Falha no login: ' + resposta.status)
}

export async function sair(): Promise<void> {
  await api('POST', '/api/logout')
  await renovarCsrf()
}
