<script setup lang="ts">
import { azPor, azRotulos } from '../utils/ordenacao'
import { computed, onMounted, ref, watch } from 'vue'
import { AREAS, ROTULO_AREA, type AreaAtuacao } from '../api/cadastros'
import {
  assinarProposta,
  carregarCatalogo,
  fecharProposta,
  propostaDoAtendimento,
  propostaNaoFechada,
  recalcularProposta,
  ROTULO_MOTIVO,
  SECCIONAIS,
  sugerirNoAtendimento,
  sugerirParaCliente,
  urlMinuta,
  type MotivoJustificativa,
  type OpcaoParcelamento,
  type Proposta,
  type Seccional,
  type ServicoCatalogo,
} from '../api/honorarios'
import { enviar } from '../api/http'
import { mensagemDeErro } from '../composables/useFeedback'
import { hojeIso } from '../utils/datas'
import { formatarMoeda } from '../utils/moeda'

const props = defineProps<{ agendamentoId?: number; clienteId?: number; podeAssinar: boolean }>()

const catalogo = ref<ServicoCatalogo[]>([])
const proposta = ref<Proposta | null>(null)
const seccional = ref<Seccional>('DF')
const area = ref<AreaAtuacao | ''>('')
const escolhido = ref('')
const servicos = ref<string[]>([])
const erro = ref('')
const aviso = ref('')
const ocupado = ref(false)
const fechamento = ref({
  valor: 0,
  entrada: 0,
  parcelas: 0,
  primeiroVencimento: hojeIso(30),
  forma: '',
  motivo: '' as MotivoJustificativa | '',
  justificativa: '',
})
const assinatura = ref(hojeIso())

const doArea = computed(() => catalogo.value.filter((s) => (area.value ? s.area === area.value : true)))
const nomeServico = (chave: string) => catalogo.value.find((s) => s.chave === chave)?.tipoServico ?? 'Serviço'
const encerrada = computed(() => proposta.value?.status === 'FECHADA')
const abaixo = computed(() => !!proposta.value && fechamento.value.valor < proposta.value.valorMinimoOab)
const diferenca = computed(() => (proposta.value ? proposta.value.valorMinimoOab - fechamento.value.valor : 0))

function carregarEstado(p: Proposta) {
  proposta.value = p
  seccional.value = p.seccional
  servicos.value = p.itens.map((i) => i.chave)
  fechamento.value = {
    valor: p.valorFechado ?? p.valorSugerido,
    entrada: p.entrada ?? 0,
    parcelas: p.parcelas ?? 0,
    primeiroVencimento: p.primeiroVencimento ?? hojeIso(30),
    forma: p.formaPagamento ?? '',
    motivo: p.justificativaTipo ?? '',
    justificativa: p.justificativa ?? '',
  }
}

async function acao(f: () => Promise<void>, sucesso = '') {
  erro.value = ''
  aviso.value = ''
  ocupado.value = true
  try {
    await f()
    aviso.value = sucesso
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    ocupado.value = false
  }
}

/** Escolher um servico na lista o inclui na proposta (varias demandas somam). */
watch(escolhido, (chave) => {
  if (chave && !servicos.value.includes(chave)) {
    servicos.value = [...servicos.value, chave]
  }
  escolhido.value = ''
})

function remover(chave: string) {
  servicos.value = servicos.value.filter((c) => c !== chave)
}

async function calcular() {
  await acao(async () => {
    if (proposta.value) {
      carregarEstado(await recalcularProposta(proposta.value.id, seccional.value, servicos.value))
    } else if (props.agendamentoId) {
      carregarEstado(await sugerirNoAtendimento(props.agendamentoId, seccional.value, servicos.value))
    } else if (props.clienteId) {
      carregarEstado(await sugerirParaCliente(props.clienteId, seccional.value, servicos.value))
    }
  })
}

function usar(o: OpcaoParcelamento) {
  fechamento.value.valor = o.total
  fechamento.value.entrada = o.parcelas === 0 ? o.total : o.entrada
  fechamento.value.parcelas = o.parcelas === 0 ? 0 : o.parcelas
  fechamento.value.forma = o.rotulo
}

async function fechar() {
  const p = proposta.value
  if (!p) {
    return
  }
  await acao(async () => {
    carregarEstado(
      await fecharProposta(p.id, {
        valorFechado: fechamento.value.valor,
        entrada: fechamento.value.entrada || null,
        parcelas: fechamento.value.parcelas || null,
        primeiroVencimento: fechamento.value.parcelas > 0 ? fechamento.value.primeiroVencimento : null,
        formaPagamento: fechamento.value.forma || null,
        justificativaTipo: abaixo.value ? (fechamento.value.motivo || null) : null,
        justificativa: abaixo.value ? fechamento.value.justificativa : null,
      }),
    )
  }, 'Valor fechado registrado (o valor sugerido continua guardado para os relatórios).')
}

