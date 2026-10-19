import { ApiError, requisitar } from './http'

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

/** Retorna o usuario logado ou null quando nao ha sessao (401). */
export async function carregarEu(): Promise<Eu | null> {
  try {
    const resposta = await requisitar('GET', '/api/me')
    return (await resposta.json()) as Eu
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) {
      return null
    }
    throw e
  }
}

/** Retorna true quando o login foi aceito; false para credenciais invalidas. */
export async function entrar(email: string, senha: string): Promise<boolean> {
  const corpo = new URLSearchParams({ email: email, senha: senha })
  try {
    await requisitar('POST', '/api/login', corpo)
    return true
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) {
      return false
    }
    throw e
  }
}

export async function sair(): Promise<void> {
  await requisitar('POST', '/api/logout')
}
