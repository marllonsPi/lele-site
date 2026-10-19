<script setup lang="ts">
import { useSessao } from '../composables/useSessao'

const { eu } = useSessao()
</script>

<template>
  <section>
    <h1>Olá, {{ eu?.nome }}</h1>
    <p>Sistema em construção. As telas de agenda, prazos, clientes e processos chegam por etapas.</p>
  </section>
</template>
