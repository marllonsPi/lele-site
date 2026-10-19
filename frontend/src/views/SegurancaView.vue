<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  confirmarDoisFatores,
  desativarDoisFatores,
  iniciarDoisFatores,
  statusDoisFatores,
  type CadastroDoisFatores,
} from '../api/relatorios'
import Feedback from '../components/Feedback.vue'
import { useFeedback } from '../composables/useFeedback'

const { erro, aviso, executar } = useFeedback()

const ativo = ref<boolean | null>(null)
const cadastro = ref<CadastroDoisFatores | null>(null)
const codigo = ref('')

async function carregar() {
  await executar(async () => {
    ativo.value = (await statusDoisFatores()).ativo
  }, '', true)
}

async function iniciar() {
  await executar(async () => {
    cadastro.value = await iniciarDoisFatores()
  })
}

async function confirmar() {
  if (await executar(() => confirmarDoisFatores(codigo.value.trim()), 'Verificação em duas etapas ativada.')) {
    cadastro.value = null
    codigo.value = ''
    await carregar()
  }
}

async function desativar() {
  if (await executar(() => desativarDoisFatores(codigo.value.trim()), 'Verificação em duas etapas desativada.')) {
    codigo.value = ''
    await carregar()
  }
}

onMounted(carregar)
</script>

<template>
  <section>
    <h1>Segurança da conta</h1>
    <Feedback :erro="erro" :aviso="aviso" />
    <p v-if="ativo === null">Carregando...</p>

    <template v-else-if="ativo">
      <p>A verificação em duas etapas está <b>ativa</b>. O login pede o código do aplicativo autenticador.</p>
      <form class="formulario" @submit.prevent="desativar">
        <label>
          Código atual do aplicativo (para desativar)
          <input v-model="codigo" inputmode="numeric" autocomplete="one-time-code" maxlength="6" pattern="[0-9]{6}" required />
        </label>
        <button type="submit" class="botao-secundario">Desativar verificação em duas etapas</button>
      </form>
    </template>

    <template v-else>
      <p>
        A verificação em duas etapas está <b>desativada</b>. Ative para proteger a conta com um código do aplicativo
        autenticador (Google Authenticator, Authy, 1Password...).
      </p>
      <button v-if="!cadastro" type="button" class="botao-primario" @click="iniciar">Ativar</button>
      <template v-else>
        <p>No aplicativo, adicione uma conta manualmente com esta chave e depois informe o código de 6 dígitos:</p>
        <p><code class="segredo">{{ cadastro.segredo }}</code></p>
        <p class="suave">Ou abra o endereço de configuração no aparelho: <code class="segredo">{{ cadastro.uri }}</code></p>
        <form class="formulario" @submit.prevent="confirmar">
          <label>
            Código de 6 dígitos
            <input v-model="codigo" inputmode="numeric" autocomplete="one-time-code" maxlength="6" pattern="[0-9]{6}" required />
          </label>
          <button type="submit" class="botao-primario">Confirmar e ativar</button>
        </form>
      </template>
    </template>
  </section>
</template>
