<script setup lang="ts">
import { az, azRotulos } from '../utils/ordenacao'
import Modal from '../components/Modal.vue'
import { computed, onMounted, ref } from 'vue'
import {
  carregarAgenda,
  carregarClientes,
  carregarProcessos,
  finalizar,
  marcarCompromisso,
  ROTULO_MODALIDADE,
  type AvisoConflito,
  type ClienteResumo,
  type ItemAgenda,
  type Modalidade,
  type ProcessoResumo,
} from '../api/agenda'
import { ApiError } from '../api/http'
import CartaoAgenda from '../components/CartaoAgenda.vue'
import FormMotivo from '../components/FormMotivo.vue'
import { useSessao } from '../composables/useSessao'
import { useUsuarios } from '../composables/useUsuarios'
import { hojeIso } from '../utils/datas'

const { eu, temPerfil } = useSessao()
const { usuarios, carregar: carregarUsuarios } = useUsuarios()

const itens = ref<ItemAgenda[]>([])
const alvo = ref<number | null>(null)
const carregando = ref(true)
const erro = ref('')
const aviso = ref('')
const finalizando = ref<number | null>(null)

const processos = ref<ProcessoResumo[]>([])
const clientes = ref<ClienteResumo[]>([])
const novo = ref(false)
const form = ref(formVazio())
const avisosConflito = ref<AvisoConflito[]>([])

function formVazio() {
  return {
    tipo: 'AUD' as 'AUD' | 'ATD',
    processoId: '' as number | '',
    clienteId: '' as number | '',
    descricao: '',
    responsavelId: eu.value?.id ?? 0,
    urgente: false,
    data: hojeIso(1),
    hora: '10:00',
    duracaoMin: 60,
    modalidade: 'PRESENCIAL' as Modalidade,
    localOuLink: '',
  }
}

const responsaveis = computed(() => usuarios.value.filter((u) => u.ativo))
const podeEscolherUsuario = computed(() => temPerfil('SOCIA'))

function mensagem(e: unknown): string {
  return e instanceof ApiError && e.detalhe ? e.detalhe : 'Não foi possível concluir a operação.'
}

async function recarregar() {
  erro.value = ''
  try {
    itens.value = await carregarAgenda(alvo.value ?? undefined)
  } catch (e) {
    erro.value = mensagem(e)
  } finally {
    carregando.value = false
  }
}

async function concluir(item: ItemAgenda, resumo?: string) {
  erro.value = ''
  aviso.value = ''
  try {
    await finalizar(item.id, resumo)
    finalizando.value = null
    aviso.value = 'Item finalizado e enviado para conferência.'
    await recarregar()
  } catch (e) {
    erro.value = mensagem(e)
  }
}

function aoFinalizar(item: ItemAgenda) {
  if (item.tipo === 'ATD') {
    finalizando.value = finalizando.value === item.id ? null : item.id
  } else {
    void concluir(item)
  }
}

async function abrirNovo() {
  novo.value = true
  if (novo.value && processos.value.length === 0) {
    try {
      ;[processos.value, clientes.value] = await Promise.all([carregarProcessos(), carregarClientes()])
    } catch (e) {
      erro.value = mensagem(e)
    }
  }
}

async function marcar() {
  erro.value = ''
  aviso.value = ''
  avisosConflito.value = []
  const f = form.value
  try {
    const r = await marcarCompromisso({
      tipo: f.tipo,
      processoId: f.processoId === '' ? null : f.processoId,
      clienteId: f.tipo === 'ATD' && f.clienteId !== '' ? f.clienteId : null,
      descricao: f.descricao,
      responsavelId: f.responsavelId,
      urgente: f.urgente,
      inicio: `${f.data}T${f.hora}:00`,
      duracaoMin: f.duracaoMin || null,
      modalidade: f.modalidade,
      localOuLink: f.localOuLink,
    })
    avisosConflito.value = r.avisos ?? []
    aviso.value = 'Compromisso marcado.'
    form.value = formVazio()
    novo.value = false
    await recarregar()
  } catch (e) {
    erro.value = mensagem(e)
  }
}

onMounted(async () => {
  await carregarUsuarios()
  await recarregar()
})
</script>

