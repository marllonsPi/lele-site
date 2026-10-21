<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ROTULO_AREA, type AreaAtuacao } from '../api/cadastros'
import { carregarPainelFinanceiro, type ParcelaLinha, type PainelFinanceiroDados } from '../api/importacao'
import Feedback from './Feedback.vue'
import { useFeedback } from '../composables/useFeedback'
import { formatarData } from '../utils/datas'
import { formatarMoeda } from '../utils/moeda'

const props = defineProps<{ area: AreaAtuacao | '' }>()
const { erro, executar } = useFeedback()
const dados = ref<PainelFinanceiroDados | null>(null)

async function recarregar() {
  await executar(async () => {
    dados.value = await carregarPainelFinanceiro(props.area || null)
  })
}

function rotuloMes(m: string): string {
  const [a, mes] = m.split('-')
  return `${mes}/${a}`
}

function rotuloArea(a: string | null): string {
  return a ? (ROTULO_AREA[a as AreaAtuacao] ?? a) : 'Sem área'
}

const listas: { chave: 'aVencer7Dias' | 'aVencer30Dias'; titulo: string }[] = [
  { chave: 'aVencer7Dias', titulo: 'A vencer nos próximos 7 dias' },
  { chave: 'aVencer30Dias', titulo: 'A vencer nos próximos 30 dias' },
]

function ordenadas(l: ParcelaLinha[]): ParcelaLinha[] {
  return l
}

watch(() => props.area, recarregar)
onMounted(recarregar)
</script>

<template>
  <section aria-labelledby="titulo-financeiro">
    <h2 id="titulo-financeiro">Financeiro</h2>
    <Feedback :erro="erro" />
    <template v-if="dados">
      <p v-if="dados.quantidadeVencidas > 0" class="alerta-incompleto" role="alert">
        {{ dados.quantidadeVencidas }} parcela(s) vencida(s), somando {{ formatarMoeda(dados.totalVencido) }} em aberto.
      </p>
      <p v-else class="suave">Nenhuma parcela vencida.</p>

      <h3>Recebido e em aberto por mês</h3>
      <p v-if="dados.meses.length === 0" class="suave">Sem lançamentos.</p>
      <div v-else class="tabela-rolavel">
        <table>
          <thead>
            <tr><th scope="col">Mês</th><th scope="col">Recebido</th><th scope="col">Em aberto</th></tr>
          </thead>
          <tbody>
            <tr v-for="m in dados.meses" :key="m.mes">
              <th scope="row">{{ rotuloMes(m.mes) }}</th>
              <td>{{ formatarMoeda(m.recebido) }}</td>
              <td>{{ formatarMoeda(m.emAberto) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <h3>Parcelas vencidas <span class="suave">({{ dados.quantidadeVencidas }})</span></h3>
      <p v-if="dados.vencidas.length === 0" class="suave">Nada vencido.</p>
      <div v-else class="tabela-rolavel">
        <table>
          <thead>
            <tr>
              <th scope="col">Cliente</th><th scope="col">Contrato</th><th scope="col">Parcela</th><th scope="col">Vencimento</th>
              <th scope="col">Em aberto</th><th scope="col">Dias de atraso</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in ordenadas(dados.vencidas)" :key="p.parcelaId" class="perigo">
              <th scope="row">{{ p.cliente }}</th>
              <td>{{ p.contrato }}</td>
              <td>{{ p.rotulo }}</td>
              <td>{{ formatarData(p.vencimento) }}</td>
              <td>{{ formatarMoeda(p.valorEmAberto) }}</td>
              <td>{{ p.diasAtraso }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <template v-for="l in listas" :key="l.chave">
        <h3>{{ l.titulo }} <span class="suave">({{ dados[l.chave].quantidade }} · {{ formatarMoeda(dados[l.chave].total) }})</span></h3>
        <p v-if="dados[l.chave].parcelas.length === 0" class="suave">Nada a vencer.</p>
        <div v-else class="tabela-rolavel">
          <table>
            <thead>
              <tr><th scope="col">Cliente</th><th scope="col">Contrato</th><th scope="col">Parcela</th><th scope="col">Vencimento</th><th scope="col">Valor</th></tr>
            </thead>
            <tbody>
              <tr v-for="p in dados[l.chave].parcelas" :key="p.parcelaId">
                <th scope="row">{{ p.cliente }}</th>
                <td>{{ p.contrato }}</td>
                <td>{{ p.rotulo }}</td>
                <td>{{ formatarData(p.vencimento) }}</td>
                <td>{{ formatarMoeda(p.valorEmAberto) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>

      <h3>Receita por área de atuação</h3>
      <div class="tabela-rolavel">
        <table>
          <thead>
            <tr><th scope="col">Área</th><th scope="col">Recebido</th><th scope="col">Em aberto</th></tr>
          </thead>
          <tbody>
            <tr v-for="a in dados.porArea" :key="a.area ?? 'sem'">
              <th scope="row">{{ rotuloArea(a.area) }}</th>
              <td>{{ formatarMoeda(a.recebido) }}</td>
              <td>{{ formatarMoeda(a.emAberto) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>
