<script setup lang="ts">
import { az, azPor } from '../utils/ordenacao'
import { computed, onMounted, ref } from 'vue'
import { AREAS, listarClientes, listarProcessos, ROTULO_AREA, type AreaAtuacao, type Cliente, type Processo } from '../api/cadastros'
import {
  criarContrato,
  listarContratos,
  listarParcelas,
  pagarParcela,
  registrarDespesa,
  registrarHonorarioVariavel,
  resumoDoCliente,
  type Contrato,
  type Parcela,
  type ResumoFinanceiro,
  type SituacaoParcela,
} from '../api/financeiro'
import Feedback from '../components/Feedback.vue'
import Modal from '../components/Modal.vue'
import { useFeedback } from '../composables/useFeedback'
import { formatarData, hojeIso } from '../utils/datas'
import { formatarMoeda } from '../utils/moeda'

const ROTULO_SITUACAO: Record<SituacaoParcela, string> = { PAGA: 'Paga', A_VENCER: 'A vencer', EM_ATRASO: 'Em atraso', CANCELADA: 'Cancelada' }

const { erro, aviso, executar } = useFeedback()

const clientes = ref<Cliente[]>([])
const processos = ref<Processo[]>([])
const clienteId = ref<number | null>(null)
const resumo = ref<ResumoFinanceiro | null>(null)
const contratos = ref<Contrato[]>([])
const parcelas = ref<Record<number, Parcela[]>>({})
const pagando = ref<number | null>(null)
const pagamento = ref({ data: hojeIso(), valor: 0, forma: '' })
const painel = ref<'contrato' | 'despesa' | 'honorario' | null>(null)

const contratoForm = ref({ processoId: '' as number | '', descricao: '', valorTotal: 0, parcelas: 1, primeiroVencimento: hojeIso(30), area: '' as AreaAtuacao | '' })
const despesaForm = ref({ processoId: '' as number | '', tipo: 'CUSTAS' as 'CUSTAS' | 'DESPESA', descricao: '', valor: 0, data: hojeIso(), reembolsavel: false })
const honorarioForm = ref({ processoId: '' as number | '', tipo: 'EXITO' as 'EXITO' | 'SUCUMBENCIA', descricao: '', valorPrevisto: 0 })

const processosDoCliente = computed(() => processos.value.filter((p) => p.clienteId === clienteId.value))

const linhasResumo = computed(() => {
  const r = resumo.value
  if (!r) {
    return []
  }
  return [
    ['Honorários contratados', formatarMoeda(r.contratado)],
    ['Parcelas pagas', formatarMoeda(r.parcelasPagas)],
    ['Parcelas a vencer', formatarMoeda(r.parcelasAVencer)],
    [`Parcelas em atraso (${r.quantidadeEmAtraso})`, formatarMoeda(r.parcelasEmAtraso)],
    ['Custas e despesas', formatarMoeda(r.custasEDespesas)],
    ['Reembolsável', formatarMoeda(r.reembolsavel)],
    ['Êxito previsto / recebido', `${formatarMoeda(r.exitoPrevisto)} / ${formatarMoeda(r.exitoRecebido)}`],
    ['Sucumbência prevista / recebida', `${formatarMoeda(r.sucumbenciaPrevista)} / ${formatarMoeda(r.sucumbenciaRecebida)}`],
  ]
})

async function carregarCliente() {
  resumo.value = null
  contratos.value = []
  parcelas.value = {}
  if (clienteId.value === null) {
    return
  }
  const id = clienteId.value
  await executar(async () => {
    resumo.value = await resumoDoCliente(id)
    contratos.value = await listarContratos(id)
    const lista = await Promise.all(contratos.value.map((c) => listarParcelas(c.id)))
    parcelas.value = Object.fromEntries(contratos.value.map((c, i) => [c.id, lista[i]]))
  }, '', true)
}

async function depoisDeSalvar(ok: boolean) {
  if (ok) {
    painel.value = null
    await carregarCliente()
  }
}

async function novoContrato() {
  const f = contratoForm.value
  await depoisDeSalvar(
    await executar(
      () =>
        criarContrato({
          clienteId: clienteId.value as number,
          processoId: f.processoId === '' ? null : f.processoId,
          descricao: f.descricao,
          valorTotal: f.valorTotal,
          parcelas: f.parcelas,
          primeiroVencimento: f.primeiroVencimento,
          area: f.area as AreaAtuacao,
        }),
      'Contrato criado com as parcelas.',
    ),
  )
}

