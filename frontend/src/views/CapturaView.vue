<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  atualizarFiltro,
  dadosDoFiltro,
  resumoDoFiltro,
  excluirFiltro,
  excluirExecucao,
  executarCaptura,
  lerAgendamento,
  ligarAgendamento,
  limparExecucoes,
  listarExecucoes,
  listarFiltros,
  listarIgnorados,
  ROTULO_FONTE,
  type AgendamentoCaptura,
  type ExecucaoCaptura,
  type FiltroCaptura,
  type Ignorado,
} from '../api/publicacoes'
import Feedback from '../components/Feedback.vue'
import { useFeedback } from '../composables/useFeedback'
import { useUsuarios } from '../composables/useUsuarios'
import { formatarDataHora } from '../utils/datas'

const { carregar: carregarUsuarios, nome } = useUsuarios()
const { erro, aviso, executar, ocupado } = useFeedback()

const agenda = ref<AgendamentoCaptura | null>(null)
const execucoes = ref<ExecucaoCaptura[]>([])
const ignorados = ref<Ignorado[]>([])
const filtros = ref<FiltroCaptura[]>([])

async function recarregar() {
  await executar(async () => {
    ;[execucoes.value, ignorados.value, filtros.value, agenda.value] = await Promise.all([listarExecucoes(), listarIgnorados(), listarFiltros(), lerAgendamento()])
  }, '', true)
}

async function alternarAgenda() {
  const ligar = !agenda.value?.ativa
  if (await executar(async () => { agenda.value = await ligarAgendamento(ligar) }, ligar ? 'Captura diária ligada.' : 'Captura diária desligada.')) {
    await recarregar()
  }
}

async function rodar() {
  if (await executar(() => executarCaptura(), 'Captura executada.')) {
    await recarregar()
  }
}

async function alternar(f: FiltroCaptura) {
  const msg = f.ativo ? 'Filtro desativado.' : 'Filtro reativado.'
  if (await executar(() => atualizarFiltro(f.id, { ...dadosDoFiltro(f), ativo: !f.ativo }), msg)) {
    await recarregar()
  }
}

async function remover(f: FiltroCaptura) {
  if (!window.confirm(`Excluir definitivamente o filtro (${resumoDoFiltro(f)})?`)) {
    return
  }
  if (await executar(() => excluirFiltro(f.id), 'Filtro excluído.')) {
    await recarregar()
  }
}

async function apagarExecucao(e: ExecucaoCaptura) {
  if (await executar(() => excluirExecucao(e.id), 'Execução excluída.')) {
    await recarregar()
  }
}

async function apagarTodasExecucoes() {
  if (!window.confirm('Apagar todo o histórico de execuções concluídas?')) {
    return
  }
  if (await executar(() => limparExecucoes(), 'Histórico de execuções apagado.')) {
    await recarregar()
  }
}

onMounted(async () => {
  await carregarUsuarios()
  await recarregar()
})
</script>

<template>
  <section>
    <h1>Captura de publicações</h1>
    <div class="barra-ferramentas">
      <button type="button" class="botao-primario" :disabled="ocupado" @click="rodar">Executar captura agora</button>
    </div>
    <Feedback :erro="erro" :aviso="aviso" />

    <div v-if="agenda" class="cartao">
      <p>
        <b>Captura diária automática:</b> {{ agenda.ativa ? 'ligada' : 'desligada' }}
        <span class="suave"> · roda todo dia às 6h (últimos 3 dias) quando ligada</span>
      </p>
      <p v-if="agenda.ultima" class="suave">Última execução: {{ formatarDataHora(agenda.ultima.inicio) }} · {{ agenda.ultima.fonte }} · {{ agenda.ultima.resultado }}</p>
      <button type="button" class="botao-secundario" :disabled="ocupado" @click="alternarAgenda">
        {{ agenda.ativa ? 'Desligar captura diária' : 'Ligar captura diária' }}
      </button>
    </div>

    <h2>Filtros de captura</h2>
    <p class="suave">Ex.: prefixo "TRT" com exceção "TRT-10" ignora os TRT, menos o TRT-10. O ignorado fica no registro.</p>
    <div class="tabela-rolavel">
      <table>
        <thead>
          <tr><th scope="col">Pessoa</th><th scope="col">Fonte</th><th scope="col">Regra</th><th scope="col"><span class="sr-only">Ações</span></th></tr>
        </thead>
        <tbody>
          <tr v-for="f in filtros" :key="f.id" :class="{ suave: !f.ativo }">
            <td>{{ nome(f.usuarioId) }}</td>
            <td>{{ ROTULO_FONTE[f.fonte] }}</td>
            <td>{{ resumoDoFiltro(f) }}</td>
            <td>
              <button type="button" class="botao-link" @click="alternar(f)">{{ f.ativo ? 'desativar' : 'reativar' }}</button>
              <button type="button" class="botao-link" @click="remover(f)">excluir</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p class="suave">Para criar ou editar filtros, abra <router-link to="/usuarios">Contas e OABs</router-link> → a pessoa → aba Captura.</p>

    <h2>Itens ignorados por filtro</h2>
    <p v-if="ignorados.length === 0" class="suave">Nada ignorado.</p>
    <div v-else class="tabela-rolavel">
      <table>
        <thead>
          <tr><th scope="col">Quando</th><th scope="col">Fonte</th><th scope="col">Tribunal</th><th scope="col">Motivo</th></tr>
        </thead>
        <tbody>
          <tr v-for="(i, k) in ignorados" :key="k">
            <td>{{ formatarDataHora(i.momento) }}</td><td>{{ i.fonte }}</td><td>{{ i.tribunal }}</td><td>{{ i.motivo }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <h2>Execuções</h2>
    <div class="barra-ferramentas" v-if="execucoes.length">
      <button type="button" class="botao-secundario" :disabled="ocupado" @click="apagarTodasExecucoes">Limpar histórico</button>
    </div>
    <p v-if="execucoes.length === 0" class="suave">Nenhuma execução.</p>
    <div v-else class="tabela-rolavel">
      <table>
        <thead>
          <tr><th scope="col">Quando</th><th scope="col">Fonte</th><th scope="col">Resultado</th><th scope="col">Lidos</th><th scope="col">Novos</th><th scope="col">Erro</th><th scope="col"><span class="sr-only">Ações</span></th></tr>
        </thead>
        <tbody>
          <tr v-for="e in execucoes" :key="e.id">
            <td>{{ formatarDataHora(e.inicio) }}</td><td>{{ e.fonte }}</td><td>{{ e.resultado }}</td>
            <td>{{ e.itensLidos }}</td><td>{{ e.itensNovos }}</td><td>{{ e.erro }}</td>
            <td><button v-if="e.resultado !== 'EM_ANDAMENTO'" type="button" class="botao-link" @click="apagarExecucao(e)">excluir</button></td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
