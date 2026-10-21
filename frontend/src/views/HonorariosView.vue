<script setup lang="ts">
import { az, azPor, azRotulos } from '../utils/ordenacao'
import { computed, onMounted, ref } from 'vue'
import { AREAS, listarClientes, ROTULO_AREA, type AreaAtuacao, type Cliente } from '../api/cadastros'
import {
  atualizarItemTabela,
  atualizarPolitica,
  atualizarUrh,
  carregarModeloContrato,
  carregarRelatorioHonorarios,
  criarItemTabela,
  excluirItemTabela,
  importarTabela,
  listarItensTabela,
  listarPropostas,
  listarVersoes,
  restaurarItemTabela,
  ROTULO_FASE,
  salvarModeloContrato,
  SECCIONAIS,
  type DadosItemTabela,
  type Fase,
  type ItemTabela,
  type RelatorioHonorarios,
  type ResumoProposta,
  type Seccional,
  type Versao,
} from '../api/honorarios'
import Feedback from '../components/Feedback.vue'
import Modal from '../components/Modal.vue'
import QuadroSugestao from '../components/QuadroSugestao.vue'
import { useFeedback } from '../composables/useFeedback'
import { formatarDataHora } from '../utils/datas'
import { formatarMoeda } from '../utils/moeda'

const { erro, aviso, executar } = useFeedback()
const aba = ref<'tabelas' | 'propostas' | 'modelo'>('tabelas')

const versoes = ref<Versao[]>([])
const versaoId = ref<number | null>(null)
const itens = ref<ItemTabela[]>([])
const incluirExcluidos = ref(false)
const filtroArea = ref<AreaAtuacao | ''>('')
const busca = ref('')
const editando = ref<number | 'novo' | null>(null)
const form = ref<DadosItemTabela & { descontoPct: number | null }>(vazio())
const urhNova = ref(0)
const politica = ref({ fator: 1.2, descontoPadrao: 10, descontoTrabalhista: 5, descontoPacote: 0 })
const seccionalImport = ref<Seccional>('DF')
const substituirEscritorio = ref(true)
const avisosImportacao = ref<string[]>([])

const relatorio = ref<RelatorioHonorarios | null>(null)
const propostas = ref<ResumoProposta[]>([])
const de = ref('')
const ate = ref('')
const clientes = ref<Cliente[]>([])
const clienteProposta = ref<number | null>(null)
const modelo = ref('')

const versao = computed(() => versoes.value.find((v) => v.id === versaoId.value) ?? null)
const ehEscritorio = computed(() => versao.value?.tabela === 'ESCRITORIO')
const editavel = computed(() => !!versao.value?.vigente)
const filtrados = computed(() => {
  const t = busca.value.trim().toLowerCase()
  return itens.value.filter((i) => (!filtroArea.value || i.area === filtroArea.value) && (!t || i.tipoServico.toLowerCase().includes(t)))
})

function vazio(): DadosItemTabela & { descontoPct: number | null } {
  return {
    area: null, tipoServico: '', fase: null, itemOab: null, valorUrh: null, valor: null, descontoPercentual: null, descontoPct: null, valorSugerido: null,
    exitoTexto: null, exitoMin: null, exitoMax: null, parcelamento: null, observacoes: null,
  }
}

const rotuloVersao = (v: Versao) =>
  `${v.tabela === 'OAB' ? 'OAB/' + v.seccional : 'Escritório'} — ${v.referencia ?? formatarDataHora(v.criadoEm)}${v.vigente ? ' (vigente)' : ' (histórico)'} · ${v.itens} itens`

async function carregarVersoes(manterSelecao = true) {
  await executar(async () => {
    versoes.value = await listarVersoes()
    if (!manterSelecao || !versoes.value.some((v) => v.id === versaoId.value)) {
      versaoId.value = (versoes.value.find((v) => v.vigente && v.tabela === 'OAB') ?? versoes.value[0])?.id ?? null
    }
    const e = versoes.value.find((v) => v.tabela === 'ESCRITORIO' && v.vigente)
    if (e) {
      politica.value = {
        fator: e.fatorEscritorio ?? 1.2,
        descontoPadrao: (e.descontoPadrao ?? 0) * 100,
        descontoTrabalhista: (e.descontoTrabalhista ?? 0) * 100,
        descontoPacote: e.descontoPacote * 100,
      }
    }
  }, '', true)
  await carregarItens()
}

async function carregarItens() {
  if (versaoId.value == null) {
    itens.value = []
    return
  }
  const id = versaoId.value
  await executar(async () => {
    itens.value = await listarItensTabela(id, incluirExcluidos.value)
  }, '', true)
}

