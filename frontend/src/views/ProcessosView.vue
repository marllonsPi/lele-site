<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  adicionarParte,
  AREAS,
  atualizarProcesso,
  criarProcesso,
  desativarProcesso,
  listarClientes,
  listarProcessos,
  ROTULO_AREA,
  ROTULO_PAPEL,
  type AreaAtuacao,
  type Cliente,
  type DadosProcesso,
  type PapelParte,
  type Processo,
} from '../api/cadastros'
import AnexosPainel from '../components/AnexosPainel.vue'
import Feedback from '../components/Feedback.vue'
import Modal from '../components/Modal.vue'
import SeletorOrdem from '../components/SeletorOrdem.vue'
import { useOrdenacao } from '../composables/useOrdenacao'
import { az, azPor, azRotulos, porNome } from '../utils/ordenacao'
import { useFeedback } from '../composables/useFeedback'
import { useSessao } from '../composables/useSessao'
import { useUsuarios } from '../composables/useUsuarios'

const rota = useRoute()
const { temPerfil } = useSessao()
const { usuarios, carregar: carregarUsuarios, nome } = useUsuarios()
const { erro, aviso, executar } = useFeedback()

const processos = ref<Processo[]>([])
const ordem = useOrdenacao<Processo>('processos', [
  { chave: 'recentes', rotulo: 'Mais recentes', valor: (p) => -p.id },
  { chave: 'cliente', rotulo: 'Cliente', valor: (p) => p.clienteNome },
  { chave: 'numero', rotulo: 'Número do processo', valor: (p) => p.numero },
  { chave: 'tribunal', rotulo: 'Tribunal', valor: (p) => p.tribunal },
  { chave: 'area', rotulo: 'Área de atuação', valor: (p) => (p.area ? ROTULO_AREA[p.area] : null) },
  { chave: 'responsavel', rotulo: 'Responsável', valor: (p) => nome(p.advogadoId) },
], [
  { chave: 'cliente', rotulo: 'Cliente', valor: (p) => p.clienteNome },
  { chave: 'tribunal', rotulo: 'Tribunal', valor: (p) => p.tribunal },
  { chave: 'area', rotulo: 'Área de atuação', valor: (p) => (p.area ? ROTULO_AREA[p.area] : null) },
  { chave: 'responsavel', rotulo: 'Responsável', valor: (p) => nome(p.advogadoId) },
  { chave: 'situacao', rotulo: 'Situação', valor: (p) => (p.ativo ? 'Ativos' : 'Inativos') },
])
const gruposLista = computed(() => ordem.grupos(processos.value))
const clientes = ref<Cliente[]>([])
const carregando = ref(true)
const clienteFiltro = ref<number | null>(rota.query.cliente ? Number(rota.query.cliente) : null)
const areaFiltro = ref<AreaAtuacao | ''>('')
const editando = ref<number | 'novo' | null>(null)
const abertos = ref<Set<number>>(new Set())
const form = ref<DadosProcesso>(vazio())
const novaParte = ref<{ nome: string; papel: PapelParte }>({ nome: '', papel: 'CONTRARIA' })

const ehSocia = computed(() => temPerfil('SOCIA'))
const advogados = computed(() => porNome(usuarios.value.filter((u) => u.ativo && (u.perfil === 'SOCIA' || u.perfil === 'ADVOGADO')), (u) => u.nome))
const clientesOrdenados = computed(() => porNome(clientes.value, (c) => c.nome))

function vazio(): DadosProcesso {
  return { numero: '', tribunal: '', varaComarca: '', classe: '', situacao: '', valorCausa: null, clienteId: 0, advogadoId: 0, area: null }
}

async function recarregar() {
  await executar(async () => {
    processos.value = await listarProcessos(clienteFiltro.value ?? undefined, areaFiltro.value || null)
  }, '', true)
  carregando.value = false
}

async function prepararFormulario() {
  if (clientes.value.length === 0) {
    await executar(async () => {
      clientes.value = (await listarClientes()).filter((c) => c.ativo)
    })
  }
}

async function abrirNovo() {
  await prepararFormulario()
  form.value = { ...vazio(), clienteId: clienteFiltro.value ?? 0 }
  editando.value = 'novo'
}

async function abrirEdicao(p: Processo) {
  await prepararFormulario()
  form.value = {
    numero: p.numero,
    tribunal: p.tribunal,
    varaComarca: p.varaComarca ?? '',
    classe: p.classe ?? '',
    situacao: p.situacao ?? '',
    valorCausa: p.valorCausa,
    clienteId: p.clienteId,
    advogadoId: p.advogadoId,
    area: p.area,
  }
  editando.value = p.id
}

async function salvar() {
  const id = editando.value
  const ok = await executar(
    () => (id === 'novo' ? criarProcesso(form.value) : atualizarProcesso(id as number, form.value)),
    id === 'novo' ? 'Processo cadastrado.' : 'Processo atualizado.',
  )
  if (ok) {
    editando.value = null
    await recarregar()
  }
}

async function desativar(p: Processo) {
  if (await executar(() => desativarProcesso(p.id), 'Processo desativado.')) {
    await recarregar()
  }
}

async function incluirParte(p: Processo) {
  const ok = await executar(() => adicionarParte(p.id, novaParte.value.nome.trim(), novaParte.value.papel), 'Parte adicionada.')
  if (ok) {
    novaParte.value = { nome: '', papel: 'CONTRARIA' }
    await recarregar()
  }
}

function alternar(id: number) {
  const s = new Set(abertos.value)
  if (!s.delete(id)) {
    s.add(id)
  }
  abertos.value = s
}

onMounted(async () => {
  await carregarUsuarios()
  await Promise.all([prepararFormulario(), recarregar()])
})
</script>

