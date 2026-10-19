<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { carregarAgenda, carregarPendentesConferencia, type ItemAgenda } from '../api/agenda'
import { carregarPainel, type Painel } from '../api/publicacoes'
import CartaoAgenda from '../components/CartaoAgenda.vue'
import { useSessao } from '../composables/useSessao'
import { useUsuarios } from '../composables/useUsuarios'
import { formatarData, hojeIso } from '../utils/datas'

const { eu } = useSessao()
const { carregar: carregarNomes } = useUsuarios()

const agenda = ref<ItemAgenda[]>([])
const pendentes = ref<ItemAgenda[]>([])
const painel = ref<Painel | null>(null)
const carregando = ref(true)

const hoje = hojeIso()
const saudacao = computed(() => {
  const h = new Date().getHours()
  return h < 12 ? 'Bom dia' : h < 18 ? 'Boa tarde' : 'Boa noite'
})
const primeiroNome = computed(() => (eu.value?.nome ?? '').split(' ')[0])
const dataExtenso = new Date().toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' })

const abertos = computed(() => agenda.value.filter((i) => i.status !== 'CONFERIDO' && i.status !== 'FINALIZADO'))
const vencidos = computed(() => abertos.value.filter((i) => i.statusVisual === 'VERMELHO'))
const vencemHoje = computed(() => abertos.value.filter((i) => i.statusVisual === 'AMARELO'))
const vencemAmanha = computed(() => abertos.value.filter((i) => i.statusVisual === 'VERDE'))
const compromissos = computed(() =>
  agenda.value
    .filter((i) => (i.tipo === 'AUD' || i.tipo === 'ATD') && i.data >= hoje && i.data <= hojeIso(14))
    .sort((a, b) => (a.data + (a.hora ?? '')).localeCompare(b.data + (b.hora ?? ''))),
)
const urgentes = computed(() => abertos.value.filter((i) => i.urgente))
/** O que merece atencao primeiro: atrasos, urgentes e o que vence hoje. */
const foco = computed(() => {
  const vistos = new Set<number>()
  return [...vencidos.value, ...urgentes.value, ...vencemHoje.value].filter((i) => !vistos.has(i.id) && vistos.add(i.id)).slice(0, 5)
})

const resumo = computed(() => {
  if (vencidos.value.length > 0) {
    return `Atenção: ${vencidos.value.length} item(ns) em atraso.`
  }
  if (vencemHoje.value.length > 0) {
    return `${vencemHoje.value.length} item(ns) vencem hoje.`
  }
  return 'Nada em atraso. Bom trabalho!'
})

onMounted(async () => {
  await carregarNomes()
  const [ag, pe, pa] = await Promise.allSettled([
    carregarAgenda(),
    carregarPendentesConferencia(),
    eu.value?.perfil === 'SOCIA' ? carregarPainel() : Promise.reject(new Error('sem painel')),
  ])
  agenda.value = ag.status === 'fulfilled' && Array.isArray(ag.value) ? ag.value : []
  pendentes.value = pe.status === 'fulfilled' && Array.isArray(pe.value) ? pe.value : []
  painel.value = pa.status === 'fulfilled' && Array.isArray(pa.value?.atrasos) ? pa.value : null
  carregando.value = false
})
</script>

<template>
  <section>
    <div class="cabecalho-pagina">
      <div>
        <h1>{{ saudacao }}, {{ primeiroNome }}</h1>
        <p class="suave">{{ dataExtenso }} · {{ carregando ? 'Carregando seu dia...' : resumo }}</p>
      </div>
      <router-link v-if="eu?.mestre" to="/sistema" class="botao-secundario como-botao">Configurar o sistema</router-link>
    </div>

    <div class="grade-resumo">
      <router-link to="/agenda" class="indicador" :class="{ 'alerta-forte': vencidos.length > 0 }">
        <span class="indicador-numero">{{ vencidos.length }}</span>
        <span class="indicador-rotulo">Em atraso</span>
      </router-link>
      <router-link to="/agenda" class="indicador" :class="{ 'alerta-medio': vencemHoje.length > 0 }">
        <span class="indicador-numero">{{ vencemHoje.length }}</span>
        <span class="indicador-rotulo">Vencem hoje</span>
      </router-link>
      <router-link to="/agenda" class="indicador">
        <span class="indicador-numero">{{ vencemAmanha.length }}</span>
        <span class="indicador-rotulo">Vencem amanhã</span>
      </router-link>
      <router-link to="/conferencia" class="indicador">
        <span class="indicador-numero">{{ pendentes.length }}</span>
        <span class="indicador-rotulo">Para conferir</span>
      </router-link>
      <router-link v-if="painel" to="/publicacoes" class="indicador" :class="{ 'alerta-medio': painel.aClassificar > 0 }">
        <span class="indicador-numero">{{ painel.aClassificar }}</span>
        <span class="indicador-rotulo">Publicações a classificar</span>
      </router-link>
    </div>

    <div class="duas-colunas">
      <div>
        <h2>Precisa de atenção agora</h2>
        <p v-if="!carregando && foco.length === 0" class="vazio">Nenhum atraso, urgência ou vencimento para hoje.</p>
        <CartaoAgenda v-for="i in foco" :key="i.id" :item="i" />
      </div>

      <aside class="painel">
        <h2>Próximos compromissos</h2>
        <p v-if="!carregando && compromissos.length === 0" class="suave">Sem audiências ou atendimentos nos próximos 14 dias.</p>
        <ul class="lista-simples">
          <li v-for="c in compromissos" :key="c.id">
            <b>{{ formatarData(c.data) }}<template v-if="c.hora"> {{ c.hora.substring(0, 5) }}</template></b>
            · {{ c.tipo === 'AUD' ? 'Audiência' : 'Atendimento' }}
            <span class="suave"> — {{ c.descricao || 'sem descrição' }}</span>
          </li>
        </ul>
        <p><router-link to="/agenda">Ver agenda completa</router-link></p>
      </aside>
    </div>
  </section>
</template>
