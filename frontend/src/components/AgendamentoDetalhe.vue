<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  anexarArquivo,
  carregarAnexos,
  carregarHistoricoConferencia,
  carregarObservacoes,
  registrarObservacao,
  urlDownload,
  type Anexo,
  type Conferencia,
  type Observacao,
} from '../api/agenda'
import { ApiError } from '../api/http'
import { useUsuarios } from '../composables/useUsuarios'
import { formatarDataHora, formatarTamanho } from '../utils/datas'

const props = defineProps<{ id: number }>()
const { nome } = useUsuarios()

const observacoes = ref<Observacao[]>([])
const anexos = ref<Anexo[]>([])
const historico = ref<Conferencia[]>([])
const texto = ref('')
const erro = ref('')
const carregando = ref(true)
const enviando = ref(false)

function mensagem(e: unknown): string {
  return e instanceof ApiError && e.detalhe ? e.detalhe : 'Não foi possível concluir a operação.'
}

async function carregar() {
  try {
    ;[observacoes.value, anexos.value, historico.value] = await Promise.all([
      carregarObservacoes(props.id),
      carregarAnexos(props.id),
      carregarHistoricoConferencia(props.id),
    ])
  } catch (e) {
    erro.value = mensagem(e)
  } finally {
    carregando.value = false
  }
}

async function adicionarObservacao() {
  erro.value = ''
  enviando.value = true
  try {
    await registrarObservacao(props.id, texto.value.trim())
    texto.value = ''
    observacoes.value = await carregarObservacoes(props.id)
  } catch (e) {
    erro.value = mensagem(e)
  } finally {
    enviando.value = false
  }
}

async function enviarArquivo(ev: Event) {
  const campo = ev.target as HTMLInputElement
  const arquivo = campo.files?.[0]
  if (!arquivo) {
    return
  }
  erro.value = ''
  enviando.value = true
  try {
    await anexarArquivo(props.id, arquivo)
    anexos.value = await carregarAnexos(props.id)
  } catch (e) {
    erro.value = mensagem(e)
  } finally {
    enviando.value = false
    campo.value = ''
  }
}

onMounted(carregar)
</script>

<template>
  <div class="detalhe" :aria-busy="carregando">
    <p v-if="carregando">Carregando...</p>
    <p v-if="erro" class="erro" role="alert">{{ erro }}</p>

    <section>
      <h3>Observações</h3>
      <p v-if="!carregando && observacoes.length === 0" class="suave">Nenhuma observação.</p>
      <ul class="lista-simples">
        <li v-for="o in observacoes" :key="o.id">
          <span class="suave">{{ nome(o.autorId) }} · {{ formatarDataHora(o.criadoEm) }}</span>
          <p class="texto-livre">{{ o.texto }}</p>
        </li>
      </ul>
      <form class="linha-form" @submit.prevent="adicionarObservacao">
        <label>
          Nova observação
          <textarea v-model="texto" rows="2" maxlength="5000" required></textarea>
        </label>
        <button type="submit" class="botao-secundario" :disabled="enviando || !texto.trim()">Registrar</button>
      </form>
    </section>

    <section>
      <h3>Anexos</h3>
      <p v-if="!carregando && anexos.length === 0" class="suave">Nenhum anexo.</p>
      <ul class="lista-simples">
        <li v-for="a in anexos" :key="a.id">
          <a :href="urlDownload(a.id)">{{ a.nome }}</a>
          <span class="suave"> · {{ formatarTamanho(a.tamanho) }} · {{ nome(a.autorId) }} · {{ formatarDataHora(a.criadoEm) }}</span>
        </li>
      </ul>
      <label>
        Anexar peça ou documento (PDF, DOCX, DOC, XLSX, XLS, ODT, PNG, JPG)
        <input type="file" accept=".pdf,.docx,.doc,.xlsx,.xls,.odt,.png,.jpg,.jpeg" :disabled="enviando" @change="enviarArquivo" />
      </label>
    </section>

    <section v-if="historico.length">
      <h3>Histórico de conferência</h3>
      <ul class="lista-simples">
        <li v-for="c in historico" :key="c.id">
          <b>{{ c.etapa }}</b> — {{ c.resultado }}
          <span class="suave"> · {{ nome(c.usuarioId) }} · {{ formatarDataHora(c.criadoEm) }}</span>
          <p v-if="c.motivo" class="texto-livre">Motivo: {{ c.motivo }}</p>
        </li>
      </ul>
    </section>
  </div>
</template>
