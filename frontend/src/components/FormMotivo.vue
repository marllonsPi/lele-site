<script setup lang="ts">
import { ref } from 'vue'

const props = withDefaults(defineProps<{ rotulo: string; botao: string; obrigatorio?: boolean }>(), { obrigatorio: true })
const emit = defineEmits<{ (e: 'enviar', texto: string): void; (e: 'cancelar'): void }>()
const texto = ref('')

function enviar() {
  const t = texto.value.trim()
  if (props.obrigatorio && !t) {
    return
  }
  emit('enviar', t)
}
</script>

<template>
  <form class="linha-form" @submit.prevent="enviar">
    <label>
      {{ rotulo }}
      <textarea v-model="texto" rows="2" maxlength="2000" :required="obrigatorio"></textarea>
    </label>
    <div class="grupo-botoes">
      <button type="submit" class="botao-primario">{{ botao }}</button>
      <button type="button" class="botao-secundario" @click="emit('cancelar')">Cancelar</button>
    </div>
  </form>
</template>
