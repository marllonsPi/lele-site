<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  carregarConfiguracao,
  carregarProntidao,
  salvarConfiguracao,
  type CampoConfiguracao,
  type ItemProntidao,
} from '../api/sistema'
import Feedback from '../components/Feedback.vue'
import { useFeedback } from '../composables/useFeedback'

const { erro, aviso, executar } = useFeedback()

const campos = ref<CampoConfiguracao[]>([])
const valores = ref<Record<string, string>>({})
const itens = ref<ItemProntidao[]>([])
const carregando = ref(true)

const grupos = computed(() => [...new Set(campos.value.map((c) => c.grupo))])
const prontos = computed(() => itens.value.filter((i) => i.situacao === 'OK').length)
const percentual = computed(() => (itens.value.length ? Math.round((prontos.value / itens.value.length) * 100) : 0))
const ROTULO = { OK: 'Pronto', ATENCAO: 'Atenção', PENDENTE: 'Pendente' } as const

async function carregar() {
  await executar(async () => {
    const [cfg, pr] = await Promise.all([carregarConfiguracao(), carregarProntidao()])
    campos.value = cfg.campos
    valores.value = { ...cfg.valores }
    itens.value = pr
  }, '', true)
  carregando.value = false
}

async function salvar() {
  const ok = await executar(async () => {
    valores.value = { ...(await salvarConfiguracao(valores.value)).valores }
  }, 'Configuração salva.')
  if (ok) {
    itens.value = await carregarProntidao()
  }
}

onMounted(carregar)
</script>

<template>
  <section>
    <div class="cabecalho-pagina">
      <div>
        <h1>Sistema</h1>
        <p class="suave">Configuração geral e diagnóstico. Visível só para o acesso mestre.</p>
      </div>
    </div>
    <Feedback :erro="erro" :aviso="aviso" />
    <p v-if="carregando">Carregando...</p>

    <div class="duas-colunas" v-else>
      <div class="painel">
        <h2>Prontidão do sistema</h2>
        <p class="suave">{{ prontos }} de {{ itens.length }} itens prontos ({{ percentual }}%).</p>
        <div class="barra-progresso" role="progressbar" :aria-valuenow="percentual" aria-valuemin="0" aria-valuemax="100">
          <span :style="{ width: percentual + '%' }" />
        </div>
        <div v-for="i in itens" :key="i.id" class="item-prontidao">
          <span class="ponto" :class="'ponto-' + i.situacao" aria-hidden="true" />
          <div>
            <b>{{ i.titulo }}</b>
            <p class="suave">{{ i.detalhe }}</p>
          </div>
          <span>
            <span class="selo" :class="{ 'selo-ok': i.situacao === 'OK', 'selo-atencao': i.situacao === 'ATENCAO', 'selo-pendente': i.situacao === 'PENDENTE' }">{{ ROTULO[i.situacao] }}</span>
            <router-link v-if="i.rota && i.situacao !== 'OK'" :to="i.rota"> Resolver</router-link>
          </span>
        </div>
      </div>

      <form class="painel" @submit.prevent="salvar">
        <h2>Dados do escritório</h2>
        <p class="suave">Usados no timbre dos relatórios em PDF e nos e-mails. A alteração vale na hora.</p>
        <template v-for="g in grupos" :key="g">
          <h3>{{ g }}</h3>
          <div class="linha-form">
            <label v-for="c in campos.filter((x) => x.grupo === g)" :key="c.chave">
              {{ c.rotulo }}
              <textarea v-if="c.longo" v-model="valores[c.chave]" :maxlength="c.maximo" rows="3" />
              <input v-else v-model="valores[c.chave]" :maxlength="c.maximo" :required="c.chave === 'escritorio.nome'" />
              <span class="suave">{{ c.ajuda }}</span>
            </label>
          </div>
        </template>
        <div class="grupo-botoes">
          <button type="submit" class="botao-primario">Salvar</button>
        </div>
      </form>
    </div>
  </section>
</template>
