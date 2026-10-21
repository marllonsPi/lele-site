<script setup lang="ts">
import { az, azPor, azRotulos } from '../utils/ordenacao'
import { computed, onMounted, ref } from 'vue'
import {
  adicionarOab,
  atualizarUsuario,
  criarUsuario,
  desativarOab,
  desativarUsuario,
  listarUsuarios,
  reativarOab,
  reativarUsuario,
  redefinirSenha,
  tornarOabPrincipal,
  type DadosProfissionais,
  type Usuario,
} from '../api/cadastros'
import {
  atualizarFiltro,
  criarFiltro,
  dadosDoFiltro,
  excluirFiltro,
  listarFiltros,
  resumoDoFiltro,
  ROTULO_FONTE,
  type DadosFiltro,
  type FiltroCaptura,
} from '../api/publicacoes'
import { ROTULO_PERFIL, type Perfil } from '../api/sessao'
import Feedback from '../components/Feedback.vue'
import Modal from '../components/Modal.vue'
import SeletorOrdem from '../components/SeletorOrdem.vue'
import { useOrdenacao } from '../composables/useOrdenacao'
import { useFeedback } from '../composables/useFeedback'
import { useSessao } from '../composables/useSessao'
import { useUsuarios } from '../composables/useUsuarios'

type Aba = 'dados' | 'contato' | 'oabs' | 'captura' | 'acesso'
const ABAS: { id: Aba; rotulo: string; soExistente?: boolean }[] = [
  { id: 'dados', rotulo: 'Dados e perfil' },
  { id: 'contato', rotulo: 'Contato e alertas' },
  { id: 'oabs', rotulo: 'OABs', soExistente: true },
  { id: 'captura', rotulo: 'Captura', soExistente: true },
  { id: 'acesso', rotulo: 'Acesso', soExistente: true },
]

const { erro, aviso, executar } = useFeedback()
const { carregar: recarregarNomes } = useUsuarios()
const { eu } = useSessao()

const usuarios = ref<Usuario[]>([])
const filtros = ref<FiltroCaptura[]>([])
const carregando = ref(true)
const busca = ref('')
const mostrarInativos = ref(false)

const editando = ref<number | 'novo' | null>(null)
const aba = ref<Aba>('dados')
const form = ref(vazio())
const novaSenha = ref('')
const oab = ref({ numero: '', uf: 'DF', principal: false })
const filtro = ref<DadosFiltro>(filtroVazio())
const filtroEditando = ref<FiltroCaptura | null>(null)

const perfis = Object.keys(ROTULO_PERFIL) as Perfil[]
const euMestre = computed(() => eu.value?.mestre === true)
const supervisores = computed(() => usuarios.value.filter((u) => u.ativo && u.perfil === 'ADVOGADO'))
const atual = computed(() => (typeof editando.value === 'number' ? usuarios.value.find((u) => u.id === editando.value) ?? null : null))
/** Conta mestre so e alterada por outro mestre (o servidor tambem recusa). */
const protegida = computed(() => atual.value?.mestre === true && !euMestre.value && atual.value.id !== eu.value?.id)
const temOab = computed(() => form.value.perfil === 'SOCIA' || form.value.perfil === 'ADVOGADO')
const abasVisiveis = computed(() =>
  ABAS.filter((a) => (!a.soExistente || editando.value !== 'novo') && ((a.id !== 'oabs' && a.id !== 'captura') || temOab.value)),
)
const filtrosDaConta = computed(() => filtros.value.filter((f) => f.usuarioId === atual.value?.id))

const ordem = useOrdenacao<Usuario>('contas', [
  { chave: 'nome', rotulo: 'Nome', valor: (u) => u.nome },
  { chave: 'perfil', rotulo: 'Perfil', valor: (u) => ROTULO_PERFIL[u.perfil] },
  { chave: 'cargo', rotulo: 'Cargo', valor: (u) => u.cargo },
  { chave: 'recentes', rotulo: 'Cadastro mais recente', valor: (u) => -u.id },
], [
  { chave: 'perfil', rotulo: 'Perfil', valor: (u) => ROTULO_PERFIL[u.perfil] },
  { chave: 'situacao', rotulo: 'Situação', valor: (u) => (u.ativo ? 'Ativas' : 'Inativas') },
])

