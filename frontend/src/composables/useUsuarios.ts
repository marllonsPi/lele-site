import { readonly, ref } from 'vue'
import { api } from '../api/http'
import type { Perfil } from '../api/sessao'
import { useSessao } from './useSessao'

export interface UsuarioBasico {
  id: number
  nome: string
  perfil: Perfil
  ativo: boolean
}

const usuarios = ref<UsuarioBasico[]>([])
let carregado = false

/**
 * Nomes da equipe para exibir (GET /api/usuarios/nomes, aberto a todos os perfis). Se a chamada falhar,
 * mostra so o proprio nome e "Usuario #id" para os outros.
 */
async function carregar(forcar = false): Promise<void> {
  if (carregado && !forcar) {
    return
  }
  const { eu } = useSessao()
  try {
    usuarios.value = await api<UsuarioBasico[]>('GET', '/api/usuarios/nomes')
  } catch {
    usuarios.value = eu.value ? [{ id: eu.value.id, nome: eu.value.nome, perfil: eu.value.perfil, ativo: true }] : []
  }
  carregado = true
}

function nome(id: number | null | undefined): string {
  if (id === null || id === undefined) {
    return '-'
  }
  return usuarios.value.find((u) => u.id === id)?.nome ?? `Usuário #${id}`
}

/** So para testes. */
function reiniciar(): void {
  usuarios.value = []
  carregado = false
}

export function useUsuarios() {
  return { usuarios: readonly(usuarios), carregar, nome, reiniciar }
}