async function novaDespesa() {
  const f = despesaForm.value
  await depoisDeSalvar(
    await executar(
      () =>
        registrarDespesa({
          clienteId: clienteId.value as number,
          processoId: f.processoId === '' ? null : f.processoId,
          tipo: f.tipo,
          descricao: f.descricao,
          valor: f.valor,
          data: f.data || null,
          reembolsavel: f.reembolsavel,
        }),
      'Despesa registrada.',
    ),
  )
}

async function novoHonorario() {
  const f = honorarioForm.value
  await depoisDeSalvar(
    await executar(
      () =>
        registrarHonorarioVariavel({
          clienteId: clienteId.value as number,
          processoId: f.processoId === '' ? null : f.processoId,
          tipo: f.tipo,
          descricao: f.descricao,
          valorPrevisto: f.valorPrevisto,
        }),
      'Honorário variável registrado.',
    ),
  )
}

function abrirPagamento(p: Parcela) {
  pagando.value = pagando.value === p.id ? null : p.id
  pagamento.value = { data: hojeIso(), valor: p.valor, forma: '' }
}

async function pagar(p: Parcela) {
  const ok = await executar(() => (pagamento.value.forma ? pagarParcela(p.id, pagamento.value.data, pagamento.value.valor, pagamento.value.forma) : pagarParcela(p.id, pagamento.value.data, pagamento.value.valor)), 'Pagamento registrado.')
  if (ok) {
    pagando.value = null
    await carregarCliente()
  }
}

onMounted(async () => {
  await executar(async () => {
    ;[clientes.value, processos.value] = await Promise.all([listarClientes(), listarProcessos()])
  }, '', true)
})
</script>

