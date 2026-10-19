<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ROTULO_TIPO, TIPOS_PRAZO, type TipoAgendamento } from '../api/agenda'
import { listarProcessos, type Processo } from '../api/cadastros'
import {
  buscarPublicacao,
  classificarPublicacao,
  descartarPublicacao,
  listarPublicacoes,
  ROTULO_ESTADO,
  ROTULO_FONTE,
  vincularProcesso,
  type EstadoPublicacao,
  type Publicacao,
} from '../api/publicacoes'
import Feedback from '../components/Feedback.vue'
import FormMotivo from '../components/FormMotivo.vue'
import { useFeedback } from '../composables/useFeedback'
import { useSessao } from '../composables/useSessao'
import { useUsuarios } from '../composables/useUsuarios'
import { formatarData, hojeIso } from '../utils/datas'

const { eu, temPerfil } = useSessao()
const { usuarios, carregar: carregarUsuarios, nome } = useUsuarios()
const { erro, aviso, executar } = useFeedback()

const publicacoes = ref<Publicacao[]>([])
const processos = ref<Processo[]>([])
const carregando = ref(true)
const estado = ref<EstadoPublicacao | ''>('')
const classificando = ref<number | null>(null)
const descartando = ref<number | null>(null)
const vinculando = ref<number | null>(null)
const processoEscolhido = ref<number | ''>('')
const conteudos = ref<Record<number, string>>({})

const ehSocia = computed(() => temPerfil('SOCIA'))
const responsaveis = computed(() => usuarios.value.filter((u) => u.ativo))

const form = ref(formVazio())

function formVazio() {
  return {
    tipo: 'CON' as TipoAgendamento,
    processoId: '' as number | '',
    responsavelId: eu.value?.id ?? 0,
    urgente: false,
    descricao: '',
    inicioPrazo: hojeIso(),
    prazoFatal: hojeIso(15),
  }
}

async function recarregar() {
  await executar(async () => {
    publicacoes.value = await listarPublicacoes(estado.value || undefined)
  }, '', true)
  carregando.value = false
}

async function carregarProcessos() {
  if (processos.value.length === 0) {
    await executar(async () => {
      processos.value = (await listarProcessos()).filter((p) => p.ativo)
    }, '', true)
  }
}

async function abrirClassificar(p: Publicacao) {
  await carregarProcessos()
  form.value = { ...formVazio(), processoId: p.processoId ?? '', descricao: p.resumo?.slice(0, 300) ?? '' }
  classificando.value = classificando.value === p.id ? null : p.id
}

async function classificar(p: Publicacao) {
  const f = form.value
  const ok = await executar(
    () =>
      classificarPublicacao(p.id, {
        tipo: f.tipo,
        processoId: f.processoId === '' ? null : f.processoId,
        responsavelId: f.responsavelId,
        urgente: f.urgente,
        descricao: f.descricao,
        inicioPrazo: f.inicioPrazo,
        prazoFatal: f.prazoFatal,
      }),
    'Publicação classificada: o prazo aguarda a conferência de outra pessoa.',
  )
  if (ok) {
    classificando.value = null
    await recarregar()
  }
}

async function descartar(p: Publicacao, motivo: string) {
  if (await executar(() => descartarPublicacao(p.id, motivo), 'Publicação descartada com registro.')) {
    descartando.value = null
    await recarregar()
  }
}

async function abrirVinculo(p: Publicacao) {
  await carregarProcessos()
  processoEscolhido.value = ''
  vinculando.value = vinculando.value === p.id ? null : p.id
}

async function vincular(p: Publicacao) {
  if (await executar(() => vincularProcesso(p.id, processoEscolhido.value as number), 'Processo vinculado.')) {
    vinculando.value = null
    await recarregar()
  }
}

async function verConteudo(p: Publicacao) {
  if (conteudos.value[p.id] !== undefined) {
    const { [p.id]: _, ...resto } = conteudos.value
    conteudos.value = resto
    return
  }
  await executar(async () => {
    const completa = await buscarPublicacao(p.id)
    conteudos.value = { ...conteudos.value, [p.id]: completa.conteudo ?? '(sem conteúdo)' }
  }, '', true)
}

onMounted(async () => {
  await carregarUsuarios()
  await recarregar()
})
</script>

