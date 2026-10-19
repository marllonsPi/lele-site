import { ref } from 'vue'
import { ApiError } from '../api/http'

/** Mensagens de erro/sucesso e execucao de acoes com tratamento uniforme de ProblemDetail. */
export function mensagemDeErro(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.detalhe) {
      return e.detalhe
    }
    if (e.status === 403) {
      return 'Você não tem permissão para esta ação.'
    }
    if (e.status === 404) {
      return 'Não encontrado.'
    }
  }
  return 'Não foi possível concluir a operação.'
}

export function useFeedback() {
  const erro = ref('')
  const aviso = ref('')
  const ocupado = ref(false)

  function limpar() {
    erro.value = ''
    aviso.value = ''
  }

  /**
   * Executa a acao; devolve true se deu certo.
   * Com `manter`, e uma recarga de fundo: nao apaga o aviso anterior nem o substitui.
   */
  async function executar(acao: () => Promise<unknown>, sucesso = '', manter = false): Promise<boolean> {
    if (!manter) {
      limpar()
    }
    ocupado.value = true
    try {
      await acao()
      if (!manter) {
        aviso.value = sucesso
      }
      return true
    } catch (e) {
      erro.value = mensagemDeErro(e)
      return false
    } finally {
      ocupado.value = false
    }
  }

  return { erro, aviso, ocupado, limpar, executar }
}
