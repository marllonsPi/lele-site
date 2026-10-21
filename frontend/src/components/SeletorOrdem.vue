<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Ordenacao } from '../composables/useOrdenacao'
import Modal from './Modal.vue'

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const props = defineProps<{ ordem: Ordenacao<any>; total?: number }>()

const aberto = ref(false)
const chave = ref('')
const direcao = ref<'asc' | 'desc'>('asc')
const grupo = ref('')

const ordemAtual = computed(() => props.ordem.criterios.find((c) => c.chave === props.ordem.chave.value))
const grupoAtual = computed(() => props.ordem.agrupadores.find((a) => a.chave === props.ordem.agrupar.value))

function abrir() {
  chave.value = props.ordem.chave.value
  direcao.value = props.ordem.direcao.value
  grupo.value = props.ordem.agrupar.value
  aberto.value = true
}

function aplicar() {
  props.ordem.aplicarEscolha(chave.value, direcao.value, grupo.value)
  aberto.value = false
}

function restaurar() {
  props.ordem.restaurar()
  aberto.value = false
}
</script>

<template>
  <div class="organizar">
    <button type="button" class="botao-secundario" @click="abrir">Organizar lista</button>
    <p class="organizar-resumo suave" aria-live="polite">
      <template v-if="total !== undefined">{{ total }} {{ total === 1 ? 'item' : 'itens' }} · </template>
      Ordenado por <b>{{ ordemAtual?.rotulo }}</b> ({{ ordem.direcao.value === 'asc' ? 'crescente' : 'decrescente' }})<template v-if="grupoAtual">
        · agrupado por <b>{{ grupoAtual.rotulo }}</b></template
      >
    </p>

    <Modal :aberto="aberto" titulo="Como você quer organizar esta lista?" form-id="f-organizar" salvar-rotulo="Aplicar" @fechar="aberto = false">
      <form id="f-organizar" class="organizar-form" @submit.prevent="aplicar">
        <fieldset>
          <legend>1. Ordenar por</legend>
          <label v-for="c in ordem.criterios" :key="c.chave" class="opcao-linha">
            <input v-model="chave" type="radio" name="org-chave" :value="c.chave" /> {{ c.rotulo }}
          </label>
        </fieldset>

        <fieldset>
          <legend>2. Em que sentido</legend>
          <label class="opcao-linha"><input v-model="direcao" type="radio" name="org-dir" value="asc" /> Crescente (A → Z, mais antigo primeiro, menor primeiro)</label>
          <label class="opcao-linha"><input v-model="direcao" type="radio" name="org-dir" value="desc" /> Decrescente (Z → A, mais recente primeiro, maior primeiro)</label>
        </fieldset>

        <fieldset v-if="ordem.agrupadores.length">
          <legend>3. Separar em grupos?</legend>
          <label class="opcao-linha"><input v-model="grupo" type="radio" name="org-grupo" value="" /> Não separar</label>
          <label v-for="a in ordem.agrupadores" :key="a.chave" class="opcao-linha">
            <input v-model="grupo" type="radio" name="org-grupo" :value="a.chave" /> Agrupar por {{ a.rotulo }}
          </label>
        </fieldset>

        <button v-if="ordem.personalizada.value" type="button" class="botao-link" @click="restaurar">Voltar à organização padrão</button>
      </form>
    </Modal>
  </div>
</template>

<style scoped>
.organizar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--espaco-2) var(--espaco-3);
  margin: var(--espaco-3) 0;
}
.organizar-resumo {
  margin: 0;
  font-size: 0.9rem;
}
.organizar-form {
  display: grid;
  gap: var(--espaco-3);
}
.organizar-form fieldset {
  display: grid;
  gap: var(--espaco-2);
  margin: 0;
  padding: var(--espaco-3);
  border: 1px solid var(--cor-borda);
  border-radius: var(--raio);
}
.organizar-form legend {
  padding: 0 var(--espaco-2);
  font-weight: 700;
}
</style>