<template>
  <section>
    <h1>Publicações</h1>
    <p class="suave">
      A fila "A classificar" é tratada pelas sócias. Itens ignorados por filtro ficam no registro da captura, nunca
      somem.
    </p>
    <div class="barra-ferramentas">
      <label>
        Estado
        <select v-model="estado" @change="recarregar">
          <option value="">Todos</option>
          <option v-for="(r, v) in ROTULO_ESTADO" :key="v" :value="v">{{ r }}</option>
        </select>
      </label>
    </div>

    <Feedback :erro="erro" :aviso="aviso" />
    <p v-if="carregando">Carregando...</p>
    <p v-else-if="publicacoes.length === 0" class="suave">Nenhuma publicação.</p>

    <article v-for="p in publicacoes" :key="p.id" class="cartao" :class="{ destaque: p.estado === 'A_CLASSIFICAR' }">
      <header class="cartao-topo">
        <b>{{ p.numeroFormatado || p.numeroProcesso }}</b>
        <span v-for="f in p.fontes" :key="f" class="selo">{{ ROTULO_FONTE[f] }}</span>
        <span class="cartao-quando">{{ formatarData(p.dataPublicacao) }}</span>
      </header>
      <p class="suave">
        {{ p.tribunal }} · em nome de {{ nome(p.titularId) }} · {{ ROTULO_ESTADO[p.estado] }}
      </p>
      <p v-if="p.somenteEmUmaFonte" class="alerta"><span aria-hidden="true">⚠</span> Apareceu em uma única fonte</p>
      <p v-if="p.sugerirCadastro" class="alerta">Processo ainda não cadastrado: sugerido cadastrar.</p>
      <p v-else-if="p.processoId" class="suave">Vinculada ao processo #{{ p.processoId }}</p>
      <p v-if="p.motivoDescarte" class="suave">Motivo do descarte: {{ p.motivoDescarte }}</p>
      <p v-if="p.agendamentoId" class="suave">Gerou o prazo #{{ p.agendamentoId }} (veja em Prazos).</p>
      <p class="texto-livre">{{ p.resumo }}</p>
      <pre v-if="conteudos[p.id] !== undefined" class="conteudo-bruto">{{ conteudos[p.id] }}</pre>

      <div class="cartao-acoes">
        <button type="button" class="botao-secundario" :aria-expanded="conteudos[p.id] !== undefined" @click="verConteudo(p)">
          {{ conteudos[p.id] !== undefined ? 'Ocultar texto' : 'Ver texto completo' }}
        </button>
        <template v-if="ehSocia && p.estado === 'A_CLASSIFICAR'">
          <button type="button" class="botao-primario" @click="abrirClassificar(p)">Classificar</button>
          <button type="button" class="botao-secundario" @click="descartando = descartando === p.id ? null : p.id">Descartar</button>
        </template>
        <button
          v-if="p.estado !== 'DESCARTADA_COM_REGISTRO' && !p.processoId"
          type="button"
          class="botao-secundario"
          @click="abrirVinculo(p)"
        >
          Vincular processo
        </button>
      </div>

      <FormMotivo
        v-if="descartando === p.id"
        rotulo="Motivo do descarte"
        botao="Descartar"
        @enviar="(m) => descartar(p, m)"
        @cancelar="descartando = null"
      />

      <form v-if="vinculando === p.id" class="grupo-botoes" @submit.prevent="vincular(p)">
        <label>
          Processo
          <select v-model="processoEscolhido" required>
            <option value="">Selecione</option>
            <option v-for="x in processos" :key="x.id" :value="x.id">{{ x.numeroFormatado }} · {{ x.clienteNome }}</option>
          </select>
        </label>
        <button type="submit" class="botao-secundario">Vincular</button>
      </form>

      <form v-if="classificando === p.id" class="formulario" @submit.prevent="classificar(p)">
        <label>
          Tipo de prazo
          <select v-model="form.tipo">
            <option v-for="t in TIPOS_PRAZO" :key="t" :value="t">{{ t }} · {{ ROTULO_TIPO[t] }}</option>
          </select>
        </label>
        <label>
          Processo
          <select v-model="form.processoId" :required="!p.processoId">
            <option value="">{{ p.processoId ? 'Já vinculado' : 'Selecione' }}</option>
            <option v-for="x in processos" :key="x.id" :value="x.id">{{ x.numeroFormatado }} · {{ x.clienteNome }}</option>
          </select>
        </label>
        <label>
          Responsável
          <select v-model="form.responsavelId" required>
            <option v-for="u in responsaveis" :key="u.id" :value="u.id">{{ u.nome }}</option>
          </select>
        </label>
        <label>Início do prazo <input v-model="form.inicioPrazo" type="date" required /></label>
        <label>Prazo fatal <input v-model="form.prazoFatal" type="date" required /></label>
        <label>Descrição <input v-model="form.descricao" maxlength="300" /></label>
        <label class="marcar"><input v-model="form.urgente" type="checkbox" /> Urgente</label>
        <button type="submit" class="botao-primario">Classificar e criar prazo</button>
      </form>
    </article>
  </section>
</template>
