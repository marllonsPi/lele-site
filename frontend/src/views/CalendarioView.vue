<script setup lang="ts">
import { azRotulos } from '../utils/ordenacao'
import { computed, onMounted, ref } from 'vue'
import {
  atualizarFeriado,
  criarFeriado,
  desativarFeriado,
  listarCalendario,
  type DadosFeriado,
  type EscopoFeriado,
  type Feriado,
  type TipoFeriado,
} from '../api/cadastros'
import Feedback from '../components/Feedback.vue'
import Modal from '../components/Modal.vue'
import { useFeedback } from '../composables/useFeedback'
import { useSessao } from '../composables/useSessao'
import { formatarData } from '../utils/datas'

const ROTULO_ESCOPO: Record<EscopoFeriado, string> = { NACIONAL: 'Nacional', ESTADO: 'Estado', MUNICIPIO: 'Município', TRIBUNAL: 'Tribunal' }
const ROTULO_TIPO: Record<TipoFeriado, string> = { FERIADO: 'Feriado', SUSPENSAO: 'Suspensão' }

const { temPerfil } = useSessao()
const { erro, aviso, executar } = useFeedback()

const itens = ref<Feriado[]>([])
const carregando = ref(true)
const editando = ref<number | 'novo' | null>(null)
const form = ref<DadosFeriado>(vazio())

const ehSocia = computed(() => temPerfil('SOCIA'))

function vazio(): DadosFeriado {
  return { escopo: 'NACIONAL', referencia: '', tipo: 'FERIADO', dataInicial: '', dataFinal: null, descricao: '' }
}

async function recarregar() {
  await executar(async () => {
    itens.value = await listarCalendario()
  }, '', true)
  carregando.value = false
}

function abrirNovo() {
  form.value = vazio()
  editando.value = 'novo'
}

function abrirEdicao(f: Feriado) {
  form.value = {
    escopo: f.escopo,
    referencia: f.referencia ?? '',
    tipo: f.tipo,
    dataInicial: f.dataInicial,
    dataFinal: f.dataFinal,
    descricao: f.descricao,
  }
  editando.value = f.id
}

async function salvar() {
  const id = editando.value
  const dados = { ...form.value, dataFinal: form.value.dataFinal || null, referencia: form.value.referencia || null }
  const ok = await executar(
    () => (id === 'novo' ? criarFeriado(dados) : atualizarFeriado(id as number, dados)),
    'Calendário atualizado.',
  )
  if (ok) {
    editando.value = null
    await recarregar()
  }
}

async function desativar(f: Feriado) {
  if (await executar(() => desativarFeriado(f.id), 'Entrada desativada.')) {
    await recarregar()
  }
}

onMounted(recarregar)
</script>

<template>
  <section>
    <h1>Calendário</h1>
    <p class="suave">Feriados e suspensões usados no cálculo do prazo interno (3 dias úteis antes do fatal).</p>
    <div class="barra-ferramentas">
      <button v-if="ehSocia" type="button" class="botao-primario" @click="abrirNovo">Novo feriado ou suspensão</button>
    </div>

    <Modal :aberto="editando !== null" :titulo="editando === 'novo' ? 'Novo feriado ou suspensão' : 'Editar feriado ou suspensão'" form-id="f-salvar-feriado" salvar-rotulo="Salvar" :erro="erro" @fechar="editando = null">
      <form id="f-salvar-feriado" class="formulario" @submit.prevent="salvar">
        <label>
          Escopo
          <select v-model="form.escopo">
            <option v-for="[v, r] in azRotulos(ROTULO_ESCOPO)" :key="v" :value="v">{{ r }}</option>
          </select>
        </label>
        <label>Referência (UF, cidade ou tribunal) <input v-model="form.referencia" maxlength="80" /></label>
        <label>
          Tipo
          <select v-model="form.tipo">
            <option v-for="[v, r] in azRotulos(ROTULO_TIPO)" :key="v" :value="v">{{ r }}</option>
          </select>
        </label>
        <label>Data inicial <input v-model="form.dataInicial" type="date" required /></label>
        <label>Data final (se período) <input v-model="form.dataFinal" type="date" /></label>
        <label>Descrição <input v-model="form.descricao" required maxlength="200" /></label>
      </form>
    </Modal>

    <Feedback :erro="erro" :aviso="aviso" />
    <p v-if="carregando">Carregando...</p>
    <p v-else-if="itens.length === 0" class="suave">Nada cadastrado.</p>

    <div v-else class="tabela-rolavel">
      <table>
        <thead>
          <tr>
            <th scope="col">Quando</th>
            <th scope="col">Escopo</th>
            <th scope="col">Tipo</th>
            <th scope="col">Descrição</th>
            <th v-if="ehSocia" scope="col"><span class="sr-only">Ações</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="f in itens" :key="f.id" :class="{ suave: !f.ativo }">
            <td>
              {{ formatarData(f.dataInicial) }}<template v-if="f.dataFinal"> a {{ formatarData(f.dataFinal) }}</template>
            </td>
            <td>{{ ROTULO_ESCOPO[f.escopo] }}<template v-if="f.referencia"> · {{ f.referencia }}</template></td>
            <td>{{ ROTULO_TIPO[f.tipo] }}</td>
            <td>{{ f.descricao }}<template v-if="!f.ativo"> (inativo)</template></td>
            <td v-if="ehSocia">
              <button type="button" class="botao-link" @click="abrirEdicao(f)">editar</button>
              <button v-if="f.ativo" type="button" class="botao-link" @click="desativar(f)">desativar</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
