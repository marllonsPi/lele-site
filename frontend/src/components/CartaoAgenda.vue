<script setup lang="ts">
import { computed, ref } from 'vue'
import { ROTULO_MODALIDADE, ROTULO_STATUS, type ItemAgenda } from '../api/agenda'
import { useSessao } from '../composables/useSessao'
import { useUsuarios } from '../composables/useUsuarios'
import { formatarData, formatarHora } from '../utils/datas'
import AgendamentoDetalhe from './AgendamentoDetalhe.vue'
import QuadroSugestao from './QuadroSugestao.vue'
import BolinhaTipo from './BolinhaTipo.vue'

const props = defineProps<{ item: ItemAgenda }>()
const { nome } = useUsuarios()
const { eu, temPerfil } = useSessao()
/** Quadro de honorarios: socias, ou o advogado que conduz o atendimento. O servidor confere de novo. */
const podeHonorarios = computed(() => temPerfil('SOCIA') || (temPerfil('ADVOGADO') && eu.value?.id === props.item.responsavelId))
const aberto = ref(false)

function quando(i: ItemAgenda): string {
  const partes = [formatarData(i.data)]
  if (i.hora) {
    partes.push(formatarHora(i.hora))
  }
  if (i.duracaoMin) {
    partes.push(`(${i.duracaoMin} min)`)
  }
  return partes.join(' ')
}
</script>

<template>
  <article class="cartao" :class="'status-' + item.statusVisual">
    <header class="cartao-topo">
      <BolinhaTipo :tipo="item.tipo" />
      <span v-if="item.urgente" class="urgente"><span aria-hidden="true">⚠</span> URGENTE</span>
      <span class="cartao-quando">{{ quando(item) }}</span>
    </header>
    <p class="cartao-descricao">{{ item.descricao || '(sem descrição)' }}</p>
    <p class="suave">
      Responsável: {{ nome(item.responsavelId) }} · Criado por: {{ nome(item.criadoPor) }} ·
      {{ ROTULO_STATUS[item.status] }}
    </p>
    <p v-if="item.prazoFatal" class="suave">
      Interno {{ formatarData(item.prazoInterno) }} · Fatal {{ formatarData(item.prazoFatal) }}
    </p>
    <p v-if="item.modalidade" class="suave">
      {{ ROTULO_MODALIDADE[item.modalidade] }}<template v-if="item.localOuLink"> · {{ item.localOuLink }}</template>
    </p>
    <p v-if="item.alertaAudiencia" class="alerta"><span aria-hidden="true">🔔</span> Audiência em até 10 dias</p>
    <p v-if="item.aguardandoConferenciaPrazo" class="alerta">
      <span aria-hidden="true">⏳</span> Aguardando conferência da contagem do prazo
    </p>
    <div class="cartao-acoes">
      <slot name="acoes" :item="item" />
      <button type="button" class="botao-secundario" :aria-expanded="aberto" @click="aberto = !aberto">
        {{ aberto ? 'Ocultar detalhes' : 'Observações e anexos' }}
      </button>
    </div>
    <slot name="extra" :item="item" />
    <AgendamentoDetalhe v-if="aberto" :id="item.id" />
    <QuadroSugestao v-if="aberto && item.tipo === 'ATD' && podeHonorarios" :agendamento-id="item.id" :pode-assinar="temPerfil('SOCIA')" />
  </article>
</template>
