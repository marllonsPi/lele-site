<script setup lang="ts">
import { azPor } from '../utils/ordenacao'
import { computed, onMounted, ref } from 'vue'
import {
  AREAS,
  atualizarCliente,
  criarCliente,
  desativarCliente,
  listarClientes,
  reativarCliente,
  ROTULO_AREA,
  type AreaAtuacao,
  type Cliente,
  type DadosCliente,
} from '../api/cadastros'
import DocumentosCliente from '../components/DocumentosCliente.vue'
import Feedback from '../components/Feedback.vue'
import Modal from '../components/Modal.vue'
import SeletorOrdem from '../components/SeletorOrdem.vue'
import { useOrdenacao } from '../composables/useOrdenacao'
import { useFeedback } from '../composables/useFeedback'
import { useSessao } from '../composables/useSessao'

const { temPerfil } = useSessao()
const { erro, aviso, executar } = useFeedback()

const clientes = ref<Cliente[]>([])
const ordem = useOrdenacao<Cliente>('clientes', [
  { chave: 'nome', rotulo: 'Nome', valor: (c) => c.nome },
  { chave: 'incompletos', rotulo: 'Cadastro incompleto primeiro', valor: (c) => (c.cadastroIncompleto || c.aConferir ? 0 : 1) },
  { chave: 'area', rotulo: 'Área de atuação', valor: (c) => c.areas.map((a) => ROTULO_AREA[a]).sort().join(' ') },
  { chave: 'recentes', rotulo: 'Cadastro mais recente', valor: (c) => c.id },
], [
  { chave: 'area', rotulo: 'Área de atuação', valor: (c) => c.areas.map((a) => ROTULO_AREA[a]).sort().join(' · ') },
  { chave: 'situacao', rotulo: 'Situação do cadastro', valor: (c) => (!c.ativo ? 'Inativos' : c.cadastroIncompleto || c.aConferir ? 'Cadastro a completar' : 'Cadastro em dia') },
])
const carregando = ref(true)
const busca = ref('')
const areaFiltro = ref<AreaAtuacao | ''>('')
const comDocumentos = ref<Set<number>>(new Set())

function alternarDocumentos(id: number) {
  const s = new Set(comDocumentos.value)
  if (!s.delete(id)) {
    s.add(id)
  }
  comDocumentos.value = s
}
const editando = ref<number | 'novo' | null>(null)
const form = ref<DadosCliente>(vazio())

const ehSocia = computed(() => temPerfil('SOCIA'))
const incompletos = computed(() => clientes.value.filter((c) => c.ativo && c.cadastroIncompleto).length)
const filtrados = computed(() => {
  const t = busca.value.trim().toLowerCase()
  return clientes.value.filter(
    (c) =>
      (!areaFiltro.value || c.areas.includes(areaFiltro.value)) &&
      (!t || c.nome.toLowerCase().includes(t) || (c.documento ?? '').includes(t)),
  )
})

const gruposLista = computed(() => ordem.grupos(filtrados.value))

function textoAreas(c: Cliente): string {
  return c.areas.map((x) => ROTULO_AREA[x]).join(' · ')
}

function vazio(): DadosCliente {
  return { nome: '', documento: '', email: '', telefone: '', endereco: '', observacoes: '', areas: [] }
}

async function recarregar() {
  await executar(async () => {
    clientes.value = await listarClientes()
  }, '', true)
  carregando.value = false
}

function abrirNovo() {
  form.value = vazio()
  editando.value = 'novo'
}

function abrirEdicao(c: Cliente) {
  form.value = {
    nome: c.nome,
    documento: c.documento ?? '',
    email: c.email ?? '',
    telefone: c.telefone ?? '',
    endereco: c.endereco ?? '',
    observacoes: c.observacoes ?? '',
    areas: [...c.areas],
  }
  editando.value = c.id
}

async function salvar() {
  const id = editando.value
  if (form.value.areas.length === 0) {
    erro.value = 'Marque ao menos uma área de atuação.'
    return
  }
  const ok = await executar(
    () => (id === 'novo' ? criarCliente(form.value) : atualizarCliente(id as number, form.value)),
    id === 'novo' ? 'Cliente cadastrado.' : 'Cliente atualizado.',
  )
  if (ok) {
    editando.value = null
    await recarregar()
  }
}

async function alternarAtivo(c: Cliente) {
  const ok = await executar(
    () => (c.ativo ? desativarCliente(c.id) : reativarCliente(c.id)),
    c.ativo ? 'Cliente desativado.' : 'Cliente reativado.',
  )
  if (ok) {
    await recarregar()
  }
}