<template>
  <section>
    <h1>Agenda</h1>
    <div class="barra-ferramentas">
      <label v-if="podeEscolherUsuario">
        Ver agenda de
        <select v-model="alvo" @change="recarregar">
          <option :value="null">Minha agenda</option>
          <option v-for="u in az(responsaveis, (x) => x.nome)" :key="u.id" :value="u.id">{{ u.nome }}</option>
        </select>
      </label>
      <button type="button" class="botao-primario" @click="abrirNovo">
        Marcar audiência ou atendimento
      </button>
    </div>

    <Modal :aberto="novo" titulo="Marcar audiência ou atendimento" form-id="f-marcar-621" salvar-rotulo="Marcar" :erro="erro" @fechar="novo = false">
      <form id="f-marcar-621" class="formulario" @submit.prevent="marcar">
        <label>
          Tipo
          <select v-model="form.tipo">
            <option value="AUD">Audiência</option>
            <option value="ATD">Atendimento de cliente</option>
          </select>
        </label>
        <label>
          Processo {{ form.tipo === 'ATD' ? '(opcional)' : '' }}
          <select v-model="form.processoId" :required="form.tipo === 'AUD'">
            <option value="">Selecione</option>
            <option v-for="p in az(processos, (x) => x.clienteNome + ' ' + x.numeroFormatado)" :key="p.id" :value="p.id">{{ p.numeroFormatado }} · {{ p.clienteNome }}</option>
          </select>
        </label>
        <label v-if="form.tipo === 'ATD'">
          Cliente {{ form.processoId === '' ? '' : '(opcional)' }}
          <select v-model="form.clienteId" :required="form.processoId === ''">
            <option value="">Selecione</option>
            <option v-for="c in az(clientes, (x) => x.nome)" :key="c.id" :value="c.id">{{ c.nome }}</option>
          </select>
        </label>
        <label>
          {{ form.tipo === 'ATD' ? 'Advogado que atenderá' : 'Responsável' }}
          <select v-model="form.responsavelId" :disabled="!podeEscolherUsuario">
            <option v-if="!podeEscolherUsuario && eu" :value="eu.id">{{ eu.nome }}</option>
            <option v-for="u in az(podeEscolherUsuario ? responsaveis : [], (x) => x.nome)" :key="u.id" :value="u.id">{{ u.nome }}</option>
          </select>
        </label>
        <label>Data <input v-model="form.data" type="date" required /></label>
        <label>Hora <input v-model="form.hora" type="time" required /></label>
        <label>Duração (min) <input v-model.number="form.duracaoMin" type="number" min="5" step="5" required /></label>
        <label>
          Modalidade
          <select v-model="form.modalidade">
            <option v-for="[valor, rotulo] in azRotulos(ROTULO_MODALIDADE)" :key="valor" :value="valor">{{ rotulo }}</option>
          </select>
        </label>
        <label>Local ou link <input v-model="form.localOuLink" maxlength="300" /></label>
        <label>Descrição <input v-model="form.descricao" maxlength="300" /></label>
        <label class="marcar"><input v-model="form.urgente" type="checkbox" /> Urgente</label>
      </form>
    </Modal>

    <p v-if="aviso" class="ok" role="status">{{ aviso }}</p>
    <ul v-if="avisosConflito.length" class="alerta" role="status">
      <li v-for="(a, i) in avisosConflito" :key="i">
        <span aria-hidden="true">⚠</span>
        {{
          a.tipo === 'MESMO_ADVOGADO'
            ? 'Conflito: o mesmo advogado já tem ' + a.comTipo + ' nesse horário.'
            : 'Conflito: há outra audiência do escritório nesse horário.'
        }}
      </li>
    </ul>
    <p v-if="erro" class="erro" role="alert">{{ erro }}</p>
    <p v-if="carregando">Carregando...</p>
    <p v-else-if="itens.length === 0" class="suave">Nada na agenda.</p>

    <CartaoAgenda v-for="item in itens" :key="item.id" :item="item">
      <template #acoes="{ item: i }">
        <button
          v-if="i.status === 'PENDENTE' || i.status === 'DEVOLVIDO'"
          type="button"
          class="botao-primario"
          @click="aoFinalizar(i)"
        >
          Finalizar
        </button>
      </template>
      <template #extra="{ item: i }">
        <FormMotivo
          v-if="finalizando === i.id"
          rotulo="Resumo do atendimento"
          botao="Finalizar atendimento"
          :obrigatorio="false"
          @enviar="(t) => concluir(i, t)"
          @cancelar="finalizando = null"
        />
      </template>
    </CartaoAgenda>
  </section>
</template>
