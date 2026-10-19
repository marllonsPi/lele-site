<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { carregarEu, type Eu } from './api/sessao'
import InicioView from './views/InicioView.vue'
import LoginView from './views/LoginView.vue'

const eu = ref<Eu | null>(null)
const carregando = ref(true)

async function atualizar() {
  try {
    eu.value = await carregarEu()
  } catch (e) {
    eu.value = null
  } finally {
    carregando.value = false
  }
}

onMounted(atualizar)
</script>

<template>
  <p v-if="carregando" class="carregando">Carregando...</p>
  <InicioView v-else-if="eu" :eu="eu" @saiu="eu = null" />
  <LoginView v-else @autenticado="atualizar" />
</template>
