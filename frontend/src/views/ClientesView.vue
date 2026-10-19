<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  atualizarCliente,
  criarCliente,
  desativarCliente,
  listarClientes,
  reativarCliente,
  type Cliente,
  type DadosCliente,
} from '../api/cadastros'
import Feedback from '../components/Feedback.vue'
import { useFeedback } from '../composables/useFeedback'
import { useSessao } from '../composables/useSessao'

const { temPerfil } = useSessao()
const { erro, aviso, executar } = useFeedback()

const clientes = ref<Cliente[]>([])
const carregando = ref(true)
const busca = ref('')
const editando = ref<number | 'novo' | null>(null)
const form = ref<DadosCliente>(vazio())

const ehSocia = computed(() => temPerfil('SOCIA'))
const filtrados = computed(() => {
  const t = busca.value.trim().toLowerCase()
  return t ? clientes.value.filter((c) => c.nome.toLowerCase().includes(t) || (c.documento ?? '').includes(t)) : clientes.value
})

function vazio(): DadosCliente {
  return { nome: '', documento: '', email: '', telefone: '', endereco: '', observacoes: '' }
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
  }
  editando.value = c.id
}

async function salvar() {
  const id = editando.value
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
      <button v-if="ehSocia" type="button" class="botao-primario" @click="abrirNovo">Novo cliente</button>
    </div>

    <form v-if="editando !== null" class="formulario" @submit.prevent="salvar">
      <label>Nome <input v-model="form.nome" required maxlength="200" /></label>
      <label>Documento (CPF/CNPJ) <input v-model="form.documento" maxlength="18" /></label>
      <label>E-mail <input v-model="form.email" type="email" maxlength="254" /></label>
      <label>Telefone <input v-model="form.telefone" maxlength="30" /></label>
      <label>Endereço <input v-model="form.endereco" maxlength="400" /></label>
      <label>Observações <textarea v-model="form.observacoes" rows="2" maxlength="5000"></textarea></label>
      <div class="grupo-botoes">
        <button type="submit" class="botao-primario">Salvar</button>
        <button type="button" class="botao-secundario" @click="editando = null">Cancelar</button>
      </div>
    </form>

    <Feedback :erro="erro" :aviso="aviso" />
    <p v-if="carregando">Carregando...</p>
    <p v-else-if="filtrados.length === 0" class="suave">Nenhum cliente.</p>

    <article v-for="c in filtrados" :key="c.id" class="cartao" :class="{ inativo: !c.ativo }">
      <header class="cartao-topo">
        <b>{{ c.nome }}</b>
        <span v-if="!c.ativo" class="selo">Inativo</span>
      </header>
      <p class="suave">
        <template v-if="c.documento">{{ c.documento }} · </template>
        <template v-if="c.email">{{ c.email }} · </template>
        <template v-if="c.telefone">{{ c.telefone }}</template>
      </p>
      <p v-if="c.endereco" class="suave">{{ c.endereco }}</p>
      <p v-if="c.observacoes" class="texto-livre">{{ c.observacoes }}</p>
      <div class="cartao-acoes">
        <router-link class="botao-secundario como-botao" :to="{ name: 'processos', query: { cliente: c.id } }">Processos</router-link>
        <template v-if="ehSocia">
          <button type="button" class="botao-secundario" @click="abrirEdicao(c)">Editar</button>
          <button type="button" class="botao-secundario" @click="alternarAtivo(c)">{{ c.ativo ? 'Desativar' : 'Reativar' }}</button>
        </template>
      </div>
    </article>
  </section>
</template>
