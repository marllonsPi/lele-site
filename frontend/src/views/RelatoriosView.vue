<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listarClientes, type Cliente } from '../api/cadastros'
import {
  aprovarRelatorioCliente,
  buscarRelatorioCliente,
  criarRelatorioCliente,
  editarRelatorioCliente,
  enviarInternoAsSocias,
  enviarRelatorioCliente,
  gerarInterno,
  relatoriosDoCliente,
  ROTULO_ESTADO_RELATORIO,
  ROTULO_TIPO_INTERNO,
  urlPdfCliente,
  urlPdfInterno,
  type RelatorioCliente,
  type RelatorioDados,
  type TipoInterno,
} from '../api/relatorios'
import Feedback from '../components/Feedback.vue'
import { useFeedback } from '../composables/useFeedback'
import { useSessao } from '../composables/useSessao'
import { useUsuarios } from '../composables/useUsuarios'
import { formatarData, formatarDataHora } from '../utils/datas'
import { formatarMoeda } from '../utils/moeda'

const { temPerfil } = useSessao()
const { usuarios, carregar: carregarUsuarios, nome } = useUsuarios()
const interno = useFeedback()
const cliente = useFeedback()

const clientes = ref<Cliente[]>([])
const ehSocia = computed(() => temPerfil('SOCIA'))

// ---- 14.1 internos
const tipo = ref<TipoInterno>('AGENDA_DIA')
const filtros = ref({ de: '', ate: '', usuarioId: null as number | null, clienteId: null as number | null })
const dados = ref<RelatorioDados | null>(null)

const filtrosApi = () => ({
  de: filtros.value.de || undefined,
  ate: filtros.value.ate || undefined,
  usuarioId: filtros.value.usuarioId,
  clienteId: filtros.value.clienteId,
})

async function ver() {
  dados.value = null
  await interno.executar(async () => {
    dados.value = await gerarInterno(tipo.value, filtrosApi())
  })
}

async function enviarSocias() {
  let enviados = 0
  const ok = await interno.executar(async () => {
    enviados = (await enviarInternoAsSocias(tipo.value, filtrosApi())).enviados
  })
  if (ok) {
    interno.aviso.value = `Enviado a ${enviados} sócia(s).`
  }
}

// ---- 14.2 ao cliente
const clienteId = ref<number | null>(null)
const novo = ref({ de: '', ate: '', desdeUltimo: false, incluirFinanceiro: false })
const historico = ref<RelatorioCliente[]>([])
const relatorio = ref<RelatorioCliente | null>(null)
const comentarios = ref('')
const canal = ref('EMAIL')

const editavel = computed(() => relatorio.value !== null && ['RASCUNHO', 'EM_REVISAO'].includes(relatorio.value.estado))
const comPdf = computed(() => relatorio.value !== null && ['APROVADO', 'ENVIADO'].includes(relatorio.value.estado))

function abrir(r: RelatorioCliente) {
  relatorio.value = r
  comentarios.value = r.conteudo.comentarios ?? ''
}

async function carregarHistorico() {
  relatorio.value = null
  historico.value = []
  if (clienteId.value === null) {
    return
  }
  const id = clienteId.value
  await cliente.executar(async () => {
    historico.value = await relatoriosDoCliente(id)
  }, '', true)
}

async function gerarRascunho() {
  const ok = await cliente.executar(async () => {
    abrir(
      await criarRelatorioCliente({
        clienteId: clienteId.value as number,
        processoIds: null,
        de: novo.value.de || null,
        ate: novo.value.ate || null,
        desdeUltimoRelatorio: novo.value.desdeUltimo,
        incluirFinanceiro: novo.value.incluirFinanceiro,
      }),
    )
  }, 'Rascunho gerado. Revise antes de aprovar: nada sai sem aprovação.')
  if (ok) {
    await carregarHistorico2()
  }
}

async function carregarHistorico2() {
  const id = clienteId.value
  if (id !== null) {
    await cliente.executar(async () => {
      historico.value = await relatoriosDoCliente(id)
    }, '', true)
  }
}

