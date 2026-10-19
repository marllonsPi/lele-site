<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api/http'
import { urlDownload, type Anexo } from '../api/agenda'
import { mensagemDeErro } from '../composables/useFeedback'
import { useUsuarios } from '../composables/useUsuarios'
import { formatarDataHora, formatarTamanho } from '../utils/datas'

const props = defineProps<{ base: string }>()
const { nome } = useUsuarios()
const anexos = ref<Anexo[]>([])
const erro = ref('')
const enviando = ref(false)

async function carregar() {
  try {
    anexos.value = await api<Anexo[]>('GET', props.base)
  } catch (e) {
    erro.value = mensagemDeErro(e)
  }
}

async function enviar(ev: Event) {
  const campo = ev.target as HTMLInputElement
  const arquivo = campo.files?.[0]
  if (!arquivo) {
    return
  }
  erro.value = ''
  enviando.value = true
  try {
    const dados = new FormData()
    dados.append('arquivo', arquivo)
    await api('POST', props.base, { multipart: dados })
    await carregar()
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    enviando.value = false
    campo.value = ''
  }
}

onMounted(carregar)
</script>

<template>
  <section>
    <h3>Anexos</h3>
    <p v-if="erro" class="erro" role="alert">{{ erro }}</p>
    <p v-if="anexos.length === 0" class="suave">Nenhum anexo.</p>
    <ul class="lista-simples">
      <li v-for="a in anexos" :key="a.id">
        <a :href="urlDownload(a.id)">{{ a.nome }}</a>
        <span class="suave"> · {{ formatarTamanho(a.tamanho) }} · {{ nome(a.autorId) }} · {{ formatarDataHora(a.criadoEm) }}</span>
      </li>
    </ul>
    <label>
      Anexar peça ou documento (PDF, DOCX, DOC, XLSX, XLS, ODT, PNG, JPG)
      <input type="file" accept=".pdf,.docx,.doc,.xlsx,.xls,.odt,.png,.jpg,.jpeg" :disabled="enviando" @change="enviar" />
    </label>
  </section>
</template>
