<script setup lang="ts">
import { ROTULO_PERFIL, sair, type Eu } from '../api/sessao'

defineProps<{ eu: Eu }>()
const emit = defineEmits<{ (e: 'saiu'): void }>()

async function encerrar() {
  await sair()
  emit('saiu')
}
</script>

<template>
  <main class="inicio">
    <header>
      <h1>Crispim &amp; Campos Advocacia</h1>
      <div>
        <span>{{ eu.nome }} · {{ ROTULO_PERFIL[eu.perfil] }}</span>
        <button type="button" @click="encerrar">Sair</button>
      </div>
    </header>
    <p>Sistema em construção. Próximas telas: agenda, processos, clientes.</p>
  </main>
</template>