async function recarregarRelatorio() {
  const r = relatorio.value
  if (r) {
    abrir(await buscarRelatorioCliente(r.id))
    await carregarHistorico2()
  }
}

async function salvar() {
  const r = relatorio.value!
  await cliente.executar(async () => {
    abrir(await editarRelatorioCliente(r.id, { ...r.conteudo, comentarios: comentarios.value }))
  }, 'Edição salva.')
}

async function aprovar() {
  const r = relatorio.value!
  await cliente.executar(async () => {
    if (comentarios.value !== (r.conteudo.comentarios ?? '')) {
      await editarRelatorioCliente(r.id, { ...r.conteudo, comentarios: comentarios.value })
    }
    await aprovarRelatorioCliente(r.id)
    await recarregarRelatorio()
  }, 'Relatório aprovado.')
}

async function enviar() {
  const r = relatorio.value!
  await cliente.executar(async () => {
    await enviarRelatorioCliente(r.id, canal.value)
    await recarregarRelatorio()
  }, 'Relatório enviado; os andamentos incluídos foram marcados como repassados.')
}

onMounted(async () => {
  await carregarUsuarios()
  await cliente.executar(async () => {
    clientes.value = await listarClientes()
  }, '', true)
})
</script>

<template>
  <section>
    <h1>Relatórios</h1>

    <h2>Relatórios internos</h2>
    <div class="formulario">
      <label>
        Tipo
        <select v-model="tipo">
          <option v-for="(r, v) in ROTULO_TIPO_INTERNO" :key="v" :value="v">{{ r }}</option>
        </select>
      </label>
      <label>De <input v-model="filtros.de" type="date" /></label>
      <label>Até <input v-model="filtros.ate" type="date" /></label>
      <label v-if="tipo === 'AGENDA_USUARIO' || ehSocia">
        Pessoa
        <select v-model="filtros.usuarioId">
          <option :value="null">Todas / eu</option>
          <option v-for="u in usuarios" :key="u.id" :value="u.id">{{ u.nome }}</option>
        </select>
      </label>
      <label v-if="tipo === 'FINANCEIRO_CLIENTE' || tipo === 'ATENDIMENTOS'">
        Cliente
        <select v-model="filtros.clienteId">
          <option :value="null">Todos</option>
          <option v-for="c in clientes" :key="c.id" :value="c.id">{{ c.nome }}</option>
        </select>
      </label>
      <div class="grupo-botoes">
        <button type="button" class="botao-primario" @click="ver">Ver</button>
        <a class="botao-secundario como-botao" :href="urlPdfInterno(tipo, filtrosApi())" target="_blank" rel="noopener">Abrir PDF</a>
        <button type="button" class="botao-secundario" @click="enviarSocias">Enviar às sócias</button>
      </div>
    </div>
    <Feedback :erro="interno.erro.value" :aviso="interno.aviso.value" />
    <template v-if="dados">
      <h3>{{ dados.titulo }}</h3>
      <p class="suave">{{ dados.subtitulo }}</p>
      <p v-if="dados.linhas.length === 0" class="suave">Nenhum item.</p>
      <div v-else class="tabela-rolavel">
        <table>
          <thead>
            <tr><th v-for="c in dados.colunas" :key="c" scope="col">{{ c }}</th></tr>
          </thead>
          <tbody>
            <tr v-for="(l, i) in dados.linhas" :key="i"><td v-for="(c, j) in l" :key="j">{{ c }}</td></tr>
          </tbody>
        </table>
      </div>
    </template>

    <h2>Relatório resumido ao cliente</h2>
    <p class="suave">Rascunho → revisão → aprovação → envio. Nada sai sem aprovação; o texto não inclui observações internas.</p>
    <div class="barra-ferramentas">
      <label>
        Cliente
        <select v-model="clienteId" @change="carregarHistorico">
          <option :value="null">Selecione</option>
          <option v-for="c in clientes" :key="c.id" :value="c.id">{{ c.nome }}</option>
        </select>
      </label>
    </div>
    <Feedback :erro="cliente.erro.value" :aviso="cliente.aviso.value" />

    <template v-if="clienteId !== null">
      <form class="formulario" @submit.prevent="gerarRascunho">
        <label>De <input v-model="novo.de" type="date" :disabled="novo.desdeUltimo" /></label>
        <label>Até <input v-model="novo.ate" type="date" /></label>
        <label class="marcar"><input v-model="novo.desdeUltimo" type="checkbox" /> Desde o último relatório</label>
        <label v-if="ehSocia" class="marcar"><input v-model="novo.incluirFinanceiro" type="checkbox" /> Incluir financeiro</label>
        <button type="submit" class="botao-primario">Gerar rascunho</button>
      </form>

      <h3 v-if="historico.length">Relatórios deste cliente</h3>
      <ul class="lista-simples">
        <li v-for="h in historico" :key="h.id">
          <button type="button" class="botao-link" @click="abrir(h)">
            #{{ h.id }} · até {{ formatarData(h.periodoFim) }} · {{ ROTULO_ESTADO_RELATORIO[h.estado] }}
          </button>
        </li>
      </ul>
    </template>

    <article v-if="relatorio" class="cartao">
      <header class="cartao-topo">
        <b>Relatório #{{ relatorio.id }}</b>
        <span class="selo">{{ ROTULO_ESTADO_RELATORIO[relatorio.estado] }}</span>
        <span class="cartao-quando">
          {{ relatorio.periodoInicio ? formatarData(relatorio.periodoInicio) : 'Todo o histórico' }} a {{ formatarData(relatorio.periodoFim) }}
        </span>
      </header>
      <p v-if="relatorio.aprovadoPor" class="suave">Aprovado por {{ nome(relatorio.aprovadoPor) }} em {{ formatarDataHora(relatorio.aprovadoEm) }}</p>
      <p v-if="relatorio.estado === 'ENVIADO'" class="suave">
        Enviado por {{ relatorio.canal }}<template v-if="relatorio.enviadoEm"> em {{ formatarDataHora(relatorio.enviadoEm) }}</template>
      </p>

      <section v-for="p in relatorio.conteudo.processos" :key="p.numero" class="detalhe">
        <h3>Processo {{ p.numero }} · {{ p.tribunal }}<template v-if="p.situacao"> · {{ p.situacao }}</template></h3>
        <p class="suave">Partes: {{ p.partes.join('; ') }}</p>
        <template v-for="[titulo, itens] in [['Movimentações', p.movimentacoes], ['O que o escritório fez', p.feitos], ['Próximos passos', p.proximosPassos]] as const" :key="titulo">
          <h4>{{ titulo }}</h4>
          <p v-if="itens.length === 0" class="suave">Nada no período.</p>
          <ul class="lista-simples">
            <li v-for="(i, k) in itens" :key="k">{{ formatarData(i.data) }} — {{ i.texto }}</li>
          </ul>
        </template>
      </section>
      <p v-if="relatorio.conteudo.financeiro">
        Financeiro: pagas {{ formatarMoeda(relatorio.conteudo.financeiro.parcelasPagas) }} · a vencer
        {{ formatarMoeda(relatorio.conteudo.financeiro.parcelasAVencer) }}
      </p>

      <label>
        Comentários do advogado
        <textarea v-model="comentarios" rows="4" :disabled="!editavel"></textarea>
      </label>

      <div class="cartao-acoes">
        <template v-if="editavel">
          <button type="button" class="botao-secundario" @click="salvar">Salvar edição</button>
          <button type="button" class="botao-primario" @click="aprovar">Aprovar</button>
        </template>
        <a v-if="comPdf" class="botao-secundario como-botao" :href="urlPdfCliente(relatorio.id)" target="_blank" rel="noopener">Abrir PDF</a>
        <span v-else class="suave">O PDF só fica disponível depois de aprovado.</span>
        <template v-if="relatorio.estado === 'APROVADO'">
          <label>
            Enviar por
            <select v-model="canal">
              <option value="EMAIL">E-mail</option>
              <option value="WHATSAPP">WhatsApp</option>
              <option value="IMPRESSAO">Impressão</option>
            </select>
          </label>
          <button type="button" class="botao-primario" @click="enviar">Enviar ao cliente</button>
        </template>
      </div>
    </article>
  </section>
</template>
