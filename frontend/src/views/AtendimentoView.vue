<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  listarAndamentosPendentes,
  listarAtendimentos,
  marcarRepassado,
  registrarAndamento,
  registrarAtendimento,
  ROTULO_CANAL,
  type Andamento,
  type Atendimento,
  type Canal,
} from '../api/atendimento'
import { listarClientes, listarProcessos, type Cliente, type Processo } from '../api/cadastros'
import Feedback from '../components/Feedback.vue'
import { useFeedback } from '../composables/useFeedback'
import { useSessao } from '../composables/useSessao'
import { useUsuarios } from '../composables/useUsuarios'
import { formatarData, formatarDataHora, hojeIso } from '../utils/datas'

const { eu } = useSessao()
const { carregar: carregarUsuarios, nome } = useUsuarios()
const { erro, aviso, executar } = useFeedback()

const atendimentos = ref<Atendimento[]>([])
const andamentos = ref<Andamento[]>([])
const clientes = ref<Cliente[]>([])
const processos = ref<Processo[]>([])
const carregando = ref(true)
const filtroCliente = ref<number | null>(null)
const novoAtd = ref(false)
const novoAnd = ref(false)
const formAtd = ref(vazioAtd())
const formAnd = ref({ processoId: '' as number | '', data: hojeIso(), descricao: '' })
const via = ref<Record<number, string>>({})

function vazioAtd() {
  return { clienteId: '' as number | '', processoId: '' as number | '', canal: 'TELEFONE' as Canal, resumo: '' }
}

const nomeCliente = (id: number) => clientes.value.find((c) => c.id === id)?.nome ?? `Cliente #${id}`
const numeroProcesso = (id: number) => processos.value.find((p) => p.id === id)?.numeroFormatado ?? `Processo #${id}`

async function recarregar() {
  await executar(async () => {
    ;[atendimentos.value, andamentos.value] = await Promise.all([
      listarAtendimentos(filtroCliente.value ?? undefined),
      listarAndamentosPendentes(),
    ])
    for (const a of andamentos.value) {
      via.value[a.id] ||= 'EMAIL'
    }
  }, '', true)
  carregando.value = false
}

async function registrarAtd() {
  const f = formAtd.value
  const ok = await executar(
    () =>
      registrarAtendimento({
        clienteId: f.clienteId as number,
        processoId: f.processoId === '' ? null : f.processoId,
        canal: f.canal,
        momento: null,
        resumo: f.resumo,
        atendenteId: eu.value?.id ?? null,
      }),
    'Atendimento registrado.',
  )
  if (ok) {
    formAtd.value = vazioAtd()
    novoAtd.value = false
    await recarregar()
  }
}

async function registrarAnd() {
  const f = formAnd.value
  const ok = await executar(
    () => registrarAndamento({ processoId: f.processoId as number, data: f.data || null, descricao: f.descricao }),
    'Andamento registrado; fica pendente de repasse ao cliente.',
  )
  if (ok) {
    formAnd.value = { processoId: '', data: hojeIso(), descricao: '' }
    novoAnd.value = false
    await recarregar()
  }
}

async function repassar(a: Andamento) {
  if (await executar(() => marcarRepassado(a.id, via.value[a.id] || 'EMAIL'), 'Andamento marcado como repassado.')) {
    await recarregar()
  }
}

onMounted(async () => {
  await carregarUsuarios()
  await executar(async () => {
    ;[clientes.value, processos.value] = await Promise.all([listarClientes(), listarProcessos()])
  }, '', true)
  await recarregar()
})
</script>

