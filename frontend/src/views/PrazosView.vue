<script setup lang="ts">
import Modal from '../components/Modal.vue'
import { computed, onMounted, ref } from 'vue'
import {
  carregarPrazos,
  carregarProcessos,
  conferirPrazo,
  lancarPrazo,
  ROTULO_STATUS,
  TIPOS_PRAZO,
  ROTULO_TIPO,
  type Prazo,
  type ProcessoResumo,
  type TipoAgendamento,
} from '../api/agenda'
import { ApiError } from '../api/http'
import AgendamentoDetalhe from '../components/AgendamentoDetalhe.vue'
import BolinhaTipo from '../components/BolinhaTipo.vue'
import SeletorOrdem from '../components/SeletorOrdem.vue'
import { useOrdenacao } from '../composables/useOrdenacao'
import { az, azPor, porNome } from '../utils/ordenacao'
import { useSessao } from '../composables/useSessao'
import { useUsuarios } from '../composables/useUsuarios'
import { formatarData, hojeIso } from '../utils/datas'

const { eu, temPerfil } = useSessao()
const { usuarios, carregar: carregarUsuarios, nome } = useUsuarios()

const prazos = ref<Prazo[]>([])
const ordem = useOrdenacao<Prazo>('prazos', [
  { chave: 'fatal', rotulo: 'Prazo fatal', valor: (p) => p.prazoFatal },
  { chave: 'interno', rotulo: 'Prazo interno', valor: (p) => p.prazoInterno },
  { chave: 'responsavel', rotulo: 'Responsável', valor: (p) => nome(p.responsavelId) },
  { chave: 'tipo', rotulo: 'Tipo', valor: (p) => p.tipo },
  { chave: 'situacao', rotulo: 'Situação', valor: (p) => ROTULO_STATUS[p.status] },
  { chave: 'urgencia', rotulo: 'Urgentes primeiro', valor: (p) => (p.urgente ? 0 : 1) },
], [
  { chave: 'responsavel', rotulo: 'Responsável', valor: (p) => nome(p.responsavelId) },
  { chave: 'tipo', rotulo: 'Tipo', valor: (p) => ROTULO_TIPO[p.tipo] },
  { chave: 'situacao', rotulo: 'Situação', valor: (p) => ROTULO_STATUS[p.status] },
  { chave: 'urgencia', rotulo: 'Urgência', valor: (p) => (p.urgente ? 'Urgentes' : 'Normais') },
])
const gruposLista = computed(() => ordem.grupos(prazos.value))
const processos = ref<ProcessoResumo[]>([])
const carregando = ref(true)
const erro = ref('')
const aviso = ref('')
const novo = ref(false)
const abertos = ref<Set<number>>(new Set())

const form = ref(formVazio())

function formVazio() {
  return {
    processoId: '' as number | '',
    tipo: 'CON' as TipoAgendamento,
    descricao: '',
    responsavelId: eu.value?.id ?? 0,
    urgente: false,
    inicioPrazo: hojeIso(),
    prazoFatal: hojeIso(15),
  }
}

const podeEscolherUsuario = computed(() => temPerfil('SOCIA'))
const responsaveis = computed(() => porNome(usuarios.value.filter((u) => u.ativo), (u) => u.nome))
const processosOrdenados = computed(() => porNome(processos.value, (p) => p.clienteNome + ' ' + p.numeroFormatado))

function mensagem(e: unknown): string {
  return e instanceof ApiError && e.detalhe ? e.detalhe : 'Não foi possível concluir a operação.'
}

/** A UI so esconde; quem lancou nao confirma e estagiario nunca confirma (o servidor valida de novo). */
function podeConferir(p: Prazo): boolean {
  return (
    p.status === 'AGUARDANDO_CONFERENCIA_PRAZO' &&
    !!eu.value &&
    eu.value.perfil !== 'ESTAGIARIO' &&
    p.lancadoPor !== eu.value.id
  )
}

async function recarregar() {
  try {
    prazos.value = await carregarPrazos()
  } catch (e) {
    erro.value = mensagem(e)
  } finally {
    carregando.value = false
  }
}

async function conferir(p: Prazo) {
  erro.value = ''
  aviso.value = ''
  try {
    await conferirPrazo(p.id)
    aviso.value = 'Prazo conferido.'
    await recarregar()
  } catch (e) {
    erro.value = mensagem(e)
  }
}

async function abrirNovo() {
  novo.value = true
  if (novo.value && processos.value.length === 0) {
    try {
      processos.value = await carregarProcessos()
    } catch (e) {
      erro.value = mensagem(e)
    }
  }
}