async function semFechamento() {
  const p = proposta.value
  if (p) {
    await acao(async () => carregarEstado(await propostaNaoFechada(p.id)), 'Registrado: reunião sem fechamento.')
  }
}

async function minuta() {
  const p = proposta.value
  if (!p) {
    return
  }
  await acao(async () => {
    const r = await enviar('POST', urlMinuta(p.id))
    if (!r.ok) {
      throw new Error('minuta')
    }
    const blob = await r.blob()
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = 'Minuta de contrato de honorários.docx'
    a.click()
    URL.revokeObjectURL(a.href)
  }, 'Minuta gerada e guardada na pasta do cliente (restrita às sócias).')
}

async function assinar() {
  const p = proposta.value
  if (p) {
    await acao(async () => carregarEstado(await assinarProposta(p.id, assinatura.value)), 'Contrato assinado: parcelas lançadas no financeiro.')
  }
}

onMounted(async () => {
  await acao(async () => {
    catalogo.value = await carregarCatalogo()
    if (props.agendamentoId) {
      const p = await propostaDoAtendimento(props.agendamentoId)
      if (p) {
        carregarEstado(p)
      }
    }
  })
})
</script>

<template>
  <section class="detalhe" aria-labelledby="titulo-quadro">
    <h3 id="titulo-quadro">Sugestão de honorários</h3>
    <p v-if="erro" class="erro" role="alert">{{ erro }}</p>
    <p v-if="aviso" class="suave" role="status">{{ aviso }}</p>

    <form v-if="!encerrada" class="formulario" @submit.prevent="calcular">
      <label>
        Seccional do local onde o serviço será prestado
        <select v-model="seccional">
          <option v-for="s in SECCIONAIS" :key="s" :value="s">OAB/{{ s }}</option>
        </select>
      </label>
      <label>
        Área de atuação
        <select v-model="area">
          <option value="">Todas</option>
          <option v-for="a in azPor(AREAS, ROTULO_AREA)" :key="a" :value="a">{{ ROTULO_AREA[a] }}</option>
        </select>
      </label>
      <label>
        Tipo de serviço pretendido
        <select v-model="escolhido">
          <option value="">Selecione…</option>
          <option v-for="s in doArea" :key="s.chave" :value="s.chave">{{ s.area ? ROTULO_AREA[s.area] + ' — ' : '' }}{{ s.tipoServico }}</option>
        </select>
      </label>
      <ul v-if="servicos.length" class="lista-simples">
        <li v-for="c in servicos" :key="c">
          {{ nomeServico(c) }}
          <button type="button" class="botao-link" @click="remover(c)">remover</button>
        </li>
      </ul>
      <p v-if="servicos.length > 1" class="suave">Mais de um serviço: o quadro soma tudo e aplica o desconto por pacote definido pelas sócias.</p>
      <button type="submit" class="botao-primario" :disabled="ocupado || servicos.length === 0">Calcular sugestão</button>
    </form>

    <template v-if="proposta && proposta.quadro">
      <p v-if="!proposta.tabelaOabVigente" class="alerta-incompleto" role="alert">
        Não há tabela da OAB/{{ proposta.seccional }} cadastrada: não há piso para conferir. Peça às sócias para importá-la.
      </p>
      <div class="tabela-rolavel">
        <table>
          <thead>
            <tr><th scope="col">Serviço</th><th scope="col">Mínimo da OAB</th><th scope="col">Tabela do escritório</th><th scope="col">Sugerido</th></tr>
          </thead>
          <tbody>
            <tr v-for="l in proposta.quadro.linhas" :key="l.servico.chave">
              <th scope="row">{{ l.servico.tipo }}<template v-for="a in l.avisos" :key="a"><br /><small class="suave">{{ a }}</small></template></th>
              <td>{{ l.minimoOab == null ? '—' : formatarMoeda(l.minimoOab) }}</td>
              <td>{{ l.valorTabela == null ? '—' : formatarMoeda(l.valorTabela) }}</td>
              <td>{{ l.valorSugerido == null ? '—' : formatarMoeda(l.valorSugerido) }}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <th scope="row">Total</th>
              <td>{{ formatarMoeda(proposta.quadro.minimoOabTotal) }}</td>
              <td>{{ formatarMoeda(proposta.quadro.valorTabelaTotal) }}</td>
              <td><b>{{ formatarMoeda(proposta.quadro.valorSugeridoTotal) }}</b></td>
            </tr>
          </tfoot>
        </table>
      </div>
      <p v-for="a in proposta.quadro.avisos" :key="a" class="suave">{{ a }}</p>
      <template v-if="proposta.quadro.exito.length">
        <h4>Honorários de êxito sugeridos</h4>
        <ul class="lista-simples"><li v-for="e in proposta.quadro.exito" :key="e">{{ e }}</li></ul>
      </template>

      <template v-if="proposta.quadro.opcoes.length">
        <h4>Parcelamento</h4>
        <div class="tabela-rolavel">
          <table>
            <thead>
              <tr><th scope="col">Condição</th><th scope="col">Entrada</th><th scope="col">Parcelas</th><th scope="col">Total</th><th scope="col"></th></tr>
            </thead>
            <tbody>
              <tr v-for="o in proposta.quadro.opcoes" :key="o.rotulo">
                <th scope="row">{{ o.rotulo }}</th>
                <td>{{ o.parcelas === 0 ? '—' : formatarMoeda(o.entrada) }}</td>
                <td>{{ o.parcelas === 0 ? 'à vista' : o.parcelas + ' × ' + formatarMoeda(o.valorParcela) }}</td>
                <td>{{ formatarMoeda(o.total) }}</td>
                <td><button v-if="!encerrada" type="button" class="botao-link" @click="usar(o)">usar</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>

      <h4>Valor negociado</h4>
      <p v-if="encerrada" class="suave">
        Sugerido {{ formatarMoeda(proposta.valorSugerido) }} · fechado <b>{{ formatarMoeda(proposta.valorFechado) }}</b>
        <template v-if="proposta.abaixoDaOab"> · abaixo do mínimo da OAB em {{ formatarMoeda(proposta.diferencaAbaixoOab) }} ({{ proposta.justificativaTipo ? ROTULO_MOTIVO[proposta.justificativaTipo] : '' }})</template>
      </p>
      <form v-if="!encerrada || !proposta.dataAssinatura" class="formulario" @submit.prevent="fechar">
        <label>Valor fechado (R$) <input v-model.number="fechamento.valor" type="number" min="0" step="0.01" required /></label>
        <label>Entrada (R$) <input v-model.number="fechamento.entrada" type="number" min="0" step="0.01" /></label>
        <label>Nº de parcelas (0 = à vista) <input v-model.number="fechamento.parcelas" type="number" min="0" max="120" /></label>
        <label v-if="fechamento.parcelas > 0">Vencimento da 1ª parcela <input v-model="fechamento.primeiroVencimento" type="date" /></label>
        <label>Forma de pagamento (texto do contrato) <input v-model="fechamento.forma" maxlength="300" /></label>
        <div v-if="abaixo" class="alerta-incompleto" role="alert">
          <b>Valor abaixo do mínimo da tabela da OAB</b>: diferença de {{ formatarMoeda(diferenca) }}. Informe o motivo e a justificativa para salvar; o caso aparece no relatório das sócias.
          <label>
            Motivo
            <select v-model="fechamento.motivo" required>
              <option value="" disabled>Selecione</option>
              <option v-for="[v, r] in azRotulos(ROTULO_MOTIVO)" :key="v" :value="v">{{ r }}</option>
            </select>
          </label>
          <label>Justificativa <textarea v-model="fechamento.justificativa" rows="2" maxlength="2000" required></textarea></label>
        </div>
        <div class="grupo-botoes">
          <button type="submit" class="botao-primario" :disabled="ocupado">{{ encerrada ? 'Atualizar valor fechado' : 'Registrar valor fechado' }}</button>
          <button v-if="!encerrada" type="button" class="botao-secundario" :disabled="ocupado" @click="semFechamento">Reunião sem fechamento</button>
        </div>
      </form>

      <div v-if="encerrada" class="grupo-botoes">
        <button type="button" class="botao-secundario" :disabled="ocupado" @click="minuta">Gerar minuta do contrato</button>
        <template v-if="podeAssinar && !proposta.dataAssinatura">
          <label>Data da assinatura <input v-model="assinatura" type="date" /></label>
          <button type="button" class="botao-primario" :disabled="ocupado" @click="assinar">Contrato assinado: lançar parcelas</button>
        </template>
        <span v-if="proposta.dataAssinatura" class="selo selo-ok">Assinado: parcelas lançadas no financeiro</span>
      </div>
      <p v-if="proposta.status === 'NAO_FECHADA'" class="suave">Reunião registrada sem fechamento. Você ainda pode registrar um valor fechado se o cliente voltar.</p>
    </template>
  </section>
</template>