const lista = computed(() => {
  const q = busca.value.trim().toLowerCase()
  return usuarios.value
    .filter((u) => (mostrarInativos.value || u.ativo) && (!q || (u.nome + ' ' + u.email + ' ' + (u.cargo ?? '')).toLowerCase().includes(q)))
})

const gruposLista = computed(() => ordem.grupos(lista.value))

function filtroVazio(): DadosFiltro {
  return { fonte: 'DJEN', modo: 'IGNORAR', tribunalPrefixo: '', excecoes: '', ufs: '', oabNumero: '', oabUf: '' }
}

function vazio() {
  return {
    nome: '',
    email: '',
    perfil: 'ADVOGADO' as Perfil,
    supervisorId: null as number | null,
    senha: '',
    mestre: false,
    telefone: '',
    whatsapp: '',
    cargo: '',
    emailAlertas: '',
    alertasEmail: true,
    assinatura: '',
  }
}

function profissionais(): DadosProfissionais {
  const v = form.value
  return {
    telefone: v.telefone || null,
    whatsapp: v.whatsapp || null,
    cargo: v.cargo || null,
    emailAlertas: v.emailAlertas || null,
    alertasEmail: v.alertasEmail,
    assinatura: v.assinatura || null,
  }
}

function iniciais(nome: string): string {
  return nome
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]!.toUpperCase())
    .join('')
}

async function recarregar() {
  await executar(async () => {
    ;[usuarios.value, filtros.value] = await Promise.all([listarUsuarios(euMestre.value), listarFiltros()])
  }, '', true)
  carregando.value = false
  await recarregarNomes(true)
}

function abrirNovo() {
  form.value = vazio()
  aba.value = 'dados'
  editando.value = 'novo'
}

function abrirEdicao(u: Usuario, abaInicial: Aba = 'dados') {
  form.value = {
    nome: u.nome,
    email: u.email,
    perfil: u.perfil,
    supervisorId: u.supervisorId,
    senha: '',
    mestre: u.mestre,
    telefone: u.telefone ?? '',
    whatsapp: u.whatsapp ?? '',
    cargo: u.cargo ?? '',
    emailAlertas: u.emailAlertas ?? '',
    alertasEmail: u.alertasEmail,
    assinatura: u.assinatura ?? '',
  }
  novaSenha.value = ''
  aba.value = abaInicial
  editando.value = u.id
  document.getElementById('conteudo')?.scrollIntoView?.({ behavior: 'smooth' })
}

function fechar() {
  editando.value = null
}

async function salvar() {
  const id = editando.value
  const v = form.value
  const dados = {
    nome: v.nome,
    email: v.email,
    perfil: v.perfil,
    supervisorId: v.perfil === 'ESTAGIARIO' ? v.supervisorId : null,
    profissionais: profissionais(),
    mestre: v.perfil === 'SOCIA' && v.mestre,
  }
  let novoId: number | null = null
  const ok = await executar(async () => {
    if (id === 'novo') {
      novoId = (await criarUsuario({ ...dados, senha: v.senha })).id
    } else {
      await atualizarUsuario(id as number, dados)
    }
  }, id === 'novo' ? 'Conta criada. Complete as OABs e a captura nas outras abas.' : 'Conta atualizada.')
  if (ok) {
    await recarregar()
    const criada = novoId !== null ? usuarios.value.find((u) => u.id === novoId) : undefined
    if (criada) {
      abrirEdicao(criada, criada.perfil === 'SOCIA' || criada.perfil === 'ADVOGADO' ? 'oabs' : 'dados')
    }
  }
}

async function alternarAtivo(u: Usuario) {
  if (await executar(() => (u.ativo ? desativarUsuario(u.id) : reativarUsuario(u.id)), u.ativo ? 'Conta desativada.' : 'Conta reativada.')) {
    await recarregar()
  }
}

