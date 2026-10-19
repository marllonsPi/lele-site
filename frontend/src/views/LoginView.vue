<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSessao } from '../composables/useSessao'

const { entrar } = useSessao()
const roteador = useRouter()
const rota = useRoute()

const email = ref('')
const senha = ref('')
const codigo = ref('')
const pedeCodigo = ref(false)
const erro = ref('')
const enviando = ref(false)
const campoCodigo = ref<HTMLInputElement | null>(null)

/** So volta para caminhos internos do app (evita redirecionamento aberto). */
function destino(): string {
  const volta = rota.query.volta
  return typeof volta === 'string' && volta.startsWith('/') && !volta.startsWith('//') ? volta : '/'
}

async function enviar() {
  erro.value = ''
  enviando.value = true
  try {
    const resultado = await entrar(email.value, senha.value, pedeCodigo.value ? codigo.value : undefined)
    if (resultado === 'ok') {
      await roteador.replace(destino())
    } else if (resultado === 'precisa-codigo') {
      pedeCodigo.value = true
      await nextTick()
      campoCodigo.value?.focus()
    } else if (resultado === 'muitas-tentativas') {
      erro.value = 'Muitas tentativas. Aguarde alguns minutos e tente de novo.'
    } else {
      erro.value = pedeCodigo.value ? 'Código inválido ou expirado.' : 'E-mail ou senha inválidos.'
      codigo.value = ''
    }
  } catch {
    erro.value = 'Não foi possível entrar agora. Tente novamente.'
  } finally {
    enviando.value = false
  }
}

function voltar() {
  pedeCodigo.value = false
  codigo.value = ''
  senha.value = ''
  erro.value = ''
}
</script>

<template>
  <main class="login" id="conteudo">
    <h1>Crispim &amp; Campos Advocacia</h1>
    <form @submit.prevent="enviar">
      <template v-if="!pedeCodigo">
        <label>
          E-mail
          <input v-model="email" type="email" autocomplete="username" required />
        </label>
        <label>
          Senha
          <input v-model="senha" type="password" autocomplete="current-password" required />
        </label>
      </template>
      <template v-else>
        <p>Informe o código de 6 dígitos do aplicativo autenticador.</p>
        <label>
          Código de verificação
          <input
            ref="campoCodigo"
            v-model="codigo"
            inputmode="numeric"
            autocomplete="one-time-code"
            pattern="[0-9]{6}"
            maxlength="6"
            required
          />
        </label>
      </template>
      <p v-if="erro" class="erro" role="alert">{{ erro }}</p>
      <button type="submit" class="botao-primario" :disabled="enviando">
        {{ enviando ? 'Entrando...' : pedeCodigo ? 'Confirmar' : 'Entrar' }}
      </button>
      <button v-if="pedeCodigo" type="button" class="botao-secundario" @click="voltar">Voltar</button>
    </form>
  </main>
</template>