<template>
  <section>
    <h1>Processos</h1>
    <div class="barra-ferramentas">
      <label>
        Cliente
        <select v-model="clienteFiltro" @change="recarregar">
          <option :value="null">Todos</option>
          <option v-for="c in az(clientesOrdenados, (x) => x.nome)" :key="c.id" :value="c.id">{{ c.nome }}</option>
          <option v-if="clienteFiltro && !clientes.some((c) => c.id === clienteFiltro)" :value="clienteFiltro">
            Cliente #{{ clienteFiltro }}
          </option>
        </select>
      </label>
      <label>
        Área de atuação
        <select v-model="areaFiltro" @change="recarregar">
          <option value="">Todas</option>
          <option v-for="a in azPor(AREAS, ROTULO_AREA)" :key="a" :value="a">{{ ROTULO_AREA[a] }}</option>
        </select>
      </label>
      <button v-if="ehSocia" type="button" class="botao-primario" @click="abrirNovo">Novo processo</button>
    </div>

    <Modal :aberto="editando !== null" :titulo="editando === 'novo' ? 'Novo processo' : 'Editar processo'" form-id="f-salvar-949" salvar-rotulo="Salvar" :erro="erro" @fechar="editando = null">
      <form id="f-salvar-949" class="formulario" @submit.prevent="salvar">
        <label>
          Número (CNJ)
          <input v-model="form.numero" required maxlength="30" :disabled="editando !== 'novo'" />
        </label>
        <label>Tribunal <input v-model="form.tribunal" required maxlength="40" /></label>
        <label>
          Cliente
          <select v-model="form.clienteId" required>
            <option :value="0" disabled>Selecione</option>
            <option v-for="c in az(clientesOrdenados, (x) => x.nome)" :key="c.id" :value="c.id">{{ c.nome }}</option>
          </select>
        </label>
        <label>
          Advogado responsável
          <select v-model="form.advogadoId" required>
            <option :value="0" disabled>Selecione</option>
            <option v-for="u in az(advogados, (x) => x.nome)" :key="u.id" :value="u.id">{{ u.nome }}</option>
          </select>
        </label>
        <label>
          Área de atuação
          <select v-model="form.area" required>
            <option :value="null" disabled>Selecione</option>
            <option v-for="a in azPor(AREAS, ROTULO_AREA)" :key="a" :value="a">{{ ROTULO_AREA[a] }}</option>
          </select>
        </label>
        <label>Vara / comarca <input v-model="form.varaComarca" maxlength="200" /></label>
        <label>Classe <input v-model="form.classe" maxlength="200" /></label>
        <label>Situação <input v-model="form.situacao" maxlength="100" /></label>
        <label>Valor da causa <input v-model.number="form.valorCausa" type="number" min="0" step="0.01" /></label>
      </form>
    </Modal>

    <Feedback :erro="erro" :aviso="aviso" />
    <SeletorOrdem :ordem="ordem" :total="processos.length" />
    <p v-if="carregando">Carregando...</p>
    <p v-else-if="processos.length === 0" class="suave">Nenhum processo visível para você.</p>

    <template v-for="g in gruposLista" :key="g.titulo">
      <h2 v-if="g.titulo" class="grupo-titulo">{{ g.titulo }} <span class="suave">({{ g.itens.length }})</span></h2>
      <article v-for="p in g.itens" :key="p.id"  class="cartao" :class="{ inativo: !p.ativo }">
      <header class="cartao-topo">
        <b>{{ p.numeroFormatado }}</b>
        <span v-if="!p.ativo" class="selo">Inativo</span>
        <span v-if="p.area" class="selo">{{ ROTULO_AREA[p.area] }}</span>
        <span v-else class="selo selo-pendente">Área pendente</span>
        <span class="cartao-quando">{{ p.tribunal }}</span>
      </header>
      <p>{{ p.clienteNome }}</p>
      <p class="suave">
        Responsável: {{ nome(p.advogadoId) }}<template v-if="p.classe"> · {{ p.classe }}</template
        ><template v-if="p.situacao"> · {{ p.situacao }}</template>
      </p>
      <div class="cartao-acoes">
        <button type="button" class="botao-secundario" :aria-expanded="abertos.has(p.id)" @click="alternar(p.id)">
          {{ abertos.has(p.id) ? 'Ocultar detalhes' : 'Partes e anexos' }}
        </button>
        <template v-if="ehSocia">
          <button type="button" class="botao-secundario" @click="abrirEdicao(p)">Editar</button>
          <button v-if="p.ativo" type="button" class="botao-secundario" @click="desativar(p)">Desativar</button>
        </template>
      </div>
      <div v-if="abertos.has(p.id)" class="detalhe">
        <h3>Partes</h3>
        <p v-if="p.partes.length === 0" class="suave">Nenhuma parte.</p>
        <ul class="lista-simples">
          <li v-for="x in p.partes" :key="x.id">{{ x.nome }} <span class="suave">({{ ROTULO_PAPEL[x.papel] }})</span></li>
        </ul>
        <form v-if="ehSocia" class="grupo-botoes" @submit.prevent="incluirParte(p)">
          <label>Nome da parte <input v-model="novaParte.nome" required maxlength="200" /></label>
          <label>
            Papel
            <select v-model="novaParte.papel">
              <option v-for="[v, r] in azRotulos(ROTULO_PAPEL)" :key="v" :value="v">{{ r }}</option>
            </select>
          </label>
          <button type="submit" class="botao-secundario">Adicionar parte</button>
        </form>
        <AnexosPainel :base="`/api/processos/${p.id}/anexos`" />
      </div>
      </article>
    </template>
  </section>
</template>
