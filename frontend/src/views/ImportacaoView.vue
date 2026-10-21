<script setup lang="ts">
import { azPor } from '../utils/ordenacao'
import { computed, onMounted, ref } from 'vue'
import { AREAS, ROTULO_AREA, type AreaAtuacao } from '../api/cadastros'
import {
  cancelarImportacao,
  confirmarImportacao,
  conectarGoogle,
  decidirItem,
  decidirLote,
  detalheImportacao,
  enviarPlanilha,
  listarImportacoes,
  previaDoDrive,
  previaPlanilhaDoDrive,
  ROTULO_ACAO_ITEM,
  ROTULO_TIPO_ITEM,
  statusDrive,
  statusGoogle,
  type AcaoItem,
  type DecisaoItem,
  type Importacao,
  type ItemPrevia,
  type StatusDrive,
  type StatusGoogle,
  type TipoItem,
} from '../api/importacao'
import Feedback from '../components/Feedback.vue'
import { useFeedback } from '../composables/useFeedback'
import { formatarDataHora } from '../utils/datas'

const { erro, aviso, executar, ocupado } = useFeedback()

const historico = ref<Importacao[]>([])
const atual = ref<Importacao | null>(null)
const drive = ref<StatusDrive | null>(null)
const google = ref<StatusGoogle | null>(null)
const filtroTipo = ref<TipoItem | ''>('')
const soDivergencias = ref(false)
const mostrarIgnorados = ref(false)
const limite = ref(40)

const TIPOS = Object.keys(ROTULO_TIPO_ITEM) as TipoItem[]
const ACOES: AcaoItem[] = ['CRIAR', 'CONFLITO', 'SEM_MUDANCA', 'IGNORAR']

const emPrevia = computed(() => atual.value?.status === 'PREVIA')
const itensFiltrados = computed(() => {
  const a = atual.value
  if (!a) {
    return []
  }
  return a.itens.filter(
    (i) =>
      (!filtroTipo.value || i.tipo === filtroTipo.value) &&
      (mostrarIgnorados.value || i.acao !== 'IGNORAR') &&
      (!soDivergencias.value || i.divergencias.length > 0 || i.acao === 'CONFLITO'),
  )
})

async function carregarHistorico() {
  await executar(async () => {
    historico.value = await listarImportacoes()
    drive.value = await statusDrive()
    google.value = await statusGoogle()
  }, '', true)
}

async function abrir(id: number) {
  await executar(async () => {
    atual.value = await detalheImportacao(id)
    limite.value = 40
  })
}

async function gerar(acao: () => Promise<Importacao>) {
  const ok = await executar(async () => {
    atual.value = await acao()
    limite.value = 40
  })
  if (ok) {
    await carregarHistorico()
  }
}

function enviarArquivo(ev: Event) {
  const campo = ev.target as HTMLInputElement
  const arquivo = campo.files?.[0]
  if (arquivo) {
    gerar(() => enviarPlanilha(arquivo)).finally(() => (campo.value = ''))
  }
}

async function conectar() {
  await executar(async () => {
    const r = await conectarGoogle(true)
    window.location.href = r.url
  })
}

async function salvarItem(i: ItemPrevia, decisao: DecisaoItem | null, ajustes: Record<string, unknown> = {}) {
  const a = atual.value
  if (!a) {
    return
  }
  await executar(async () => {
    atual.value = await decidirItem(a.id, i.id, { decisao, ajustes })
  }, '', true)
}

async function lote(acao: AcaoItem, tipo: TipoItem | null, decisao: DecisaoItem) {
  const a = atual.value
  if (a) {
    await executar(async () => {
      atual.value = await decidirLote(a.id, acao, tipo, decisao)
    })
  }
}

async function confirmar() {
  const a = atual.value
  if (!a) {
    return
  }
  const ok = await executar(async () => {
    atual.value = await confirmarImportacao(a.id)
  }, 'Importação concluída.')
  if (ok) {
    await carregarHistorico()
  }
}

async function cancelar() {
  const a = atual.value
  if (a && (await executar(async () => (atual.value = await cancelarImportacao(a.id)), 'Prévia descartada: nada foi gravado.'))) {
    await carregarHistorico()
  }
}

// ---- apresentacao dos campos lado a lado

const ROTULOS: Record<string, string> = {
  nome: 'Nome', documento: 'CPF/CNPJ', rg: 'RG', estadoCivil: 'Estado civil', endereco: 'Endereço', telefone: 'Telefone', email: 'E-mail',
  areas: 'Áreas', area: 'Área', valorTotal: 'Valor total', percentualExito: '% êxito', formaPagamento: 'Forma de pagamento',
  situacaoOrigem: 'Situação na planilha', numeroParcelas: 'Nº de parcelas', dataAssinatura: 'Assinatura', valor: 'Valor', vencimento: 'Vencimento',
  situacao: 'Situação', numero: 'Nº da parcela', cnj: 'Processo (CNJ)', tribunal: 'Tribunal', classe: 'Classe', tipoAcao: 'Tipo de ação',
}

