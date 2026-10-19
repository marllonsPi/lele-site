<script setup lang="ts">
import { ROTULO_TIPO, type TipoAgendamento } from '../api/agenda'

defineProps<{ tipo: TipoAgendamento }>()
</script>

<template>
  <span class="tipo">
    <span class="bolinha" :class="'tipo-' + tipo" aria-hidden="true"></span>
    <b>{{ tipo }}</b>
    <span class="sr-only">{{ ROTULO_TIPO[tipo] }}</span>
  </span>
</template>