async function trocarSenha(u: Usuario) {
  if (await executar(() => redefinirSenha(u.id, novaSenha.value), 'Senha redefinida.')) {
    novaSenha.value = ''
  }
}

async function incluirOab(u: Usuario) {
  const ok = await executar(
    () => adicionarOab(u.id, oab.value.numero.trim(), oab.value.uf.trim().toUpperCase(), oab.value.principal),
    'OAB cadastrada. A captura de publicações passa a monitorá-la.',
  )
  if (ok) {
    oab.value = { numero: '', uf: oab.value.uf, principal: false }
    await recarregar()
  }
}

async function alterarOab(acao: () => Promise<unknown>, mensagem: string) {
  if (await executar(acao, mensagem)) {
    await recarregar()
  }
}

async function incluirFiltro(u: Usuario) {
  const dados = { ...filtro.value, tribunalPrefixo: filtro.value.tribunalPrefixo.trim(), excecoes: filtro.value.excecoes.trim() }
  const editado = filtroEditando.value
  const ok = await executar(
    () => (editado ? atualizarFiltro(editado.id, { ...dados, ativo: editado.ativo }) : criarFiltro({ usuarioId: u.id, ...dados })),
    editado ? 'Filtro atualizado.' : 'Filtro criado.',
  )
  if (ok) {
    cancelarEdicaoFiltro()
    await recarregar()
  }
}

function editarFiltro(f: FiltroCaptura) {
  filtroEditando.value = f
  filtro.value = dadosDoFiltro(f)
}

function cancelarEdicaoFiltro() {
  filtroEditando.value = null
  filtro.value = { ...filtroVazio(), fonte: filtro.value.fonte }
}

/** Escolha de OAB no formulario: "numero/UF" ou vazio (todas). */
const oabDoFiltro = computed({
  get: () => (filtro.value.oabNumero ? `${filtro.value.oabNumero}/${filtro.value.oabUf}` : ''),
  set: (v: string) => {
    const [n, uf] = v ? v.split('/') : ['', '']
    filtro.value = { ...filtro.value, oabNumero: n ?? '', oabUf: uf ?? '' }
  },
})

async function alternarFiltro(f: FiltroCaptura) {
  const msg = f.ativo ? 'Filtro desativado.' : 'Filtro reativado.'
  if (await executar(() => atualizarFiltro(f.id, { ...dadosDoFiltro(f), ativo: !f.ativo }), msg)) {
    await recarregar()
  }
}

async function removerFiltro(f: FiltroCaptura) {
  if (!window.confirm(`Excluir definitivamente o filtro "${f.tribunalPrefixo}"? O que ele já ignorou continua no registro de ignorados.`)) {
    return
  }
  if (await executar(() => excluirFiltro(f.id), 'Filtro excluído.')) {
    if (filtroEditando.value?.id === f.id) {
      cancelarEdicaoFiltro()
    }
    await recarregar()
  }
}

onMounted(recarregar)
</script>

