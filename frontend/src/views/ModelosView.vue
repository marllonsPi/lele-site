<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { criarModelo, desativarModelo, listarAreas, listarModelos, urlDownloadModelo, type Modelo } from '../api/relatorios'
import { ROTULO_TIPO } from '../api/agenda'
import Feedback from '../components/Feedback.vue'
import { useFeedback } from '../composables/useFeedback'
import { useSessao } from '../composables/useSessao'
import { formatarTamanho } from '../utils/datas'

const TIPOS = ['INI', 'CON', 'MAN', 'REC'] as const

const { temPerfil } = useSessao()
const { erro, aviso, executar } = useFeedback()

const modelos = ref<Modelo[]>([])
const areas = ref<string[]>([])
const carregando = ref(true)
const filtroTipo = ref('')
const filtroArea = ref('')
const novo = ref(false)
const form = ref({ tipo: 'CON', area: '', titulo: '' })
const arquivo = ref<File | null>(null)

const ehSocia = computed(() => temPerfil('SOCIA'))

async function recarregar() {
  await executar(async () => {
    ;[modelos.value, areas.value] = await Promise.all([listarModelos(filtroTipo.value || undefined, filtroArea.value || undefined), listarAreas()])
  }, '', true)
  carregando.value = false
}

function escolherArquivo(ev: Event) {
  arquivo.value = (ev.target as HTMLInputElement).files?.[0] ?? null
}

async function cadastrar() {
  if (!arquivo.value) {
    return
  }
  const f = arquivo.value
  if (await executar(() => criarModelo(form.value.tipo, form.value.area.trim(), form.value.titulo.trim(), f), 'Modelo cadastrado.')) {
    form.value = { tipo: 'CON', area: '', titulo: '' }
    arquivo.value = null
    novo.value = false
    await recarregar()
  }
}

async function desativar(m: Modelo) {
  if (await executar(() => desativarModelo(m.id), 'Modelo desativado.')) {
    await recarregar()
  }
}

onMounted(recarregar)
</script>

<template>
  <section>
    <h1>Modelos de peças</h1>
    <div class="barra-ferramentas">
      <label>
        Tipo
        <select v-model="filtroTipo" @change="recarregar">
          <option value="">Todos</option>
          <option v-for="t in TIPOS" :key="t" :value="t">{{ t }} · {{ ROTULO_TIPO[t] }}</option>
        </select>
      </label>
      <label>
        Área do direito
        <select v-model="filtroArea" @change="recarregar">
          <option value="">Todas</option>
          <option v-for="a in areas" :key="a" :value="a">{{ a }}</option>
        </select>
      </label>
      <button v-if="ehSocia" type="button" class="botao-primario" @click="novo = !novo">Novo modelo</button>
    </div>

    <form v-if="novo" class="formulario" @submit.prevent="cadastrar">
      <label>
        Tipo
        <select v-model="form.tipo">
          <option v-for="t in TIPOS" :key="t" :value="t">{{ t }} · {{ ROTULO_TIPO[t] }}</option>
        </select>
      </label>
      <label>Área do direito <input v-model="form.area" list="areas-modelo" required /></label>
      <datalist id="areas-modelo"><option v-for="a in areas" :key="a" :value="a" /></datalist>
      <label>Título <input v-model="form.titulo" required maxlength="200" /></label>
      <label>Arquivo <input type="file" required accept=".pdf,.docx,.doc,.odt" @change="escolherArquivo" /></label>
      <button type="submit" class="botao-primario">Cadastrar</button>
    </form>

    <Feedback :erro="erro" :aviso="aviso" />
    <p v-if="carregando">Carregando...</p>
    <p v-else-if="modelos.length === 0" class="suave">Nenhum modelo.</p>

    <article v-for="m in modelos" :key="m.id" class="cartao">
      <header class="cartao-topo">
        <span class="tipo"><span class="bolinha" :class="'tipo-' + m.tipo" aria-hidden="true"></span><b>{{ m.tipo }}</b></span>
        <b>{{ m.titulo }}</b>
      </header>
      <p class="suave">{{ m.areaDireito }} · {{ m.nomeArquivo }} · {{ formatarTamanho(m.tamanho) }}</p>
      <div class="cartao-acoes">
        <a class="botao-secundario como-botao" :href="urlDownloadModelo(m.id)">Baixar</a>
        <button v-if="ehSocia" type="button" class="botao-secundario" @click="desativar(m)">Desativar</button>
      </div>
    </article>
  </section>
</template>
