<script setup lang="ts">
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
import { useSessao } from '../composables/useSessao'
import { useUsuarios } from '../composables/useUsuarios'
import { formatarData, hojeIso } from '../utils/datas'

const { eu, temPerfil } = useSessao()
const { usuarios, carregar: carregarUsuarios, nome } = useUsuarios()

const prazos = ref<Prazo[]>([])
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
const responsaveis = computed(() => usuarios.value.filter((u) => u.ativo))

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
  novo.value = !novo.value
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
      <button type="button" class="botao-primario" :aria-expanded="novo" @click="abrirNovo">
        {{ novo ? 'Fechar' : 'Lançar prazo' }}
      </button>
    </div>

    <form v-if="novo" class="formulario" @submit.prevent="lancar">
      <label>
        Processo
        <select v-model="form.processoId" required>
          <option value="">Selecione</option>
          <option v-for="p in processos" :key="p.id" :value="p.id">{{ p.numeroFormatado }} · {{ p.clienteNome }}</option>
        </select>
      </label>
      <label>
        Tipo
        <select v-model="form.tipo">
          <option v-for="t in TIPOS_PRAZO" :key="t" :value="t">{{ t }} · {{ ROTULO_TIPO[t] }}</option>
        </select>
      </label>
      <label>
        Responsável
        <select v-model="form.responsavelId" :disabled="!podeEscolherUsuario">
          <option v-if="!podeEscolherUsuario && eu" :value="eu.id">{{ eu.nome }}</option>
          <option v-for="u in podeEscolherUsuario ? responsaveis : []" :key="u.id" :value="u.id">{{ u.nome }}</option>
        </select>
      </label>
      <label>Início do prazo (intimação) <input v-model="form.inicioPrazo" type="date" required /></label>
      <label>Prazo fatal <input v-model="form.prazoFatal" type="date" required /></label>
      <label>Descrição <input v-model="form.descricao" maxlength="300" /></label>
      <label class="marcar"><input v-model="form.urgente" type="checkbox" /> Urgente</label>
      <p class="suave">O prazo interno (3 dias úteis antes do fatal) é calculado pelo sistema.</p>
      <button type="submit" class="botao-primario">Lançar</button>
    </form>

    <p v-if="aviso" class="ok" role="status">{{ aviso }}</p>
    <p v-if="erro" class="erro" role="alert">{{ erro }}</p>
    <p v-if="carregando">Carregando...</p>
    <p v-else-if="prazos.length === 0" class="suave">Nenhum prazo.</p>

    <article v-for="p in prazos" :key="p.id" class="cartao" :class="{ 'status-AGUARDANDO': p.status === 'AGUARDANDO_CONFERENCIA_PRAZO' }">
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
  </section>
</template>
