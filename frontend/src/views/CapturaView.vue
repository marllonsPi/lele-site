<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  criarFiltro,
  desativarFiltro,
  executarCaptura,
  listarExecucoes,
  listarFiltros,
  listarIgnorados,
  ROTULO_FONTE,
  type ExecucaoCaptura,
  type FiltroCaptura,
  type Fonte,
  type Ignorado,
} from '../api/publicacoes'
import Feedback from '../components/Feedback.vue'
import { useFeedback } from '../composables/useFeedback'
import { useUsuarios } from '../composables/useUsuarios'
import { formatarDataHora } from '../utils/datas'

const { usuarios, carregar: carregarUsuarios, nome } = useUsuarios()
const { erro, aviso, executar, ocupado } = useFeedback()

const execucoes = ref<ExecucaoCaptura[]>([])
const ignorados = ref<Ignorado[]>([])
const filtros = ref<FiltroCaptura[]>([])
const novo = ref({ usuarioId: 0, fonte: 'EMAIL' as Fonte, tribunalPrefixo: '', excecoes: '' })

async function recarregar() {
  await executar(async () => {
    ;[execucoes.value, ignorados.value, filtros.value] = await Promise.all([listarExecucoes(), listarIgnorados(), listarFiltros()])
  }, '', true)
}

async function rodar() {
  if (await executar(() => executarCaptura(), 'Captura executada.')) {
    await recarregar()
  }
}

async function adicionar() {
  if (await executar(() => criarFiltro(novo.value), 'Filtro criado.')) {
    novo.value = { ...novo.value, tribunalPrefixo: '', excecoes: '' }
    await recarregar()
  }
}

async function remover(f: FiltroCaptura) {
  if (await executar(() => desativarFiltro(f.id), 'Filtro desativado.')) {
    await recarregar()
  }
}

onMounted(async () => {
  await carregarUsuarios()
  novo.value.usuarioId = usuarios.value[0]?.id ?? 0
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

    <h2>Filtros de captura</h2>
    <p class="suave">Ex.: prefixo "TRT" com exceção "TRT-10" ignora os TRT, menos o TRT-10. O ignorado fica no registro.</p>
    <div class="tabela-rolavel">
      <table>
        <thead>
          <tr><th scope="col">Pessoa</th><th scope="col">Fonte</th><th scope="col">Prefixo</th><th scope="col">Exceções</th><th scope="col"><span class="sr-only">Ações</span></th></tr>
        </thead>
        <tbody>
          <tr v-for="f in filtros" :key="f.id" :class="{ suave: !f.ativo }">
            <td>{{ nome(f.usuarioId) }}</td>
            <td>{{ ROTULO_FONTE[f.fonte] }}</td>
            <td>{{ f.tribunalPrefixo }}</td>
            <td>{{ f.excecoes }}</td>
            <td><button v-if="f.ativo" type="button" class="botao-link" @click="remover(f)">desativar</button></td>
          </tr>
        </tbody>
      </table>
    </div>
    <form class="formulario" @submit.prevent="adicionar">
      <label>
        Pessoa
        <select v-model="novo.usuarioId">
          <option v-for="u in usuarios" :key="u.id" :value="u.id">{{ u.nome }}</option>
        </select>
      </label>
      <label>
        Fonte
        <select v-model="novo.fonte">
          <option v-for="(r, v) in ROTULO_FONTE" :key="v" :value="v">{{ r }}</option>
        </select>
      </label>
      <label>Prefixo do tribunal <input v-model="novo.tribunalPrefixo" required /></label>
      <label>Exceções (separadas por vírgula) <input v-model="novo.excecoes" /></label>
      <button type="submit" class="botao-secundario">Adicionar filtro</button>
    </form>

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
    <p v-if="execucoes.length === 0" class="suave">Nenhuma execução.</p>
    <div v-else class="tabela-rolavel">
      <table>
        <thead>
          <tr><th scope="col">Quando</th><th scope="col">Fonte</th><th scope="col">Resultado</th><th scope="col">Lidos</th><th scope="col">Novos</th><th scope="col">Erro</th></tr>
        </thead>
        <tbody>
          <tr v-for="e in execucoes" :key="e.id">
            <td>{{ formatarDataHora(e.inicio) }}</td><td>{{ e.fonte }}</td><td>{{ e.resultado }}</td>
            <td>{{ e.itensLidos }}</td><td>{{ e.itensNovos }}</td><td>{{ e.erro }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