function campos(i: ItemPrevia): string[] {
  const chaves = Object.keys(ROTULOS)
  return chaves.filter((k) => (i.importado[k] != null && i.importado[k] !== '') || (i.existente && i.existente[k] != null && i.existente[k] !== ''))
}

function mostrar(v: unknown): string {
  if (v == null || v === '') {
    return '—'
  }
  if (Array.isArray(v)) {
    return v.map((x) => ROTULO_AREA[x as AreaAtuacao] ?? String(x)).join(', ') || '—'
  }
  if (typeof v === 'string' && ROTULO_AREA[v as AreaAtuacao]) {
    return ROTULO_AREA[v as AreaAtuacao]
  }
  return String(v)
}

function diferente(i: ItemPrevia, k: string): boolean {
  return !!i.existente && i.importado[k] != null && String(i.importado[k]) !== String(i.existente[k] ?? '')
}

function efetivo(i: ItemPrevia, campo: string): unknown {
  return i.ajustes[campo] !== undefined ? i.ajustes[campo] : i.importado[campo]
}

function areasMarcadas(i: ItemPrevia): string[] {
  const v = efetivo(i, 'areas')
  return Array.isArray(v) ? (v as string[]) : []
}

function alternarArea(i: ItemPrevia, a: AreaAtuacao, marcada: boolean) {
  const s = new Set(areasMarcadas(i))
  if (marcada) {
    s.add(a)
  } else {
    s.delete(a)
  }
  salvarItem(i, i.decisao, { areas: [...s] })
}

const decisaoPadrao = (i: ItemPrevia): DecisaoItem => i.decisao ?? (i.acao === 'CRIAR' ? 'APLICAR' : 'MANTER')

const ROTULO_STATUS = { PREVIA: 'Prévia (aguardando confirmação)', CONCLUIDA: 'Concluída', CANCELADA: 'Descartada' } as const

onMounted(async () => {
  await carregarHistorico()
  const id = new URLSearchParams(window.location.search).get('id')
  if (id) {
    await abrir(Number(id))
  }
})
</script>

