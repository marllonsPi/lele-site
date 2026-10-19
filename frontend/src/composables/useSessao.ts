import { readonly, ref } from 'vue'
import { carregarEu, entrar as entrarApi, sair as sairApi, type Eu, type Perfil, type ResultadoLogin } from '../api/sessao'
import { definirAoExpirar } from '../api/http'

const eu = ref<Eu | null>(null)
const carregada = ref(false)

let aoPerder: () => void = () => {}

/** Registra o que fazer (ex.: ir ao login) quando a sessao expira durante o uso. */
export function aoPerderSessao(acao: () => void): void {
  aoPerder = acao
}

definirAoExpirar(() => {
  eu.value = null
  aoPerder()
})

/** Le a sessao do servidor (uma vez, ou de novo com forcar). */
async function carregar(forcar = false): Promise<Eu | null> {
  if (carregada.value && !forcar) {
    return eu.value
  }
  try {
    eu.value = await carregarEu()
  } catch {
    eu.value = null
  }
  carregada.value = true
  return eu.value
}

async function entrar(email: string, senha: string, codigo?: string): Promise<ResultadoLogin> {
  const resultado = await entrarApi(email, senha, codigo)
  if (resultado === 'ok') {
    await carregar(true)
  }
  return resultado
}

async function sair(): Promise<void> {
  try {
    await sairApi()
  } finally {
    eu.value = null
  }
}

/** Esconde na tela o que o perfil nao pode; quem decide de verdade e o servidor. */
function temPerfil(...perfis: Perfil[]): boolean {
  return eu.value !== null && perfis.includes(eu.value.perfil)
}

/** So para testes: volta ao estado inicial. */
function reiniciar(): void {
  eu.value = null
  carregada.value = false
}

export function useSessao() {
  return { eu: readonly(eu), carregada: readonly(carregada), carregar, entrar, sair, temPerfil, reiniciar }
}