<template>
  <section>
    <h1>Atendimento</h1>
    <Feedback :erro="erro" :aviso="aviso" />

    <h2>Andamentos a repassar ao cliente</h2>
    <div class="barra-ferramentas">
      <button type="button" class="botao-secundario" :aria-expanded="novoAnd" @click="novoAnd = !novoAnd">Registrar andamento</button>
    </div>
    <form v-if="novoAnd" class="formulario" @submit.prevent="registrarAnd">
      <label>
        Processo
        <select v-model="formAnd.processoId" required>
          <option value="">Selecione</option>
          <option v-for="p in processos" :key="p.id" :value="p.id">{{ p.numeroFormatado }} · {{ p.clienteNome }}</option>
        </select>
      </label>
      <label>Data <input v-model="formAnd.data" type="date" /></label>
      <label>Descrição <textarea v-model="formAnd.descricao" rows="2" required></textarea></label>
      <button type="submit" class="botao-primario">Registrar</button>
    </form>
    <p v-if="!carregando && andamentos.length === 0" class="suave">Nenhum andamento pendente de repasse.</p>
    <article v-for="a in andamentos" :key="a.id" class="cartao">
      <header class="cartao-topo">
        <b>{{ numeroProcesso(a.processoId) }}</b>
        <span class="cartao-quando">{{ formatarData(a.data) }}</span>
      </header>
      <p class="texto-livre">{{ a.descricao }}</p>
      <p class="suave">Origem: {{ a.origem === 'PUBLICACAO' ? 'publicação classificada' : 'registro manual' }}</p>
      <div class="cartao-acoes">
        <label>
          Repassado por
          <select v-model="via[a.id]">
            <option value="EMAIL">E-mail</option>
            <option value="WHATSAPP">WhatsApp</option>
            <option value="TELEFONE">Telefone</option>
            <option value="PRESENCIAL">Presencial</option>
          </select>
        </label>
        <button type="button" class="botao-primario" @click="repassar(a)">Marcar repassado</button>
      </div>
    </article>

    <h2>Atendimentos</h2>
    <div class="barra-ferramentas">
      <label>
        Cliente
        <select v-model="filtroCliente" @change="recarregar">
          <option :value="null">Todos</option>
          <option v-for="c in clientes" :key="c.id" :value="c.id">{{ c.nome }}</option>
        </select>
      </label>
      <button type="button" class="botao-primario" :aria-expanded="novoAtd" @click="novoAtd = !novoAtd">Registrar atendimento</button>
    </div>
    <form v-if="novoAtd" class="formulario" @submit.prevent="registrarAtd">
      <label>
        Cliente
        <select v-model="formAtd.clienteId" required>
          <option value="">Selecione</option>
          <option v-for="c in clientes" :key="c.id" :value="c.id">{{ c.nome }}</option>
        </select>
      </label>
      <label>
        Processo (opcional)
        <select v-model="formAtd.processoId">
          <option value="">Nenhum</option>
          <option v-for="p in processos.filter((x) => x.clienteId === formAtd.clienteId)" :key="p.id" :value="p.id">
            {{ p.numeroFormatado }}
          </option>
        </select>
      </label>
      <label>
        Canal
        <select v-model="formAtd.canal">
          <option v-for="(r, v) in ROTULO_CANAL" :key="v" :value="v">{{ r }}</option>
        </select>
      </label>
      <label>Resumo <textarea v-model="formAtd.resumo" rows="2" required></textarea></label>
      <button type="submit" class="botao-primario">Registrar</button>
    </form>
    <p v-if="carregando">Carregando...</p>
    <p v-else-if="atendimentos.length === 0" class="suave">Nenhum atendimento.</p>
    <article v-for="x in atendimentos" :key="x.id" class="cartao">
      <header class="cartao-topo">
        <b>{{ nomeCliente(x.clienteId) }}</b>
        <span class="selo">{{ ROTULO_CANAL[x.canal as Canal] ?? x.canal }}</span>
        <span class="cartao-quando">{{ formatarDataHora(x.momento) }}</span>
      </header>
      <p class="texto-livre">{{ x.resumo }}</p>
      <p class="suave">
        Atendente: {{ nome(x.atendenteId) }}<template v-if="x.processoId"> · {{ numeroProcesso(x.processoId) }}</template>
        · {{ x.origem === 'AGENDAMENTO' ? 'gerado ao concluir o agendamento' : 'registro avulso' }}
      </p>
    </article>
  </section>
</template>