function abrirNovo() {
  form.value = vazio()
  editando.value = 'novo'
}

function abrirEdicao(i: ItemTabela) {
  form.value = {
    area: i.area, tipoServico: i.tipoServico, fase: i.fase, itemOab: i.itemOab, valorUrh: i.valorUrh, valor: i.valor, descontoPercentual: i.descontoPercentual,
    descontoPct: i.descontoPercentual == null ? null : i.descontoPercentual * 100, valorSugerido: i.valorSugerido, exitoTexto: i.exitoTexto, exitoMin: i.exitoMin,
    exitoMax: i.exitoMax, parcelamento: i.parcelamento, observacoes: i.observacoes,
  }
  editando.value = i.id
}

async function salvarItem() {
  const f = form.value
  const dados: DadosItemTabela = { ...f, descontoPercentual: f.descontoPct == null ? null : f.descontoPct / 100 }
  delete (dados as Partial<typeof f>).descontoPct
  const id = editando.value
  const ok = await executar(
    () => (id === 'novo' ? criarItemTabela(versaoId.value as number, dados) : atualizarItemTabela(id as number, dados)),
    id === 'novo' ? 'Item incluído.' : 'Item atualizado.',
  )
  if (ok) {
    editando.value = null
    await carregarItens()
  }
}

async function excluir(i: ItemTabela) {
  if (await executar(() => excluirItemTabela(i.id), 'Item excluído (fica no histórico da versão).')) {
    await carregarItens()
  }
}

async function restaurar(i: ItemTabela) {
  if (await executar(() => restaurarItemTabela(i.id), 'Item restaurado.')) {
    await carregarItens()
  }
}

async function salvarUrh() {
  const v = versao.value
  if (v && (await executar(() => atualizarUrh(v.id, urhNova.value), 'URH atualizada; os valores derivados foram recalculados.'))) {
    await carregarVersoes()
  }
}

async function salvarPolitica() {
  const v = versao.value
  const p = politica.value
  if (
    v &&
    (await executar(
      () => atualizarPolitica(v.id, { fator: p.fator, descontoPadrao: p.descontoPadrao / 100, descontoTrabalhista: p.descontoTrabalhista / 100, descontoPacote: p.descontoPacote / 100 }),
      'Política do escritório atualizada.',
    ))
  ) {
    await carregarVersoes()
  }
}

async function importar(ev: Event) {
  const campo = ev.target as HTMLInputElement
  const arquivo = campo.files?.[0]
  if (!arquivo) {
    return
  }
  const ok = await executar(async () => {
    const r = await importarTabela(arquivo, seccionalImport.value, substituirEscritorio.value)
    avisosImportacao.value = r.avisos
    aviso.value = `Planilha importada: ${r.servicos} serviços (${r.semArea} sem área, ${r.semValor} sem valor fixo). A versão anterior ficou no histórico.`
  })
  campo.value = ''
  if (ok) {
    await carregarVersoes(false)
  }
}

async function carregarRelatorio() {
  await executar(async () => {
    relatorio.value = await carregarRelatorioHonorarios(de.value || undefined, ate.value || undefined)
    propostas.value = await listarPropostas()
  }, '', true)
}

async function abrirPropostas() {
  aba.value = 'propostas'
  await carregarRelatorio()
  if (clientes.value.length === 0) {
    await executar(async () => {
      clientes.value = (await listarClientes()).filter((c) => c.ativo)
    }, '', true)
  }
}

async function abrirModelo() {
  aba.value = 'modelo'
  await executar(async () => {
    modelo.value = (await carregarModeloContrato()).texto
  }, '', true)
}

async function salvarModelo() {
  await executar(() => salvarModeloContrato(modelo.value), 'Modelo do contrato salvo.')
}

const rotuloArea = (a: string | null) => (a ? (ROTULO_AREA[a as AreaAtuacao] ?? a) : 'Sem área')

onMounted(() => carregarVersoes(false))
</script>