<template>
  <section>
    <h1>Financeiro</h1>
    <p class="suave">Restrito às sócias.</p>
    <div class="barra-ferramentas">
      <label>
        Cliente
        <select v-model="clienteId" @change="carregarCliente">
          <option :value="null">Selecione</option>
          <option v-for="c in az(clientes, (x) => x.nome)" :key="c.id" :value="c.id">{{ c.nome }}</option>
        </select>
      </label>
    </div>
    <Feedback :erro="erro" :aviso="aviso" />

    <template v-if="clienteId !== null && resumo">
      <h2>Resumo</h2>
      <div class="tabela-rolavel">
        <table>
          <tbody>
            <tr v-for="[rotulo, valor] in linhasResumo" :key="rotulo">
              <th scope="row">{{ rotulo }}</th>
              <td>{{ valor }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="barra-ferramentas">
        <button type="button" class="botao-secundario" @click="painel = 'contrato'">Novo contrato</button>
        <button type="button" class="botao-secundario" @click="painel = 'despesa'">Registrar despesa</button>
        <button type="button" class="botao-secundario" @click="painel = 'honorario'">Honorário de êxito/sucumbência</button>
      </div>

      <Modal :aberto="painel === 'contrato'" titulo="Novo contrato" form-id="f-novoContrato-173" salvar-rotulo="Criar contrato" :erro="erro" @fechar="painel = null">
        <form id="f-novoContrato-173" class="formulario" @submit.prevent="novoContrato">
          <label>
            Processo (opcional)
            <select v-model="contratoForm.processoId">
              <option value="">Nenhum</option>
              <option v-for="p in az(processosDoCliente, (x) => x.numeroFormatado)" :key="p.id" :value="p.id">{{ p.numeroFormatado }}</option>
            </select>
          </label>
          <label>
            Área de atuação
            <select v-model="contratoForm.area" required>
              <option value="" disabled>Selecione</option>
              <option v-for="a in azPor(AREAS, ROTULO_AREA)" :key="a" :value="a">{{ ROTULO_AREA[a] }}</option>
            </select>
          </label>
          <label>Descrição <input v-model="contratoForm.descricao" required /></label>
          <label>Valor total (R$) <input v-model.number="contratoForm.valorTotal" type="number" min="0.01" step="0.01" required /></label>
          <label>Parcelas <input v-model.number="contratoForm.parcelas" type="number" min="1" max="120" required /></label>
          <label>Primeiro vencimento <input v-model="contratoForm.primeiroVencimento" type="date" required /></label>
        </form>
      </Modal>

      <Modal :aberto="painel === 'despesa'" titulo="Registrar despesa" form-id="f-novaDespesa-214" salvar-rotulo="Registrar" :erro="erro" @fechar="painel = null">
        <form id="f-novaDespesa-214" class="formulario" @submit.prevent="novaDespesa">
          <label>
            Tipo
            <select v-model="despesaForm.tipo">
              <option value="CUSTAS">Custas</option>
              <option value="DESPESA">Despesa</option>
            </select>
          </label>
          <label>
            Processo (opcional)
            <select v-model="despesaForm.processoId">
              <option value="">Nenhum</option>
              <option v-for="p in az(processosDoCliente, (x) => x.numeroFormatado)" :key="p.id" :value="p.id">{{ p.numeroFormatado }}</option>
            </select>
          </label>
          <label>Descrição <input v-model="despesaForm.descricao" required /></label>
          <label>Valor (R$) <input v-model.number="despesaForm.valor" type="number" min="0.01" step="0.01" required /></label>
          <label>Data <input v-model="despesaForm.data" type="date" /></label>
          <label class="marcar"><input v-model="despesaForm.reembolsavel" type="checkbox" /> Reembolsável pelo cliente</label>
        </form>
      </Modal>

      <Modal :aberto="painel === 'honorario'" titulo="Honorário de êxito ou sucumbência" form-id="f-novoHonorario-382" salvar-rotulo="Registrar" :erro="erro" @fechar="painel = null">
        <form id="f-novoHonorario-382" class="formulario" @submit.prevent="novoHonorario">
          <label>
            Tipo
            <select v-model="honorarioForm.tipo">
              <option value="EXITO">Êxito</option>
              <option value="SUCUMBENCIA">Sucumbência</option>
            </select>
          </label>
          <label>
            Processo (opcional)
            <select v-model="honorarioForm.processoId">
              <option value="">Nenhum</option>
              <option v-for="p in az(processosDoCliente, (x) => x.numeroFormatado)" :key="p.id" :value="p.id">{{ p.numeroFormatado }}</option>
            </select>
          </label>
          <label>Descrição <input v-model="honorarioForm.descricao" required /></label>
          <label>Valor previsto (R$) <input v-model.number="honorarioForm.valorPrevisto" type="number" min="0.01" step="0.01" required /></label>
        </form>
      </Modal>

      <h2>Contratos e parcelas</h2>
      <p v-if="contratos.length === 0" class="suave">Nenhum contrato.</p>
      <article v-for="c in contratos" :key="c.id" class="cartao">
        <header class="cartao-topo">
          <b>{{ c.descricao }}</b>
          <span class="cartao-quando">{{ formatarMoeda(c.valor_total) }}</span>
        </header>
        <div class="tabela-rolavel">
          <table>
            <thead>
              <tr>
                <th scope="col">Nº</th><th scope="col">Vencimento</th><th scope="col">Valor</th><th scope="col">Situação</th>
                <th scope="col"><span class="sr-only">Ações</span></th>
              </tr>
            </thead>
            <tbody>
              <template v-for="p in parcelas[c.id] ?? []" :key="p.id">
                <tr :class="{ perigo: p.situacao === 'EM_ATRASO' }">
                  <td>{{ p.numero }}</td>
                  <td>{{ formatarData(p.vencimento) }}</td>
                  <td>{{ formatarMoeda(p.valor) }}</td>
                  <td>{{ ROTULO_SITUACAO[p.situacao] }}<template v-if="p.pago_em"> em {{ formatarData(p.pago_em) }}</template></td>
                  <td><button v-if="p.situacao !== 'PAGA' && p.situacao !== 'CANCELADA'" type="button" class="botao-link" @click="abrirPagamento(p)">registrar pagamento</button></td>
                </tr>
                <tr v-if="pagando === p.id">
                  <td colspan="5">
                    <form class="grupo-botoes" @submit.prevent="pagar(p)">
                      <label>Data <input v-model="pagamento.data" type="date" required /></label>
                      <label>Valor pago (R$) <input v-model.number="pagamento.valor" type="number" min="0.01" step="0.01" required /></label>
                      <label>
                        Forma de pagamento
                        <select v-model="pagamento.forma">
                          <option value="">Não informar</option>
                          <option>PIX</option><option>Dinheiro</option><option>Cartão de débito</option><option>Cartão de crédito</option>
                          <option>Boleto</option><option>Transferência</option>
                        </select>
                      </label>
                      <button type="submit" class="botao-primario">Confirmar pagamento</button>
                    </form>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
      </article>
    </template>
  </section>
</template>
