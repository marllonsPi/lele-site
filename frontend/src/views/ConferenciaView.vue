<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  aprovarSupervisor,
  carregarPendentesConferencia,
  conferirSocia,
  devolverSocia,
  devolverSupervisor,
  type ItemAgenda,
} from '../api/agenda'
import { ApiError } from '../api/http'
import CartaoAgenda from '../components/CartaoAgenda.vue'
import FormMotivo from '../components/FormMotivo.vue'
import { useUsuarios } from '../composables/useUsuarios'

const { carregar: carregarUsuarios } = useUsuarios()

const itens = ref<ItemAgenda[]>([])
const carregando = ref(true)
const erro = ref('')
const aviso = ref('')
const devolvendo = ref<number | null>(null)

function mensagem(e: unknown): string {
  return e instanceof ApiError && e.detalhe ? e.detalhe : 'Não foi possível concluir a operação.'
}

async function recarregar() {
  try {
    itens.value = await carregarPendentesConferencia()
  } catch (e) {
    erro.value = mensagem(e)
  } finally {
    carregando.value = false
  }
}

async function executar(acao: () => Promise<unknown>, texto: string) {
  erro.value = ''
  aviso.value = ''
  try {
    await acao()
    devolvendo.value = null
    aviso.value = texto
    await recarregar()
  } catch (e) {
    erro.value = mensagem(e)
  }
}

function devolver(item: ItemAgenda, motivo: string) {
  const supervisor = item.status === 'EM_CONFERENCIA_SUPERVISOR'
  return executar(
    () => (supervisor ? devolverSupervisor(item.id, motivo) : devolverSocia(item.id, motivo)),
    'Item devolvido ao responsável.',
  )
}

onMounted(async () => {
  await carregarUsuarios()
  await recarregar()
})
</script>

<template>
  <section>
    <h1>Conferência</h1>
    <p class="suave">
      Estagiário → supervisor → sócia. Sócias veem o que aguarda as sócias; supervisores, o que aguarda por eles.
    </p>
    <p v-if="aviso" class="ok" role="status">{{ aviso }}</p>
    <p v-if="erro" class="erro" role="alert">{{ erro }}</p>
    <p v-if="carregando">Carregando...</p>
    <p v-else-if="itens.length === 0" class="suave">Nada aguardando conferência.</p>

    <CartaoAgenda v-for="item in itens" :key="item.id" :item="item">
      <template #acoes="{ item: i }">
        <button
          v-if="i.status === 'EM_CONFERENCIA_SUPERVISOR'"
          type="button"
          class="botao-primario"
          @click="executar(() => aprovarSupervisor(i.id), 'Aprovado; segue para as sócias.')"
        >
          Aprovar (supervisor)
        </button>
        <button
          v-else
          type="button"
          class="botao-primario"
          @click="executar(() => conferirSocia(i.id), 'Item conferido.')"
        >
          Conferido
        </button>
        <button type="button" class="botao-secundario" @click="devolvendo = devolvendo === i.id ? null : i.id">
          Devolver
        </button>
      </template>
      <template #extra="{ item: i }">
        <FormMotivo
          v-if="devolvendo === i.id"
          rotulo="Motivo da devolução"
          botao="Devolver"
          @enviar="(m) => devolver(i, m)"
          @cancelar="devolvendo = null"
        />
      </template>
    </CartaoAgenda>
  </section>
</template>