<template>
  <section>
    <h1>Honorários</h1>
    <p class="suave">Tabela da OAB (por seccional) e tabela do escritório, sempre separadas. Restrito às sócias.</p>
    <Feedback :erro="erro" :aviso="aviso" />

    <div class="grupo-botoes" role="tablist">
      <button type="button" class="botao-secundario" :aria-pressed="aba === 'tabelas'" @click="aba = 'tabelas'">Tabelas</button>
      <button type="button" class="botao-secundario" :aria-pressed="aba === 'propostas'" @click="abrirPropostas">Propostas e relatórios</button>
      <button type="button" class="botao-secundario" :aria-pressed="aba === 'modelo'" @click="abrirModelo">Modelo do contrato</button>
    </div>

    <!-- ===== tabelas ===== -->
    <template v-if="aba === 'tabelas'">
      <h2>Importar planilha</h2>
      <div class="formulario">
        <label>
          Seccional da tabela da OAB na planilha
          <select v-model="seccionalImport">
            <option v-for="s in SECCIONAIS" :key="s" :value="s">OAB/{{ s }}</option>
          </select>
        </label>
        <label class="opcao-linha"><input v-model="substituirEscritorio" type="checkbox" /> Substituir também a tabela do escritório</label>
        <label>Planilha (.xlsx) <input type="file" accept=".xlsx" @change="importar" /></label>
        <p class="suave">Cada importação cria uma nova versão; a anterior fica no histórico, consultável abaixo.</p>
        <ul v-if="avisosImportacao.length" class="lista-simples">
          <li v-for="a in avisosImportacao.slice(0, 20)" :key="a">⚠ {{ a }}</li>
        </ul>
      </div>

      <h2>Tabela</h2>
      <label>
        Versão
        <select v-model="versaoId" @change="carregarItens">
          <option v-for="v in versoes" :key="v.id" :value="v.id">{{ rotuloVersao(v) }}</option>
        </select>
      </label>
      <p v-if="versoes.length === 0" class="suave">Nenhuma tabela importada ainda.</p>

      <template v-if="versao">
        <form v-if="versao.tabela === 'OAB' && editavel" class="grupo-botoes" @submit.prevent="salvarUrh">
          <label>Valor da URH (R$) — hoje {{ versao.valorUrh ?? '—' }} <input v-model.number="urhNova" type="number" min="0.01" step="0.0001" required /></label>
          <button type="submit" class="botao-secundario">Atualizar URH</button>
        </form>
        <form v-if="ehEscritorio && editavel" class="formulario" @submit.prevent="salvarPolitica">
          <label>Fator sobre o mínimo da OAB <input v-model.number="politica.fator" type="number" min="1" step="0.01" /></label>
          <label>Desconto padrão (%) <input v-model.number="politica.descontoPadrao" type="number" min="0" max="100" step="0.1" /></label>
          <label>Desconto trabalhista (%) <input v-model.number="politica.descontoTrabalhista" type="number" min="0" max="100" step="0.1" /></label>
          <label>Desconto por pacote (%) <input v-model.number="politica.descontoPacote" type="number" min="0" max="100" step="0.1" /></label>
          <button type="submit" class="botao-secundario">Salvar política</button>
        </form>

        <div class="barra-ferramentas">
          <label>Buscar <input v-model="busca" type="search" placeholder="Tipo de serviço" /></label>
          <label>
            Área
            <select v-model="filtroArea">
              <option value="">Todas</option>
              <option v-for="a in azPor(AREAS, ROTULO_AREA)" :key="a" :value="a">{{ ROTULO_AREA[a] }}</option>
            </select>
          </label>
          <label class="opcao-linha"><input v-model="incluirExcluidos" type="checkbox" @change="carregarItens" /> Mostrar excluídos</label>
          <button v-if="editavel" type="button" class="botao-primario" @click="abrirNovo">Novo serviço</button>
        </div>

        <Modal :aberto="editando !== null" :titulo="editando === 'novo' ? 'Novo serviço' : 'Editar serviço'" form-id="f-salvarItem-949" salvar-rotulo="Salvar" :erro="erro" larg="lg" @fechar="editando = null">
          <form id="f-salvarItem-949" class="formulario" @submit.prevent="salvarItem">
            <label>
              Área de atuação
              <select v-model="form.area">
                <option :value="null">(a definir)</option>
                <option v-for="a in azPor(AREAS, ROTULO_AREA)" :key="a" :value="a">{{ ROTULO_AREA[a] }}</option>
              </select>
            </label>
            <label>Tipo de serviço <input v-model="form.tipoServico" required maxlength="300" /></label>
            <label>
              Fase ou rito
              <select v-model="form.fase">
                <option :value="null">—</option>
                <option v-for="[f, r] in azRotulos(ROTULO_FASE)" :key="f" :value="f as Fase">{{ r }}</option>
              </select>
            </label>
            <label>Item da tabela da OAB <input v-model="form.itemOab" maxlength="30" /></label>
            <template v-if="!ehEscritorio">
              <label>Valor mínimo em URH <input v-model.number="form.valorUrh" type="number" min="0" step="0.01" /></label>
              <label>Valor mínimo da OAB (R$) <input v-model.number="form.valor" type="number" min="0" step="0.01" /></label>
            </template>
            <template v-else>
              <label>Valor de tabela do escritório (R$) <input v-model.number="form.valor" type="number" min="0" step="0.01" /></label>
              <label>Desconto padrão (%) <input v-model.number="form.descontoPct" type="number" min="0" max="100" step="0.1" /></label>
              <label>Valor final sugerido (R$) <input v-model.number="form.valorSugerido" type="number" min="0" step="0.01" /></label>
              <label>Forma de parcelamento sugerida <input v-model="form.parcelamento" maxlength="400" /></label>
            </template>
            <label>Honorários de êxito (texto) <input v-model="form.exitoTexto" maxlength="300" /></label>
            <label>Êxito mínimo (%) <input v-model.number="form.exitoMin" type="number" min="0" max="100" step="0.1" /></label>
            <label>Êxito máximo (%) <input v-model.number="form.exitoMax" type="number" min="0" max="100" step="0.1" /></label>
            <label>Observações (por ato, por hora, por audiência…) <input v-model="form.observacoes" maxlength="500" /></label>
          </form>
        </Modal>

        <p class="suave">{{ filtrados.length }} serviço(s).</p>
        <div class="tabela-rolavel">
          <table>
            <thead>
              <tr>
                <th scope="col">Área</th><th scope="col">Serviço</th><th scope="col">Fase</th><th scope="col">Item OAB</th>
                <th scope="col">{{ ehEscritorio ? 'Tabela' : 'Mínimo OAB' }}</th>
                <th v-if="ehEscritorio" scope="col">Sugerido</th>
                <th scope="col">Êxito</th><th scope="col"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="i in filtrados.slice(0, 300)" :key="i.id" :class="{ inativo: !i.ativo }">
                <td>{{ rotuloArea(i.area) }}</td>
                <th scope="row">{{ i.tipoServico }}<br /><small class="suave">{{ i.observacoes }}</small></th>
                <td>{{ i.fase ? ROTULO_FASE[i.fase] : '—' }}</td>
                <td>{{ i.itemOab ?? '—' }}</td>
                <td>{{ i.valor == null ? 'sem valor fixo' : formatarMoeda(i.valor) }}<template v-if="i.valorUrh"> <small class="suave">({{ i.valorUrh }} URH)</small></template></td>
                <td v-if="ehEscritorio">{{ i.valorSugerido == null ? '—' : formatarMoeda(i.valorSugerido) }}</td>
                <td>{{ i.exitoTexto ?? '—' }}</td>
                <td>
                  <template v-if="editavel">
                    <button v-if="i.ativo" type="button" class="botao-link" @click="abrirEdicao(i)">editar</button>
                    <button v-if="i.ativo" type="button" class="botao-link" @click="excluir(i)">excluir</button>
                    <button v-else type="button" class="botao-link" @click="restaurar(i)">restaurar</button>
                  </template>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </template>

    <!-- ===== propostas e relatorios ===== -->
    <template v-if="aba === 'propostas'">
      <h2>Nova proposta para um cliente</h2>
      <label>
        Cliente
        <select v-model="clienteProposta">
          <option :value="null">Selecione…</option>
          <option v-for="c in az(clientes, (x) => x.nome)" :key="c.id" :value="c.id">{{ c.nome }}</option>
        </select>
      </label>
      <QuadroSugestao v-if="clienteProposta" :key="clienteProposta" :cliente-id="clienteProposta" :pode-assinar="true" />

      <h2>Relatórios</h2>
      <div class="barra-ferramentas">
        <label>De <input v-model="de" type="date" /></label>
        <label>Até <input v-model="ate" type="date" /></label>
        <button type="button" class="botao-secundario" @click="carregarRelatorio">Atualizar</button>
      </div>
      <template v-if="relatorio">
        <p class="suave">Período de {{ relatorio.de }} a {{ relatorio.ate }}.</p>

        <h3>Taxa de conversão dos atendimentos</h3>
        <p>
          Reuniões realizadas: <b>{{ relatorio.conversaoGeral.reunioes }}</b> · contratos fechados: <b>{{ relatorio.conversaoGeral.fechadas }}</b> · conversão:
          <b>{{ relatorio.conversaoGeral.taxa }}%</b>
        </p>
        <div v-if="relatorio.conversao.length" class="tabela-rolavel">
          <table>
            <thead><tr><th scope="col">Advogado</th><th scope="col">Reuniões</th><th scope="col">Fechados</th><th scope="col">Conversão</th></tr></thead>
            <tbody>
              <tr v-for="c in relatorio.conversao" :key="c.advogadoId"><th scope="row">{{ c.advogado }}</th><td>{{ c.reunioes }}</td><td>{{ c.fechadas }}</td><td>{{ c.taxa }}%</td></tr>
            </tbody>
          </table>
        </div>

        <h3>Valor sugerido × valor fechado</h3>
        <p v-if="relatorio.sugeridoXFechado.length === 0" class="suave">Nenhum contrato fechado no período.</p>
        <div v-else class="tabela-rolavel">
          <table>
            <thead><tr><th scope="col">Mês</th><th scope="col">Advogado</th><th scope="col">Área</th><th scope="col">Propostas</th><th scope="col">Sugerido</th><th scope="col">Fechado</th></tr></thead>
            <tbody>
              <tr v-for="(x, n) in relatorio.sugeridoXFechado" :key="n">
                <td>{{ x.mes }}</td><td>{{ x.advogado }}</td><td>{{ rotuloArea(x.area) }}</td><td>{{ x.propostas }}</td><td>{{ formatarMoeda(x.sugerido) }}</td><td>{{ formatarMoeda(x.fechado) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <h3>Ticket médio por área</h3>
        <div v-if="relatorio.ticketMedio.length" class="tabela-rolavel">
          <table>
            <thead><tr><th scope="col">Área</th><th scope="col">Contratos</th><th scope="col">Total</th><th scope="col">Ticket médio</th></tr></thead>
            <tbody>
              <tr v-for="t in relatorio.ticketMedio" :key="t.area ?? 'sem'"><th scope="row">{{ rotuloArea(t.area) }}</th><td>{{ t.contratos }}</td><td>{{ formatarMoeda(t.total) }}</td><td>{{ formatarMoeda(t.ticketMedio) }}</td></tr>
            </tbody>
          </table>
        </div>

        <h3>Fechados abaixo da tabela da OAB (para conferência)</h3>
        <p v-if="relatorio.abaixoDaTabela.length === 0" class="suave">Nenhum caso no período.</p>
        <div v-else class="tabela-rolavel">
          <table>
            <thead><tr><th scope="col">Data</th><th scope="col">Cliente</th><th scope="col">Advogado</th><th scope="col">Mínimo OAB</th><th scope="col">Fechado</th><th scope="col">Diferença</th><th scope="col">Motivo</th><th scope="col">Justificativa</th></tr></thead>
            <tbody>
              <tr v-for="a in relatorio.abaixoDaTabela" :key="a.propostaId" class="perigo">
                <td>{{ a.data }}</td><td>{{ a.cliente }}</td><td>{{ a.advogado }}</td><td>{{ formatarMoeda(a.minimoOab) }}</td><td>{{ formatarMoeda(a.fechado) }}</td>
                <td>{{ formatarMoeda(a.diferenca) }}</td><td>{{ a.tipo }}</td><td>{{ a.justificativa }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>

      <h3>Propostas</h3>
      <div class="tabela-rolavel">
        <table>
          <thead><tr><th scope="col">Cliente</th><th scope="col">Advogado</th><th scope="col">Situação</th><th scope="col">Sugerido</th><th scope="col">Fechado</th><th scope="col">Contrato</th></tr></thead>
          <tbody>
            <tr v-for="p in propostas" :key="p.id" :class="{ perigo: p.abaixoDaOab }">
              <th scope="row">{{ p.cliente }}</th><td>{{ p.advogado }}</td><td>{{ p.status }}</td><td>{{ formatarMoeda(p.valorSugerido) }}</td>
              <td>{{ p.valorFechado == null ? '—' : formatarMoeda(p.valorFechado) }}</td><td>{{ p.contratoLancado ? 'lançado' : '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <!-- ===== modelo ===== -->
    <template v-if="aba === 'modelo'">
      <h2>Modelo da minuta do contrato de honorários</h2>
      <p class="suave">
        Texto com campos entre chaves duplas: cliente_nome, cliente_documento, cliente_rg, cliente_estado_civil, cliente_endereco, cliente_telefone, cliente_email,
        escritorio_nome, advogado_nome, servicos, valor_total, valor_extenso, forma_pagamento, parcelas, exito, local_data. Linhas começadas por “# ” viram título.
        Enquanto não houver um modelo salvo, vale um modelo-padrão genérico.
      </p>
      <label>Texto do modelo <textarea v-model="modelo" rows="24"></textarea></label>
      <button type="button" class="botao-primario" @click="salvarModelo">Salvar modelo</button>
    </template>
  </section>
</template>