<template>
  <section>
    <h1>Importação de dados</h1>
    <p class="suave">
      Planilha financeira e pastas de clientes do Google Drive. Nada é gravado até uma sócia confirmar a prévia; dados já existentes só são
      alterados quando você decide.
    </p>
    <Feedback :erro="erro" :aviso="aviso" />

    <h2>Nova importação</h2>
    <div class="formulario">
      <label>
        Planilha financeira (.xlsx)
        <input type="file" accept=".xlsx" :disabled="ocupado" @change="enviarArquivo" />
      </label>
      <div class="grupo-botoes">
        <button type="button" class="botao-secundario" :disabled="ocupado || !drive?.planilhaConfigurada || !drive?.contaConectadaAoDrive" @click="gerar(previaPlanilhaDoDrive)">
          Ler a planilha do Drive
        </button>
        <button type="button" class="botao-secundario" :disabled="ocupado || !drive?.pastaClientesConfigurada || !drive?.contaConectadaAoDrive" @click="gerar(previaDoDrive)">
          Ler as pastas de clientes do Drive
        </button>
      </div>
      <p v-if="drive && !drive.contaConectadaAoDrive" class="suave">
        Sua conta Google ainda não está conectada com acesso ao Drive (somente leitura).
        <button v-if="google?.disponivel" type="button" class="botao-secundario" @click="conectar">Conectar Google com Drive</button>
        <span v-else>A conexão com o Google não está configurada neste ambiente.</span>
      </p>
      <p v-if="drive && drive.contaConectadaAoDrive && (!drive.pastaClientesConfigurada || !drive.planilhaConfigurada)" class="suave">
        Falta configurar no servidor:
        <template v-if="!drive.pastaClientesConfigurada">IMPORTACAO_PASTA_CLIENTES_ID </template>
        <template v-if="!drive.planilhaConfigurada">IMPORTACAO_PLANILHA_ID</template>
      </p>
    </div>

    <h2>Histórico</h2>
    <p v-if="historico.length === 0" class="suave">Nenhuma importação ainda.</p>
    <article v-for="h in historico" :key="h.id" class="cartao">
      <header class="cartao-topo">
        <b>#{{ h.id }} · {{ h.origem === 'PLANILHA' ? 'Planilha' : 'Drive' }}<template v-if="h.modo === 'DIARIA'"> (diária)</template></b>
        <span class="selo" :class="{ 'selo-ok': h.status === 'CONCLUIDA', 'selo-atencao': h.status === 'PREVIA' }">{{ ROTULO_STATUS[h.status] }}</span>
        <span class="cartao-quando">{{ formatarDataHora(h.criadoEm) }}</span>
      </header>
      <p class="suave">{{ h.arquivoNome }}</p>
      <div class="cartao-acoes">
        <button type="button" class="botao-secundario" @click="abrir(h.id)">Abrir</button>
      </div>
    </article>

    <template v-if="atual">
      <h2>
        Importação #{{ atual.id }}
        <span class="selo">{{ ROTULO_STATUS[atual.status] }}</span>
      </h2>

      <div class="tabela-rolavel">
        <table>
          <thead>
            <tr>
              <th scope="col">Tipo</th>
              <th v-for="a in ACOES" :key="a" scope="col">{{ ROTULO_ACAO_ITEM[a] }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="t in TIPOS.filter((x) => atual!.contagens[x])" :key="t">
              <th scope="row">{{ ROTULO_TIPO_ITEM[t] }}</th>
              <td v-for="a in ACOES" :key="a">{{ atual.contagens[t]?.[a] ?? 0 }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p>
        <b>{{ atual.comDivergencia }}</b> registro(s) com divergência para conferir.
      </p>

      <div v-if="emPrevia && atual.pendencias.length" class="alerta-incompleto" role="alert">
        <b>Pendências para poder confirmar ({{ atual.pendencias.length }}):</b>
        <ul class="lista-simples">
          <li v-for="p in atual.pendencias.slice(0, 15)" :key="p">{{ p }}</li>
          <li v-if="atual.pendencias.length > 15">… e mais {{ atual.pendencias.length - 15 }}.</li>
        </ul>
      </div>

      <div v-if="emPrevia" class="grupo-botoes">
        <button type="button" class="botao-primario" :disabled="ocupado || atual.pendencias.length > 0" @click="confirmar">
          Confirmar importação
        </button>
        <button type="button" class="botao-secundario" :disabled="ocupado" @click="cancelar">Descartar prévia</button>
        <button type="button" class="botao-secundario" @click="lote('CONFLITO', null, 'APLICAR')">Aplicar todos os conflitos</button>
        <button type="button" class="botao-secundario" @click="lote('CONFLITO', null, 'MANTER')">Manter todos os existentes</button>
        <button type="button" class="botao-secundario" @click="lote('CRIAR', null, 'MANTER')">Não importar nenhum novo</button>
        <button type="button" class="botao-secundario" @click="lote('CRIAR', null, 'APLICAR')">Importar todos os novos</button>
      </div>

      <template v-if="atual.relatorio">
        <h3>Relatório da importação</h3>
        <div class="tabela-rolavel">
          <table>
            <thead>
              <tr>
                <th scope="col">Tipo</th>
                <th scope="col">Criados</th>
                <th scope="col">Atualizados</th>
                <th scope="col">Mantidos / sem mudança</th>
                <th scope="col">Ignorados</th>
                <th scope="col">Erros</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(v, t) in atual.relatorio.totais" :key="t">
                <th scope="row">{{ ROTULO_TIPO_ITEM[t as TipoItem] ?? t }}</th>
                <td>{{ v.criados ?? 0 }}</td>
                <td>{{ v.atualizados ?? 0 }}</td>
                <td>{{ v.mantidos ?? 0 }}</td>
                <td>{{ v.ignorados ?? 0 }}</td>
                <td :class="{ perigo: (v.erros ?? 0) > 0 }">{{ v.erros ?? 0 }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <template v-if="atual.relatorio.erros.length">
          <h4>Erros</h4>
          <ul class="lista-simples">
            <li v-for="(e, n) in atual.relatorio.erros" :key="n">{{ e.item }}: {{ e.motivo }}</li>
          </ul>
        </template>
        <details>
          <summary>Ignorados e mantidos, com o motivo ({{ atual.relatorio.ignorados.length }})</summary>
          <ul class="lista-simples">
            <li v-for="(e, n) in atual.relatorio.ignorados.slice(0, 300)" :key="n">{{ e.item }}: {{ e.motivo }}</li>
          </ul>
        </details>
      </template>

      <h3>Registros</h3>
      <div class="barra-ferramentas">
        <label>
          Tipo
          <select v-model="filtroTipo">
            <option value="">Todos</option>
            <option v-for="t in TIPOS" :key="t" :value="t">{{ ROTULO_TIPO_ITEM[t] }}</option>
          </select>
        </label>
        <label class="opcao-linha"><input v-model="soDivergencias" type="checkbox" /> Só com divergência</label>
        <label class="opcao-linha"><input v-model="mostrarIgnorados" type="checkbox" /> Mostrar ignorados</label>
      </div>
      <p class="suave">{{ itensFiltrados.length }} registro(s).</p>

      <article v-for="i in itensFiltrados.slice(0, limite)" :key="i.id" class="cartao" :class="{ inativo: i.acao === 'IGNORAR' || i.acao === 'SEM_MUDANCA' }">
        <header class="cartao-topo">
          <b>{{ i.rotulo }}</b>
          <span class="selo">{{ ROTULO_TIPO_ITEM[i.tipo] }}</span>
          <span class="selo" :class="{ 'selo-pendente': i.acao === 'CONFLITO', 'selo-ok': i.acao === 'CRIAR' }">{{ ROTULO_ACAO_ITEM[i.acao] }}</span>
          <span v-if="i.resultado" class="selo">{{ i.resultado }}</span>
        </header>
        <ul v-if="i.divergencias.length" class="lista-simples divergencias">
          <li v-for="d in i.divergencias" :key="d">⚠ {{ d }}</li>
        </ul>

        <div v-if="i.existente || campos(i).length" class="tabela-rolavel">
          <table>
            <thead>
              <tr>
                <th scope="col">Campo</th>
                <th scope="col">Importado</th>
                <th v-if="i.existente" scope="col">No sistema</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="k in campos(i)" :key="k" :class="{ perigo: diferente(i, k) }">
                <th scope="row">{{ ROTULOS[k] }}</th>
                <td>{{ mostrar(efetivo(i, k)) }}</td>
                <td v-if="i.existente">{{ mostrar(i.existente[k]) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="i.motivo" class="suave">{{ i.motivo }}</p>

        <div v-if="emPrevia && (i.acao === 'CRIAR' || i.acao === 'CONFLITO')" class="formulario">
          <label>
            Decisão
            <select :value="decisaoPadrao(i)" @change="salvarItem(i, ($event.target as HTMLSelectElement).value as DecisaoItem)">
              <option value="APLICAR">{{ i.acao === 'CRIAR' ? 'Importar' : 'Aplicar o importado' }}</option>
              <option value="MANTER">{{ i.acao === 'CRIAR' ? 'Não importar' : 'Manter o do sistema' }}</option>
            </select>
          </label>
          <fieldset v-if="i.tipo === 'CLIENTE'">
            <legend>Área de atuação do cliente</legend>
            <label v-for="a in AREAS" :key="a" class="opcao-linha">
              <input type="checkbox" :checked="areasMarcadas(i).includes(a)" @change="alternarArea(i, a, ($event.target as HTMLInputElement).checked)" />
              {{ ROTULO_AREA[a] }}
            </label>
          </fieldset>
          <label v-if="i.tipo === 'CONTRATO' || i.tipo === 'PROCESSO'">
            Área de atuação
            <select :value="(efetivo(i, 'area') as string) ?? ''" @change="salvarItem(i, i.decisao, { area: ($event.target as HTMLSelectElement).value })">
              <option value="">(a definir)</option>
              <option v-for="a in azPor(AREAS, ROTULO_AREA)" :key="a" :value="a">{{ ROTULO_AREA[a] }}</option>
            </select>
          </label>
          <label v-if="i.tipo === 'PROCESSO'">
            Tribunal
            <input :value="(efetivo(i, 'tribunal') as string) ?? ''" maxlength="40" @change="salvarItem(i, i.decisao, { tribunal: ($event.target as HTMLInputElement).value })" />
          </label>
          <label v-if="i.tipo === 'PARCELA'">
            Vencimento
            <input type="date" :value="(efetivo(i, 'vencimento') as string) ?? ''" @change="salvarItem(i, i.decisao, { vencimento: ($event.target as HTMLInputElement).value })" />
          </label>
          <label v-if="i.acao === 'CONFLITO' && atual.origem === 'DRIVE' && (i.tipo === 'CLIENTE' || i.tipo === 'CONTRATO')" class="opcao-linha">
            <input
              type="checkbox"
              :checked="i.ajustes.sobrescrever === true || i.ajustes.sobrescrever === 'true'"
              @change="salvarItem(i, i.decisao ?? 'APLICAR', { sobrescrever: ($event.target as HTMLInputElement).checked ? true : '' })"
            />
            Sobrescrever também os valores diferentes (por padrão só preenche o que está vazio)
          </label>
        </div>
      </article>
      <button v-if="itensFiltrados.length > limite" type="button" class="botao-secundario" @click="limite += 60">Mostrar mais</button>
    </template>
  </section>
</template>
