<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  adicionarOab,
  atualizarUsuario,
  criarUsuario,
  desativarOab,
  desativarUsuario,
  listarUsuarios,
  reativarUsuario,
  redefinirSenha,
  type DadosUsuario,
  type Usuario,
} from '../api/cadastros'
import { ROTULO_PERFIL, type Perfil } from '../api/sessao'
import Feedback from '../components/Feedback.vue'
import { useFeedback } from '../composables/useFeedback'
import { useUsuarios } from '../composables/useUsuarios'

const { erro, aviso, executar } = useFeedback()
const { carregar: recarregarNomes } = useUsuarios()

const usuarios = ref<Usuario[]>([])
const carregando = ref(true)
const editando = ref<number | 'novo' | null>(null)
const form = ref<DadosUsuario & { senha: string }>(vazio())
const senhaDe = ref<number | null>(null)
const novaSenha = ref('')
const oabDe = ref<number | null>(null)
const oab = ref({ numero: '', uf: 'DF', principal: false })

const perfis = Object.keys(ROTULO_PERFIL) as Perfil[]
const supervisores = computed(() => usuarios.value.filter((u) => u.ativo && (u.perfil === 'SOCIA' || u.perfil === 'ADVOGADO')))

function vazio() {
  return { nome: '', email: '', perfil: 'ADVOGADO' as Perfil, supervisorId: null as number | null, senha: '' }
}

async function recarregar() {
  await executar(async () => {
    usuarios.value = await listarUsuarios()
  }, '', true)
  carregando.value = false
  await recarregarNomes(true)
}

function abrirNovo() {
  form.value = vazio()
  editando.value = 'novo'
}

function abrirEdicao(u: Usuario) {
  form.value = { nome: u.nome, email: u.email, perfil: u.perfil, supervisorId: u.supervisorId, senha: '' }
  editando.value = u.id
}

async function salvar() {
  const id = editando.value
  const { senha, ...dados } = form.value
  const ok = await executar(
    () => (id === 'novo' ? criarUsuario({ ...dados, senha }) : atualizarUsuario(id as number, dados)),
    id === 'novo' ? 'Usuário criado.' : 'Usuário atualizado.',
  )
  if (ok) {
    editando.value = null
    await recarregar()
  }
}

async function alternarAtivo(u: Usuario) {
  if (await executar(() => (u.ativo ? desativarUsuario(u.id) : reativarUsuario(u.id)), u.ativo ? 'Usuário desativado.' : 'Usuário reativado.')) {
    await recarregar()
  }
}

async function trocarSenha(u: Usuario) {
  if (await executar(() => redefinirSenha(u.id, novaSenha.value), 'Senha redefinida.')) {
    senhaDe.value = null
    novaSenha.value = ''
  }
}

async function incluirOab(u: Usuario) {
  const ok = await executar(
    () => adicionarOab(u.id, oab.value.numero.trim(), oab.value.uf.trim().toUpperCase(), oab.value.principal),
    'OAB cadastrada.',
  )
  if (ok) {
    oab.value = { numero: '', uf: 'DF', principal: false }
    await recarregar()
  }
}

async function removerOab(id: number) {
  if (await executar(() => desativarOab(id), 'OAB desativada.')) {
    await recarregar()
  }
}

onMounted(recarregar)
</script>

<template>
  <section>
    <h1>Usuários e OABs</h1>
    <div class="barra-ferramentas">
      <button type="button" class="botao-primario" @click="abrirNovo">Novo usuário</button>
    </div>

    <form v-if="editando !== null" class="formulario" @submit.prevent="salvar">
      <label>Nome <input v-model="form.nome" required maxlength="200" /></label>
      <label>E-mail <input v-model="form.email" type="email" required maxlength="254" /></label>
      <label>
        Perfil
        <select v-model="form.perfil">
          <option v-for="p in perfis" :key="p" :value="p">{{ ROTULO_PERFIL[p] }}</option>
        </select>
      </label>
      <label v-if="form.perfil === 'ESTAGIARIO'">
        Supervisor(a)
        <select v-model="form.supervisorId" required>
          <option :value="null" disabled>Selecione</option>
          <option v-for="s in supervisores" :key="s.id" :value="s.id">{{ s.nome }}</option>
        </select>
      </label>
      <label v-if="editando === 'novo'">
        Senha inicial
        <input v-model="form.senha" type="password" required autocomplete="new-password" />
      </label>
      <div class="grupo-botoes">
        <button type="submit" class="botao-primario">Salvar</button>
        <button type="button" class="botao-secundario" @click="editando = null">Cancelar</button>
      </div>
    </form>

    <Feedback :erro="erro" :aviso="aviso" />
    <p v-if="carregando">Carregando...</p>

    <article v-for="u in usuarios" :key="u.id" class="cartao" :class="{ inativo: !u.ativo }">
      <header class="cartao-topo">
        <b>{{ u.nome }}</b>
        <span class="selo">{{ ROTULO_PERFIL[u.perfil] }}</span>
        <span v-if="!u.ativo" class="selo">Inativo</span>
      </header>
      <p class="suave">{{ u.email }}</p>
      <p v-if="u.supervisorId" class="suave">Supervisor(a): {{ usuarios.find((s) => s.id === u.supervisorId)?.nome ?? '#' + u.supervisorId }}</p>
      <ul class="lista-simples">
        <li v-for="o in u.oabs" :key="o.id" :class="{ suave: !o.ativa }">
          OAB {{ o.numero }}/{{ o.uf }}<template v-if="o.principal"> (principal)</template
          ><template v-if="!o.ativa"> (inativa)</template>
          <button v-if="o.ativa" type="button" class="botao-link" @click="removerOab(o.id)">desativar</button>
        </li>
      </ul>
      <div class="cartao-acoes">
        <button type="button" class="botao-secundario" @click="abrirEdicao(u)">Editar</button>
        <button type="button" class="botao-secundario" @click="oabDe = oabDe === u.id ? null : u.id">OAB</button>
        <button type="button" class="botao-secundario" @click="senhaDe = senhaDe === u.id ? null : u.id">Redefinir senha</button>
        <button type="button" class="botao-secundario" @click="alternarAtivo(u)">{{ u.ativo ? 'Desativar' : 'Reativar' }}</button>
      </div>
      <form v-if="oabDe === u.id" class="grupo-botoes" @submit.prevent="incluirOab(u)">
        <label>Número <input v-model="oab.numero" required maxlength="20" /></label>
        <label>UF <input v-model="oab.uf" required minlength="2" maxlength="2" size="2" /></label>
        <label class="marcar"><input v-model="oab.principal" type="checkbox" /> Principal</label>
        <button type="submit" class="botao-secundario">Adicionar OAB</button>
      </form>
      <form v-if="senhaDe === u.id" class="grupo-botoes" @submit.prevent="trocarSenha(u)">
        <label>Nova senha <input v-model="novaSenha" type="password" required autocomplete="new-password" /></label>
        <button type="submit" class="botao-secundario">Redefinir</button>
      </form>
    </article>
  </section>
</template>