onMounted(recarregar)
</script>

<template>
  <section>
    <h1>Clientes</h1>
    <div class="barra-ferramentas">
      <label>
        Buscar
        <input v-model="busca" type="search" placeholder="Nome ou documento" />
      </label>
      <label>
        Área de atuação
        <select v-model="areaFiltro">
          <option value="">Todas</option>
          <option v-for="a in azPor(AREAS, ROTULO_AREA)" :key="a" :value="a">{{ ROTULO_AREA[a] }}</option>
        </select>
      </label>
      <button v-if="ehSocia" type="button" class="botao-primario" @click="abrirNovo">Novo cliente</button>
    </div>

    <Modal :aberto="editando !== null" :titulo="editando === 'novo' ? 'Novo cliente' : 'Editar cliente'" form-id="f-salvar-949" salvar-rotulo="Salvar" :erro="erro" @fechar="editando = null">
      <form id="f-salvar-949" class="formulario" @submit.prevent="salvar">
        <label>Nome <input v-model="form.nome" required maxlength="200" /></label>
        <label>Documento (CPF/CNPJ) <input v-model="form.documento" maxlength="18" /></label>
        <label>E-mail <input v-model="form.email" type="email" maxlength="254" /></label>
        <label>Telefone <input v-model="form.telefone" maxlength="30" /></label>
        <label>Endereço <input v-model="form.endereco" maxlength="400" /></label>
        <fieldset>
          <legend>Área de atuação (obrigatória; marque uma ou mais)</legend>
          <label v-for="a in AREAS" :key="a" class="opcao-linha">
            <input v-model="form.areas" type="checkbox" :value="a" /> {{ ROTULO_AREA[a] }}
          </label>
        </fieldset>
        <label>Observações <textarea v-model="form.observacoes" rows="2" maxlength="5000"></textarea></label>
      </form>
    </Modal>

    <Feedback :erro="erro" :aviso="aviso" />
    <p v-if="incompletos > 0" class="alerta-incompleto" role="alert">
      {{ incompletos }} cliente(s) com cadastro incompleto: falta a área de atuação.
    </p>
    <SeletorOrdem :ordem="ordem" :total="filtrados.length" />
    <p v-if="carregando">Carregando...</p>
    <p v-else-if="filtrados.length === 0" class="suave">Nenhum cliente.</p>

    <template v-for="g in gruposLista" :key="g.titulo">
      <h2 v-if="g.titulo" class="grupo-titulo">{{ g.titulo }} <span class="suave">({{ g.itens.length }})</span></h2>
      <article v-for="c in g.itens" :key="c.id"  class="cartao" :class="{ inativo: !c.ativo }">
      <header class="cartao-topo">
        <b>{{ c.nome }}</b>
        <span v-if="!c.ativo" class="selo">Inativo</span>
        <span v-if="c.cadastroIncompleto" class="selo selo-pendente">Cadastro incompleto: falta a área de atuação</span>
        <span v-if="c.aConferir" class="selo selo-atencao">Importado: conferir cadastro</span>
      </header>
      <p v-if="c.areas.length" class="suave">{{ textoAreas(c) }}</p>
      <p class="suave">
        <template v-if="c.documento">{{ c.documento }} · </template>
        <template v-if="c.email">{{ c.email }} · </template>
        <template v-if="c.telefone">{{ c.telefone }}</template>
      </p>
      <p v-if="c.endereco" class="suave">{{ c.endereco }}</p>
      <p v-if="c.observacoes" class="texto-livre">{{ c.observacoes }}</p>
      <div class="cartao-acoes">
        <router-link class="botao-secundario como-botao" :to="{ name: 'processos', query: { cliente: c.id } }">Processos</router-link>
        <button type="button" class="botao-secundario" :aria-expanded="comDocumentos.has(c.id)" @click="alternarDocumentos(c.id)">Documentos</button>
        <template v-if="ehSocia">
          <button type="button" class="botao-secundario" @click="abrirEdicao(c)">Editar</button>
          <button type="button" class="botao-secundario" @click="alternarAtivo(c)">{{ c.ativo ? 'Desativar' : 'Reativar' }}</button>
        </template>
      </div>
      <DocumentosCliente v-if="comDocumentos.has(c.id)" :cliente-id="c.id" :pode-enviar="ehSocia" />
      </article>
    </template>
  </section>
</template>