<template>
  <section>
    <div class="cabecalho-pagina">
      <div>
        <h1>Contas e OABs</h1>
        <p class="suave">Tudo que o sistema precisa saber de cada pessoa: perfil, OABs, alertas, filtros de captura e acesso.</p>
      </div>
      <button type="button" class="botao-primario" @click="abrirNovo">Nova conta</button>
    </div>

    <Feedback :erro="erro" :aviso="aviso" />

    <Modal :aberto="editando !== null" :titulo="editando === 'novo' ? 'Nova conta' : form.nome || 'Editar conta'" larg="lg" sem-rodape :erro="erro" @fechar="fechar">
      <p v-if="atual?.mestre"><span class="selo selo-mestre">Acesso mestre</span></p>
      <p v-if="protegida" class="erro">Esta conta tem acesso mestre e só pode ser alterada por outro acesso mestre.</p>

      <div class="abas" role="tablist">
        <button
          v-for="a in abasVisiveis"
          :key="a.id"
          type="button"
          role="tab"
          :aria-selected="aba === a.id"
          @click="aba = a.id"
        >
          {{ a.rotulo }}
        </button>
      </div>

      <form v-if="aba === 'dados' || aba === 'contato'" class="formulario sem-moldura" @submit.prevent="salvar">
        <fieldset :disabled="protegida" class="grade-form">
          <template v-if="aba === 'dados'">
            <label>Nome <input v-model="form.nome" required maxlength="200" /></label>
            <label>E-mail de login <input v-model="form.email" type="email" required maxlength="254" /></label>
            <label>
              Perfil
              <select v-model="form.perfil" :disabled="atual?.mestre">
                <option v-for="p in azPor(perfis, ROTULO_PERFIL)" :key="p" :value="p">{{ ROTULO_PERFIL[p] }}</option>
              </select>
            </label>
            <label v-if="form.perfil === 'ESTAGIARIO'">
              Supervisor(a)
              <select v-model="form.supervisorId" required>
                <option :value="null" disabled>Selecione um advogado</option>
                <option v-for="s in az(supervisores, (x) => x.nome)" :key="s.id" :value="s.id">{{ s.nome }}</option>
              </select>
            </label>
            <label v-if="editando === 'novo'">
              Senha inicial (mínimo 12 caracteres)
              <input v-model="form.senha" type="password" required minlength="12" autocomplete="new-password" />
            </label>
            <label v-if="euMestre && form.perfil === 'SOCIA' && atual?.id !== eu?.id" class="marcar largo">
              <input v-model="form.mestre" type="checkbox" /> Acesso mestre (administra o sistema e vê tudo)
            </label>
            <p v-if="form.perfil === 'ESTAGIARIO'" class="suave largo">Estagiário só vê o que lhe for atribuído e nunca confere prazos.</p>
            <p v-if="form.perfil === 'ASSISTENTE'" class="suave largo">
              Assistente começa sem acesso aos processos: libere os titulares em <router-link to="/visibilidade">Visibilidade</router-link>.
            </p>
          </template>
          <template v-else>
            <label>Telefone <input v-model="form.telefone" maxlength="30" autocomplete="off" /></label>
            <label>WhatsApp <input v-model="form.whatsapp" maxlength="30" autocomplete="off" /></label>
            <label>Cargo / função <input v-model="form.cargo" maxlength="80" /></label>
            <label>E-mail para alertas (se diferente do login) <input v-model="form.emailAlertas" type="email" maxlength="254" /></label>
            <label class="marcar largo"><input v-model="form.alertasEmail" type="checkbox" /> Receber alertas de prazos e audiências por e-mail</label>
            <label class="largo">
              Assinatura (relatórios e e-mails)
              <textarea v-model="form.assinatura" maxlength="500" rows="3" />
            </label>
          </template>
        </fieldset>
        <div class="grupo-botoes largo">
          <button type="submit" class="botao-primario" :disabled="protegida">{{ editando === 'novo' ? 'Criar conta' : 'Salvar' }}</button>
          <button type="button" class="botao-secundario" @click="fechar">Cancelar</button>
        </div>
      </form>

      <div v-else-if="aba === 'oabs' && atual">
        <p class="suave">A captura diária de publicações monitora todas as OABs ativas (DJEN). Adicionar ou reativar uma OAB já a inclui na captura.</p>
        <ul class="lista-simples">
          <li v-for="o in atual.oabs" :key="o.id" :class="{ suave: !o.ativa }">
            <b>OAB {{ o.numero }}/{{ o.uf }}</b>
            <span v-if="o.principal" class="selo"> principal</span>
            <span v-if="!o.ativa" class="selo"> inativa</span>
            <template v-if="!protegida">
              <button v-if="o.ativa && !o.principal" type="button" class="botao-link" @click="alterarOab(() => tornarOabPrincipal(o.id), 'OAB principal definida.')">tornar principal</button>
              <button v-if="o.ativa" type="button" class="botao-link" @click="alterarOab(() => desativarOab(o.id), 'OAB desativada.')">desativar</button>
              <button v-else type="button" class="botao-link" @click="alterarOab(() => reativarOab(o.id), 'OAB reativada.')">reativar</button>
            </template>
          </li>
        </ul>
        <p v-if="atual.oabs.length === 0" class="vazio">Nenhuma OAB cadastrada. Sem OAB, nada é capturado para esta pessoa.</p>
        <form v-if="!protegida" class="grupo-botoes" @submit.prevent="incluirOab(atual)">
          <label>Número <input v-model="oab.numero" required maxlength="20" /></label>
          <label>UF <input v-model="oab.uf" required minlength="2" maxlength="2" size="2" /></label>
          <label class="marcar"><input v-model="oab.principal" type="checkbox" /> Principal</label>
          <button type="submit" class="botao-primario">Adicionar OAB</button>
        </form>
      </div>

      <div v-else-if="aba === 'captura' && atual">
        <p class="suave">
          Filtros decidem o que entra na fila desta pessoa. Ex.: prefixo "TRT" com exceção "TRT-10" ignora os TRT, menos o TRT-10.
          "Só trazer estes estados" (ex.: GO) descobre o estado pelo número do processo; o que não dá para identificar (Justiça Federal, tribunais superiores, e-mail sem número) continua entrando. O que o filtro ignora fica no registro de ignorados, nunca some.
        </p>
        <div class="tabela-rolavel" v-if="filtrosDaConta.length">
          <table>
            <thead>
              <tr><th scope="col">Fonte</th><th scope="col">Regra</th><th scope="col"><span class="sr-only">Ação</span></th></tr>
            </thead>
            <tbody>
              <tr v-for="f in filtrosDaConta" :key="f.id" :class="{ suave: !f.ativo }">
                <td>{{ ROTULO_FONTE[f.fonte] }}</td>
                <td>{{ resumoDoFiltro(f) }}</td>
                <td v-if="!protegida">
                  <button type="button" class="botao-link" @click="editarFiltro(f)">editar</button>
                  <button type="button" class="botao-link" @click="alternarFiltro(f)">{{ f.ativo ? 'desativar' : 'reativar' }}</button>
                  <button type="button" class="botao-link" @click="removerFiltro(f)">excluir</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-else class="vazio">Sem filtros: tudo o que chegar para as OABs desta pessoa entra na fila.</p>
        <form v-if="!protegida" class="grupo-botoes" @submit.prevent="incluirFiltro(atual)">
          <label>
            Fonte
            <select v-model="filtro.fonte">
              <option v-for="[f, rotulo] in azRotulos(ROTULO_FONTE)" :key="f" :value="f">{{ rotulo }}</option>
            </select>
          </label>
          <label>
            Regra
            <select v-model="filtro.modo">
              <option value="IGNORAR">Ignorar tribunal</option>
              <option value="SO_UF">Só trazer estes estados</option>
            </select>
          </label>
          <template v-if="filtro.modo === 'IGNORAR'">
            <label>Tribunal (prefixo) <input v-model="filtro.tribunalPrefixo" required maxlength="40" placeholder="TRT" /></label>
            <label>Exceções (vírgula) <input v-model="filtro.excecoes" placeholder="TRT-10" /></label>
          </template>
          <label v-else>Estados (UF, vírgula) <input v-model="filtro.ufs" required maxlength="60" placeholder="GO" /></label>
          <label>
            Vale para a OAB
            <select v-model="oabDoFiltro">
              <option value="">Todas as OABs</option>
              <option v-for="o in atual.oabs.filter((x) => x.ativa)" :key="o.id" :value="`${o.numero}/${o.uf}`">OAB {{ o.numero }}/{{ o.uf }}</option>
            </select>
          </label>
          <button type="submit" class="botao-secundario">{{ filtroEditando ? 'Salvar filtro' : 'Adicionar filtro' }}</button>
          <button v-if="filtroEditando" type="button" class="botao-link" @click="cancelarEdicaoFiltro">Cancelar edição</button>
        </form>
      </div>

      <div v-else-if="aba === 'acesso' && atual">
        <p>
          <b>O que esta pessoa enxerga</b> é definido na matriz de <router-link to="/visibilidade">Visibilidade</router-link>
          <template v-if="atual.mestre"> (o acesso mestre ignora a matriz e vê tudo)</template>.
        </p>
        <form v-if="!protegida" class="grupo-botoes" @submit.prevent="trocarSenha(atual)">
          <label>
            Redefinir senha
            <input v-model="novaSenha" type="password" required minlength="12" autocomplete="new-password" />
          </label>
          <button type="submit" class="botao-secundario">Redefinir senha</button>
        </form>
        <div class="grupo-botoes">
          <button v-if="!protegida && atual.id !== eu?.id" type="button" class="botao-secundario" @click="alternarAtivo(atual)">
            {{ atual.ativo ? 'Desativar conta' : 'Reativar conta' }}
          </button>
        </div>
        <p class="suave">Contas desativadas não entram no sistema, mas o histórico (auditoria, prazos, publicações) é preservado.</p>
      </div>
    </Modal>

    <div class="barra-ferramentas">
      <label class="busca">
        <span class="sr-only">Buscar</span>
        <input v-model="busca" type="search" placeholder="Buscar por nome, e-mail ou cargo" />
      </label>
      <label class="marcar"><input v-model="mostrarInativos" type="checkbox" /> Mostrar inativas</label>
    </div>
    <SeletorOrdem :ordem="ordem" :total="lista.length" />
    <p v-if="carregando">Carregando...</p>
    <p v-else-if="lista.length === 0" class="vazio">Nenhuma conta encontrada.</p>

    <template v-for="g in gruposLista" :key="g.titulo">
      <h2 v-if="g.titulo" class="grupo-titulo">{{ g.titulo }} <span class="suave">({{ g.itens.length }})</span></h2>
      <article v-for="u in g.itens" :key="u.id"  class="cartao cartao-conta" :class="{ inativo: !u.ativo, mestre: u.mestre }">
      <header class="cartao-topo">
        <span class="avatar" aria-hidden="true">{{ iniciais(u.nome) }}</span>
        <b>{{ u.nome }}</b>
        <span class="selo">{{ ROTULO_PERFIL[u.perfil] }}</span>
        <span v-if="u.mestre" class="selo selo-mestre">Acesso mestre</span>
        <span v-if="!u.ativo" class="selo">Inativa</span>
      </header>
      <p class="suave">
        {{ u.email }}<template v-if="u.cargo"> · {{ u.cargo }}</template
        ><template v-if="u.telefone || u.whatsapp"> · {{ u.whatsapp || u.telefone }}</template>
      </p>
      <p v-if="u.supervisorId" class="suave">Supervisor(a): {{ usuarios.find((s) => s.id === u.supervisorId)?.nome ?? '#' + u.supervisorId }}</p>
      <p v-if="u.perfil === 'SOCIA' || u.perfil === 'ADVOGADO'">
        <template v-if="u.oabs.filter((o) => o.ativa).length">
          <span v-for="o in u.oabs.filter((x) => x.ativa)" :key="o.id" class="selo">OAB {{ o.numero }}/{{ o.uf }}<template v-if="o.principal"> ★</template></span>
        </template>
        <span v-else-if="!u.mestre" class="perigo">Sem OAB ativa: nada é capturado.</span>
      </p>
      <p v-if="!u.alertasEmail" class="suave">Alertas por e-mail desligados.</p>
      <div class="cartao-acoes">
        <button type="button" class="botao-secundario" @click="abrirEdicao(u)">Editar</button>
        <button v-if="u.perfil === 'SOCIA' || u.perfil === 'ADVOGADO'" type="button" class="botao-secundario" @click="abrirEdicao(u, 'oabs')">OABs</button>
        <button type="button" class="botao-secundario" @click="abrirEdicao(u, 'acesso')">Acesso</button>
      </div>
      </article>
    </template>
  </section>
</template>

<style scoped>
.sem-moldura {
  margin: 0;
  padding: 0;
  border: 0;
  box-shadow: none;
  background: none;
}

.grade-form {
  grid-column: 1 / -1;
  display: grid;
  gap: var(--espaco-3);
  min-width: 0;
  padding: 0;
  border: 0;
}

.largo {
  grid-column: 1 / -1;
}

.busca {
  flex: 1;
  min-width: 14rem;
}

@media (min-width: 48rem) {
  .grade-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
