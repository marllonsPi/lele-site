<script setup lang="ts">
import { ref } from 'vue'
import { entrar } from '../api/sessao'

const emit = defineEmits<{ (e: 'autenticado'): void }>()

const email = ref('')
const senha = ref('')
const erro = ref('')
const enviando = ref(false)

async function enviar() {
  erro.value = ''
  enviando.value = true
  try {
    if (await entrar(email.value, senha.value)) {
      emit('autenticado')
    } else {
      erro.value = 'E-mail ou senha inválidos.'
    }
  } catch (e) {
    erro.value = 'Não foi possível entrar agora. Tente novamente.'
  } finally {
    enviando.value = false
    senha.value = ''
  }
}
</script>

<template>
  <main class="login">
    <h1>Crispim &amp; Campos Advocacia</h1>
    <form @submit.prevent="enviar">
      <label>
        E-mail
        <input v-model="email" type="email" autocomplete="username" required />
      </label>
      <label>
        Senha
        <input v-model="senha" type="password" autocomplete="current-password" required />
      </label>
      <p v-if="erro" class="erro" role="alert">{{ erro }}</p>
      <button type="submit" :disabled="enviando">{{ enviando ? 'Entrando...' : 'Entrar' }}</button>
    </form>
  </main>
</template>