async function lancar() {
  erro.value = ''
  aviso.value = ''
  const f = form.value
  try {
    const criado = await lancarPrazo({
      processoId: f.processoId as number,
      tipo: f.tipo,
      descricao: f.descricao,
      responsavelId: f.responsavelId,
      urgente: f.urgente,
      inicioPrazo: f.inicioPrazo,
      prazoFatal: f.prazoFatal,
    })
    aviso.value = `Prazo lançado: interno em ${formatarData(criado.prazoInterno)}. Aguarda conferência de outra pessoa.`
    form.value = formVazio()
    novo.value = false
    await recarregar()
  } catch (e) {
    erro.value = mensagem(e)
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
  await recarregar()
})
</script>

<template>
  <section>
    <h1>Prazos</h1>
    <p class="suave">Quem lança o prazo não o confere; estagiários não conferem.</p>
    <div class="barra-ferramentas">
      <button type="button" class="botao-primario" @click="abrirNovo">
        Lançar prazo
      </button>
    </div>

    <Modal :aberto="novo" titulo="Lançar prazo" form-id="f-lancar-621" salvar-rotulo="Lançar" :erro="erro" @fechar="novo = false">
      <form id="f-lancar-621" class="formulario" @submit.prevent="lancar">
        <label>
          Processo
          <select v-model="form.processoId" required>
            <option value="">Selecione</option>
            <option v-for="p in az(processosOrdenados, (x) => x.clienteNome + ' ' + x.numeroFormatado)" :key="p.id" :value="p.id">{{ p.numeroFormatado }} · {{ p.clienteNome }}</option>
          </select>
        </label>
        <label>
          Tipo
          <select v-model="form.tipo">
            <option v-for="t in azPor(TIPOS_PRAZO, ROTULO_TIPO)" :key="t" :value="t">{{ t }} · {{ ROTULO_TIPO[t] }}</option>
          </select>
        </label>
        <label>
          Responsável
          <select v-model="form.responsavelId" :disabled="!podeEscolherUsuario">
            <option v-if="!podeEscolherUsuario && eu" :value="eu.id">{{ eu.nome }}</option>
            <option v-for="u in az(podeEscolherUsuario ? responsaveis : [], (x) => x.nome)" :key="u.id" :value="u.id">{{ u.nome }}</option>
          </select>
        </label>
        <label>Início do prazo (intimação) <input v-model="form.inicioPrazo" type="date" required /></label>
        <label>Prazo fatal <input v-model="form.prazoFatal" type="date" required /></label>
        <label>Descrição <input v-model="form.descricao" maxlength="300" /></label>
        <label class="marcar"><input v-model="form.urgente" type="checkbox" /> Urgente</label>
        <p class="suave">O prazo interno (3 dias úteis antes do fatal) é calculado pelo sistema.</p>
      </form>
    </Modal>

    <p v-if="aviso" class="ok" role="status">{{ aviso }}</p>
    <p v-if="erro" class="erro" role="alert">{{ erro }}</p>
    <SeletorOrdem :ordem="ordem" :total="prazos.length" />
    <p v-if="carregando">Carregando...</p>
    <p v-else-if="prazos.length === 0" class="suave">Nenhum prazo.</p>

    <template v-for="g in gruposLista" :key="g.titulo">
      <h2 v-if="g.titulo" class="grupo-titulo">{{ g.titulo }} <span class="suave">({{ g.itens.length }})</span></h2>
      <article v-for="p in g.itens" :key="p.id"  class="cartao" :class="{ 'status-AGUARDANDO': p.status === 'AGUARDANDO_CONFERENCIA_PRAZO' }">
      <header class="cartao-topo">
        <BolinhaTipo :tipo="p.tipo" />
        <span v-if="p.urgente" class="urgente"><span aria-hidden="true">⚠</span> URGENTE</span>
        <span class="cartao-quando">Fatal {{ formatarData(p.prazoFatal) }}</span>
      </header>
      <p class="cartao-descricao">{{ p.descricao || '(sem descrição)' }}</p>
      <p class="suave">
        Início {{ formatarData(p.inicioPrazo) }} · <b>Interno {{ formatarData(p.prazoInterno) }}</b> · Fatal
        {{ formatarData(p.prazoFatal) }}
      </p>
      <p class="suave">
        {{ ROTULO_STATUS[p.status] }} · Responsável: {{ nome(p.responsavelId) }} · Lançado por: {{ nome(p.lancadoPor) }}
        <template v-if="p.conferidoPor"> · Conferido por: {{ nome(p.conferidoPor) }}</template>
      </p>
      <div class="cartao-acoes">
        <button v-if="podeConferir(p)" type="button" class="botao-primario" @click="conferir(p)">Conferir contagem</button>
        <button type="button" class="botao-secundario" :aria-expanded="abertos.has(p.id)" @click="alternar(p.id)">
          {{ abertos.has(p.id) ? 'Ocultar detalhes' : 'Observações e anexos' }}
        </button>
      </div>
      <AgendamentoDetalhe v-if="abertos.has(p.id)" :id="p.id" />
      </article>
    </template>
  </section>
</template>
